@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.source.model

import android.net.Uri
import androidx.compose.runtime.Stable
import eu.kanade.tachiyomi.network.ProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonObject
import java.io.Serializable as JavaSerializable

sealed class Filter<T>(val name: String, var state: T) {
    open class Header(name: String) : Filter<Any>(name, 0)
    open class Separator(name: String = "") : Filter<Any>(name, 0)
    abstract class Select<V>(name: String, val values: Array<V>, state: Int = 0) : Filter<Int>(name, state)
    abstract class Text(name: String, state: String = "") : Filter<String>(name, state)
    abstract class CheckBox(name: String, state: Boolean = false) : Filter<Boolean>(name, state)
    abstract class TriState(name: String, state: Int = STATE_IGNORE) : Filter<Int>(name, state) {
        fun isIgnored() = state == STATE_IGNORE
        fun isIncluded() = state == STATE_INCLUDE
        fun isExcluded() = state == STATE_EXCLUDE
        companion object { const val STATE_IGNORE = 0; const val STATE_INCLUDE = 1; const val STATE_EXCLUDE = 2 }
    }
    abstract class Group<V>(name: String, state: List<V>) : Filter<List<V>>(name, state)
    abstract class Sort(name: String, val values: Array<String>, state: Selection? = null) : Filter<Sort.Selection?>(name, state) {
        data class Selection(val index: Int, val ascending: Boolean)
    }
}

@Stable
data class FilterList(val list: List<Filter<*>>) : List<Filter<*>> by list {
    constructor(vararg fs: Filter<*>) : this(if (fs.isNotEmpty()) fs.asList() else emptyList())
    override fun equals(other: Any?) = false
    override fun hashCode() = list.hashCode()
}

class MangasPage(val mangas: List<SManga>, val hasNextPage: Boolean) {
    @Deprecated("MangasPage is now a regular class") operator fun component1() = mangas
    @Deprecated("MangasPage is now a regular class") operator fun component2() = hasNextPage
    @Deprecated("MangasPage is now a regular class") fun copy(
        mangas: List<SManga> = this.mangas,
        hasNextPage: Boolean = this.hasNextPage,
    ) = MangasPage(mangas, hasNextPage)
}

interface SChapter : JavaSerializable {
    var url: String
    var name: String
    var chapter_number: Float
    var scanlator: String?
    var date_upload: Long
    var memo: JsonObject
    fun copyFrom(other: SChapter) {
        url = other.url; name = other.name; chapter_number = other.chapter_number
        scanlator = other.scanlator; date_upload = other.date_upload; memo = other.memo
    }
    companion object { fun create(): SChapter = SChapterImpl() }
}

class SChapterImpl : SChapter {
    override lateinit var url: String
    override lateinit var name: String
    override var chapter_number = -1f
    override var scanlator: String? = null
    override var date_upload = 0L
    override var memo = JsonObject(emptyMap())
}

interface SManga : JavaSerializable {
    var url: String
    var title: String
    var thumbnail_url: String?
    var artist: String?
    var author: String?
    var status: Int
    var description: String?
    var genre: String?
    var update_strategy: UpdateStrategy
    var initialized: Boolean
    var memo: JsonObject
    fun getGenres(): List<String>? = genre?.takeIf { it.isNotBlank() }?.split(", ")?.map(String::trim)?.filter(String::isNotBlank)?.distinct()
    fun copy(): SManga = create().also { out ->
        out.url=url; out.title=title; out.thumbnail_url=thumbnail_url; out.artist=artist; out.author=author
        out.status=status; out.description=description; out.genre=genre; out.update_strategy=update_strategy
        out.initialized=initialized; out.memo=memo
    }
    companion object {
        const val UNKNOWN=0; const val ONGOING=1; const val COMPLETED=2; const val LICENSED=3
        const val PUBLISHING_FINISHED=4; const val CANCELLED=5; const val ON_HIATUS=6
        fun create(): SManga = SMangaImpl()
    }
}

class SMangaImpl : SManga {
    override lateinit var url: String
    override lateinit var title: String
    override var thumbnail_url: String? = null
    override var artist: String? = null
    override var author: String? = null
    override var status = 0
    override var description: String? = null
    override var genre: String? = null
    override var update_strategy = UpdateStrategy.ALWAYS_UPDATE
    override var initialized = false
    override var memo = JsonObject(emptyMap())
}

class SMangaUpdate(val manga: SManga, val chapters: List<SChapter>)
enum class UpdateStrategy { ALWAYS_UPDATE, ONLY_FETCH_ONCE }

@Serializable
open class Page(
    val index: Int,
    val url: String = "",
    var imageUrl: String? = null,
    @Transient var uri: Uri? = null,
) : ProgressListener {
    val number get() = index + 1
    @Transient private val _statusFlow = MutableStateFlow<State>(State.Queue)
    @Transient val statusFlow = _statusFlow.asStateFlow()
    var status: State get() = _statusFlow.value; set(value) { _statusFlow.value = value }
    @Transient private val _progressFlow = MutableStateFlow(0)
    @Transient val progressFlow = _progressFlow.asStateFlow()
    var progress: Int get() = _progressFlow.value; set(value) { _progressFlow.value = value }
    override fun update(bytesRead: Long, contentLength: Long, done: Boolean) {
        progress = if (contentLength > 0) (100 * bytesRead / contentLength).toInt() else -1
    }
    sealed interface State {
        data object Queue : State; data object LoadPage : State; data object DownloadImage : State; data object Ready : State
        data class Error(val error: Throwable) : State
    }
}
