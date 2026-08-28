# Weaverse Helper Guide

This guide describes the Android app at the
`checkpoint-v1.3.2-codex-navigation` checkpoint. Weaverse is an offline-first
creative workspace with five modes over one shared library: Novel, RPG,
Chatting, Storyboard, and Notes. AI generation needs OpenRouter; editing,
reading, organizing, and local media continue to work offline.

## Quick start

1. Tap the book icon to open Home.
2. Choose a mode. Its main button always opens that mode's home:
   - **Novel** → **Bookshelf**
   - **RPG** → **Campaign**
   - **Chatting** → **Chats**
   - **Storyboard** → **Window**
   - **Notes** → shared notes board
3. Create or select a work from its shelf.
4. Use the second menu for the current mode's work areas.
5. Use **Extra** for shared tools such as Codex, Prompts, Notes, Snippets,
   Chats, and Pictures.

Work is saved as you edit. Import and export remain the safest way to make a
portable backup before large changes.

## Navigation and customization

The top chrome has three logical groups:

1. **Mode**: Novel, RPG, Chatting, Storyboard, Notes.
2. **Workspace**: destinations belonging to the active mode.
3. **Extra**: shared tools usable across works and modes.

Main mode buttons return to the mode home instead of leaving you in a deep
editor. Menu items can be reordered with press-and-hold: hold the item, choose
the move action, then drag it into its new position. This works for the visible
menu bar and items inside drop-down menus. Navigation order is kept in app
preferences.

## Home and shelves

Home is intentionally small: it launches the five modes. Active work belongs
on its mode shelf rather than in a duplicate “continue where you left off” area.

Bookshelf, Campaign, and Window share these interactions:

- Tap a card to open it.
- Use the add button to create a novel, campaign, or storyboard.
- Press and hold a card for its context menu.
- Use **Select for quick remove** to build a multi-selection, then **Quick
  remove** to remove the selected works together.
- Novel cards also expose export, copy, cover art, and delete actions.

Deletion asks for confirmation. Export first when the work may be needed later.

## Novel

### Bookshelf

Bookshelf is the Novel home. It shows cover art and titles and is the place to
select an existing novel or add one. Opening Novel from the main mode button
returns here.

### Plan

Plan organizes the manuscript as **Book → Chapter → Scene → Scene beat**. Use
it to structure the story, set scene information, and choose what Write edits.

### Write

Write is the primary manuscript editor. It supports scene text, scene beats,
Codex mention highlighting, media blocks, speech input, and AI/manual prompt
entry. Long-press selected text for editing actions, including adding it to the
Codex. Tapping a highlighted Codex mention opens that entry.

Media inserted in Write is stored with the manuscript and appears in reading
and review surfaces. If imported media does not display, confirm the source
still exists and make a fresh export before reinstalling the app.

### Read and review

The reader provides page and chapter navigation plus jump-to-top and
jump-to-bottom controls. By default, changing pages moves to the top. Enable
the keep-position format option when comparing matching areas across pages.

## Codex

The Codex is shared across every book and mode. It is for structured reference
material—characters, locations, objects, lore, and custom categories—while
Notes is for flexible personal writing.

### Browsing all entries

Open **Extra → Codex**. With no entry selected, category sections and entries
use the available screen. Tap a category heading to expand or collapse it. Use
**+** on a category to add an entry there.

### Working inside an entry

When an entry opens:

- The shared status (`Shared · entry count · every book & mode`) moves into the
  title band.
- A compact, horizontally scrollable category strip appears below it.
- Only the active category and that category's entries occupy the strip.
- Tap the category name—**Characters**, **Locations**, **Lore**, and so on—to
  open a popup containing every category and its entry count.
- Select a popup category to jump directly to it without repeated dragging.
- Tap an entry in the strip to switch entries. The selected entry stays
  highlighted.
- Tap **+** to create an entry in the active category.

The divider below the strip is draggable. Drag it downward to reveal more
Codex entries and upward to return to the one-line view. The editor toolbar has
a single collapse/expand chevron in place of the old back and forward buttons;
it restores the last useful expanded height.

Each entry can store a name, aliases or nicknames, entry text, and supporting
media. Names and aliases are used for Codex mentions in writing surfaces.

## Notes

Notes is one shared board across every book and mode. Open Notes from the main
mode button or **Extra → Notes**. The list occupies the workspace until a note
is selected; opening one reveals its editor while keeping navigation nearby.
Notes do not show the writing prompt dock because they are edited directly and
are not a generation play surface.

Use Notes for research, checklists, loose ideas, and information that should not
become a structured Codex entry. Speech-to-text is available in the editor.

## RPG

RPG opens at **Campaign**, the high-level shelf for all adventures. Its areas
include Campaign, Adventure, Inventory, Roster, Lore, and Presets.

| RPG concept | Manuscript concept |
|---|---|
| Adventure | Book |
| Day | Chapter |
| Mission | Scene |
| Event or battle | Scene beat |

### Creating a campaign

New Campaign asks for a campaign title and setting, then lets you select one or
more **Main character(s)** from your personas, Roster, and Characters Codex.
Tense is selected rather than typed. House rules begin with a tabletop-system
template—D&D 5e, Pathfinder 2e, D&D 3.5e, OSR/B/X, Powered by the Apocalypse,
Fate Core, or Custom/systemless—and can be extended with campaign-specific
rulings, tone, and boundaries.

### Campaign and Adventure

Campaign shows every adventure by title and art. Choose one to enter its
Adventure play session. Adventure is not a messenger: one large scene image
anchors the top of the page, the session unfolds below as narrative prose with
player actions clearly marked, and the action entry remains at the bottom.
Generated game-master responses receive the selected main characters, tense,
rules template, house rules, and difficulty as session context.

