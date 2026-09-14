#!/usr/bin/env python3
"""Re-encode Adams Haven Godot art into the Weaverse APK core set and media packs.

The Godot art tree (see ``sourceRoot`` in ``tools/media-packs.json``) is treated as
strictly read-only: this script only ever reads from it. Everything it writes lands
either under ``app/src/main/assets`` (the small core set that ships inside the APK) or
under ``build/media-packs`` (optional downloadable packs).

Usage:
    python tools/build_media_packs.py [--dry-run] [--force] [--only GROUP[,GROUP...]]
                                      [--no-zip] [--manifest PATH]
"""

from __future__ import annotations

import argparse
import json
import shutil
import sys
import zipfile
from dataclasses import dataclass, field
from pathlib import Path

try:
    from PIL import Image
except ImportError:  # pragma: no cover - environment guard
    sys.exit("Pillow is required: python -m pip install Pillow")

IMAGE_SUFFIXES = {".webp", ".png", ".jpg", ".jpeg"}
CACHE_NAME = ".media-packs-cache.json"
REPO_ROOT = Path(__file__).resolve().parent.parent


def title_from_stem(stem: str) -> str:
    words = stem.replace("_", " ").replace("-", " ").split()
    return " ".join(word if word.isupper() else word.capitalize() for word in words)


def expand(template: str, stem: str, category: str) -> str:
    return template.replace("{stem}", stem).replace("{category}", title_from_stem(category))


@dataclass
class Item:
    """One encoded output file plus the metadata the app needs to register it."""

    source: Path
    dest: Path
    relative_path: str
    media_id: str
    display_name: str
    category: str
    tags: list[str]
    max_edge: int
    quality: int
    copy_verbatim: bool = False
    pack: str | None = None
    width: int = 0
    height: int = 0
    byte_size: int = 0
    skipped: bool = False


@dataclass
class GroupReport:
    group_id: str
    core: list[Item] = field(default_factory=list)
    packed: list[Item] = field(default_factory=list)
    source_count: int = 0
    source_bytes: int = 0


def load_cache(path: Path) -> dict:
    if not path.is_file():
        return {}
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}


def cache_stamp(item: Item) -> dict:
    stat = item.source.stat()
    return {
        "src": str(item.source),
        "mtime": int(stat.st_mtime),
        "size": stat.st_size,
        "maxEdge": item.max_edge,
        "quality": item.quality,
        "verbatim": item.copy_verbatim,
    }


def collect_sources(group: dict, source_root: Path) -> list[tuple[Path, str]]:
    """Returns (file, category) pairs, where category is empty for flat groups."""
    root = source_root / group["source"]
    if not root.is_dir():
        print(f"  ! source folder missing, skipped: {root}")
        return []
    found: list[tuple[Path, str]] = []
    if group.get("recursive"):
        for path in sorted(root.rglob("*")):
            if not path.is_file() or path.suffix.lower() not in IMAGE_SUFFIXES:
                continue
            if "_staging" in path.parts:
                continue
            found.append((path, path.parent.name))
    else:
        for path in sorted(root.iterdir()):
            if path.is_file() and path.suffix.lower() in IMAGE_SUFFIXES:
                found.append((path, ""))
    return found


def clone(item: Item, pack: str, max_edge: int) -> Item:
    twin = Item(**dict(item.__dict__))
    twin.pack = pack
    twin.max_edge = max_edge
    return twin


def build_items(group: dict, source_root: Path, asset_root: Path) -> GroupReport:
    report = GroupReport(group_id=group["id"])
    sources = collect_sources(group, source_root)
    report.source_count = len(sources)
    report.source_bytes = sum(path.stat().st_size for path, _ in sources)

    copy_as = group.get("copyAs")
    per_category = group.get("coreVariantsPerCategory")
    hires_pack = group.get("hiresPack")
    hires_edge = int(group.get("hiresMaxEdge", 0))
    seen_per_category: dict[str, int] = {}

    for path, category in sources:
        stem = path.stem
        suffix = f".{copy_as}" if copy_as else ".webp"
        dest_dir = Path(group["dest"]) / category if category else Path(group["dest"])
        relative = (dest_dir / f"{stem}{suffix}").as_posix()

        core_slot = True
        if per_category is not None:
            used = seen_per_category.get(category, 0)
            core_slot = used < per_category
            seen_per_category[category] = used + 1

        item = Item(
            source=path,
            dest=asset_root / relative,
            relative_path=relative,
            media_id=expand(group["mediaId"], stem, category),
            display_name=title_from_stem(stem),
            category=expand(group["category"], stem, category),
            tags=[expand(tag, stem, category) for tag in group["tags"]],
            max_edge=int(group.get("maxEdge", 0)),
            quality=int(group.get("quality", 82)),
            copy_verbatim=bool(copy_as),
        )

        if core_slot:
            report.core.append(item)
        if hires_pack:
            # Every source file has a pack twin: core files get a higher-fidelity
            # replacement, non-core files exist only in the pack.
            report.packed.append(clone(item, hires_pack, hires_edge))

    return report


