# Translated lettering and manga-source repair — 2026-09-15

## Implemented

- Text-body dragging commits the final position. Eight selected-only handles resize width, height, or both while anchoring the opposite edge. Rotation-aware bounds calculations constrain movement/resizing to the page.
- The page editor now uses the same overlay gesture surface as the storyboard page. Double-tap opens text settings; an accessibility action also opens editing.
- Horizontal and upright vertical-column writing, separate rotation, font size, line spacing, padding, alignment, and auto-fit are persisted. Manual overflow gets a red underline. A shared Android lettering renderer handles preview and export.
- Removed automatic whole-rectangle reconstruction from translation cleanup. Source cleanup coordinates stay independent of translated-text placement.
- Clean tool provides a draft add/subtract mask, preview, mask undo/redo, sampled-color fill, masked repair, and original-image restoration within the mask. Rebuild from original requires confirmation and only changes the editor preview until Save copy. New manually added text is excluded from source cleanup.
- Legacy extension adapter initial Browse now calls the actual extension popular/latest operation instead of inheriting an empty default.
- Atsumaru uses its current public catalog, search, metadata, chapter, and reader endpoints.
- Comix embedded catalog data is parsed. Comix/MangaFire JavaScript-only catalogs use the site's normal rendered WebView. Current search routes replace obsolete paths. Chapter lists can use rendered pages.
- Source browser is user-controlled and shares WebView cookies with catalog requests. No verification solving or fabricated access tokens.

## Verification evidence

- MuMu serial: `127.0.0.1:16384`.
- `LetteringDeviceTest`: 2 passes, including both writing directions in a narrow tall box, clipping/overflow, and unchanged pixels outside a repair mask.
- `OverlayGestureDeviceTest`: 1 pass. Actual Compose pointer injection at 125% zoom verifies committed body movement, independent width resizing, anchored opposite edge, unchanged height and cleanup bounds.
- Live Atsumaru: popular/latest/search/detail/chapter list/image-page retrieval passed.
- Live Comix: popular/latest/title-matching search/detail/chapter-list retrieval passed.
- Live MangaFire: popular/latest/title-matching search/detail/chapter-list retrieval passed.
- Live MangaDot: blocked by browser-verification interstitial, not reported as successful empty results.
- Focused source/geometry/serialization/inpaint unit tests passed. Full suite: 469 tests, 465 pass, 4 failures in `DifficultyPresetsTest` and `RailTabDefaultsTest` (same test names seen before these changes; unrelated files were not changed for this work).
- Final debug compilation/package and release compilation/minification/signing succeeded. Final signed release was installed with `adb install -r`, without uninstall/data clearing. The existing library, offline 20-page chapter, translated page, selected-only handles, and text settings dialog were reopened visually in the production app.
- Artifact: `S:/AI/Novel/Weaververse/Beta.Test.Build/weaverse-v1.4.17-lettering-sources-20260915-signed.apk`.
- Installed production APK certificate and build certificate both have SHA-256 `f16db5088b05af8c941a0e23eb2364dab470dd4ae07c3023f858708f6451fc05`.
- User screenshot and a frame extracted at 00:10 of the supplied recording were inspected. The old installed page shows the reported smearing; saved damaged images are not silently reconstructed.

## Remaining acceptance gaps (not claimed complete)

- MangaDot needs user-completed verification. Open source website using the globe button, complete the challenge, close, and retry.
- No live extension-store URL was supplied and no shared Tachiyomi extension packages were present in MuMu. The legacy browse bridge has a deterministic regression test, not a completed real-APK compatibility certification.
- Comix/MangaFire complete chapter pagination and chapter image downloads are not covered by the live checks above. The generic image importer is not a guarantee for JavaScript-only reader pages.
- All permutations of overlapping/rotated tiny boxes, saved export pixel equivalence, persistence after a complete emulator restart, and mask behavior on every real artwork style have not been exhaustively device-tested.
- Original-image recovery is available only when the original file resolves at matching decoded dimensions. Masked original restoration is brush-mask-then-apply, not a dedicated continuous restore-brush mode.
- The broader Mihon parity plan (all tracking services, fixtures, Shizuku certification, full backup/migration acceptance) remains incomplete.

Existing unrelated worktree changes were preserved; no checkout/reset/clean or app-data clearing was performed.
