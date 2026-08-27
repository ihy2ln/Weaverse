package com.ihy2ln.weaverse.feature.library

/** Hub destinations on the home screen — one card per workspace. */
enum class HomeWorkspace(
    val title: String,
    val blurb: String,
) {
    Novel("Novel", "Plan, write and review a book"),
    Rpg("RPG", "Run a campaign: adventures, party, lore"),
    Chatting("Chatting", "Message the cast like a messenger app"),
    Storyboard("Storyboard", "Build comic and manga pages"),
    Notes("Notes", "One shared board across every mode"),
}

data class HomeRecentWork(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val coverPath: String? = null,
)

enum class LibraryPane { Home, Bookshelf }

enum class ItemAdminAction {
    Export,
    Copy,
    AddCover,
    Delete,
    SelectToRemove,
    Rename,
    Pin,
}
