# Weaverse v1.4.17 Manga Translation Sizing — Checkpoint

Date: 2026-09-15

## Current branch

`cursor/manga-translation-sizing-764a`

This branch continues from the recoverable v1.4.16 source point
`f961b2c` (`feature/adams-haven-art-media-packs`).

## Implemented fixes

- `ImageOps.insetNormalizedBox()` keeps burned English lettering inside the
  usable center of curved speech bubbles. The current frame fractions are
  `0.84` width and `0.78` height.
- `RoleplayChatViewModel.typesetOntoPage()` still cleans the full detected
  source region, expands it to the bubble, then typesets only inside the
  inset frame.
- `TextOverlayLayer` uses `TextMeasurer` and a binary search to fit preview
  text within its available width and height instead of relying on a fixed
  `sp` size.
- Optional bundled sample import failures are logged and skipped so a legacy
  sample ZIP cannot crash the app before the user's library opens.

## Verification

Focused tests:

```text
./gradlew :app:testDebugUnitTest \
  --tests 'com.ihy2ln.weaverse.core.media.ImageOpsPanelDetectionTest' \
  --tests 'com.ihy2ln.weaverse.feature.roleplay.chat.PanelAiTranslationTest'
```

Result: 15 tests passed, 0 failed.

Builds:

- `:app:assembleDebug` — successful
- `:app:assembleRelease` — successful
- Version code: `143`
- Version name: `1.4.17`

APK paths:

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release.apk`

## Device status

The debug APK installed and the app now launches on the API 30 emulator after
the sample-import guard. Visual verification could not be completed because
the fresh install has no manga chapters and the Storyboard workspace selector
did not accept taps in the headless emulator. Therefore the `0.84 × 0.78`
fractions remain unverified against a real translated page and may need tuning.

## Next session

Use an emulator/device with an existing downloaded manga chapter or import a
test page into Storyboard, then verify:

1. multi-line English stays inside the curved bubble at normal and zoomed
   scale;
2. source lettering is removed without damaging the bubble;
3. the editor preview and burned lettering agree on sizing.

