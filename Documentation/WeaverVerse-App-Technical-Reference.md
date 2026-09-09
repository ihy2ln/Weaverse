# WeaverVerse Technical Reference

## Repository and runtime

The Android application is the `app` module, with shared Kotlin code in `sync-core`. UI is Jetpack Compose, persistence is Room, dependency injection is Hilt, and local artwork is resolved through the media repository. The project is built with Gradle 8.11.1 and Android Gradle Plugin 8.7.3.

The RPG implementation is under `app/src/main/java/com/ihy2ln/weaverse/feature/roleplay/`. RPG code may use shared core, media, AI, and character-sheet services, but RPG combat classes must not import `feature.roleplay.textgame` packages.

## RPG state model

The structured save envelope is the source of truth for RPG progression. Its major records are:

- `RpgCampaignState` — campaign identity, mode, rule system, map, party, companion, progression, encounter, outcome, recap, and startup state.
- `RpgStartupState` — wizard step, setup snapshot, plan answers, chapter outline, opening guideline, scene draft, generation status, request ID, and progress.
- `RpgCampaignRepository` — Room-backed serialization, restore, migration, and save operations.
- `RpgCombatRuleset`, `RpgCombatState`, `RpgEncounterSetup`, `RpgCombatAction`, `RpgCombatPreview`, `RpgCombatOutcome`, `RpgEnemyIntent`, and `RpgStatusEffect` — RPG-only combat contract.

Campaign setup writes both the typed mode/rule-system fields and compatibility lines in the authors note. Restore prefers the typed saved mode and accepts legacy IDs or labels when normalizing older saves. Text Game records remain outside this envelope.

## Four-screen startup state machine

`RpgStartupStep` is the durable state machine:

```text
Cyoa → GeneratingChapterPlan → ChapterPlan → Verification
                                      ↓
                               GeneratingScene → Started
```

Generation status is separately tracked as Idle, Generating, Complete, or Failed. Every generation request has a stable request ID, persisted progress, and a retry path. The request is idempotent at the save boundary: a completed opening scene is stored once under the request ID before the startup step becomes `Started`.

The planner operations are implemented in the roleplay view model and planner helpers: save/select/skip CYOA answers, randomize unanswered fields, generate or fall back to a chapter plan, edit outline fields, open verification, and generate or fall back to Scene One.

## AI boundary and metadata

AI output is parsed into structured planner or scene records. Hidden metadata is never shown as narration:

- `[[RPG_CHOICE|id=1..3|title=...|description=...]]` creates exactly three scene choice cards.
- `[[SCENE_ART:asset-id|category=...|mood=...]]` identifies a candidate local-art request.
- Startup and scene-advance markers remain internal bookkeeping.

Numbered prose, arbitrary outline text, and user-written numbers are not parsed as gameplay choices. The app owns action legality, deterministic rolls, HP, conditions, rewards, relationship consequences, and save transitions.

## Mode enforcement

`authoritativeRpgMode()` reads the restored typed campaign setup before constructing scene prompts and UI state. The prompt includes a mode-specific directive. Focused Tactical Cards explicitly forbids silently switching to d20; D&D d20 requests the existing sheet and deterministic roll service; Text Reactions keeps text actions primary while applying app guardrails. `RpgCombatRuleset.fromId()` accepts IDs, labels, and legacy descriptive values so old saves do not fall back unnecessarily.

## Local scene-media selection

`SceneMediaLibrary.find(SceneMediaRequest)` ranks local media by scene tokens, category, tags, requested tags, and display name. RPG scene publishing calls this service for opening and ordinary Dungeon Master replies when the document has no image block. The selected `MediaBlock` stores a stable media ID, allowing normal Room/media resolution and the existing scene-art controls to render it.

The AI is instructed to select only eligible local media IDs and never invent paths. Media lookup failure is non-fatal: narration and choices are still persisted.

## UI interaction contract

`AdventurePlayScreen` renders three mode-aware preset menus immediately above `UnifiedPromptBar`. The prompt remains the fourth custom action surface. Story prose is wrapped in a selection container with a custom text toolbar: selecting text copies the selected range and reveals the existing AddText dialog trigger. The button is hidden until selection and does not consume permanent scene space.

Planner and startup panes use independent scroll containers. Horizontal preset rows use `LazyRow`; the outer adventure content remains vertically scrollable. Startup hides the normal transcript, token strip, and scene-art panel until the player reaches normal gameplay.

## Tests

Focused unit coverage currently includes:

- RPG mode parsing and label/ID normalization.
- Card, d20, and text combat reducers.
- CYOA, chapter-plan, opening-scene parsing and fallback completion.
- RPG save creation, migration, persistence, and startup state.
- Scene choice/art marker parsing.
- Local scene-media ranking and category/tag behavior.

Recommended commands from the repository root (the bundled Gradle distribution avoids a network download):

```powershell
$env:GRADLE_USER_HOME = 'S:\AI\Novel\Weaververse\gradle-user-home'
$env:ANDROID_USER_HOME = 'S:\AI\Novel\Weaververse\android-user-home'
$env:ANDROID_HOME = 'S:\AI\Android'
& 'S:\AI\Novel\Weaververse\.gradle-dist\gradle-8.11.1\bin\gradle.bat' ':app:testDebugUnitTest' '-Pkotlin.compiler.execution.strategy=in-process'
& 'S:\AI\Novel\Weaververse\.gradle-dist\gradle-8.11.1\bin\gradle.bat' ':app:assembleDebug' '-Pkotlin.compiler.execution.strategy=in-process'
```

The current testing artifact is copied to `S:\AI\Novel\Weaververse\Beta.Test.Build\weaververse-rpg-card-game-debug.apk`. Verify it with `apksigner`, `zipalign`, and `aapt dump badging` before manual installation. Only this single testing APK should be handed to the user for the current workflow.

## Release/checkpoint procedure

1. Run focused tests and assemble the debug APK.
2. Copy exactly one APK to `Beta.Test.Build\weaververse-rpg-card-game-debug.apk`.
3. Record SHA-256, package, version, signing certificate, and test status.
4. Commit source, tests, documentation, and checkpoint notes together.
5. Push the branch and the annotated checkpoint tag to `origin` when the user requests a GitHub update.

The current debug artifact reports package `com.ihy2ln.weaverse.textgame`, version code `128`, version `1.3.87-beta-debug`, and Android Debug certificate signing. A live install test requires an attached Android device or running emulator; APK signature/alignment verification is still mandatory when no device is available.
