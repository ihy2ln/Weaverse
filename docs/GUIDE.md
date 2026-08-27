# Weaverse — Full Guide

Weaverse is five workspaces over one shared library. Everything except AI text
generation works with the network off.

---

## Home

The **book button** in the top bar opens Home. Home is the way into every
workspace, not just novels: a card per mode, then your novels underneath.

## The chrome

Three rows across the top:

1. **Modes** — Novel · RPG · Chatting · Storyboard · Notes
2. **Sub-modes** — whatever the current mode is divided into
3. **Extra** — Codex, Prompts, Notes, Snippets, Chats, Pictures. App-wide tools
   that do not belong to any single mode.

**Focus** switches between the writing view and the picture gallery.

---

## Novel

`Bookshelf · Plan · Write · Read · Chat · Review`

- **Bookshelf** — the novels in this workspace.
- **Plan** — the outline: Book → Chapter → Scene → Scene beat.
- **Write** — the scene editor, with media blocks and codex highlighting.
- **Read** — a paged reading view of the same scenes (theme, type size,
  contents, bookmarks, read-aloud). Pictures placed in Write belong here
  in the same document order; at v1.2.5-beta they were text-only — see
  [novel-write-read.md](features/novel-write-read.md).
- **Chat** — a workshop conversation about the book.
- **Review** — a read-through pass.

---

## RPG

`Inventory · Adventure · Roster · Lore · Presets`

A campaign is shaped exactly like a book, so RPG reuses the manuscript model:

| RPG | Novel |
|---|---|
| Adventure | Book |
| Day | Chapter |
| Mission | Scene |
| Event / Battle | Scene beat |

- **Adventure** — the campaign outline. Picking a Mission's *Play* chooses which
  conversation to run it in.
- **Roster** — your immediate team only. Your personas sit under **You**; the
  characters travelling with you under **Team**. Use *Add / remove* to change who
  is on the team; the wider cast stays in Lore.
- **Inventory** — grouped **You → Team → Roster**, each group collapsible. Tap a
  name to open their equipment plate: head, torso, arms, legs, weapon,
  accessory. `+ Item` adds to their pack; tapping a slot equips from it.
- **Lore** — this adventure's codex only. A new adventure starts empty and fills
  as you play. Your full library is under **Extra → Codex**.
- **Presets** — difficulty, not a sampler setting:
  - **Slice of life** — warm, low stakes, nobody really loses.
  - **Normal** — effort is rewarded, mistakes are recoverable.
  - **Hard** — the world pushes back; plans need thought.
  - **Ruthless** — enemies exploit weakness; lasting loss is on the table.

  Each one injects a directive into the system prompt, so the world actually
  behaves that way rather than just shifting randomness.

---

## Chatting

`Contacts · Chats`

A phone-messenger view of your cast.

- **Contacts** — everyone you can talk to, drawn from the character codex.
  Tapping someone opens their conversation, creating it on first contact and
  seeding their greeting.
- **Chats** — open conversations with search, **All / Unread / Groups** filters,
  and unread badges. Badges are real: a chat remembers when you last opened it.
- **Meet someone** writes a brand-new character for you. One arrives
  automatically each day (needs an OpenRouter key; skipped quietly when offline,
  retried next launch). Toggle it in Settings.

The transcript is a modern messenger layout: avatar gutter, the speaker's name
in their own colour, timestamps, and messages grouped when they arrive close
together, with day dividers between sessions.

---

## Storyboard

`Manga · Comic`

Comic pages built from panels. **Manga** reads right-to-left; **Comic** reads
left-to-right.

- **Pages** — page tabs sit above the canvas. `+` adds a page; long-press a tab
  to rename, delete or reorder. Each page is its own canvas.
- **Layout** — pick from six templates (six-panel, pair/wide/split, establishing
  shot, tall/wide/full, vertical strip, splash). The chosen layout draws its
  slots as numbered empty frames, so a page reads as a comic page before you add
  anything. Some layouts tilt panels for slanted gutters.
- **Panels are for media.** Media dropped in lands in the first free slot at that
  slot's size. Tap a panel to select it, then drag to move it; drag the corner
  grip to resize. Drop one panel onto another to stack them.
- **Adjust image** (panel menu) pans and zooms the picture *inside* its frame,
  which is remembered.
- **Text is an overlay**, not a panel: *Add text* on the panel menu drops a box
  you can drag and resize, as either a plain caption or a manga speech bubble
  with a tail.

---

## Notes

One shared board, visible from every mode.

---

## Appearance

**Settings → Appearance** has two levels:

- **Profile** — a whole look: palette, lettering and corner shape together.
  *Classic* (the original), *Fantasy*, *Arcade*, *Synthwave*, *Chill*,
  *Tabletop*.
- **Theme** — Classic keeps all four (Light / Sepia / Dark / OLED). The styled
  profiles carry their own mood, so they offer light or dark only.

Per-section colours layer on top, and *Reset section colours* undoes them.

---

## Prompts and AI

- `/` opens the AI prompt, `\` opens manual entry — from anywhere.
- The **PROMPT** dock collapses to a single line with the ▾ control.
- **Models** picks any OpenRouter text model.
- Add your API key in **Settings**. Without one, everything except generation
  still works.

---

## Sync

Weaverse has no account and no cloud. Sync is peer-to-peer over your own Wi-Fi:

1. Start the desktop EXE — it opens the web hub and shows a password.
2. On Android: **Settings → Open web sync**, enter that password once.
3. Leave **Auto-sync** on.

---

## Import / export

**Import** and **Export** live in the top bar. Novelcrafter ZIP exports with
`novel.docx`/`novel.md` plus a `characters/` folder are supported; codex folders
land in Characters / Locations / Objects / Lore, and characters also become
roleplay cards.

---

## Leaving

Back asks before closing, since an edge swipe is easy to trigger by accident.
Your work is saved as you go.
