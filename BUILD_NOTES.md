# BUILD_NOTES

Working log for Weaverse: decisions, deviations, known gaps, and a resume
state for picking this back up in a fresh session.

## v1.3.48-beta — ! commands write the entry and the prose at once

- Typing `!character`, `!location`, `!object`, `!lore` or `!other` (plus the
  usual synonyms: `!npc`, `!place`, `!item`, `!weapon`, `!event`, `!note`, …)
  followed by a brief runs one AI call that fills that kind's codex template,
  files the entry in the matching category, and drops the prose into whatever
  is open. `CodexBang` parses it; only a leading known keyword counts, so
  ordinary prose with an exclamation mark is never hijacked.
- **One to one by construction.** The model fills the template's *own field
  names* (pulled from the serializer descriptor, so the prompt can never drift
  from the sheet), and the prose is then rendered from the saved entry by
  `CodexEntryText` — the same fields, in sheet order, blank ones skipped. The
  text the writer receives is byte-for-byte the entry's `plainText`.
- `!character` also creates the roster character: sheet (class, species,
  level, HP, AC, ability scores), description, personality, and its starting
  gear as real inventory items.
- Wired into the global prompt dock (`GlobalPromptViewModel.submit`, so it
  works over the manuscript, notes and chats) and the RPG adventure composer
  (`RoleplayChatViewModel.send`, which posts the entry text into the scene).
  The prompt bar's meter chip turns into the keyword list while a `!` line is
  being typed.
- New `CodexRepository.ensureCategory(name)` creates the target category when
  a project does not have it yet.

## v1.3.47-beta — the ledger is named for what actually holds it

- A place has no inventory and neither does a sword, so the middle tab is now
  named per kind by `InventoryVocabulary` (`party/InventoryRules.kt`):
  **Character → Inventory**, **Location → Contents**, **Object/Item →
  Components**. Lore and Other still have no ledger tab.
- The words follow: add button (+ Item / + Part), dialog title, weight caption
  (WEIGHT CARRIED / WEIGHT STORED / TOTAL WEIGHT), the count noun, the search
  hint and the empty state.
- The body-shaped parts are gone for places and objects: no equipment plate
  (head/torso/weapon slots), no backpack panel or capacity fields, no item
  template picker, and the Equipment/Backpack filters are dropped — with a
  fallback so a filter carried over from a character's pack cannot leave a
  location's contents filtered by something it cannot have.
- One mapping drives all of it — `CodexEntryKind.ledgerVocabulary()` — so the
  tab strip, the plate's "Open sheet & contents ›" line, and the "which kinds
  can hold things" test all read from the same place.

## v1.3.46-beta — a template per codex kind

- The Roster sheet was only ever right for characters. Every codex entry now
  resolves to a **kind** (`CodexEntryKind`: Character, Location, Object/Item,
  Lore, Other) from its category name, overridable per entry, and each kind
  gets its own template drawn with the Roster sheet's furniture — framed
  picture, stat strip, expandable section cards (`CodexSheetScreen`).
- **Character** → the real RPG Roster sheet, unchanged.
- **Location** → Description / Placement / **Census** (population counter,
  demographics, notable residents, factions) / History & Lore / Points of
  Interest / Hooks & Secrets.
- **Object/Item** → Description & Appearance / Item Details (type, rarity,
  value, weight, attunement) / **Stats & Mechanics** (damage, to-hit, AC
  bonus, range, charges, save DC, properties, effects — revealed once the item
  has stats, and counted as statted the moment any mechanical field is filled)
  / History & Lore / Ownership.
- **Lore** → an Explanation section built for length (summary, in-depth
  explanation, further detail) plus Placement, Origins & Timeline,
  Connections, Beliefs & Secrets.
- **Other** → free sheet: description, details, background, connections, and
  fields the writer names themselves.
- Templates live in `codex_entries.sheetJson` as `CodexSheetData` — no
  migration; a pre-v1.3.45 `RpgCharacterSheet` in that column has none of the
  new keys and decodes to an empty sheet. Entries that only had `plainText`
  (including AI capture-sorted lore) open with that prose already seeded into
  the kind's body field.
- Each kind's body field is written back to `plainText` on save, so mention
  matching and AI context keep reading the same prose the sheet shows.
- Plates in every codex list now print that kind's chips — CLASS/HP/AC/GEAR,
  TYPE/SCALE/POP, TYPE/RARITY/VALUE/STATS, TYPE/ERA — with the template name
  as the type line.
- Roster characters are only created for kinds that can carry: characters on
  open, locations and objects when their Inventory tab is first used. Lore and
  Other never get one, and have no Inventory tab.

## v1.3.45-beta — the Codex *is* the Roster/Inventory

- **Every codex entry is now a roster character sheet.** Opening an entry
  creates (once) an `RpCharacterEntity` linked by `defaultCodexId`, seeded
  from the entry's name, text, v1.3.43 `sheetJson` and `inventoryJson`, and
  its first codex image as the portrait. `CodexRosterLink`
  (`feature/novel/codex/CodexRosterFormat.kt`) owns the link and the plate
  decoration for every codex surface.
