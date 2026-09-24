package com.ihy2ln.weaverse.feature.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.*
import com.ihy2ln.weaverse.core.media.MediaRepository
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.BookEntity
import com.ihy2ln.weaverse.data.repo.BookRepository
import com.ihy2ln.weaverse.data.settings.ReaderSavedState
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import com.ihy2ln.weaverse.feature.shell.HomeHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Browsing metadata is deliberately separate from manuscript documents. */
@Entity(tableName = "book_browsing")
data class BookBrowsing(
    @PrimaryKey val bookId: String,
    val synopsis: String = "",
    val backdropMediaId: String? = null,
    val listedAt: Long = 0,
    val readAt: Long = 0,
    val writeAt: Long = 0,
    val writeSceneId: String = "",
)

@Dao
abstract class BookBrowsingDao {
    @Query("SELECT * FROM book_browsing") abstract fun observeAll(): Flow<List<BookBrowsing>>
    @Query("SELECT * FROM book_browsing WHERE bookId = :id") abstract suspend fun get(id: String): BookBrowsing?
    @Insert(onConflict = OnConflictStrategy.IGNORE) abstract suspend fun ensure(value: BookBrowsing)
    @Query("UPDATE book_browsing SET listedAt = CASE WHEN listedAt = 0 THEN :now ELSE 0 END WHERE bookId = :id")
    protected abstract suspend fun toggleListRow(id: String, now: Long)
    @Query("UPDATE book_browsing SET synopsis = :text WHERE bookId = :id")
    protected abstract suspend fun synopsisRow(id: String, text: String)
    @Query("UPDATE book_browsing SET backdropMediaId = :mediaId WHERE bookId = :id")
    protected abstract suspend fun backdropRow(id: String, mediaId: String?)
    @Query("UPDATE book_browsing SET readAt = :now WHERE bookId = :id")
    protected abstract suspend fun readRow(id: String, now: Long)
    @Query("UPDATE book_browsing SET writeAt = :now, writeSceneId = :sceneId WHERE bookId = :id")
    protected abstract suspend fun writeRow(id: String, sceneId: String, now: Long)
    @Query("DELETE FROM book_browsing WHERE bookId = :id") abstract suspend fun delete(id: String)
    @Transaction open suspend fun toggleList(id: String, now: Long) { ensure(BookBrowsing(id)); toggleListRow(id, now) }
    @Transaction open suspend fun synopsis(id: String, text: String) { ensure(BookBrowsing(id)); synopsisRow(id, text) }
    @Transaction open suspend fun backdrop(id: String, mediaId: String?) { ensure(BookBrowsing(id)); backdropRow(id, mediaId) }
    @Transaction open suspend fun read(id: String, now: Long) { ensure(BookBrowsing(id)); readRow(id, now) }
    @Transaction open suspend fun write(id: String, sceneId: String, now: Long) { ensure(BookBrowsing(id)); writeRow(id, sceneId, now) }
}

data class BrowseBook(
    val book: BookEntity,
    val browsing: BookBrowsing = BookBrowsing(book.id),
    val cover: String? = null,
    val backdrop: String? = null,
    val series: String = "",
    val accessedAt: Long = 0,
    val reader: ReaderSavedState = ReaderSavedState(),
) {
    val id get() = book.id
    val hasRead get() = browsing.readAt > 0 || reader.lastSceneId.isNotBlank()
    val hasWritten get() = browsing.writeAt > 0
}

fun featuredBook(books: List<BrowseBook>): BrowseBook? = books
    .filter { it.accessedAt > 0 }.maxWithOrNull(compareBy<BrowseBook> { it.accessedAt }.thenBy { it.id })
    ?: books.maxWithOrNull(compareBy<BrowseBook> { it.book.createdAt }.thenBy { it.id })

fun booksForShelf(books: List<BrowseBook>, shelf: String, query: String = "", genre: String = ""): List<BrowseBook> {
    val selected = when {
        shelf == "reading" -> books.filter { it.hasRead }.sortedByDescending { it.browsing.readAt }
        shelf == "writing" -> books.filter { it.hasWritten }.sortedByDescending { it.browsing.writeAt }
        shelf == "list" -> books.filter { it.browsing.listedAt > 0 }.sortedByDescending { it.browsing.listedAt }
        shelf == "recent" -> books.filter { it.accessedAt > 0 }.sortedByDescending { it.accessedAt }
        shelf.startsWith("genre:") -> books.filter { it.book.genre.equals(shelf.removePrefix("genre:"), true) }.sortedByDescending { it.book.createdAt }
        else -> books.sortedByDescending { it.book.createdAt }
    }
    return selected.filter {
        (genre.isBlank() || it.book.genre.equals(genre, true) || genre == "Uncategorized" && it.book.genre.isBlank()) &&
            (query.isBlank() || listOf(it.book.title, it.book.genre, it.series, it.browsing.synopsis).any { field -> field.contains(query.trim(), true) })
    }
}

