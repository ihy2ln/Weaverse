# Gallery source login QA — v1.4.24 (150)

## Delivered

- Built-in E-Hentai and ExHentai adapters, disabled by default until adult-content opt-in.
- Browse > Extensions > E-Hentai / ExHentai > Settings / Login.
- Official HTTPS WebView login, visible host, restricted top-level navigation and secure window. No password interception or injected credential bridge.
- Account session stored using the existing encrypted secret store. Secret preferences and WebView data excluded from platform backups; custom export does not include them.
- Separate restricted-source setting requires a locally saved session. The website determines eligibility; a saved session is not evidence of live access.
- Scoped logout preserves other source cookies, downloads and library records.
- Basic website-native search/rating/page-count filters, cursor paging, namespaced metadata, detail and ordered gallery-page resolution.

## Verified

- Debug/release compilation, release R8 minification, APK signature verification passed.
- 39 manga JVM regression tests passed, including eight gallery tests.
- Six MuMu instrumentation tests passed: two account/session tests and four existing download-option tests.
- Fixture checks cover disabled-source network isolation, URL/cookie scoping, title/cover/tag parsing, filter encoding, page ordering, encrypted session reopening and scoped logout.
- Production MuMu app updated with install -r; no uninstall or data clearing. Existing two library titles remain visible.
- Manual navigation reaches source settings and official forums login in the signed release.
- The official login currently displays Cloudflare human verification. Left open for the user. No real credentials submitted or captured in test artifacts.

## Not verified / not included

- Real account authentication, eligible ExHentai access and live gallery download: blocked pending user completion of human verification.
- Full TachiyomiSY filter/settings parity, favorites sync and gallery revision handling are not implemented.
- Tracker authentication/sync remains separate pending work; no seven-tracker support claim.
- This is a local signed APK; no GitHub release was published for this build during this verification.

Local artifact: `Beta.Test.Build/weaverse-v1.4.24-gallery-login-signed.apk` (relative to workspace root).
Signing certificate SHA-256: `f16db5088b05af8c941a0e23eb2364dab470dd4ae07c3023f858708f6451fc05` (existing local Android debug certificate).
