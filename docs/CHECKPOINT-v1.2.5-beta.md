# Checkpoint — Weaverse v1.2.5-beta

Frozen baseline for the next update. Branch new work from this tag, not from
stale `main` (GitHub `main` can still point at the older InkForge transplant
at `0.5.17`).

| | |
|---|---|
| **Tag** | [`v1.2.5-beta`](https://github.com/ihy2ln/Weaverse/releases/tag/v1.2.5-beta) |
| **Commit** | `fec9e85d43923326b99631f47d256f7e2124d581` |
| **Android** | `versionName` `1.2.5-beta` · `versionCode` `46` · `com.ihy2ln.weaverse` |
| **Published** | 2026-08-27 |
| **Git** | `git checkout -b <branch> v1.2.5-beta` |

Release assets: `weaverse-v1.2.5-beta-debug-signed.apk` and
`Weaverse-Desktop-v1.2.5-beta.zip`.

---

## What this version is

Five Android workspaces over one local library, plus a Windows desktop host
that serves the web hub and Wi-Fi / remote sync.

| Workspace | Sub-modes (as shipped) |
|---|---|
| **Novel** | Bookshelf · Plan · Write · **Read** · Chat · Review |
| **RPG** | Campaign · Inventory · Adventure · Town · Roster · Lore · Presets |
| **Chatting** | Chats · Contacts |
| **Storyboard** | Window · Manga · Comic |
| **Notes** | Board |

Shared extra tools: Codex, Prompts, Notes, Snippets, Chats, Pictures.

Shipped in and around this tag:

- Premium reader (themes, bookmarks, TTS, volume-key paging) — added in
  `v1.2.0-beta` (`7b77c9f`)
- Workspace shelves and creation popups
- Compact / reorderable prompt dock
- Extra AI providers, merge without relaunch, hourly writing snapshots
- RPG Town, expandable sheets, inventory grouping
- Storyboard comic pages with in-panel media transform

---

## Known gap this update starts from

**Pictures added in Novel → Write do not show in Novel → Read, and would not
keep their place even if they did.**

Write stores media as first-class document blocks in scene `docJson`:

- `MediaBlock` — one image / video / audio, with `widthPercent` and `align`
- `MediaStackBlock` — stacked pictures, cycled in the editor
- `MediaGridBlock` — multi-image template

The reader at this checkpoint flattens each scene to `plainText()` and
renders only paragraphs. `Document.plainText()` skips media blocks, so a
Write → Read switch drops every picture. Inserting media in Write can also
wipe the surrounding paragraph instead of sitting next to it, and persist
races a fast tab switch because it re-reads `loadedScene` inside the
coroutine rather than pinning the edited scene id.

The next update must:

1. Keep surrounding prose when inserting media (only clear `/image`-style
   slash residue).
2. Copy picker files into app storage and resolve a *readable* path.
3. Persist against the scene that was edited.
4. Observe manuscript JSON + media live in Read.
5. Render image / stack / grid / audio blocks **in document order**, using
   the same width and alignment as Write.

---

## Where the code lives (at this tag)

| Area | Path |
|---|---|
| Write canvas | `app/src/main/java/com/ihy2ln/weaverse/feature/novel/write/` |
| Write media UI | `.../write/editor/MediaBlockView.kt`, `MediaStackBlockView.kt` |
| Insert / persist | `.../write/WriteViewModel.kt` (`insertMediaBlock`, `persistScene`) |
| Document model | `app/src/main/java/com/ihy2ln/weaverse/core/text/DocumentModel.kt` |
| Media files | `app/src/main/java/com/ihy2ln/weaverse/core/media/MediaRepository.kt` |
| Reader | `app/src/main/java/com/ihy2ln/weaverse/feature/novel/read/ReaderScreen.kt` |
| Reader query | `ManuscriptDao.getReaderScenes` in `data/db/dao/InkDaos.kt` |
| Shared zoom/play | `core/ui/components/ZoomableMedia.kt`, `AudioMediaPlayer.kt` |

Do not rebuild the reader from the old `0.5.17` transplant. Extend this
premium `ReaderScreen`.

---

## How to resume

```bash
git fetch origin tag v1.2.5-beta
git checkout -b cursor/<topic> v1.2.5-beta
```

Companion docs at this tag: [BUILD_NOTES.md](../BUILD_NOTES.md),
[ARCHITECTURE.md](ARCHITECTURE.md), [GUIDE.md](GUIDE.md).