data class BookAudio(val title: String, val path: String, val scene: String)
data class BookDetailsInfo(val position: String = "Not started", val audio: List<BookAudio> = emptyList())

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class BookBrowserViewModel @Inject constructor(
    private val db: WeaverseDatabase,
    private val settings: SettingsRepository,
    private val media: MediaRepository,
    private val repository: BookRepository,
    private val history: HomeHistory,
) : ViewModel() {
    val status = MutableStateFlow("")
    private val rows = combine(db.bookDao().observeAll(), db.bookBrowsingDao().observeAll(), media.observeAll(),
        db.seriesDao().observeAll(), history.items) { books, metadata, art, series, recent ->
        val assets = art.associateBy { it.id }
        val byId = metadata.associateBy { it.bookId }
        books.filter { it.workType == "novel" }.map { book ->
            val meta = byId[book.id] ?: BookBrowsing(book.id)
            fun path(id: String?) = assets[id]?.let(media::resolveFile)?.takeIf { it.exists() }?.absolutePath
            BrowseBook(book, meta, path(book.coverMediaId), path(meta.backdropMediaId),
                series.firstOrNull { it.id == book.seriesId }?.title.orEmpty(),
                recent.firstOrNull { it.mode == "Novel" && it.contentId == book.id }?.accessedAt ?: 0)
        }
    }
    val books = rows.flatMapLatest { books ->
        if (books.isEmpty()) flowOf(emptyList()) else combine(books.map { book -> settings.readerState(book.id).map { book.copy(reader = it) } }) { it.toList() }
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun details(id: String) = combine(db.manuscriptDao().observeBookScenes(id), db.novelMediaDao().observe(id),
        media.observeAll(), settings.readerState(id)) { scenes, links, assets, saved ->
        val index = scenes.indexOfFirst { it.id == saved.lastSceneId }
        val position = if (index < 0) "Not started" else "${scenes[index].title} · Scene ${index + 1} of ${scenes.size} · " + if (saved.paragraphIndex == 0) "Beginning" else "Block ${saved.paragraphIndex}"
        val inline = scenes.flatMap { scene -> com.ihy2ln.weaverse.feature.novel.inlineNovelMediaIds(com.ihy2ln.weaverse.core.text.documentFromJson(scene.docJson).blocks).map { it to scene.title } }
        BookDetailsInfo(position, assets.filter { (it.type == "audio" || it.mimeType.startsWith("audio/") || it.mimeType == "application/ogg") && (it.id in links.map { l -> l.mediaId } || inline.any { pair -> pair.first == it.id }) }
            .mapNotNull { asset -> media.resolveFile(asset).takeIf { it.exists() }?.let { file ->
                BookAudio(asset.displayName.ifBlank { "Linked audio" }, file.absolutePath,
                    links.firstOrNull { it.mediaId == asset.id }?.let { link -> scenes.firstOrNull { it.id == link.sceneId }?.title }
                        ?: inline.firstOrNull { it.first == asset.id }?.second.orEmpty())
            } })
    }.flowOn(Dispatchers.IO)

    fun opened(id: String) { viewModelScope.launch { history.record("Novel", "book", id) } }
    fun toggleList(id: String) { viewModelScope.launch { db.bookBrowsingDao().toggleList(id, System.currentTimeMillis()) } }
    fun synopsis(id: String, text: String) { viewModelScope.launch { db.bookBrowsingDao().synopsis(id, text) } }
    fun artwork(id: String, uri: Uri, backdrop: Boolean) { viewModelScope.launch {
        runCatching {
            val asset = media.importFromUri(uri)
            if (backdrop) db.bookBrowsingDao().backdrop(id, asset.id)
            else db.bookDao().getById(id)?.let { repository.updateBook(it.copy(coverMediaId = asset.id)) }
        }.onFailure { status.value = "Artwork could not be imported: ${it.message}" }
    } }
    fun metadata(id: String, title: String, genre: String) { viewModelScope.launch {
        db.bookDao().getById(id)?.let { repository.updateBook(it.copy(title = title.trim().ifBlank { it.title }, genre = genre.trim())) }
    } }
    fun duplicate(id: String) { viewModelScope.launch {
        runCatching { repository.duplicateBook(id) }.onSuccess { status.value = "Book copied" }.onFailure { status.value = "Copy failed: ${it.message}" }
    } }
    fun delete(id: String) { viewModelScope.launch {
        repository.deleteBook(id); db.bookBrowsingDao().delete(id)
        if (settings.preferences.first().selectedBookId == id) settings.setSelectedBookId("")
    } }
}
