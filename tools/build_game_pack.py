"""Build the Adams Haven game pack that Weaverse's Games mode runs.

The pack is the exported Godot project, read straight out of a Godot Android
build: everything under the APK's ``assets/`` becomes the root of a zip, which
the embedded Godot runtime opens with ``--main-pack``. Entries are STORED (no
compression) so Godot can seek inside the battle videos without inflating them.

Official Godot Android runtimes refuse ``--main-pack`` from outside the APK, so
the game is not started as the main pack. The APK carries a small boot project
(``app/src/main/assets/project.binary`` + ``weaverse-game/boot.gd``) whose first
autoload mounts this zip with ``ProjectSettings.load_resource_pack`` before the
game's own autoloads load. The game's project settings reach the engine through
``weaverse-game/override.cfg`` in the pack, converted here from its
``project.binary``; Weaverse copies it to ``user://`` before each launch.

A ``weaverse-game.json`` manifest at the root names the game, its version and
the engine it was exported with; Weaverse refuses a pack whose engine does not
match the runtime bundled in the APK.

Usage:
    python tools/build_game_pack.py [path/to/AdamsHaven-vX.Y.Z.apk] [--version X.Y.Z]
    python tools/build_game_pack.py --boot   # rewrite the APK's boot project.binary

The source APK is only read, never modified.
"""

from __future__ import annotations

import argparse
import json
import re
import struct
import sys
import zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
DEFAULT_APK = Path("S:/AI/Game/AHCG test builds/AdamsHaven-v0.11.0.apk")
OUT_DIR = REPO / "build" / "game-packs"

GAME_ID = "adams-haven"
GAME_TITLE = "Adams Haven"
# Must match the godot-lib AAR in app/libs (GodotRuntime.ENGINE_VERSION).
ENGINE = "4.7.1.stable"

# Android-export plumbing that means nothing inside a pack. `_cl_` is the
# export's baked command line; Weaverse passes its own.
SKIP = {"_cl_"}

OVERRIDE = "weaverse-game/override.cfg"
BOOT_BINARY = REPO / "app" / "src" / "main" / "assets" / "project.binary"

# Godot Variant type ids (core/variant/variant.h) for what project settings hold.
V_BOOL, V_INT, V_FLOAT, V_STRING, V_PACKED_STRING_ARRAY = 1, 2, 3, 4, 34
FLAG_64 = 1 << 16


def read_project_binary(data: bytes) -> list[tuple[str, bytes]]:
    """project.binary: b"ECFG", a count, then (key, encoded Variant) pairs in load order."""
    if data[:4] != b"ECFG":
        raise ValueError("not a Godot project.binary")
    count = struct.unpack_from("<I", data, 4)[0]
    offset, entries = 8, []
    for _ in range(count):
        klen = struct.unpack_from("<I", data, offset)[0]
        key = data[offset + 4: offset + 4 + klen].decode()
        offset += 4 + klen
        vlen = struct.unpack_from("<I", data, offset)[0]
        entries.append((key, data[offset + 4: offset + 4 + vlen]))
        offset += 4 + vlen
    return entries


def _godot_string(text: str) -> str:
    return '"' + text.replace("\\", "\\\\").replace('"', '\\"') + '"'


def variant_to_text(value: bytes) -> str | None:
    """The project.godot text form of an encoded Variant, for the types settings use."""
    header = struct.unpack_from("<I", value, 0)[0]
    kind, wide = header & 0xFF, bool(header & FLAG_64)
    if kind == V_BOOL:
        return "true" if struct.unpack_from("<I", value, 4)[0] else "false"
    if kind == V_INT:
        return str(struct.unpack_from("<q" if wide else "<i", value, 4)[0])
    if kind == V_FLOAT:
        return repr(struct.unpack_from("<d" if wide else "<f", value, 4)[0])
    if kind == V_STRING:
        length = struct.unpack_from("<I", value, 4)[0]
        return _godot_string(value[8:8 + length].decode())
    if kind == V_PACKED_STRING_ARRAY:
        count, offset, items = struct.unpack_from("<I", value, 4)[0], 8, []
        for _ in range(count):
            length = struct.unpack_from("<I", value, offset)[0]
            # Each element's length counts its NUL terminator.
            items.append(_godot_string(value[offset + 4: offset + 4 + length].decode().rstrip("\0")))
            offset += 4 + length + (-length % 4)
        return "PackedStringArray(" + ", ".join(items) + ")"
    return None


