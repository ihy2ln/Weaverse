# Hard Checkpoint — v1.4.47 One Theme on Every Page

**Checkpoint date:** 2026-09-24<br>
**Application:** Weaverse / WeaverVerse<br>
**Android package:** `com.ihy2ln.weaverse` (debug: `com.ihy2ln.weaverse.textgame`)<br>
**Version:** `1.4.47` (`versionCode 179`)<br>
**Previous committed release:** `981204c` — Release 1.4.40<br>
**Purpose:** a complete recovery point after the appearance overhaul, and the first
commit of the 1.4.41–1.4.46 work that until now had only shipped as local APKs.

## 1. What is preserved

The full source tree, Gradle wrapper, Room schema export, documentation, wiki pages,
and the installable APK:

| File | Meaning |
| --- | --- |
| `S:\AI\Novel\Weaververse\Beta.Test.Build\weaverse-v1.4.47.apk` | this checkpoint's build (also attached to GitHub release `weaverse-v1.4.47`) |

APK SHA-256: `9a78d650f663fe1bd0f3cffae80c80f46e66cbac5d814080b581639e082c6ae4`.
Signed with the same debug key as 1.4.38–1.4.46 (cert SHA-256
`f16db508…6451fc05`), so it installs over them without losing data.

Docs: `BUILD_NOTES.md` (v1.4.47 entry), `Weaverse-Wiki-Manual.md` (regenerated
from `WikiContent.kt`), `docs/wiki/Appearance.md` (new), `docs/wiki/_Sidebar.md`,
`docs/wiki/Home.md`, `docs/wiki/Navigation-and-Shelves.md`.

## 2. Feature state

### 2.1 Appearance (v1.4.47)

- **One theme everywhere.** Home, Books, the manga hub, Mihon browse and the panel
  editor no longer pin WeaverVerse colours; `BookBrowsingTheme` is a pass-through.
  Non-WeaverVerse profiles build a full Material scheme with `colorSchemeFrom`, and
  every profile typography defines all 15 styles.
- **Font size / line height.** `ui_text_scale_percent` (80–140) scales
  `LocalDensity.fontScale` inside `WeaverseTheme`; `ui_line_spacing` (0.85–1.5)
  rewrites typography leading in `em` via `withLineSpacing`. Reader keeps
  `font_size_sp` / `line_height`. Never nest `WeaverseTheme` — a nested call would
  apply the text scale twice.
- **Wallpapers.** `BackdropStyle` + `AppBackdrop` in `core/ui/theme/SystemBackdrops.kt`:
  Theme art, Bloom, Bloom Dark, Glow, Bliss, Big Sur, Monterey, Ventura, Sonoma,
  Sequoia, all procedural. Pref `backdrop_style`; `profile_background_enabled=false`
  is "None". Shown behind every page including book browsing.
- **Glass.** `glass_clarity_percent` (0–80, default 30) → `LocalGlassClarity`.
  `Modifier.glassPanel()` / `glassFillAlpha()` in `core/ui/components/Glass.kt`;
  the shell wash alpha is `1 − clarity × 0.8`.
- **Settings.** Glass `ExpandableSection` cards with icons, live preview, profile
  cards drawn as miniatures of each profile (`ProfileCard`), wallpaper tiles,
  preset pills + sliders, Help & friends split into its own card.
- Tests: `AppearanceScalingTest` (line spacing, colour schemes, backdrop fallback).

### 2.2 Carried from 1.4.41–1.4.46 (first committed here)

- **Database safety.** Room `exportSchema = true` with schemas under `app/schemas`;
  `WeaverseDatabase.ALL_MIGRATIONS` is the single ordered list registered by
  `DatabaseModule`; `addColumnIfMissing` makes column adds safe to re-run after an
  interrupted upgrade. `MigrationChainTest` (unit) and `MigrationTest` (device).
- **Release signing guard.** `assembleRelease` no longer silently falls back to the
  debug key; pass `-PallowDebugSignedRelease=true` for a local build. The release
  workflow does this explicitly when no signing secrets exist.
- **Start save slots.** `StartSlotStore` keeps JSON slots beside the database: a
  "CYOA set up" checkpoint per book/campaign and a starting template per mode that
  jumps a new start straight to verification.
- **Manga editor.** Read / Edit switch always on screen; every version on one
  scrollable row; up to three clean-letter-verify passes that each reload the
  original; overflowing lines reworded shorter; a slow-quality Vision review of
  colorized pages with one targeted redo.
- Debug app label follows `versionName` (`Weaverse Test 1.4.47`).

## 3. Verification at checkpoint

- `./gradlew :app:assembleDebug :app:testDebugUnitTest --offline` — 599 tests, 0
  failures.
- On MuMuPlayer: Home and Settings checked under WeaverVerse, Classic (dark) and
  Arcade; Bloom Dark wallpaper; font size 130% then back to 100%.

## 4. Known gaps

- `BUILD_NOTES.md` has no per-version entries for 1.4.41–1.4.46; section 2.2 is the
  summary of that work.
- Wallpapers are drawn at full screen each frame they are visible; they are static,
  so this costs only on recomposition, but a cached bitmap would be cheaper.
- Glass is a translucent tint with a lit edge, not a real backdrop blur.
