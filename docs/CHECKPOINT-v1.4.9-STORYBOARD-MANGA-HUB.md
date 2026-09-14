# Weaverse v1.4.9 Storyboard Manga Hub — Hard Checkpoint

Date: 2026-09-13

This checkpoint records the first end-to-end Storyboard manga-library slice:

`source catalog → series details → chapters → download → offline reader → favorites → Storyboard`

## User interface

Storyboard → Window is a dedicated dark manga hub with five primary tabs:

- Library — downloaded titles plus user-named favorite sections.
- Browse — source selector, Popular, Latest, Search, cover grid, metadata, chapters, and download actions.
- Downloads — live queue state with Stop, Retry, and Read.
- Extensions — every built-in adapter and custom website records.
- Projects — existing Storyboard documents and imports.

The header displays the installed build version so screenshots can identify stale APKs.

## Built-in source adapters

`MangaSourceAdapter` is the canonical contract for search, popular/latest browse,
details, chapters, and ordered page manifests.

- MangaDex uses the documented MangaDex API and At-Home page service.
- Comix, Atsumaru, MangaFire, MangaDot, and Rawkuma use reviewed app-owned
  public-HTML adapters with source-specific catalog routes.
- Rawkuma Japanese chapters default to RTL reading order.
- Public-HTML adapters make ordinary HTTP requests and parse visible metadata,
  covers, series links, chapter links, and reader images.
- The adapters do not execute CAPTCHA challenges, log in, bypass paywalls, or
  evade anti-bot controls. Those responses become actionable blocked errors.

MangaDex chapter loading now sends the required singular `manga` UUID parameter
and does not filter out non-English chapters. This allows raws and other
languages to appear for later translation.

## Favorites and categories

Room schema version 20 adds:

- `manga_series` for saved catalog metadata independent of downloads;
- `manga_favorite_categories` for default and user-created sections;
- `manga_favorites` for many-to-many title/category membership.

The default section is Favorites. A title can be placed in multiple named
sections and remains saved without downloading a chapter.

## Downloads and reader

Chapter discovery remains separate from downloading. WorkManager queues page
manifests, downloads immutable originals, validates image responses, writes
progress to Room, and supports stop/retry and process restart.

The offline-reader bug from v1.4.8 was fixed by resolving completed chapters
from the Room-backed UI flow instead of the ViewModel's local form state.

## Important files

- `app/src/main/java/com/ihy2ln/weaverse/core/manga/MangaSource.kt`
- `app/src/main/java/com/ihy2ln/weaverse/core/manga/PublicHtmlMangaSources.kt`
- `app/src/main/java/com/ihy2ln/weaverse/core/manga/MangaDownloadRepository.kt`
- `app/src/main/java/com/ihy2ln/weaverse/feature/storyboard/MangaSourceScreen.kt`
- `app/src/main/java/com/ihy2ln/weaverse/feature/storyboard/StoryboardMangaHubScreen.kt`
- `app/src/main/java/com/ihy2ln/weaverse/data/db/WeaverseDatabase.kt`

## Verification

- Android debug compilation succeeds.
- The APK installs over an existing schema-19 emulator database, migrates it to
  schema 20, launches, and remains running without an AndroidRuntime exception.
- Public HTML adapter fixtures verify cover, title, chapter number, language,
  and RTL metadata extraction.
- Existing MangaDex contract and web-link page extraction tests pass.
- Rawkuma's current public series page exposes 45 chapter links for the supplied
  Dungeon Gakuen title; its supplied chapter remains the ordered-page acceptance example.

## Restore/build point

- Version code: 136
- Version name: `1.4.9-debug`
- APK: `app/build/outputs/apk/debug/app-debug.apk`

Build locally with:

```powershell
./gradlew.bat :app:assembleDebug
```
