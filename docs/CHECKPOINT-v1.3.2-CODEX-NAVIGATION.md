# Hard Checkpoint: v1.3.2 Codex Navigation

Date: 2026-08-28  
Tag: `checkpoint-v1.3.2-codex-navigation`

## Purpose

This checkpoint preserves the working state immediately after the Codex entry
navigation update and documentation refresh. It is a recovery point, not a
replacement for the public `v1.3.2-beta` release tag.

## Included behavior

- Codex entries fill the workspace until an entry is selected.
- While editing, the title band carries the shared entry count.
- The compact Codex rail displays one active category and scrollable entries.
- Tapping the category name opens a direct category picker with counts.
- The entry rail is vertically resizable by dragging its divider.
- A single toolbar chevron collapses or restores the rail.
- The last useful expanded height is remembered for the current app session.
- The complete helper guide and wiki-ready pages are stored in the repo.

## Verified build

The Android tasks `:app:compileDebugKotlin` and `:app:assembleDebug` completed
successfully before the checkpoint. The local test package is:

`S:\AI\Novel\Weaververse\Weaverse-Test-v1.3.2-beta-Codex-Categories.apk`

- Application ID: `com.ihy2ln.weaverse.debug`
- Version code: `48`
- Label: `Weaverse Test 1.3.2`
- SHA-256: `1365E4B3124E3D84990E846A46F0527E3CD07F22C8B3AA02DE9F8576FCA8FDC0`

## Recovering this state

To inspect without changing the current branch:

```bash
git show checkpoint-v1.3.2-codex-navigation
git switch --detach checkpoint-v1.3.2-codex-navigation
```

To create a recovery branch:

```bash
git switch -c recovery/codex-navigation checkpoint-v1.3.2-codex-navigation
```

Export app data before installing a differently signed build or clearing app
storage. Git restores source code; it does not restore Android app data.

## Documentation map

- Full user guide: [GUIDE.md](GUIDE.md)
- Wiki home mirror: [wiki/Home.md](wiki/Home.md)
- Technical architecture: [ARCHITECTURE.md](ARCHITECTURE.md)
- Build history: [../BUILD_NOTES.md](../BUILD_NOTES.md)
