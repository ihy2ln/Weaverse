# Novel direct prompting v1.4.27 — verification

## Shipped

- One NovelWorkspaceScreen toolbar, with fixed Prompt and horizontally scrollable Undo/Redo, Format, Insert, Context, Write, Plan, Codex, Media and Workshop. Tools moved into More.
- Compact, initially hidden instruction composer; Custom, Continue, Rewrite, Expand, Shorten and Scene beat actions. Generate is explicit; Enter remains multiline input.
- Exact cursor insertion for paragraph targets, explicit selection replacement, source snapshot and stable block/scene guards. A changed passage rejects application and keeps the candidate for retargeting.
- Streaming/Stop; prior candidates retained on Retry; Original/Candidate phone tabs, Copy and Discard. Applying goes through the existing undo queue and captures an original revision.
- Prompt-library search, selection, instruction saving and template duplication. Generation renders the selected template instead of silently resolving a different default.
- Quick Codex search, detected/pinned labels, descriptions and local editing. Book Memory, Author's Note and existing style guide; book-specific provider/model override and output length.
- Preview and generation share request assembly. Dropped Codex entries are absent from both rendered Codex and the transmitted system content. Explicit instruction and selected target are retained. Capacity failures block rather than silently truncating required content.
- Room 23 → 24 adds novel_prompt_drafts and novel_writing_settings. Existing manuscript/media/settings tables remain intact. Full database backups include both new tables. Restored candidates never automatically send a request.
- No manga or roleplay feature files changed. The shared ContextBuilder fix and the Novel-only shell key routing are the only shared behavior changes.

## Verified

- 133 targeted JVM tests passed, zero failures/errors. Coverage includes selected-template fidelity, request inclusion/exclusion, required-content capacity blocking, cursor insertion with surrounding formatting, selected replacement, removed/changed anchors, beat omission, serialized candidate targets, mocked streaming/Stop/Retry, Novel model override, scene switching and ViewModel recreation without resending, plus existing document, Novel, context, export/import and backup regressions.
- Three MuMu instrumentation tests passed in com.ihy2ln.weaverse.mangaqa: existing reference migration/isolation, styled caret Parcel round trip, and additive writing-state migration plus database backup ZIP round trip. The new migration test checks old book/style/prose/reference records and new prompt/settings rows after restoration.
- Debug, Android test and R8 release builds passed. Signed APK v2 verified with existing local certificate SHA-256 f16db5088b05af8c941a0e23eb2364dab470dd4ae07c3023f858708f6451fc05.
- In-place QA package updates succeeded. Opened an existing novel; observed fixed Prompt and toolbar scrolling, prompt draft editing, and saved draft restoration after process restart and APK update. No uninstall or clear-data operation.
- Meaningful captures reviewed at 360-dp portrait width, landscape, and font scale 1.3. Full toolbar is visible in landscape; portrait scrolls. Larger text preserves one-line toolbar labels and fixed Prompt.

## Verification limits and remaining work

- No paid/live provider requests. Provider-specific vision handling, network errors and output quality remain untested; streaming behavior was mocked.
- Software keyboard was not visible on MuMu's secondary app display despite enabling its hardware-keyboard setting. The imePadding implementation is present, but keyboard-open layout is NOT a passing device check.
- Compare/Copy/Discard, candidate application/undo, picker return with an active prompt, full UI backup/restore and new guidance/library controls have not all received end-to-end device walkthroughs. Unit/instrumentation coverage does not replace that matrix.
- The landscape capture verifies chrome/composer orientation and toolbar reachability; long-paragraph scroll-position behavior across rotation needs further device coverage. The composer can use much of the short landscape viewport while open.
- Context tokens are estimates, including a conservative per-image allowance; provider tokenizers and image accounting differ. Unknown model capacity uses the existing fallback limit.
- Attachments currently use a single selected image. Model override accepts the existing provider/model reference, with searchable cached OpenRouter choices; it does not add new providers.
- Full backups include the new tables. Project ZIP/JSON exports retain their pre-existing scope and are not substitutes for full backups.

## Artifact

- Version 1.4.27, code 153, package com.ihy2ln.weaverse.
- Local: S:/AI/Novel/Weaververse/Beta.Test.Build/weaverse-v1.4.27-novel-prompting-signed.apk
- Size: 530739582 bytes.
- APK SHA-256: 60243eebc78c0d6e7f8d7a3b1a5d520fc44874e3071f7b0b2aca7f3a1cd72d5b
- Evidence: S:/AI/Novel/Weaververse/Beta.Test.Build/qa-v1.4.27. Only the explicitly named final portrait/restored-draft/landscape/large-font images are passing captures. No keyboard image is counted as keyboard proof.
