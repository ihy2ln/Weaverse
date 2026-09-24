package com.ihy2ln.weaverse.data.db

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DatabaseMigrationsTest {
    @Test
    fun everyUpgradeStepHasAMigration() {
        val steps = DatabaseMigrations.ALL.map { it.startVersion to it.endVersion }
        assertEquals(
            emptyList<Pair<Int, Int>>(),
            DatabaseMigrations.missingSteps(steps),
            "Bumped WeaverseDatabase.VERSION without adding a Migration to DatabaseMigrations.ALL",
        )
    }

    @Test
    fun legacyFallbackNeverCoversMigratableVersions() {
        assertTrue(DatabaseMigrations.LEGACY_VERSIONS.all { it < DatabaseMigrations.FIRST_MIGRATABLE_VERSION })
    }

    @Test
    fun missingStepsReportsGaps() {
        assertEquals(listOf(6 to 7), DatabaseMigrations.missingSteps(listOf(5 to 6), from = 5, to = 7))
        assertEquals(emptyList<Pair<Int, Int>>(), DatabaseMigrations.missingSteps(emptyList(), from = 5, to = 5))
    }
}
