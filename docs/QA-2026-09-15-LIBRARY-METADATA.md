# Library identity and source metadata — Android 1.4.21

## What changed

- Library identity is source + remote series ID, never the display title. Same-named works from different sources remain separate. Downloaded and favorited copies of the same source record merge into one entry. Category and downloaded-only intersections retain their downloaded chapters.
- Opening a downloaded series presents all its chapters, ordered by volume/chapter, with language, page count and download/read state. Chapter selection opens that exact existing record.
- Rawkuma's first h1 is the sidebar label “Last Updates.” Series metadata now uses `h1[itemprop=name]` and its Book/ComicSeries JSON-LD, with the corresponding image, author, genres, year and numeric score. The HTML interface locale is not mistaken for chapter language. Chapter IDs appended to URLs are not parsed as decimal chapter numbers.
- Existing invalid Rawkuma titles are repaired using the authoritative series page. Library → More → Repair title metadata retries all saved downloaded series and corrects chapter labels where their exact remote ID/URL matches. No chapter ID, original image, edited media, page, read/bookmark state, favorite or download is deleted/recreated by repair.
- Selected series metadata persists independently of favorites. Partial catalog saves preserve previously known nonempty metadata. Favorite membership checks use favorite records, not the metadata cache.
- Additive Room migration 21 → 22 stores publication type, year, content rating and score. Existing database/media backups include these fields because they archive the database. No OAuth secret or extension APK was added to backups.
- Native source filters appear first. Additional Weaverse loaded-result filtering is separate, collapsed and labeled accurately. Source-native include/exclude controls cycle clear → include → exclude; sources with only inclusion checkboxes retain that limitation.

## Public website inspection and implementation

Verified against the source sites on 2026-09-15, not copied from a different source's tag list:

| Source | Source-wide controls implemented | Metadata path |
| --- | --- | --- |
| Rawkuma | Website form Status, Type, Genres, Sort (Date/Title/Popular/Rating); `search_term`, `the_status`, `the_type`, `the_genre`, `the_orderby`; filters retained on next-page links | Scoped series JSON-LD and itemprop fields; actual chapter tab |
| Comix | Live option IDs for genres, formats, demographic, type, status, sort and years; include/exclude + AND/OR, content rating, minimum chapters | `initial-data` series metadata; rendered catalog card title/cover pairs |
| MangaFire | Public filter-options taxonomy: genres/formats, themes, demographic, type, status, sort, content rating; languages, AND/OR, minimum chapters and years | Normal rendered website; title-detail selectors for title/cover/credits/tags/status/year/rating/score |
| Atsumaru | `api/explore/availableFilters` genre/tag IDs and groups, type/status; include/exclude, minimum chapters; six directly supported search sorts | Existing public JSON catalog/detail adapter; separate score vs content rating; preserve `Manwha` request ID, display “Manhwa” |
| MangaDex | Official tag IDs grouped by type, include/exclude + AND/OR, status, demographic, content rating, selected common chapter languages, server sorting | Official manga/relationships/statistics API; null metadata remains unknown, not literal `null` |
| MangaDot | Not verified: public page presented a security verification interstitial | No guessed native filters added; existing adapter reports failures |

References: [Rawkuma](https://rawkuma.net/manga/), [Comix](https://comix.to/browse), [MangaFire](https://mangafire.to/browse), [MangaFire options](https://mangafire.to/api/filter-options), [Atsumaru](https://atsu.moe/explore), [Atsumaru options](https://atsu.moe/api/explore/availableFilters), [MangaDex API schema](https://api.mangadex.org/docs/static/api.yaml).

This is not a claim of complete website parity: remote author/artist autocomplete, Comix arbitrary-tag search, Atsumaru account-specific/advanced home rankings and timeframes, and the complete MangaDex language list are not exposed by this change. MangaDot requires a separate authorized verification session. Installed third-party APK extensions continue to supply their own native filters; arbitrary repositories/extensions were not revalidated.

## Verification

- 24 manga JVM tests passed, including stable source identity, title/cover scoping, numeric score separation, include/exclude option IDs, pagination query retention and chapter suffix handling.
- Seven MuMu isolated `.mangaqa` tests passed: additive migration; live Rawkuma, Atsumaru, Comix and MangaFire filtered catalog → cover → detail metadata flows; live MangaDex tag filtering/cover/null handling; immediate native-filter include/check → exclude/X → clear rendering. No paid AI operations.
- Production signing certificate matched the existing package. In-place MuMu update opened the existing Room database successfully. UI inspection confirmed corrected saved series title and access to both existing downloaded chapters. No uninstall or clear-data operation was used.
- Existing editor changes from previous tasks were preserved; this task changes catalog/library/persistence only.

## Final UI checks

- Production MuMu library retained both saved series. Both now display their actual source cover rather than a downloaded chapter's first page.
- The formerly mislabeled Rawkuma entry opens a chapter picker with Chapter 23 (15 pages) and Chapter 24 (11 pages). Opening the second row loaded Chapter 24's existing offline pages.
- Comix filters showed website-derived Sort, Content rating, Type, AND/OR, Genres and Formats. Manual interaction exposed stale checkmark rendering from mutable extension filter objects; explicit row invalidation fixed it and a Compose instrumentation test now guards the three-state cycle.
- Local captures: `captures/library-metadata/library-final.png`, `chapters-final.png`, and `filters-final.png`. The signed release was reinstalled in place and visually verified: first genre tap displays Included, second displays X / Excluded. The final filter capture shows the corrected state.
- The unrelated outer storyboard project heading can retain its historical “Last Updates” name; this task repairs catalog/library records, not user/editor project names.

## Delivered artifact

- Version 1.4.21, code 147, package `com.ihy2ln.weaverse`.
- Local APK: `S:/AI/Novel/Weaververse/Beta.Test.Build/weaverse-v1.4.21-library-metadata-signed.apk`.
- SHA-256: `d13fbeb0de3c20278d13c85014536c38befb322a651369788109f4959c8be468`.
- `assembleRelease` including R8 minification succeeded. `apksigner verify --print-certs` succeeded; certificate SHA-256 `f16db5088b05af8c941a0e23eb2364dab470dd4ae07c3023f858708f6451fc05` matches the existing production package (Android debug certificate used for this local signed build).
- No GitHub release, uninstall, clear-data operation or paid AI call was performed.
