package com.ihy2ln.weaverse.feature.chatting.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.ai.AIError
import com.ihy2ln.weaverse.ai.AiGenerationService
import com.ihy2ln.weaverse.ai.context.AssembledPrompt
import com.ihy2ln.weaverse.core.media.MediaRepository
import com.ihy2ln.weaverse.core.roleplay.avatarColorHexFor
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.MediaEntity
import com.ihy2ln.weaverse.data.db.entities.RpCharacterEntity
import com.ihy2ln.weaverse.data.db.entities.SocialPostEntity
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import com.ihy2ln.weaverse.feature.chatting.ChatCastResolver
import com.ihy2ln.weaverse.feature.chatting.ChatRoomSeeder
import com.ihy2ln.weaverse.feature.chatting.matchNamedCharacters
import com.ihy2ln.weaverse.feature.chatting.ParsedLine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import kotlin.math.absoluteValue
import kotlin.random.Random

/** The one WeaverSocial feed. Older Facebook/Twitter posts keep their ids and show in it too. */
const val PLATFORM_WEAVERSOCIAL = "weaversocial"
private val LEGACY_PLATFORMS = listOf("facebook", "twitter")

/** Facebook's seven reactions, in the order its picker shows them. */
enum class FbReaction(val id: String, val emoji: String, val label: String, val colorHex: Long) {
    Like("like", "👍", "Like", 0xFF1877F2),
    Love("love", "❤️", "Love", 0xFFF33E58),
    Care("care", "🥰", "Care", 0xFFF7B125),
    Haha("haha", "😆", "Haha", 0xFFF7B125),
    Wow("wow", "😮", "Wow", 0xFFF7B125),
    Sad("sad", "😢", "Sad", 0xFFF7B125),
    Angry("angry", "😡", "Angry", 0xFFE9710F),
    ;

    companion object {
        fun of(id: String): FbReaction? = entries.firstOrNull { it.id == id }
    }
}

data class SocialPostUi(
    val id: String,
    val authorCharacterId: String?,
    val authorName: String,
    val handle: String,
    val colorHex: String,
    val isYou: Boolean,
    val verified: Boolean,
    val text: String,
    val imagePath: String?,
    val createdAt: Long,
    val parentId: String?,
    val likeCount: Int,
    val repostCount: Int,
    val replyCount: Int,
    val viewCount: Int,
    val userReaction: String,
    val userReposted: Boolean,
    val bookmarked: Boolean,
    /** Facebook: the most-used reactions, most first, for the stacked icons. */
    val topReactions: List<FbReaction>,
    val feeling: String,
    val repostOf: SocialPostUi?,
)

data class SocialPersonUi(
    val characterId: String,
    val name: String,
    val handle: String,
    val colorHex: String,
    val bio: String,
    val followers: Int,
    val following: Int,
    val verified: Boolean,
    val isFollowing: Boolean,
    val joinedAt: Long,
)

data class SocialNotificationUi(
    val id: String,
    /** reply | like | follow | mention */
    val kind: String,
    val actorName: String,
    val actorColorHex: String,
    val text: String,
    val postId: String?,
    val createdAt: Long,
)

data class SocialUiState(
    val platform: String = "",
    val personaName: String = "You",
    val personaBio: String = "",
    val posts: List<SocialPostUi> = emptyList(),
    val repliesByParent: Map<String, List<SocialPostUi>> = emptyMap(),
    val allById: Map<String, SocialPostUi> = emptyMap(),
    val people: List<SocialPersonUi> = emptyList(),
    val notifications: List<SocialNotificationUi> = emptyList(),
    val trends: List<Pair<String, Int>> = emptyList(),
    val generating: Boolean = false,
    val status: String = "",
    val error: String = "",
    val pendingImagePath: String? = null,
    val mediaPickRequestId: Long = 0,
    val castLoaded: Boolean = false,
) {
    val youHandle: String get() = handleFor(personaName)
    val followingIds: Set<String> get() = people.filter { it.isFollowing }.map { it.characterId }.toSet()
}

