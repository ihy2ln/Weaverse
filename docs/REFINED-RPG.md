# WeaverVerse RPG — Refined Implementation Notes

This document is the compact engineering companion to the RPG design document. It describes the boundaries that keep the RPG modes independent from the legacy Text Game engine.

## State boundary

RPG campaigns use a versioned `RpgCampaignState` and Room-backed `RpgSaveEnvelope`. The state contains campaign mode and rule system, chapter/scene node, hybrid map and exploration state, party and companions, milestone progression, active encounter/combat, last normalized outcome, and chapter recap. Text Game save records remain isolated.

Core RPG operations are:

`createRpgCampaign`, `restoreRpgCampaign`, `saveRpgCampaign`, `selectRpgMode`, `listAvailableSceneNodes`, `enterFreeformExploration`, `returnToChapterNode`, `createEncounter`, `selectEncounterRuleset`, `previewCombatAction`, `confirmCombatAction`, `resolveCombatAction`, `endCombat`, `applyCombatOutcome`, `applyCompanionConsequence`, `advanceMilestone`, and `buildChapterRecap`.

## Mode and rule-system separation

Campaign setup presents Setting Template, Setting Details preset/custom details, Mode, Rule system, Additional House Rules preset/custom rules, then character and table preferences. The three modes are Focused Tactical Cards, D&D d20, and Text Reactions. The mode selects the player-facing loop; the rule system supplies RPG conventions. Encounter overrides are transient and never rewrite the campaign default.

Setting Template and Setting Details use a hierarchical main-section → theme → preset browser. Favorites are persisted independently for each catalog. The setting catalog includes the Adams Haven worlds; detail themes include fantasy, action media, survival, science fiction, 18+, Slow Life, and Overpowered Protagonist.

Players can create reusable entries in either catalog by supplying a name, main section, theme, and AI guidance. Custom templates and details presets persist locally, can be favorited, and can be removed without affecting campaigns that already copied their guidance into saved setup state.

## Combat contract

`RpgCombatRuleset`, `RpgCombatState`, `RpgEncounterSetup`, `RpgCombatAction`, `RpgCombatPreview`, `RpgCombatOutcome`, `RpgEnemyIntent`, and `RpgStatusEffect` are RPG-only types. Each mode resolves to the same normalized outcome contract so the adventure layer can update party HP/conditions, inventory and rewards, companions, factions/world changes, recap, and the next map node. No RPG combat class imports `textgame` packages.

## Setup catalogs

Backend catalogs provide starter setting details (Frontier, City, Wilds, War), house rules (Cinematic, Gritty, Heroic, Political), and custom entries. Preset identity is saved explicitly; custom text supplements or overrides guidance without erasing which preset was chosen.

During an active scene, Character cards and the Other RPG party action open the campaign player and active party outside combat. Focused Tactical Cards exposes ATK, DEF, SUP, SPD, AP, EP, and a signature card; D&D d20 exposes the six ability scores and modifiers. Both link to the full editable sheet. New, AI-created, and imported characters are initialized with populated d20 and tactical statistics.

## UI and offline rules

The map shows authored node availability and a clearly bounded Explore Freely branch. Encounter setup always shows title, stakes, scene art, enemy intent, readiness, current mode, and an optional override. Card actions explain cost, target, effect, and legality before confirmation. D&D checks expose the deterministic roll breakdown. Text Reactions show a risky-check preview before narration. Local authored fallback content keeps the loop playable without network access.

## Verification checklist

- New campaigns retain the chosen mode, rule system, presets, and custom guidance.
- Older RPG saves migrate without losing story, party, farming, inventory, or progression data; Text Game saves remain unchanged.
- Map availability, freeform return, encounter override reset, and force-close/resume work at setup, preview, combat, and recap.
- Card AP/EP, d20 modifiers, and Text Reaction legality all resolve deterministically and emit equivalent outcomes.
- Companion consequences, milestones, rewards, and chapter recap persist across saves.
