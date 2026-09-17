# Compact manga reader/editor follow-up — local v1.4.19

## Changes

- Focus mode retains Back and a bottom page counter. Tap the manga to restore navigation; Focus collapses it again.
- READ uses black bars and white text; EDIT uses white bars and black text. The active mode has an explicit label when menus are expanded.
- Imported chapter pages share one lazy vertical canvas in both modes. Page selection follows the most visible page while scrolling, using cached metadata rather than querying all images on each swipe.
- Page labels sit below each page on an opaque contrasting strip. A compact numbered page grid remains available for jumping.
- More and manual-tool sheets are capped near 27% of display height, including the small drag handle. Contents scroll inside the sheet while its heading/Close control stays visible.
- AI action/scope controls occupy two rows; settings, panel separation and review share a compact secondary row. AI/settings sheets never exceed approximately 75% of display height.
- Manual tools are under Edit beside AI: text, cleanup, original/edited, zoom reset, offline panel separation and PNG export. Undo/Redo opens a combined menu, including in the detailed editor.
- Cleanup opens direct color swatches, a custom hex field, size slider and size presets. Eyedropper is optional and does not recolor a selected translation when being used for cleanup.
- In Edit mode, tap text to select it before dragging to move; unselected text allows a swipe to scroll the chapter.

## Verification

- Signed release compilation/R8 packaging and nine targeted lettering/serialization unit tests pass.
- Eight MuMu instrumentation tests pass: direct palette/custom hex/size changes without eyedropper, scrolling over unselected text, selected movement/resizing at zoom, read-mode interaction isolation, lettering fit, rotation/overlap checks and cleanup pixel isolation.
- MuMu UI checks: swiped between pages 1/2 in Focus/Read and pages 2/3 in Edit without Previous/Next; navigation expansion and mode switching preserved visible page position; compact More and AI grouping inspected; cleanup Black changed the field to `#000000`, and the 2% preset changed brush size from 5% to 2% without applying any image edit.
- Production updates use `adb install -r`, never uninstall or clear-data. Existing library and saved edits retained. Test APK uses `.mangaqa`, separate from production and `.textgame`.
- Local evidence: `captures/compact-reader/`. Candidate screenshots precede the final drag-handle height adjustment; final screenshots are named `final-*`.

No paid generation, new cleanup application, database migration, provider change or GitHub publication was performed for this follow-up. This UI change does not reconstruct or overwrite previously damaged image versions. Physical-phone keyboard behavior and every chapter size/orientation combination are not exhaustively tested.
