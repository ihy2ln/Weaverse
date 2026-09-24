package com.ihy2ln.weaverse.core.media

/** A private AI instruction asking the app to show local media for [topic]. */
data class TopicMediaRequest(
    val topic: String,
    val kind: String = "any",
)

data class TopicMediaParsedReply(
    val visibleText: String,
    val requests: List<TopicMediaRequest>,
)

private val TopicMediaMarker = Regex(
    "\\[\\[MEDIA\\|(.+?)]]",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)

/** Removes private media markers from model output and returns safe, bounded requests. */
fun parseTopicMediaReply(text: String): TopicMediaParsedReply {
    fun fields(payload: String): Map<String, String> = payload
        .split('|')
        .mapNotNull { part ->
            val split = part.indexOf('=')
            if (split <= 0) null
            else part.substring(0, split).trim().lowercase() to part.substring(split + 1).trim()
        }
        .toMap()

    val requests = TopicMediaMarker.findAll(text).mapNotNull { match ->
        val values = fields(match.groupValues[1])
        val topic = sanitizeTopicMediaName(values["topic"].orEmpty())
            .takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val kind = values["kind"].orEmpty().lowercase().let {
            when (it) {
                "image", "video" -> it
                else -> "any"
            }
        }
        TopicMediaRequest(topic = topic, kind = kind)
    }.distinctBy { it.topic to it.kind }.take(2).toList()

    return TopicMediaParsedReply(
        visibleText = TopicMediaMarker.replace(text, "").trim(),
        requests = requests,
    )
}

/** Keeps incomplete private markers out of the live streaming bubble. */
fun topicMediaVisibleText(text: String): String {
    val cleaned = TopicMediaMarker.replace(text, "")
    val partial = cleaned.indexOf("[[MEDIA", ignoreCase = true)
    return if (partial < 0) cleaned else cleaned.substring(0, partial).trimEnd()
}

fun sanitizeTopicMediaName(value: String): String = value
    .trim()
    .lowercase()
    .replace(Regex("[^a-z0-9 _-]"), "")
    .replace(Regex("\\s+"), " ")
    .take(80)

internal fun normalizedTopicMediaName(value: String): String = sanitizeTopicMediaName(value)
    .replace(Regex("[ _-]+"), " ")

/**
 * Explicit markers win. If a model forgets the marker, a clearly mentioned configured topic
 * still produces one attachment so the feature remains useful with less obedient models.
 */
fun topicMediaRequestsFor(
    parsed: TopicMediaParsedReply,
    availableTopics: List<String>,
): List<TopicMediaRequest> {
    if (parsed.requests.isNotEmpty()) return parsed.requests
    val visible = normalizedTopicMediaName(parsed.visibleText)
    val fallback = availableTopics.firstOrNull { topic ->
        val normalized = normalizedTopicMediaName(topic)
        normalized.isNotBlank() && Regex("(?:^| )${Regex.escape(normalized)}(?: |$)").containsMatchIn(visible)
    }
    return fallback?.let { listOf(TopicMediaRequest(it)) }.orEmpty()
}

