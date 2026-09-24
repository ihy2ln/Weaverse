# Hard Checkpoint — v1.3.32 Discord Chatting, Brainstorm, Codex Everywhere

**Checkpoint date:** 2026-08-29<br>
**Application:** Weaverse / WeaverVerse<br>
**Android package:** `com.ihy2ln.weaverse`<br>
**Version:** `1.3.32-beta` (`versionCode 78`)<br>
**Previous checkpoint:** `CHECKPOINT-v1.3.27-AI-TOPIC-MEDIA.md` (root) / `docs/CHECKPOINT-v1.3.26-PROMPT-TEMPLATES.md`<br>
**Purpose:** preserve a complete recovery point after the Chatting-mode Discord
redesign, the Brainstorm workspace, campaign options editing, and codex
activation across modes, plus the first full wiki manual (in-app and on disk).

## 1. What is preserved

This checkpoint preserves the complete editable source tree, Gradle wrapper,
project documentation, wiki pages, and the most recent installable APKs in
`S:\AI\Novel\Weaververse\Beta.Test.Build`. Generated build output and
superseded APKs are not part of the durable checkpoint.

Release artifacts at checkpoint time:

| File | Meaning |
| --- | --- |
| `weaverse-v1.3.27-beta-ai-topic-media-local.apk` | prior checkpoint build |
| `weaverse-v1.3.28-beta-discord-chat-local.apk` | Discord Chatting debut |
| `weaverse-v1.3.29-beta-discord-chat-local.apk` | single composer, auto rooms, ⌫ undo |
| `weaverse-v1.3.30-beta-rpg-brainstorm-local.apk` | campaign options, codex in RPG, Brainstorm |
| `weaverse-v1.3.31-beta-codex-back-local.apk` | codex ‹ Back header |
| `weaverse-v1.3.32-beta-wiki-local.apk` | this checkpoint's build (in-app wiki refresh) |

User documentation at checkpoint time:

- `Weaverse-Wiki-Manual.md` (repo root one level up) — the full standalone
  markdown manual for the computer.
- In-app guide — `feature/help/HelpContent.kt` (searchable sections), shown by
  `HelpScreen.kt`; content mirrors this manual.
- `docs/GUIDE.md`, `docs/ARCHITECTURE.md`, `docs/wiki/*`, `BUILD_NOTES.md`.

## 2. Feature state at checkpoint

### 2.1 Chatting mode — Discord-style (v1.3.28–29)

- New package `feature/chatting/`: `DiscordChatScreen.kt`,
  `DiscordChatViewModel.kt`, `ChatRoomSeeder.kt`,
  `CharacterPickerViewModel.kt`.
- The sub-mode destinations stay `Chats` and `Friends` (enum names persisted;
  `Friends` label renamed from "Contacts").
- **Server rail:** every `BookEntity` with `workType` `novel|campaign` is a
  server icon; the `⌂` slot is Home (direct messages). Selection state lives
  in AppShell (`chatServerId`, rooms reuse `selectedRpChatId`).
- **Rooms are `RpChatEntity` rows:** `displayMode = "messenger"`,
  `bookId` = the work, new column `roomKind`:
  `""` legacy messenger (reads as a DM), `"channel"`, `"character"`, `"dm"`.
  Room identity: campaign/storyboard chats (`dungeonMaster`/`roleplay`)
  are untouched and still open through RPG and Storyboard.
- **Auto-seeding:** `ChatRoomSeeder` (singleton, in-memory guard per book)
  creates `#general/#lore/#brainstorm` plus character rooms from the campaign
  setup note (`roster:<id>` entries) and codex-linked characters
  (`defaultCodexId` → codex entry with `scopeId == bookId`). Called from
  `AppShellViewModel.createWork` (novels/campaigns at creation) and
  defensively from `DiscordChatViewModel` on every books emission.
- **Generation:** channels talk to an AI narrator scoped to the book's title/
  genre/POV/tense/style guide; character rooms/DMs use
  `RoleplayPromptBuilder` for the bound character; `@Name` mentions (full or
  unambiguous first name) inject mentioned character cards and prefer
  `"Name: line"` replies. Replies persist as `RpMessageEntity`
  (`displayMode = "messenger"`, `speakerCharacterId` on single-voice replies).
