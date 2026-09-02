package com.ihy2ln.weaverse.feature.shell

/**
 * Top-level workspaces. `Roleplay` is labelled "RPG" in the UI; the constant is
 * kept because it is persisted in shell state and in the launchMode preference.
 */
enum class AppMode(val label: String) {
    Novel("Novel"),
    Roleplay("RPG"),
    Games("Games"),
    Chatting("Chatting"),
    Storyboard("Storyboard"),
    Notes("Brainstorm/Notes"),
}

enum class NovelDestination(val label: String) {
    Bookshelf("Bookshelf"),
    Plan("Plan"),
    Write("Write"),
    Read("Read"),
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

/**
 * RPG workspace sections, mirroring how Novel is organised:
 * Play is the adventure itself, Party is who is in it, Journal is what you have
 * gathered along the way. Friends/Chats stay for the messenger side.
 *
 * Enum constant names are persisted in shell state, so they are kept stable —
 * only the labels are user-facing.
 */
enum class RoleplayDestination(val label: String) {
    Campaign("Campaign"),
    Inventory("Inventory"),
    Chats("Adventure"),
    Town("Town"),
    Characters("Roster"),
    Codex("Lore"),
    Presets("Presets"),
}

/** The Games workspace — text-driven card games in their own mode. */
enum class GamesDestination(val label: String) {
    TextGames("Text Games"),
}

/** The messenger workspace: the Discord server view, plus the friends list. */
enum class ChattingDestination(val label: String) {
    Chats("Chats"),
    Friends("Friends"),
}

/** The comic workspace — the same page canvas, read right-to-left or left-to-right. */
enum class StoryboardDestination(val label: String) {
    Window("Window"),
    Manga("Manga"),
    Comic("Comic"),
}

enum class NotesDestination(val label: String) {
    /** NovelCrafter-style AI brainstorm chat — the mode's home. */
    Chat("Brainstorm"),
    /** The classic notes board. */
    Board("Notes"),
}

/**
 * Saved shell state can name a destination that no longer exists (Personas and
 * Presets were folded into Roster and Settings), so every lookup falls back to
 * the workspace's first section rather than throwing.
 */
fun roleplayDestinationOf(id: String?): RoleplayDestination =
    RoleplayDestination.entries.firstOrNull { it.name == id } ?: RoleplayDestination.Chats

fun gamesDestinationOf(id: String?): GamesDestination =
    GamesDestination.entries.firstOrNull { it.name == id } ?: GamesDestination.TextGames

fun chattingDestinationOf(id: String?): ChattingDestination =
    ChattingDestination.entries.firstOrNull { it.name == id } ?: ChattingDestination.Chats

fun storyboardDestinationOf(id: String?): StoryboardDestination =
    StoryboardDestination.entries.firstOrNull { it.name == id } ?: StoryboardDestination.Window

fun novelDestinationOf(id: String?): NovelDestination =
    NovelDestination.entries.firstOrNull { it.name == id } ?: NovelDestination.Bookshelf

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

fun <T> applySavedOrder(items: List<T>, saved: String, id: (T) -> String): List<T> {
    if (saved.isBlank()) return items
    val ranks = saved.split(',').mapIndexed { index, value -> value to index }.toMap()
    return items.withIndex().sortedWith(
        compareBy<IndexedValue<T>> { ranks[id(it.value)] ?: Int.MAX_VALUE }
            .thenBy { it.index },
    ).map { it.value }
}

/**
 * The Extra row: app-wide tools not tied to one workspace. Chats, Snippets
 * and Notes were removed — their purpose lives in the Brainstorm/Notes mode
 * (and Novel's own Chat tab).
 */
fun workspaceChromeTools(): List<RailTab> = listOf(
    RailTab.Codex,
    RailTab.Prompts,
    RailTab.Pictures,
)

fun railTabsFor(mode: AppMode): List<RailTab> = when (mode) {
    AppMode.Novel -> listOf(
        RailTab.Codex, RailTab.Prompts, RailTab.Manuscript, RailTab.Notes, RailTab.Pictures, RailTab.Snippets, RailTab.Chats,
    )
    AppMode.Notes -> listOf(RailTab.Codex, RailTab.Prompts, RailTab.Notes, RailTab.Pictures)
    AppMode.Roleplay -> listOf(RailTab.Codex, RailTab.Prompts, RailTab.Notes, RailTab.Pictures, RailTab.Snippets, RailTab.Chats)
    AppMode.Chatting -> listOf(RailTab.Codex, RailTab.Prompts, RailTab.Notes, RailTab.Pictures)
    AppMode.Games -> listOf(RailTab.Codex, RailTab.Prompts, RailTab.Notes, RailTab.Pictures)
    // Pictures first: a comic page is built out of the media library.
    AppMode.Storyboard -> listOf(RailTab.Pictures, RailTab.Codex, RailTab.Prompts, RailTab.Notes, RailTab.Snippets)
}

fun defaultRailTab(mode: AppMode): RailTab = when (mode) {
    AppMode.Novel -> RailTab.Manuscript
    AppMode.Notes -> RailTab.Notes
    AppMode.Roleplay -> RailTab.Codex
    AppMode.Chatting -> RailTab.Codex
    AppMode.Games -> RailTab.Codex
    AppMode.Storyboard -> RailTab.Pictures
}
