# Checkpoint v1.4.17 — Manga translation, whiteout, movable English

## Current scope update — 2026-09-15

User direction: focus only on Android/mobile for now. Mobile AI processing remains
OpenRouter-based. PC/browser harness integration is deferred; do not resume it without
a new user request. Deliver local APKs, not GitHub releases, unless requested otherwise.

Future PC/browser work is recorded in [PC/browser harness backlog](PC-BROWSER-HARNESS-BACKLOG.md).
The remainder of this checkpoint describes an earlier session; its verification claims
do not establish that subsequent editor changes have been tested on a device.

Handoff for the next session. Working tree is **uncommitted** on top of `f961b2c`.

---

## 1. What this is

Reliable manga translation for imported pages: read the page with Vision, translate only the
non-English lettering, clean the source glyphs off the plate, place English as **movable layers**,
then verify the result with Vision and fail closed to **Needs review** if source text survives.

Part of this landed before this session as uncommitted work (the data model, script validation,
`PanelAi.verifyNoForeignText`, the Whiteout tool, background-aware glyph masking, container
resolution). This session built the missing half and fixed what device testing exposed.

---

## 2. State at a glance

| Area | State |
|---|---|
| Translate → clean → verify → retry → persist | **Verified on device, end to end** |
| English stored as editable overlays, original `mediaId` retained | **Verified** (read back from Room) |
| Contrast colours, cleanup bounds separate from placement | **Verified** |
| Existing English + artwork preserved | **Verified** — 0.00% pixel change |
| Dark-caption cleanup | **Verified**, one small residual (§5) |
| Whiteout region + brush, undo/redo | **Verified on device** |
| Layer drag + resize handle | **Verified on device** |
| Cold-start model resolution | **Verified on device** |
| **Text auto-fit + text-safe inset** | **Compiled + unit tested, NOT verified on device** ⚠ |

Unit suite: **462 tests, 4 pre-existing failures** (`DifficultyPresetsTest` ×2,
`RailTabDefaultsTest` ×2). Those live in unrelated packages and fail independently of this work —
do not treat them as regressions.

---

## 3. RESUME HERE — the interrupted step

The last change (auto-fit) is **unverified on a device**. That is the next task.

**What changed and why.** Reported symptom: translated text is placed in the right bubble but is
far too large and overflows it; the box shrinks, the text does not. Two causes:

1. `TextOverlayLayer` drew at a fixed `overlay.fontSizeSp` and **ignored `autoFit` entirely**.
   Fixed by `rememberFittedFontSize()` in
   [TextOverlayLayer.kt](../app/src/main/java/com/ihy2ln/weaverse/core/ui/components/TextOverlayLayer.kt)
   — measures with `rememberTextMeasurer` and binary-searches the largest size that fits the box.
   Compose BOM is 2024.12.01 (1.7.x), which has **no** `BasicText(autoSize=)`; do not reach for it.
2. The placement box was the balloon's bounding **rectangle**, but a balloon is an ellipse, so text
   filling the rectangle spills at the curved top and bottom. `placedEnglishRegions()` in
   `RoleplayChatViewModel` now insets to `TextSafeWidthFraction = 0.84f` /
   `TextSafeHeightFraction = 0.78f`.

**To verify**: run a translation on a page with a *small* bubble holding a *long* line (that is the
reported failure). A generator for exactly that page is in §7. Check the rendered page, not just
the stored overlay values. Both fractions are guesses that looked right on paper — expect to tune
them against a real page.

---

## 4. Where the code is

New, pure, unit-testable (no Android types — keep it that way):
- `feature/roleplay/chat/MangaTranslationPlan.kt` — foreign/English split, fixed cleanup bounds,
  residual merging for the retry, review drafts, run summary.

Modified:
- `core/media/ImageOps.kt` — glyph mask, container resolution, flat-container repaint, contrast
  colours, `typesetLayersOnCanvas` for export flatten.
