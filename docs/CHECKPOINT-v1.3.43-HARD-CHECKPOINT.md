# Hard Checkpoint — v1.3.43 Codex Roster/Inventory Parity

**Checkpoint date:** 2026-08-30<br>
**Application:** Weaverse / WeaverVerse<br>
**Android package:** `com.ihy2ln.weaverse`<br>
**Version:** `1.3.43-beta` (`versionCode 89`)<br>
**Previous checkpoint:** `CHECKPOINT-v1.3.38-HARD-CHECKPOINT.md` (docs/ and root)<br>
**Purpose:** preserve a complete recovery point after the Codex was brought to
parity with the RPG Roster and Inventory system, plus the carried v1.3.42
state (cloud AI picture generation in the media flow).

## 1. What is preserved

The complete editable source tree, Gradle wrapper, documentation, wiki
pages, and the latest installable APKs in
`S:\AI\Novel\Weaververse\Beta.Test.Build`:

| File | Meaning |
| --- | --- |
| `weaverse-v1.3.42-beta-ai-media-local.apk` | AI-generated media in the add flow |
| `weaverse-v1.3.43-beta-codex-roster-local.apk` | this checkpoint's build |

User documentation: `S:\AI\Novel\Weaververse\Weaverse-Wiki-Manual.md`
(v1.3.43), the in-app wiki (`WikiContent.kt` / `WikiScreen.kt` — Codex page
documents the new sheet/inventory sections), `docs/GUIDE.md`,
`docs/ARCHITECTURE.md`, `BUILD_NOTES.md`, and the prior checkpoints
(`docs/CHECKPOINT-v1.3.38-HARD-CHECKPOINT.md` with v1.3.39–40 addenda,
`docs/CHECKPOINT-v1.3.32-HARD-CHECKPOINT.md`).

## 2. Feature state at checkpoint

### 2.1 Codex ↔ Roster/Inventory parity (v1.3.43)

- Schema: `codex_entries` gained `sheetJson TEXT NOT NULL DEFAULT '{}'` and
  `inventoryJson TEXT NOT NULL DEFAULT '[]'` (**MIGRATION_14_15**, DB v15,
  registered in DatabaseModule). Fields sit after `updatedAt` so the
  positional `CodexEntryDto.toEntity()` mapping in ProjectExportManager is
  unaffected (DTO fields intentionally not extended this checkpoint).
- `CodexRepository.updateEntry` gained `sheetJson`/`inventoryJson` nullable
  merge params (same semantics as `imageMediaId`).
- `CodexEntryDetailViewModel`:
  - Resolves the entry's category on load
    (`observeAllCategories().first()`) and flags `isCharacterCategory` when
    the category name is "Characters" (case-insensitive).
  - Decodes `RpgCharacterSheet` from `sheetJson` and `List<RpItem>` from
    `inventoryJson`; mutators `onSheet`, `adjustAbility`, `adjustHp`,
    `addItem`, `removeItem`, `toggleItemActive` mark unsaved; `save()`
    persists via the extended `updateEntry` and records undo/redo
    (`restoreCodexEntry` now restores sheet/inventory too).
- `CodexEntryDetailScreen`:
  - **Roster sheet** section (Characters-category entries): Class/Species
    text fields, Level and Armor-class and Proficiency-bonus steppers,
    HP −/current/max/+ row, six ability cards with computed modifiers
    (`abilityModifier`), attacks & actions notes.
  - **Inventory** section (every entry): "+ Item" opens a dialog (name,
    quantity, weight lb, cost gp, tags, notes) creating an `RpItem`;
    ledger rows show active ■/□ toggle, quantity, weight/cost/tags, notes,
    and Remove.
- The v1.3.42 AI-media work is included in this build: cloud image
  generation via OpenRouter image-output models
  (`OpenRouterRepository.generateImage` with `modalities:["image","text"]`,
  parsing `message.images[].image_url.url` data URLs;
  `AiGenerationService.generateImage`), reachable from press-and-hold
  panel menus (**Generate picture (AI)**) and empty-slot menus
  (**Add picture / video**, **Generate picture (AI)**).

### 2.2 Carried v1.3.39–41 state

