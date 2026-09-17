# Home and unified navigation preview

## Implemented

- Dedicated cold-launch Home with all six modes, saved mode ordering, horizontal poster shelves, clickable mode headings, empty states, and retained scroll positions.
- Up to ten actual accesses per mode; current metadata and artwork are resolved from the database. Mixed shelves distinguish content types. Covers resume supported scene, session, thread, note, and offline manga reading locations.
- Room schema 25 adds device-local `home_access` through migration 24 → 25. Missing targets are filtered before applying the ten-item limit. Removing or clearing history does not delete content.
- Unified navigation drawer with modes, active-mode destinations, shared tools, reordering, accessible actions, consistent violet accents, and sans-serif typography. Home is available inside specialized workspaces. Nested Back handling precedes the exit confirmation.
- New installations default to Dark; explicitly stored theme selections remain honored. Home and navigation adapt to light profiles and custom backgrounds.
- Notes uses independent destination state. Explicit note opens are recorded after resolving their IDs, avoiding stale selection and automatic-selection history entries.
- Mode-heading entry explicitly resets session and detail selections. History read/update operations run in a Room transaction so overlapping generic opens cannot erase a saved resume position.

## Validation

- `:app:testDebugUnitTest --tests '*HomeHistoryTest' :app:assembleDebug :app:assembleDebugAndroidTest -PqaApplicationIdSuffix=.homeqa --offline --max-workers=1` succeeded.
- Four JVM tests passed: per-mode limits/order, deduplication, mixed-type identity, and empty history.
- Follow-up build passed with all four JVM tests. Concurrent history updates (20 rounds of overlapping writes) and app-level mode navigation/restoration passed together on the updated preview (`OK (2 tests)`, 22.118 seconds). This adds a tenth distinct device test to the earlier coverage.
- Six Home component/device tests passed: empty mode access, cover and history actions, persistence/removal/deleted targets, migration preservation, unified-menu routing, and enlarged text with missing artwork.
- App-level cold launch, all six mode routes, Home return, and activity recreation passed. Landscape navigation passed in a separate targeted run. The new-note creation-to-Home regression also passed (nine distinct device tests total).
- One combined run was interrupted by the emulator's Android system process exhausting orientation sensor listeners (limit 128). The interrupted landscape case passed after recovery; this was not an application assertion failure. A second full run was also interrupted in UiAutomation startup, so results are recorded per successful case rather than claiming an uninterrupted eight-test run.
- Visual captures are in `captures/home-redesign/`: empty Home, enlarged-text posters, and landscape. The emulator screenshot API did not capture the drawer overlay reliably; drawer routing is covered by assertions. Inspection caught inherited serif typography and constrained card titles; both were corrected and rebuilt.
- `git diff --check` passed. Production application data was not used as a test fixture.

## Preview artifact

- APK: `S:/AI/Novel/Weaververse/Beta.Test.Build/weaverse-home-preview-debug.apk`
- Package: `com.ihy2ln.weaverse.homeqa` (separate preview installation).
- Version: `1.4.27-debug`, code 153. No public release was published.
- SHA-256: `2A931BF638AA1BE9AB4F7C177B3B4FA6BEB126D2F831D7FE837A249D3E38A29D`.

History intentionally begins empty. Open content through its mode to populate Home; existing edit timestamps are not presented as access history.

## Animation flicker follow-up

- Ran the preview on the existing MuMu Android 15 device and inspected Home and the drawer through the desktop window.
- Disabled the drawer's platform window animation and platform dim amount: Compose now owns the slide and synchronized scrim. Removed the simultaneous full-panel alpha animation, avoiding an extra translucent rendering layer.
- System-bar styling is keyed to the actual bar color rather than reapplied on every enclosing recomposition.
- Debug and instrumentation APK builds succeeded; installed the updated isolated preview in MuMu. Ten consecutive drawer open/close cycles passed their Home-visibility and drawer-dismissal assertions.
- Drawer routing also passed in the same run: `OK (2 tests)`, 111.6 seconds. The updated preview was reopened in MuMu after testing.
- MuMu intermittently timed out and briefly disconnected from ADB. Static screenshots verify layout, not continuous animation; the user's visual flicker has not been conclusively reproduced or proven eliminated. These changes address plausible app-side causes without changing emulator graphics settings.