- **Entry detail is the RPG UI, not a text form.** Three tabs: **Sheet** is
  the real `CharacterDetailScreen` (embedded, `showBackRow = false`, keyed
  per character), **Inventory** is the real `InventoryScreen` filtered to
  this entry's carrier (`carrierFilterId`, no group headers), and **Codex**
  keeps only what a sheet has no room for — aliases, "always include in
  context", and the extra picture/video/audio gallery.
- Name and entry text now live on the sheet: the sheet's name/description are
  mirrored back into the codex entry on every change, so mention matching,
  `@` links and AI context keep working off the same text.
- The adventure **Lorebook** list and the Codex rail both render the shared
  `CodexEntryPlate` — portrait, name, category type line, CLASS / HP / AC /
  GEAR chips, equipped line — so the three lists are one format.
- Codex-backed characters carry the `Codex` tag, stay `inParty = false` (so
  they never crowd the Roster team list) and show up in the global RPG
  Inventory under **Other**.

## v1.3.44-beta — AI capture-sort into Codex / Roster / Inventory

- Press-and-hold an adventure message → **AI sort into Codex / Roster /
  Inventory…**. A cloud model (`AdventureCapture.plan`) splits the selected
  text into sections: character-sheet facts, inventory items (with carrier),
  and codex lore with a suggested category. Rows the model is unsure about
  are flagged so the review dialog asks the user to re-route them.
- The review dialog groups candidates by destination section (Character
  sheet / Inventory / Codex); "Place selected" applies them: characters
  merge into the roster **with a linked codex entry** (Characters category,
  `defaultCodexId` set, entry text seeded from notes), items file into the
  named carrier's inventory (fallbacks: persona → main character → party),
  and lore becomes a codex entry in the suggested category
  (`ensureCategory`/`ensureCodexEntry` in AdventureCapture).
- Existing heuristic capture ("Add to roster"/"inventory") unchanged and
  also now links rostered characters to codex entries.

## v1.3.43-beta — codex roster/inventory parity

- Codex entries in a **Characters** category now carry the same roster sheet
  as the RPG Roster (class, species, level, HP with +/−, armor class,
  proficiency bonus, six ability scores with computed modifiers, attacks &
  actions), and **every entry** carries an RPG Inventory-style item ledger
  (quantity, weight, cost, tags, notes, active toggle, add/remove).
  Persisted in new `codex_entries.sheetJson` / `inventoryJson` columns
  (MIGRATION_14_15, DB v15), saved through `CodexRepository.updateEntry`,
  included in undo/redo, and editable in the codex entry detail screen.
- Verified on emulator: fresh launch + DB v14→15 migration + monkey, no
  crashes.
- Codex **list** now draws every entry the way the RPG Roster draws a party
  member: framed 88dp portrait (roster avatar, else the entry's first codex
  image, else a monogram), bold name in the entry tint, the category as the
  small-caps type line, two-line summary, and a CLASS / HP / AC stat strip
  read off the linked roster sheet (falling back to the entry's own
  `sheetJson`) plus a carried-item count from the inventory. Category
  headers use the Roster's letter-spaced small caps with a hairline rule.
  `CodexViewModel` now emits `CodexEntryUi` (entity + portrait + sheet
  labels + item count) instead of raw entities.
- Fixed compile breaks left in the roster-parity WIP: a stray brace in
  `CodexEntryDetailViewModel.load`, a missing `inkTokens()` in
  `CodexEntryDetailScreen`, and `AdventureCapture` (Flow vs suspend category
  lookup, `CodexCategoryEntity.createdAt` → `updatedAt`, missing
  `Document.toJson` import, undeclared `linkedCodexId`).

## v1.3.42-beta — AI-generated media in the add flow

- Press-and-hold a panel → **Generate picture (AI)**: a dialog to describe
  the picture and pick a cloud image model. Generation goes through
  OpenRouter (`modalities: ["image","text"]`, parsing
  `message.images[].image_url.url` data URLs via the new
  `OpenRouterRepository.generateImage` + `AiGenerationService.generateImage`).
  The generated picture is imported into media storage and attached to the
  active page like any imported media.
- Empty layout slots offer both **Add picture / video** and **Generate
  picture (AI)** on long-press.
- Settings → Models: **Image generation** tab listing every OpenRouter
  image-output model (`generatesImages` on ModelInfo, tagged in All).

## v1.3.41-beta — + Storyboard import option, empty-slot menu

- Pressing **+ Storyboard** now offers a choice: **Create new** (the
  existing dialog) or **Import a whole manga / comic / webtoon file** —
  the file picker (PDF, CBZ/ZIP, image, long strip) opens directly, a
  pre-filled title is editable, and Import creates the storyboard with
  every file page as its own full-page panel (streamed on IO).