- Whole manga/comic file import from **+ Storyboard** (import option →
  file picker → title → create; `MangaFileImporter` streams PDF pages via
  PdfRenderer, CBZ/ZIP image entries sorted naturally, long webtoon strips
  sliced 2:3 with region decoding; persisted progressively on IO).
- **Add pages** bulk-import button on the comic canvas; long-press on
  empty layout slots → Add picture/video (v1.3.41) + Generate picture (AI).
- Discord-style Chatting, Brainstorm chat with sub-categories, campaign
  options editing, codex mention links + lore activation, unified
  UnifiedPromptBar everywhere with the full dock cluster, profile theme
  art, in-app wiki manual.

## 3. Data model

| Migration | Change |
| --- | --- |
| `MIGRATION_13_14` (DB v14) | `chat_threads.parentThreadId TEXT` (Brainstorm sub-categories) |
| `MIGRATION_14_15` (DB v15) | `codex_entries.sheetJson TEXT DEFAULT '{}'`, `codex_entries.inventoryJson TEXT DEFAULT '[]'` |

DB version is **15**. `SyncSchema` lists tables/keys only (no column
enumeration), so no sync-core change was needed.

## 4. Key files (delta since the v1.3.38 checkpoint)

```
core/media/MangaFileImporter.kt              whole-file → page import (PDF/CBZ/strip)
feature/roleplay/chat/PanelAi.kt             vision panel boxes + text read/translate
feature/roleplay/chat/PanelImageEditor.kt    full-screen picture editor (erase/undo/save)
feature/roleplay/chat/ImageGenDialog.kt      cloud image generation dialog
feature/roleplay/chat/RoleplayChatViewModel.kt + separatePanels, image editor, image gen
feature/shell/AppShellViewModel.kt           + importPagesIntoChat (whole-file import)
feature/shell/AppShell.kt                    + Storyboard import chooser, codex ‹ Back,
                                             Notes-mode Brainstorm/Board split,
                                             prompt dock off in Chatting
core/mcp/McpTools.kt                         MCP JSON-RPC tool server (read-only)
data/sync/SyncCoordinator.kt                 + POST /mcp route (Bearer auth)
feature/novel/codex/CodexEntryDetailScreen.kt     + roster sheet + inventory editors
feature/novel/codex/CodexEntryDetailViewModel.kt  + sheet/items state & persistence
feature/prompt/UnifiedPromptBar.kt           + onUndoClear; InkClearIconButton ⌫
feature/chatting/*, feature/brainstorm/*     Discord workspace; Brainstorm chat
core/ui/theme/Backgrounds.kt                 per-profile ambient background art
feature/help/WikiContent.kt + WikiScreen.kt  in-app wiki (12 pages + figures)
```

## 5. Known gaps / next candidates

- Codex export DTO (`CodexEntryDto`) does not yet carry
  `sheetJson`/`inventoryJson` — exported backups round-trip entries without
  the new fields (defaults on re-import).
- Roster-sheet parity is editing-side; the AI prompt includes the entry
  text but not yet the structured sheet numbers.
- No persona/character-side join surface yet for the codex-side sheet (the
  character → codex link stays one-directional).
- Erasing in the picture editor fills flat color (no inpainting); AI
  panel/text detection quality depends on the chosen Vision model.
- Discord pane renders media as thumbnails only for images; audio/video
  show as attachment notes.

## 6. Resume state

1. Source root: `S:\AI\Novel\Weaververse\src\Weaverse` (modules `app`,
   `sync-core`, `desktop`).
2. Read `BUILD_NOTES.md` (v1.3.25 → v1.3.43), this file, and
   `docs/CHECKPOINT-v1.3.38-HARD-CHECKPOINT.md` for the intermediate state.
3. Wiki lives in three places that move together:
   `Weaverse-Wiki-Manual.md`, `feature/help/WikiContent.kt`,
   `docs/GUIDE.md`.
4. Beta artifacts: `weaverse-v<version>-<topic>-local.apk` in
   `Beta.Test.Build`; build via `gradlew :app:assembleDebug` +
   `scripts/copy-beta-build.ps1 -Name <name>.apk`.
5. Emulator verification: adb at `S:\Android\platform-tools\adb.exe`
   (device `emulator-5554`); install → launch → logcat crash buffer →
   monkey before shipping.
