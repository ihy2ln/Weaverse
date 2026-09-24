package com.ihy2ln.weaverse.core.story

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Save slots for the novel and campaign starts, kept as small JSON files beside the
 * database rather than in it, so they survive a finished start, Start over, and any
 * schema change.
 *
 * Two kinds of slot:
 *  - the "CYOA set up" checkpoint, one per book or campaign, captured when the story
 *    questions are finished and the chapter plan is asked for;
 *  - the starting template, one per mode, which any new start can load to skip
 *    straight to verification.
 */
@Singleton
class StartSlotStore @Inject constructor(@ApplicationContext context: Context) {
    private val dir = File(context.filesDir, "start-slots")

    suspend fun read(key: String): String? = withContext(Dispatchers.IO) {
        File(dir, fileName(key)).takeIf { it.isFile }?.let { runCatching { it.readText() }.getOrNull() }
            ?.takeIf { it.isNotBlank() }
    }

    suspend fun write(key: String, json: String) = withContext(Dispatchers.IO) {
        if (json.isBlank()) return@withContext
        dir.mkdirs()
        // Write beside, then rename, so a crash mid-write never leaves a torn slot.
        val target = File(dir, fileName(key))
        val temp = File(dir, fileName(key) + ".tmp")
        temp.writeText(json)
        if (!temp.renameTo(target)) {
            target.delete()
            temp.renameTo(target)
        }
    }

    suspend fun delete(key: String) = withContext(Dispatchers.IO) {
        File(dir, fileName(key)).delete()
    }

    /** Marks a just-created book or campaign to load the starting template when its start opens. */
    suspend fun markPendingTemplate(workId: String) = write(pendingKey(workId), "1")

    /** True once, for a work created from the + menu's template entry. */
    suspend fun consumePendingTemplate(workId: String): Boolean {
        val pending = read(pendingKey(workId)) != null
        if (pending) delete(pendingKey(workId))
        return pending
    }

    private fun pendingKey(workId: String) = "pending-template-$workId"

    private fun fileName(key: String) = key.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".json"

    companion object {
        const val NOVEL_TEMPLATE = "novel-template"
        const val RPG_TEMPLATE = "rpg-template"
        fun novelCyoaCheckpoint(bookId: String) = "novel-cyoa-$bookId"
        fun rpgCyoaCheckpoint(campaignId: String) = "rpg-cyoa-$campaignId"

        /** Title of a work made from the + menu's template entry. */
        fun templateWorkTitle(template: String?): String = template?.takeIf { it.isNotBlank() } ?: "From CYOA template"
    }
}
