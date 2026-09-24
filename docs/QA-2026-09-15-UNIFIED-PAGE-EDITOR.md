# Unified mobile page editor — v1.4.20

## Interaction model

- Read replaces the separate Focus mode. It is non-editable, with Back/page count; a page tap reveals or hides navigation. Read retains continuous chapter scrolling.
- Edit opens the page editor, not a second overlay editor in the reader. It starts with Text/select, never an active paintbrush. The header is white and labeled EDIT; the reader is black.
- Text, Paint, Cleanup, Hand and Layers form one canvas toolbar. Color swatches and brush size are inline when painting/cleaning. Custom color and optional eyedropper remain available.
- Existing translated text and manually added text are loaded into the same layer collection. Text selection, movement, resize, formatting, addition and deletion all happen in the page editor. Manual layer source/style/background metadata are preserved.
- Review items live in Layers & review, alongside their text and Format action. Reviewed is explicit and undoable; Save still validates translation content.
- Undo and Redo are directly visible above the canvas. One bounded history captures image pixels, cleanup mask and lettering in chronological order. A new action clears redo; it does not overwrite the source image.
- Save creates an edited image version with separate editable text layers. Leaving with unsaved changes offers discard/keep editing. Read position is retained when returning.

## Checks

- Android debug compilation passed.
- MuMu: 12 instrumentation tests passed, including an actual paint gesture, immediate Undo restoring every pixel, Redo, moving an existing translation inside the page editor and undoing it, shared mask/text/image history, exact round-trip of untouched legacy overlays, and existing movement/lettering tests.
- Tests use generated images in the separate `.mangaqa` package; no user artwork is painted or saved by the test.
- No provider change, database migration, paid AI operation or GitHub publication.
- Signed release/R8 build passed; certificate matches the installed production package. MuMu was updated in place without uninstalling or clearing data. Live UI checks confirmed Read/Edit entry, default Text tool, inline paint/cleanup controls and the fitted canvas. Final screenshot: `captures/unified-editor/final-paint.png` (local/private).
- Local artifact: `Beta.Test.Build/weaverse-v1.4.20-unified-editor-signed.apk` (workspace root), SHA-256 `94f13335f2a6ecaccb1fde013462004b05fc4e0ad08272271e000eadc3aca5d8`.

This is a mobile editing workflow consolidation, not a full Photoshop feature implementation. Reader scrolling remains continuous; pixel edits are performed on the selected page so unsaved strokes cannot silently move to another page.
