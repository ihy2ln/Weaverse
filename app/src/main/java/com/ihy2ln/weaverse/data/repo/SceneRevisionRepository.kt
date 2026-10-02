package com.ihy2ln.weaverse.data.repo

import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.SceneEntity
import com.ihy2ln.weaverse.data.db.entities.SceneRevisionEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Singleton
class SceneRevisionRepository @Inject constructor(
    private val db: WeaverseDatabase,
    private val writeStamps: SceneWriteStamps,
) {
    fun observe(sceneId: String): Flow<List<SceneRevisionEntity>> =
        db.manuscriptDao().observeRevisions(sceneId)

    suspend fun snapshotIfDue(scene: SceneEntity, minIntervalMs: Long = HOUR_MS) {
        val latest = db.manuscriptDao().latestRevision(scene.id)
        val now = System.currentTimeMillis()
        if (!isDue(latest?.createdAt, latest?.docJson, scene.docJson, now, minIntervalMs)) return
        db.manuscriptDao().upsertRevision(
            SceneRevisionEntity(
                id = "rev-${UUID.randomUUID()}",
                sceneId = scene.id,
                createdAt = now,
                docJson = scene.docJson,
                plainText = scene.plainText,
                wordCount = scene.wordCount,
                kind = "hourly",
            ),
        )
        db.manuscriptDao().pruneRevisions(scene.id, KEEP_PER_SCENE)
    }

    /** Snapshots the writer asked for (or made before a restore) are kept apart from the hourly rotation. */
    suspend fun snapshotNow(scene: SceneEntity, kind: String = "manual") {
        db.manuscriptDao().upsertRevision(
            SceneRevisionEntity(
                id = "rev-${UUID.randomUUID()}",
                sceneId = scene.id,
                createdAt = System.currentTimeMillis(),
                docJson = scene.docJson,
                plainText = scene.plainText,
                wordCount = scene.wordCount,
                kind = kind,
            ),
        )
        db.manuscriptDao().pruneKeptRevisions(scene.id, KEEP_SAVED_PER_SCENE)
    }

    /**
     * Saves [current] — the text on screen, including edits not yet written — as a
     * "before-restore" snapshot, then hands back [revisionId] for the editor to write
     * through its own ordered save queue. Restoring never loses what was there.
     */
    suspend fun prepareRestore(revisionId: String, current: SceneEntity): SceneRevisionEntity? {
        val revision = db.manuscriptDao().getRevision(revisionId) ?: return null
        if (revision.sceneId != current.id) return null
        snapshotNow(current, kind = KIND_BEFORE_RESTORE)
        return revision
    }

    companion object {
        const val HOUR_MS = 60L * 60L * 1000L
        const val KEEP_PER_SCENE = 24
        const val KEEP_SAVED_PER_SCENE = 100
        const val KIND_BEFORE_RESTORE = "before-restore"

        fun isDue(
            latestCreatedAt: Long?,
            latestDocJson: String?,
            currentDocJson: String,
            now: Long,
            minIntervalMs: Long = HOUR_MS,
        ): Boolean {
            if (latestCreatedAt == null || latestDocJson == null) return true
            if (now - latestCreatedAt < minIntervalMs) return false
            if (latestDocJson == currentDocJson) return false
            return true
        }
    }
}