- Long-pressing an **empty layout slot** on the comic canvas opens the
  media menu with **Add picture / video** — imported panels auto-place
  into free slots, so filling a template starts from any frame.
- Verified on emulator: launch + monkey, no crashes.

## v1.3.40-beta — generated-panel asset handoff

- Storyboard now has **Import generated panel**. It opens Android's document
  picker for one or multiple images and copies them through
  `MediaRepository.importFromUris` into durable app-private media storage.
- Empty layout frames are selectable. The first import uses the selected empty
  slot; otherwise imports fill the first free layout slots in picker order.
  Selecting occupied artwork opens a safe pre-import choice between the next
  free slot and explicit replacement. Overflow continues on a new page.
- Added the in-canvas Codex/ImageGen handoff hint and a host utility at
  `tools/stage-storyboard-assets.ps1`, with versioned, non-destructive staging,
  optional adb push, and `tools/README-storyboard-assets.md` instructions.
- Separator feedback now identifies AI versus offline white-gutter detection,
  reports unreadable/zero/one-panel results, documents the offline detector's
  near-white gutter limit, and explicitly preserves the source page while
  writing crops to a new page.
- Added focused JVM coverage for empty/occupied/full-page placement, clean
  multiple gutters, single-panel detection, unreadable detection input,
  natural archive ordering, and original-page/media preservation.
- Press-and-hold a panel → **Add picture / video** in the media menu
  (MediaEditAction.AddMedia → requestMediaPick) — imported media is
  auto-placed on the page.
- Settings → Models gains an **Image generation** tab listing OpenRouter
  text-to-image models (`OpenRouterModelDto.generatesImages()` /
  `ModelInfo.generatesImages`, tagged "Image generation").
- **MCP & CLI harnesses** (Settings → Sync): the web-hub Ktor host serves a
  minimal Model Context Protocol endpoint at `/mcp` (JSON-RPC 2.0:
  initialize / tools/list / tools/call / ping) with read-only library tools
  — list_works, list_scenes, read_scene, search_codex, read_codex_entry,
  list_notes, read_note (`core/mcp/McpTools.kt`). Auth is the sync pairing
  password as a Bearer token. The settings block shows the endpoint URL plus
  ready-to-copy connect commands for Claude Code, OpenCode, and Codex CLI.
- Appearance section modernized: profile picker is now a horizontally
  scrolling card row showing each profile's own palette swatches (light and
  dark preview) instead of a text pill.
- Version advanced to 1.3.40-beta (`versionCode 86`). No ComfyUI, cloud image
  generation, Android API key, networking, or database migration was added.

## v1.3.39-beta — whole manga and page import

- New Storyboard can now take an optional whole comic file: PDF, CBZ/ZIP,
  a normal page image, or one long webtoon strip. PDF pages, naturally-sorted
  archive entries, and webtoon slices become ordered storyboard pages.
- Import is streamed page-by-page on the IO dispatcher instead of retaining a
  whole volume in memory. Long strips use region decoding, and the initial
  empty placeholder page is removed when a book is imported at creation.
- The canvas has an explicit **Add pages** button. Its file picker accepts
  whole comic files and multiple individual images; imported pages append in
  order and the last new page opens automatically.
- Long-pressing the desired panel selects it and opens Picture tools, including
  AI or offline panel separation. The empty-page hint now teaches this gesture.
- Version advanced to 1.3.39-beta (`versionCode 85`).

## v1.3.38-beta — Storyboard AI: panels, picture editor, translation

- Comic/manga page import + panel separation: long-press an imported page →
  Picture tools → Separate panels. AI mode sends a downscaled copy to a
  Vision model (OpenRouter image attachments) and expects JSON boxes;
  offline mode splits on white gutters on-device (ImageOps gutter
  heuristic). Every detected panel is cropped, registered as new media, and
  placed in reading order on a new page via grid placement.
- Full-screen picture editor (Picture tools → Edit picture): brush erase
  (size + fill color), rect erase, undo stack, save-as-new-media with
  block mediaId re-point (cache-safe).
- AI text read + translate in the editor: "Read & translate" finds every
  text region, transcribes it, and translates into a chosen language.
  Regions are highlighted; tap to erase the original instantly; checklist
  selects which apply; "Add translations" erases originals and adds
  speech-bubble overlays with the translation.
- New MediaEditActions EditImage/SeparatePanels/SeparatePanelsAuto behind
  showPictureTools; storyboard status line above the page strip.
- Hard checkpoint: docs/CHECKPOINT-v1.3.38-HARD-CHECKPOINT.md (mirrored at
  the Weaververse root). Wiki Storyboard page + standalone manual updated.
- Verified on emulator: launch + monkey, no crashes.

## v1.3.37-beta — full dock cluster on every prompt bar