def encode(item: Item, out_path: Path, cache: dict, force: bool, dry_run: bool) -> None:
    """Writes one encoded file, recording its final dimensions and size on the item."""
    key = str(out_path)
    stamp = cache_stamp(item)
    if not force and out_path.is_file() and cache.get(key) == stamp:
        item.skipped = True
        with Image.open(out_path) as probe:
            item.width, item.height = probe.size
        item.byte_size = out_path.stat().st_size
        return

    with Image.open(item.source) as image:
        width, height = image.size
        if item.max_edge and max(width, height) > item.max_edge:
            scale = item.max_edge / max(width, height)
            width = max(1, round(width * scale))
            height = max(1, round(height * scale))

        if dry_run:
            item.width, item.height = width, height
            item.byte_size = item.source.stat().st_size if item.copy_verbatim else 0
            return

        out_path.parent.mkdir(parents=True, exist_ok=True)
        if item.copy_verbatim:
            shutil.copyfile(item.source, out_path)
        else:
            resized = image
            if (width, height) != image.size:
                resized = image.resize((width, height), Image.LANCZOS)
            mode = "RGBA" if "A" in resized.getbands() else "RGB"
            resized.convert(mode).save(out_path, format="WEBP", quality=item.quality, method=6)

    item.width, item.height = width, height
    item.byte_size = out_path.stat().st_size
    cache[key] = stamp


def human(size: float) -> str:
    value = float(size)
    for unit in ("B", "KB", "MB"):
        if value < 1024:
            return f"{value:,.1f} {unit}"
        value /= 1024
    return f"{value:,.1f} GB"


def write_packs(packs: dict, staged: dict[str, list[Item]], pack_out: Path, make_zip: bool) -> None:
    for pack_id, items in staged.items():
        meta = packs.get(pack_id, {})
        pack_dir = pack_out / pack_id
        manifest = {
            "schema": 1,
            "id": pack_id,
            "name": meta.get("name", pack_id),
            "version": meta.get("version", 1),
            "description": meta.get("description", ""),
            "byteSize": sum(item.byte_size for item in items),
            "items": [
                {
                    "mediaId": item.media_id,
                    "relativePath": item.relative_path,
                    "type": "image",
                    "width": item.width,
                    "height": item.height,
                    "displayName": item.display_name,
                    "category": item.category,
                    "tags": ",".join(item.tags + [f"pack:{pack_id}"]),
                }
                for item in items
            ],
        }
        pack_dir.mkdir(parents=True, exist_ok=True)
        body = json.dumps(manifest, indent=2)
        (pack_dir / "pack.json").write_text(body, encoding="utf-8")
        if not make_zip:
            continue
        zip_path = pack_out / f"weaverse-media-{pack_id}-v{manifest['version']}.zip"
        with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as archive:
            archive.writestr("pack.json", body)
            for item in items:
                archive.write(
                    pack_dir / "media" / item.relative_path,
                    f"media/{item.relative_path}",
                )
        print(f"  pack {pack_id}: {zip_path.name} ({human(zip_path.stat().st_size)})")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", default=str(REPO_ROOT / "tools" / "media-packs.json"))
    parser.add_argument("--dry-run", action="store_true", help="report only; write nothing")
    parser.add_argument("--force", action="store_true", help="re-encode even if unchanged")
    parser.add_argument("--only", default="", help="comma-separated group ids")
    parser.add_argument("--no-zip", action="store_true", help="stage packs but skip zipping")
    args = parser.parse_args()

    manifest_path = Path(args.manifest)
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    source_root = Path(manifest["sourceRoot"])
    asset_root = REPO_ROOT / manifest["assetRoot"]
    pack_out = REPO_ROOT / manifest["packOut"]
    wanted = {name.strip() for name in args.only.split(",") if name.strip()}

    if not source_root.is_dir():
        print(f"Source art not found: {source_root}")
        return 1

    cache_path = manifest_path.parent / CACHE_NAME
    cache = load_cache(cache_path)
    staged: dict[str, list[Item]] = {}
    total_core = total_packed = total_source = 0

    for group in manifest["groups"]:
        if wanted and group["id"] not in wanted:
            continue
        report = build_items(group, source_root, asset_root)
        for item in report.core:
            encode(item, item.dest, cache, args.force, args.dry_run)
        for item in report.packed:
            encode(item, pack_out / item.pack / "media" / item.relative_path,
                   cache, args.force, args.dry_run)
            staged.setdefault(item.pack, []).append(item)

        core_bytes = sum(item.byte_size for item in report.core)
        pack_bytes = sum(item.byte_size for item in report.packed)
        total_core += core_bytes
        total_packed += pack_bytes
        total_source += report.source_bytes
        reused = sum(1 for item in report.core + report.packed if item.skipped)
        line = (
            f"{report.group_id:<11} {report.source_count:>3} src {human(report.source_bytes):>10}"
            f"  ->  core {len(report.core):>3} {human(core_bytes):>10}"
            f"   pack {len(report.packed):>3} {human(pack_bytes):>10}"
        )
        print(line + (f"   [{reused} unchanged]" if reused else ""))

    print("-" * 92)
    print(
        f"{'TOTAL':<11}     {human(total_source):>10}  ->  core     {human(total_core):>10}"
        f"   pack     {human(total_packed):>10}"
    )

    if args.dry_run:
        print("\n(dry run - nothing written)")
        return 0

    write_packs(manifest.get("packs", {}), staged, pack_out, not args.no_zip)
    cache_path.write_text(json.dumps(cache, indent=2), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