fun handleFor(name: String): String =
    name.lowercase().filter { it.isLetterOrDigit() || it == '_' }.ifBlank { "you" }.take(15)

/**
 * Backs the Chatting mode's Facebook and Twitter feeds. The cast — every codex character
 * and character card — post, comment and react in their own voice through the writing model.
 */
@HiltViewModel
class SocialFeedViewModel @Inject constructor(
    private val db: WeaverseDatabase,
    private val aiGeneration: AiGenerationService,
    private val settings: SettingsRepository,
    private val castResolver: ChatCastResolver,
    private val roomSeeder: ChatRoomSeeder,
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SocialUiState())
    val uiState: StateFlow<SocialUiState> = _uiState.asStateFlow()

    private var platform = ""
    private var cast: List<RpCharacterEntity> = emptyList()
    private var pendingMedia: MediaEntity? = null
    private var entities: Map<String, SocialPostEntity> = emptyMap()

    fun bind(platformId: String = PLATFORM_WEAVERSOCIAL) {
        if (platform == platformId) return
        platform = platformId
        _uiState.update { it.copy(platform = platformId) }
        viewModelScope.launch {
            val persona = roomSeeder.defaultPersona()
            _uiState.update {
                it.copy(personaName = persona.name.ifBlank { "You" }, personaBio = persona.description)
            }
            cast = castResolver.allChatContacts()
            _uiState.update { it.copy(castLoaded = true) }
            combine(db.socialDao().observeAllPosts(), allFollows()) { posts, follows ->
                posts to follows
            }.collect { (posts, follows) -> publish(posts, follows) }
        }
    }

    // ---------------------------------------------------------------- rendering

    private suspend fun publish(posts: List<SocialPostEntity>, follows: Set<String>) {
        entities = posts.associateBy { it.id }
        val replyCounts = posts.filter { it.parentId != null }.groupingBy { it.parentId!! }.eachCount()
        val quoteCounts = posts.filter { it.repostOfId != null }.groupingBy { it.repostOfId!! }.eachCount()
        val shallow = posts.associate { it.id to it.toUi(replyCounts, quoteCounts, null) }
        val full = posts.associate { post ->
            post.id to post.toUi(replyCounts, quoteCounts, post.repostOfId?.let { shallow[it] })
        }
        val top = posts.filter { it.parentId == null }.mapNotNull { full[it.id] }
        val replies = posts.filter { it.parentId != null }
            .sortedBy { it.createdAt }
            .groupBy { it.parentId!! }
            .mapValues { (_, list) -> list.mapNotNull { full[it.id] } }
        val people = cast.map { character ->
            val seed = character.name.hashCode().absoluteValue
            SocialPersonUi(
                characterId = character.id,
                name = character.name,
                handle = handleFor(character.name),
                colorHex = avatarColorHexFor(character.name, character.colorHex),
                bio = bioOf(character),
                followers = 80 + seed % 48_000,
                following = 20 + (seed / 7) % 900,
                verified = seed % 3 == 0,
                isFollowing = character.id in follows,
                joinedAt = character.createdAt,
            )
        }
        _uiState.update {
            it.copy(
                posts = top,
                repliesByParent = replies,
                allById = full,
                people = people,
                notifications = buildNotifications(posts, follows),
                trends = buildTrends(posts),
            )
        }
    }

    private suspend fun SocialPostEntity.toUi(
        replyCounts: Map<String, Int>,
        quoteCounts: Map<String, Int>,
        repostOf: SocialPostUi?,
    ): SocialPostUi {
        val character = authorCharacterId?.let { id -> cast.firstOrNull { it.id == id } }
        val isYou = authorCharacterId == null
        val name = if (isYou) _uiState.value.personaName else authorName
        val reactions = decodeCounts(reactionsJson).toMutableMap()
        if (userReaction.isNotBlank()) reactions[userReaction] = (reactions[userReaction] ?: 0) + 1
        val image = mediaId?.let { id ->
            mediaRepository.getById(id)?.let { mediaRepository.resolveFile(it).absolutePath }
        }
        val seed = name.hashCode().absoluteValue
        return SocialPostUi(
            id = id,
            authorCharacterId = authorCharacterId,
            authorName = name,
            handle = handleFor(name),
            colorHex = avatarColorHexFor(name, character?.colorHex),
            isYou = isYou,
            verified = !isYou && seed % 3 == 0,
            // Older posts were saved before markdown was unwrapped on the way in.
            text = text.replace(Regex("\\*{1,2}([^*\\n]+)\\*{1,2}"), "$1"),
            imagePath = image,
            createdAt = createdAt,
            parentId = parentId,
            likeCount = likeCount + (if (userReaction.isNotBlank()) 1 else 0),
            repostCount = repostCount + (quoteCounts[id] ?: 0) + (if (userReposted) 1 else 0),
            replyCount = replyCounts[id] ?: 0,
            viewCount = viewCount,
            userReaction = userReaction,
            userReposted = userReposted,
            bookmarked = bookmarked,
            topReactions = reactions.entries.sortedByDescending { it.value }
                .mapNotNull { FbReaction.of(it.key) }.take(3),
            feeling = feeling,
            repostOf = repostOf,
        )
    }

    private fun buildNotifications(posts: List<SocialPostEntity>, follows: Set<String>): List<SocialNotificationUi> {
        val mine = posts.filter { it.authorCharacterId == null }.associateBy { it.id }
        val out = mutableListOf<SocialNotificationUi>()
        val verb = "replied to your post"
        posts.forEach { post ->
            if (post.authorCharacterId == null) return@forEach
            val parent = post.parentId?.let { mine[it] }
            val quoted = post.repostOfId?.let { mine[it] }
            val persona = _uiState.value.personaName
            when {
                parent != null -> out += SocialNotificationUi(
                    id = "n-" + post.id,
                    kind = "reply",
                    actorName = post.authorName,
                    actorColorHex = avatarColorHexFor(post.authorName, null),
                    text = "${post.authorName} $verb: \"${post.text.take(80)}\"",
                    postId = parent.id,
                    createdAt = post.createdAt,
                )
                quoted != null -> out += SocialNotificationUi(
                    id = "n-" + post.id,
                    kind = "repost",
                    actorName = post.authorName,
                    actorColorHex = avatarColorHexFor(post.authorName, null),
                    text = "${post.authorName} reshared your post",
                    postId = post.id,
                    createdAt = post.createdAt,
                )
                persona.isNotBlank() && post.text.contains("@" + handleFor(persona), ignoreCase = true) -> out += SocialNotificationUi(
                    id = "n-" + post.id,
                    kind = "mention",
                    actorName = post.authorName,
                    actorColorHex = avatarColorHexFor(post.authorName, null),
                    text = "${post.authorName} mentioned you",
                    postId = post.id,
                    createdAt = post.createdAt,
                )
            }
        }
        // Likes on the writer's own posts, credited to a stable cast member.
        mine.values.filter { it.likeCount > 0 && it.parentId == null }.forEach { post ->
            val actor = cast.getOrNull(post.id.hashCode().absoluteValue % cast.size.coerceAtLeast(1))
            if (actor != null) {
                val others = post.likeCount - 1
                out += SocialNotificationUi(
                    id = "l-" + post.id,
                    kind = "like",
                    actorName = actor.name,
                    actorColorHex = avatarColorHexFor(actor.name, actor.colorHex),
                    text = if (others > 0) {
                        "${actor.name} and $others others reacted to your post"
                    } else {
                        "${actor.name} reacted to your post"
                    },
                    postId = post.id,
                    createdAt = post.createdAt + 60_000,
                )
            }
        }
        cast.filter { it.id in follows }.take(6).forEach { character ->
            out += SocialNotificationUi(
                id = "f-" + character.id,
                kind = "follow",
                actorName = character.name,
                actorColorHex = avatarColorHexFor(character.name, character.colorHex),
                text = "${character.name} followed you back",
                postId = null,
                createdAt = character.updatedAt.takeIf { it > 0 } ?: character.createdAt,
            )
        }
        return out.sortedByDescending { it.createdAt }
    }

    private fun buildTrends(posts: List<SocialPostEntity>): List<Pair<String, Int>> {
        val tags = Regex("#[A-Za-z][A-Za-z0-9_]{1,30}")
        val counts = posts.flatMap { post -> tags.findAll(post.text).map { it.value }.toList() }
            .groupingBy { it }.eachCount()
        return counts.entries.sortedByDescending { it.value }.take(10).map { it.key to it.value * 1_137 + 240 }
    }

    // ------------------------------------------------------------------ actions

    fun requestImagePick() {
        _uiState.update { it.copy(mediaPickRequestId = it.mediaPickRequestId + 1) }
    }

    fun attachImage(uri: android.net.Uri) {
        viewModelScope.launch {
            val media = runCatching { mediaRepository.importFromUris(listOf(uri)) }.getOrNull()?.firstOrNull()
            if (media == null) {
                _uiState.update { it.copy(error = "Could not attach that picture.") }
                return@launch
            }
            pendingMedia = media
            _uiState.update { it.copy(pendingImagePath = mediaRepository.resolveFile(media).absolutePath) }
        }
    }

    fun clearImage() {
        pendingMedia = null
        _uiState.update { it.copy(pendingImagePath = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = "") }
    }

    /** The writer posts; the cast then comments on it. */
    fun post(text: String, feeling: String = "", quoteOf: String? = null) {
        val clean = text.trim()
        val media = pendingMedia
        if (clean.isBlank() && media == null && quoteOf == null) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val post = SocialPostEntity(
                id = "sp-${UUID.randomUUID()}",
                platform = platform,
                authorCharacterId = null,
                authorName = _uiState.value.personaName,
                text = clean,
                mediaId = media?.id,
                repostOfId = quoteOf,
                viewCount = Random.nextInt(12, 240),
                feeling = feeling,
                createdAt = now,
            )
            db.socialDao().upsert(post)
            clearImage()
            generateComments(post)
        }
    }

    /** The writer replies to [parentId] (a top-level post); its author answers back. */
    fun reply(parentId: String, text: String, replyingTo: SocialPostUi? = null) {
        val clean = text.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            val parent = db.socialDao().getPost(parentId) ?: return@launch
            val body = if (replyingTo != null && !replyingTo.isYou && replyingTo.id != parentId) {
                "@${replyingTo.handle} $clean"
            } else {
                clean
            }
            val reply = SocialPostEntity(
                id = "sp-${UUID.randomUUID()}",
                platform = platform,
                authorCharacterId = null,
                authorName = _uiState.value.personaName,
                text = body,
                parentId = parentId,
                viewCount = Random.nextInt(5, 60),
                createdAt = System.currentTimeMillis(),
            )
            db.socialDao().upsert(reply)
            val answerer = replyingTo?.authorCharacterId?.let { id -> cast.firstOrNull { it.id == id } }
                ?: parent.authorCharacterId?.let { id -> cast.firstOrNull { it.id == id } }
            generateThreadReply(parent, body, answerer)
        }
    }

    /** Facebook: set or clear a reaction. Twitter: [reaction] is "like". */
    fun react(postId: String, reaction: String) {
        viewModelScope.launch {
            val post = db.socialDao().getPost(postId) ?: return@launch
            val next = if (post.userReaction == reaction) "" else reaction
            db.socialDao().upsert(post.copy(userReaction = next))
        }
    }

    fun toggleRepost(postId: String) {
        viewModelScope.launch {
            val post = db.socialDao().getPost(postId) ?: return@launch
            db.socialDao().upsert(post.copy(userReposted = !post.userReposted))
        }
    }

    /** Facebook "Share now": a share post on the writer's timeline pointing at the original. */
    fun shareNow(postId: String, caption: String = "") {
        post(caption, quoteOf = postId)
    }

    fun toggleBookmark(postId: String) {
        viewModelScope.launch {
            val post = db.socialDao().getPost(postId) ?: return@launch
            db.socialDao().upsert(post.copy(bookmarked = !post.bookmarked))
        }
    }

    fun delete(postId: String) {
        viewModelScope.launch { db.socialDao().deleteWithReplies(postId) }
    }

    /** Follows made on the old Facebook (friends) and Twitter (follows) screens still count. */
    private fun allFollows() = combine(
        settings.socialFollows(PLATFORM_WEAVERSOCIAL),
        settings.socialFollows(LEGACY_PLATFORMS[0]),
        settings.socialFollows(LEGACY_PLATFORMS[1]),
    ) { a, b, c -> a + b + c }

    fun toggleFollow(characterId: String) {
        viewModelScope.launch {
            if (characterId in allFollows().first()) {
                (LEGACY_PLATFORMS + PLATFORM_WEAVERSOCIAL).forEach { key ->
                    val set = settings.socialFollows(key).first()
                    if (characterId in set) settings.setSocialFollows(key, set - characterId)
                }
            } else {
                val set = settings.socialFollows(PLATFORM_WEAVERSOCIAL).first()
                settings.setSocialFollows(PLATFORM_WEAVERSOCIAL, set + characterId)
            }
        }
    }

    // --------------------------------------------------------------- generation

    /** Refresh / pull-to-refresh: a few new posts from the cast. */
    fun refreshFeed(count: Int = 4) {
        if (_uiState.value.generating) return
        viewModelScope.launch {
            if (!ensureReady()) return@launch
            val posters = pickCast(count.coerceAtMost(cast.size), prefer = _uiState.value.followingIds)
            val recent = entities.values.filter { it.parentId == null }
                .sortedByDescending { it.createdAt }.take(8)
                .joinToString("\n") { "${it.authorName}: ${it.text.take(160)}" }
            val system = buildList {
                add(platformVoice())
                add("The people posting (stay true to each):")
                posters.forEach { add(cardFor(it)) }
                if (recent.isNotBlank()) add("Already on the timeline (do not repeat; people may react to these):\n$recent")
                add(outputRules())
            }
            val user = "Write ${posters.size} new posts, one from each of: " +
                posters.joinToString(", ") { it.name } + "."
            val raw = complete(system, user, maxTokens = 900) ?: return@launch
            val lines = parseSocialLines(raw, cast)
            val base = System.currentTimeMillis()
            lines.forEachIndexed { index, line ->
                val author = line.character ?: return@forEachIndexed
                val (feeling, body) = splitFeeling(line.text)
                if (body.isBlank()) return@forEachIndexed
                db.socialDao().upsert(generatedPost(author, body, feeling, base - (lines.size - index) * 97_000L, parentId = null))
            }
            finish()
        }
    }

    private suspend fun generateComments(post: SocialPostEntity) {
        if (!ensureReady()) return
        val named = matchNamedCharacters(post.text, cast) +
            cast.filter { post.text.contains("@" + handleFor(it.name), ignoreCase = true) }
        val repliers = (named + pickCast(3, prefer = _uiState.value.followingIds)).distinctBy { it.id }.take(4)
        val quoted = post.repostOfId?.let { entities[it] }
        val system = buildList {
            add(platformVoice())
            add("These people are replying to a post by ${_uiState.value.personaName} (the writer). Stay true to each card:")
            repliers.forEach { add(cardFor(it)) }
            add(outputRules(reply = true))
        }
        val user = buildString {
            append("${_uiState.value.personaName} posted: \"${post.text}\"")
            if (post.feeling.isNotBlank()) append(" (feeling ${post.feeling})")
            if (post.mediaId != null) append(" [with a photo]")
            quoted?.let { append("\nThey were sharing ${it.authorName}'s post: \"${it.text.take(200)}\"") }
            append("\nWrite ${repliers.size.coerceIn(1, 4)} replies, ")
            append("one per person, from: ${repliers.joinToString(", ") { it.name }}.")
        }
        val raw = complete(system, user, maxTokens = 600) ?: return
        val base = System.currentTimeMillis()
        val lines = parseSocialLines(raw, cast)
        lines.forEachIndexed { index, line ->
            val author = line.character ?: return@forEachIndexed
            val (_, body) = splitFeeling(line.text)
            if (body.isBlank()) return@forEachIndexed
            db.socialDao().upsert(generatedPost(author, body, "", base + (index + 1) * 41_000L, parentId = post.id, small = true))
        }
        // Everyone else who saw it leaves a like or reaction.
        db.socialDao().getPost(post.id)?.let { fresh ->
            val reactions = randomReactions(lines.size + Random.nextInt(1, 9))
            db.socialDao().upsert(
                fresh.copy(
                    likeCount = reactions.values.sum(),
                    repostCount = Random.nextInt(0, 6),
                    viewCount = fresh.viewCount + Random.nextInt(80, 1_400),
                    reactionsJson = encodeCounts(reactions),
                ),
            )
        }
        finish()
    }

    private suspend fun generateThreadReply(parent: SocialPostEntity, userText: String, answerer: RpCharacterEntity?) {
        if (!ensureReady()) return
        val thread = db.socialDao().getReplies(parent.id).takeLast(10)
            .joinToString("\n") { "${it.authorName}: ${it.text.take(200)}" }
        val speaker = answerer ?: parent.authorCharacterId?.let { id -> cast.firstOrNull { it.id == id } } ?: pickCast(1).firstOrNull()
            ?: return
        val system = listOf(
            platformVoice(),
            "You are replying as ${speaker.name}:",
            cardFor(speaker),
            outputRules(reply = true),
        )
        val user = "Original post by ${parent.authorName}: \"${parent.text.take(300)}\"\n" +
            "Thread so far:\n$thread\n\n${_uiState.value.personaName} just wrote: \"$userText\"\n" +
            "Write ${speaker.name}'s reply."
        val raw = complete(system, user, maxTokens = 300) ?: return
        val line = parseSocialLines(raw, listOf(speaker)).firstOrNull()
            ?: com.ihy2ln.weaverse.feature.chatting.ParsedLine(speaker, speaker.name, raw.substringAfter(':').trim())
        val body = splitFeeling(line.text).second
        if (body.isNotBlank()) {
            db.socialDao().upsert(
                generatedPost(speaker, body, "", System.currentTimeMillis() + 30_000L, parentId = parent.id, small = true),
            )
        }
        finish()
    }

    private suspend fun ensureReady(): Boolean {
        if (cast.isEmpty()) cast = castResolver.allChatContacts()
        if (cast.isEmpty()) {
            _uiState.update { it.copy(error = "No characters yet — add people in the Codex so they can post.") }
            return false
        }
        if (!aiGeneration.hasApiKey(null)) {
            _uiState.update { it.copy(error = AIError.NoApiKey().message.orEmpty()) }
            return false
        }
        _uiState.update {
            it.copy(
                generating = true,
                error = "",
                status = "Weaving new posts…",
            )
        }
        return true
    }

    private fun finish() {
        _uiState.update { it.copy(generating = false, status = "") }
    }

    private suspend fun complete(system: List<String>, user: String, maxTokens: Int): String? =
        runCatching {
            aiGeneration.complete(
                userMessage = user,
                assembled = AssembledPrompt(
                    systemBlocks = system,
                    messages = emptyList(),
                    usedEntries = emptyList(),
                    tokenBreakdown = emptyList(),
                ),
                maxTokens = maxTokens,
                temperature = 0.9,
            ).text
        }.onFailure { err ->
            _uiState.update {
                it.copy(
                    generating = false,
                    status = "",
                    error = err.message?.takeIf { m -> m.isNotBlank() } ?: "Couldn't load posts — check your model and API key.",
                )
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun platformVoice(): String =
        "You write posts for WeaverSocial, one social network shared by fictional people from many " +
            "worlds. It mixes three habits: Twitter's short, punchy takes and live reactions (most posts " +
            "stay under 280 characters, with the odd #hashtag or @handle — handles are names in lowercase " +
            "with no spaces); Facebook's personal life updates, feelings, photos described in words and " +
            "questions to friends (now and then a longer, warmer post); and Discord's in-jokes and " +
            "community chatter about the groups and places people belong to. Everyone stays true to " +
            "their own world. A post may start with [feeling X] (for example [feeling blessed]) when the " +
            "person would set a feeling. Replies are one or two lines. Emoji where the person would use them."

    private fun outputRules(reply: Boolean = false): String = buildString {
        appendLine("Output format, no exceptions:")
        appendLine("- One ${if (reply) "reply" else "post"} per line: \"Name: text\" and nothing else.")
        appendLine("- First person, in each person's own voice, about their own world and life.")
        appendLine("- No narration, no asterisk actions, no markdown, no quotation marks around the text.")
        append("- Never write for ${_uiState.value.personaName}.")
    }

    private fun cardFor(character: RpCharacterEntity): String {
        val about = listOf(character.description, character.personality)
            .filter { it.isNotBlank() }.joinToString(" ").replace('\n', ' ').take(CARD_CHARS)
        return "${character.name} (@${handleFor(character.name)}): ${about.ifBlank { "No card details." }}"
    }

    private fun bioOf(character: RpCharacterEntity): String =
        (character.creatorNotes.takeIf { it.isNotBlank() && it.length < 160 } ?: character.description)
            .replace('\n', ' ').trim().take(BIO_CHARS)

    private fun pickCast(count: Int, prefer: Set<String> = emptySet()): List<RpCharacterEntity> {
        if (cast.isEmpty() || count <= 0) return emptyList()
        val preferred = cast.filter { it.id in prefer }.shuffled()
        val rest = cast.filterNot { it.id in prefer }.shuffled()
        return (preferred + rest).take(count)
    }

    private fun splitFeeling(text: String): Pair<String, String> {
        val trimmed = text.trim().trim('"')
        val match = Regex("^\\[feeling ([^\\]]{1,30})\\]\\s*", RegexOption.IGNORE_CASE).find(trimmed)
        return if (match != null) {
            match.groupValues[1].trim() to trimmed.substring(match.range.last + 1).trim()
        } else {
            "" to trimmed.replace(Regex("^\\[feeling [^\\]]*\\]\\s*", RegexOption.IGNORE_CASE), "")
        }
    }

    private fun generatedPost(
        author: RpCharacterEntity,
        text: String,
        feeling: String,
        createdAt: Long,
        parentId: String?,
        small: Boolean = false,
    ): SocialPostEntity {
        val reach = if (small) 12 else 60 + author.name.hashCode().absoluteValue % 900
        val likes = Random.nextInt(0, reach)
        val reactions = randomReactions(likes)
        return SocialPostEntity(
            id = "sp-${UUID.randomUUID()}",
            platform = platform,
            authorCharacterId = author.id,
            authorName = author.name,
            // Feeds show plain text, so markdown emphasis the model slips in is unwrapped.
            text = text.replace(Regex("\\*{1,2}([^*\\n]+)\\*{1,2}"), "$1")
                .take(POST_CHARS),
            parentId = parentId,
            likeCount = reactions.values.sum(),
            repostCount = likes / Random.nextInt(4, 14),
            viewCount = likes * Random.nextInt(18, 70) + Random.nextInt(10, 200),
            reactionsJson = encodeCounts(reactions),
            feeling = feeling,
            createdAt = createdAt,
        )
    }

    private fun randomReactions(total: Int): Map<String, Int> {
        if (total <= 0) return emptyMap()
        val weights = listOf("like" to 60, "love" to 22, "haha" to 8, "care" to 4, "wow" to 3, "sad" to 2, "angry" to 1)
        val out = mutableMapOf<String, Int>()
        repeat(total) {
            var roll = Random.nextInt(100)
            val pick = weights.first { (_, w) -> (roll - w).also { roll = it } < 0 }.first
            out[pick] = (out[pick] ?: 0) + 1
        }
        return out
    }

    companion object {
        private const val CARD_CHARS = 420
        private const val BIO_CHARS = 160
        const val POST_CHARS = 500
    }
}

private val countsJson = Json { ignoreUnknownKeys = true }

private fun decodeCounts(json: String): Map<String, Int> =
    runCatching { countsJson.decodeFromString<Map<String, Int>>(json.ifBlank { "{}" }) }.getOrDefault(emptyMap())

private fun encodeCounts(map: Map<String, Int>): String = countsJson.encodeToString<Map<String, Int>>(map)

/**
 * Splits "Name: text" output into posts. Unlike the chat parser this accepts any
 * character in a name (quotes, nicknames) and matches against the whole cast, so a
 * poster the model added on its own still gets their own post instead of being glued
 * onto the one before.
 */
internal fun parseSocialLines(raw: String, cast: List<RpCharacterEntity>): List<ParsedLine> {
    fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
    fun find(name: String): RpCharacterEntity? {
        val n = norm(name)
        if (n.isBlank()) return null
        return cast.firstOrNull { norm(it.name) == n }
            ?: cast.firstOrNull { norm(it.name).startsWith(n) || n.startsWith(norm(it.name)) }
            ?: cast.firstOrNull { norm(it.name).split(' ').firstOrNull() == n.split(' ').firstOrNull() }
    }
    val out = mutableListOf<ParsedLine>()
    var current: RpCharacterEntity? = null
    var name = ""
    val text = StringBuilder()
    fun flush() {
        val body = text.toString().trim().trim('"')
        if (body.isNotBlank() && current != null) out += ParsedLine(current, name, body)
        text.clear()
    }
    raw.lines().forEach { rawLine ->
        val line = rawLine.trim().removePrefix("- ").trim()
        val colon = line.indexOf(':')
        val prefix = if (colon in 1..60) line.substring(0, colon).replace("*", "").trim() else ""
        val who = if (prefix.isNotBlank() && prefix.split(' ').size <= 7) find(prefix) else null
        if (who != null) {
            flush()
            current = who
            name = who.name
            text.append(line.substring(colon + 1).replace("**", "").trim())
        } else if (current != null && line.isNotBlank()) {
            text.append('\n').append(line)
        }
    }
    flush()
    return out
}

/** "2m", "3h", "Sep 24" — Twitter's compact timestamps. */
fun compactAge(createdAt: Long, now: Long = System.currentTimeMillis()): String {
    val secs = ((now - createdAt) / 1000).coerceAtLeast(0)
    return when {
        secs < 60 -> "${secs.coerceAtLeast(1)}s"
        secs < 3600 -> "${secs / 60}m"
        secs < 86_400 -> "${secs / 3600}h"
        secs < 7 * 86_400 -> "${secs / 86_400}d"
        else -> java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(java.util.Date(createdAt))
    }
}

/** "Just now", "12m", "3h", "Yesterday at 4:12 PM", "September 24 at 4:12 PM" — Facebook's. */
fun facebookAge(createdAt: Long, now: Long = System.currentTimeMillis()): String {
    val secs = ((now - createdAt) / 1000).coerceAtLeast(0)
    val time = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date(createdAt))
    return when {
        secs < 60 -> "Just now"
        secs < 3600 -> "${secs / 60}m"
        secs < 86_400 -> "${secs / 3600}h"
        secs < 2 * 86_400 -> "Yesterday at $time"
        else -> java.text.SimpleDateFormat("MMMM d", java.util.Locale.getDefault()).format(java.util.Date(createdAt)) + " at $time"
    }
}

/** 1234 → "1.2K", 2_500_000 → "2.5M". */
fun compactCount(n: Int): String = when {
    n >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", n / 1_000_000.0).replace(".0M", "M")
    n >= 10_000 -> "${n / 1000}K"
    n >= 1_000 -> String.format(java.util.Locale.US, "%.1fK", n / 1_000.0).replace(".0K", "K")
    else -> n.toString()
}
