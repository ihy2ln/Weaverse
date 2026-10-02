package com.ihy2ln.weaverse.feature.chatting

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** workType of a server the writer made just for chatting (not a novel or campaign). */
const val WORK_TYPE_SERVER = "server"

/** A thread under a text channel, or a post in a forum channel. */
const val ROOM_KIND_THREAD = "thread"

/** A forum channel: every post is its own thread. */
const val ROOM_KIND_FORUM = "forum"

/** A voice channel: the cast talk out loud. */
const val ROOM_KIND_VOICE = "voice"

/** The server's codex channel: the canon its AI follows, browsable and editable. */
const val ROOM_KIND_CODEX = "codex"

/** A Discord role: a name, a color for names in chat, and whether it gets its own member-list group. */
@Serializable
data class ServerRole(
    val id: String,
    val name: String,
    val colorHex: String,
    val hoist: Boolean = true,
)

/** JSON helpers for [com.ihy2ln.weaverse.data.db.entities.ChatServerEntity]'s columns. */
object ServerJson {
    private val json = Json { ignoreUnknownKeys = true }
    private val ids = ListSerializer(String.serializer())
    private val roles = ListSerializer(ServerRole.serializer())
    private val memberRoles = MapSerializer(String.serializer(), ListSerializer(String.serializer()))

    fun ids(text: String): List<String> = runCatching { json.decodeFromString(ids, text) }.getOrDefault(emptyList())
    fun ids(list: List<String>): String = json.encodeToString(ids, list.distinct())
    fun roles(text: String): List<ServerRole> = runCatching { json.decodeFromString(roles, text) }.getOrDefault(emptyList())
    fun roles(list: List<ServerRole>): String = json.encodeToString(roles, list)
    fun memberRoles(text: String): Map<String, List<String>> =
        runCatching { json.decodeFromString(memberRoles, text) }.getOrDefault(emptyMap())
    fun memberRoles(map: Map<String, List<String>>): String = json.encodeToString(memberRoles, map.filterValues { it.isNotEmpty() })
}

/** Role colors offered when making a role, the same set Discord shows. */
val ROLE_COLORS = listOf(
    "#1ABC9C", "#2ECC71", "#3498DB", "#9B59B6", "#E91E63", "#F1C40F", "#E67E22", "#E74C3C",
    "#95A5A6", "#607D8B", "#11806A", "#1F8B4C", "#206694", "#71368A", "#AD1457", "#C27C0E",
)

/** Whether a message pings every seated member, like Discord's @everyone and @here. */
fun pingsEveryone(text: String): Boolean = Regex("""(^|\s)@(everyone|here)\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)
