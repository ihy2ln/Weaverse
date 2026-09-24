package com.ihy2ln.weaverse.core.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MangaFileImporterTest {
    @Test
    fun comicArchivePagesUseNaturalFilenameOrder() {
        val unordered = listOf(
            "chapter/page-10.png",
            "chapter/page-2.png",
            "chapter/page-001.png",
            "chapter/page-20.png",
            "chapter/page-3.png",
        )

        val ordered = unordered.sortedWith(::compareComicPageNames)

        assertEquals(
            listOf(
                "chapter/page-001.png",
                "chapter/page-2.png",
                "chapter/page-3.png",
                "chapter/page-10.png",
                "chapter/page-20.png",
            ),
            ordered,
        )
    }
}
