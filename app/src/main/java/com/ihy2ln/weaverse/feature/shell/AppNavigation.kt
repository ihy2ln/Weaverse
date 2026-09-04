package com.ihy2ln.weaverse.feature.shell

enum class AppMode {
    Novel,
    Roleplay,
    Notes,
}

enum class NovelDestination(val label: String) {
    Plan("Plan"),
    Write("Write"),
    Chat("Chat"),
    Review("Review"),
}

/** How Plan's Write button opens the editor — not a workspace mode. */
enum class WriteJumpKind(val label: String) {
    Scene("Scene"),
    SceneBeat("Scene beat"),
    Chapter("Chapter"),
    ;

    companion object {
        /** Plan Write ▾ — Scene is opened from Plan/Write/Chat/Review, not this menu. */
        val planMenu: List<WriteJumpKind> = listOf(SceneBeat, Chapter)
    }
}

enum class RoleplayDestination(val label: String) {
    Rpg("RPG"),
    Chats("Chats"),
    Characters("Characters"),
    Personas("Personas"),
    Codex("Codex"),
    Presets("Presets"),
}

enum class NotesDestination(val label: String) {
    Board("Board"),
}

enum class RailTab(val label: String) {
    Manuscript("Manuscript"),
    Codex("Codex"),
    Prompts("Prompts"),
    Notes("Notes"),
    Pictures("Pictures"),
    Snippets("Snippets"),
    Chats("Chats"),
}

/** NovelAI-style focus: writing canvas vs picture gallery. Does not remove modes. */
enum class WorkspaceFocus(val label: String) {
    Story("Story"),
    Pictures("Pictures"),
}

fun workspaceChromeTools(): List<RailTab> = listOf(
    RailTab.Codex,
    RailTab.Prompts,
    RailTab.Notes,
    RailTab.Snippets,
    RailTab.Chats,
    RailTab.Pictures,
)

fun railTabsFor(mode: AppMode): List<RailTab> = when (mode) {
    AppMode.Novel -> listOf(
        RailTab.Codex, RailTab.Prompts, RailTab.Manuscript, RailTab.Notes, RailTab.Pictures, RailTab.Snippets, RailTab.Chats,
    )
    AppMode.Notes -> listOf(RailTab.Codex, RailTab.Prompts, RailTab.Notes, RailTab.Pictures)
    AppMode.Roleplay -> listOf(RailTab.Codex, RailTab.Prompts, RailTab.Notes, RailTab.Pictures, RailTab.Snippets, RailTab.Chats)
}

fun defaultRailTab(mode: AppMode): RailTab = when (mode) {
    AppMode.Novel -> RailTab.Manuscript
    AppMode.Notes -> RailTab.Notes
    AppMode.Roleplay -> RailTab.Codex
}
