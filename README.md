# Weaverse

Novel writing (Novelcrafter-style) and SillyTavern-style roleplay — **Android**,
**Windows desktop**, and **web**, with **Wi-Fi / remote sync** between them.

**Android package:** `com.ihy2ln.weaverse`
**Repo:** [github.com/ihy2ln/weaverse](https://github.com/ihy2ln/weaverse)

Offline-first: everything except AI text generation works with the network off.
No account, no login, no cloud sync — sync is peer-to-peer over your own Wi-Fi
or a tunnel you control, with a one-time password issued by the desktop/web hub.

Status: actively developed — see [BUILD_NOTES.md](BUILD_NOTES.md) for the
working log, and [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for a from-the-
ground-up description of how the app is built (detailed enough to rebuild it
from scratch if this repo ever disappeared).

## Downloads

| Platform | Where |
|----------|-------|
| **Android APK** | [GitHub Releases](https://github.com/ihy2ln/weaverse/releases), or the `weaverse-debug-apk` artifact on the latest [Build Action](https://github.com/ihy2ln/weaverse/actions/workflows/build.yml) run |
| **Windows desktop + web** | `Weaverse-Desktop-*.zip` on [GitHub Releases](https://github.com/ihy2ln/weaverse/releases) |
| **PC package (source)** | [`Weaverse/`](Weaverse/) — run `INSTALL-TO-S.ps1` to install locally |

## Platforms

### Android (full editor)
Novel · Roleplay · Notes · Library · Prompts · OpenRouter AI · speech-to-text
**Settings → Sync** — start a host or Push/Pull to a desktop/web peer.

### Windows desktop (sync host + companion)
Double-click `Weaverse.exe` (or `START-DESKTOP.bat`). Needs Java 17+ unless
you use the full Windows portable zip. Hosts the sync API and opens the web
UI. Data lives in `Weaverse/data/` (or wherever you installed the package).

### Web (Novelcrafter-style hub)
Plan · Write · Chat · Review · Roleplay · Notes, with a manuscript rail and
Codex. Same Wi-Fi: `http://<pc-ip>:8787` · Remote: tunnel port **8787**.

## Sync (through the web version — stays connected)

1. Start the **desktop EXE** — it opens the **web hub**.
2. The web page shows the **single password**.
3. On Android: **Settings → Open web sync** → enter that password once.
4. Leave **Auto-sync on** — the phone Push/Pulls in the background.
5. After a Pull the app reloads so the new library is live.

## Import a Novelcrafter ZIP

Success check: drop `Weaverse/import/isekai-gacha-full-word.zip` (or any full
export with `novel.docx`/`novel.md` + `characters/`) into:

- **Android** — top-bar **Import** / **Export** (novels, roleplay, notes).
- **Web / desktop** — **Import** / **Export** on the hub, or place the file
  in `Weaverse/import/` and start the EXE.

Codex folders land in Characters / Locations / Objects / Lore. Characters
also become Roleplay cards with Messenger, DM, and manga chats.

## What's in this app

### Novel
- **Plan · Write · Chat · Review** with a manuscript rail; **Codex** and
  **Notes** are shared across every book and mode.
- Scene documents with media blocks.

### Roleplay — three separate workspaces
| Mode | Layout |
|------|--------|
| **Messenger** | Color-coded chat bubbles + inline media |
| **DM** | Invisible **3×3** snap canvas |
| **Roleplay** | Invisible **6×6** manga canvas |

### Notes
- Shared board — same notes in Novel, Roleplay, and Notes mode.
- Mic speech-to-text.

### Prompts & AI
- **`/`** AI prompt · **`\`** manual entry · OpenRouter provider.
- Compact **PROMPT** dock; **Models** picks any OpenRouter text model.

## Building locally

**Requirements:** JDK 17, Android SDK 35 (only needed for the Android
target — `:sync-core` and `:desktop` are pure JVM).

```bash
./gradlew assembleDebug                    # Android APK
./gradlew :desktop:packageDesktopZip       # Windows/desktop bundle
./gradlew :sync-core:test                  # shared sync-protocol tests
```

- Android APK → `app/build/outputs/apk/debug/`
- Desktop zip → `releases/desktop/Weaverse-Desktop-v1.2.5-beta.zip`

To build a release APK signed with your own key, set the `KEYSTORE_PATH` /
`KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` environment variables (CI
instead decodes a base64 keystore from repo secrets — see
`.github/workflows/release.yml`), then run `./gradlew assembleRelease`.
Without a keystore, the release build type falls back to debug signing so it
still installs.

## Tech stack

Kotlin · Jetpack Compose (Material 3) · Navigation Compose · Hilt ·
Room + FTS4 · DataStore · Jetpack Security · Ktor (client + embedded server,
SSE streaming) · Coil 3 · Media3/ExoPlayer · kotlinx.serialization ·
JUnit 5 + Turbine (app), a Go launcher for the Windows `.exe` shim.

## Project layout

- `app/` — the Android app: `com.ihy2ln.weaverse` (`ai/`, `core/`, `data/`,
  `feature/novel/`, `feature/roleplay/`, `di/`, …)
- `sync-core/` — pure-Kotlin/JVM module shared by `app` and `desktop`: the
  sync package format and protocol logic.
- `desktop/` — Windows/desktop companion: an embedded Ktor server (the "web
  hub") plus a tiny Go launcher (`desktop/launcher/`) compiled to
  `Weaverse.exe`.
- `Weaverse/` — the distributable PC package (scripts, install helper,
  sample import, not the built binaries — those come from Releases).

## Docs

- Build notes / working log: [BUILD_NOTES.md](BUILD_NOTES.md)
- **Rebuild documentation** (architecture, data model, protocols — detailed
  enough to reconstruct this app from scratch): [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
- Same content, wiki-formatted: the [repo Wiki](https://github.com/ihy2ln/weaverse/wiki)
- Desktop package: [Weaverse/README.md](Weaverse/README.md)
