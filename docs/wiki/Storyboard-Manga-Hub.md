# Storyboard Manga Hub

The Storyboard Window is a cover-first manga, comic, and manhwa workspace. It
keeps source discovery, downloads, offline reading, favorites, and editable
Storyboard projects together inside Weaverse.

## Main tabs

| Tab | Purpose |
|---|---|
| Library | Downloaded titles and personal favorite sections |
| Browse | Popular, Latest, Search, series details, and chapters |
| Downloads | Queue progress, Stop, Retry, and offline Read |
| Extensions | Built-in source adapters and custom website records |
| Projects | Storyboard pages, file import, panel editing, translation, and coloring |

## Browse a source

1. Open **Storyboard → Window → Browse**.
2. Choose MangaDex, Comix, Atsumaru, MangaFire, MangaDot, or Rawkuma.
3. Tap **Popular**, **Latest**, or enter a title and tap **Search**.
4. Tap a cover to load the title, description, and chapter list.
5. Tap **Download** beside a chapter.

MangaDex uses its API. The other built-in adapters inspect the catalog,
metadata, and chapter links exposed in public HTML. If a site requires a
browser challenge, login, CAPTCHA, or paywall, Weaverse reports that block; it
does not circumvent the restriction.

## Favorites and personal sections

Titles do not need to be downloaded before they are saved.

1. Open a title in Browse.
2. Under **Favorite sections**, tap Favorites or another section.
3. Open Library and select that section to see its cover grid.
4. To create a section, enter a name in **New favorite section** and tap Add.

A title can belong to multiple sections, such as Reading, Finished, Translate,
Reference, or any names you choose.

## Downloads and offline reading

Downloads are user-triggered. The queue stores ordered original pages in
managed app storage and survives process restarts.

- **Stop** cancels active work without deleting completed pages.
- **Retry** resumes a failed or stopped chapter.
- **Read** opens a completed chapter in the offline reader.

For another public reader page, paste its URL into **Download from web link**.
Preview shows the detected ordered page count before you confirm the download.

## Edit in Storyboard

Downloaded or imported pages remain immutable originals. Add selected pages to
a Storyboard project to separate panels, translate Japanese/Korean/Chinese text,
clean source lettering, typeset a translation, color artwork, and export a
derived page.

**Page editor** (or **Art** on the manga canvas) opens a touch-first page
editor inspired by Koharu's translation steps:

- Bottom tools: Move, Text, Paint, Erase, Color, Clean, and Hand. Tap a
  tool, then work on the page with your finger. There are no keyboard
  shortcuts.
- Tap **Find text**, **Read**, **Translate**, and **Clean art**, then
  **Go**. Clean art rebuilds the drawing under the old lettering; the page
  shows the translation, never the source OCR.
- Type and Layers keep Source and Translation as separate fields, with
  auto-fit, alignment, fill, stroke, and horizontal/vertical type.
- **Save copy** writes a new picture file. The original page stays on disk.
  Use Original / Edited to compare.

## Managing library titles

Long-press a cover in the Library for that title's categories and **Remove from
library**. Removing clears every category and deletes its downloaded chapters,
after a confirmation. **More → Data and storage** reports what downloads and the
image cache are using and can clear either; **More → Help** opens this wiki at the
Storyboard page.

## Filtering chapters

The filter icon above the chapter list opens **Language**, scanlator group, and a
chapter-number range. The language picker lists only the languages that title
actually publishes, with readable names (English (EN), French (FR)), so a series
with seven translations narrows to the one you read. Reset clears every filter.
