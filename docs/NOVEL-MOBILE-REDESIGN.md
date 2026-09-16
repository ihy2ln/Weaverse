# Mobile Novel workspace redesign

Status: first Android implementation delivered in v1.4.26; remaining work and verification limits are listed below. This is not a claim of full Novelcrafter/NovelAI parity.

## Implemented in v1.4.26

- Novel-only mobile shell: Write, Plan, Codex, Media and Workshop; scene picker and compact writing tools; Read/Review/export/settings remain reachable.
- Scene summary/POV/status editing, accessible sibling reorder, scene-specific Codex pins and a preview of assembled AI context before generation.
- Separate reference-only media relationships, novel-scoped gallery, reusable inline media, scene/Codex associations, reference captions/descriptions and unlink-without-deleting-files.
- Additive Room 22 → 23 migration for reference relationships. Existing manuscript documents and inline media remain in their original tables.
- Candidate text preview, explicit acceptance and resumable hidden AI sheets; protection against replacing changed/deleted passages with delayed output.
- Media insertion preserves adjacent prose; pending saves survive scene switches; summary updates no longer save stale manuscript copies.
- Illustrated, self-contained HTML export, escaped text/formatting, and explicit missing/unsupported-media notices. In-story images are bounded to 8 MB each / 32 MB total. Reference attachments are excluded. Full database-and-media backup remains the complete archival format.
- Cursor state uses a parcel-safe saver after MuMu exposed a crash when opening Android's document picker.

## Remaining work / verification limits

- Dedicated generation and variant comparison for artwork inside the Novel Media tab; current tab supports import and reuse. Existing scene-beat/image-to-prose workflows remain available.
- Project ZIP/JSON are not complete media archives; use full backup. Illustrated EPUB/DOCX and portable audio/video packaging are not implemented here.
- Hidden AI drafts survive sheet dismissal in the active scene, not process death or arbitrary scene switching.
- No paid provider request was made during this pass. Model-specific image-to-prose quality, cancellation and network failures still need credentialed end-to-end QA.
- Full Novelcrafter import → edit → backup → restore device walkthrough and landscape/keyboard accessibility matrix remain to be finished. Parser, document, revision and backup unit regressions passed; that is not a substitute for the entire matrix.
- No PC/browser implementation or manga/storyboard redesign in this change.

## Direction

Android/mobile first, primarily inspired by Novelcrafter's connected planning, Codex and scene-writing workflow, with NovelAI-inspired focused writing and explicit story context. Keep WeaverVerse identity, existing novels, imported manuscripts and media. No manga/storyboard redesign or PC/browser work in this scope.

Official references inspected:
- https://www.novelcrafter.com/ — connected Codex, planning, writing and workshop; series-wide knowledge.
- https://docs.novelai.net/en/text/editor/storysettings/ — model/preset controls, Memory, Author's Note and Lorebook quick access.

## Existing foundations inspected

- Novel Plan, Write, Read, Codex, Workshop and Review features already exist.
- WriteUiState includes selections, AI preview, context meter, undo/redo and scene revisions.
- WriteMediaOps and the document model support manuscript media blocks/stacks; WriteScreen exposes image/video and audio import.
- SceneMediaLibrary ranks existing image/video assets by scene/category/tags. It is not yet evidence of a complete novel-scoped media relationship system.
- Novelcrafter import and regression fixtures already exist.

Reuse these foundations and verify their behavior; do not assume every currently exposed control works.

## Mobile workspace

Primary destinations: Write, Plan, Codex, Media, Workshop. Read and revision comparison are accessible from Write. On phones only one main surface is visible at a time; supporting tools use dismissible sheets. Larger layouts may use a split pane.

Write defaults to the manuscript, with one compact header for chapter/scene, save status and navigation. A keyboard-aware action row exposes formatting, insert media, undo/redo and AI. Hide large prompts and configuration panels until requested. Preserve caret, scroll and draft when switching panels. Keep touch targets at least 48 dp and controls accessible with larger fonts and the keyboard open.

Plan uses book/chapter/scene cards with summaries, POV, status and linked references. Support accessible move-up/down controls in addition to drag reordering. Selecting a scene returns directly to its writing position.

Codex connects characters, places, items and lore to scenes and series. Relevant entries and manual pins appear in AI context with a clear explanation of what will be sent. Keep proposed AI facts separate from accepted story canon.

Workshop supports scene/book-aware brainstorming and outline development. Applying suggestions is explicit and reversible.

## Media integrated with the story

Two explicit roles:
1. In-story media: illustrations, maps, audio and supported video blocks anchored between prose blocks; visible in the reader.
2. Reference media: portraits, location art, mood boards and research attached to a scene or Codex entry, excluded from reading/export unless inserted deliberately.

Media selection supports import, reuse from this novel, and deliberate generation. Show captions, alt text, source/provenance, related scene/entry and usage locations. Store relationships by stable IDs, not guessed filename matches. Reuse assets rather than duplicating the original file. Distinguish removing a placement from deleting an asset.

Image-based writing uses selected attachments only and a compatible model; show a context preview before sending. Do not silently send the whole media library. No paid generation on opening a menu. Generated prose/art remains a candidate until accepted; originals and prior revisions are kept.

Audio/video are opt-in playback, not autoplay. Media layouts remain legible on phones. Text-only and illustrated exports must explicitly handle unsupported audio/video through links/posters or omission notices, never silently drop content.

## Build order

1. Mobile workspace and manuscript focus: navigation, keyboard behavior, scene picker, autosave visibility and existing undo/history integration.
2. Connected planner/Codex: scene metadata, quick-reference sheets, context selection and continuity links.
3. Integrated media: novel-scoped gallery, inline/reference role, scene/Codex associations, reader and export handling.
4. AI workflow: continue/rewrite/expand/shorten, scene beats, image-to-prose, candidate comparison, context diagnostics and failure/cancellation handling.
5. Regression/device verification and signed APK delivery.

## Data safety and acceptance

- Audit existing persistence before schema decisions; any needed migration is additive, tested and non-destructive.
- Existing novels, scene IDs, formatting, mentions, revisions and media survive save/reopen, process restart and backup/restore.
- Import a Novelcrafter fixture; verify outline, prose, Codex and media associations.
- Test caret/scroll restoration, typing during generation, undo/redo and safe application of a delayed AI result.
- Create a scene, attach a portrait as reference, insert an illustration into prose, read it, reorder the scene and confirm references remain correct.
- Test missing/corrupt media and unsupported provider capabilities with actionable errors.
- MuMu: small portrait, landscape, enlarged font and keyboard-open layouts. No hidden save controls or permanent panels crowding the manuscript.
- Run relevant Novel/document/media/revision/import tests, build/signature verification and in-place install without clearing data. Record untested provider-dependent operations honestly.
