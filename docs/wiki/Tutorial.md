# Weaverse Tutorial (v1.3.21-beta)

A practical guide to the prompt system, the Novel write section, and the RPG
Adventure tools as they ship in this build.

---

## 1. The Prompt Dock

The dock sits at the bottom of every writing surface (Novel → Write, Roleplay,
Adventure). It is a single compact line:

```
PROMPT ▴ · Model-name          context: 790 / 1048.6k
[ your prompt text… ]  W 50–100  ⌖¶3 →End /A ✓ 🎤
```

- **PROMPT ▴** collapses the dock to one tiny label. The model name sits right
  next to it — tap it to pick a model (the Settings default stays unless you
  change it per generation).
- **W 50–100** is the word range. Defaults are minimum 50, maximum 100; the
  maximum is a hard cap that generation is trimmed to.
- **⌖¶3 / →End** is the insert target:
  - `→End` appends generated text at the end of the scene.
  - `⌖¶3` inserts at the tapped caret. Tap any spot in the document and the
    chip updates to that paragraph; the generated prose splits the paragraph
    right at your cursor and lands between the halves.
  - As soon as a scene is open, the target defaults to the end of its last
    paragraph — you always have a valid destination.

### The ✓ button (tap and hold)

| Action | How | What it does |
|---|---|---|
| ✓ Confirm | Tap | Generates from the typed prompt (AI mode) or inserts the text (manual mode). |
| ↻ Retry | Hold → ↻ | Resubmits the last prompt without retyping. |
| » Continue | Hold → » | Keeps writing from where the document left off — works even with an empty prompt box. |

The hold menu opens even when the prompt box is empty, so Retry and Continue
are always reachable. The menu uses symbols only: ✓ confirm, ↻ retry, » continue.

### The 🎤 button (tap and hold)

Tap starts voice input. Hold opens the composer menu:

- **+** attach a picture (used by the Describe-Image prompt path).
- **🎲** roll a d20 — appends `[d20: n]` to the prompt text.
- **🎤** voice input again.

While text streams in, the ✓ becomes a small × that cancels the generation.

### Modes

- **/A** (default) is AI generation.
- **\M** is manual entry — text goes into the document without a model call.
  Press `/` or `\` on a hardware keyboard to jump to either mode.

### Adults only

Every writing prompt ships with an adult frame: all romantic or sexual
participants are unambiguously 18+, pursuit is enthusiastic and consensual,
and the tone is adult male wish fulfilment (playful sensual tension,
fan-service energy, romantic escalation, comedy).

---

## 2. Novel → Write

- Type directly in the scene; the editor keeps your caret where you leave it,
  even when you move to the prompt dock.
- Typing `/` in the scene opens the AI prompt dock; `\` opens the manual one.
- Long-press selected text for edit actions: bold/italic, color, Add to Codex,
  Shorten/Extend/Replace (AI passes anchored to your selection).
- **Scene beats**: the blue SCENE BEAT card holds your prompt; Generate plays
  the beat, then ✓ accepts (prose lands right after the card) or ↻ retries.
- **Summarize** runs the Scene Summarizations prompt into the scene summary.
- **History** keeps hourly snapshots plus manual snapshots; Restore brings any
  revision back.

### Prompt Collection (Prompts ▾ tab)

Shared by Novel, Roleplay, and Notes. Categories: Adams Haven, Continue,
Custom, Expand, Prompt Components, Roleplay, Scene Beat Completions, Scene
Summarizations, Text Replacements, Workshop Chats.

Every template is themed for adult male wish fulfilment and actively consults
the Codex entries **WAHB, WAH, WAHO, AFM, Gender Ratio, GKOM, and Celestium**.
The Gender Ratio is treated as lived social worldbuilding; Celestium as
established technology/lore; acronyms keep their project-defined meanings.

Prompt Components (`AdditionalContext`, `AdditionalInstructions`,
`Chat/DefaultContext`, `Chat/DefaultInstructions`) are editable building blocks
the other prompts include.

---

## 3. RPG Adventure

The Adventure screen is the illustrated tabletop session:

- Same prompt dock, plus a **🎲 Roll** action in the composer menu: submits your
  typed action and forces a resolved check.
- The ✓ hold-menu here also has **↻ reroll** (regenerates the latest reply) and
  **» continue**.

### Roster and Inventory

Hold the 🎤 composer button for five symbols:

| Symbol | Action |
|---|---|
| **+** | Attach a picture to the scene. |
| **🎲** | Roll — force an action check. |
| **👤+** | Add to roster. |
| **🛍** | Add to inventory. |
| **🎤** | Voice input. |

- **Add to roster (👤+)** reads the recent scene and files every named
  character into the roster — party members flagged automatically, blank
  sheets created for new characters (class, species, level, HP, AC, looks).
  Existing characters only get their missing details filled in.
- **Add to inventory (🛍)** extracts the items and files each one into its
  **carrier's** inventory — Seren Vex's hexblade goes to Seren Vex, not to you.
  Items tagged "party" land in your (persona) pack. Quantities stack.
- After every DM reply, the same bookkeeping runs automatically.
- A status line above the dock confirms each action
  ("Added to roster: Seren Vex", "Items filed: Obsidian hexblade → Seren Vex").
- Nothing found? You get a blank editable entry and a message saying so — edit
  it in Roster / Inventory later.

---

## 4. Troubleshooting

- **No generation**: check the OpenRouter key (Settings → AI Connections), the
  model, and that the dock shows **/A**.
- **Text went to the wrong place**: flip the target chip to `⌖¶n` and tap the
  spot in the document before pressing ✓.
- **Roster/inventory adds find nothing**: make sure the scene actually mentions
  the character/item and that an API key is set; otherwise blank entries are
  created for manual editing.
- Wiki homepage: see `Home.md` in the wiki; architecture notes live in
  `docs/ARCHITECTURE.md`.
