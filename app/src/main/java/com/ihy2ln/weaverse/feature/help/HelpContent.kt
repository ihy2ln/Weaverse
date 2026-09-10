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
                        "action. Just above the prompt are Actions (observe, move, use, help), " +
                        "Thoughts (recall, study, consider), and Other RPG actions (speak, ask, " +
                        "persuade, search, or prepare for the selected mode). These are shortcuts, " +
                        "not a replacement for freeform play.",
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
                    "The left rail lists every novel and campaign as a server icon; the " +
                        "house button is Home, where direct messages live. Opening a server " +
                        "for the first time auto-creates #general, #lore, and #brainstorm " +
                        "channels, plus one live room per tied character.",
                ),
                HelpEntry(
                    "Channels and character rooms",
                    "Text channels chat with an AI narrator that knows the work's title, " +
                        "genre, point of view, tense, and style guide. Character rooms talk " +
                        "to that character in person. The + buttons add custom channels or " +
                        "character rooms; long-press a room to delete it.",
                ),
                HelpEntry(
                    "@mentions",
                    "Type @CharacterName in any room (full name or unambiguous first name) " +
                        "and that character joins the AI's reply, voiced from their card.",
                ),
                HelpEntry(
                    "Direct messages",
                    "Friends (the sub-mode tab) lists everyone you can talk to; tapping a " +
                        "friend opens their DM under Home. The composer there has the full " +
                        "word range, AI/manual toggle, retry, cancel, and context meter.",
                ),
                HelpEntry(
                    "New people",
                    "Meet someone writes a new character. One arrives each day when you " +
                        "have an OpenRouter key; offline it is skipped and retried later. " +
                        "Turn it off in Settings.",
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
                        "anywhere. The PROMPT dock collapses to one line with its arrow.",
                ),
                HelpEntry(
                    "The composer controls",
                    "W sets the minimum and maximum word count the AI targets. /A is AI " +
                        "generation, \\M files your text without calling a model. Tap ✓ to " +
                        "send; hold ✓ for retry and continue; while streaming, × cancels. " +
                        "The context meter shows roughly how full the model's window is. " +
                        "The maximum is a target: the AI may go slightly over it to finish " +
                        "the final sentence instead of cutting the reply off mid-sentence.",
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
