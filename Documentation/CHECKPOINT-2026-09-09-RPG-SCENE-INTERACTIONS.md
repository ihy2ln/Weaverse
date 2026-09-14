# Hard Checkpoint — RPG Scene Interactions

Date: 2026-09-09

This checkpoint captures the current WeaverVerse RPG work before the next feature pass.

## Included

- Structured RPG campaign/startup persistence and four-screen CYOA → chapter-plan → verification → Scene One flow.
- Campaign-aware setting, mode, rule-system, and house-rule setup.
- Focused Tactical Cards, D&D d20, and Text Reactions as separate RPG modes.
- Saved-mode authority and label/ID normalization to prevent accidental D&D fallback.
- Three scene action preset groups: Actions, Thoughts, and Other RPG.
- Three hidden AI choice markers plus a custom fourth action through the prompt bar.
- Selection-triggered Add Text behavior.
- Local scene-media ranking and automatic artwork attachment for opening and later RPG scenes.
- Planner, combat, startup, save, and media unit coverage.

## Verification at checkpoint

- Focused RPG/media unit tests: passed.
- Debug APK assembly: passed.
- APK v2 signature verification: passed.
- APK zip alignment: passed.
- APK package: `com.ihy2ln.weaverse.textgame`.
- Testing artifact: `S:\AI\Novel\Weaververse\Beta.Test.Build\weaververse-rpg-card-game-debug.apk`.

The APK is intentionally kept outside the Git source commit and is copied to the stable testing folder as the single user-facing build. Live device installation must be repeated when an Android device or emulator is connected.

## Boundaries

This checkpoint does not merge or rewrite the legacy Text Game engine. Existing unrelated worktree changes are preserved in the same source checkpoint because the requested hard commit is intended to make the current project state recoverable.