### Character sheet

The character sheet keeps tabletop information on one screen as compact,
illustrated sections. Tap a section name or picture to expand it; tap again to
collapse it. Sections cover identity and portrait, health and death, ability
scores, saves and skills, combat, spells, features and traits, inventory,
equipped gear, resources and tools, biography, and settings. Health includes
current, maximum, and temporary hit points plus quick change controls.

### Inventory, roster, lore, and presets

- **Inventory** groups You, Team, and Roster. Open a character to manage their
  pack and equipment slots.
- **Roster** controls personas and current companions without deleting the
  wider cast.
- **Lore** is the current campaign reference; the full shared Codex is under
  Extra.
- **Presets** change world difficulty: Slice of life, Normal, Hard, and
  Ruthless. They add behavioral direction to play rather than merely changing
  randomness.

## Chatting

Chatting opens at **Chats**, with **Friends/Contacts** for starting new
conversations. The layout follows a modern messenger: search, filters, unread
counts, avatars, speaker colors, timestamps, grouped messages, and day dividers.

The chat list is collapsible so the conversation can use the screen. Add chats
from Friends/Contacts. Press and hold a chat for its actions, including select
or unselect for quick removal; selected chats can be removed together. Opening
a character for the first time creates a conversation and may seed a greeting.

## Storyboard

Storyboard opens at **Window**, the cover-art shelf for all manga and comic
projects. Tap a title or its main art to open its Manga or Comic canvas.

- **Manga** uses right-to-left reading order.
- **Comic** uses left-to-right reading order.
- Page tabs sit above the canvas; add, rename, remove, or reorder pages there.
- Layout templates show their complete panel grid when selected, including
  full-page, strip, split, establishing, and multi-panel arrangements.
- Dropped media fills the first available panel at that panel's size.
- Select and drag a panel to move it; use its corner grip to resize it.
- Dropping one panel over another stacks them.
- **Adjust image** pans and zooms media inside the frame without changing the
  panel itself.
- Text is an overlay. Captions and speech bubbles can be moved and resized
  independently from panels.

The creation dialog uses comic/manga language: series title, reading style,
main art, and page-oriented setup.

## Prompt dock and AI

The full prompt dock appears only where generating or entering prose is useful:
Novel Write and applicable RPG, Chatting, or Storyboard play/creation surfaces.
It is not shown on Bookshelf, Campaign, Window, reader pages, Notes, Codex, or
other management screens.

When expanded, prompt text sits above one compact control row:

- **PROMPT** is the small collapse control at the top-left.
- **W minimum–maximum** sets the requested generation range. The app asks the
  model to stay within the maximum, although model output cannot be guaranteed
  with mathematical precision.
- **Model** opens the OpenRouter model selector. Long names scroll within their
  available space.
- **/A · \\M** is one toggle: A selects AI generation and M manual entry.
- **✓** accepts, **×** clears or cancels, and the microphone starts speech.

When collapsed, the dock becomes a small **PROMPT** label and triangle so it
does not cover the workspace. Add an OpenRouter API key in Settings before AI
use. Manual entry and all non-AI editing remain available without a key.

## Import, export, sync, and recovery

### Import and export

Top-bar Import and Export cover novels, RPG data, and shared Notes. A
NovelCrafter export containing `novel.docx` or `novel.md` plus character folders
is supported. Codex folders map into Characters, Locations, Objects, and Lore;
characters can also become roleplay/chat cards.

For a durable backup:

1. Export the work and its project extras.
2. Keep the exported ZIP or JSON outside the app's private storage.
3. Verify the file exists before deleting, reinstalling, or clearing app data.

### Peer-to-peer sync

1. Start the Weaverse desktop package. It opens the local web hub.
2. Note the one-time password shown by the hub.
3. On Android, open **Settings → Open web sync** and enter the password.
4. Leave Auto-sync enabled, or use Push/Pull manually.

Sync uses your Wi-Fi or a tunnel you control; it does not require a Weaverse
account or hosted Weaverse cloud.

### Installing test APKs

Debug/test and release builds can use different Android application IDs and
signing keys. A test APK may install beside a release build. If Android reports
an incompatible signature, export first, uninstall only the conflicting build,
then install the new APK and restore the export.

## Troubleshooting

### I can only see one Codex category

Tap the visible category word in the compact strip. The popup lists every
category and its entry count. Select the one you need. Drag the divider down if
you want the full category list visible while editing.

### The prompt dock is missing

This is expected on shelves, readers, Notes, Codex, and management screens.
Open a Write or play surface. If condensed, tap the small PROMPT label and
triangle.

### AI generation does nothing

Confirm the OpenRouter key and selected model in Settings, check connectivity,
and make sure AI mode (/A) rather than manual mode (\\M) is selected. Local work
remains usable offline.

### Media does not appear

Confirm the source media has not been moved or removed, reopen the work, and
check Pictures. For imported projects, export a backup and re-import if the
archive did not include media assets.

### A shelf or chat is crowded

Press and hold an item and choose **Select for quick remove**. Select additional
items, then use **Quick remove**. Review the confirmation carefully and export
anything you may want later.

### The interface no longer matches this guide

Check the installed version and compare it with the latest GitHub release. This
guide is anchored to `checkpoint-v1.3.2-codex-navigation`; later builds may move
controls while retaining the same concepts.

## Further documentation

- [Architecture and rebuild reference](ARCHITECTURE.md)
- [Checkpoint record](CHECKPOINT-v1.3.2-CODEX-NAVIGATION.md)
- [Wiki-ready documentation](wiki/Home.md)
- [Build and release log](../BUILD_NOTES.md)
