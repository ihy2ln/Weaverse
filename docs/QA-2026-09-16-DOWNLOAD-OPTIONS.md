# Download options QA — v1.4.23

- Source commit: ee81a5beccd3e0fcc51c940e97e82cc2618b7359.
- Version 149 / 1.4.23, package com.ihy2ln.weaverse.
- Final Gradle run: testDebugUnitTest (manga filter), assembleDebug, assembleDebugAndroidTest, assembleRelease; offline, one worker. Passed; 31 manga JVM tests, no failures/skips.
- MuMu 127.0.0.1:16384: MangaDownloadOptionsDeviceTest (4) and PageEditorWorkspaceDeviceTest (4) passed on final QA build. Isolated .mangaqa package; no production test fixtures.
- Production signed APK updated using install -r. Certificate SHA-256 f16db5088b05af8c941a0e23eb2364dab470dd4ae07c3023f858708f6451fc05.
- Confirmed Library retained 2 titles and 3 downloaded chapters, with actual titles and covers. Library chapter dialog now exposes Download / AI options for completed originals.
- Selected Both on an existing completed chapter, prepared follow-up, force-stopped and reopened app. AI-ready reminder and Both choice survived.
- Open AI reused the existing editor. Accessibility checked=true on Colorize + Translate and Chapter; Run remained explicit. Never tapped Run, no paid provider generation invoked.
- Closed sheet/editor and removed only the temporary follow-up. Library/downloads remained intact. Existing prior translated overlays were not changed by QA.
- Private screenshots: captures/download-options/01-options.png and 02-prepared-ai.png; matching handoff accessibility dump 02-prepared-ai.xml. Not release assets.
- New regression covers repeat web-link completed/in-flight manifests; UI tests cover default original, selection without submission, explicit Both confirmation, cancel, and persisted removal without unrelated preference changes. Existing editor tests cover immediate paint undo and overlay/original-mask isolation.
- No new live AI output-quality claims; no fresh web download/provider generation was performed in the production walkthrough. Background automatic AI processing is not implemented: this release offers persistent, explicit editor follow-ups.
- Signed APK SHA-256: 57017680a81e461c0661d6ca407249fb8949434214f144127e0333ba4c28888c.
