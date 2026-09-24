# RawKuma titles and browser sorting

- RawKuma catalog parsing now prefers readable links with the exact same series URL over cover alt text. Generic section labels (including Last Updates) are rejected; the URL slug is the final fallback. No saved library titles are rewritten automatically.
- Browser filters include Source order, Popular, Latest updates, title ascending/descending, score descending and release year descending.
- Popular and Latest select source listings, not search sort parameters. Extension-native filters remain the mechanism for server-side search sorting. Title/score/year sorts operate on loaded results and re-sort the accumulated list after pagination; missing numeric metadata goes last. They do not invent scores or claim whole-catalog ordering.
- Public RawKuma catalog inspected on 2026-09-15: https://rawkuma.net/manga/ . Title links are separate from cover links and generic image labels exist.
- Regression test added for Last Updates cover alt labels with separate series links, source ordering, alphabetical sorting and missing-score placement.
- All five PublicHtmlMangaSourceAdapterTest tests passed; assembleRelease (including R8) succeeded. APK signature verification passed with the existing local Android Debug certificate.
- Device interaction verification is not included in this change. Existing incorrectly named library entries may require a separate metadata refresh/repair.
