# RPG, Chatting, and Storyboard

## RPG

RPG opens at Campaign, the high-level shelf. New Campaign selects main
characters from personas, Roster, and the Characters Codex, plus narrative
tense and AI-backed setting and rules templates. Their full directives are
stored with the campaign, so High fantasy + D&D 3.5e supplies both a
dungeon-fantasy world framework and that system's check, save, combat, and
magic conventions.

Campaign setup also chooses who runs the table and how the story is viewed.
Character(s) mode keeps the AI as game master. Dungeon Master mode gives the
user authority over scenes, NPCs, and rulings while the AI plays the selected
party. POV choices include third-person multiple/limited, rotating first
person, second person, omniscient ensemble, and cinematic. The Past, Present,
and Future controls use a tighter single-row layout.

### RPG modes, rule systems, and presets

The setup hierarchy is Setting Template, Setting Details preset (or custom
details), Mode, Rule system, Additional House Rules preset (or custom rules),
then characters and table preferences. Mode is the player-facing interaction:

* **Focused Tactical Cards** is the Adams Haven Card Game-style combat mode,
  with a shared hand, AP/EP costs, target previews, statuses, and visible enemy
  intent.
* **D&D d20** is the character-sheet mode, showing deterministic d20 rolls,
  modifiers, proficiency, AC, HP, conditions, and outcome explanations.
* **Text Reactions** is the traditional text-first mode, with legal action
  validation and a check preview before risky actions.

Rule system remains a separate RPG convention selector. An encounter can use a
one-battle override from its scene card (title, stakes, scene art, enemy intent,
party readiness, and current mode); ending the battle restores the saved
campaign default. All modes write the same normalized outcome so rewards,
health, companions, milestones, map progress, and chapter recap stay coherent.

Setting Details presets currently include Frontier, City, Wilds, and War.
Additional House Rules presets include Cinematic, Gritty, Heroic, and Political.
Preset identity is saved alongside custom text, so custom guidance supplements
or overrides the preset without losing its source label.

RPG saves use a versioned structured campaign envelope, separate from Text Game
saves. The hybrid adventure map exposes authored chapter nodes plus a bounded
Explore Freely branch that returns to a known node. Authored fallback scenes and
encounters keep the chapter playable offline; AI varies prose and local scene
art but cannot skip locked nodes or own rolls, damage, rewards, or legality.

Pick a campaign to enter Adventure, its dedicated play session. Adventure is
an illustrated story rather than a messenger: a single large scene image sits
above the narrative, player actions are marked in the story, and the action
entry stays at the bottom. The game master receives the chosen characters,
tense, rules template, extra house rules, and difficulty as context.

Each action also receives a hidden system-appropriate roll (d20, PbtA 2d6, or
Fate 4dF). The game master decides whether the roll applies and shows the
resolved outcome label and narrative consequence while the raw die, notation,
and DC remain private. It keeps the scene going until the player asks to advance or
a decisive transition occurs. Previous browses an earlier saved scene, Next
moves forward or advances from the latest scene, and Stay here—or an action
such as “do not advance”—overrules a transition.

The character sheet keeps tabletop information on one screen. Each category has
a name and reference picture; tap either to expand. It includes health/death,
ability scores, saves/skills, combat, spells, traits, inventory, equipped gear,
resources/tools, biography, and settings. Health supports current, maximum, and
temporary hit points.

Roster uses portrait cards with class, level, HP, and AC summaries; tapping a
character opens the full stat sheet, while Inventory & gear opens that same
character's linked equipment record. Inventory opens at the top with Writer /
You and the wider roster collapsed; Team roster remains visible. Editable tags
separate the wider cast into NPCs, Enemies, and Other. Equipment uses one item per head, torso,
arms, legs, weapon, accessory, or backpack slot. Slot-matched add templates and
picture controls are available directly on the equipment plate. An equipped
backpack opens an expandable inventory sized by its capacity; carried items
consume configurable space and show pictures, empty slots, and overflow. New
items can receive a picture in the Add item window. That picture follows the
item between pack and equipment, and tapping it opens replace/remove controls.
Town is a grid of tappable shops and landmarks, each with a persistent slot for
the user's own location art. Shop interiors use a game-style catalog with unit
prices, live totals, editable quantities, minus/plus controls, and AI fill for
a suggested amount. Buying sends the selected quantity to the active
character's linked inventory.

## Chatting

Chatting opens at Chats. Friends/Contacts starts conversations; Chats provides
search, filters, unread badges, a collapsible thread sidebar, avatars, speaker
colors, timestamps, grouped bubbles, and day dividers. Hold a chat for its menu
and use selection plus Quick remove for batch cleanup.

## Storyboard

Storyboard opens at Window, a cover-art view of manga and comic projects. Tap
main art or title to enter the canvas. Manga reads right-to-left; Comic reads
left-to-right.

Selected templates draw their full panel grids. Pages can be added, renamed,
removed, and reordered. Media fills panels and can be moved, resized, stacked,
panned, or zoomed. Captions and speech bubbles are independent overlays. The
create dialog asks for comic/manga-oriented information such as series title,
reading style, and main art.
