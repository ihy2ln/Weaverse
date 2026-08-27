# Checkpoints

A running log of points in this branch's history the user has flagged as
stable — "if the next round of work goes badly, come back here." Each
entry names the commit, what was considered done at that point, and how
to get back to it.

Reverting to a checkpoint (pick one, depending on how much you want to
discard):

```bash
# See what's changed since the checkpoint, without touching anything:
git diff <checkpoint-sha>..HEAD

# Throw away everything after the checkpoint on this branch (destructive —
# confirm with the user first; this is exactly the kind of operation the
# assistant should never run silently):
git reset --hard <checkpoint-sha>

# Or, non-destructively: branch off the checkpoint instead of rewriting
# history, so both lines of work stay around:
git checkout -b <new-branch-name> <checkpoint-sha>
```

The **released** snapshot this file is based on can also be restored
without this branch:

```bash
git checkout -b restore-v1.2.5 v1.2.5-beta
# or: git reset --hard v1.2.5-beta
```

Release: https://github.com/ihy2ln/Weaverse/releases/tag/v1.2.5-beta

---

## 2026-08-27 — v1.2.5-beta (released Android + desktop)

**Tag**: `v1.2.5-beta`  
**Commit**: `fec9e85d43923326b99631f47d256f7e2124d581`  
(`Skip release-build lint so assembleRelease survives the Kotlin 2.0 LiveData detector crash.`)  
**App**: `versionName = "1.2.5-beta"`, `versionCode = 46`  
**This branch's doc commit**: the commit that adds `docs/CHECKPOINTS.md` and
`docs/features/v1.2.5-beta.md` on top of that tag (code tree identical to the
release; documentation only).

**What's considered done as of here**: the five-workspace app as shipped in
the v1.2.5-beta APK and desktop zip. Novel (Bookshelf / Plan / Write / Read /
Chat / Review), RPG (including Town), Chatting, Storyboard, Notes; OpenAI /
Anthropic / Gemini / OpenRouter; record-level sync that merges without a
relaunch; daily backups; scene history; find/replace; PNG character cards;
EPUB; SillyTavern import. Full inventory:
[`docs/features/v1.2.5-beta.md`](features/v1.2.5-beta.md).

Write already stores pictures as ordered `MediaBlock` / `MediaStackBlock`
entries in scene `docJson` and copies picker files into app storage. Detail:
[`docs/features/novel-write-read.md`](features/novel-write-read.md).

**What's explicitly NOT done / known gaps carried forward**:

- **Novel Read does not show pictures added in Write.** The reader flattens
  each scene to `plainText` and paints newline-split paragraphs only. Media
  blocks never render, and the scene list is a one-shot DB snapshot, so a
  Write → Read switch can also miss a just-saved image. This is the next
  piece of work after this checkpoint.
- `docs/GUIDE.md` still lists Novel as `Plan · Write · Chat · Review` and
  omits Bookshelf / Read (those destinations exist in `AppNavigation.kt`).

**Why this checkpoint exists**: start a new update from the real v1.2.5-beta
release rather than from the later recreated `main` (InkForge-era transplant)
or from the v1.3.x tags that were cut on that other lineage. If the
write-to-read picture work (or anything after it) needs to be thrown away,
return to `v1.2.5-beta` / this doc commit.
