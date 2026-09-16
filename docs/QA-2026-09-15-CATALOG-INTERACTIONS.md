# Catalog interactions and metadata

Changes in this pass are confined to manga source/filter UI and adapters; no editor-owned file was edited in this pass. The working tree contains pre-existing editor changes.

- Keep the lazy grid state outside the conditional detail view so opening and closing manga details does not recreate it.
- Long-press catalog entries to open the existing persistent favorite-category operations, including category creation.
- Shared tag choices cycle neutral, include, exclude, neutral. Exclusions apply in both AND and OR modes. Common source spelling variants are normalized.
- Add missing genre choices from the supplied screenshots and a type section.
- Fetch missing filter metadata from detail pages with three concurrent requests maximum and a session cache. Failed metadata requests are counted and reported; unknown titles are skipped without aborting the other results. Skip at most five empty filtered pages automatically; further scanning remains explicit.
- Read Comix initial-data genres, demographics, formats, tags, authors, artists, content rating, score, original language, year, and normalized status. Live inspection confirmed these fields exist on the public detail page but taxonomies are absent on catalog cards.
- Show returned tags, content rating, and score on details. Ratings are not fabricated for sources that do not supply them.

Seven focused release unit tests passed: three CatalogTagsTest cases and four PublicHtmlMangaSourceAdapterTest cases. The latter includes Comix detail metadata and the earlier 120-title Rawkuma pagination regression.

Scope limitation: shared filters can only match metadata supplied by an adapter; native extension filters remain passed through to their own source implementation. This is not an end-to-end certification of every third-party extension.

MuMu interaction checks: opened The Forgotten Field from a scrolled Comix catalog, returned and repeated, observing the same title and vertical offset rather than the first row. Detail screen displayed score 9.1, safe content rating, creators and taxonomies. Long-press opened the category picker; adding The Forgotten Field to Favorites produced a checked row and persisted via the existing repository. This test favorite is left in MuMu. First tap on Action produced a check; second tap produced an X. A live metadata timeout initially aborted the batch; the follow-up correction isolates and reports such failures.

Final correction: release compilation/R8 and all seven focused tests passed again. Installed in place and confirmed both the original library entry and the new test favorite survived. Comix Action inclusion returned live results; reopening retained the selection. Changing Action to excluded returned a different live catalog (Dungeons and Crayons / Do Your Best and Regret at the top instead of the Action entries). No data was cleared. Signed artifact: Beta.Test.Build/weaverse-v1.4.17-catalog-interactions-20260915-signed.apk, SHA-256 3ed1c44435cf3a87efeccc1cd27bc128e5099d0cf3a2a6f40f24d882e971c09b.