- Every UnifiedPromptBar now carries the complete dock feature set from the
  Novel/RPG dock: the **+ / 🎲 / 🎤** composer cluster (attach media, append a
  d20 roll, dictate), the **✓ hold-menu (↻ retry / » continue)**, and the ⌫
  hold-undo clear.
- Chatting, Brainstorm, and the Novel workshop chat gained real media
  attach: the + imports pictures/videos (MediaRepository) and they ride along
  with the next message as MediaBlocks, rendered inline in all three panes
  (media-only messages are allowed). Workshop chat and Brainstorm also
  gained » continue; the RPG messenger's bar gained 🎲 (forces a tabletop
  roll, same as Adventure).
- Dice rolls append `[d20: N]` to the draft on every surface; dictation
  merges into the current draft via a stale-state-safe accessor.
- Verified on emulator: launch + monkey run, no crashes.

## v1.3.36-beta — one prompt bar everywhere, sub-categories, picker search

- Unified composer: every generation surface now uses the same prompt window
  as the RPG adventure and Novel editor — Chatting rooms, Brainstorm, the
  Novel workshop chat, and the RPG messenger. Word range, AI/manual toggle,
  model picker (per-surface, settings default underneath), ✓ hold-menu
  retry/continue, × cancel, context meter, and ⌫ hold-undo everywhere. The
  bespoke Discord/messenger/brainstorm composers were removed; the messenger
  keeps media attach via the bar's + button.
- RPG character selection (campaign creation + Setup dialog): the chip row
  is now a two-row scrollable section with a "Search characters" field.
- Brainstorm sub-categories: + Add creates a main category; each main row
  now has a **+** beside the ⌫ delete that adds a sub-category nested under
  it (indented, `└` prefix). Deleting a main category removes its subs.
  `chat_threads.parentThreadId` added (MIGRATION_13_14, DB v14).
- Brainstorm/Notes mode now has two sub-modes: **Brainstorm** (the AI chat,
  default) and **Notes** (the notes board), so removing the Extra buttons
  keeps the board reachable.
- Extras row trimmed to Codex, Prompts, Pictures — Chats, Snippets and Notes
  buttons removed per their purpose living in Brainstorm/Notes and Novel's
  Chat tab. Novel's own side-rail tabs are unchanged.
- Verified on emulator: fresh launch, DB v13→14 migration, and a 200-event
  monkey run all pass with no crash.

## v1.3.35-beta — startup crash fix

- Fixed a force close on launch: the new profile theme art used
  `painterResource` on layer-list gradient drawables, which only supports
  vector and raster assets — first composition of the shell threw
  IllegalArgumentException. The art is now drawn with Compose brushes
  (`ProfileBackgroundArt` in core/ui/theme/Backgrounds.kt, per-profile
  vertical gradient + radial glows); the unused bg_profile_*.xml drawables
  were removed.
- Verified on emulator: app installs, launches, survives a randomized
  monkey run with no crash.

## v1.3.34-beta — picker fix, theme art, wiki figures

- Fixed the section-color HSV picker snapping to black. Two causes: (1) the
  picker re-seeded hue/sat/value from `selected` on every persisted echo, so
  drags snapped mid-gesture — now only genuine external changes (section
  switch, reset) resync, via an echo guard; (2) picking a hue while the
  current color was unsaturated or a dark theme fallback kept the color
  effectively black — a hue pick now jumps saturation/value to full so the
  selection is visible.
- Appearance: ambient theme art behind the whole shell, one gradient
  backdrop per profile (Classic, Fantasy, Arcade, Synthwave, Chill,
  Tabletop) in res/drawable, shown when no custom background image is set
  (Settings → Background media toggle). The shell chrome wash thins to ~86%
  so the art reads through; section opacity still controls depth.
- Wiki: schematic figure support (`{{figure:...}}` in the markdown subset)
  drawing wireframe "screenshots" of Chatting, the RPG adventure page,
  Novel, Brainstorm, and the prompt dock, embedded on their pages.

## v1.3.33-beta — in-app wiki manual

- New full wiki manual in the app: `feature/help/WikiContent.kt` (twelve
  markdown pages: Home, Getting Started, Navigation and Shelves, Novel and
  Reader, RPG, Chatting, Brainstorm and Notes, Storyboard, Codex, Prompts and
  AI, Appearance, Backup Sync and Troubleshooting) and `WikiScreen.kt` — a
  web-wiki-style reader with a navigation sidebar, page search, breadcrumbs,
  tables, bullets, **bold**, and [[clickable links]] between pages.
- Opened from Settings → Help → "Open Wiki manual" as a full-screen overlay
  with a Close control; the old compact quick guide remains beside it.
- The quick guide's Help section copy updated to point at the wiki.

## v1.3.32-beta — hard checkpoint and wiki manual

- Hard checkpoint recorded at `docs/CHECKPOINT-v1.3.32-HARD-CHECKPOINT.md`
  (mirrored at the Weaververse root alongside the v1.3.26/27 checkpoints):
  full feature state for Chatting/Discord, Brainstorm, campaign options,
  codex activation/links, the ⌫ hold-undo surfaces, migrations, file map,
  known gaps, and resume state.
