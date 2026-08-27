package com.ihy2ln.weaverse.feature.novel.write

import com.ihy2ln.weaverse.core.text.Block
import com.ihy2ln.weaverse.core.text.Document
import com.ihy2ln.weaverse.core.text.DocumentFindReplace
import com.ihy2ln.weaverse.core.text.FindHit
import com.ihy2ln.weaverse.core.text.plainText
import com.ihy2ln.weaverse.core.text.toJson
import com.ihy2ln.weaverse.core.text.wordCount
import com.ihy2ln.weaverse.data.db.entities.SceneEntity
import com.ihy2ln.weaverse.data.db.entities.SceneRevisionEntity
import com.ihy2ln.weaverse.data.repo.ManuscriptRepository
import com.ihy2ln.weaverse.data.repo.SceneRevisionRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Scene document persist plus in-scene find/replace. WriteViewModel stays a Hilt coordinator.
 */
@Singleton
class WriteDocumentOps @Inject constructor(
    private val manuscriptRepository: ManuscriptRepository,
    private val sceneRevisions: SceneRevisionRepository,
) {
    private val persistMutex = Mutex()

    fun observeRevisions(sceneId: String): Flow<List<SceneRevisionEntity>> =
        sceneRevisions.observe(sceneId)

    fun observeScene(sceneId: String) = manuscriptRepository.observeScene(sceneId)

    suspend fun getScene(sceneId: String): SceneEntity? = manuscriptRepository.getScene(sceneId)

    suspend fun persist(base: SceneEntity, doc: Document): SceneEntity = persistMutex.withLock {
        val latest = manuscriptRepository.getScene(base.id) ?: base
        val snapshot = Document(doc.blocks.toList())
        val updated = latest.copy(
            docJson = snapshot.toJson(),
            plainText = snapshot.plainText(),
            wordCount = snapshot.wordCount(),
            updatedAt = System.currentTimeMillis(),
        )
        manuscriptRepository.saveScene(updated)
        sceneRevisions.snapshotIfDue(updated)
        updated
    }

    suspend fun saveScene(scene: SceneEntity) = manuscriptRepository.saveScene(scene)

    suspend fun restoreBlocks(sceneId: String, blocks: List<Block>): SceneEntity? {
        val doc = Document(blocks)
        val base = manuscriptRepository.getScene(sceneId) ?: return null
        val updated = base.copy(
            docJson = doc.toJson(),
            plainText = doc.plainText(),
            wordCount = doc.wordCount(),
            updatedAt = System.currentTimeMillis(),
        )
        manuscriptRepository.saveScene(updated)
        return updated
    }

    suspend fun restoreRevision(revisionId: String): SceneEntity? = sceneRevisions.restore(revisionId)

    suspend fun snapshotNow(scene: SceneEntity, kind: String = "manual") =
        sceneRevisions.snapshotNow(scene, kind)

    fun recomputeFind(blocks: List<Block>, state: FindReplaceState): FindReplaceState {
        val matches = DocumentFindReplace.findAll(blocks, state.query, state.caseSensitive)
        val index = if (matches.isEmpty()) 0 else state.matchIndex.coerceIn(0, matches.lastIndex)
        return state.copy(matches = matches, matchIndex = index)
    }

    fun stepFind(state: FindReplaceState, delta: Int): FindReplaceState {
        if (state.matches.isEmpty()) return state
        val next = (state.matchIndex + delta).mod(state.matches.size)
        return state.copy(matchIndex = next)
    }

    fun selectionFor(hit: FindHit): SelectionState =
        SelectionState(blockIndex = hit.blockIndex, start = hit.start, end = hit.end)

    fun replaceCurrent(blocks: List<Block>, state: FindReplaceState): List<Block>? {
        val hit = state.matches.getOrNull(state.matchIndex) ?: return null
        return DocumentFindReplace.replaceHit(blocks, hit, state.replacement)
    }

    fun replaceAll(blocks: List<Block>, state: FindReplaceState): Pair<List<Block>, Int> {
        if (state.query.isEmpty()) return blocks to 0
        return DocumentFindReplace.replaceAll(
            blocks,
            state.query,
            state.replacement,
            state.caseSensitive,
        )
    }

    fun revisionUi(list: List<SceneRevisionEntity>): List<SceneRevisionUi> =
        list.map { rev ->
            SceneRevisionUi(
                id = rev.id,
                createdAt = rev.createdAt,
                wordCount = rev.wordCount,
                preview = rev.plainText.replace('\n', ' ').take(120),
            )
        }
}
