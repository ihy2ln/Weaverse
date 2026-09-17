# WeaverVerse 1.4.29 (155): app-wide cinematic theme

The Home screen's visual identity is now the default throughout the Android app: near-black backgrounds, charcoal surfaces, white sans-serif text, muted secondary labels and violet actions. Shared rails, tabs, chips, forms, prompt controls, cards, dialogs and menus use the active theme. Reader/writer defaults, manga browsing and image-editor chrome have matching styling. Launch windows also use the dark palette.

## Behavior and data

- An additive DataStore migration applies the new WeaverVerse profile once, including on existing installations. Later explicit theme changes persist. Existing alternate profiles remain in Appearance settings.
- The reader follows the app by default. Explicitly saved Paper/Sepia/Night selections remain available and are preserved.
- Custom content colors, artwork, game visuals and document formatting remain content choices. No manuscript conversion or database schema change was introduced in this update.
- Phone Chatting now switches between the conversation list and a full-width message view. Wider layouts retain multiple panes. Brainstorm uses a chat-picker sheet on phones and a sidebar on larger screens. Its context actions scroll horizontally. Reader Previous/Next actions occupy their own row.
- Existing Home/history/navigation work and unrelated checkout edits were retained.

## Verified

- Offline debug, instrumented-test and signed release builds succeeded.
- 14 JVM tests passed: appearance migration (2), book browsing (5), Novel composer (6) and composer workflow (1). Migration tests verify unrelated preferences remain intact and later user theme choices are not reset.
- Final Android device run passed both AppThemeDeviceTest and BookBrowserNavigationTest. Checks cover every enabled mode's entry screen, Settings, conversation open/return, Brainstorm picker dismissal, reader/writer resumption, original shelf/filter restoration, activity recreation, unchanged scene documents and absence of duplicate browsing navigation in workspaces.
- The mode/settings/navigation test also passed with system text at 130%; the emulator setting was restored to 100% afterward.
- The landscape Home/drawer/Settings return test passed separately.
- Screenshots are genuine Android renders on a 360-dp-wide MuMu Android 15 emulator. QA fixtures are confined to the separate `.homeqa` app. The ZIP contains normal portrait, 130% text and landscape renders.
- APK signature verified. Certificate SHA-256: `f16db5088b05af8c941a0e23eb2364dab470dd4ae07c3023f858708f6451fc05` (same existing certificate).
- Signed production-package update installed with `adb install -r` over 1.4.28 (154), without uninstalling or clearing data. Version is now 1.4.29 (155); firstInstallTime remained `2026-09-16 20:04:43`.
- Existing test book `Upgrade-Retention-1427` remained present. Its exact previously saved manuscript text, `This mnuscript ust surmive the update.`, was verified after the update.
- Release APK SHA-256: `d211b7fcc6cac576247610dfaec424f3f1a1f4d2a6807dd60f9ac2791031695f`.

## Verification limits and recovered failures

- An early UI test sent Back to the Activity instead of the modal window; the test now sends the system Back event and passes.
- The first 130% text run aborted in MuMu's native RenderThread: `Failed to acquire physical displays for WCG support!`. Starting the app to stabilize the display and repeating the test passed. The crash log is retained; that first run is not counted as a pass.
- This is not exhaustive verification of every game canvas, provider dialog, media codec, physical device or font-size setting. Specialist image-editor palette changes were compiled and reviewed but not separately exercised through a complete image-editing session.
- Full archive migration/restore tests passed for 1.4.28 and were not rerun for this theme-only update; no database/archive format changed here. This update's preference migration and actual in-place install were tested.
- No GitHub release was published. The APK is a local signed delivery.

## Evidence

Logs under `S:/AI/Novel/Weaververse/task-artifacts`: `theme-final-build.log`, `theme-device-final2.log`, `theme-large-font-retry.log`, `theme-landscape-tests.log`, `theme-release-final.log`, `theme-upgrade-before.txt`, `theme-upgrade-after.txt`, `theme-retained-reader.xml`, and `theme-crash-log.txt`.
