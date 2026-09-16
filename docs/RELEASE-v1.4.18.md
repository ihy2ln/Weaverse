# Weaverse 1.4.18 — Manga-first reader and safer lettering

Android update for the existing `com.ihy2ln.weaverse` app. Install over the current app; do not uninstall or clear its data.

## Reader and editor

- Imported manga opens in reading mode with the outer app chrome hidden. Tap the page for navigation, then choose Edit.
- One compact editing toolbar: Undo, Redo, Text, Cleanup and AI. Page picker, Original/Edited, export, panel separation and status/review live in sheets.
- AI actions have explicit Page/Chapter scope and a Run button. Opening a menu does not start a paid processing job.
- Model selection, color presets and the color guide are in a separate scrollable settings sheet.
- The detailed page editor keeps its canvas visible and moves options into a sheet. Leaving unsaved bitmap edits asks before discarding them.
- Back clears a selected text box before leaving editing mode. Initial-page navigation is applied once, so later document updates do not reset the selected page.

## Translation placement

- Auto-fit shrinks from a preferred font size rather than growing short dialogue to fill a large box.
- Placement stays close to the original lettering. Separate passages retain separate placement areas and fixed cleanup masks.
- Shared rendering measures clipping, small text, passage overlap and transformed rotation bounds before accepting new translations. Unsafe results retain the original and enter review.
- Text tools offer local re-layout for the page or an individual translation. Wording and cleanup masks are retained; manually adjusted and legacy boxes are protected from automatic page-wide re-layout.

## Verification and limits

- Minified release build and APK signature verification; in-place MuMu installation with existing library data retained.
- MuMu checks: unobstructed reading, compact editing, AI/settings sheets, reachable Save color guide, cleanup discard protection, portrait, landscape and a smaller display at 130% font scale.
- Six Android regression tests pass for lettering fit, overlap/rotation validation, cleanup-mask pixel isolation, zoomed movement/resizing, read-mode interaction disabling and selection reset.
- Full JVM suite: 484 of 488 tests pass. Four existing failures concern roleplay difficulty names and global navigation defaults (`DifficultyPresetsTest`, `RailTabDefaultsTest`); those tests and their underlying preset/navigation definitions are unchanged. The focused manga regression suite passes.
- Live paid AI generation was not re-run for this release. Better placement is constrained and validated, not a guarantee that every model will detect every bubble correctly. Artwork avoidance still depends on detection quality and local safe-area estimates.
- MuMu routes its software keyboard separately; actual phone keyboard occlusion still needs a physical-device check. This is not a claim that every acceptance scenario or every provider has been live-tested.
- No PC/browser harness implementation in this update. Existing source/provider work already present in the working tree is retained.

Build: versionCode 144. Signed with the existing installation-compatible certificate (SHA-256 `f16db5088b05af8c941a0e23eb2364dab470dd4ae07c3023f858708f6451fc05`).
