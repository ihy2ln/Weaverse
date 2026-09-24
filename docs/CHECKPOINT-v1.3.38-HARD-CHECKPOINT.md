# Hard Checkpoint — v1.3.38 Storyboard AI: Panel Separation, Picture Editor, Translation

**Checkpoint date:** 2026-08-30<br>
**Application:** Weaverse / WeaverVerse<br>
**Android package:** `com.ihy2ln.weaverse`<br>
**Version:** `1.3.38-beta` (`versionCode 84`)<br>
**Previous checkpoint:** `CHECKPOINT-v1.3.32-HARD-CHECKPOINT.md` (docs/ and root)<br>
**Purpose:** preserve a complete recovery point after the Storyboard AI work —
comic page import with AI/offline panel separation, the full-screen picture
editor (erase speech text), and Vision-model text reading + translation.

## 1. What is preserved

The complete editable source tree, Gradle wrapper, documentation, wiki pages,
and the latest installable APKs in
`S:\AI\Novel\Weaververse\Beta.Test.Build`:

| File | Meaning |
| --- | --- |
| `weaverse-v1.3.37-beta-dock-cluster-local.apk` | unified dock cluster on every prompt bar |
| `weaverse-v1.3.38-beta-storyboard-ai-local.apk` | this checkpoint's build |

User documentation at checkpoint time:

- `S:\AI\Novel\Weaververse\Weaverse-Wiki-Manual.md` — standalone markdown
  manual (updated for Storyboard AI).
- In-app wiki — `feature/help/WikiContent.kt` + `WikiScreen.kt` (Settings →
  Help → "Open Wiki manual"); Storyboard page documents the new tools.
- `docs/CHECKPOINT-v1.3.32-HARD-CHECKPOINT.md` — prior checkpoint covering
  Discord Chatting, Brainstorm, campaign options, codex activation, the
  unified prompt bar, and sub-categories.

## 2. Feature state at checkpoint

### 2.1 Comic page import + panel separation

- Any image attached to a storyboard page (existing **+** media flow) can be
  separated: long-press the panel → **Picture tools** →
  **Separate panels (AI)** or **Separate panels (offline)**.
- `core/media/ImageOps.kt` — the app's first bitmap toolkit:
  `loadBitmap` (downsampled decode), `crop`, `eraseCircle`, `eraseRect`,
  `toPngBytes`/`toJpegBytes`, and `detectPanelsByGutters` (recursive
  near-white gutter splitting on a 200px thumbnail; falls back to a single
  full-page box).
- `feature/roleplay/chat/PanelAi.kt` — Vision helpers:
  `detectPanels` (JSON boxes on a 0–1000 scale) and `readText` (per-region
  original + translation). Both send a ≤1100px JPEG as an OpenRouter
  `image_url` data URL via `AiGenerationService.complete`, parse a strict
  JSON array, and return null on any failure so callers can fall back.
- `RoleplayChatViewModel.separatePanels(messageId, blockId, useAi)`:
  boxes → `ImageOps.crop` each (0.6% inset) → `importFromBytes` per crop →
  new `RpPageMeta` ("Panels ×N") with the cropped panels as one new
  `RpMessageEntity`, each `MediaBlock` placed by `withGridPlacement` from its
  box geometry on the 12×12 grid. Vision model choice:
  `visionModelRef()` = first available `supportsImages` model, else the
  settings default if it supports images.
- Status surfaces on the canvas above the page strip
  (`storyboardStatus` in UiState).

### 2.2 Picture editor

- `feature/roleplay/chat/PanelImageEditor.kt` — full-screen editor opened
  from **Picture tools → Edit picture** (`imageEditor: PanelEditorUi?` in
  UiState; the screen `return`s early while open).
- Tools: **Brush** erase (round tip, size 8–90, fill color = bubble white /
  ink black / page cream), **Rect** erase (drag), **Undo** (8-deep bitmap
  copies), **Save** (`importFromBytes` PNG → the block's `mediaId` is
  re-pointed at the new entity, so Coil cache cannot serve the stale image).
- AI: **Read & translate** calls `PanelAi.readText` with a target language
  typed inline (default English). Regions are listed (original + translation,
  checkboxes) and highlighted on the picture: tapping a highlighted region
  erases it immediately; **Erase found text** erases all;
  **Add translations** erases selected regions and adds one
  `TextOverlay(style = SpeechBubble)` per region at the box center via
  `applyTranslatedRegions` (white bubble, dark text, width/size derived from
  the box).
- New `MediaEditAction.EditImage / SeparatePanels / SeparatePanelsAuto` with
  `MediaEditPopupConfig.showPictureTools`; Notes/Write dispatch them as no-ops.

### 2.3 Carried from v1.3.33–37 (see prior checkpoint for detail)