- Standalone wiki manual for the computer:
  `S:\AI\Novel\Weaververse\Weaverse-Wiki-Manual.md` (markdown).
- In-app guide (`HelpContent.kt`) refreshed to match: Chatting rewritten for
  the Discord workspace, a new Brainstorm section, RPG Setup and codex-link
  entries, Novel codex-link note, and composer/backspace entries under
  Prompts & AI.

## v1.3.31-beta — codex back/exit

- The codex panel that opens from mention hyperlinks (Novel write, RPG
  adventure prose, search results) now has a "‹ Back" header that closes it
  and returns to the exact spot you left, instead of trapping you until you
  switched workspaces.

## v1.3.30-beta — campaign options, codex everywhere, Brainstorm

- RPG: a "Setup" button on the adventure page reopens the campaign options
  sheet (CampaignOptionsDialog) for an existing campaign — add/remove character
  perspectives, change setting template and details, play-as role, point of
  view, tense, rules system, and house rules. Applying rewrites the campaign
  setup note, syncs the roster's inParty flags, persona, the chat title, and
  the underlying book fields. Setup parses the stored note back into the form
  (template ids recovered by label, house rules extracted).
- Codex lore is now active in RPG adventures: lorebook-style activation injects
  matching entries (name/alias mentioned in recent story or the new action,
  plus always-include entries) into every DM prompt via ContextBuilder, and
  codex names/aliases in adventure prose render as tappable underlined links
  that open the entry (CodexMentionText in AdventurePlayScreen →
  selectedCodexEntryId). Novel Write already had clickable mentions.
- Backspace (⌫) clear with press-and-hold undo is now consistent everywhere:
  the adventure prompt bar (onUndoClear added), the workshop chat composer, and
  ChatComposerRow (Clear Text capsule replaced with the backspace button).
  Global prompt overlay and Chatting composer already had it from v1.3.29.
- Notes mode renamed "Brainstorm/Notes" and is now a NovelCrafter-Chat-style
  AI conversation (feature/brainstorm): chat thread rail (app-global scope,
  not book-scoped), user/AI bubbles, streaming, cancel, retry via ✓ hold-menu,
  W min–max word range, model picker, codex context chips + picker, preview
  prompt, usage line, context meter, and the backspace clear with hold-undo.
  Notes themselves remain available through any mode's Notes rail tab.

## v1.3.29-beta — single composer, auto rooms, backspace undo

- Chatting no longer opens the shared prompt dock — two generation surfaces
  fought over the keyboard and the dock never worked against Discord rooms.
  PromptSurface (and AppShell) now keep the overlay off in Chatting entirely.
- The Discord composer absorbed the dock's features: W min–max word fields,
  /A ↔ \M AI/manual toggle, ✓ with the hold-menu (↻ retry, » continue),
  × cancel while streaming, and a live "context: used / limit" meter.
- Retry deletes the latest AI reply and regenerates from the last message;
  continue nudges the room onward without inserting a new user message.
- The prompt-bar clear X is now a backspace (⌫): tap deletes the draft,
  press-and-hold restores it. Applies to the shared InkClearIconButton
  (Novel/RPG/Storyboard prompt bars included) and the Discord composer.
- Rooms now auto-generate the moment a novel/campaign is created: createWork
  seeds via the new ChatRoomSeeder singleton; the Discord workspace also
  re-checks every server on open so legacy works catch up.
- Word range feeds the model: max words drive the token budget and the
  PromptWordLimit trim; the min–max instruction steers reply length.

## v1.3.28-beta — Discord-style Chatting mode

- Chatting → Chats is now a Discord-style workspace: a server rail where every
  novel and campaign adventure is a "server", a channel sidebar, and a message
  pane (DiscordChatScreen / DiscordChatViewModel in feature/chatting).
- First open of a server auto-seeds #general, #lore, and #brainstorm text
  channels plus one live room per tied character (campaign roster members via
  the setup note; novel characters via defaultCodexId codex links).
- Channel rooms chat with an AI narrator scoped to the work's title, genre,
  POV, tense, and style guide; character rooms chat in-character.
- @mention support: typing @CharacterName (full or unambiguous first name) in
  any room brings that character into the AI's reply, voiced from their card.
- Users can add custom channels and character rooms (+ buttons in the
  sidebar) and delete any room via long-press (messages go with it).
- Home (the ⌂ rail slot) lists direct messages with unread badges and
  previews. Legacy messenger chats with no owning work surface as DMs.
- Friends (renamed from Contacts) now creates DM-kind chats that open under
  Home; the top pane has a Friends shortcut.
- rp_chats gained a roomKind column (MIGRATION_12_13, DB v13): "" legacy,
  "channel", "character", "dm". Campaign/storyboard chats are untouched and
  still open through RPG and Storyboard modes.
