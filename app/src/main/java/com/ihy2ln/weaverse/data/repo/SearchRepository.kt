package com.ihy2ln.weaverse.data.repo

import com.ihy2ln.weaverse.data.db.AppDatabase
import javax.inject.Inject
import javax.inject.Singleton

data class SearchResults(
    val sceneIds: List<String>,
    val codexEntryIds: List<String>,
    val chatMessageIds: List<String>,
    val rpMessageIds: List<String>,
    val snippetIds: List<String>,
) {
    val isEmpty: Boolean
        get() = sceneIds.isEmpty() && codexEntryIds.isEmpty() && chatMessageIds.isEmpty() &&
            rpMessageIds.isEmpty() && snippetIds.isEmpty()
}

/**
 * Cross-entity full-text search (spec §4/§9 Global Search) — queries every
 * FTS4 table in parallel and returns matched entity ids per type; the
 * caller resolves those against the owning repository to render results.
 */
@Singleton
class SearchRepository @Inject constructor(private val db: AppDatabase) {
    suspend fun search(rawQuery: String): SearchResults {
        val query = rawQuery.trim()
        if (query.isEmpty()) {
            return SearchResults(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        }
        // FTS4 MATCH treats the query as its own mini-syntax (AND/OR/NEAR,
        // quoting); wildcard-suffix each raw word for "starts with" style
        // matching, which is the behavior users expect from an app search box.
        val ftsQuery = query.split(Regex("\\s+")).joinToString(" ") { "$it*" }

        return SearchResults(
            sceneIds = db.sceneFtsDao().search(ftsQuery),
            codexEntryIds = db.codexEntryFtsDao().search(ftsQuery),
            chatMessageIds = db.chatMessageFtsDao().search(ftsQuery),
            rpMessageIds = db.rpMessageFtsDao().search(ftsQuery),
            snippetIds = db.snippetFtsDao().search(ftsQuery),
        )
    }
}
