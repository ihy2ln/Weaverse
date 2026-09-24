package com.ihy2ln.weaverse.data.db

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The database no longer rebuilds itself when a step is missing, so a version bump
 * without a registered migration must fail here, before a build ever reaches a phone.
 */
class MigrationChainTest {
    @Test
    fun `every version from 5 to the current schema has exactly one step`() {
        // Room exports one JSON per version; the newest is the schema the app builds.
        val current = File("schemas/${WeaverseDatabase::class.java.name}")
            .listFiles().orEmpty().mapNotNull { it.nameWithoutExtension.toIntOrNull() }.max()
        val steps = WeaverseDatabase.ALL_MIGRATIONS.map { it.startVersion to it.endVersion }
        assertEquals((5 until current).map { it to it + 1 }, steps)
    }
}
