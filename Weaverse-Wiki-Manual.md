# Weaverse Wiki Manual

> This is the full text of the in-app wiki manual (Settings → Help → Wiki), exported to Markdown for reading outside the app or in a browser. It is generated from `app/src/main/java/com/ihy2ln/weaverse/feature/help/WikiContent.kt` by `tools/export_wiki_manual.py` and should be regenerated whenever that file changes, so the two never drift apart.

## Contents

- [Home](#home) — Welcome to the Weaverse wiki
- [Getting Started](#getting-started) — First ten minutes with a fresh install
- [Navigation and Shelves](#navigation-and-shelves) — Home, the three rows, and every shelf
- [Novel and Reader](#novel-and-reader) — Plan, Write, Read, Chat, Review
- [RPG](#rpg) — Campaigns, adventure play, dice, roster, town
- [Chatting](#chatting) — A Discord-style space for your works
- [Brainstorm and Notes](#brainstorm-and-notes) — AI brainstorm chat, and the notes board
- [Storyboard](#storyboard) — Manga and comic pages — import, separate, edit, translate
- [Codex](#codex) — One shared library for every mode
- [Prompts and AI](#prompts-and-ai) — The dock, the composer, models and keys
- [Appearance](#appearance) — Profiles, themes and section colors
- [Backup, Sync and Troubleshooting](#backup-sync-and-troubleshooting) — Moving data and fixing problems

---

## Home

Welcome to the **Weaverse wiki** — the full manual for every
workspace. Use the sidebar to move between pages, or search.
Blue links like [Novel and Reader](#novel-and-reader) jump straight to a page.

### The five workspaces

> *[Illustration: Novel Write — the manuscript with Plan, Write, Read, Chat, and Review across the top.]*

- [Novel and Reader](#novel-and-reader) — plan, write, read, workshop-chat and
  review your manuscripts.
- [RPG](#rpg) — AI game-master campaigns, dice, roster, inventory,
  towns, and illustrated adventure scenes.
- [Chatting](#chatting) — a Discord-style space where your novels and
  campaigns are servers with channels and character rooms.
- [Storyboard](#storyboard) — manga and comic page building.
- [Brainstorm and Notes](#brainstorm-and-notes) — chat with the AI to brainstorm and
  research; plus the classic notes board.

### Shared tools

- [Codex](#codex) — one library of people, places, things and lore
  shared by every book and mode.
- [Prompts and AI](#prompts-and-ai) — the prompt dock, composer controls,
  models, keys, and templates.
- [Appearance](#appearance) — profiles, themes and section colors.
- [Backup, Sync and Troubleshooting](#backup-sync-and-troubleshooting) — moving data between
  devices and fixing problems.

New here? Start with [Getting Started](#getting-started).

---

## Getting Started

### 1. Create a book

The book button (top-left) opens Home. Pick **Novel**, then +
to create one — only the title is required. Everything else
can be filled in later from Plan or the editor.

### 2. Learn the three rows

Across the top: **modes** (Novel, RPG, Chatting, Storyboard,
Brainstorm/Notes), then the current mode's **sub-modes**
(Plan, Write, Read…), then **Extra** — Codex, Prompts, Notes,
Snippets, Chats, Pictures. See [Navigation and Shelves](#navigation-and-shelves).

### 3. Add an AI key (optional)

Without a key everything except AI generation works. Open
**Settings**, find the OpenRouter section, and paste a key
from openrouter.ai/keys. Then **Refresh models** in
Settings → Writing and pick a default model. See
[Prompts and AI](#prompts-and-ai).

### 4. Open the prompt dock

From any writing surface, tap **/** for an AI prompt or
**\\** for manual entry. The dock collapses to one line so it
never covers your page. Details in [Prompts and AI](#prompts-and-ai).

### 5. Build a codex

Characters, places and lore live in the [Codex](#codex) and are
shared by every book and mode. Entries you mention in your
text are highlighted and fed to the AI automatically.

### 6. Back up

Settings → Backup schedules automatic local backups, and
[Backup, Sync and Troubleshooting](#backup-sync-and-troubleshooting) covers moving to another
device.

---

## Navigation and Shelves

### Home

The book button opens Home: a card per workspace with your
novels underneath.

### The three rows

1. **Modes** — Novel, RPG, Chatting, Storyboard,
   Brainstorm/Notes.
2. **Sub-modes** for the current mode — for example Novel:
   Bookshelf, Plan, Write, Read, Chat, Review.
3. **Extra** — Codex, Prompts, Notes, Snippets, Chats,
   Pictures: tools that belong to no single workspace.

Both mode rows can be reordered; the app remembers your
layout. **Focus** switches the writing view to the picture
gallery.

### Shelves

- **Bookshelf** — novels, on the Novel mode.
- **Campaign shelf** — campaigns, on RPG mode (Campaign or
  Adventure).
- **Storyboard shelf** — visual series, on Storyboard mode.

All three are manuscripts underneath — the same library,
different doors.

### Leaving

Back asks before closing, because an edge swipe is easy to
hit by accident. Everything saves as you go.

---

## Novel and Reader

### Plan

The outline: **Acts → Chapters → Scenes → Scene beats**, with
summaries, status, point of view, labels and word counts.
Plan's Write ▾ jumps straight to a beat or chapter.

### Write

The scene editor. Documents are blocks — paragraphs, scene
beats, and media (images, audio) you can drop in and arrange.

- **Codex highlighting:** entry names and aliases are
  underlined as you type. Tap one to open the entry; the
  codex panel has a **‹ Back** header that returns you to the
  exact spot.
- **Slash commands** insert codex entries and beats without
  leaving the keyboard.
- **Tap anywhere on a line** — not only exactly on the text
  — to move the caret there; tap the empty space below the
  last line to jump to the very end of the scene.
- The prompt dock lives here, and a generated draft renders
  inline in the manuscript where it will land — editable
  before you insert it. See [Prompts and AI](#prompts-and-ai).

### Read

A distraction-free reader: saved position, contents,
bookmarks, Paper/Sepia/Night themes, typography controls, and
read-aloud.

### Chat

The workshop: an AI conversation about the book. Codex
entries join the context when you mention them, or pin them
manually with **+ Codex**. Preview shows the exact prompt
before sending. The composer is the same dock as everywhere
else — attach pictures, roll dice, dictate, pick the model.

### Review

A read-through pass over the manuscript with scene-by-scene
notes.

Your work here feeds [RPG](#rpg) and [Chatting](#chatting) too — every
mode shares the same [Codex](#codex).

---

## RPG

### Creating a campaign

The campaign shelf's + opens the options sheet:

- **Main characters** — pick from You (personas), Roster, or
  the Characters Codex. The chips scroll in two rows and a
  **search field** filters long casts. Add or remove any
  time from the Setup button.
- **Play as** — Character(s) (you act, AI runs the world) or
  Dungeon Master (you run the world, AI plays the party).
- **Setting template** and setting details use a three-level
  browser: main section, theme, then preset. Tap ☆ to place
  any preset in the persistent **Favorites** section. Adams
  Haven's worlds, Fantasy, Action, survival, science fiction,
  games/media, 18+, Slow Life and Overpowered Protagonist are
  organized here. **+ Add** in either browser creates a reusable
  template or details preset with its own section, theme and AI
  guidance; custom entries can be favorited or removed.
- **Point of view**, **tense**, **rules system** (D&D 5e,
  Pathfinder 2e, older D&D, OSR, PbtA, Fate, custom), and
  **house rules** complete setup.

Their full guidance is supplied to the game master during
play.

### Mode and rule system

Mode controls the player-facing interaction. Rule system
controls the underlying conventions, and both are saved
independently.

- **Focused Tactical Cards** — Adams Haven-style hand,
  AP/EP, targets, visible intent and statuses.
- **D&D d20** — character-sheet modifiers, proficiency, AC,
  HP, conditions and deterministic checks.
- **Text Reactions** — written or dictated actions with legal
  action checks and risky-action previews.

**Roleplay → Enter combat** (in the expanded prompt dock)
opens the native screen for the saved campaign mode. An
immediate AI encounter opens the same screen automatically
after its narration is saved. Active rounds survive
force-close and resume.

A battle may use a temporary override. Victory, defeat,
retreat or surrender restores the saved campaign mode for the
next encounter. Older RPG saves normalize to D&D d20; Text
Game saves remain separate.

### Four-screen adventure startup

1. **Create Your Own Adventure** — six editable cards for plot,
   first goal, first scene, starting party, tone and opening
   complication. Every card supports a multiline answer,
   horizontal preset chips, campaign-aware suggestions, and
   **Skip**. Presets fill the box but never prevent editing.
2. **Chapter One Plan** — a saved, editable rough chapter
   outline plus opening-scene guideline. Progress moves from
   1–100% and invalid AI fields receive authored fallback data.
3. **Verify Your Adventure** — review Campaign Setup, CYOA,
   chapter outline and opening guideline before generation.
4. **Scene One** — the actual playable opening. Planning text
   stays out of the narration.

### Scene actions and artwork

Every scene provides three AI choices and a fourth custom
action, shown as cards right above the input in the prompt
dock — see [Prompts and AI](#prompts-and-ai). Expand the dock and the
**Actions**, **Thoughts**, **Dialogue**, **Combat**, and
**Roleplay** chips switch a grid of common shortcuts while
preserving freeform input.
Select story text to reveal **Add text to…** and open the
existing capture popup.

The AI supplies scene-art tags or an eligible asset ID. The
app ranks local library images against the scene prose,
campaign setting, category, mood and tags, then attaches the
best match to the saved scene. Art is optional and never blocks
play.

### Setup — changing a campaign later

The **Setup** button on the adventure page reopens the same
sheet for an existing campaign. Add or remove character
perspectives, change the setting, role, point of view, tense,
rules, or house rules — saving rewrites the campaign setup
the AI plays by. Your story is untouched.

### Adventure is the play session

> *[Illustration: RPG Adventure — one large scene illustration above the ongoing prose and action bar.]*

Not a chat: one large scene illustration, ongoing prose, and
an action box. Uncertain actions receive a hidden rules-aware
dice roll; the page shows the resolved outcome and
consequences. **Previous** browses saved scenes; **Next
scene ›** and **Stay here** control pacing.

Tap **Character cards** in the scene header, or use
**Roleplay → View tactical roster cards / View party
character sheets** in the dock, to inspect the player and
active party outside
combat. Tactical cards show ATK, DEF, SUP, SPD, AP, EP and a
signature card; d20 mode shows the six abilities and modifiers.
Each card opens its complete editable sheet. Newly created
characters start with populated d20 and tactical statistics.

### Codex links and lore in play

Codex names and aliases in the story are underlined links —
tap to open, ‹ Back to return. The AI reads the codex too:
entries you mention (or that are always-on) join the game
master's prompt automatically.

### Roster, Inventory, Town, Lore

- **Roster** — portrait cards for You and the team, with full
  stat sheets one tap away.
- **Inventory** — per-slot equipment and carried items, with
  optional item pictures.
- **Town** — a picture directory of shops and landmarks; BUY
  sends purchases to the active character.
- **Lore** — this adventure's own codex, filling as you play.
- **Presets** — difficulty (Slice of life → Ruthless) that
  changes how hard the world pushes back.

---

## Chatting

### Servers are your works

> *[Illustration: Chatting — a server rail, channel list, and message thread.]*

The left rail lists every novel and campaign as a server
icon. The house button is **Home**; directly under it the
**envelope marked DM** opens your direct messages from
anywhere. Opening a server for the first time auto-creates
**#general**, **#lore** and **#brainstorm**, plus one room
per character tied to the work.

### Who is in a room

Every room is seeded with **1-5 people** drawn from the
work's Codex characters, shown as an avatar strip under the
room header. Each channel gets its own stable cast, so
#general and #lore are not the same crowd. Character rooms
seat that character plus a couple of others. **Long-press**
an avatar to remove that person from the room.

- **Text channels** are group chats with the people seated
  there — never a narrator.
- **Character rooms** are that person's own room, seeded
  with their greeting.
- The **+** beside Text Channels adds a custom channel; the
  **+** beside Characters adds a character room.
- **Long-press** a room to delete it (and its history).

### @mentions pull people in

Typing **@** opens a name picker above the message box that
narrows as you type — **@k** lists the K names, **@kae**
narrows further. Tap a name to complete it; a name with a
**+** is not in the room yet and will be pulled in.

Type **@Name** — full name, or an unambiguous first name —
and that person is added to the room for good, with a
"joined" line in the history. They answer first, and anyone
else seated there may chime in if they have something to
add. Each speaker lands as their own message with their own
avatar, the way a real group chat reads.

### Rooms keep their place

Switching rooms no longer resets anything: each room keeps
its own unsent draft, its own scroll position and its own
history, so moving between them and back lands you exactly
where you left off. Drafts survive an app restart, and the
server reopens on the room you had last.

### Reading and sending

Message text is selectable — press and hold to select and
copy. The **picture button** in the room header attaches an
image, which posts straight into the room and renders
inline.

### Direct messages

The **DM envelope** under Home opens a contacts screen listing
every codex character, with a search box. Tap someone to open
their direct message — a DM holds just the two of you, unlike a
room, which seats a cast.

### Home

Home lists **Recent Conversations** first, then **Direct
Messages**, then a section per server listing all of its
channels and character rooms, so nothing is more than a tap
away. **Friends** lists everyone you can talk to; **Meet
someone** writes a new character daily when you have an
OpenRouter key.

### The prompt window

Chatting uses the same resizable **prompt window** as Novel,
not a slim bar: drag its handle to resize, then use the
message box and **Send**, the **/A** AI versus **\M**
manual chip, the word target (**W**), the model row — which has
its own **Search models** box — and a live context meter.

The arrow beside **Less/More** folds the window down to the
message box and Send, and back again.

**More** opens the rest: Attach image, **Emoji**, Dictate, Roll d20,
Retry, Continue, Clear, All models — and the **quick
messages**, chat-themed templates such as Say Hi, Catch Me
Up, Banter, React To That, Ask The Room, Make Plans, Check
In On Me and Nudge For A Reply. Tick as many as you want;
they layer in the order picked. See [Prompts and AI](#prompts-and-ai).

### Chat voice, not prose

Replies are written as chat messages: first person, present
tense, short. No narration, no scene-setting, no
third-person description and no asterisk stage directions —
a chat app only has what someone types. Emoji are welcome
where they suit the character.

---

## Brainstorm and Notes

### Brainstorm — chat with the AI

> *[Illustration: Brainstorm — a threaded AI chat alongside the classic notes board.]*

The Brainstorm/Notes mode is a NovelCrafter-style AI chat for
ideas and information: plots, worldbuilding, research,
names, structure, honest feedback. It is not character chat —
the AI is a brainstorming partner, not a persona.

### Main categories and sub-categories

**+ Add** starts a main category. Every main row carries a
**+** beside its ⌫ delete — that adds a **sub-category**
nested underneath (indented with a `└` prefix). Long-press a
category to delete it; deleting a main category removes its
sub-categories and their history. Threads are app-wide,
shared across every book and mode.

- **Codex context** — + Codex pins entries; mentioning an
  entry pulls it in automatically; chips show what the AI
  will see; **Preview** shows the exact prompt.
- **Composer** — the full dock: word range, model picker,
  ✓ send with hold-menu **retry and continue**, × cancel,
  usage and context meter, ⌫ clear with hold-to-undo, plus
  **+** picture attachments that render inline, **🎲** rolls
  and **🎤** dictation.

### Notes — the board

The classic notes board is the mode's second sub-mode —
switch with the **Brainstorm / Notes** pills in the
sub-mode row. Notes have a title, rich text, images and
audio, shared across all books, and integrate with the
prompt dock — you can generate into a note or paste from it.

---

## Storyboard

### Window, Manga, Comic

**Window** opens the dedicated manga hub: **Library, Browse,
Downloads, Extensions, and Projects**. **Manga** reads
right-to-left; **Comic** the other way.

### Browsable sources

Browse starts with source chips for **MangaDex, Comix,
Atsumaru, MangaFire, MangaDot, and Rawkuma**. Select one, then
use Popular, Latest, or Search. A cover opens series metadata
and the chapter list without leaving Weaverse. MangaDex uses
its API; the other built-in adapters read catalog and chapter
links exposed by their public pages. If a host requires a
JavaScript, CAPTCHA, login, or anti-bot challenge, Weaverse
reports that source as blocked instead of pretending that it
returned an empty catalog.

### Favorites and named sections

Open a title in Browse and select one or more favorite-section
chips. Favorites are saved even when no chapter is downloaded.
Library begins with Downloads and Favorites sections. Enter a
name in **New favorite section** and tap Add to create personal
categories such as Reading, Finished, Translate, or Reference.

### Downloading and offline reading

Open a cover, choose a chapter, then tap Download. Downloads
shows queued, downloading, stopped, failed, and completed
states with Stop, Retry, and Read actions. Completed originals
open offline from Downloads or Library. The direct URL importer
remains available for public chapter-reader links: Preview
verifies the ordered page manifest before Download is enabled.

### Pages

Page tabs sit above the canvas. **+** adds a page;
long-press a tab to rename or delete it. **Add pages** imports
one or more page images, or a whole PDF, CBZ/ZIP, or long
webtoon strip and creates the ordered pages automatically.

### Import generated panel artwork

Generate artwork outside the Android app with Codex/ImageGen,
export it as PNG/JPG/WEBP, then return to Storyboard. Tap an
empty layout frame and choose **Import generated panel**. You
may select multiple images; they retain picker order and fill
the selected slot followed by the next free slots.

If occupied artwork is selected, Weaverse asks whether to use
the next free slot or explicitly replace it. Imported files are
copied into private app storage immediately. The Android app
does not access Windows paths and does not require an OpenAI
key for this file-import workflow.

### Layouts and panels

Six templates draw numbered empty frames, so a page looks
like a comic page before anything is added. Media lands in
the first free slot at that slot's size. Tap a panel to
select, drag to move; drag the corner grip to resize. Drop
one panel onto another to stack them. **Adjust image** pans
and zooms inside the frame and is remembered per panel.

### Import a whole manga, comic, or webtoon

When creating a storyboard from the **+ Storyboard** button,
choose an optional whole comic file. You can also use **Add
pages** into the dedicated Manga Editor. Downloaded/imported
pages no longer open in the blank six-panel Storyboard composer.
Each source page stays full-size and can be viewed as **Original**
or **Edited**. Select a page or picture to use its bottom tools:

- **Separate panels (AI)** — a Vision model finds every
  panel on the page; each one is cropped into its own
  picture and placed in reading order on a new page. Needs
  a Vision-capable model in Settings → Writing.
- **Separate panels (offline)** — the same split, done on
  device by detecting the white gutters between panels.
  Free, no key, and it never sends the image anywhere. It is a
  near-white, page-spanning gutter heuristic; dark, borderless,
  or irregular gutters may require AI detection.

Detection reports whether AI or offline analysis ran and
explains unreadable, zero-panel, and one-panel outcomes. A
successful split is always written to a new page; the imported
original page remains unchanged.

**Translate English** detects Japanese, Korean, or Chinese text,
removes the source lettering, and typesets the translation into
the cleaned bubbles. **Colorize B&W** creates a colorized copy
while retaining ink and white bubble areas. Page and whole-chapter
actions show progress and a Stop button; completed copies remain
saved if a batch is stopped.

### Picture editor

**Edit picture** / **Page editor** opens a touch-first page
editor on the phone:

- Bottom tools are **Move**, **Text**, **Paint**, **Erase**,
  **Color**, **Clean**, and **Hand**. Tap one, then use your
  finger on the page. There are no keyboard keys.
- Tap **Find text**, **Read**, **Translate**, and **Clean art**,
  then **Go**. Clean art rebuilds the drawing under old
  lettering. The page shows the translation; source OCR stays
  in Type.
- Type and Layers edit source, translation, auto-fit,
  alignment, fill, stroke, and writing direction.
- **Save copy** writes a new picture file and never overwrites
  the original. Use Original / Edited to compare. Undo and Redo
  cover paint, erase, and clean strokes.

### Generate pictures with AI (cloud)

Picture tools → **Generate picture (AI)** connects to a
cloud image model through OpenRouter (Nano Banana, Flux,
GPT-Image and friends — see Settings → Models → Image
generation). Describe the panel, pick the model, and the
generated picture lands on the page like imported media,
ready for the editor and overlays. Long-pressing an empty
layout slot offers both Add and Generate.

Text overlays themselves are unchanged: **Add text** drops
a draggable caption or a manga speech bubble with a tail.

### Managing library titles

**Long-press** a cover in the Library for that title's
categories and **Remove from library**. Removing clears every
category and deletes its downloaded chapters, after a
confirmation. **More → Data and storage** reports what the
downloads and image cache are using, and can clear either.

### Filtering chapters

The filter icon above the chapter list opens **Language**,
scanlator group, and a chapter-number range. The language
picker lists only what that title actually publishes, so a
series with seven translations narrows to the one you read.

---

## Codex

### What lives here

Characters, Locations, Objects, Lore, Factions, Subplots,
Magic/Tech, Events, Organizations and Notes — one codex
shared by every book and mode. Open it from **Extra →
Codex** in any workspace.

### Entries

Each entry has a name, aliases, a color, an image, and free
text. Lorebook fields (keys, secondary keys, insertion
order, always-include, probability) control exactly when the
AI sees it.

### Mentions and links

- In the Novel editor, entry names and aliases are
  highlighted while you write; tap to open.
- In RPG adventure prose, they are underlined links too.
- Mentioning an entry in Novel Chat, RPG play, or
  [Brainstorm and Notes](#brainstorm-and-notes) pulls it into the AI's context
  automatically — the chips show what was detected.

### Roster sheet & inventory (RPG parity)

Entries in a **Characters** category get the same roster
sheet the RPG Roster uses: class, species, level, HP,
armor class, proficiency bonus, the six ability scores,
and attacks & actions — editable right on the entry with
steppers. **Every entry** (any category) also carries an
**inventory**: items with quantity, weight, cost, tags and
notes, with an active ■/□ toggle, matching the RPG
Inventory system. Saving the entry persists both.

### AI sort into Codex / Roster / Inventory

Long-press an adventure message and pick **AI sort into
Codex / Roster / Inventory…**. The AI splits the text into
the right sections — character-sheet facts, inventory items
(with the carrier who holds them), and codex lore with a
suggested category — and shows a review dialog where you
tick what to keep. Unsure rows are flagged so you can
re-route them. Placing merges characters into the roster
with linked codex entries, files items into inventories,
and writes lore into the codex.

### In and out

Novelcrafter ZIP imports map their codex folders to these
categories. The RPG game master also writes entries as you
play — characters, items and scene synopses land in the
adventure's codex.

---

## Prompts and AI

### The prompt dock

**/** opens an AI prompt and **\\** opens manual entry from
any writing surface — Novel Write, RPG Adventure, Chatting,
Brainstorm. Novel and RPG share one dock design; Chatting and
Brainstorm use the same controls in a slightly simpler shell.

> *[Illustration: The prompt dock — drag handle, compact control row, and the template/preset grid.]*

### Resizing the dock

Drag the thin handle along the top of the dock up or down to
make it taller or shorter — the instruction box grows with
it, so a taller dock is a taller place to write, not empty
space. **Double-tap the handle** to snap back to automatic
sizing. The dock remembers your dragged height for the rest
of the session; it resets to automatic the next time you
open the app.

### Compact control row

- **W min–max** — a four-digit box for each end of the word
  range the AI targets (up to 9999). The app finishes on a
  complete sentence and may allow a small overrun rather
  than cut prose off mid-sentence.
- **/A vs \\M** — AI generation versus filing your text
  without calling a model.
- **Model ▾** — tap to open a scrolling list of the cached
  OpenRouter text models. No provider/model string to type by
  hand; pick one and it's saved for that book or campaign.
- **More / Less** — expands the dock to show templates or
  presets, the model row, and (in Novel) context preview and
  attachments.
- **Generate / Send**, **×** to cancel a stream, the clear
  **⌫** (press and hold to undo a clear), and the mic, roll,
  and add-media controls sit in the input row itself.

### Novel: multiple templates at once

Expand the dock and the lower half shows every saved prompt
template in a two-column grid split by dividing lines. Tap
any number of them — they layer in the order you tapped
them, numbered **1.**, **2.**, and so on, and every ticked
template contributes its system instructions to the same
generation. **Clear** drops back to the action's default
template. Save a new template, or duplicate the current
selection's first template, right below the grid.

### RPG: Actions, Thoughts, Dialogue, Combat, Roleplay

The same grid holds turn presets instead of templates: five
chips — **Actions**, **Thoughts**, **Dialogue**, **Combat**,
and **Roleplay** — switch which column of quick moves is
showing. Tapping a preset fills it straight into the
composer; the two entries that aren't text (viewing the
roster, entering combat) open their own screen instead. When
the AI offers "Choose the party's direction" moves for the
current beat, those cards now appear inside the dock, right
above the input, instead of floating over the story — pick
one to load it into the box, still editable before you send.
**Retry**, **Continue**, and the media/roll/roster/inventory
shortcuts sit together in that same expanded row, next to
each other rather than tucked behind a separate menu.

### The generated draft lives in the story

A finished AI draft no longer opens in a separate review
box. It renders **inline in the manuscript**, right where it
will land, in a panel with a contrasting fill and outline so
it can never be mistaken for text that's already part of the
page. While it's sitting there you can **edit it directly**
— fix a line, delete a stray media request, rewrite a
sentence — before choosing **Insert / Replace**, **Retry**,
**Compare** (against the original passage), **Copy**, or
**Discard**. Earlier candidates from the same session stay
one tap away as **Earlier 1**, **Earlier 2**, and so on.

A line like `[MEDIA type=image query=… tags=…]` that
sometimes appears in a draft is not an image-generation
request — the AI is asking the app to search your **existing**
media library for a matching asset. Nothing is downloaded or
invented; if nothing in your library matches, the line is
simply dropped when you insert the text.

### Tap anywhere to place the caret

In Novel Write, tapping anywhere on a line of prose — not
only exactly on the text — moves the text caret there and
focuses that line. Tapping the empty space below the last
line moves the caret to the very end of the scene, so there
is always somewhere to tap to keep writing.

### Models and keys

Keys are stored encrypted in Settings (OpenRouter, OpenAI,
Anthropic, Gemini). Without a key everything except AI
generation still works. **Refresh models** in Settings →
Writing keeps the dock's model list current.

### Prompt Collection

Persisted mode templates (Novel, RPG, Chatting, Storyboard),
multi-select genre add-ons, age ratings from PG to X, and
**Refresh**, which rebuilds a read-only preview of the exact
effective system prompt.

### Usage

Every AI reply records prompt/completion tokens and cost;
the usage line under each composer shows the last call, and
Novel's context meter reads `context: used / limit` — a
live estimate for the chosen model, including the output
reserve and any attached image.

---

## Appearance

### Profiles

A profile is a whole look — palette, lettering and corners
together: **Classic**, **Fantasy**, **Arcade**, **Synthwave**,
**Chill**, **Tabletop**.

### Themes

Classic keeps all four moods (Light, Sepia, Dark, OLED). The
styled profiles carry their own mood and offer light or dark.

### Section colors

Per-section tints layered on top of any profile. **Reset
section colors** undoes them. Set them in Settings →
Appearance.

---

## Backup, Sync and Troubleshooting

### Import and export

**Import/Export** sits in the top bar. Novelcrafter ZIPs are
supported — codex folders become Characters, Locations,
Objects and Lore, and characters also become roleplay cards.
Novels export to EPUB.

### Backup

Settings → Backup schedules automatic local backups. Keep
them on; they are your safety net.

### Sync

No account, no cloud. Start the desktop companion, read the
password off the web hub, then Settings → Open web sync on
the phone. Leave **Auto-sync** on.

### MCP and CLI

Settings → Sync lists copy commands for **Cursor IDE**,
**Cursor CLI** (`agent mcp enable weaverse` after the same
`~/.cursor/mcp.json` entry), Claude Code, OpenCode, and Codex
CLI. Auth is the sync password as a Bearer token.

### Troubleshooting

- **AI replies fail** — check the key in Settings, the
  selected model, and the context meter; retry from the ✓
  hold-menu after a rate limit.
- **Nothing streams** — confirm a default model exists
  (Settings → Writing → Refresh models).
- **Lost text** — Undo covers most actions; backups cover
  the rest. The ⌫ hold-undo restores a just-deleted draft.
- **Crash log** — Settings → Show crash log, then Copy and
  send it along.

---

