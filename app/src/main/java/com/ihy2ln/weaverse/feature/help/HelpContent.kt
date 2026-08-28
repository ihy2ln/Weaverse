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
                HelpEntry("Write", "The scene editor, with media blocks and codex highlighting."),
                HelpEntry(
                    "Read",
                    "A distraction-free reader with saved position, contents, bookmarks, " +
                        "Paper/Sepia/Night themes, typography controls, and read-aloud.",
                ),
                HelpEntry("Chat", "A workshop conversation about the book."),
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
                    "Dungeon Master mode",
                    "You control scene framing, the world, NPCs, and rulings. The AI plays the " +
                        "selected character party and responds to your DM prompts with its " +
                        "decisions, actions, dialogue, and rules-aware reactions.",
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
            summary = "Chats · Contacts",
            entries = listOf(
                HelpEntry(
                    "Contacts",
                    "Everyone you can talk to, from the character codex. Tapping someone " +
                        "opens their chat, creating it on first contact.",
                ),
                HelpEntry(
                    "Chats",
                    "Search, All / Unread / Groups filters, and unread badges. Badges are " +
                        "real — a chat remembers when you last opened it.",
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
