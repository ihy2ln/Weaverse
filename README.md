# Weaverse

A native Android app that pairs a Novelcrafter-style novel-writing workspace
(Plan / Write / Chat / Review, Codex, Snippets, Prompt library) with a
SillyTavern-style roleplay chat mode (characters, personas, codex,
sampler presets, real streaming 1:1 chats with swipe-cycled regeneration).
Both modes share one Room database, one AI provider layer, and one media
engine; a single pill in the top bar switches between them.

Offline-first: everything except AI text generation works with the network
off. No account, no login, no cloud sync, no telemetry.

Status: under active construction, phase by phase — see
[BUILD_NOTES.md](BUILD_NOTES.md) for what's done and what's next.

## Getting the APK

Every push to `main` builds a debug APK in GitHub Actions:

1. Open the repo's **Actions** tab → the latest **Build** run.
2. Download the `weaverse-debug-apk` artifact and unzip it to get
   `app-debug.apk`.

Tagged releases (`vX.Y.Z`) additionally publish a release APK to the repo's
**Releases** page via the **Release** workflow.

## Sideloading

1. Copy the APK to your phone (e.g. via a file share, USB, or a link).
2. On the phone: **Settings → Apps → Special app access → Install unknown
   apps**, and enable it for whichever app you used to open the file (Files,
   Chrome, etc.).
3. Open the APK file and confirm the install.

Weaverse is not signed with a Play-distributed key, so if you previously
installed a build with a different signature you'll need to uninstall the
old one first (`adb uninstall com.ihy2ln.weaverse` or Settings → Apps →
Weaverse → Uninstall).

## API keys

AI generation (workshop chats, scene beats, roleplay replies, text
replacements) needs at least one provider configured:

**Settings → AI Providers → add a connection profile** — pick Anthropic,
OpenAI-compatible (also covers OpenRouter, DeepSeek, Together, KoboldCpp,
Ollama, LM Studio, or any compatible endpoint via a custom base URL), or
Gemini, paste your API key, and tap **Test connection**.

Keys are stored on-device only, encrypted with Jetpack Security
(`EncryptedSharedPreferences`, AES-256-GCM) — never in plaintext, never
synced anywhere. With no key configured, AI surfaces return a clearly
labeled mock response instead of failing, so the rest of the app is fully
explorable without one.

## Backup and restore

**Settings → Export & Import → Export current book** writes a JSON file
(acts/chapters/scenes as plain text, plus the codex) to a location you
choose — a structural backup, not a full database/media dump. **Import a
book** reads one back in as a new book. Roleplay characters export/import
individually as PNG character cards (**Roleplay → Characters**), matching
the SillyTavern/Chub card format.

## Building locally

Requires JDK 17 and Gradle 8.9 (no `gradlew` wrapper is committed yet — see
**BUILD_NOTES.md → Gradle wrapper** for why, and run `gradle wrapper
--gradle-version 8.9` once locally to generate it if you want one).

```bash
gradle assembleDebug
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

To build a release APK signed with your own key, create
`keystore.properties` at the repo root (gitignored) or set the
`WEAVERSE_KEYSTORE_PATH` / `WEAVERSE_KEYSTORE_PASSWORD` /
`WEAVERSE_KEY_ALIAS` / `WEAVERSE_KEY_PASSWORD` environment variables, then
run `gradle assembleRelease`. Without either, the release build type falls
back to debug signing so it still installs.

## Tech stack

Kotlin 2.0 · Jetpack Compose (Material 3) · Navigation Compose · Hilt ·
Room + FTS4 · DataStore · Jetpack Security · Ktor Client (SSE streaming) ·
Coil 3 · Media3/ExoPlayer · kotlinx.serialization · JUnit 5 + Turbine.

## Project layout

See `app/src/main/java/com/ihy2ln/weaverse/` — `core/` (design system,
media, text, util), `data/` (Room, repositories, settings), `ai/`
(providers, prompt library, ContextBuilder, token budgeting), `feature/`
(the actual screens, split into `novel/`, `roleplay/`, `search/`, and
`settings/`), `di/` (Hilt modules).
