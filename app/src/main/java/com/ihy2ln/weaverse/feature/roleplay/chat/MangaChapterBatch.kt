package com.ihy2ln.weaverse.feature.roleplay.chat

import kotlinx.coroutines.CancellationException

/** A failed image or AI call must not prevent the remaining chapter pages from running. */
internal suspend fun <T> runMangaChapterBatch(
    targets: List<T>,
    onFailure: suspend (index: Int, error: Exception) -> Unit,
    process: suspend (index: Int, target: T) -> Unit,
) {
    targets.forEachIndexed { index, target ->
        try {
            process(index, target)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            onFailure(index, failure)
        }
    }
}
