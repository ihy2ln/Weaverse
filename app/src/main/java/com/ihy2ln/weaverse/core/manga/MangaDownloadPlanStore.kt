package com.ihy2ln.weaverse.core.manga

import android.content.SharedPreferences

/** Durable, non-secret follow-up choices. Never starts generation or changes source files. */
class MangaDownloadPlanStore(private val preferences: SharedPreferences) {
    fun read(): Map<String, MangaDownloadTreatment> = preferences.all.mapNotNull { (key, value) ->
        if (!key.startsWith(PREFIX)) return@mapNotNull null
        val mode = MangaDownloadTreatment.decode(value as? String)
        val chapterId = key.removePrefix(PREFIX)
        if (chapterId.isBlank() || mode == MangaDownloadTreatment.Original) null else chapterId to mode
    }.toMap()

    fun save(chapterId: String, mode: MangaDownloadTreatment) {
        require(chapterId.isNotBlank())
        val editor = preferences.edit()
        if (mode == MangaDownloadTreatment.Original) editor.remove(PREFIX + chapterId)
        else editor.putString(PREFIX + chapterId, mode.name)
        // Small metadata write: commit before reporting a durable queued choice.
        check(editor.commit()) { "Could not save the download option. Please retry." }
    }

    companion object { private const val PREFIX = "download-treatment." }
}
