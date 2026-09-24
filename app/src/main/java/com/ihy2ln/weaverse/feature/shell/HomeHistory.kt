package com.ihy2ln.weaverse.feature.shell

import androidx.room.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.core.media.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Entity(tableName = "home_access", primaryKeys = ["mode", "kind", "contentId"])
data class HomeAccess(val mode: String, val kind: String, val contentId: String, val accessedAt: Long, val target: String = "")

data class HomeItem(
    val mode: String, val kind: String, val contentId: String, val accessedAt: Long,
    val target: String, val title: String, val mediaId: String?, val remoteCover: String?,
    val bookId: String?, val sessionId: String?,
) {
    val key get() = "$mode:$kind:$contentId"
    val badge get() = when (kind) { "book" -> AppMode.entries.firstOrNull { it.name == mode }?.label ?: mode; "manga" -> "Manga"; "note" -> "Note"; "thread" -> "Brainstorm"; else -> if (mode == "Games") "Game" else if (mode == "Storyboard") "Storyboard" else "Conversation" }
}

@Dao
interface HomeAccessDao {
    @Upsert suspend fun record(access: HomeAccess)
    @Query("SELECT * FROM home_access WHERE mode = :mode AND kind = :kind AND contentId = :id")
    suspend fun get(mode: String, kind: String, id: String): HomeAccess?
    @Query("DELETE FROM home_access WHERE mode = :mode AND kind = :kind AND contentId = :id")
    suspend fun remove(mode: String, kind: String, id: String)
    @Query("DELETE FROM home_access WHERE mode = :mode") suspend fun clear(mode: String)
    @Query("""
        SELECT h.*, COALESCE(b.title, c.title, n.title, t.name, m.title) AS title,
        COALESCE(b.coverMediaId, c.backgroundMediaId, a.avatarMediaId, (SELECT backgroundMediaId FROM rp_chats WHERE bookId = b.id ORDER BY updatedAt DESC LIMIT 1)) AS mediaId, m.coverUrl AS remoteCover,
        COALESCE(b.id, c.bookId) AS bookId,
        CASE WHEN h.kind = 'chat' THEN c.id ELSE COALESCE((SELECT id FROM rp_chats WHERE bookId = b.id AND id = h.target), (SELECT id FROM rp_chats WHERE bookId = b.id ORDER BY updatedAt DESC LIMIT 1)) END AS sessionId
        FROM home_access h
        LEFT JOIN books b ON h.kind = 'book' AND b.id = h.contentId
        LEFT JOIN rp_chats c ON h.kind = 'chat' AND c.id = h.contentId
        LEFT JOIN rp_characters a ON a.id = c.characterId
        LEFT JOIN snippets n ON h.kind = 'note' AND n.id = h.contentId
        LEFT JOIN chat_threads t ON h.kind = 'thread' AND t.id = h.contentId
        LEFT JOIN manga_series m ON h.kind = 'manga' AND m.id = h.contentId
        WHERE COALESCE(b.title, c.title, n.title, t.name, m.title) IS NOT NULL
        ORDER BY h.accessedAt DESC, h.contentId ASC
    """)
    fun observeItems(): Flow<List<HomeItem>>
}

fun recentShelves(items: List<HomeItem>): Map<String, List<HomeItem>> = items
    .sortedWith(compareByDescending<HomeItem> { it.accessedAt }.thenBy { it.key })
    .distinctBy { it.key }.groupBy { it.mode }.mapValues { it.value.take(10) }

@Singleton
class HomeHistory @Inject constructor(private val db: WeaverseDatabase) {
    val items get() = db.homeAccessDao().observeItems()
    suspend fun record(mode: String, kind: String, id: String, target: String = "") {
        if (id.isNotBlank()) {
            db.withTransaction {
                val previous = db.homeAccessDao().get(mode, kind, id)
                db.homeAccessDao().record(HomeAccess(mode, kind, id, System.currentTimeMillis(), target.ifBlank { previous?.target.orEmpty() }))
            }
        }
    }
    suspend fun remove(item: HomeItem) = db.homeAccessDao().remove(item.mode, item.kind, item.contentId)
    suspend fun clear(mode: String) = db.homeAccessDao().clear(mode)
}

@HiltViewModel
class HomeViewModel @Inject constructor(private val history: HomeHistory, media: MediaRepository) : ViewModel() {
    val shelves = history.items.map(::recentShelves).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    val art = media.observeAll().map { list -> list.associate { it.id to media.resolveFile(it).absolutePath } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    fun remove(item: HomeItem) { viewModelScope.launch { history.remove(item) } }
    fun clear(mode: String) { viewModelScope.launch { history.clear(mode) } }
}
