# WeaverVerse v1.4.25

## Storyboard translations
- Generated edited translations display automatically without a review-approval step. Actual failed or incomplete translations still report errors.
- Versions replaces the reader review action: Original, Translation without cleanup, and Translation with edits.
- Translation without cleanup places translated text over untouched original artwork. Each new successful run retains separate versions; switching preserves manual changes.
- Older pages cannot recover a translation-only snapshot that was never saved.

## Built-in gallery sources
- Opt-in E-Hentai/ExHentai sources with official in-app website login, encrypted session storage, scoped logout and basic search/paging/metadata support.
- Find account settings under Browse > Extensions > E-Hentai / ExHentai > Settings / Login.
- Website eligibility and human verification still apply. No favorites synchronization or new tracker integration is included.

## Verification and limitations
- Debug and minified release builds passed; APK signature verified.
- 17 targeted translation/version tests passed.
- Previous gallery validation passed 39 manga unit tests and six MuMu device tests; official login entry was reached but human verification blocked live authentication.
- New translation version UI and a paid AI translation round trip have not been verified in MuMu.
- Android APK uses the existing local Android debug signing certificate, matching prior local releases. Install as an update; do not uninstall or clear data. Devices with a different signing certificate cannot update in place.

APK SHA-256: `537dffe73646b37edcb7b0287fa5cd0ee10cb9c6dc9b8529689e10c4b34db96f`
