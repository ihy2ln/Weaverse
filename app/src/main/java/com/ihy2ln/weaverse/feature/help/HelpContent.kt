package com.ihy2ln.weaverse.feature.help

/**
 * The in-app guide, kept as data so the same text can be searched and rendered
 * without pulling in a Markdown parser. Mirrors docs/GUIDE.md.
 */
data class HelpSection(
    val id: String,
    val title: String,
    val summary: String,
    val entries: List<HelpEntry>,
)

data class HelpEntry(val heading: String, val body: String)

object HelpContent {
    val sections: List<HelpSection> = listOf(
        HelpSection(
            id = "start",
            title = "Getting around",
            summary = "Home, and the three rows across the top.",
            entries = listOf(
                HelpEntry(
                    "Direct messages",
                    "The DM envelope under Home opens a contacts screen listing every codex " +
                        "character, with a search box. Tap someone to open their DM — a DM holds " +
                        "just the two of you.",
                ),
                HelpEntry(
                    "Home",
                    "The book button opens Home. It is the way into every workspace, " +
                        "not just novels — a card per mode, with your novels underneath.",
                ),
                HelpEntry(
                    "The three rows",
                    "1) Modes: Novel, RPG, Chatting, Storyboard, Notes. " +
                        "2) Sub-modes for whichever mode you are in. " +
                        "3) Extra: Codex, Prompts, Notes, Snippets, Chats and Pictures — " +
                        "the tools that do not belong to any one mode.",
                ),
                HelpEntry(
                    "Focus",
                    "Switches between the writing view and the picture gallery.",
                ),
                HelpEntry(
                    "Leaving",
                    "Back asks before closing, because an edge swipe is easy to hit by " +
                        "accident. Your work is saved as you go.",
                ),
            ),
        ),
        HelpSection(
            id = "novel",
            title = "Novel",
            summary = "Plan · Write · Chat · Review",
            entries = listOf(
                HelpEntry(
                    "Starting a book",
                    "A new book opens the same four-step start a campaign uses: Create Your Own " +
                        "Story, the Chapter One plan, Verify Your Story, and Chapter One itself. " +
                        "The AI takes part in three of the four — it suggests answers, drafts the " +
                        "plan, then writes the opening scene — and each can be retried, stopped, " +
                        "or replaced with a written fallback. Every box stays editable. " +
                        "The company clicker (Solo, Duo, Party, Team) is a hard rule: on Solo " +
                        "nobody joins the protagonist, though other people still appear and are " +
                        "left behind. Finishing saves Chapter One into the book's first scene. " +
                        "The table-only parts of the campaign start are left out — no play-as " +
                        "role, game mode, rule system or house rules, and no reader choices.",
                ),
                HelpEntry("Plan", "The outline: Book, Chapter, Scene, Scene beat."),
                HelpEntry(
                    "Write",
                    "The scene editor, with media blocks and codex highlighting. " +
                        "Codex entry names render as underlined links — tap one to open " +
                        "the entry, then Back to return to your exact spot.",
                ),
                HelpEntry(
                    "Read",
                    "A distraction-free reader with saved position, contents, bookmarks, " +
                        "Paper/Sepia/Night themes, typography controls, and read-aloud.",
                ),
                HelpEntry(
                    "Chat",
                    "A workshop conversation about the book. Codex entries can be pinned " +
                        "into the context with + Codex, or they join automatically when " +
                        "you mention them.",
                ),
                HelpEntry("Review", "A read-through pass."),
            ),
        ),
        HelpSection(
            id = "rpg",
            title = "RPG",
            summary = "Inventory · Adventure · Campaign · Roster · Lore · Presets",
            entries = listOf(
                HelpEntry(
                    "Create a campaign",
                    "Choose one or more main characters from You, Roster, or the Characters " +
                        "Codex. Choose Character(s) or Dungeon Master mode, a single- or " +
                        "multi-perspective point of view, AI-backed setting/rules templates, and " +
                        "narrative tense, then add setting details and house rules. Their full " +
                        "guidance is supplied to the game master during play.",
                ),
                HelpEntry(
                    "Setting browsers and favorites",
                    "Setting Template and Setting Details open as Main section → Theme → " +
                        "Preset browsers. Sections include Adams Haven worlds, Fantasy, Action, " +
                        "survival, science fiction, games/media, 18+, and Custom. Tap ☆ beside " +
                        "any preset to keep it in Favorites. Slow Life and Overpowered " +
                        "Protagonist are available under Setting Details. Use + Add in either " +
                        "browser to save your own named section, theme, and AI guidance.",
                ),
                HelpEntry(
                    "Mode and rule system",
                    "Campaign setup keeps Mode and Rule system separate. Focused Tactical Cards " +
                        "uses the Adams Haven card loop; D&D d20 uses the saved character sheet " +
                        "and deterministic checks; Text Reactions keeps written actions primary. " +
                        "Other RPG can enter combat directly, and an immediate AI encounter opens " +
                        "the same native screen automatically in the saved campaign mode. " +
                        "A one-battle override never changes the saved campaign mode, and the " +
                        "next encounter returns to that mode.",
                ),
                HelpEntry(
                    "Create Your Own Adventure",
                    "After Campaign Setup, answer six editable cards: plot, first goal, first " +
                        "scene, starting party, tone, and opening complication. Tap a campaign-aware " +
                        "preset to fill a card, write your own answer, or Skip. Skipped answers are " +
                        "saved as explicit blanks and can be completed by the chapter fallback. " +
                        "The page scrolls; presets scroll horizontally; the prompt writer is only " +
                        "a fallback.",
                ),
                HelpEntry(
                    "Chapter plan and verification",
                    "The AI turns Campaign Setup and the six CYOA answers into an editable Chapter " +
                        "One Plan with a separate opening-scene guideline. Progress is saved from " +
                        "1–100%. Then Verify Your Adventure reviews setup, answers, outline, and " +
                        "scene plan together. Retry or Use authored fallback if generation fails. " +
                        "Scene One is not created until verification is accepted.",
                ),
                HelpEntry(
                    "Scene choices and action bubbles",
                    "Scene One and later scenes show three AI choice cards plus a fourth custom " +
                        "action, as cards inside the expanded prompt dock, right above the input. " +
                        "Five chips there — Actions (observe, move, use, help), Thoughts (recall, " +
                        "study, consider), Dialogue (speak, persuade, intimidate, lie), Combat " +
                        "(attack, defend, retreat, enter combat), and Roleplay (react in " +
                        "character, view the roster) — switch a grid of shortcuts. These fill " +
                        "the composer rather than replacing freeform play.",
                ),
                HelpEntry(
                    "Text selection and scene art",
                    "Select any part of story text to reveal Add text to… and open the existing " +
                        "capture popup. Scene art is chosen from eligible local library pictures " +
                        "by matching the scene, setting, category, mood, and tags. If no image fits, " +
                        "the scene still loads normally.",
                ),
                HelpEntry(
                    "Dungeon Master mode",
                    "You control scene framing, the world, NPCs, and rulings. The AI plays the " +
                        "selected character party and responds to your DM prompts with its " +
                        "decisions, actions, dialogue, and rules-aware reactions.",
                ),
                HelpEntry(
                    "Setup — campaign options",
                    "The Setup button on the adventure page reopens the campaign options " +
                        "sheet at any time: add or remove character perspectives (You, Roster, " +
                        "or Characters Codex), switch Dungeon Master/Character play, change the " +
                        "setting template and details, point of view, tense, rules system, and " +
                        "house rules. Saving rewrites the campaign setup the AI plays by.",
                ),
                HelpEntry(
                    "Codex links and lore in play",
                    "Codex names and aliases appear as underlined links inside the adventure " +
                        "story — tap one to open the entry, then ‹ Back. The AI also reads the " +
                        "codex: entries you mention (or that are always-on) are pulled into the " +
                        "game master's prompt automatically.",
                ),
                HelpEntry(
                    "Adventure is the play session",
                    "Adventure is not a messenger chat. The current scene uses one large " +
                        "illustration, an ongoing prose story, and an action box at the bottom. " +
                        "Your uncertain actions receive a hidden rules-aware dice roll; the page " +
                        "shows its resolved outcome and consequences without exposing the raw die. " +
                        "Play stays in the numbered scene until you or the " +
                        "game master advances it; Previous browses saved scenes, while Next scene " +
                        "and Stay here let you control or overrule pacing.",
                ),
                HelpEntry(
                    "Roster",
                    "Portrait cards for You and the immediate team. Character cards show class, " +
                        "level, HP, and AC; tap one for its full stat sheet, or Inventory & gear " +
                        "for that same character's linked equipment. Add / remove changes the " +
                        "team while the wider cast stays in Lore.",
                ),
                HelpEntry(
                    "Character cards during play",
                    "Tap Character cards in the scene header, or open Other RPG and choose the " +
                        "party viewer. Focused Tactical Cards shows ATK, DEF, SUP, SPD, AP, EP, " +
                        "and the signature card. D&D d20 shows abilities and modifiers. Every " +
                        "card opens the complete editable sheet without ending the campaign. " +
                        "New characters receive filled d20 and tactical starting statistics.",
                ),
                HelpEntry(
                    "Inventory",
                    "Starts at the top with Writer / You and the wider roster collapsed; Team " +
                        "roster stays visible. Tags separate NPCs, Enemies, and Other. Tap a " +
                        "name for one-item head, torso, arms, legs, weapon, accessory, and backpack " +
                        "slots. Add item can save a picture with the item; tap existing art to " +
                        "preview, replace, or remove it. Pictures follow items between the pack and " +
                        "equipment. The equipped backpack sets the expandable carried-item capacity.",
                ),
                HelpEntry(
                    "Town",
                    "A tappable picture directory of shops and landmarks. Empty slots let you " +
                        "add location art; once filled, tap the picture to enter that place. Shop " +
                        "menus include unit prices, an editable quantity, AI fill, live totals, and " +
                        "BUY sends the chosen amount to the active character's inventory.",
                ),
                HelpEntry(
                    "Lore",
                    "This adventure's codex only. A new adventure starts empty and fills " +
                        "as you play. Your full library is under Extra then Codex.",
                ),
                HelpEntry(
                    "Presets are difficulty",
                    "Slice of life, Normal, Hard, Ruthless. Each injects a directive into " +
                        "the prompt, so the world really does push back that much — it is " +
                        "not just a randomness slider.",
                ),
            ),
        ),
        HelpSection(
            id = "chatting",
            title = "Chatting",
            summary = "A Discord-style workspace for your works",
            entries = listOf(
                HelpEntry(
                    "Servers are your works",
                    "The left rail lists every novel and campaign as a server icon. The " +
                        "house button is Home; the envelope marked DM right below it opens " +
                        "direct messages from anywhere. Opening a server for the first time " +
                        "auto-creates #general, #lore, and #brainstorm, plus one room per " +
                        "character tied to the work.",
                ),
                HelpEntry(
                    "Who is in a room",
                    "Every room seats 1-5 people from the work's Codex characters, shown as " +
                        "an avatar strip under the room header. Each channel gets its own " +
                        "stable cast, so #general and #lore are not the same crowd. " +
                        "Long-press an avatar to remove that person.",
                ),
                HelpEntry(
                    "@mention autocomplete",
                    "Typing @ opens a name picker above the message box that narrows as you " +
                        "type: @k lists the K names, @kae narrows further. Tap a name to " +
                        "complete it. Names with a + are not in the room yet and will be " +
                        "pulled in when you send.",
                ),
                HelpEntry(
                    "@mentions pull people in",
                    "Type @Name (full name or unambiguous first name) and that person is " +
                        "added to the room for good, with a joined line in the history. They " +
                        "answer first and others may chime in, each as their own message " +
                        "with their own avatar.",
                ),
                HelpEntry(
                    "Rooms keep their place",
                    "Each room keeps its own unsent draft, scroll position, and history, so " +
                        "switching rooms and coming back lands you where you left off. " +
                        "Drafts survive a restart and the server reopens on your last room.",
                ),
                HelpEntry(
                    "Reading and sending",
                    "Message text is selectable — press and hold to select and copy. The " +
                        "picture button in the room header attaches an image, which posts " +
                        "into the room and renders inline.",
                ),
                HelpEntry(
                    "Home",
                    "Home lists Recent Conversations, then Direct Messages, then a section " +
                        "per server with all of its channels and character rooms. Friends " +
                        "lists everyone you can talk to; Meet someone writes a new character " +
                        "daily when you have an OpenRouter key.",
                ),
                HelpEntry(
                    "The prompt window",
                    "Chatting uses the same resizable prompt window as Novel, not a slim " +
                        "bar: drag to resize, then use the message box and Send, the /A AI " +
                        "versus \\M manual chip, the word target, the model row, and a live " +
                        "context meter. More opens Attach image, Dictate, Roll d20, Retry, " +
                        "Continue, Clear, All models, and the chat quick messages.",
                ),
                HelpEntry(
                    "Emoji and collapsing",
                    "More opens an Emoji picker beside Attach image; tap one to drop it in the " +
                        "draft. The arrow next to Less/More folds the window down to the message " +
                        "box and Send, and back again.",
                ),
                HelpEntry(
                    "Quick messages",
                    "Chat-themed templates under More: Say Hi, Catch Me Up, Banter, React " +
                        "To That, Ask The Room, Make Plans, Check In On Me, Nudge For A " +
                        "Reply, Continue Chat, and Out of Character Note. Tick as many as " +
                        "you want; they layer in the order picked.",
                ),
                HelpEntry(
                    "Chat voice, not prose",
                    "Replies are written as chat messages: first person, present tense, " +
                        "short. No narrator, no scene-setting, no third-person description, " +
                        "no asterisk stage directions.",
                ),
            ),
        ),
        HelpSection(
            id = "brainstorm",
            title = "Brainstorm/Notes",
            summary = "Chat with the AI to brainstorm and research",
            entries = listOf(
                HelpEntry(
                    "What it is",
                    "A NovelCrafter-style AI chat for ideas and information — not talking " +
                        "to bots. Ask about plots, worldbuilding, research, names, structure. " +
                        "The old notes board still lives in every mode's Notes rail tab.",
                ),
                HelpEntry(
                    "Threads",
                    "Chats are app-wide and shared across every book and mode. + Add starts " +
                        "a new thread; the backspace beside a thread deletes it after a " +
                        "confirmation.",
                ),
                HelpEntry(
                    "! commands",
                    "Type !character, !location, !object, !lore or !other followed by a " +
                        "brief — for example \"!location a drowned port city on the Marrow\". " +
                        "The AI writes the entry, files it in the Codex (characters also get " +
                        "a roster sheet and their starting gear), and drops the same words " +
                        "into what you are writing. The prose and the entry are one and the " +
                        "same text, so they never drift apart.",
                ),
                HelpEntry(
                    "Codex context",
                    "+ Codex pins entries into the conversation; entries you mention are " +
                        "pulled in automatically, and the chips show what the AI will see. " +
                        "Preview shows the exact prompt before it is sent.",
                ),
            ),
        ),
        HelpSection(
            id = "storyboard",
            title = "Storyboard",
            summary = "Window · Manga · Comic",
            entries = listOf(
                HelpEntry(
                    "Pages",
                    "Page tabs sit above the canvas. Plus adds a page; long-press a tab " +
                        "to rename or delete. Window is the cover-art shelf for every storyboard. " +
                        "Manga reads right-to-left, Comic the other way.",
                ),
                HelpEntry(
                    "Managing library titles",
                    "Long-press a cover in the manga Library for its categories and Remove from " +
                        "library. Removing clears every category and deletes that title's " +
                        "downloaded chapters, after a confirmation.",
                ),
                HelpEntry(
                    "Filtering chapters",
                    "The filter icon above the chapter list opens Language, scanlator group, and " +
                        "a chapter number range. Language lists what that title publishes with a " +
                        "chapter count each, and calls out any language the title advertises but " +
                        "has no chapters for.",
                ),
                HelpEntry(
                    "Manga hub and sources",
                    "Storyboard → Window opens Library, Browse, Downloads, Extensions, and Projects. " +
                        "Browse selects MangaDex, Comix, Atsumaru, MangaFire, MangaDot, or Rawkuma, " +
                        "then loads Popular, Latest, Search, series metadata, and chapter links inside Weaverse. " +
                        "A source that requires a browser challenge reports the block instead of showing a fake empty library.",
                ),
                HelpEntry(
                    "Favorites and sections",
                    "Open a title in Browse and tap one or more Favorite section chips. In Library, " +
                        "switch between Downloads and your named sections. Type a name in New favorite section " +
                        "and tap Add to create another category. Favorites do not require a chapter download.",
                ),
                HelpEntry(
                    "Download and read chapters",
                    "Open a cover to load its chapters, then tap Download. Downloads shows queue progress, Stop, " +
                        "Retry, and Read. Completed chapters open offline from either Downloads or Library. " +
                        "For another public reader page, paste its chapter URL in Browse and preview the ordered images first.",
                ),
                HelpEntry(
                    "Layouts",
                    "Six templates. The chosen one draws numbered empty frames, so a page " +
                        "looks like a comic page before you add anything.",
                ),
                HelpEntry(
                    "Panels are for media",
                    "Media lands in the first free slot at that slot's size. Tap a panel " +
                        "to select, then drag to move; drag the corner grip to resize. " +
                        "Drop one panel on another to stack them.",
                ),
                HelpEntry(
                    "Adjust image",
                    "Pans and zooms the picture inside its frame. Remembered per panel.",
                ),
                HelpEntry(
                    "Text is an overlay",
                    "Add text drops a box you can drag and resize, as a plain caption or " +
                        "a manga speech bubble with a tail. It is not a panel.",
                ),
                HelpEntry(
                    "Page editor",
                    "Page editor is a phone workspace: Move, Text, Paint, Erase, Color, Clean, " +
                        "and Hand along the bottom. Tap Find text, Read, Translate, and Clean art, " +
                        "then Go. Source OCR and translation stay separate; the page shows " +
                        "translation only. Clean rebuilds art under old lettering. Save copy writes " +
                        "a new file and never overwrites the original — use Original / Edited to compare.",
                ),
            ),
        ),
        HelpSection(
            id = "appearance",
            title = "Appearance",
            summary = "Profiles, themes and section colours.",
            entries = listOf(
                HelpEntry(
                    "Profile",
                    "A whole look — palette, lettering and corners together. Classic, " +
                        "Fantasy, Arcade, Synthwave, Chill, Tabletop.",
                ),
                HelpEntry(
                    "Theme",
                    "Classic keeps all four (Light, Sepia, Dark, OLED). The styled " +
                        "profiles carry their own mood, so they offer light or dark only.",
                ),
                HelpEntry(
                    "Section colours",
                    "Layer on top of any profile. Reset section colours undoes them.",
                ),
            ),
        ),
        HelpSection(
            id = "ai",
            title = "Prompts, AI and sync",
            summary = "Generation, models, and moving data between devices.",
            entries = listOf(
                HelpEntry(
                    "Prompts",
                    "Slash opens the AI prompt, backslash opens manual entry, from " +
                        "anywhere. Novel and RPG share one dock design; drag the thin handle " +
                        "along its top to resize it, double-tap the handle to snap back to " +
                        "automatic sizing.",
                ),
                HelpEntry(
                    "The composer controls",
                    "W sets the minimum and maximum word count the AI targets, in a four-digit " +
                        "box. /A is AI generation, \\M files your text without calling a model. " +
                        "Model opens a scrollable list of cached OpenRouter models instead of a " +
                        "typed provider/model box. Generate sends; while streaming, × cancels. " +
                        "The context meter shows roughly how full the model's window is. " +
                        "The maximum is a target: the AI may go slightly over it to finish " +
                        "the final sentence instead of cutting the reply off mid-sentence.",
                ),
                HelpEntry(
                    "Templates and presets",
                    "Expand the dock for a two-column grid below the composer. In Novel this " +
                        "holds prompt templates — tick several at once and they layer in the " +
                        "order picked, numbered 1, 2, 3…, each contributing its own system " +
                        "instructions to the same generation. In RPG the same grid holds turn " +
                        "presets, switched by Actions / Thoughts / Dialogue / Combat / Roleplay chips.",
                ),
                HelpEntry(
                    "The generated draft",
                    "A finished AI draft renders inline in the story, in a panel with a " +
                        "contrasting fill and outline, and it is directly editable there before " +
                        "you choose Insert, Retry, Compare, Copy, or Discard. A line such as " +
                        "[MEDIA type=image query=…] inside a draft is not image generation — it " +
                        "asks the app to search your existing media library for a matching " +
                        "asset, and is dropped silently if nothing matches.",
                ),
                HelpEntry(
                    "Backspace clear with hold-to-undo",
                    "The backspace (⌫) button deletes your draft in one tap. Press and " +
                        "hold it to restore the text you just deleted. It works in the prompt " +
                        "dock, the adventure bar, and the Chatting and Brainstorm composers.",
                ),
                HelpEntry(
                    "Models and keys",
                    "Models picks any OpenRouter text model. Add your key in Settings; " +
                        "without one, everything except generation still works.",
                ),
                HelpEntry(
                    "Sync",
                    "No account, no cloud. Start the desktop app, read the password off " +
                        "the web hub, then Settings then Open web sync on the phone. " +
                        "Leave Auto-sync on.",
                ),
                HelpEntry(
                    "Import and export",
                    "In the top bar. Novelcrafter ZIPs are supported — codex folders " +
                        "become Characters, Locations, Objects and Lore, and characters " +
                        "also become roleplay cards.",
                ),
            ),
        ),
    )

    /** Case-insensitive search across section titles, headings and bodies. */
    fun search(query: String): List<HelpSection> {
        val q = query.trim()
        if (q.isBlank()) return sections
        return sections.mapNotNull { section ->
            val hits = section.entries.filter {
                it.heading.contains(q, true) || it.body.contains(q, true)
            }
            when {
                section.title.contains(q, true) -> section
                hits.isNotEmpty() -> section.copy(entries = hits)
                else -> null
            }
        }
    }
}