- `core/text/DocumentModel.kt` — `TextOverlay` gained height, autoFit, alignment, stroke,
  sourceLanguage, cleanup bounds. **All defaulted; no migration** (covered by
  `TextOverlayCompatibilityTest`).
- `core/ui/components/TextOverlayLayer.kt` — auto-fit (§3).
- `core/media/StoryboardPageExporter.kt` — flattens manga layers on PNG export only.
- `feature/roleplay/chat/RoleplayChatViewModel.kt` — the pipeline: `renderTranslatedPage`,
  `placedEnglishRegions`, `compositeWithEnglish`, `markPageNeedsReview`, `visionModelRef` warm-up.
- `feature/roleplay/chat/PanelImageEditor.kt` — Whiteout tool, review highlight, layout fixes.
- `RoleplayChatDetailScreen.kt`, `ImportedMangaEditorScreen.kt`, `MangaSourceScreen.kt` — labels
  renamed to **Translate to English**, plus the wiring in §6.

New tests: `MangaTranslationPlanTest`, `MangaEnglishValidationTest`, `ImageOpsTextContainerTest`,
`TextOverlayCompatibilityTest`.

---

## 5. Known issues, open

1. **Caption notch.** A narrow white-on-black *vertical* caption keeps a small light notch at one
   bottom corner (~3% of that box). Source text is fully removed; this is residual damage. One
   Whiteout stroke clears it. The horizontal dark strip and all bubbles come out at **0% damage**.
2. **Auto-fit unverified** (§3).
3. **Eight test storyboard projects** (`test_chapter` … `test_chapter9`) are in the app library on
   the MuMu emulator. Delete them.
4. **The delivered APKs are stale.** `S:\AI\Novel\Weaververse\Beta.Test.Build\Weaverse-v1.4.17-manga-translate-{debug,release}.apk`
   were built at 20:32, **before** the auto-fit fix. Rebuild and replace them once §3 is verified.
5. **Release APK is signed with the debug key** — `app/build.gradle.kts` falls back when
   `KEYSTORE_PATH` is unset. Fine for testing, not store-signable.

---

## 6. Gotchas that cost time — read before changing cleanup

**Do not re-try these. They were measured and are worse.**

