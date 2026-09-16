# Weaverse v1.4.22 — Searchable tags and library metadata

Android update containing the changes delivered locally in v1.4.19–v1.4.22. No new desktop/browser build is included.

## Highlights

- 1,327 unique media genre/tag labels in 21 categories, with search, category browsing, selected-only view and include/check → exclude/X → clear selection.
- Find the expanded vocabulary at **Browser → source → Filters → Additional filters → Genres / Tags**. Large native source filter groups are searchable too.
- Additional rating, content-rating, chapter-count and volume-label filters. Local filtering loads full title metadata when catalog cards have incomplete tags; unknown values are not invented.
- Actual library titles and source covers; downloaded series open a list of their individual chapters. **Library → More → Repair title metadata** retries older entries without replacing downloaded pages or saved edits.
- Website-derived filters and metadata improvements for Rawkuma, Comix, MangaFire, Atsumaru and MangaDex.
- Reader/editor improvements from the intervening local builds: continuous chapter reading, compact navigation, unified page-editor text/paint/cleanup tools, inline brush color/size, and immediate undo/redo.

## Important distinctions

- Website-native filters search the source catalog. WeaverVerse's broader vocabulary and additional filters refine returned pages; adding a label does not make it a supported server-side tag on every site.
- This is not a complete import of MangaDot's taxonomy or a new native MangaDot API integration. Remote author/artist autocomplete and some advanced website-specific filters remain outside this update.
- Chapter thresholds count available source chapter entries; volume filtering checks source volume labels. Neither invents missing publication metadata.
- The historical name of an existing storyboard project may still contain “Last Updates”; catalog/library repair does not rename user projects.

## Verification and installation

- 28 manga unit tests and 8 MuMu instrumentation tests passed for v1.4.22, including searchable tag selection and live catalog/filter checks.
- Release compilation and R8 minification passed. APK signature verified against the certificate used by the existing production package.
- MuMu updated in place with its existing library preserved; final UI verified tag search, selection count, exclusion X and accessible additional controls.
- Install over your existing app; do not uninstall or clear data. Back up important data before updating. This local-distribution APK uses the project's existing Android debug signing certificate.
- Package: `com.ihy2ln.weaverse`; version code: **148**; minimum Android: **8.0 (API 26)**.

## Download verification

APK: `weaverse-v1.4.22-searchable-tags-signed.apk`

SHA-256: `04d6ef5bd7069751639653ff46bc2b34652cb52f5ed8e437b42709c4b84957b3`
