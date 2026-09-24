# Prompts and AI

The prompt dock is available on actual writing and play surfaces — Novel Write,
RPG Adventure, Chatting, Brainstorm — not on Home, Bookshelf, Campaign, Window,
reading, Notes, Codex, or management screens. Novel and RPG share one dock
design; Chatting and Brainstorm use the same controls in a slightly simpler
shell.

## Resizing the dock

A thin drag handle runs along the top of the dock. Drag it up to make the dock
taller, down to make it shorter — the instruction box grows or shrinks with
it, so a taller dock is more room to write, not empty space. Double-tap the
handle to snap back to automatic sizing. The dock keeps whatever height you
last dragged it to for the rest of the session, and returns to automatic the
next time the app is opened.

## Compact control row

- **W min–max** — a four-digit box for each end of the word range the AI
  targets (up to 9999 words).
- **/A · \\M** toggles AI generation or manual entry.
- **Model ▾** opens a scrollable list of the cached OpenRouter text models —
  no provider/model string to type by hand. Picking one saves it as that
  book's or campaign's override.
- **More / Less** expands the dock to show templates or presets, the model
  row, and (in Novel) context preview and image attachment.
- Generate/Send, **×** to cancel a stream, **⌫** clear (press and hold to
  undo), and the mic/roll/add-media controls live in the input row.

## Novel: multiple templates at once

Expanding the dock shows every saved prompt template in a two-column grid
split by dividing lines. Tap any number of them — they layer in the order
tapped, numbered **1.**, **2.**, and so on, and every ticked template
contributes its system instructions to the same generation. **Clear** drops
back to the action's default template.

## RPG: Actions, Thoughts, Dialogue, Combat, Roleplay

The same grid holds turn presets instead of templates. Five chips — Actions,
Thoughts, Dialogue, Combat, Roleplay — switch which column of quick moves is
showing; tapping a preset fills it into the composer, except the two entries
that open their own screen (viewing the roster, entering combat). Retry,
Continue, and the media/roll/roster/inventory shortcuts now sit together in
the expanded row above this grid, instead of behind a separate menu. When the
AI offers "Choose the party's direction" moves for the current beat, those
cards now render inside
the dock, above the input, instead of floating over the story.

## The generated draft lives in the story

A finished AI draft no longer opens in a separate review box — it renders
inline in the manuscript, in a panel with a contrasting fill and outline so it
can never be mistaken for text already in the page. It is directly editable
there before you choose **Insert/Replace**, **Retry**, **Compare** (against
the original passage), **Copy**, or **Discard**. Earlier candidates from the
same session stay available as **Earlier 1**, **Earlier 2**, and so on.

A line such as `[MEDIA type=image query=… tags=…]` that sometimes appears in a
draft is **not** an image-generation request — it asks the app to search your
existing media library for a matching asset. Nothing is downloaded or
invented; if nothing matches, the line is dropped when the text is inserted.

## Tap anywhere to place the caret

In Novel Write, tapping anywhere on a line — not only exactly on the text —
moves the caret there. Tapping the empty space below the last line moves the
caret to the end of the scene.

## Models and keys

Keys are stored encrypted in Settings (OpenRouter, OpenAI, Anthropic,
Gemini). Without a key, everything except generation still works. If
generation fails, verify the key, model selection, connection, and /A mode.
The maximum word value is a strong instruction to the model, not a perfectly
enforceable tokenizer boundary.
