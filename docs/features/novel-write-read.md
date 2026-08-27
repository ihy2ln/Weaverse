# Novel Write ↔ Read (media)

How pictures move (or fail to move) from the Novel **Write** editor to the
Novel **Read** screen. Snapshot of **v1.2.5-beta** (`fec9e85`) — the
checkpoint this branch starts from.

See also [`v1.2.5-beta.md`](v1.2.5-beta.md) and [`CHECKPOINTS.md`](../CHECKPOINTS.md).

---

## Write — pictures are real document blocks

1. User picks an image (slash `/image`, title-row Media, or a scene-beat
   image). `WriteViewModel.importImages` → `WriteMediaOps.importFromUris` →
   `MediaRepository.importFromUri`.
2. The picker URI is **copied** into `filesDir/media/{uuid}.{ext}`. A
   `MediaEntity` row stores `relativePath = "media/{uuid}.{ext}"`.
3. `insertMediaBlock` appends a `MediaBlock(mediaId, kind, widthPercent,
   align, caption, …)` (or later a `MediaStackBlock` if pictures are stacked)
   into the scene's `blocks` list.
4. `WriteDocumentOps.persist` writes the full `Document` as `docJson` and
   refreshes `plainText` / `wordCount`. `Document.plainText()` skips media
   blocks, so search / TTS / word-count never see the image.

Write's `DocumentEditor` walks `blocks` in order and paints
`MediaBlockView` / `MediaStackBlockView` with `mediaPaths[mediaId]` =
absolute file path. Placement in Write is **document order**, with optional
`widthPercent` (single image) and tap-to-cycle on stacks.

Known Write bugs at this checkpoint:

- `insertMediaBlock` **blanks the target paragraph** before inserting.
  Toolbar "add media" after the last paragraph can wipe that paragraph's
  prose. Only slash `/image` residue should disappear.
- `persistScene` launches a coroutine that re-reads `loadedScene` **after**
  the delay. A fast scene switch can persist the previous document onto the
  newly selected scene.

---

## Read — pictures are thrown away

`ReaderViewModel` loads `manuscriptDao().getReaderScenes(bookId)` **once**
inside `settings.preferences.collectLatest`, then maps each row to:

```text
ReaderScene.text = row.plainText  (or documentFromJson(docJson).plainText())
```

`ReaderScreen` splits that string on newlines and shows `Text` only. There
is no `MediaRepository`, no `docJson` on `ReaderScene`, and no live Room
observation of scenes.

| What Write stored | What Read showed |
|---|---|
| `Paragraph("Before")` · `MediaBlock(img)` · `Paragraph("After")` | "Before" then "After" — image slot gone |
| Stack of three pictures | Nothing |
| Picture added, then immediate Write → Read | Often still nothing (stale snapshot) |

Read cannot match Write placement because it never sees blocks.

---

## After the picture fix (this branch)

Read observes `observeReaderScenes(bookId)` plus `MediaRepository.observeAll()`,
keeps `docJson`, resolves `mediaId → readable path`, and renders the same
block types as Write in **document order**:

- Prose (`Paragraph` / `Heading` / `Quote` / `ListItem` / `CodeBlock`)
- `Divider`
- `MediaBlock` — width, alignment, caption; image / video / audio
- `MediaStackBlock` — one picture at `currentIndex`, tap the frame to cycle
- `MediaGridBlock` — 2-up / 3-up row for imported grids
- `SceneBeatBlock` — skipped in the reader (author-only)

Write inserts with `insertMediaAfter` (keeps surrounding prose; strips only
slash-command residue) and pins persist to the edited scene id so a fast
Write → Read switch cannot drop the picture.

Tests: `DocumentModelTest` (JSON round-trip + insert/placement),
`MediaPathsTest` (readable path / URI heuristics).
