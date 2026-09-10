package com.ihy2ln.weaverse.core.media

import com.ihy2ln.weaverse.data.db.entities.MediaEntity
import kotlinx.serialization.Serializable

@Serializable
data class AiMediaRequest(
    val type: String = "image",
    val query: String = "",
    val tags: List<String> = emptyList(),
    val category: String = "",
    val caption: String = "",
)

object AiMediaRequestParser {
    fun normalize(request: AiMediaRequest): AiMediaRequest? {
        val type = request.type.lowercase().trim()
        if (type !in setOf("image", "video")) return null
        if (request.query.isBlank() && request.tags.isEmpty() && request.category.isBlank()) return null
        return request.copy(type = type, query = request.query.trim())
    }

    private val line = Regex("\\[MEDIA\\s+type=(image|video)\\s+query=([^\\]]*?)(?:\\s+tags=([^\\]]*?))?(?:\\s+category=([^\\]]*?))?\\s*\\]", RegexOption.IGNORE_CASE)

    fun extract(text: String): Pair<String, List<AiMediaRequest>> {
        val requests = line.findAll(text).map { match ->
            AiMediaRequest(
                type = match.groupValues[1],
                query = match.groupValues[2].trim(),
                tags = match.groupValues.getOrNull(3).orEmpty().split(',').map(String::trim).filter(String::isNotBlank),
                category = match.groupValues.getOrNull(4).orEmpty().trim(),
            )
        }.toList()
        return text.replace(line, "").trim() to requests
    }
}

class AiMediaResolver(private val allMedia: suspend () -> List<MediaEntity>) {
    suspend fun resolve(request: AiMediaRequest): MediaEntity? {
        val wanted = AiMediaRequestParser.normalize(request) ?: return null
        val terms = (wanted.tags + wanted.query.split(Regex("\\W+"))).map { it.lowercase() }.filter { it.length > 2 }.toSet()
        return allMedia().asSequence()
            .filter { it.type == wanted.type }
            .filter { wanted.category.isBlank() || it.category.equals(wanted.category, ignoreCase = true) }
            .map { media -> media to score(media, terms) }
            .filter { it.second > 0 || terms.isEmpty() }
            .sortedWith(compareByDescending<Pair<MediaEntity, Int>> { it.second }.thenBy { it.first.displayName }.thenBy { it.first.id })
            .map { it.first }
            .firstOrNull()
    }

    private fun score(media: MediaEntity, terms: Set<String>): Int {
        val haystack = "${media.displayName} ${media.category} ${media.tags}".lowercase()
        return terms.fold(0) { total, term -> total + if (haystack.contains(term)) 1 else 0 }
    }
}
