# Handoff — WV 10.3.0 (v1.4.89) and what sits on top of it

**Handoff date:** 2026-10-04<br>
**Application:** Weaverse / WeaverVerse<br>
**Android package:** `com.ihy2ln.weaverse` (debug build used for testing: `com.ihy2ln.weaverse.textgame`, "Weaverse Test")<br>
**Released version:** `1.4.89` (`versionCode 221`) = **WV 10.3.0**, on `main` at `b238f16`, GitHub release `weaverse-v1.4.89`<br>
**Newest local build:** `1.4.91` (`versionCode 223`) on `claude/wv-10-5-0`, not pushed<br>
**Purpose:** let the next session pick up without re-reading the transcript: branch layout, what WV 10.3.0 contains
(including the v1.4.85–1.4.88 work it absorbed), the PC-side setup, open problems, and the user's standing rules.

## 1. Branches, worktrees and builds

Repo `S:\AI\Novel\Weaververse\src\Weaverse` (GitHub `ihy2ln/Weaverse`). The main checkout is on an unrelated
branch (`feature/adams-haven-art-media-packs`); work happens in `.claude/worktrees/*`.

| Branch | Tip | Version | State |
| --- | --- | --- | --- |
| `main` | `b238f16` | 1.4.89 (WV 10.3.0) | released (GitHub `weaverse-v1.4.89`) |
| `claude/wv-cardgame` | `ef6f5e3` | 1.4.90 | **unmerged, unreleased** — Card Game (Unity) + MangaRaw source. Worktree `.claude/worktrees/wv-cardgame` (clean) |
| `claude/wv-10-5-0` | `83a7464` | 1.4.91 | **unmerged, unreleased** — on top of wv-cardgame: manga download progress bars. Worktree `.claude/worktrees/wv-10-1-0-6d87c4` |
| `claude/wv-10-3-0-fal-ollama` | `b238f16` | 1.4.89 | same as main; worktree can be cleaned up |
| `claude/wv-10-2-1` | `278bd05` | 1.4.83 | merged into main; stale worktree |
| (detached) `ba7e2ed` | — | — | `.claude/worktrees/objective-goldberg-81e6a6`, an older prompt-library line; ownership unknown, leave alone |

Merge order when releasing next: `claude/wv-10-5-0` already contains `claude/wv-cardgame`, so fast-forwarding `main`
to `83a7464` ships both (1.4.90 + 1.4.91). Check `git ls-remote origin refs/heads/main` first — other sessions push
here too.

