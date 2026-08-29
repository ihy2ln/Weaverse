# BUILD_NOTES

Working log for Weaverse: decisions, deviations, known gaps, and a resume
state for picking this back up in a fresh session.

## v1.3.8-beta — selective tabletop checks and unified prompt

- Adventure actions now pass through a backend tabletop ruling before dice
  are generated. Routine movement, conversation, item handling, and DM
  narration resolve without a roll.
- Attacks, defenses, saves, contested actions, explicit roll requests, and
  uncertain Strength/Dexterity/Constitution/Intelligence/Wisdom/Charisma
  actions activate the campaign's dice system.
- D&D/Pathfinder d20 checks apply the active roster character's ability
  modifier and attack/spell proficiency bonus once, then provide that exact
  result to the AI DM and the pixelized low-poly roll animation.
- Adventure and the global writing surfaces now share the same compact prompt
  control with context, word range, model, AI/manual, media, submit, clear,
  and voice controls.
- Local beta artifacts now go to
  `S:\AI\Novel\Weaververse\Beta.Test.Build`; tagged builds continue to GitHub
  Releases.

## Resume state (2026-08-16)

**The app codebase in this repo was replaced today.** Everything under
`app/`, `desktop/`, and `sync-core/` is now the transplanted source of
**InkForge** (a Kotlin Multiplatform Android + Windows-desktop + web-sync
novel/roleplay app, most recently at its own `v0.5.17`), rebranded to
Weaverse (`com.ihy2ln.weaverse`, app name "Weaverse", repo
`github.com/ihy2ln/weaverse`). This supersedes the previous contents of this
repo — an independent, from-scratch native-Android-only rewrite of the same
concept (14 build phases + a revision pass, tagged `v0.1.0`–`v0.2.0` and
released) — which is still visible in this repo's git history (commits
before `Import InkForge → Weaverse transplant`) but is no longer what's on
`main`.

**Why:** the from-scratch rewrite covered core novel/roleplay/AI-provider
functionality but not everything InkForge had already built and shipped
(Windows desktop host, Wi-Fi/remote sync between devices, a web UI served
by that host, OpenRouter model browsing, NovelCrafter ZIP import). Rather
than keep building those out a second time from a spec, the actual InkForge
source — already at a mature, versioned, released state — was brought over
directly and rebranded in place.

**What changed mechanically** (for anyone diffing this against InkForge's
own repo, `github.com/ihy2ln/cursor-novel`):
- `com.ihy2ln.inkforge` → `com.ihy2ln.weaverse` everywhere (namespace,
  applicationId, every package/import, directory tree).
- `InkForgeApp.kt`/`InkForgeDatabase.kt`/`InkForgeAiLog.kt` →
  `WeaverseApp.kt`/`WeaverseDatabase.kt`/`WeaverseAiLog.kt` (filename +
  class name).
- The top-level `InkForge/` PC-package folder became `Weaverse/`, trimmed
  to scripts/docs/sample-import only — the committed `InkForge.exe`
  (1.7 MB) and `InkForge.jar` (29 MB) binaries were **not** carried over;
  they're CI/Release-built artifacts now, not committed source, matching
  the convention this repo already used before the transplant. The Go
  launcher source (`desktop/launcher/`) that produces `Weaverse.exe` was
  carried over and rebranded; rebuild it locally with `go build` (see
  `desktop/launcher/README.md`) if you need a fresh binary before the next
  tagged Release builds one.
