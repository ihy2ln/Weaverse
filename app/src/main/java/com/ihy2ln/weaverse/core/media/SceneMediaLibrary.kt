package com.ihy2ln.weaverse.core.media

import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.MediaEntity
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

data class SceneMediaRequest(
    val scene: String,
    val kind: String = "any",
    val tags: List<String> = emptyList(),
    val limit: Int = 12,
)

data class SceneMediaCandidate(
    val id: String,
    val kind: String,
    val displayName: String,
    val category: String,
    val tags: List<String>,
    val width: Int,
    val height: Int,
    val durationMs: Long?,
    val score: Int,
)

/** Local scene-media search shared by app features and the AI-facing MCP backend. */
@Singleton
class SceneMediaLibrary @Inject constructor(
    private val db: WeaverseDatabase,
) {
    suspend fun find(request: SceneMediaRequest): List<SceneMediaCandidate> =
        rankSceneMedia(db.mediaDao().observeAll().first(), request)

    suspend fun promptContext(request: SceneMediaRequest): String {
        val matches = find(request)
        if (matches.isEmpty()) {
            return "No categorized image or video matches scene '${request.scene}'."
        }
        return buildString {
            appendLine("Eligible local media for scene '${request.scene}':")
            matches.forEach { candidate ->
                append("- mediaId=${candidate.id} | kind=${candidate.kind} | name=${candidate.displayName}")
                append(" | category=${candidate.category.ifBlank { "Uncategorized" }}")
                if (candidate.tags.isNotEmpty()) append(" | tags=${candidate.tags.joinToString(",")}")
                if (candidate.width > 0 && candidate.height > 0) append(" | ${candidate.width}x${candidate.height}")
                appendLine()
            }
            append("Choose only from these mediaId values when attaching existing scene media. ")
            append("If none fits the prose, request newly generated art instead of inventing an ID.")
        }
    }
}

internal fun rankSceneMedia(
    media: List<MediaEntity>,
    request: SceneMediaRequest,
): List<SceneMediaCandidate> {
    val requestedKind = request.kind.trim().lowercase()
    val sceneTokens = mediaSearchTokens(request.scene)
    val requestedTags = request.tags.flatMap(::mediaSearchTokens).toSet()
    return media.asSequence()
        .filter { it.type == "image" || it.type == "video" }
        .filter { requestedKind == "any" || requestedKind.isBlank() || it.type == requestedKind }
        .mapNotNull { entity ->
            val tags = parseMediaTags(entity.tags)
            val tagTokens = tags.flatMap(::mediaSearchTokens).toSet()
            val categoryTokens = mediaSearchTokens(entity.category).toSet()
            val nameTokens = mediaSearchTokens(entity.displayName).toSet()
            val exactSceneTags = sceneTokens.map { "scene:$it" }.toSet()
            var score = 0
            score += tags.count { it in exactSceneTags } * 100
            score += sceneTokens.count { it in categoryTokens } * 35
            score += sceneTokens.count { it in tagTokens } * 18
            score += sceneTokens.count { it in nameTokens } * 6
            score += requestedTags.count { it in tagTokens } * 30
            if (requestedTags.isNotEmpty() && requestedTags.none { it in tagTokens }) score -= 20
            if (score <= 0) null else SceneMediaCandidate(
                id = entity.id,
                kind = entity.type,
                displayName = entity.displayName.ifBlank { entity.id },
                category = entity.category,
                tags = tags,
                width = entity.width,
                height = entity.height,
                durationMs = entity.durationMs,
                score = score,
            )
        }
        .sortedWith(compareByDescending<SceneMediaCandidate> { it.score }.thenBy { it.displayName.lowercase() }.thenBy { it.id })
        .take(request.limit.coerceIn(1, 50))
        .toList()
}

internal fun sanitizeMediaCategory(value: String): String = value
    .trim()
    .split('/')
    .map(String::trim)
    .filter(String::isNotBlank)
    .joinToString(" / ")
    .take(120)

internal fun tagsAfterCategoryMove(existing: String, category: String): String {
    val retained = parseMediaTags(existing).filterNot { it.startsWith("scene:") }
    return (retained + sceneTagsForCategory(category)).distinct().joinToString(",")
}

internal fun sceneTagsForCategory(category: String): List<String> {
    val marker = Regex("(?:^|/)\\s*scene\\s*/", RegexOption.IGNORE_CASE)
    val match = marker.find(category) ?: return emptyList()
    val scenePart = category.substring(match.range.last + 1)
    return scenePart.split('&', ',', '+')
        .flatMap(::mediaSearchTokens)
        .map(::singularSceneToken)
        .filter { it !in setOf("scene", "and") }
        .distinct()
        .map { "scene:$it" }
}

internal fun parseMediaTags(value: String): List<String> = value
    .split(',')
    .map { it.trim().lowercase() }
    .filter(String::isNotBlank)
    .distinct()

private fun mediaSearchTokens(value: String): List<String> = value.lowercase()
    .split(Regex("[^a-z0-9]+"))
    .filter { it.length > 1 }
    .map(::singularSceneToken)

private fun singularSceneToken(value: String): String = when (value) {
    "dungeons" -> "dungeon"
    "battles" -> "battle"
    "locations" -> "location"
    "pictures" -> "picture"
    "videos" -> "video"
    "houses" -> "house"
    else -> value
}
