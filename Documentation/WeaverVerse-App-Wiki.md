# WeaverVerse App Wiki

## What WeaverVerse is

WeaverVerse is an offline-capable Android writing and roleplaying workspace. It combines a library, character and campaign setup, AI-assisted roleplay, visual-novel-style scenes, local artwork, prompt tools, and the separate Adams Haven Text Game. The application owns saved state and gameplay rules. AI provides framing, prose, suggestions, and art tags; it does not own health, rolls, rewards, legality, or persistence.

## Main navigation

The top navigation is organized around the current workspace:

- **Library** — books, campaigns, pages, and imported material.
- **RPG** — character sheets, campaign setup, adventure planning, scenes, companions, and RPG combat.
- **Adventure** — scene progression and the Dungeon Master conversation.
- **Extra** — supporting tools, settings, media, and import/export workflows.

The in-app back arrow returns to the previous screen without discarding a draft. Android back follows the same navigation boundary where the host screen supports it.

## Creating an RPG campaign

Campaign setup is intentionally hierarchical. Values selected here are written into the campaign and are authoritative for the adventure planner and scene generator.

1. Choose a **Setting Template**.
2. Choose **Setting Details** or write custom details.
3. Choose a player-facing **Mode**.
4. Choose the underlying **Rule system**.
5. Choose **Additional House Rules** or write custom rules.
6. Select characters, point of view, tense, and whether the player is the Adventurer or Dungeon Master.

### Setting templates

Setting Template opens a three-level browser: **Main section → Theme → Preset**. Main sections include Adams Haven, Fantasy, Horror & survival, Science fiction, Games & media, Other worlds, and Custom. The Adams Haven section includes Elysium Vale, Aqualuria, Arcanis, Elysara, Heroica, Mythoria, Nexara Prime, Veridian, and Serenity Atoll. Other built-ins include High fantasy, Dark fantasy, Sword & sorcery, Highschool of the Dead-inspired, The Walking Dead-inspired, World War Z-inspired, Original zombie apocalypse, and additional genre templates. Inspired zombie templates are written for fictional survival play and do not sexualize minors.

Tap the ☆ on a preset to add it to the persistent **Favorites** main section. Favorites can contain built-in or custom setting templates and remain available in both new-campaign setup and the in-campaign Setup editor.

Use **+ Add setting template** or **+ Add details preset** inside the appropriate browser to create reusable entries. The editor records a name, main section, theme subsection, and AI guidance. Saved entries appear immediately, survive app restarts, support Favorites, and expose a Remove action. Older custom setting templates migrate into Custom → Saved templates.

### Setting-details presets

Setting Details uses the same Main section → Theme → Preset navigation and its own Favorites list. Presets include Frontier settlement, Factional city, Wilderness expedition, War-torn realm, Stormbound coast, Sky-island frontier, Under-city intrigue, Arcane academy, Reclaimed wasteland, Fey borderlands, Clockwork metropolis, Stars beyond the veil, Slow Life, Overpowered Protagonist, action movie/anime/superhero themes, and adult-only consent-forward options such as Porn with plot, Smut romance, Hentai fantasy, and Ecchi comedy. Adult presets are grouped under 18+ and are guidance for fictional adult characters; provider or platform restrictions still apply.

### House-rule presets

- **Cinematic and forgiving** — failures create complications instead of stopping momentum.
- **Gritty survival** — scarcity, injuries, travel pressure, and lasting consequences matter.
- **Heroic fantasy** — teamwork, clever plans, and decisive risks are rewarded.
- **Factions and consequences** — reputation, promises, and public choices affect later scenes.
- **Custom house rules** — the player’s own guidance is stored and applied.

## Modes and rules

Mode controls how the player interacts with the adventure. Rule system controls the conventions used underneath. They are saved separately.

| Mode | Player experience | Rules authority |
| --- | --- | --- |
| **Focused Tactical Cards** | Adams Haven-style cards, targets, AP/EP, visible intent, and statuses. | RPG card reducer; never silently changes to d20. |
| **D&D d20** | Character sheets, ability modifiers, proficiency, AC, HP, conditions, and visible deterministic checks. | RPG sheet and roll service. |
| **Text Reactions** | Written or dictated actions, legal-action checks, risky-action previews, and narrated reactions. | RPG turn, health, status, and reward service. |

An encounter can temporarily override the campaign mode. After victory, defeat, retreat, or surrender, the saved campaign mode is restored for the next encounter. Older RPG saves default to D&D d20 during migration; Text Game saves are not migrated or rewritten.

## Adventure startup: four screens