- **Composer features:** W min–max word range (drives the prompt instruction,
  token budget, and `PromptWordLimit.trim`), `/A` ↔ `\M` AI/manual toggle,
  ✓ send with hold-menu (↻ retry deletes the last AI reply and regenerates,
  » continue without inserting a user message), × cancel while streaming,
  live `context: used / limit` meter, and the ⌫ clear with hold-to-undo.
- **Friends** creates DM-kind chats (`roomKind = "dm"`); legacy messenger
  chats (`roomKind = ""`, no `bookId`) surface as DMs with unread badges and
  previews (`RoleplayDao.observeDmChats`).
- The shared prompt dock is **disabled in Chatting** (`PromptSurface
  .usesGlobalOverlay` returns false; AppShell's `activeWritingDestination`
  likewise) — one generation surface per mode.

### 2.2 Campaign options editing (v1.3.30)

- `CampaignOptionsDialog.kt` (feature/roleplay/chat) reopens the campaign
  creation form for an existing campaign: setting template + details, main
  character multi-select (persona/roster/codex options), play-as role,
  narrative POV, tense, rules system, house rules.
- `RoleplayChatViewModel.beginCampaignSetup()` parses the stored setup note
  back into `NewWorkDetails` (labels → template ids, `House rules:` block
  extracted). `applyCampaignSetup(details)` rebuilds the note in the exact
  `createCampaignSession` format, syncs `inParty` flags, converts persona
  options via `ensurePlayerSheet` (mirrored from AppShellViewModel), updates
  `title`/`personaId`/`authorsNote` and the book's `title/genre/tense/
  styleGuide`.
- Entry point: **Setup** button on `AdventurePlayScreen`'s header row, always
  visible (even during adventure startup).

### 2.3 Codex activation and links (v1.3.30–31)

- **RPG prompts:** `RoleplayChatViewModel.generate()` runs `ContextBuilder`
  over all codex entries with `scanText = last 6 history messages + userText`
  (`maxContextTokens = 6000`, reserve = reply budget); the resulting
  `codexBlock` joins `extraSystem`. This is lorebook-style activation
  (trackMentions name/alias matching + alwaysInclude).
- **Clickable mentions:** `CodexMentionText` composable (AdventurePlayScreen)
  renders story prose with `findCodexMentions` + `CodexMentionTag` string
  annotations, intercepted at `PointerEventPass.Initial`; taps set
  `selectedCodexEntryId` via the new `onOpenCodexEntry` parameter. Novel
  Write already had this pipeline.
- **‹ Back:** the codex panel that opens from hyperlinks (Novel write, RPG
  adventure, search) has a `‹ Back` header (AppShell, v1.3.31) that clears
  `selectedCodexEntryId` and returns to the exact prior surface.
- Character/Persona detail screens already had back buttons.

### 2.4 Backspace clear with hold-to-undo (v1.3.29–30)

- `InkClearIconButton` is a backspace (`Icons.AutoMirrored.Filled.Backspace`)
  with `combinedClickable`: tap → `onClick` (delete), long-press → `onUndo`
  (restore). Callers stash the cleared text:
  - `GlobalPromptViewModel.clearText()/undoClearText()` (stash
    `lastClearedPromptText`).
  - `RoleplayChatViewModel.clearInput()/undoClearInput()`.
  - `DiscordChatViewModel.clearInput()/undoClearInput()`.
  - `WorkshopChatViewModel.clearInput()/undoClearInput()`.
  - `BrainstormChatViewModel.clearInput()/undoClearInput()`.
- `UnifiedPromptBar` grew `onUndoClear` (both clear-button sites);
  `ChatComposerRow`'s "Clear Text" capsule was replaced by the backspace
  button with `onUndoClear`.

### 2.5 Brainstorm/Notes (v1.3.30, wiki v1.3.32)

- `AppMode.Notes` label is **"Brainstorm/Notes"** (enum name `Notes` kept —
  persisted); `NotesDestination.Board` label is **"Brainstorm"**.
- Notes mode now hosts `feature/brainstorm/BrainstormChatScreen` +
  `BrainstormChatViewModel`: NovelCrafter-Chat-style AI conversation.
  Threads use `chat_threads.scopeId = "brainstorm"` (app-global, not
  book-scoped); messages in `chat_messages`.
- Features: thread rail (add/delete with confirm), user/AI bubbles,
  streaming, cancel, ✓ hold-menu retry (regenerate last reply), W word
  range, model picker (`PromptModelPickerDialog`), codex chips + picker via
  `ContextBuilder` (manual include/exclude + mention detection), preview
  prompt, usage line, context meter, ⌫ clear with hold-undo.
- System stem: `BrainstormChatViewModel.BRAIN_STEM` (brainstorming partner,
  no roleplay) + codex block + word-range instruction.
- The classic notes board survives under any mode's **Notes** rail tab
  (`NotesWorkspaceScreen` in AppShell is still used by `RailTab.Notes`).

### 2.6 In-app wiki (v1.3.32–33)

- `HelpContent.kt` updated: Chatting rewritten for the Discord redesign,
  new Brainstorm section, RPG Setup + codex-links entries, Novel codex-link
  note, and AI-section entries for the composer controls and ⌫ hold-undo.
- v1.3.33 added the full wiki manual: `WikiContent.kt` (twelve markdown
  pages) + `WikiScreen.kt` — a web-wiki-style reader (navigation sidebar,
  page search, breadcrumbs, tables, [[links]]) opened from Settings → Help →
  "Open Wiki manual" as a full-screen overlay. The compact quick guide stays
  beside it. Content mirrors `Weaverse-Wiki-Manual.md` on disk.
- v1.3.34 added schematic `{{figure:...}}` wireframes (Chatting, RPG, Novel,
  Brainstorm, prompt dock) drawn with theme colors.

### 2.8 One prompt bar everywhere, Brainstorm sub-categories (v1.3.36)

- Every generation surface now renders `UnifiedPromptBar`: RPG adventure,
  Novel write overlay, Novel workshop chat, RPG messenger
  (RoleplayChatDetailScreen), Chatting rooms (DiscordComposer wraps the bar;
  DiscordChatViewModel gained selectedModelRef/writingModels), and
  Brainstorm (which also gained an /A ↔ \M manual mode). Per-surface model
  pickers use `PromptModelPickerDialog`.
- Brainstorm sub-categories: `chat_threads.parentThreadId` (MIGRATION_13_14,
  DB v14; ChatThreadDto carries the field for export/import). The thread
  rail renders a parent→child tree (`buildThreadTree` in
  BrainstormChatViewModel); the **+** beside ⌫ on a main category adds a sub;
  deleting a parent cascades to its subs. Brainstorm/Notes mode has two
  sub-modes: Chat (default) and Board (notes).
- Extras row trimmed to Codex/Prompts/Pictures (`workspaceChromeTools`).
  Novel's side-rail tabs keep Manuscript/Notes/Snippets/Chats.
- RPG character selection in CreateWorkDialog and CampaignOptionsDialog:
  two-row scrollable chip area with a search field.
- v1.3.37 completed the dock cluster on every surface: + / 🎲 / 🎤 (media
  attach via MediaRepository + MediaBlock in the outgoing Document, d20 roll
  appended to the draft, dictation) plus » continue everywhere. Chatting,
  Brainstorm, and workshop panes render attached images inline
  (`mediaPaths` on their message UI models, resolved from MediaBlock ids);
  media-only messages are allowed (`hasPendingMedia` in state).

### 2.7 Appearance picker fix and theme art (v1.3.34–35)

- `InkHsvColorWheel` no longer re-seeds hue/sat/value from every persisted
  echo of its own selection (echo guard) — drags no longer snap, and picking
  a hue on an unsaturated/dark color now jumps to the full hue instead of
  collapsing to black.
- New ambient theme art per AppearanceProfile, drawn with Compose brushes in
  `core/ui/theme/Backgrounds.kt` (`ProfileBackgroundArt`). Shown behind the
  shell when no custom background image is set; toggle in Settings →
  Background media ("Theme art behind the app"), pref
  `profile_background_enabled` (default on). Shell chrome wash thins to ~86%
  alpha while the art shows; section opacity still controls depth.
- v1.3.35 crash fix: the art originally used `painterResource` on layer-list
  gradient drawables, which only supports vector/raster assets and threw
  IllegalArgumentException on first shell composition (startup force close).
  Do not load gradient layer-lists with painterResource; use the Compose
  brush renderer. Verified launch + monkey run on emulator.

## 3. Data model changes since the last checkpoint

| Migration | Change |
| --- | --- |
| `MIGRATION_12_13` (DB v13) | `rp_chats.roomKind TEXT NOT NULL DEFAULT ''` |

Schema state: `WeaverseDatabase` version **13**, entities unchanged otherwise;
`RoleplayDao` gained `observeRoomsForBook`, `observeDmChats`, `deleteChat`.
No further migrations pending. `fallbackToDestructiveMigration()` remains as
a last-resort safety net.

## 4. Key file map (delta since v1.3.27)

```
feature/chatting/DiscordChatScreen.kt        Discord workspace UI
feature/chatting/DiscordChatViewModel.kt     servers/rooms/DMs + room generation
feature/chatting/ChatRoomSeeder.kt           auto room seeding singleton
feature/chatting/CharacterPickerViewModel.kt + Characters room picker
feature/brainstorm/BrainstormChatScreen.kt   Notes-mode AI chat UI
feature/brainstorm/BrainstormChatViewModel.kt threads + brainstorm generation
feature/roleplay/chat/CampaignOptionsDialog.kt  campaign setup sheet (edit)
feature/roleplay/chat/RoleplayChatViewModel.kt  + beginCampaignSetup/applyCampaignSetup,
                                                + codex targets/context, + clearInput undo
