# Hard Checkpoint: v1.3.2 RPG Adventure

Date: 2026-08-28  
Tag: `checkpoint-v1.3.2-rpg-adventure`

## Purpose

This checkpoint preserves the first RPG-focused update after
`checkpoint-v1.3.2-codex-navigation`. It is a source recovery point rather than
a public release tag.

## Included behavior

- New Campaign uses **Main character(s)** rather than “Whose eyes.”
- Main characters can be selected from personas, Roster, and Characters Codex.
- Past, Present, and Future tense are selectable.
- House-rule templates cover D&D 5e, Pathfinder 2e, D&D 3.5e, OSR/B/X,
  Powered by the Apocalypse, Fate Core, and custom/systemless play.
- Additional house rules remain editable and are included in game-master AI
  context together with characters, tense, and difficulty.
- Each campaign owns a dedicated Adventure session; legacy campaigns receive
  one when opened.
- Adventure is an illustrated prose play screen with one large scene image, a
  story/action record, and persistent bottom action entry—not messenger chat.

## Verification

- `:app:compileDebugKotlin`: passed.
- `:app:assembleDebug`: passed.
- `CampaignSetupTemplatesTest`: 2 tests passed.
- Full suite: 220 passed and one unrelated existing assertion failed in
  `WritingModelSeedsTest.noApiKeyHasHelpfulMessage`.

Local test APK:

`S:\AI\Novel\Weaververse\Weaverse-Test-v1.3.2-beta-RPG-Adventure.apk`

SHA-256:

`7D4412129F82CEEB469C0D8C26F75AD486D0E63690C552E57A0683157A38EDD3`

## Recovery

```bash
git switch -c recovery/rpg-adventure checkpoint-v1.3.2-rpg-adventure
```

Git restores source code, not Android app data. Export app data before
uninstalling, clearing storage, or installing a build with incompatible signing.
