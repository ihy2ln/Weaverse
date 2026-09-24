# Rawkuma catalog repair

The generic HTML parser capped each catalog at 40 entries and the adapter inherited empty results for every subsequent page. Rawkuma now uses its own DOM card parser and follows observed same-host pagination links for browse and search. Series IDs keep the existing URL-based hashing scheme. Each cover is extracted from the same anchor as its title, avoiding the old surrounding-HTML heuristic. Only series-shaped /manga/slug/ URLs with image cards are included; chapter, archive and pagination links are excluded.

The regression fixture loads 120 distinct series across two pages, checks every title's cover URL, and verifies no request is issued past the last page. All three PublicHtmlMangaSourceAdapterTest cases passed.

Live source check: five Rawkuma catalog pages exposed 154 distinct series URLs in aggregate; a sampled cover returned HTTP 200 image/jpeg. This checks source availability, not a claim that every cover or every chapter works.

MuMu confirmed later catalog batches load after scrolling. Initial image verification found a separate HTTP issue: the same live cover returned 403 to okhttp/4.12.0 and 200 to Mozilla/5.0. Browse and detail cover requests now supply the browser User-Agent used by the catalog plus the Rawkuma Referer, using Coil's per-request headers API (https://coil-kt.github.io/coil/network/).

Final release build (including R8) passed. APK signature SHA-256 certificate fingerprint: f16db5088b05af8c941a0e23eb2364dab470dd4ae07c3023f858708f6451fc05. The signed APK was installed into MuMu with adb install -r; no uninstall or data clearing occurred. After launching the updated production package, the existing library remained present and Rawkuma's browser visibly displayed actual cover artwork. Artifact: Beta.Test.Build/weaverse-v1.4.17-rawkuma-20260915-signed.apk (workspace-relative).
