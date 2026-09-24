# Storyboard translation versions

## Behavior

- New translations automatically activate their edited output. Placement and verification warnings are informational, not approval gates. Missing images, unusable translations and actual processing failures still report errors instead of pretending success.
- The reader's review action is now Versions. Each successful run retains Original, Translation without cleanup (translated text over the untouched original image), and Translation with edits. Repeated runs append snapshots.
- Switching persists the selected version and snapshots manual changes to the outgoing version. Original source media is not overwritten. Bitmap/text snapshots use existing media storage and document JSON; no Room migration is needed.
- Legacy pages retain Original/current edits; a translation-only image cannot be reconstructed if it was never saved.
- The user was asked whether "without edits" means no artwork cleanup or pre-proofreading text. The current implementation uses the former; labels explicitly say "without cleanup".

## Verification

- Four version persistence/switching tests and thirteen existing translation-plan tests passed.
- Final debug compilation and signed, minified release build passed (v1.4.25, version code 151). Local APK: `Beta.Test.Build/weaverse-v1.4.25-translation-versions-signed.apk` under the workspace root.
- Full paid translation/provider round trip and new version-picker MuMu interaction have not been verified in this task. Do not imply that passing serialization tests verifies AI output quality.
- Existing unrelated gallery-login changes were preserved.