APKs in `S:\AI\Novel\Weaververse\Beta.Test.Build\`:

| File | SHA-256 |
| --- | --- |
| `weaverse-v1.4.89.apk` (WV 10.3.0) | `754a67934dd4a884ae2ad86c6994ce8f8c42a54bcad053b4f014545e32797aa3` |
| `weaverse-v1.4.90.apk` | `a3a4c4b47743a7a9e43747792cceee751e2aef1c5d0862dd121d4750c5c4e72f` |
| `weaverse-v1.4.91.apk` | `8b257e4f3c445d5d16bce2b715e8b84fa2887d375a1a88fb4e0f7123ad090882` |

All signed with the same debug key (cert `f16db508…51fc05`), so each installs over the last. The user's phone
(Samsung S25+, adb serial `R5GL13YL0KE`, USB debugging on) last ran a 1.4.90 build.

### Build recipe

```bash
export GRADLE_OPTS="-Djavax.net.ssl.trustStore=S:/Android/local_cacerts -Djavax.net.ssl.trustStorePassword=changeit"
./gradlew :app:assembleDebug :app:testDebugUnitTest --console=plain "-Dorg.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8" -Pkotlin.daemon.jvmargs=-Xmx4g
./gradlew :desktop:stageDesktopDist   # Weaverse Desktop → desktop/build/package
```

- The truststore works around Norton's HTTPS interception; the 4 GB heaps stop the Kotlin daemon running out of memory.
- **Never commit or ship two private WIP files** that live untracked in the worktree:
  `app/src/main/res/xml/network_security_config.xml` (modified: trusts user CAs) and
  `app/src/main/java/com/ihy2ln/weaverse/feature/chatting/social/SignedInSites.kt` (untracked). Before a build,
  copy both to the session scratchpad, `git checkout` the XML and remove the .kt; restore both afterwards. They were
  wiped once by an outside reset and rebuilt from transcripts, so keep a backup copy.
- Since 1.4.90 the APK is **arm64-v8a only** (Unity). The Dev AVD runs it via ARM translation; the phone is the
  reliable test device.
- Tests are JUnit 5 (`org.junit.jupiter.api.*`); JUnit 4 imports compile but are silently not run.

## 2. What WV 10.3.0 contains

`main` at 1.4.89 includes everything from 1.4.85 onward. Details are in `BUILD_NOTES.md`; summary:

### 2.1 Mode backgrounds and focus videos (1.4.85)
- Every mode sits on a backdrop (`feature/shell/ModeBackdrop.kt`, drawn once in `AppShell`). It shows full on Home,
  as a "hero" on a mode's first page, and "quiet" (blurred, dimmed, videos paused) further in.
- Several backgrounds per mode with an optional 30 s slideshow: Settings → Appearance → Mode backgrounds
  (`ModeBackdrops.kt`, pref `mode_backdrops`).
- Six seamless 20 s focus videos ship in `assets/videos/ambient/`, made by `tools/render_ambient_videos.py`.

### 2.2 Manga Studio on the phone (1.4.85–1.4.86)
- **Online reading:** tapping a chapter reads it online. Edit, Translate and Color work straight from the online
  reader; `prepareForEditing` downloads the pages behind the scenes first.
- **Auto-save:** `MangaCopySaver` puts every finished edit, translation and coloring into `Pictures/Weaverse/<title>/`.
- **Force-close fixed:** `MangaAiForegroundService` used to be stopped before it reached the foreground, which killed
  the app on Android 12+. Stop is now a handshake.
- **Runs that stop instantly now say why:** the "Couldn't run — see why" chip opens the Status sheet.
- **Colorize fallback:** `MangaColorFallback` retries a page a provider declined on two other providers.
  `AllModelsFailed` puts each model's error into the summary.

### 2.3 PC harnesses through Weaverse Desktop (1.4.87, 1.4.89)
- **Bridge:** Weaverse Desktop's `AiBridge` (auth = the sync password as Bearer; jobs are polled) runs:
  - Claude Code (`claudecode/<model>`)
  - Codex/ChatGPT (`codex/<model>`)
  - Ollama (`ollama/<model>`, streamed; vision models marked)
  - ComfyUI picture workflows (`comfy/<workflow>`)
- **Built-in ComfyUI colorize workflow:** `qwen-image-edit` uses the user's Qwen Image 2.1 Edit. It keeps line art and
  lettering and takes about 4 min/page on the RTX 5070 Ti. Extra API-format workflows dropped into
  `data/comfy-workflows` become more models.
- **App side:** `ai/pc/PcHarness.kt`, Settings → **PC harnesses** (Check PC). The models are listed in Settings →
  Models and Manga Studio → AI settings.

### 2.4 fal.ai (1.4.89)
- **Refs:** `fal/<endpoint>` for pictures (queue API, `enable_safety_checker: false`) and `fal-llm/<model>` for text.
  The key is set in Settings → fal.ai.
- **Account has no credit:** errors read "fal.ai has no credit left". No real fal picture has been made yet.

### 2.5 The web version (1.4.88 + commit `10b9e13`, both in main)
- **What it is:** Weaverse Desktop serves it at `http://127.0.0.1:8787` (phones use `http://192.168.0.93:8787`).
  The pages live in `sync-core/src/main/resources/web/` (`index.html`, `app.css`, `app.js`, `manga.js`, `manga.css`).
- **Look:** the app's current design — Home over splash art, the same mode tabs, banner cards and backdrop rules.
  The Settings sheet shows the sync password (browser on the PC only) and PC harness status.
- **Manga Studio** is a port of the APK's Mihon-style screens, running the APK's own manga sources:
  - Library, Updates, History, Browse (Sources / Extensions / Migrate) and More.
  - Catalogs with paging and global search.
  - Title pages with newest-first chapters and a language filter.
  - A reader (long strip / LTR / RTL) and categories.
  - The phone's own library, read from the DB its last sync push left on the PC.
- **Shared module** `manga-core` (new Gradle module): the APK and Weaverse Desktop both build the manga sources
  from it. Android-only pieces stay in the app: extension manager, WebView `RenderedCatalogClient` (behind the
  `RenderedCatalog` interface), gallery accounts and the download repository.
- **Desktop manga API:** `/api/manga/*` in `desktop/.../MangaService.kt`, covering:
  - sources, catalog, search, details, chapters, pages
  - an image proxy with disk cache
  - library, categories, history and updates
  - storage in `data/manga/library.json`
- `10b9e13` has no BUILD_NOTES entry yet; this section is its record.

## 3. On top of WV 10.3.0 (unmerged)

- **1.4.90 (`claude/wv-cardgame`, another session):**
  - Adams Haven Card Game embedded via Unity as a Library; novel characters get AI card sets.
  - **MangaRaw** (mangaraw.ac) source with tile un-shuffling (`ScrambledPages`).
