# Weaverse v1.4.9 — Storyboard Manga Hub

This release delivers the first reliable end-to-end manga/comic/manhwa workflow inside Storyboard:

- A dedicated five-tab hub: Library, Browse, Downloads, Extensions, and Projects.
- Browse adapters for MangaDex, Comix, Atsumaru, MangaFire, MangaDot, and Rawkuma, with clear blocked/unavailable errors when a site rejects ordinary public requests.
- Cover-first series browsing, metadata, chapter discovery, ordered page manifests, and user-confirmed downloads.
- Working completed-download and library navigation into the offline reader.
- Favorite titles and user-named favorite sections for personal organization.
- Web-link chapter preview/download, persistent download state, stop/retry controls, and immutable originals.
- Storyboard import, panel separation, translation/coloring processing, editable overlays, and image export foundations.
- MangaDex chapter retrieval fixes, including non-English chapter discovery.
- Updated in-app Help, README, hard-checkpoint notes, and a GitHub-wiki-ready Manga Hub guide.

The attached artifact is the local Android debug APK (`1.4.9-debug`).

## Verification

- Focused manga adapter, page importer, file importer, storyboard placement, and serialization tests pass.
- Android debug APK builds successfully.
- Upgrade smoke test passes against an existing schema-19 app database.
- Public HTML adapter fixtures verify catalog covers, metadata, chapters, language, and RTL ordering.

## Source access note

Weaverse uses reviewed, app-owned adapters and ordinary public requests. It does not bypass CAPTCHA, login, paywalls, or anti-bot challenges. A source that blocks access reports an actionable error instead of pretending that its catalog is available.
