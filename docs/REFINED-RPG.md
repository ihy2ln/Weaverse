# WeaverVerse RPG — Refined Implementation Notes

This document is the compact engineering companion to the RPG design document. It describes the boundaries that keep the RPG modes independent from the legacy Text Game engine.

## State boundary

RPG campaigns use a versioned `RpgCampaignState` and Room-backed `RpgSaveEnvelope`. The state contains campaign mode and rule system, chapter/scene node, hybrid map and exploration state, party and companions, milestone progression, active encounter/combat, last normalized outcome, and chapter recap. Text Game save records remain isolated.

Core RPG operations are:

`createRpgCampaign`, `restoreRpgCampaign`, `saveRpgCampaign`, `selectRpgMode`, `listAvailableSceneNodes`, `enterFreeformExploration`, `returnToChapterNode`, `createEncounter`, `selectEncounterRuleset`, `previewCombatAction`, `confirmCombatAction`, `resolveCombatAction`, `endCombat`, `applyCombatOutcome`, `applyCompanionConsequence`, `advanceMilestone`, and `buildChapterRecap`.

## Mode and rule-system separation

Campaign setup presents Setting Template, Setting Details preset/custom details, Mode, Rule system, Additional House Rules preset/custom rules, then character and table preferences. The three modes are Focused Tactical Cards, D&D d20, and Text Reactions. The mode selects the player-facing loop; the rule system supplies RPG conventions. Encounter overrides are transient and never rewrite the campaign default.

## Combat contract

`RpgCombatRuleset`, `RpgCombatState`, `RpgEncounterSetup`, `RpgCombatAction`, `RpgCombatPreview`, `RpgCombatOutcome`, `RpgEnemyIntent`, and `RpgStatusEffect` are RPG-only types. Each mode resolves to the same normalized outcome contract so the adventure layer can update party HP/conditions, inventory and rewards, companions, factions/world changes, recap, and the next map node. No RPG combat class imports `textgame` packages.

## Setup catalogs

Backend catalogs provide starter setting details (Frontier, City, Wilds, War), house rules (Cinematic, Gritty, Heroic, Political), and custom entries. Preset identity is saved explicitly; custom text supplements or overrides guidance without erasing which preset was chosen.

## UI and offline rules

The map shows authored node availability and a clearly bounded Explore Freely branch. Encounter setup always shows title, stakes, scene art, enemy intent, readiness, current mode, and an optional override. Card actions explain cost, target, effect, and legality before confirmation. D&D checks expose the deterministic roll breakdown. Text Reactions show a risky-check preview before narration. Local authored fallback content keeps the loop playable without network access.

## Verification checklist

- New campaigns retain the chosen mode, rule system, presets, and custom guidance.
- Older RPG saves migrate without losing story, party, farming, inventory, or progression data; Text Game saves remain unchanged.
- Map availability, freeform return, encounter override reset, and force-close/resume work at setup, preview, combat, and recap.
- Card AP/EP, d20 modifiers, and Text Reaction legality all resolve deterministically and emit equivalent outcomes.
- Companion consequences, milestones, rewards, and chapter recap persist across saves.