- **1.4.91 (`claude/wv-10-5-0`):**
  - Manga downloads show a 0–100% bar: "Queued" → "Downloading · 42%"; failed chapters show their error.
  - Online preparation of shuffled MangaRaw pages shows "Preparing pages · N%".
  - A plain message when a site's image server is down.
  - 652 unit tests pass. Not yet seen on a device.

## 4. PC-side setup (outside the repo)

| Thing | Where / state |
| --- | --- |
| Weaverse Desktop | `S:\AI\Novel\Weaververse\Desktop` (`START-DESKTOP.bat`, port 8787, config `data/sync-config.json`). Runs the jar from commit `10b9e13`, not 1.4.89; rebuild with `:desktop:stageDesktopDist` and copy `Weaverse.jar` in. |
| Old install folder | `S:\AI\Novel\Weaverse` is empty but was locked (probably an Explorer window). Delete it when free. |
| ComfyUI | `S:\AI\ComfyUI_windows_portable`, port 8188, RTX 5070 Ti 16 GB |
| Ollama | `S:\AI\Ollama\app`; models in `S:\AI\Harness\Ollama\.ollama\models` (qwen3.6, qwen3-vl-abliterated, NeuralDaredevil-8B, llama3.2-uncensored) |
| Claude Code CLI | installed (2.1.280) but **signed out**: run `claude` → `/login` |
| Codex CLI | **not installed**: `npm install -g @openai/codex`, then `codex login` |
| fal.ai | key saved on the phone; **no credit** |
| Phone sync | phone address field set to `http://192.168.0.93:8787` with the password; **Push to web not yet done** (needed for the web Library to show the phone's manga) |
| Emulators | Games AVD (`emulator-5556`, 1080x1920) for tests; Dev AVD (5554) is often in use by Adams Haven work. C: is nearly full, so new AVDs go on S:. |

## 5. Open problems and next steps

1. **MangaRaw images are down for everyone.**
   - Its CDN `img-cdn.stackpathcdn.app` (185.255.120.53) timed out from six countries on 2026-10-04, and
     mangaraw.ac's own reader showed no pages.
   - No app fix is possible; recheck later. Tile un-shuffling has never been checked on a real scrambled page.
   - Rawkuma works; its pages now come from `kuma.kyut.dev`.
2. **Coloring fan-service pages.** Every OpenRouter image model the phone tried refused them: Gemini 3.1 Flash Image,
   FLUX 2 Flex, Seedream 4.5, GPT-Image 2, Qwen-Image 3. Paths that work:
   - ComfyUI on the PC (works).
   - fal.ai with the safety checker off (needs credit).
   - Untested on OpenRouter: `x-ai/grok-imagine-image-2.0`.
3. **Translation on OpenRouter.** OpenRouter marks every OpenAI and Anthropic model as moderated, and that includes
   the app's default reader, GPT-5.6 Luna. Recommended instead:
   - `x-ai/grok-4.7` / `grok-4.3`
   - `qwen/qwen3-vl-235b-a22b-instruct`
   - `qwen/qwen3.8-flash`
   - `mistralai/mistral-medium-3-5`
   - Local Ollama `qwen3-vl-abliterated` (no filter at all)
4. **Web version gaps.** The user wants the web version to *be* the current APK's screens, so keep porting:
   - **Manga Studio:** downloads, Edit/Translate/Color from the reader, and Comix (needs a rendered page — use
     headless Brave/Chrome `--dump-dom` in `MangaService.plainRendered`).
   - **The phone's own web host** (`SyncCoordinator`) doesn't serve `/manga.js`, `/manga.css` or `/api/manga`.
   - **Other modes** (Novel, RPG, WeaverBrowser, Notes) are still simplified pages: port each from the APK's current
     screens. Games isn't shown on the web at all.
5. **Releasing.** Fast-forward `main` to `claude/wv-10-5-0`, then create GitHub release `weaverse-v1.4.91` with the
   Beta.Test.Build APK and its SHA. Only do this when the user asks.

## 6. The user's standing rules (learned this cycle)

- **The browser version must mirror the current APK, rebuilt as real web pages.** Do **not** stream or run the APK in
  the browser: it was built once, rejected (RAM, not what was asked) and deleted along with its `WeaverseStream` AVD.
- **Keep Weaverse files inside `S:\AI\Novel\Weaververse\`.** Never create a separate top-level folder; the Desktop
  install lives in `S:\AI\Novel\Weaververse\Desktop`.
- **Publishing:** commit when asked; release to `main` + GitHub when asked, with user-facing notes and the APK SHA.
- **The phone is the real test device.** Ask the user to unlock it rather than entering their PIN. Stop driving the
  phone if they start using it.
- **They want no content filter where possible.** Weaverse adds none; explain provider refusals plainly and route
  around them (ComfyUI, fal with the safety checker off, permissive models).