def override_text(project_binary: bytes) -> str:
    """The game's settings as an override file, sections in first-seen order."""
    sections: dict[str, list[str]] = {}
    for key, value in read_project_binary(project_binary):
        text = variant_to_text(value)
        if text is None:
            print(f"  skipped setting {key}: unsupported type", file=sys.stderr)
            continue
        section, _, name = key.partition("/")
        sections.setdefault(section, []).append(f"{name}={text}")
    lines = ["; Adams Haven project settings, applied by Weaverse over its boot project.", "config_version=5", ""]
    for section, values in sections.items():
        lines += [f"[{section}]", "", *values, ""]
    return "\n".join(lines)


def _encode_string(text: str) -> bytes:
    raw = text.encode()
    return struct.pack("<II", V_STRING, len(raw)) + raw + b"\0" * (-len(raw) % 4)


def write_boot_binary() -> None:
    """The APK's own project: nothing but the boot autoload and where to find the override."""
    settings = [
        ("application/config/name", _encode_string("Adams Haven")),
        ("application/config/project_settings_override", _encode_string("user://" + OVERRIDE)),
        ("autoload/WeaverseBoot", _encode_string("*res://weaverse-game/boot.gd")),
    ]
    out = b"ECFG" + struct.pack("<I", len(settings))
    for key, value in settings:
        raw = key.encode()
        out += struct.pack("<I", len(raw)) + raw + struct.pack("<I", len(value)) + value
    BOOT_BINARY.write_bytes(out)
    print(f"{BOOT_BINARY} ({len(out)} bytes)")


def engine_of(apk: zipfile.ZipFile) -> str | None:
    try:
        data = apk.read("lib/arm64-v8a/libgodot_android.so")
    except KeyError:
        return None
    found = re.search(rb"4\.\d+\.\d+\.stable", data)
    return found.group(0).decode() if found else None


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("apk", nargs="?", type=Path, default=DEFAULT_APK)
    parser.add_argument("--version", help="game version; read from the APK name when omitted")
    parser.add_argument("--boot", action="store_true", help="only rewrite the APK's boot project.binary")
    args = parser.parse_args()

    if args.boot:
        write_boot_binary()
        return 0

    version = args.version
    if not version:
        match = re.search(r"v(\d+\.\d+\.\d+)", args.apk.name)
        if not match:
            print("Cannot read a version from the APK name; pass --version.", file=sys.stderr)
            return 2
        version = match.group(1)

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    out = OUT_DIR / f"{GAME_ID}-v{version}.zip"
    tmp = out.with_suffix(".zip.part")

    with zipfile.ZipFile(args.apk) as apk:
        engine = engine_of(apk)
        if engine != ENGINE:
            print(f"APK engine is {engine}, Weaverse bundles {ENGINE}. Re-export with Godot {ENGINE}.", file=sys.stderr)
            return 1
        if "assets/project.binary" not in apk.namelist():
            print("No assets/project.binary: this is not a Godot export.", file=sys.stderr)
            return 1

        entries = [
            info for info in apk.infolist()
            if info.filename.startswith("assets/")
            and not info.is_dir()
            and info.filename[len("assets/"):] not in SKIP
        ]
        total = sum(info.file_size for info in entries)
        manifest = {
            "schema": 1,
            "id": GAME_ID,
            "title": GAME_TITLE,
            "version": version,
            "engine": engine,
            "files": len(entries),
            "byteSize": total,
            "source": args.apk.name,
        }

        written = 0
        with zipfile.ZipFile(tmp, "w", zipfile.ZIP_STORED, allowZip64=True) as pack:
            pack.writestr("weaverse-game.json", json.dumps(manifest, indent=2))
            pack.writestr(OVERRIDE, override_text(apk.read("assets/project.binary")))
            for index, info in enumerate(entries, 1):
                name = info.filename[len("assets/"):]
                with apk.open(info) as src, pack.open(zipfile.ZipInfo(name, date_time=info.date_time), "w", force_zip64=True) as dst:
                    while chunk := src.read(1 << 20):
                        dst.write(chunk)
                written += info.file_size
                if index % 500 == 0:
                    print(f"  {index}/{len(entries)} files, {written / 1e6:.0f}/{total / 1e6:.0f} MB")

    tmp.replace(out)
    print(f"{out}  ({out.stat().st_size / 1e6:.0f} MB, {len(entries)} files, Godot {engine})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