- Known gaps: the pane renders text with a media-attachment note (media
  blocks are not yet displayed inline); room context does not yet pull codex
  lore entries (book metadata only).

## v1.3.26-beta — compact template controls and effective preview

- Moved Mode Template to the top of TEMPLATE, immediately after its title bar.
- Condensed genre choices into a two-row, horizontally scrollable picker.
- Expanded age ratings to PG, PG-13, R, NC-17, and X, each with a distinct
  persisted backend instruction and mature-block behavior.
- Added Refresh instructions. It rebuilds a read-only effective prompt from the
  current controls and editable System message, then opens Instructions so
  added and removed layers are visible without modifying the base prompt.

## v1.3.25-beta — selectable prompt template stack

- Prompting Instructions now treats Novel, RPG, Chatting, and Storyboard as
  persisted, selectable base templates that materially change every AI request.
- Genre is now a multi-select chip set; selected genres are combined as add-ons
  alongside the independent Standard/Mature rating and Ecchi Mangaka overlay.
- Removed the unused Category text field and checkmark from Prompt Collection.
- Final template injection now happens once at the shared AI request boundary,
  covering every generation surface without the previous RPG duplication risk.

## v1.3.9-beta — AI-DM adventure startup

- New campaigns open with the AI Dungeon Master leading setup instead of a
  passive paragraph asking the player to invent the first action.
- The first screen offers three startup paths: a classic tabletop opening, a
  short setup interview, or a fully randomized opening.
- Interview mode asks for where, when, who, what is happening, and the main
  goal, then uses the answers to frame the actual opening scene.
- Classic and Random modes immediately establish the location, time, cast,
  active situation, and quest objective before inviting the first player
  decision.
- Adventure setup is stateful, forces AI mode, never rolls dice, hides scene
  navigation until complete, and labels setup answers separately from actions.
- Campaigns containing the exact legacy passive opening are migrated to the
  new AI-DM startup when opened.

## v1.3.8-beta — selective tabletop checks and unified prompt

- Adventure actions now pass through a backend tabletop ruling before dice
  are generated. Routine movement, conversation, item handling, and DM
  narration resolve without a roll.
- Attacks, defenses, saves, contested actions, explicit roll requests, and
  uncertain Strength/Dexterity/Constitution/Intelligence/Wisdom/Charisma
  actions activate the campaign's dice system.
- D&D/Pathfinder d20 checks apply the active roster character's ability
  modifier and attack/spell proficiency bonus once, then provide that exact
  result to the AI DM and the pixelized low-poly roll animation.
- Adventure and the global writing surfaces now share the same compact prompt
  control with context, word range, model, AI/manual, media, submit, clear,
  and voice controls.
- Local beta artifacts now go to
  `S:\AI\Novel\Weaververse\Beta.Test.Build`; tagged builds continue to GitHub
  Releases.

## Resume state (2026-08-16)

**The app codebase in this repo was replaced today.** Everything under
`app/`, `desktop/`, and `sync-core/` is now the transplanted source of
**InkForge** (a Kotlin Multiplatform Android + Windows-desktop + web-sync
novel/roleplay app, most recently at its own `v0.5.17`), rebranded to
Weaverse (`com.ihy2ln.weaverse`, app name "Weaverse", repo
`github.com/ihy2ln/weaverse`). This supersedes the previous contents of this
repo — an independent, from-scratch native-Android-only rewrite of the same
concept (14 build phases + a revision pass, tagged `v0.1.0`–`v0.2.0` and
released) — which is still visible in this repo's git history (commits
before `Import InkForge → Weaverse transplant`) but is no longer what's on
`main`.

**Why:** the from-scratch rewrite covered core novel/roleplay/AI-provider
functionality but not everything InkForge had already built and shipped
(Windows desktop host, Wi-Fi/remote sync between devices, a web UI served
by that host, OpenRouter model browsing, NovelCrafter ZIP import). Rather
than keep building those out a second time from a spec, the actual InkForge
source — already at a mature, versioned, released state — was brought over
directly and rebranded in place.

**What changed mechanically** (for anyone diffing this against InkForge's
own repo, `github.com/ihy2ln/cursor-novel`):
- `com.ihy2ln.inkforge` → `com.ihy2ln.weaverse` everywhere (namespace,
  applicationId, every package/import, directory tree).
- `InkForgeApp.kt`/`InkForgeDatabase.kt`/`InkForgeAiLog.kt` →
  `WeaverseApp.kt`/`WeaverseDatabase.kt`/`WeaverseAiLog.kt` (filename +
  class name).
- The top-level `InkForge/` PC-package folder became `Weaverse/`, trimmed
  to scripts/docs/sample-import only — the committed `InkForge.exe`
  (1.7 MB) and `InkForge.jar` (29 MB) binaries were **not** carried over;
  they're CI/Release-built artifacts now, not committed source, matching
  the convention this repo already used before the transplant. The Go
  launcher source (`desktop/launcher/`) that produces `Weaverse.exe` was
  carried over and rebranded; rebuild it locally with `go build` (see
  `desktop/launcher/README.md`) if you need a fresh binary before the next
  tagged Release builds one.
