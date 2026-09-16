package com.ihy2ln.weaverse.core.manga

import com.ihy2ln.weaverse.data.db.entities.MangaChapterEntity

/** Length prefix keeps arbitrary extension IDs unambiguous; titles are never identity. */
fun mangaLibraryKey(source: String, remote: String): String = "${source.length}:$source$remote"

fun groupLibraryChapters(chapters: List<MangaChapterEntity>): Map<String, List<MangaChapterEntity>> =
    chapters.groupBy { mangaLibraryKey(it.sourceId, it.mangaId.ifBlank { it.canonicalUrl.ifBlank { it.id } }) }
        .mapValues { (_, rows) -> rows.sortedWith(compareBy<MangaChapterEntity> { it.volume.toDoubleOrNull() ?: 0.0 }
            .thenBy { it.chapterNumber.toDoubleOrNull() ?: Double.MAX_VALUE }.thenBy { it.title }) }