feature/roleplay/chat/AdventurePlayScreen.kt    + Setup button, CodexMentionText,
                                                + onOpenCodexEntry, + onUndoClear
feature/shell/AppShell.kt                    Chatting→DiscordChatScreen, Notes→Brainstorm,
                                             chatServerId state, codex ‹ Back header,
                                             GlobalPromptOverlay off in Chatting
feature/prompt/PromptSurface.kt              Chatting excluded from the shared overlay
feature/prompt/UnifiedPromptBar.kt           + onUndoClear
feature/prompt/GlobalPromptViewModel.kt      + clearText/undoClearText stash
core/ui/components/InkButtons.kt             InkClearIconButton → ⌫ + hold-undo
core/ui/components/ChatComposerRow.kt        Clear Text capsule → ⌫ + hold-undo
feature/help/HelpContent.kt                  in-app wiki refresh
app/build.gradle.kts                         versionCode 78, versionName 1.3.32-beta
```

## 5. Build and release

- `versionCode 78`, `versionName "1.3.32-beta"`; debug label
  `Weaverse Test 1.3.32`.
- Debug APK: `.\gradlew.bat :app:assembleDebug` then
  `scripts/copy-beta-build.ps1 -Name <name>.apk` → copies to
  `S:\AI\Novel\Weaververse\Beta.Test.Build`.
- Compile checks: `.\gradlew.bat :app:compileDebugKotlin`.
- JDK 17; AGP/lint caveat from v1.3.26 still applies (release lint skipped).

## 6. Known gaps / next candidates

- Discord pane renders text plus a "media attachment" note; media blocks are
  not yet displayed inline.
- Room context does not yet pull codex lore into Chatting rooms (RPG and
  Brainstorm do).
- Reader/Review do not render codex mention links (Write and RPG adventure
  do).
- Discord server rail has no server-level unread badge.
- Legacy chats pre-dating `roomKind` read as DMs only when they have no
  `bookId`; group chats (`groupId`) are not shown in the Discord DM list.

## 7. Resume state

To pick up in a fresh session:

1. Source root: `S:\AI\Novel\Weaververse\src\Weaverse` (3 Gradle modules:
   `app`, `sync-core`, `desktop`).
2. Read `BUILD_NOTES.md` (v1.3.25 → v1.3.32 working log), this file, and
   `docs/ARCHITECTURE.md` for the module/data map.
3. The wiki manual lives in three places and should move together:
   `Weaverse-Wiki-Manual.md` (standalone), `feature/help/HelpContent.kt`
   (in-app), and `docs/GUIDE.md` (developer-facing guide).
4. Current beta APK naming convention:
   `weaverse-v<version>-<topic>-local.apk` in `Beta.Test.Build`.
