# WeaverVerse RPG — Refined Game Design Document

## Vision

WeaverVerse RPG is an offline-capable, story-first adventure that combines visual-novel scenes, authored chapter choices, and three player-selectable combat modes. The app owns game state and rules; AI enriches scene framing, narration, enemy intent, and local art selection without bypassing authored progression or legality.

## Player-facing modes

| Mode | Experience | Rules authority |
| --- | --- | --- |
| Focused Tactical Cards | Adams Haven Card Game-style hand, targets, AP/EP, statuses, and visible enemy intent. | RPG card combat reducer |
| D&D d20 | Character sheets, deterministic d20 checks, modifiers, proficiency, AC, HP, and conditions. | RPG sheet and roll service |
| Text Reactions | Player-written or dictated actions with legal-action checks, optional risk previews, and narrated reactions. | RPG turn/health/reward service |

Mode controls the interaction and presentation. Rule system controls conventions and can be selected separately in campaign setup. An encounter may use a one-battle override; the campaign default is restored when combat ends.

## Adventure loop

1. Create a campaign from a setting template, setting-details preset or custom details, mode, rule-system, house-rules preset or custom rules, and characters.
2. Read the chapter scene and choose one of three authored options or a custom fourth path.
3. Follow the hybrid map: authored nodes form the chapter spine, while free exploration opens optional scenes that return to a known node.
4. When a scene introduces danger, review the encounter card (stakes, art, enemies, intent, party readiness, and mode), then confirm or override the combat mode.
5. Complete, retreat from, or surrender the encounter. The normalized outcome updates HP, conditions, rewards, companion consequences, recap, and map availability.
6. Save at every boundary and resume after force-close.

## Hybrid map

The map exposes locked, available, current, completed, and discovered nodes. Authored fallback nodes are shipped locally for reliable offline play. Freeform scenes are attached to a location and a return node; they cannot unlock an unavailable chapter node or advance the spine illegally. Completed nodes retain a summary, stakes, discovered art, and consequences.

## Combat design

All modes share `RpgCombatOutcome`, so the adventure layer applies results consistently. Card mode uses a shared party hand with AP/EP budgets, legal target previews, visible enemy intent, deterministic effects, and statuses such as stunned, guarded, bleeding, exposed, and empowered. D&D mode shows the d20, modifier/proficiency breakdown, target defense, and outcome. Text Reactions distinguish narrative actions from risky checks before AI narration. The AI never owns rolls, damage, HP, rewards, or turn legality.

## Character and relationship depth

The first progression layer is milestone-based. Encounters and major choices grant milestones that unlock authored abilities, equipment, or companion improvements. The companion panel shows relationship value, stance, recent approval/disapproval, consequence history, and availability. Relationship consequences can alter future scene proposals and encounter bonuses.

## Persistence and offline behavior

RPG state is stored in a versioned save envelope separate from Text Game saves. It includes map, exploration, party, companion, progression, encounter/combat, recap, and the campaign mode/rule-system selections. Older RPG saves migrate to D&D d20 while preserving existing progress; Text Game saves are untouched. Authored scenes and encounters remain playable offline, with AI variation treated as optional enrichment.

## Milestone acceptance target

One complete offline chapter must provide an authored map path, an optional exploration branch, one polished encounter in each mode, a visible companion consequence, deterministic rewards and milestone progress, force-close/resume, and a chapter recap.
