# Weaverse v0.5.28 — User Manual

**App:** Weaverse (`com.ihy2ln.weaverse`)  
**Version:** 0.5.28 (versionCode 41)  
**Platforms:** Android · Windows desktop · Web hub

This manual matches the in-app **Help** overlay (chrome **?** icon, or **Settings → Open Help**).

---

## Quick start

1. Open **Library** and select or create a book.
2. Set your **OpenRouter API key** in Settings (required for AI).
3. Go to **Novel → Write** and draft a scene.
4. Press **/** to open AI prompting, or **\** for a manual insert.
5. Use **Write ▾** for Scene beat, Review scene, Review chapter, Snapshots.

---

## Chrome map

| Control | What it does |
|---------|----------------|
| Library | Books & series |
| Settings | Appearance, models, sync, backup |
| Help | Tutorial · Manual · What's new |
| Search | Find scenes, Codex, chats |
| Workspace | Novel · Roleplay · Notes |
| Mode | Plan / Write / Chat (Novel) |
| Focus (shell) | Story canvas vs Pictures |
| Focus (Write) | Hide chrome; write full-bleed |
| Tools | Codex, Prompts, Notes, Snippets, Chats, Pictures |

---

## Writing

- **Format:** long-press text (~650 ms). Dismiss, then press the same highlight to reopen. Selection is kept after style changes.
- **Color:** HSV wheel + editable `#RRGGBB` hex (fixed in 0.5.28 — no longer stuck on black).
- **Media:** shorter long-press + haptic; stack/reorder from the media menu.
- **Codex mentions:** tap a linked name to peek; open full Codex from the sheet.
- **Snapshots:** save/restore scene document versions (Room migration 5→6).
- **Review:** Write ▾ → Review scene / chapter — LLM continuity notes with context meter.

---

## AI & prompting

- **Preview** shows the assembled system blocks (includes resolved where applicable).
- **Models** remembers a model per action (Scene beat, Shorten, Extend, Replace, Summarize, Review, Roleplay swipe, Workshop, Prompt AI).
- **Context meter** breaks down Codex / Scene / User tokens.
- Context packing **drops overflow Codex entries from the prompt text** when the budget is exceeded (meter and sent text stay aligned).

---

## Roleplay & Notes

- Roleplay: Messenger, DM (3×3), manga (6×6) workspaces.
- Swipe replies remember their model override.
- Notes board is shared across modes.

---

## Sync & export

- **Settings → Sync** for peer Wi-Fi / tunnel with desktop/web.
- Import/Export for Novelcrafter-style ZIPs and backups.
- Desktop package: see repo `Weaverse/` and GitHub Releases.

---

## Related docs

- [What's New 0.5.28](./Whats-New-0.5.28.md)
- [Wiki Home](./wiki/Home.md)
- [Architecture](./ARCHITECTURE.md)
- [README](../README.md)