### 1. Create Your Own Adventure

This is the editable planning screen directly after Campaign Setup. It contains six question cards:

1. Plot premise or central conflict.
2. First party goal.
3. First scene or location.
4. Starting party.
5. Tone and presentation.
6. Opening complication or threat.

Each card supports a multiline answer, campaign-aware suggestions, horizontally scrollable presets, and **Skip**. A skipped answer is saved as an explicit blank marker and can be filled by the authored fallback or AI outline. The normal prompt writer remains available for custom wording, but it is not required to answer the cards.

### 2. Chapter One Plan

The app generates a structured, editable rough outline and opening-scene guideline. Progress is monotonic from 1–100% and the screen advances only after validation and persistence. Missing or malformed AI fields are completed with local authored defaults, so the plan can continue offline.

### 3. Verify Your Adventure

Review Campaign Setup, CYOA answers, the chapter outline, and the opening-scene guideline together. Each section can be edited independently. Verification is required before scene generation; if generation fails, Retry and Use Authored Fallback remain available.

### 4. Scene One

Scene One is the actual playable beginning. Planning text is kept out of the narration. The AI receives the accepted plan as private guidance and returns three actionable choices plus a fourth custom path through the prompt bar. Later choices can revise only unfinished chapter beats; completed history remains authoritative.

## Scene controls

During an RPG scene:

- **Three choice cards** show the current AI-proposed directions.
- **Actions** offers practical interactions such as observing, moving, using an item, helping, or waiting.
- **Thoughts** offers internal reasoning, recalling knowledge, studying intentions, considering risks, and focusing on a detail.
- **Other RPG** offers speaking, questioning, persuading, searching, preparing for the selected mode, and checking the party.
- **Your own action** accepts any custom player wording. The app still enforces the active mode and its rules.
- **Add text to…** appears after the player selects story text. It opens the existing text-capture popup rather than occupying the scene controls permanently.
- **Character cards** opens a horizontal gallery of the player and active party. Each playing card shows portrait art, class/level, HP, AC, and a summary, and can open the full editable character sheet.

The scene layout keeps artwork and story/action content balanced for portrait, landscape, and narrow phone screens. Setup panes and planner lists scroll independently so no question or action is clipped.

## Scene art

The AI emits scene-art tags and may name an eligible local asset, but never invents a file path. The app ranks images in its local media library against scene prose, campaign setting, category, mood, and tags, then attaches the best matching image to the saved scene document. The user can still use the Scene art control to inspect, collapse, resize, or replace artwork. If no local match exists, the scene remains playable without blocking narration.

## RPG progression and persistence

RPG saves contain campaign mode and rule system, chapter/scene state, map and exploration state, party and companions, milestones, encounter/combat state, rewards, relationship consequences, recap, startup plan, outline, scene draft, and generation status. Save and resume are supported during setup, generation, verification, active combat, and post-combat recap.

Milestones are the first progression layer. Encounters and major choices can unlock authored abilities, equipment, or companion improvements. Companion panels expose relationship value, stance, approval/disapproval, consequence history, and availability.

## Offline behavior

Authored fallback questions, outlines, scenes, map nodes, and encounters ship locally. AI enrichment is optional. If a model is unavailable, the user can use the authored fallback and continue a valid campaign. The selected model can be changed in the adventure setup and is locked only while a generation request is running.

## Text generation endings

The selected maximum word count is a target, not a destructive hard cut. Every shared AI prompt instructs the model to complete its final sentence. If the response crosses the target while finishing that sentence, the app permits a small overrun; if trimming is still needed, it stops at a detected sentence boundary instead of leaving cut-off prose.

## Troubleshooting

**The selected mode changed to D&D d20.** Reopen Campaign Setup and confirm the Mode row. The campaign stores the mode ID separately from the prose setup note; current scene prompts and scene UI read that saved ID. Existing label-form saves are normalized during restore.

**Chapter Plan says the AI response is invalid.** Tap Retry, or choose Use authored fallback. The parser accepts common JSON field aliases and fills missing fields locally. The screen should advance to verification only after a valid plan is saved.

**No scene image appears.** Confirm the image is in the app’s local media library and categorized/tagged. Art is optional and never prevents the scene from loading.

**The planner appears stuck.** Progress is persisted with the request ID. Force-close and resume; an interrupted generation is marked failed and exposes Retry. The authored fallback remains available.

## Text Game boundary

Adams Haven Text Game is a separate mode and save system. RPG combat, scene choices, planner state, and RPG saves do not import Text Game packages or reducers. Existing Text Game behavior remains unchanged.
