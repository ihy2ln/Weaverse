# Weaverse v1.4.23 — Download options

Android-only update. Choose **Original only**, **Translate**, **Colorize**, or **Colorize + Translate** when downloading a chapter or a pasted web-page preview.

- Original pages always download first. The existing editor saves AI output as separate edited media and retains the original.
- AI choices create a persistent follow-up, not an automatic paid background job. When ready, tap **AI ready → Open AI**, review models/settings, and tap **Run**. The chosen action and **Chapter** scope are preselected.
- Follow-ups survive app restarts. They remain until removed; **Remove** clears only the reminder, not downloaded pages or edits.
- Already downloaded chapters expose the choices through the checkmark in the library chapter list, source details, and download queue. Preparing AI does not re-download a completed chapter.
- Re-adding a completed/in-flight web link preserves its saved manifest and reading metadata; interrupted saved manifests resume without replacement.

## Verification

- 31 manga JVM tests passed, including action mapping and repeat-download metadata preservation.
- 8 MuMu instrumentation tests passed: download selection/confirmation/cancel/persistence and page-editor original/undo regressions.
- Final signed release installed in MuMu in place. Existing two-series/three-chapter library remained available. Restart restored a Both follow-up; accessibility and screenshots confirmed **Colorize + Translate** and **Chapter** selected in the AI sheet.
- Release compilation, R8 minification, and APK signature verification passed. No paid AI requests were executed during QA; live provider output quality is not newly certified by this update.

Install over the existing app; do not uninstall or clear data. Back up important data first. Uses the existing local-distribution Android debug signing certificate, matching the prior APK.

Package: `com.ihy2ln.weaverse`; version code **149**; Android **8.0+**.

APK: `weaverse-v1.4.23-download-options-signed.apk`

SHA-256: `57017680a81e461c0661d6ca407249fb8949434214f144127e0333ba4c28888c`