- `releases/` (60 MB of committed APKs/zips in InkForge's own repo) and
  `docs/wiki/` per-version screenshot dumps were **not** carried over —
  distribution goes through GitHub Releases / Actions artifacts, not
  binaries-in-git, again matching this repo's pre-existing convention.
- CI (`.github/workflows/build.yml`, `release.yml`) keeps this repo's
  existing shape (base64-decoded keystore from `KEYSTORE_BASE64` +
  `KEYSTORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD` secrets, short-retention
  per-push artifacts) rather than importing InkForge's own workflow files,
  which assumed a different secret-passing convention
  (`KEYSTORE_PATH`) — those workflows were extended to also run
  `:sync-core:test` and build the desktop zip, and now call the committed
  `./gradlew` instead of a bare `gradle` (InkForge's wrapper jar was
  carried over, so there's no longer a reason to rely on `setup-gradle`
  provisioning Gradle directly, the way the pre-transplant repo had to).

**Rebuild documentation**, written from reading this transplanted source
(not the old rewrite's spec, which no longer describes what's in this
repo): [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and the equivalent
[wiki pages](https://github.com/ihy2ln/weaverse/wiki) — detailed enough
that an AI with no access to this repo, working from those docs alone,
could reconstruct the module layout, data model, and core protocols.

## Version history (from InkForge, condensed)

Full narrative entries for each of these lived in InkForge's own
`BUILD_NOTES.md` (not carried over verbatim — this is the condensed
header-only form; ask an AI with web access to pull the original from
`github.com/ihy2ln/cursor-novel` if the full narrative ever matters):

- **v0.5.17** — Codex navy list, hold-picture menu, growing PROMPT + Models, two-file backup
- **v0.5.16** — Visible prompt box, check Accept, X Clear
- **v0.5.15** — Individual prompt toggles, cohesive Write text, title-row Media
- **v0.5.14** — One PROMPT box, extra generators in Settings, action undo
- **v0.5.13** — Plan create menu, Notes editor, compact prompt bar
- **v0.5.12** — Always-on Novel scroll menu, Write header, Library duplicates
- **v0.5.11** — Themed system bars, auto-scrolling title, delete confirm
- **v0.5.10** — Plan scene summaries and tighter chrome
- **v0.5.9** — One prompt surface + Clear Text
- **v0.5.8** — Library Novels dropdown + visible mode capsules
- **v0.5.7** — Scene beat prompt box in Write
- **v0.5.6** — Library modes and Plan Write jump
- **v0.5.5** — Codex + Prompts on top, filled NovelCrafter prose
- **v0.5.4** — Safe chrome, prompt buttons, guiding prose
- **v0.5.3** — NovelCrafter / NovelAI workspace
- **v0.5.2** — NovelCrafter / NovelAI-style workspace
- **v0.5.1** — NovelCrafter Word ZIP + Novel / Manga / Roleplay pictures
- **v0.5.0** — NovelCrafter-style web workspace + auto-sync
- **v0.4.3** — Web hub is the only sync password
- **v0.4.2** — Windows EXE + PC package
- **v0.4.0** — Desktop EXE/JAR · web companion · sync
- **v0.3.19** — Global Notes (not book-scoped) + readable UI
- **v0.3.18** — Modular Notes rail + downloadable Releases APK
- **v0.3.17** — Speech-to-text in Novel / Roleplay / Notes; Notes rail collapse
- **v0.3.16** — Notes microphone / speech-to-text
- **v0.3.15** — Mode-isolated Roleplay content + DM 3×3 canvas
- **v0.3.14** — Manga Move + sharp expanded media
- **v0.3.13** — Media long-press edit popup
- **v0.3.12** — Global `/` and `\` prompt windows; hide manga grid lines
- **v0.3.11** — Visible 6×6 grid, AI/NAI entry, Notes mode
- **v0.3.10** — Portrait navigation crash fix (Samsung S25)
- **v0.3.9** — Characters/Personas create-edit, Appearance color square, audio media
- **v0.3.8** — Roleplay title chrome, 6×6 snap grid, drag-onto stack, portrait
- **v0.3.7** — Title-band menu, scroll gutters, media scroll-through, stack pictures
- **v0.3.6** — Compact Roleplay gen, manga grid, remove/drag media, Characters/Personas, presets
- **v0.3.5** — Plan POV, Codex media, TTS, Roleplay mode, scene-beat vision
- **v0.3.4** — Visible checkmark confirmations
- **v0.3.3** — Checkmarks + press-and-hold edit text popup
- **v0.3.2** — Media crash fix, multi-select, Novelcrafter ZIP import
- **v0.3.1** — Write overlay + prompt injection + Plan POV + Import/Export
- **v0.3.0** — Novelcrafter-inspired library + generation UX
- **v0.2.1** — UI/UX optimization
- **v0.2.0** — Real OpenRouter integration

## Prior Weaverse native-rewrite history (superseded, kept for context)

Before the transplant, this repo held an independently-built native-Android
rewrite: all 14 phases of a build spec plus a "Revision 02" pass complete,
tagged and released through `v0.2.0` — see the git history on `main` prior
to the transplant commit for that log in full. That codebase is gone from
`main` as of this transplant; nothing in the current `app/`, `desktop/`, or
`sync-core/` originates from it.
## Hard checkpoint — v1.3.2 Codex navigation (2026-08-28)

- Added a direct Codex category popup: tap the active category name to jump to
  Characters, Locations, Lore, Objects, or any custom category without repeated
  horizontal dragging.
- Condensed the open-entry Codex rail to the active category and its scrollable
  entries; the selected entry remains highlighted.
- Added a draggable divider so the rail can expand downward or collapse back to
  one line while the detail editor receives the remaining screen.
- Replaced the entry editor's back/forward pair with one expand/collapse
  chevron, restoring the last useful rail height.
- Added [the complete helper guide](docs/GUIDE.md), a
  [wiki-ready mirror](docs/wiki/Home.md), and a
  [checkpoint recovery record](docs/CHECKPOINT-v1.3.2-CODEX-NAVIGATION.md).
- Verified `:app:compileDebugKotlin` and `:app:assembleDebug` offline. Local test
  APK SHA-256: `1365E4B3124E3D84990E846A46F0527E3CD07F22C8B3AA02DE9F8576FCA8FDC0`.
## RPG campaign setup and illustrated Adventure play (2026-08-28)

- New Campaign now selects narrative POV, including multi-character third- or
  first-person options, and persists the full perspective directive for AI use.
- Added Character(s) / Dungeon Master role selection. DM mode reverses table
  authority: the user runs the world and rulings while the AI controls the
  selected player-character party; Adventure wording changes to match.
- Tightened the campaign Past / Present / Future segmented control so the three
  tense choices occupy one compact row without the previous oversized gaps.
- Inventory now resets to the top on entry. Writer / You and the wider cast
  start collapsed, Team roster stays visible, and tagged characters are split
  into NPC, Enemy, and Other groups without a database migration.
- New Campaign now offers AI-backed setting templates as well as rules-system
  templates. Both full directives are stored in campaign guidance, so pairings
  such as High fantasy + D&D 3.5e affect world framing and mechanical rulings.
- Adventure action checks continue to roll privately, but AI responses now
  carry a structured result. The play page displays the outcome label and
  fictional consequence without exposing the raw die, notation, or DC.

- Replaced New Campaign's free-text “Whose eyes” with multi-select Main
  character(s) sourced from personas, Roster, and the Characters Codex.
- Added selectable Past/Present/Future tense and rules templates for D&D 5e,
  Pathfinder 2e, D&D 3.5e, OSR/B/X, Powered by the Apocalypse, Fate Core, and
  custom/systemless play, with an additional house-rules field.
- Campaigns now own a dedicated game-master session carrying those setup
  choices into generation context.
- Adventure now opens directly as an illustrated prose session: one large
  scene image, a lorebook-like story/action record, and a bottom action entry.
- Player actions now receive private ruleset-aware resolution rolls (d20, PbtA
  2d6, or Fate 4dF). The AI game master decides when the roll applies, resolves
  the world's response, and hides mechanical bookkeeping unless asked.
- Added persistent numbered scene boundaries. The game master may advance only
  on a decisive transition, while Next scene and Stay here give the player an
  explicit pacing override without deleting prior scenes.
- Added Previous scene browsing; Next moves forward through saved scenes before
  creating a new boundary from the latest scene.
- Replaced the side-scrolling Town with tappable location picture cards. Every
  shop and landmark has a persistent user-imported art slot.
- Roster now uses portrait-forward cards with class/level, HP, and AC summaries
  that open the full character sheet.
- Inventory items now persist optional artwork, show image-picking slots, and
  reuse the selected image in equipped weapon/accessory plates.
- Roster cards now link directly to the selected character's shared Inventory
  record instead of requiring a separate lookup in the Inventory tab.
- Added slot-matched inventory templates and direct equipment-slot image
  controls. Every equipment position remains a single-item slot.
- Added Backpack as an equipment slot. Equipped backpack capacity controls an
  expandable carried-item panel with per-item slot size, empty slots, and
  over-capacity feedback.
- Add item now accepts artwork before the item is saved. The imported picture
  remains attached across pack and equipment views and can later be previewed,
  replaced, or removed.
- Town shops now use a video-game-style catalog with unit prices, live totals,
  editable buy quantities, minus/plus controls, an AI quantity fill action, and
  quantity-aware delivery to the active character's linked inventory.
