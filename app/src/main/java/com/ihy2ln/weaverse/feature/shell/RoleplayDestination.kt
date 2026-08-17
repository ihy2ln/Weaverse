package com.ihy2ln.weaverse.feature.shell

import kotlinx.serialization.Serializable

/** Roleplay mode's primary destinations (spec §5, renamed per Revision 02 §2's ground rule that
 * this app has only one shared codex entity): Chats · Characters · Personas · Codex · Presets. */
sealed interface RoleplayDestination {
    /** [chatId] lets the rail's Sessions tab (Revision 02 §1.4/§2's rail nav contract) open a
     * specific chat rather than whatever `RpChatsViewModel`'s own in-memory selection defaults
     * to — same reason and same pattern as [NovelDestination.Write]'s `sceneId`: the rail lives
     * outside the NavHost, so it gets a different-scoped ViewModel instance than the actual
     * Chats screen and can't just poke that screen's selection state directly. */
    @Serializable
    data class Chats(val chatId: String? = null) : RoleplayDestination

    @Serializable
    data object Characters : RoleplayDestination

    @Serializable
    data object Personas : RoleplayDestination

    @Serializable
    data object Codex : RoleplayDestination

    @Serializable
    data object Presets : RoleplayDestination
}