- Discord-style Chatting (servers = novels/campaigns, auto rooms, @mentions,
  DMs), Brainstorm chat with main/sub categories (+ `chat_threads
  .parentThreadId`, DB v14) and the Notes board sub-mode, campaign options
  editing, codex activation + clickable mentions everywhere, one
  `UnifiedPromptBar` on every generation surface with the full dock cluster
  (+ media / 🎲 / 🎤 dictation, ✓ hold-menu retry/continue, ⌫ hold-undo),
  profile theme art, in-app wiki manual.

## 3. Data model

No schema change in this checkpoint (still DB v14). New media produced by
cropping/erasing flows through the existing `media` table via
`MediaRepository.importFromBytes`; translated text lives in
`MediaBlock.overlays` as `TextOverlay` rows.

## 4. Key files (delta since v1.3.32 checkpoint)

```
core/media/ImageOps.kt                              bitmap decode/crop/erase/encode + gutter detection
feature/roleplay/chat/PanelAi.kt                    Vision panel boxes + text read/translate
feature/roleplay/chat/PanelImageEditor.kt           full-screen picture editor
feature/roleplay/chat/RoleplayChatViewModel.kt      + separatePanels, openImageEditor, editorFindText,
                                                    applyTranslatedRegions, saveEditedPanel, visionModelRef
feature/roleplay/chat/RoleplayChatUiState.kt        + PanelEditorUi, imageEditor, storyboardStatus
core/ui/components/MediaEditPopup.kt                + EditImage/SeparatePanels(+Auto) actions
feature/roleplay/chat/RoleplayChatDetailScreen.kt   editor hosting, popup config, canvas status line
feature/help/WikiContent.kt                         Storyboard page documents the new tools
app/build.gradle.kts                                versionCode 84, versionName 1.3.38-beta
```

## 5. Known gaps / next candidates

- v1.3.39/40 addendum: whole manga file import (PDF/CBZ/long-strip via
  `MangaFileImporter`), an **Add pages** button on the canvas, press-and-hold
  panel → **Add picture / video** (`MediaEditAction.AddMedia`), an
  **Image generation** model tab in Settings → Models
  (`ModelInfo.generatesImages`), the **MCP & CLI harnesses** settings block +
  `/mcp` JSON-RPC endpoint on the web-hub host with read-only library tools
  (`core/mcp/McpTools.kt`; auth = sync password Bearer), and a modernized
  Appearance profile card picker. Verified launch + monkey on emulator.
- v1.3.41 addendum: **+ Storyboard** offers Create new **or** Import
  (file picker → editable title → create with all pages); long-pressing an
  empty layout slot opens a menu with **Add picture / video** (auto-placed
  into free slots).
- v1.3.44 addendum: **AI capture-sort** — long-press an adventure message →
  "AI sort into Codex / Roster / Inventory…" runs `AdventureCapture.plan`
  (cloud model splits the text into character-sheet facts / inventory items
  with carriers / codex lore with suggested categories, flagging uncertain
  rows) and shows a sectioned review dialog; "Place selected" merges
  characters into the roster **with a linked codex entry** (Characters
  category, defaultCodexId, text seeded), files items into the named
  carrier's inventory, and writes lore blobs as codex entries
  (`ensureCategory`/`ensureCodexEntry`).

- AI panel/text detection quality depends entirely on the chosen Vision
  model; the gutter heuristic struggles with dark art or borderless panels.
- Erasing fills with a flat color — no content-aware inpainting; complex
  backgrounds need manual brush work.
- Cropped panels are placed by nearest grid cell; fine art layout may need a
  nudge (drag) or a template re-apply.
- Region boxes are one-shot: re-running "Read & translate" replaces the
  previous list.
- Speech-bubble overlay text does not auto-fit the erased box; font size is
  derived from box height only.

## 6. Resume state

1. Source root: `S:\AI\Novel\Weaververse\src\Weaverse` (modules `app`,
   `sync-core`, `desktop`).
2. Read `BUILD_NOTES.md` (v1.3.25 → v1.3.38), this file, and
   `docs/CHECKPOINT-v1.3.32-HARD-CHECKPOINT.md` for the intermediate state.
3. Wiki lives in three places that move together:
   `Weaverse-Wiki-Manual.md`, `feature/help/WikiContent.kt`,
   `docs/GUIDE.md`.
4. Beta artifacts: `weaverse-v<version>-<topic>-local.apk` in
   `Beta.Test.Build`; build via `gradlew :app:assembleDebug` +
   `scripts/copy-beta-build.ps1 -Name <name>.apk`.
5. Emulator at `emulator-5554` (adb at `S:\Android\platform-tools\adb.exe`)
   — verify with install + logcat crash buffer + monkey before shipping.
