# Manga-first reader/editor QA

## Scope

Combined manga-first presentation and translation-placement update, Android only. The user explicitly requested a GitHub release for this delivery, overriding the earlier local-only delivery preference. No novel-layout, PC harness or new database migration work was introduced by this redesign; previously pending source/editor changes remain part of this repository checkpoint.

## On-device checks

MuMu Android 15, ADB serial `127.0.0.1:16384`. Production package updated with `adb install -r`; no uninstall, data clear or deletion of user manga. Existing 25-page imported chapter and library remained accessible. Instrumentation uses the separate `.mangaqa` application ID, leaving `.textgame` and production data alone.

- Reading canvas: full 1440x2560 display, no app-owned header/footer until page tap. Image aspect ratio is retained, so a shorter page can leave black canvas below it.
- Tap reveals Back/title/Edit/More and Previous/count/Next. Editing shows one header and Undo/Redo/Text/Cleanup/AI.
- AI menu exposes all three operations and Page/Chapter scope; Run is explicit. No paid generation button was pressed during UI QA.
- Settings scroll exposes Save color guide, Reset and model refresh.
- Cleanup options open as a sheet. A temporary brush stroke triggers the unsaved-edit warning; Discard returns without saving the stroke or modifying the source.
- Landscape 2560x1440 and small 1280x2000 at font_scale 1.3 inspected. Original 1440x2560 size, font_scale 1.0, keyboard setting 0 and free rotation restored after testing.
- MuMu's accessibility dump clips landscape nodes to its original display width; actual compositor screenshots show the complete toolbar. Its software IME reports shown but is routed separately from the app display, so phone keyboard occlusion is not fully verified.

Local evidence (not included in the public release): `captures/manga-first/before-visible.png`, `before.xml`, `after-reading.png`, `after-reading.xml`, `after-editing.png`, `after-settings-save.png`, `after-landscape.png`, `after-small-large-font.png`. Do not use the initial `before.png`, which captured the wrong MuMu display.

## Automated checks

- Release compile/R8/package: pass.
- Full release unit run: 488 total, 484 passed, four failed.
- Existing failures: two in `DifficultyPresetsTest` expect Normal/old preset names; two in `RailTabDefaultsTest` expect the old workspace/tool list. `PresetsScreen.kt`, `AppNavigation.kt` and those test files have no diff against the starting commit `f961b2c`. They are outside the manga redesign and were not edited to hide failures.
- Focused lettering/compatibility/placement unit tests pass.
- Android `LetteringDeviceTest` and `OverlayGestureDeviceTest`: six tests covering shrink-only fit, separated passages, invalid rotation, narrow horizontal/vertical text, cleanup pixels, zoomed movement/side resizing, reading disabling manipulation and clearing selection handles.

## Remaining verification limits

No new paid end-to-end model call was performed. The screenshot-inspired passages are covered by deterministic geometry/rendering fixtures, not a claim of a fresh AI reproduction on the user's copyrighted page. Exact full-page export/save/reopen, review repair, every multi-touch configuration and physical-phone IME behavior are not exhaustively exercised in this run. Shared renderer and backward-compatible overlay serialization are covered separately. A future visual segmentation upgrade may still be needed for complex artwork behind captions.
