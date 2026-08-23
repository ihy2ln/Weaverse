# What's New — Weaverse v0.5.34

**Released:** August 2026  
**versionName:** `0.5.34` · **versionCode:** `41`  
**APK artifact (CI):** `weaverse-v0.5.34-debug-signed.apk` (when using the debug-signed release path)

## Highlights

### Color & format editor
- Hex color no longer stuck on `#000000`; RGB derived from Compose components (not `Color.value`).
- Editable hex field on the HSV color wheel.
- Format menu no longer loops after dismiss; press highlighted text to reopen.
- Selection retained after applying marks/colors.

### Write workspace
- **Review** lives under **Write ▾** (not Plan): Review scene · Review chapter.
- LLM review with Codex-aware context packing, usage log, and context meter.
- **Focus** control beside Aa / Prompting / Media.
- **Search** magnifying glass on main chrome.
- **Scene snapshots** with Room DB v6 (`scene_snapshots` + migration 5→6).
- Codex mention tap → peek sheet; entry detail subtitle **Codex**.

### Prompt / AI
- Prompt preview of assembled system text.
- Per-action model memory (persisted in Settings).
- Token / context meter (Codex · Scene · User).
- ContextBuilder packs under budget, then builds prompt from **included** entries only.

### Gestures
- Text long-press ~650 ms; media ~400 ms + haptic feedback.

### Help
- In-app **Help** overlay: Tutorial, Manual, What's new.
- Entry points: chrome Help icon; Settings → Open Help.

## Upgrade notes

- First launch after install runs Room migration **5 → 6** (adds `scene_snapshots`).
- Existing books and Codex data are preserved.
- Per-action model map is empty until you pick models (falls back to default).

## Docs

- [User Manual](./USER-MANUAL.md)
- [Wiki Home](./wiki/Home.md)
- [Release notes (detailed)](./RELEASE-NOTES-0.5.34.md)
