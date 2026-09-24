# Tracker and source accounts — implementation requirements

Requested providers: MyAnimeList, AniList, Kitsu, MangaUpdates, Shikimori, Bangumi and Hikka. Source reference: TachiyomiSY's integrated E-Hentai/ExHentai source and account settings.

## Audit (2026-09-16)

The current app has `MangaTrackingEntity` but no tracker service implementations or working tracking settings. The existing Tracking row is inert. A database row is not evidence of working authentication or synchronization.

TachiyomiSY includes E-Hentai/ExHentai as an integrated source, not a normal installed extension APK. WeaverVerse must label its own integration accurately; it must not invent package-installation state.

## Authentication prerequisites

- Obtain WeaverVerse-owned OAuth registrations wherever required. Never reuse TachiyomiSY/Mihon client IDs or secrets merely because they appear in public source.
- Users authorize their own accounts. Do not copy another app's stored login cookies, extract passwords, or bypass site access checks.
- Verify each provider's current authorization flow, redirect registration, scopes, refresh/revocation behavior and manga-specific API support before enabling its login button.
- MyAnimeList uses OAuth authorization with PKCE; AniList documents application registration and OAuth authorization; Shikimori explicitly requires an application registration. Hikka documents registered client references and scoped authorization. Check Kitsu, MangaUpdates and Bangumi independently rather than assuming identical OAuth behavior.
- Do not ask users to paste secrets into chat. Developer registrations belong in local/CI configuration. Per-user tokens belong in encrypted app storage, excluded from device and exported backups.

## Tracker work

Implement real login/logout, account verification, search, explicit title binding, status/score/chapter progress, refresh and conflict handling. Automatic chapter updates require a user-enabled setting; never decrease remote progress silently. Source metadata may suggest a matching tracker entry but ambiguous matches require confirmation. Display actionable provider/network/authentication errors instead of false success.

## Integrated source work

- Disabled until explicit adult-content opt-in. Separate public-site and account-authorized restricted-site modes.
- Official user-controlled website login, visible HTTPS host, no injected credential collection, no session values in logs or screenshots.
- ExHentai availability is controlled by the website/account; login must not promise access or work around denial.
- Source-native filters, paging, namespaced metadata, gallery detail, ordered pages, image downloads and referer/cookie scoping must be implemented and tested as real operations.
- Account settings, logout, and visible access errors. Source/session preferences must survive restart, with secrets excluded from backups.
- Favorites sync defaults to import-only; no remote additions, removals or overwrites without an explicit user-approved policy. Import-only must not delete existing local favorites merely because they disappear remotely.
- Enhanced browse and update settings must only be exposed once their underlying functions work. Do not duplicate inert controls from reference screenshots.

## Verification gates

Unit tests: provider mappings, OAuth state/PKCE, token expiry, request-host scoping, metadata/filter values, cursor paging, progress conflicts and backup exclusions. MuMu: settings navigation, cancel/login/logout, error cases, restart persistence and original-download preservation. Authenticated round trips require dedicated authorized accounts and configured registrations. Do not publish a release claiming seven-provider support before those checks pass.

## References

- https://github.com/jobobby04/TachiyomiSY
- https://github.com/jobobby04/TachiyomiSY/blob/master/app/src/main/java/eu/kanade/tachiyomi/source/online/all/EHentai.kt
- https://github.com/jobobby04/TachiyomiSY/blob/master/app/src/main/java/exh/ui/login/EhLoginActivity.kt
- https://docs.anilist.co/guide/auth/
- https://myanimelist.net/apiconfig/references/authorization
- https://shikimori.io/oauth
- https://hikka.io/articles/hikka-oauth-ebbd59

## Implementation status: v1.4.24

E-Hentai and ExHentai now have opt-in built-in source adapters, official in-app website login, encrypted retained session credentials, scoped logout and source settings. Basic native filters, cursor paging, metadata and ordered image resolution have fixture coverage. These are integrated sources, not separate installed extension APKs.

MuMu opened the official forum login, but Cloudflare human verification blocked the sign-in form. No supplied account credentials were entered. Live authenticated browsing, ExHentai eligibility and real downloads remain unverified. Full TachiyomiSY settings/favorites synchronization and the seven tracker services above remain pending; this build must not claim those features work.