- **Background sampling is the whole ballgame.** Deciding "what is background" from the box
  *border* fails (a tight box's border *is* the lettering). Deciding it from a band *outside* the
  box also fails (when the box already covers the caption, the band is the page). Either mistake
  inverts the test and the caption itself gets treated as lettering. The rule that works, used by
  both `textGlyphMaskArgb` and `textContainerArgb`, is **the majority colour inside the box** —
  lettering is always the minority of a text region.
- **Rejected:** falling back to sampling the pixels *between* glyphs when no container ring exists.
  Measured **51% of the caption destroyed**. Reverted. If there is no ring, there is no trustworthy
  container colour — let it fall through to the inpaint.
- **Cleanup must never touch pixels outside the resolved container.** Detection boxes are padded and
  routinely overshoot; reconstructing out there drags the container colour into the artwork.
- Run-to-run deltas of a few percent are **noise** — the OCR box varies per run. Only act on large
  differences (20%+). Do not tune against a single sample.

**Reachability trap:** `Translate to English` had three call sites and two were dead —
`editorOnly` is never passed `true` anywhere, and `storyboardCreator` is always `true` for anything
opened from Projects. A CBZ/PDF import lands in Projects, so the feature was unreachable for the
import path the app advertises. It is now wired into the storyboard creator's **AI** tool group.
`ImportedMangaEditorScreen` is still only reachable from a *downloaded chapter*.

---

## 7. Test harness (MuMu)

MuMu is at `S:\AI\MumuPlayer`, ADB at `S:\AI\Android\platform-tools\adb.exe`. No emulator image
needed — MuMu exposes ADB directly. **No SDK install is required**; everything needed is present.

```bash
cd "S:/AI/MumuPlayer/MuMuPlayerGlobal/nx_main"
./MuMuManager.exe control -v 0 launch                      # start the VM
./MuMuManager.exe adb -v 0                                 # prints host/port (16384)
./MuMuManager.exe control -v 0 app launch -pkg com.ihy2ln.weaverse
```

Traps, all of which will bite again:
- **Display ids change on every app relaunch.** Resolve the logical id per run from
  `dumpsys window windows | grep -A1 weaverse/...MainActivity | grep mDisplayId`, and drive input
  with `input touchscreen -d <logical> tap x y`. The logical id does **not** map to the physical id
  used by `screencap -d`; pick the screenshot display by taking all of them and choosing the one
  with the most pixel variance.
- Git Bash mangles device paths. `export MSYS_NO_PATHCONV=1`.
- `adb push` fails on the long scratchpad path (Windows MAX_PATH). Stage via `C:/Users/<u>/AppData/Local/Temp`.
- `adb root` works (KernelSU present) — that is how to read
  `/data/data/com.ihy2ln.weaverse/{files/media,databases/weaverse.db}`. `adb unroot` after.

**Getting a page in:** generate a PNG, zip it as `.cbz`, push to `/sdcard/Download`, then
Novel ▾ → Storyboard → Library ▾ → Projects → **+ Storyboard** → *Import a whole manga…* → pick it.
Then the **AI** tool → **Translate to English**.

A page reproducing the reported sizing bug — a small bubble holding a long line:

```python
from PIL import Image, ImageDraw, ImageFont
import zipfile
im = Image.new('RGB', (1200, 1700), (245, 245, 242)); d = ImageDraw.Draw(im)
jp = ImageFont.truetype(r'C:\Windows\Fonts\msgothic.ttc', 54)
d.rectangle((40, 40, 1160, 600), outline=(20, 20, 20), width=6)
d.rectangle((46, 46, 1154, 594), fill=(150, 165, 185))
d.ellipse((150, 110, 560, 330), fill=(255, 255, 255), outline=(20, 20, 20), width=5)
d.text((215, 150), "私の国に", font=jp, fill=(10, 10, 10))
d.text((215, 225), "来ませんか", font=jp, fill=(10, 10, 10))
d.rectangle((90, 660, 250, 1090), fill=(0, 0, 0))          # vertical white-on-black caption
y = 690
for ch in "必ず戻ると":
    d.text((110, y), ch, font=jp, fill=(255, 255, 255)); y += 78
im.save('page.png')
z = zipfile.ZipFile('chapter.cbz', 'w'); z.write('page.png', '001.png'); z.close()
```

Add `d.text((800, 450), "CRASH!", font=arial_bold, fill=(20, 20, 20))` to assert that existing
English is preserved — it must produce **no** translation layer and **0.00%** pixel change.

**Measuring a result** beats eyeballing it. Pull the newest `.png` (cleaned plate) and `.jpg`
(original) from `files/media`, then per region count `leftover` (was light, still light) and
`damaged` (was dark, now light). Read back overlays from `weaverse.db` → `rp_messages.contentJson`
and assert `originalMediaId` is retained, `placement != cleanup`, and fill is `#FFFFFF` on dark
containers / `#000000` on light ones.

---

## 8. Cost and privacy

Each translation run is 3 OpenRouter calls (vision read, translate, verify). Nine runs this
session. The key is validated and on the paid tier.

⚠ **The OpenRouter API key was printed into this session's transcript** while reading the settings
screen. It was not sent anywhere, but rotate it if that transcript is shared.

---

## 9. Suggested commit split

Nothing is committed. The tree mixes the pre-session uncommitted work with this session's:

1. Model + AI layer (`DocumentModel`, `PanelAi`, `RoleplayChatUiState`) — the pre-session work.
2. `MangaTranslationPlan` + pipeline (`RoleplayChatViewModel`) + its tests.
3. `ImageOps` cleanup/container/contrast + `ImageOpsTextContainerTest`.
4. Editor + UI (`PanelImageEditor`, `TextOverlayLayer`, screens, labels, wiring).
5. Version bump to 1.4.17 / 143.
