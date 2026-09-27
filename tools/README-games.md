# Games mode: Adams Haven on the Godot runtime

Games mode runs the real Adams Haven game, not a port. Two pieces make that work:

| Piece | Where | Size |
|---|---|---|
| Godot 4.7.1 runtime | `app/libs/godot-lib-4.7.1.stable.template_release.aar`, inside the APK | ~50 MB AAR |
| The game (exported Godot project) | a game pack zip, imported on the device | ~1.3 GB |

The engine in the APK and the engine the pack was exported with **must be the same
version** (`GodotRuntime.ENGINE_VERSION`). A pack from another version is refused at import.

## The runtime AAR

Godot's `godot-lib.template_release.aar` from the 4.7.1 export templates
(`%APPDATA%/Godot/export_templates/4.7.1.stable/android_source.zip`, at
`libs/release/`), with the `x86` and `armeabi-v7a` engine libraries removed. `arm64-v8a`
is for phones, `x86_64` for the MuMu emulator, which would otherwise install Weaverse as
x86_64 and find no engine to load. Everything else in the AAR is left as it is.

To move to a new Godot version: rebuild the AAR the same way from the new templates, rename
it, update `app/build.gradle.kts`, `GodotRuntime.ENGINE_VERSION` and `ENGINE` in
`tools/build_game_pack.py`, and re-export the game with that Godot.

## Building a game pack

```bash
python tools/build_game_pack.py "S:/AI/Game/AHCG test builds/AdamsHaven-v0.15.0-Android.apk"
```

This reads an Adams Haven Android export and writes `build/game-packs/adams-haven-v<version>.zip`:
the APK's `assets/` as the zip root, stored uncompressed so Godot can seek in the videos,
plus a `weaverse-game.json` manifest. The source APK is never modified.

## Installing and updating it

- **From GitHub:** Games → **Check for updates**. When a newer release is available,
  **Download and install** fetches its Android APK, verifies the release SHA-256, and
  converts its Godot assets into a game pack. Keep Weaverse open during the download.
- **From a local file:** Games → **Import game** → pick an Adams Haven Android APK or
  a Weaverse game-pack ZIP. The APK's assets are converted on the device.
- Both routes install the pack at `Android/data/<package>/files/games/adams-haven.zip`.
- **With adb (testing):** push the zip straight to that path. The debug build's package is
  `com.ihy2ln.weaverse.textgame`.

```bash
adb push build/game-packs/adams-haven-v0.15.0.zip /sdcard/Android/data/com.ihy2ln.weaverse.textgame/files/games/adams-haven.zip
```

## How it runs

`AdamsHavenGameActivity` extends Godot's `GodotActivity` and runs in its own `:game`
process: Godot's engine starts once per process and kills the process when the game
quits, so this keeps Weaverse alive underneath. A small boot project inside Weaverse
mounts the imported pack before the game's autoloads start. `WeaverseApp` skips its
startup work (seeding, sync, backups) in that process. Game saves live in the app's own
storage (`user://`), apart from the pack, so updating or removing the pack keeps them.
