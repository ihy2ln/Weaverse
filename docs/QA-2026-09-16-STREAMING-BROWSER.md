# Streaming-style Android browsing — v1.4.28

Implemented on the existing Home/history checkout. The pre-existing Home, drawer-animation, manga, notes, and other unrelated edits were retained. No public release was published by this task.

## Delivery

- Signed APK: `S:/AI/Novel/Weaververse/Beta.Test.Build/weaverse-v1.4.28-streaming-browser-signed.apk`
- Android package: `com.ihy2ln.weaverse`
- Version: **1.4.28 (154)**. The latest published version at build time was 1.4.27 (153).
- APK SHA-256: `58044bbc672bcae10d9d3908d0818ece927ac48082ce7704e5c11298cf11595a`
- Signing certificate SHA-256: `f16db5088b05af8c941a0e23eb2364dab470dd4ae07c3023f858708f6451fc05` — matches the existing release certificate. APK Signature Scheme v2 verified.
- Screenshots: `S:/AI/Novel/Weaververse/captures/streaming-browser/files/streaming-*.png`

## Implemented behavior

- Dark, sans-serif browsing independent of editor/reader appearance. Featured title uses the most recently accessed available book, then newest addition. Intact covers, blurred dark backdrop, optional separately imported backdrop, and title-based missing-art placeholders.
- Home shows Books first, then the existing configured modes. Recent shelves retain the existing ten-item history. Empty modes remain reachable. Book recents retain long-press removal and history clearing.
- Books has Continue Reading, Continue Writing, My List, Recently Added, and populated genre shelves. Every shelf opens a searchable, genre-filtered grid; All books includes uncategorized titles. Create/import stay in Library actions.
- Book details provides explicit read/write actions, saved reader location, synopsis editing, persistent My List, and linked audio. More exposes artwork, metadata, export, duplication, and deletion. Individual scene/reference recordings are labelled linked audio, not audiobooks. Playback starts only on Play.
- Explicit saved routes retain origin, filters, vertical list position, and horizontal shelf position. One Home/Books/Search/My List bottom bar and a compact WeaverVerse/Modes header appear in browsing. The writing workspace keeps its existing controls without the extra shell header or browsing bottom bar.
- Room migration **25 → 26** adds `book_browsing`; existing **24 → 25** Home migration is retained. Synopsis/backdrop/list/read/write metadata is separate from manuscript documents. New metadata is included automatically in full database backups.
- Existing reader positions are respected. Merely opening details records access history but does not create reading or writing progress. New read activity is recorded by the reader's saved-position path; writing activity by the actual Write workspace.

## Verified

- **37 focused JVM tests passed**: browsing selection/filtering, old reader positions, independent activity, existing Home history, backup archives, and existing writing/composer/media behavior.
- Android device verification uses **MuMu, Android 15 / API 35**, 1440×2560 at 640 dpi (360-dp portrait), landscape, and 160% font scale.
- Genuine Compose screenshots cover populated Home/Books, details, empty library, missing covers/long titles, horizontal shelf scrolling, searchable results, My List, landscape, and larger text. Populated screenshots use an isolated QA fixture library with artwork already present in this repository. These fixtures are not added to production libraries.
- Actual AppShell test verifies reader and writer resume their distinct saved scenes, details do not create activity, browser navigation disappears inside the workspace, Back returns to the original filtered grid, activity recreation retains that filter, and manuscripts remain unchanged.
- Migration/full-backup test preserves books, formatted manuscript JSON, media rows/files, prompt drafts/candidates, home targets, reading settings, and all new browsing fields; exercises the existing `BackupManager.restoreFrom` into isolated storage.
- Existing Home/Novel tests cover mode navigation, note creation/history, drawer transitions, migration, prompt persistence, and legacy Home components. Campaign/storyboard functionality was not redesigned.
- Signed **1.4.27 → 1.4.28** update was tested with `adb install -r` in the emulator. A uniquely named book and the exact saved manuscript text survived; `firstInstallTime` remained unchanged. No uninstall or app-data clearing was used.
- `git diff --check` passes. Release assembly and signature verification pass.

## Verification limits

- No physical handset was connected. Emulator coverage does not certify every OEM keyboard, photo picker, font, or Android version.
- Cover/backdrop import uses the existing local-media importer; rendered artwork and missing-file behavior were tested, but a physical-device system photo-picker round trip was not performed.
- Local linked-audio playback was exercised with a short test WAV, including an older inconsistent media-type record. Arbitrary external audio formats were not exhaustively tested.
- Browse code has no AI/provider invocation and suppresses ambient background video. This was checked by code inspection and explicit-action tests; no paid provider request was used for verification.
- Other modes were reached and existing relevant tests rerun; this was not a complete retest of every RPG, manga-source, or storyboard feature.

## Evidence

Build/test logs and signed-upgrade before/after UI dumps are retained under `S:/AI/Novel/Weaververse/task-artifacts/`. The initial unrelated working-tree diff is retained under `task-artifacts/streaming-baseline/preexisting.patch`.

### Device-run accounting

23 distinct instrumentation checks passed across the focused runs (9 new browser/resume/persistence checks, 7 preserved Home component checks, 4 shell-navigation checks, and 3 Novel checks). A repeated, long consolidated batch caused an emulator system crash after numerous orientation/activity cycles. That batch is not reported as a clean pass. The note-history check was made explicitly portrait and passed independently after emulator recovery. The last image-loading adjustment was followed by focused browser rendering checks, not another long orientation stress batch.
