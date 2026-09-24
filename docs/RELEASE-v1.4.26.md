# v1.4.26 — Mobile Novel workspace

First implementation of the mobile-first Novel redesign, not full Novelcrafter/NovelAI parity.

## Included

- Compact Write, Plan, Codex, Media and Workshop navigation.
- Scene metadata and ordering, scene-specific Codex pins, and AI context preview.
- Reference-only attachments separated from in-story illustrations; reuse media without duplicating originals.
- Candidate preview and explicit acceptance, plus protection against delayed AI output replacing newer edits.
- Prose-preserving media insertion, safer queued scene saves, and a fix for the Android file-picker crash involving styled cursor state.
- Illustrated HTML export with explicit notices for unavailable or unsupported media.
- Additive database migration from version 22 to 23; existing manuscript data is retained.

## Verification

- Release build and R8 completed; APK signature verified.
- 68 targeted unit tests and two MuMu instrumentation tests passed.
- MuMu reference import retested successfully after the file-picker crash fix.

## Known limits

- Artwork generation/variant comparison inside the Novel Media tab remains unfinished.
- Project ZIP/JSON are not complete media archives. Use the full database-and-media backup; illustrated EPUB/DOCX and portable audio/video export are not included.
- Hidden AI drafts persist within the active scene, not across process death or arbitrary scene changes.
- Complete keyboard/landscape, backup/restore and paid-provider end-to-end QA remains outstanding. No paid AI requests were made during this verification.

## Android download

Install `weaverse-v1.4.26-mobile-novel-signed.apk` as an update; do not uninstall or clear app data. It uses the same local signing certificate as the previous distributed APK. Back up your library first. If Android reports a signing mismatch, do not uninstall to bypass it.

Package: `com.ihy2ln.weaverse` · version code: `152`.

APK SHA-256: `facbd73e0c66fa503bf556d68154dd9a44bc0350292d9ff385f02202cddfbddf`

Android only; no new desktop build or manga/storyboard redesign in this release.
