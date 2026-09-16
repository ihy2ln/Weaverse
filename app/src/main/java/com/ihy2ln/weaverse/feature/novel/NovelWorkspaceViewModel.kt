package com.ihy2ln.weaverse.feature.novel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.core.media.MediaRepository
import com.ihy2ln.weaverse.core.text.*
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.*
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import javax.inject.Inject
import androidx.room.withTransaction

data class NovelAsset(val mediaId: String, val name: String, val path: String, val kind: String,
    val sceneIds: List<String>, val references: List<NovelMediaLink>)
data class NovelWorkspaceState(val bookId: String = "", val scenes: List<SceneEntity> = emptyList(),
    val assets: List<NovelAsset> = emptyList(), val entries: List<CodexEntryEntity> = emptyList(),
    val contextLinks: List<SceneCodexLinkEntity> = emptyList())

internal fun inlineNovelMediaIds(blocks: List<Block>): List<String> = blocks.flatMap {
    when (it) { is MediaBlock -> listOf(it.mediaId); is MediaStackBlock -> it.mediaIds; is MediaGridBlock -> it.mediaIds; else -> emptyList() }
}.distinct()

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NovelWorkspaceViewModel @Inject constructor(private val db: WeaverseDatabase,
    private val settings: SettingsRepository, private val media: MediaRepository,
    private val history: com.ihy2ln.weaverse.feature.shell.WorkspaceHistory,
    private val stamps: com.ihy2ln.weaverse.data.repo.SceneWriteStamps) : ViewModel() {
    val status = MutableStateFlow("")
    val busy = MutableStateFlow(false)
    val state = settings.preferences.map { it.selectedBookId }.distinctUntilChanged().flatMapLatest { bookId ->
        val seriesId = db.bookDao().getById(bookId)?.seriesId
        combine(db.manuscriptDao().observeBookScenes(bookId), db.novelMediaDao().observe(bookId), db.mediaDao().observeAll(),
            db.codexDao().observeAllEntries(), db.novelMediaDao().observeContextLinks(bookId)) { scenes, links, allMedia, entries, contextLinks ->
            val placements = scenes.associate { it.id to inlineNovelMediaIds(documentFromJson(it.docJson).blocks) }
            val ids = placements.values.flatten().toSet() + links.map { it.mediaId }
            NovelWorkspaceState(bookId, scenes, allMedia.filter { it.id in ids }.map { asset ->
                NovelAsset(asset.id, asset.displayName.ifBlank { "Untitled media" }, media.resolveFile(asset).absolutePath,
                    asset.type, placements.filterValues { asset.id in it }.keys.toList(), links.filter { it.mediaId == asset.id })
            }, entries.filter { it.scopeId == bookId || it.scopeId == seriesId || it.scopeId == "global" }, contextLinks)
        }
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NovelWorkspaceState())

    fun pinContext(sceneId: String, entryId: String, pinned: Boolean) {
        if (state.value.scenes.none { it.id == sceneId } || state.value.entries.none { it.id == entryId }) return
        viewModelScope.launch {
            if (pinned) db.novelMediaDao().pin(SceneCodexLinkEntity(sceneId, entryId, updatedAt = System.currentTimeMillis()))
            else db.novelMediaDao().unpin(sceneId, entryId)
        }
    }
    fun moveScene(sceneId: String, delta: Int) {
        val scene = state.value.scenes.firstOrNull { it.id == sceneId } ?: return
        val siblings = state.value.scenes.filter { it.chapterId == scene.chapterId }
        val index = siblings.indexOfFirst { it.id == sceneId }
        val other = siblings.getOrNull(index + delta) ?: return
        viewModelScope.launch {
            suspend fun order(reverse: Boolean) = db.withTransaction {
                db.novelMediaDao().setSceneOrder(scene.id, if (reverse) scene.sortOrder else other.sortOrder, stamps.next())
                db.novelMediaDao().setSceneOrder(other.id, if (reverse) other.sortOrder else scene.sortOrder, stamps.next())
            }
            order(false)
            history.record(undo = { order(true) }, redo = { order(false) })
        }
    }
    fun updateScene(scene: SceneEntity, summary: String, pov: String, status: String) {
        if (state.value.scenes.none { it.id == scene.id }) return
        viewModelScope.launch {
            db.novelMediaDao().updateSceneMetadata(scene.id, summary, pov, status, stamps.next())
        }
    }

    fun importReferences(uris: List<Uri>, sceneId: String) {
        val snapshot = state.value
        if (busy.value || sceneId !in snapshot.scenes.map { it.id }) return
        busy.value = true
        viewModelScope.launch {
            try {
                uris.forEach { uri ->
                    val asset = media.importFromUri(uri)
                    db.novelMediaDao().upsert(NovelMediaLink(UUID.randomUUID().toString(), snapshot.bookId, sceneId,
                        mediaId = asset.id, caption = asset.displayName, createdAt = System.currentTimeMillis()))
                }
                status.value = "Reference saved. It is not included in the manuscript or sent to AI."
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { status.value = "Could not import all media. Any completed imports were kept." }
            finally { busy.value = false }
        }
    }
    fun linkReference(asset: NovelAsset, sceneId: String) {
        val snapshot = state.value
        if (sceneId !in snapshot.scenes.map { it.id } || asset !in snapshot.assets) return
        if (asset.references.any { it.sceneId == sceneId }) return
        viewModelScope.launch {
            db.novelMediaDao().upsert(NovelMediaLink(UUID.randomUUID().toString(), snapshot.bookId, sceneId,
                mediaId = asset.mediaId, caption = asset.name, provenance = "Reused from this novel", createdAt = System.currentTimeMillis()))
        }
    }
    fun saveReference(link: NovelMediaLink, caption: String, alt: String, entryId: String = link.entryId) {
        if (link.bookId != state.value.bookId) return
        if (entryId.isNotBlank() && state.value.entries.none { it.id == entryId }) return
        viewModelScope.launch { db.novelMediaDao().upsert(link.copy(caption = caption, altText = alt, entryId = entryId)) }
    }
    fun removeReference(link: NovelMediaLink) {
        val bookId = state.value.bookId
        if (link.bookId != bookId) return
        viewModelScope.launch {
            db.novelMediaDao().remove(link.id, bookId)
            status.value = "Reference removed. The original file and in-story placements were kept."
        }
    }
}