- `releases/` (60 MB of committed APKs/zips in InkForge's own repo) and
  `docs/wiki/` per-version screenshot dumps were **not** carried over —
  distribution goes through GitHub Releases / Actions artifacts, not
  binaries-in-git, again matching this repo's pre-existing convention.
- CI (`.github/workflows/build.yml`, `release.yml`) keeps this repo's
  existing shape (base64-decoded keystore from `KEYSTORE_BASE64` +
  `KEYSTORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD` secrets, short-retention
  per-push artifacts) rather than importing InkForge's own workflow files,
  which assumed a different secret-passing convention
  (`KEYSTORE_PATH`) — those workflows were extended to also run
  `:sync-core:test` and build the desktop zip, and now call the committed
  `./gradlew` instead of a bare `gradle` (InkForge's wrapper jar was
  carried over, so there's no longer a reason to rely on `setup-gradle`
  provisioning Gradle directly, the way the pre-transplant repo had to).

**Rebuild documentation**, written from reading this transplanted source
(not the old rewrite's spec, which no longer describes what's in this
repo): [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and the equivalent
[wiki pages](https://github.com/ihy2ln/weaverse/wiki) — detailed enough
that an AI with no access to this repo, working from those docs alone,
could reconstruct the module layout, data model, and core protocols.

## Version history (from InkForge, condensed)

Full narrative entries for each of these lived in InkForge's own
`BUILD_NOTES.md` (not carried over verbatim — this is the condensed
header-only form; ask an AI with web access to pull the original from
`github.com/ihy2ln/cursor-novel` if the full narrative ever matters):

- **v0.5.17** — Codex navy list, hold-picture menu, growing PROMPT + Models, two-file backup
- **v0.5.16** — Visible prompt box, check Accept, X Clear
- **v0.5.15** — Individual prompt toggles, cohesive Write text, title-row Media
- **v0.5.14** — One PROMPT box, extra generators in Settings, action undo
- **v0.5.13** — Plan create menu, Notes editor, compact prompt bar
- **v0.5.12** — Always-on Novel scroll menu, Write header, Library duplicates
- **v0.5.11** — Themed system bars, auto-scrolling title, delete confirm
- **v0.5.10** — Plan scene summaries and tighter chrome
- **v0.5.9** — One prompt surface + Clear Text
- **v0.5.8** — Library Novels dropdown + visible mode capsules
- **v0.5.7** — Scene beat prompt box in Write
- **v0.5.6** — Library modes and Plan Write jump
- **v0.5.5** — Codex + Prompts on top, filled NovelCrafter prose
- **v0.5.4** — Safe chrome, prompt buttons, guiding prose
- **v0.5.3** — NovelCrafter / NovelAI workspace
- **v0.5.2** — NovelCrafter / NovelAI-style workspace
- **v0.5.1** — NovelCrafter Word ZIP + Novel / Manga / Roleplay pictures
- **v0.5.0** — NovelCrafter-style web workspace + auto-sync
- **v0.4.3** — Web hub is the only sync password
- **v0.4.2** — Windows EXE + PC package
- **v0.4.0** — Desktop EXE/JAR · web companion · sync
- **v0.3.19** — Global Notes (not book-scoped) + readable UI
- **v0.3.18** — Modular Notes rail + downloadable Releases APK
- **v0.3.17** — Speech-to-text in Novel / Roleplay / Notes; Notes rail collapse
- **v0.3.16** — Notes microphone / speech-to-text
- **v0.3.15** — Mode-isolated Roleplay content + DM 3×3 canvas
- **v0.3.14** — Manga Move + sharp expanded media
- **v0.3.13** — Media long-press edit popup
- **v0.3.12** — Global `/` and `\` prompt windows; hide manga grid lines
- **v0.3.11** — Visible 6×6 grid, AI/NAI entry, Notes mode
- **v0.3.10** — Portrait navigation crash fix (Samsung S25)
- **v0.3.9** — Characters/Personas create-edit, Appearance color square, audio media
- **v0.3.8** — Roleplay title chrome, 6×6 snap grid, drag-onto stack, portrait
- **v0.3.7** — Title-band menu, scroll gutters, media scroll-through, stack pictures
- **v0.3.6** — Compact Roleplay gen, manga grid, remove/drag media, Characters/Personas, presets
- **v0.3.5** — Plan POV, Codex media, TTS, Roleplay mode, scene-beat vision
- **v0.3.4** — Visible checkmark confirmations
- **v0.3.3** — Checkmarks + press-and-hold edit text popup
- **v0.3.2** — Media crash fix, multi-select, Novelcrafter ZIP import
- **v0.3.1** — Write overlay + prompt injection + Plan POV + Import/Export
- **v0.3.0** — Novelcrafter-inspired library + generation UX
- **v0.2.1** — UI/UX optimization
- **v0.2.0** — Real OpenRouter integration

## Prior Weaverse native-rewrite history (superseded, kept for context)

Before the transplant, this repo held an independently-built native-Android
rewrite: all 14 phases of a build spec plus a "Revision 02" pass complete,
tagged and released through `v0.2.0` — see the git history on `main` prior
to the transplant commit for that log in full. That codebase is gone from
`main` as of this transplant; nothing in the current `app/`, `desktop/`, or
`sync-core/` originates from it.
## Hard checkpoint — v1.3.2 Codex navigation (2026-08-28)

- Added a direct Codex category popup: tap the active category name to jump to
  Characters, Locations, Lore, Objects, or any custom category without repeated
  horizontal dragging.
- Condensed the open-entry Codex rail to the active category and its scrollable
  entries; the selected entry remains highlighted.
- Added a draggable divider so the rail can expand downward or collapse back to
  one line while the detail editor receives the remaining screen.
- Replaced the entry editor's back/forward pair with one expand/collapse
  chevron, restoring the last useful rail height.
- Added [the complete helper guide](docs/GUIDE.md), a
  [wiki-ready mirror](docs/wiki/Home.md), and a
  [checkpoint recovery record](docs/CHECKPOINT-v1.3.2-CODEX-NAVIGATION.md).
- Verified `:app:compileDebugKotlin` and `:app:assembleDebug` offline. Local test
  APK SHA-256: `1365E4B3124E3D84990E846A46F0527E3CD07F22C8B3AA02DE9F8576FCA8FDC0`.
## RPG campaign setup and illustrated Adventure play (2026-08-28)

- New Campaign now selects narrative POV, including multi-character third- or
  first-person options, and persists the full perspective directive for AI use.
- Added Character(s) / Dungeon Master role selection. DM mode reverses table
  authority: the user runs the world and rulings while the AI controls the
  selected player-character party; Adventure wording changes to match.
- Tightened the campaign Past / Present / Future segmented control so the three
  tense choices occupy one compact row without the previous oversized gaps.
- Inventory now resets to the top on entry. Writer / You and the wider cast
  start collapsed, Team roster stays visible, and tagged characters are split
  into NPC, Enemy, and Other groups without a database migration.
- New Campaign now offers AI-backed setting templates as well as rules-system
  templates. Both full directives are stored in campaign guidance, so pairings
  such as High fantasy + D&D 3.5e affect world framing and mechanical rulings.
- Adventure action checks continue to roll privately, but AI responses now
  carry a structured result. The play page displays the outcome label and
  fictional consequence without exposing the raw die, notation, or DC.

- Replaced New Campaign's free-text “Whose eyes” with multi-select Main
  character(s) sourced from personas, Roster, and the Characters Codex.
- Added selectable Past/Present/Future tense and rules templates for D&D 5e,
  Pathfinder 2e, D&D 3.5e, OSR/B/X, Powered by the Apocalypse, Fate Core, and
  custom/systemless play, with an additional house-rules field.
- Campaigns now own a dedicated game-master session carrying those setup
  choices into generation context.
- Adventure now opens directly as an illustrated prose session: one large
  scene image, a lorebook-like story/action record, and a bottom action entry.
- Player actions now receive private ruleset-aware resolution rolls (d20, PbtA
  2d6, or Fate 4dF). The AI game master decides when the roll applies, resolves
  the world's response, and hides mechanical bookkeeping unless asked.
- Added persistent numbered scene boundaries. The game master may advance only
  on a decisive transition, while Next scene and Stay here give the player an
  explicit pacing override without deleting prior scenes.
- Added Previous scene browsing; Next moves forward through saved scenes before
  creating a new boundary from the latest scene.
- Replaced the side-scrolling Town with tappable location picture cards. Every
  shop and landmark has a persistent user-imported art slot.
- Roster now uses portrait-forward cards with class/level, HP, and AC summaries
  that open the full character sheet.
- Inventory items now persist optional artwork, show image-picking slots, and
  reuse the selected image in equipped weapon/accessory plates.
- Roster cards now link directly to the selected character's shared Inventory
  record instead of requiring a separate lookup in the Inventory tab.
- Added slot-matched inventory templates and direct equipment-slot image
  controls. Every equipment position remains a single-item slot.
- Added Backpack as an equipment slot. Equipped backpack capacity controls an
  expandable carried-item panel with per-item slot size, empty slots, and
  over-capacity feedback.
- Add item now accepts artwork before the item is saved. The imported picture
  remains attached across pack and equipment views and can later be previewed,
  replaced, or removed.
- Town shops now use a video-game-style catalog with unit prices, live totals,
  editable buy quantities, minus/plus controls, an AI quantity fill action, and
  quantity-aware delivery to the active character's linked inventory.
