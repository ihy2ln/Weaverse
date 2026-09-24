# Searchable media tags — Android 1.4.22

## Scope and behavior

- Added 1,327 normalized-unique WeaverVerse labels across 21 groups: genres, formats, demographics, adaptations, leads/casts, archetypes, character growth, relationships, family life, school, work, entertainment, sports/games, fantasy/powers, creatures, technology, settings, society, narrative, mood/themes, and content notices.
- The list uses the supplied screenshots as a starting point and adds a broader cross-media vocabulary. It is not a dump of every MangaDot tag or a claim that every website supports these labels.
- Browser → source → Filters → Additional filters → Genres / Tags provides searchable, virtualized lists, actual matching/total counts, a category selector, selected-only view, and include → exclude → clear behavior. Native source groups larger than 12 entries also gain a search field and bounded virtualized list. No arbitrary 80/160/824 entry limit.
- Source result labels and custom selections are merged with the vocabulary. Aliases such as Female Protagonist / Female Lead, Science Fiction / Sci-Fi, and Mahou Shoujo / Magical Girls share matching identity; opaque native website IDs are never replaced by these aliases.
- Local tags refine returned source pages, not the source's entire catalog. Existing source-native filters remain separate and are sent unchanged to their own source. Full title details are loaded/cached for local tag matching because catalog cards may contain incomplete tag subsets.
- Added local rating thresholds 5+/6+/7+/8+/9+, content rating, available chapter thresholds 10+/25+/50+/100+, and presence/absence of volume labels. Unknown scores or chapter metadata do not satisfy those constraints. Explicit scores such as 4/5 normalize to 8/10; invalid/nonfinite/out-of-range scores are rejected. Chapter filters fetch metadata with bounded concurrency and do not download chapter images. Counts are available chapter entries, not unique translated chapter numbers or asserted publication totals.
- Reset clears all additional filters. No database migration or editor changes were made for this update.

## Source reference and boundaries

The user's open [MangaDot search page](https://mangadot.net/search) was inspected on September 15–16, 2026. It exposed searchable Genres and Tags with 2,742 tags, six sort modes, types, status, scanlator, author/artist, year, content rating, score, chapter and volume controls. The screenshots are the UI reference; catalog usage counts were not copied or fabricated. Direct unauthenticated web retrieval still returned HTTP 403. This change does not add a new MangaDot API adapter or claim complete native MangaDot filter integration. Existing source adapters retain their supported filters.

## Verification

- 28 manga JVM tests passed, including four new tests for vocabulary uniqueness/size, phrase and alias search, source/custom label retention, inclusion/exclusion aliases, score validation, and chapter/volume unknown-state handling.
- Eight MuMu instrumentation tests passed: large-list search with include/exclude/clear and selection retention while changing the query; native filter redraw; migration preservation; and live metadata/filter requests for Rawkuma, Atsumaru, Comix, MangaFire and MangaDex. Test package `.mangaqa` is isolated from the user's production data.
- Final signed release installed with `adb install -r`; both existing saved series and their chapter counts remained visible. No uninstall or clear-data operation.
- Production MuMu UI showed `1327 of 1327`, category/search/selected-only controls, and `1 of 1327` after searching Reincarnation. Selecting updated Selected (1), second tap displayed X, and third cleared the test selection. Rating/chapter/volume controls were reachable by scrolling. The emulator used hardware keyboard input; a soft-keyboard-open layout was not verified.
- Captures: `captures/media-tags/search-keyboard.png` (focused search field, hardware keyboard) and `captures/media-tags/exclude.png`.
- Release compilation/R8 and `apksigner verify --print-certs` passed. Package `com.ihy2ln.weaverse`, version 1.4.22, code 148; matching existing Android debug signing certificate for local delivery.
- APK: `S:/AI/Novel/Weaververse/Beta.Test.Build/weaverse-v1.4.22-searchable-tags-signed.apk`.
- SHA-256: `04d6ef5bd7069751639653ff46bc2b34652cb52f5ed8e437b42709c4b84957b3`.
- No GitHub release, AI request, or editor-file changes in this update.
