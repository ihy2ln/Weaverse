package com.ihy2ln.weaverse.feature.help

/** In-app Manual / Tutorial / What's New copy for v0.5.28. */
object HelpContent {
    data class Section(
        val title: String,
        val body: String,
    )

    enum class Tab(val label: String) {
        Tutorial("Tutorial"),
        Manual("Manual"),
        WhatsNew("What's new"),
    }

    val tutorial: List<Section> = listOf(
        Section(
            title = "1. Pick a book",
            body = """
                Tap the Library icon (book) in the top chrome.
                Open an existing novel or create one. Codex entries and prompts
                stay shared across books in the same library.
            """.trimIndent(),
        ),
        Section(
            title = "2. Write a scene",
            body = """
                Novel → Write. Type in the scene editor.
                Long-press text (~650 ms) for Format (bold, color, font).
                Long-press pictures for the media menu (shorter press).
                Tap a highlighted Codex mention to peek its entry.
            """.trimIndent(),
        ),
        Section(
            title = "3. Prompt the AI",
            body = """
                Press / for AI prompting, or \ for a manual insert.
                In the prompt window you can:
                • Preview the resolved prompt (includes filled in)
                • Pick a model for this action (remembered next time)
                • Watch the Context meter for Codex / Scene / User tokens
            """.trimIndent(),
        ),
        Section(
            title = "4. Scene beat & Focus",
            body = """
                Use Write ▾ → Scene beat, or add a beat card in the editor.
                Generate, Accept, or Retry from the blue beat box.
                Tap Focus next to Aa / Prompting / Media to hide chrome
                and write full-bleed. Exit Focus when you need tools again.
            """.trimIndent(),
        ),
        Section(
            title = "5. Snapshots & Review",
            body = """
                Snapshots: save / restore scene versions from Write.
                Review: Write ▾ → Review scene or Review chapter for
                LLM continuity notes (voice, pacing, Codex consistency).
            """.trimIndent(),
        ),
        Section(
            title = "6. Roleplay & Notes",
            body = """
                Switch Workspace to Roleplay for Messenger / DM / manga chats.
                Notes is a shared board across every mode.
                Swipe replies in Roleplay remember their own model choice.
            """.trimIndent(),
        ),
    )

    val manual: List<Section> = listOf(
        Section(
            title = "Chrome & navigation",
            body = """
                Library — books and series.
                Settings — appearance, OpenRouter key, models, sync, backup.
                Help — this Manual / Tutorial / What's new.
                Search — find scenes, Codex, chats.
                Workspace — Novel · Roleplay · Notes.
                Mode — Plan / Write / Chat (Novel), or Roleplay destinations.
                Focus — Story canvas vs Pictures gallery (shell), or Write Focus mode.
                Tools — Codex, Prompts, Notes, Snippets, Chats, Pictures.
            """.trimIndent(),
        ),
        Section(
            title = "Writing editor",
            body = """
                Paragraphs, scene-beat cards, and media blocks.
                Format menu: reopen by pressing the same highlighted selection
                after you dismiss it (no stuck loop).
                Color: HSV wheel + editable #RRGGBB hex field.
                Media: stack, reorder via menu; haptics on long-press.
            """.trimIndent(),
        ),
        Section(
            title = "Codex",
            body = """
                World bible — characters, places, lore.
                Mentions in prose become tappable links.
                Always-include and track-mentions control AI context.
                Entry detail is labeled Codex (not generic “Entry”).
            """.trimIndent(),
        ),
        Section(
            title = "AI & models",
            body = """
                OpenRouter powers generation (set API key in Settings).
                Default model applies when an action has no override.
                Per-action memory: Scene beat, Shorten, Extend, Replace,
                Summarize, Review, Roleplay swipe, Workshop, Prompt AI.
                Context packing drops overflow Codex entries from the
                actual prompt text when the budget is tight.
            """.trimIndent(),
        ),
        Section(
            title = "Snapshots",
            body = """
                Scene snapshots store title + document JSON (Room v6).
                Save before risky AI accepts; restore or delete anytime.
                Pre-accept snapshots may be written automatically.
            """.trimIndent(),
        ),
        Section(
            title = "Sync & export",
            body = """
                Settings → Sync for peer Wi-Fi / tunnel sync with desktop/web.
                Import / Export for Novelcrafter-style ZIPs and backups.
                See the project wiki for desktop hub steps.
            """.trimIndent(),
        ),
    )

    val whatsNew: List<Section> = listOf(
        Section(
            title = "v0.5.28",
            body = """
                • Fixed color picker hex (no more stuck #000000); editable hex
                • Format menu: reopen on highlighted-text press; keep selection
                • Review moved under Write ▾ with LLM scene/chapter review
                • Search magnifying glass on main chrome
                • Focus mode next to Aa / Prompting / Media
                • Scene snapshots + database migration 5→6
                • Codex peek sheet from mention taps; Codex labeling
                • Prompt preview, per-action model memory, context meter
                • Longer text long-press vs pictures + haptics
                • In-app Manual & Tutorial (this screen)
            """.trimIndent(),
        ),
    )

    fun sectionsFor(tab: Tab): List<Section> = when (tab) {
        Tab.Tutorial -> tutorial
        Tab.Manual -> manual
        Tab.WhatsNew -> whatsNew
    }
}
