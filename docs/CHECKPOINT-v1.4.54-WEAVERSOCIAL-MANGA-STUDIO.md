# Hard Checkpoint — WeaverSocial and Manga Studio

Date: 2026-09-27
App version: `1.4.54` (version code `186`)
Branch: `feature/adams-haven-art-media-packs`
Code checkpoint: `e7a4464` — `Expand WeaverSocial home and scrolling feed`

This checkpoint records the completed WeaverSocial feed/navigation work and
the user-facing Storyboard → Manga Studio rename. The Markdown manuals and
in-app Help/Wiki copy are being synchronized in the documentation commit that
adds this checkpoint.

## WeaverSocial

- The bottom navigation has six destinations: **Home**, **Social**,
  **Servers**, **Explore**, **Alerts**, and **You**.
- **Home** combines stories and the composer with the social timeline, recent
  server conversations, feed activity, and trends when available.
- **Social** is a focused **For you / Following** timeline. Pull-to-refresh
  requests fresh posts; reaching the end generates more character posts.
- Character-post generation is prompted to attach relevant images, GIFs, or
  memes to about half of updates when appropriate. A matching image from the
  character's library is used on 75% of generated posts that do not request
  tagged media, when a matching image exists.
- The Privacy & filters page keeps topic filters off by default, supports
  sensitive-media warnings and muted words, restores posts marked **Not
  interested**, and manages muted accounts, blocked accounts, and characters
  who blocked the writer. Legal content remains visible by default; the Age
  rating fallback is X.
- Characters can block the writer in replies and DMs when it fits their
  character. These blocks can be lifted from **Accounts that blocked you**.

## Manga Studio rename

The user-facing mode and help content call the manga/comic workspace **Manga
Studio**. Existing internal package paths and historical checkpoint names may
still use `storyboard`; they are implementation/history references, not
current user-facing labels.

## Important files

- `app/src/main/java/com/ihy2ln/weaverse/feature/chatting/social/WeaverSocialScreen.kt`
- `app/src/main/java/com/ihy2ln/weaverse/feature/chatting/social/SocialFeedViewModel.kt`
- `app/src/main/java/com/ihy2ln/weaverse/feature/help/HelpContent.kt`
- `app/src/main/java/com/ihy2ln/weaverse/feature/help/WikiContent.kt`
- `Weaverse-Wiki-Manual.md`
- `docs/GUIDE.md`
- `docs/wiki/RPG-WeaverSocial-and-Manga-Studio.md`
- `docs/wiki/Manga-Studio.md`

## Verification and recovery

- The implementation checkpoint is commit `e7a4464`.
- `git diff --check` passed for the implementation and documentation changes.
- The Android Kotlin compile was attempted, but Gradle was not cached and the
  wrapper download failed with certificate validation (`PKIX path building
  failed`). The app build is therefore unverified here; no tests or emulator
  run were repeated for this documentation checkpoint.
- The pre-existing untracked `.claude/` directory is local workspace state and
  was not included in the implementation commit.

To build when the Gradle distribution can be fetched or supplied locally:

```powershell
./gradlew.bat :app:assembleDebug
```
