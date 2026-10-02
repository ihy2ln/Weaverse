package com.ihy2ln.weaverse.feature.chatting.social

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Reddit API listings (`/r/x/hot`, `/best`, `/comments/id`) as plain posts and comments. */
object RedditJson {
    data class Post(
        val id: String,
        val title: String,
        val body: String,
        val author: String,
        val subreddit: String,
        val permalink: String,
        val over18: Boolean,
        val score: Int,
        val commentCount: Int,
        /** Full picture, GIF loop or video (Reddit's fallback MP4, silent). */
        val media: String?,
        val thumbnail: String?,
        val isLoop: Boolean,
    ) {
        val url: String get() = "https://www.reddit.com$permalink"
    }

    data class Comment(val author: String, val body: String, val score: Int)

    fun posts(listing: JsonElement): List<Post> = listing.obj()["data"].obj()["children"].arr().mapNotNull { child ->
        val d = child.obj()["data"].obj()
        if (child.obj().str("kind") != "t3") return@mapNotNull null
        if (d.str("stickied") == "true") return@mapNotNull null
        val permalink = d.str("permalink") ?: return@mapNotNull null
        val title = d.str("title") ?: return@mapNotNull null
        val preview = d["preview"].obj()["images"].arr().firstOrNull()?.obj()
        val previewSource = preview?.get("source")?.obj()?.str("url")
        val previewGif = preview?.get("variants")?.obj()?.get("mp4")?.obj()?.get("source")?.obj()?.str("url")
        val video = d["secure_media"].obj()["reddit_video"].obj().str("fallback_url")
            ?: d["media"].obj()["reddit_video"].obj().str("fallback_url")
            ?: d["preview"].obj()["reddit_video_preview"].obj().str("fallback_url")
        val galleryFirst = d["gallery_data"].obj()["items"].arr().firstOrNull()?.obj()?.str("media_id")
            ?.let { id -> d["media_metadata"].obj()[id].obj()["s"].obj().let { it.str("gif") ?: it.str("u") } }
        val link = d.str("url_overridden_by_dest") ?: d.str("url")
        val direct = link?.takeIf { DIRECT.containsMatchIn(it) }?.let { if (it.endsWith(".gifv")) it.dropLast(5) + ".mp4" else it }
        val media = video ?: previewGif ?: direct ?: galleryFirst ?: previewSource
        Post(
            id = d.str("id").orEmpty(),
            title = title,
            body = d.str("selftext").orEmpty(),
            author = d.str("author").orEmpty(),
            subreddit = d.str("subreddit").orEmpty(),
            permalink = permalink,
            over18 = d.str("over_18") == "true",
            score = d.str("score")?.toIntOrNull() ?: 0,
            commentCount = d.str("num_comments")?.toIntOrNull() ?: 0,
            media = media?.takeIf { it.startsWith("https://") },
            thumbnail = previewSource?.takeIf { it.startsWith("https://") },
            isLoop = media != null && (media == video || media == previewGif || Regex("\\.(gif|mp4)(\\?|$)").containsMatchIn(media)),
        )
    }

    /** Top-level comments from `/comments/{id}`, skipping deleted ones and moderator bots. */
    fun comments(thread: JsonElement): List<Comment> = (thread as? JsonArray)?.getOrNull(1)
        ?.obj()?.get("data")?.obj()?.get("children")?.arr().orEmpty().mapNotNull { child ->
            if (child.obj().str("kind") != "t1") return@mapNotNull null
            val d = child.obj()["data"].obj()
            val author = d.str("author") ?: return@mapNotNull null
            val body = d.str("body") ?: return@mapNotNull null
            if (author == "[deleted]" || author.equals("AutoModerator", true) || body == "[removed]") return@mapNotNull null
            Comment(author, body, d.str("score")?.toIntOrNull() ?: 0)
        }

    private val DIRECT = Regex("^https://(i\\.redd\\.it|i\\.imgur\\.com)/[^?#]+\\.(gifv?|jpe?g|png|webp|mp4)$", RegexOption.IGNORE_CASE)

    private fun JsonElement?.obj(): JsonObject = this as? JsonObject ?: JsonObject(emptyMap())
    private fun JsonElement?.arr(): JsonArray = this as? JsonArray ?: JsonArray(emptyList())
    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
}
