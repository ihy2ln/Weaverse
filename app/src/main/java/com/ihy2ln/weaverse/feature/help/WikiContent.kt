package com.ihy2ln.weaverse.feature.help

/**
 * The in-app wiki manual: a web-wiki-style page set rendered by [WikiScreen].
 * Pages are markdown with a small supported subset (##/### headings, bullet
 * lists, | tables |, **bold**, and [[Wiki Links]] between pages). Keep in step
 * with Weaverse-Wiki-Manual.md on disk.
 */
object WikiContent {

    data class Page(
        val id: String,
        val title: String,
        val summary: String,
        val markdown: String,
    )

    val pages: List<Page> = listOf(
        Page(
            id = "home",
            title = "Home",
            summary = "Welcome to the Weaverse wiki",
            markdown = """
                Welcome to the **Weaverse wiki** — the full manual for every
                workspace. Use the sidebar to move between pages, or search.
                Blue links like [[Novel and Reader]] jump straight to a page.

                ## The five workspaces

                {{figure:novel}}

                - [[Novel and Reader]] — plan, write, read, workshop-chat and
                  review your manuscripts.
                - [[RPG]] — AI game-master campaigns, dice, roster, inventory,
                  towns, and illustrated adventure scenes.
                - [[Chatting]] — a Discord-style space where your novels and
                  campaigns are servers with channels and character rooms.
                - [[Storyboard]] — manga and comic page building.
                - [[Brainstorm and Notes]] — chat with the AI to brainstorm and
                  research; plus the classic notes board.

                ## Shared tools

                - [[Codex]] — one library of people, places, things and lore
                  shared by every book and mode.
                - [[Prompts and AI]] — the prompt dock, composer controls,
                  models, keys, and templates.
                - [[Appearance]] — profiles, themes and section colors.
                - [[Backup, Sync and Troubleshooting]] — moving data between
                  devices and fixing problems.

                New here? Start with [[Getting Started]].
            """.trimIndent(),
        ),
        Page(
            id = "getting-started",
            title = "Getting Started",
            summary = "First ten minutes with a fresh install",
            markdown = """
                ## 1. Create a book

                The book button (top-left) opens Home. Pick **Novel**, then +
                to create one — only the title is required. Everything else
                can be filled in later from Plan or the editor.

                ## 2. Learn the three rows

                Across the top: **modes** (Novel, RPG, Chatting, Storyboard,
                Brainstorm/Notes), then the current mode's **sub-modes**
                (Plan, Write, Read…), then **Extra** — Codex, Prompts, Notes,
                Snippets, Chats, Pictures. See [[Navigation and Shelves]].

                ## 3. Add an AI key (optional)

                Without a key everything except AI generation works. Open
                **Settings**, find the OpenRouter section, and paste a key
                from openrouter.ai/keys. Then **Refresh models** in
                Settings → Writing and pick a default model. See
                [[Prompts and AI]].

                ## 4. Open the prompt dock

                From any writing surface, tap **/** for an AI prompt or
                **\\** for manual entry. The dock collapses to one line so it
                never covers your page. Details in [[Prompts and AI]].

                ## 5. Build a codex

                Characters, places and lore live in the [[Codex]] and are
                shared by every book and mode. Entries you mention in your
                text are highlighted and fed to the AI automatically.

                ## 6. Back up

                Settings → Backup schedules automatic local backups, and
                [[Backup, Sync and Troubleshooting]] covers moving to another
                device.
            """.trimIndent(),
        ),
        Page(
            id = "navigation",
            title = "Navigation and Shelves",
            summary = "Home, the three rows, and every shelf",
            markdown = """
                ## Home

                The book button opens Home: a card per workspace with your
                novels underneath.

                ## The three rows

                1. **Modes** — Novel, RPG, Chatting, Storyboard,
                   Brainstorm/Notes.
                2. **Sub-modes** for the current mode — for example Novel:
                   Bookshelf, Plan, Write, Read, Chat, Review.
                3. **Extra** — Codex, Prompts, Notes, Snippets, Chats,
                   Pictures: tools that belong to no single workspace.

                Both mode rows can be reordered; the app remembers your
                layout. **Focus** switches the writing view to the picture
                gallery.

                ## Shelves

                - **Bookshelf** — novels, on the Novel mode.
                - **Campaign shelf** — campaigns, on RPG mode (Campaign or
                  Adventure).
                - **Storyboard shelf** — visual series, on Storyboard mode.

                All three are manuscripts underneath — the same library,
                different doors.

                ## Leaving

                Back asks before closing, because an edge swipe is easy to
                hit by accident. Everything saves as you go.
            """.trimIndent(),
        ),
        Page(
            id = "novel",
            title = "Novel and Reader",
            summary = "Plan, Write, Read, Chat, Review",
            markdown = """
                ## Plan

                The outline: **Acts → Chapters → Scenes → Scene beats**, with
                summaries, status, point of view, labels and word counts.
                Plan's Write ▾ jumps straight to a beat or chapter.

                ## Write

                The scene editor. Documents are blocks — paragraphs, scene
                beats, and media (images, audio) you can drop in and arrange.

                - **Codex highlighting:** entry names and aliases are
                  underlined as you type. Tap one to open the entry; the
                  codex panel has a **‹ Back** header that returns you to the
                  exact spot.
                - **Slash commands** insert codex entries and beats without
                  leaving the keyboard.
                - The prompt dock lives here — see [[Prompts and AI]].

                ## Read

                A distraction-free reader: saved position, contents,
                bookmarks, Paper/Sepia/Night themes, typography controls, and
                read-aloud.

                ## Chat

                The workshop: an AI conversation about the book. Codex
                entries join the context when you mention them, or pin them
                manually with **+ Codex**. Preview shows the exact prompt
                before sending. The composer is the same dock as everywhere
                else — attach pictures, roll dice, dictate, pick the model.

                ## Review

                A read-through pass over the manuscript with scene-by-scene
                notes.

                Your work here feeds [[RPG]] and [[Chatting]] too — every
                mode shares the same [[Codex]].
            """.trimIndent(),
        ),
        Page(
            id = "rpg",
            title = "RPG",
            summary = "Campaigns, adventure play, dice, roster, town",
            markdown = """
                ## Creating a campaign

                The campaign shelf's + opens the options sheet:

                - **Main characters** — pick from You (personas), Roster, or
                  the Characters Codex. The chips scroll in two rows and a
                  **search field** filters long casts. Add or remove any
                  time from the Setup button.
                - **Play as** — Character(s) (you act, AI runs the world) or
                  Dungeon Master (you run the world, AI plays the party).
                - **Setting template** and setting details, **point of
                  view**, **tense**, **rules system** (D&D 5e, Pathfinder 2e,
                  older D&D, OSR, PbtA, Fate, custom), and **house rules**.

                Their full guidance is supplied to the game master during
                play.

                ## Setup — changing a campaign later

                The **Setup** button on the adventure page reopens the same
                sheet for an existing campaign. Add or remove character
                perspectives, change the setting, role, point of view, tense,
                rules, or house rules — saving rewrites the campaign setup
                the AI plays by. Your story is untouched.

                ## Adventure is the play session

                {{figure:rpg}}

                Not a chat: one large scene illustration, ongoing prose, and
                an action box. Uncertain actions receive a hidden rules-aware
                dice roll; the page shows the resolved outcome and
                consequences. **Previous** browses saved scenes; **Next
                scene ›** and **Stay here** control pacing.

                ## Codex links and lore in play

                Codex names and aliases in the story are underlined links —
                tap to open, ‹ Back to return. The AI reads the codex too:
                entries you mention (or that are always-on) join the game
                master's prompt automatically.

                ## Roster, Inventory, Town, Lore

                - **Roster** — portrait cards for You and the team, with full
                  stat sheets one tap away.
                - **Inventory** — per-slot equipment and carried items, with
                  optional item pictures.
                - **Town** — a picture directory of shops and landmarks; BUY
                  sends purchases to the active character.
                - **Lore** — this adventure's own codex, filling as you play.
                - **Presets** — difficulty (Slice of life → Ruthless) that
                  changes how hard the world pushes back.
            """.trimIndent(),
        ),
        Page(
            id = "chatting",
            title = "Chatting",
            summary = "A Discord-style space for your works",
            markdown = """
                ## Servers are your works

                {{figure:chatting}}

                The left rail lists every novel and campaign as a server
                icon; the house button is **Home**, where direct messages
                live. Opening a server for the first time auto-creates
                **#general**, **#lore** and **#brainstorm**, plus one live
                room per tied character (campaign roster members, and novel
                characters linked through the Codex).

                ## Channels and character rooms

                - **Text channels** chat with an AI narrator that knows the
                  work's title, genre, point of view, tense and style guide.
                - **Character rooms** talk to that character in person,
                  seeded with their greeting.
                - The **+** beside Text Channels adds a custom channel; the
                  **+** beside Characters adds a character room.
                - **Long-press** a room to delete it (and its history).

                ## @mentions

                Type **@CharacterName** in any room — full name, or an
                unambiguous first name — and that character joins the AI's
                reply, voiced from their card. Mention several to get a
                group response.

                ## Direct messages

                **Friends** (the second sub-mode tab) lists everyone you can
                talk to. **Meet someone** writes a new character daily when
                you have an OpenRouter key. Tapping a friend opens their DM
                under Home. Old messenger chats appear as DMs too, with real
                unread badges and previews.

                ## The composer

                The same dock as everywhere: word range (**W**), **/A** AI
                versus **\\M** manual entry, the model picker, ✓ send with
                hold-menu retry and continue, × cancel while streaming, a
                live context meter, and the ⌫ clear with hold-to-undo. The
                **+** attaches pictures (they render inline in the chat),
                **🎲** appends a d20 roll, and **🎤** dictates. See
                [[Prompts and AI]].
            """.trimIndent(),
        ),
        Page(
            id = "brainstorm",
            title = "Brainstorm and Notes",
            summary = "AI brainstorm chat, and the notes board",
            markdown = """
                ## Brainstorm — chat with the AI

                {{figure:brainstorm}}

                The Brainstorm/Notes mode is a NovelCrafter-style AI chat for
                ideas and information: plots, worldbuilding, research,
                names, structure, honest feedback. It is not character chat —
                the AI is a brainstorming partner, not a persona.

                ## Main categories and sub-categories

                **+ Add** starts a main category. Every main row carries a
                **+** beside its ⌫ delete — that adds a **sub-category**
                nested underneath (indented with a `└` prefix). Long-press a
                category to delete it; deleting a main category removes its
                sub-categories and their history. Threads are app-wide,
                shared across every book and mode.

                - **Codex context** — + Codex pins entries; mentioning an
                  entry pulls it in automatically; chips show what the AI
                  will see; **Preview** shows the exact prompt.
                - **Composer** — the full dock: word range, model picker,
                  ✓ send with hold-menu **retry and continue**, × cancel,
                  usage and context meter, ⌫ clear with hold-to-undo, plus
                  **+** picture attachments that render inline, **🎲** rolls
                  and **🎤** dictation.

                ## Notes — the board

                The classic notes board is the mode's second sub-mode —
                switch with the **Brainstorm / Notes** pills in the
                sub-mode row. Notes have a title, rich text, images and
                audio, shared across all books, and integrate with the
                prompt dock — you can generate into a note or paste from it.
            """.trimIndent(),
        ),
        Page(
            id = "storyboard",
            title = "Storyboard",
            summary = "Manga and comic pages — import, separate, edit, translate",
            markdown = """
                ## Window, Manga, Comic

                **Window** is the cover-art shelf for every storyboard.
                **Manga** reads right-to-left; **Comic** the other way. The
                same pages are shown — only the reading direction changes.

                ## Pages

                Page tabs sit above the canvas. **+** adds a page;
                long-press a tab to rename or delete it. **Add pages** imports
                one or more page images, or a whole PDF, CBZ/ZIP, or long
                webtoon strip and creates the ordered pages automatically.

                ## Import generated panel artwork

                Generate artwork outside the Android app with Codex/ImageGen,
                export it as PNG/JPG/WEBP, then return to Storyboard. Tap an
                empty layout frame and choose **Import generated panel**. You
                may select multiple images; they retain picker order and fill
                the selected slot followed by the next free slots.

                If occupied artwork is selected, Weaverse asks whether to use
                the next free slot or explicitly replace it. Imported files are
                copied into private app storage immediately. The Android app
                does not access Windows paths and does not require an OpenAI
                key for this file-import workflow.

                ## Layouts and panels

                Six templates draw numbered empty frames, so a page looks
                like a comic page before anything is added. Media lands in
                the first free slot at that slot's size. Tap a panel to
                select, drag to move; drag the corner grip to resize. Drop
                one panel onto another to stack them. **Adjust image** pans
                and zooms inside the frame and is remembered per panel.

                ## Import a whole manga, comic, or webtoon

                When creating a storyboard from the **+ Storyboard** button,
                choose an optional whole comic file. You can also use **Add
                pages** above any existing storyboard. Each imported page
                lands as one full-page panel. Long-press the desired panel to
                select it and open **Picture tools**:

                - **Separate panels (AI)** — a Vision model finds every
                  panel on the page; each one is cropped into its own
                  picture and placed in reading order on a new page. Needs
                  a Vision-capable model in Settings → Writing.
                - **Separate panels (offline)** — the same split, done on
                  device by detecting the white gutters between panels.
                  Free, no key, and it never sends the image anywhere. It is a
                  near-white, page-spanning gutter heuristic; dark, borderless,
                  or irregular gutters may require AI detection.

                Detection reports whether AI or offline analysis ran and
                explains unreadable, zero-panel, and one-panel outcomes. A
                successful split is always written to a new page; the imported
                original page remains unchanged.

                ## Picture editor

                **Edit picture** opens the page full-screen:

                - **Brush** erases text with a round tip (choose the size
                  and the fill color — bubble white, ink black, page
                  cream). **Rect** erases a dragged rectangle. **Undo**
                  steps back; **Save** re-registers the picture so every
                  reference updates.
                - **Read & translate** sends the picture to a Vision model,
                  which finds every text region, transcribes it, and
                  translates it into the language you pick. Found regions
                  are highlighted — tap one to erase the original text
                  instantly, use the checklist to choose which to keep, and
                  **Add translations** applies them as speech-bubble
                  overlays with your new language.

                ## Generate pictures with AI (cloud)

                Picture tools → **Generate picture (AI)** connects to a
                cloud image model through OpenRouter (Nano Banana, Flux,
                GPT-Image and friends — see Settings → Models → Image
                generation). Describe the panel, pick the model, and the
                generated picture lands on the page like imported media,
                ready for the editor and overlays. Long-pressing an empty
                layout slot offers both Add and Generate.

                Text overlays themselves are unchanged: **Add text** drops
                a draggable caption or a manga speech bubble with a tail.
            """.trimIndent(),
        ),
        Page(
            id = "codex",
            title = "Codex",
            summary = "One shared library for every mode",
            markdown = """
                ## What lives here

                Characters, Locations, Objects, Lore, Factions, Subplots,
                Magic/Tech, Events, Organizations and Notes — one codex
                shared by every book and mode. Open it from **Extra →
                Codex** in any workspace.

                ## Entries

                Each entry has a name, aliases, a color, an image, and free
                text. Lorebook fields (keys, secondary keys, insertion
                order, always-include, probability) control exactly when the
                AI sees it.

                ## Mentions and links

                - In the Novel editor, entry names and aliases are
                  highlighted while you write; tap to open.
                - In RPG adventure prose, they are underlined links too.
                - Mentioning an entry in Novel Chat, RPG play, or
                  [[Brainstorm and Notes]] pulls it into the AI's context
                  automatically — the chips show what was detected.

                ## Roster sheet & inventory (RPG parity)

                Entries in a **Characters** category get the same roster
                sheet the RPG Roster uses: class, species, level, HP,
                armor class, proficiency bonus, the six ability scores,
                and attacks & actions — editable right on the entry with
                steppers. **Every entry** (any category) also carries an
                **inventory**: items with quantity, weight, cost, tags and
                notes, with an active ■/□ toggle, matching the RPG
                Inventory system. Saving the entry persists both.

                ## AI sort into Codex / Roster / Inventory

                Long-press an adventure message and pick **AI sort into
                Codex / Roster / Inventory…**. The AI splits the text into
                the right sections — character-sheet facts, inventory items
                (with the carrier who holds them), and codex lore with a
                suggested category — and shows a review dialog where you
                tick what to keep. Unsure rows are flagged so you can
                re-route them. Placing merges characters into the roster
                with linked codex entries, files items into inventories,
                and writes lore into the codex.

                ## In and out

                Novelcrafter ZIP imports map their codex folders to these
                categories. The RPG game master also writes entries as you
                play — characters, items and scene synopses land in the
                adventure's codex.
            """.trimIndent(),
        ),
        Page(
            id = "prompts",
            title = "Prompts and AI",
            summary = "The dock, the composer, models and keys",
            markdown = """
                ## The prompt dock

                **/** opens an AI prompt and **\\** opens manual entry from
                any writing surface. The dock collapses to one line so it
                never covers your page.

                ## Composer controls

                {{figure:prompts}}

                Every prompt window — Novel, RPG, Chatting, Brainstorm —
                carries the same controls:

                - **W min–max** — the word range the AI targets; the ceiling
                  also trims the finished reply.
                - **/A vs \\M** — AI generation versus filing your text
                  without calling a model.
                - **✓ send** — tap to send. **Hold ✓** for ↻ retry (redo the
                  last reply) and » continue (keep going).
                - **× cancel** — appears while the AI is streaming.
                - **⌫ clear** — tap deletes your draft; **press and hold
                  restores it**.
                - **+ attach** — import pictures/videos that ride along with
                  the next message and render inline in the chat panes.
                - **🎲 roll** — appends a fresh `[d20: N]` to the draft.
                - **🎤 dictate** — speech becomes text in the draft.
                - **Context meter** — `context: used / limit`, a live
                  estimate for the chosen model.

                ## Models and keys

                Models picks any OpenRouter text model, per surface, with
                the Settings default underneath. Keys are stored encrypted
                in Settings (OpenRouter, OpenAI, Anthropic, Gemini). Without
                a key everything except generation still works.

                ## Prompt Collection

                Persisted mode templates (Novel, RPG, Chatting, Storyboard),
                multi-select genre add-ons, age ratings from PG to X, and
                **Refresh**, which rebuilds a read-only preview of the exact
                effective system prompt.

                ## Usage

                Every AI reply records prompt/completion tokens and cost;
                the usage line under each composer shows the last call.
            """.trimIndent(),
        ),
        Page(
            id = "appearance",
            title = "Appearance",
            summary = "Profiles, themes and section colors",
            markdown = """
                ## Profiles

                A profile is a whole look — palette, lettering and corners
                together: **Classic**, **Fantasy**, **Arcade**, **Synthwave**,
                **Chill**, **Tabletop**.

                ## Themes

                Classic keeps all four moods (Light, Sepia, Dark, OLED). The
                styled profiles carry their own mood and offer light or dark.

                ## Section colors

                Per-section tints layered on top of any profile. **Reset
                section colors** undoes them. Set them in Settings →
                Appearance.
            """.trimIndent(),
        ),
        Page(
            id = "backup",
            title = "Backup, Sync and Troubleshooting",
            summary = "Moving data and fixing problems",
            markdown = """
                ## Import and export

                **Import/Export** sits in the top bar. Novelcrafter ZIPs are
                supported — codex folders become Characters, Locations,
                Objects and Lore, and characters also become roleplay cards.
                Novels export to EPUB.

                ## Backup

                Settings → Backup schedules automatic local backups. Keep
                them on; they are your safety net.

                ## Sync

                No account, no cloud. Start the desktop companion, read the
                password off the web hub, then Settings → Open web sync on
                the phone. Leave **Auto-sync** on.

                ## Troubleshooting

                - **AI replies fail** — check the key in Settings, the
                  selected model, and the context meter; retry from the ✓
                  hold-menu after a rate limit.
                - **Nothing streams** — confirm a default model exists
                  (Settings → Writing → Refresh models).
                - **Lost text** — Undo covers most actions; backups cover
                  the rest. The ⌫ hold-undo restores a just-deleted draft.
                - **Crash log** — Settings → Show crash log, then Copy and
                  send it along.
            """.trimIndent(),
        ),
    )

    /** Case-insensitive page search across titles, summaries and content. */
    fun search(query: String): List<Page> {
        val q = query.trim()
        if (q.isBlank()) return pages
        return pages.filter {
            it.title.contains(q, true) ||
                it.summary.contains(q, true) ||
                it.markdown.contains(q, true)
        }
    }

    fun findByTitle(title: String): Page? =
        pages.firstOrNull { it.title.equals(title, ignoreCase = true) }

    fun findById(id: String): Page? = pages.firstOrNull { it.id == id }
}
