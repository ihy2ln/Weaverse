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
import com.ihy2ln.weaverse.feature.chatting.media.CharacterMediaFetcher
import com.ihy2ln.weaverse.feature.chatting.media.MAX_ATTACHMENTS
import com.ihy2ln.weaverse.feature.chatting.media.MediaTag
import com.ihy2ln.weaverse.feature.chatting.media.MediaTags
import com.ihy2ln.weaverse.feature.chatting.media.SocialContentPolicy
import com.ihy2ln.weaverse.feature.chatting.media.WebSearchKind
import com.ihy2ln.weaverse.feature.chatting.media.WebPicture
import com.ihy2ln.weaverse.feature.chatting.media.WebPictureSearch
import com.ihy2ln.weaverse.feature.chatting.media.KEY_BRAVE
import com.ihy2ln.weaverse.feature.chatting.media.KEY_CIVITAI
import com.ihy2ln.weaverse.feature.chatting.media.KEY_GELBOORU_USER
import com.ihy2ln.weaverse.feature.chatting.media.KEY_GELBOORU_API
import com.ihy2ln.weaverse.feature.chatting.media.KEY_GIPHY
import com.ihy2ln.weaverse.feature.chatting.media.KEY_TENOR
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
    /** First picture, for single-image spots like story cards. */
    val imagePath: String?,
    /** Every picture or GIF on the post, in order (up to four shown). */
    val imagePaths: List<String> = listOfNotNull(imagePath),
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
    /** Content labels the author gave the post (sexual, politics…). */
    val labels: Set<ContentLabel> = emptySet(),
    val sourceUrl: String = "",
    val sourceSite: String = "",
    val sourceTitle: String = "",
    val originKind: String = "fictional",
    val mediaCredits: List<String> = emptyList(),
    val mediaLinks: List<String> = emptyList(),
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
    val blockedByYou: Boolean = false,
    val mutedByYou: Boolean = false,
    /** This person blocked the writer. */
    val blockedYou: Boolean = false,
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
    /** Pictures and GIFs attached to the next post or reply. */
    val pendingImagePaths: List<String> = emptyList(),
    val mediaPickRequestId: Long = 0,
    val castLoaded: Boolean = false,
    /** The saved posts have been read once; until then an empty list doesn't mean an empty feed. */
    val postsLoaded: Boolean = false,
    val safety: SocialSafety = SocialSafety(),
    /** One-off message such as "Mara blocked you", shown as a snackbar. */
    val notice: String = "",
    val mediaNotice: String = "",
    val loadingMediaIds: Set<String> = emptySet(),
    val comfyEndpoint: String = "",
    val comfyWorkflow: String = "",
    val imageModelRef: String = "",
    val comfyStatus: String = "",
    val braveStatus: String = "",
    val civitaiStatus: String = "",
    val gelbooruStatus: String = "",
    val addedCreatorIds: Set<String> = emptySet(),
    /** The finished post is the prompt for its picture or GIF (search words and AI image). */
    val mediaFromPost: Boolean = true,
    /** Made-up people reshare real posts from Mastodon, Bluesky, Lemmy and Hacker News. */
    val webPosts: Boolean = true,
    /** Kinds of real accounts the timeline follows: outlets, creators, communities. */
    val feedCategories: Set<FeedCategory> = FeedCategory.defaults,
    /** The writer's own accounts to follow, one per line (see [CustomFeedSource]). */
    val customFeeds: String = "",
    /** The writer confirmed they're 18+, which adult feed kinds require. */
    val adultFeedsConfirmed: Boolean = false,
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
    private val characterMedia: CharacterMediaFetcher,
    private val relations: SocialRelations,
    private val webPictures: WebPictureSearch,
    private val imageGenerator: SocialImageGenerator,
    private val realWeb: RealWebFeed,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SocialUiState())
    val uiState: StateFlow<SocialUiState> = _uiState.asStateFlow()

    private var platform = ""
    private var cast: List<RpCharacterEntity> = emptyList()
    private var pendingMedia: List<MediaEntity> = emptyList()
    /** The Pictures library, for characters who post photos of themselves. */
    private var library: List<MediaEntity> = emptyList()
    private var entities: Map<String, SocialPostEntity> = emptyMap()
    private var safety: SocialSafety = SocialSafety()
    private val mediaFetchInFlight = mutableSetOf<String>()
    private val claimedMediaUrls = mutableSetOf<String>()
    private var nextTopicDirection = Random.nextInt(SOCIAL_TOPIC_DIRECTIONS.size)
    private var lastWebPull = 0L
    /** Rotates through followed kinds so each one shows up across refreshes. */
    private var nextCategory = 0

    fun bind(platformId: String = PLATFORM_WEAVERSOCIAL) {
        if (platform == platformId) return
        platform = platformId
        _uiState.update { it.copy(platform = platformId) }
        viewModelScope.launch {
            combine(settings.socialString("comfy_endpoint"), settings.socialString("comfy_workflow"),
                settings.socialString("image_model")) { endpoint, workflow, model -> Triple(endpoint, workflow, model) }
                .collect { (endpoint, workflow, model) ->
                    _uiState.update { it.copy(comfyEndpoint = endpoint, comfyWorkflow = workflow, imageModelRef = model) }
                }
        }
        viewModelScope.launch {
            combine(settings.socialString("media_mode"), settings.socialString("web_posts")) { mode, web -> mode to web }
                .collect { (mode, web) ->
                    _uiState.update { it.copy(mediaFromPost = mode != "tags", webPosts = web != "off") }
                }
        }
        viewModelScope.launch {
            combine(settings.socialString("feed_categories"), settings.socialString("custom_feeds"),
                settings.socialString("adult_feeds_confirmed")) { kinds, custom, confirmed -> Triple(kinds, custom, confirmed) }
                .collect { (kinds, custom, confirmed) ->
                    _uiState.update {
                        it.copy(feedCategories = decodeCategories(kinds), customFeeds = custom, adultFeedsConfirmed = confirmed == "yes")
                    }
                }
        }
        viewModelScope.launch {
            val persona = roomSeeder.defaultPersona()
            _uiState.update {
                it.copy(personaName = persona.name.ifBlank { "You" }, personaBio = persona.description)
            }
            cast = castResolver.allChatContacts()
            val added = db.codexDao().getAllEntries().map { it.id }.filter { it.startsWith("social-creator-") }.toSet()
            _uiState.update { it.copy(castLoaded = true, addedCreatorIds = added) }
            combine(db.socialDao().observeAllPosts(), allFollows(), relations.safety) { posts, follows, safety ->
                Triple(posts, follows, safety)
            }.collect { (posts, follows, safety) -> publish(posts, follows, safety) }
        }
    }

    // ---------------------------------------------------------------- rendering

    private suspend fun publish(posts: List<SocialPostEntity>, follows: Set<String>, safety: SocialSafety) {
        entities = posts.associateBy { it.id }
        this.safety = safety
        val replyCounts = posts.filter { it.parentId != null }.groupingBy { it.parentId!! }.eachCount()
        val quoteCounts = posts.filter { it.repostOfId != null }.groupingBy { it.repostOfId!! }.eachCount()
        fun visible(post: SocialPostEntity): Boolean =
            (safety.adultEnabled || (ContentLabel.Sexual !in SocialTags.labelsOf(post.contentTags) &&
                !SEXUAL_TERMS.containsMatchIn(post.text))) &&
                (post.authorCharacterId == null ||
                    !safety.hides(post.authorCharacterId, post.id, post.text, SocialTags.labelsOf(post.contentTags)))
        val shallow = posts.associate { it.id to it.toUi(replyCounts, quoteCounts, null) }
        val full = posts.associate { post ->
            post.id to post.toUi(replyCounts, quoteCounts,
                post.repostOfId?.takeIf { id -> entities[id]?.let(::visible) == true }?.let { shallow[it] })
        }
        // Blocks, mutes, muted words, "not interested" and label filters; the writer's own
        // posts always show.
        val top = posts.filter { it.parentId == null && visible(it) }.mapNotNull { full[it.id] }
        val replies = posts.filter { it.parentId != null && visible(it) }
            .sortedBy { it.createdAt }
            .groupBy { it.parentId!! }
            .mapValues { (_, list) -> list.mapNotNull { full[it.id] } }
        val npcAuthors = posts.mapNotNull { it.authorCharacterId }.filter(SocialNpcs::isNpc).toSet()
        val people = (cast + SocialNpcs.characters.filter { it.id in npcAuthors }).map { character ->
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
                isFollowing = character.id in follows && character.id !in safety.blocked,
                joinedAt = character.createdAt,
                blockedByYou = character.id in safety.blocked,
                mutedByYou = character.id in safety.muted,
                blockedYou = character.id in safety.blockedBy,
            )
        }
        _uiState.update {
            it.copy(
                posts = top,
                postsLoaded = true,
                repliesByParent = replies,
                allById = full.filterKeys { id -> entities[id]?.let(::visible) == true },
                people = people,
                notifications = buildNotifications(posts.filter { visible(it) }, follows),
                safety = safety,
                trends = buildTrends(posts.filter(::visible)),
            )
        }
    }

    private suspend fun SocialPostEntity.toUi(
        replyCounts: Map<String, Int>,
        quoteCounts: Map<String, Int>,
        repostOf: SocialPostUi?,
    ): SocialPostUi {
        val character = authorCharacterId?.let(::personById)
        val isYou = authorCharacterId == null
        val name = if (isYou) _uiState.value.personaName else authorName
        val reactions = decodeCounts(reactionsJson).toMutableMap()
        if (userReaction.isNotBlank()) reactions[userReaction] = (reactions[userReaction] ?: 0) + 1
        val attached = mediaIdsOf(mediaId).mapNotNull { mediaRepository.getById(it) }
        val images = attached.map { mediaRepository.resolveFile(it).absolutePath }
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
            imagePath = images.firstOrNull(),
            imagePaths = images,
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
            labels = SocialTags.labelsOf(contentTags),
            sourceUrl = sourceUrl,
            sourceSite = sourceSite,
            sourceTitle = sourceTitle,
            originKind = originKind,
            mediaCredits = attached.map { media ->
                listOf(media.sourceSite, media.sourceCredit).filter { it.isNotBlank() }.distinct().joinToString(" · ")
            },
            mediaLinks = attached.map { it.sourceUrl },
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

    /** Pictures or GIFs picked on the device, imported into app storage and attached. */
    fun attachFromDevice(uris: List<android.net.Uri>) {
        viewModelScope.launch {
            val imported = runCatching { mediaRepository.importFromUris(uris.take(room())) }.getOrNull().orEmpty()
            if (imported.isEmpty()) {
                _uiState.update { it.copy(error = "Could not attach that picture.") }
                return@launch
            }
            addPending(imported)
        }
    }

    /** Pictures or GIFs picked from the Pictures library. */
    fun attachFromLibrary(mediaIds: List<String>) {
        viewModelScope.launch {
            addPending(mediaIds.take(room()).mapNotNull { mediaRepository.getById(it) })
        }
    }

    fun removeAttachment(index: Int) {
        pendingMedia = pendingMedia.filterIndexed { i, _ -> i != index }
        publishPending()
    }

    fun clearImage() {
        pendingMedia = emptyList()
        publishPending()
    }

    private fun room(): Int = (MAX_ATTACHMENTS - pendingMedia.size).coerceAtLeast(0)

    private fun addPending(media: List<MediaEntity>) {
        pendingMedia = (pendingMedia + media).distinctBy { it.id }.take(MAX_ATTACHMENTS)
        publishPending()
    }

    private fun publishPending() {
        _uiState.update { state ->
            state.copy(pendingImagePaths = pendingMedia.map { mediaRepository.resolveFile(it).absolutePath })
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = "") }
    }

    /** The writer posts; the cast then comments on it. */
    fun post(text: String, feeling: String = "", quoteOf: String? = null) {
        val clean = text.trim()
        val media = pendingMedia
        if (clean.isBlank() && media.isEmpty() && quoteOf == null) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val post = SocialPostEntity(
                id = "sp-${UUID.randomUUID()}",
                platform = platform,
                authorCharacterId = null,
                authorName = _uiState.value.personaName,
                text = clean,
                mediaId = joinMediaIds(media.map { it.id }),
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
        val media = pendingMedia
        if (clean.isBlank() && media.isEmpty()) return
        viewModelScope.launch {
            val parent = db.socialDao().getPost(parentId) ?: return@launch
            val targetCharacterId = replyingTo?.authorCharacterId ?: parent.authorCharacterId
            if (safety.cantInteract(targetCharacterId)) {
                _uiState.update { it.copy(notice = blockNotice(targetCharacterId!!)) }
                return@launch
            }
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
                mediaId = joinMediaIds(media.map { it.id }),
                parentId = parentId,
                viewCount = Random.nextInt(5, 60),
                createdAt = System.currentTimeMillis(),
            )
            db.socialDao().upsert(reply)
            clearImage()
            val answerer = replyingTo?.authorCharacterId?.let(::personById)
                ?: parent.authorCharacterId?.let(::personById)
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
        if (safety.cantInteract(characterId)) {
            _uiState.update { it.copy(notice = blockNotice(characterId)) }
            return
        }
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

    // ------------------------------------------------------------ safety

    fun block(characterId: String, blocked: Boolean) {
        viewModelScope.launch { relations.setBlocked(characterId, blocked) }
    }

    fun mute(characterId: String, muted: Boolean) {
        viewModelScope.launch { relations.setMuted(characterId, muted) }
    }

    /** "Not interested in this post". */
    fun hidePost(postId: String) {
        viewModelScope.launch { relations.hidePost(postId) }
    }

    fun restoreHiddenPosts() {
        viewModelScope.launch { relations.clearHiddenPosts() }
    }

    fun addMutedWord(word: String) {
        viewModelScope.launch { relations.addMutedWord(word) }
    }

    fun removeMutedWord(word: String) {
        viewModelScope.launch { relations.removeMutedWord(word) }
    }

    fun setLabelHidden(label: ContentLabel, hidden: Boolean) {
        viewModelScope.launch { relations.setLabelHidden(label, hidden) }
    }

    fun setWarnSensitive(on: Boolean) {
        viewModelScope.launch { relations.setWarnSensitive(on) }
    }

    fun setAdultEnabled(on: Boolean) {
        viewModelScope.launch { relations.setAdultEnabled(on) }
    }

    fun braveKey(): String = webPictures.key(KEY_BRAVE)
    fun setBraveKey(value: String) = webPictures.setKey(KEY_BRAVE, value)
    fun civitaiKey(): String = webPictures.key(KEY_CIVITAI)
    fun setCivitaiKey(value: String) = webPictures.setKey(KEY_CIVITAI, value)
    fun gelbooruUserId(): String = webPictures.key(KEY_GELBOORU_USER)
    fun setGelbooruUserId(value: String) = webPictures.setKey(KEY_GELBOORU_USER, value)
    fun gelbooruApiKey(): String = webPictures.key(KEY_GELBOORU_API)
    fun setGelbooruApiKey(value: String) = webPictures.setKey(KEY_GELBOORU_API, value)
    fun giphyKey(): String = webPictures.key(KEY_GIPHY)
    fun setGiphyKey(value: String) = webPictures.setKey(KEY_GIPHY, value)
    fun tenorKey(): String = webPictures.key(KEY_TENOR)
    fun setTenorKey(value: String) = webPictures.setKey(KEY_TENOR, value)

    fun setMediaFromPost(on: Boolean) {
        viewModelScope.launch { settings.setSocialString("media_mode", if (on) "" else "tags") }
    }

    fun setWebPosts(on: Boolean) {
        viewModelScope.launch { settings.setSocialString("web_posts", if (on) "" else "off") }
    }

    /** Turns one kind of followed account on or off. Adult kinds need [confirmAdultFeeds] first. */
    fun setFeedCategory(category: FeedCategory, on: Boolean) {
        if (on && category.adult && !_uiState.value.adultFeedsConfirmed) return
        val next = if (on) _uiState.value.feedCategories + category else _uiState.value.feedCategories - category
        viewModelScope.launch { settings.setSocialString("feed_categories", encodeCategories(next)) }
    }

    /** The writer confirmed they're 18 or older; adult kinds can now be followed. */
    fun confirmAdultFeeds(category: FeedCategory?) {
        viewModelScope.launch {
            settings.setSocialString("adult_feeds_confirmed", "yes")
            category?.let { settings.setSocialString("feed_categories", encodeCategories(_uiState.value.feedCategories + it)) }
        }
    }

    fun setCustomFeeds(text: String) {
        viewModelScope.launch {
            settings.setSocialString("custom_feeds", text)
            val count = CustomFeedSource.parseAll(text).size
            _uiState.update { it.copy(mediaNotice = "$count account${if (count == 1) "" else "s"} saved.") }
        }
    }

    private fun encodeCategories(set: Set<FeedCategory>): String =
        set.joinToString(",") { it.id }.ifBlank { NO_CATEGORIES }

    private fun decodeCategories(value: String): Set<FeedCategory> = when (value) {
        "" -> FeedCategory.defaults
        NO_CATEGORIES -> emptySet()
        else -> value.split(',').mapNotNull { FeedCategory.byId(it.trim()) }.toSet()
    }

    /**
     * Opening WeaverSocial pulls new real posts from the accounts the writer follows, like
     * any social app does on launch. Skipped when the last pull was moments ago.
     */
    fun onOpened() {
        val state = _uiState.value
        if (!state.webPosts || state.generating) return
        if (System.currentTimeMillis() - lastWebPull < WEB_PULL_COOLDOWN_MS) return
        pullWebPosts(5)
    }

    /** Fetches and reshares real posts without writing new cast posts (also works with no API key). */
    private fun pullWebPosts(count: Int) {
        if (_uiState.value.generating) return
        _uiState.update { it.copy(generating = true) }
        viewModelScope.launch {
            try {
                shareWebPosts(count)
            } finally {
                finish()
            }
        }
    }

    fun saveMediaSettings(endpoint: String, workflow: String, imageModel: String) {
        viewModelScope.launch {
            settings.setSocialString("comfy_endpoint", endpoint)
            settings.setSocialString("comfy_workflow", workflow)
            settings.setSocialString("image_model", imageModel)
            _uiState.update { it.copy(mediaNotice = "Media settings saved.") }
        }
    }

    fun checkComfy() {
        viewModelScope.launch { _uiState.update { it.copy(comfyStatus = imageGenerator.comfyStatus()) } }
    }

    fun checkBrave() {
        viewModelScope.launch { _uiState.update { it.copy(braveStatus = webPictures.braveStatus()) } }
    }

    fun checkCivitai() {
        viewModelScope.launch {
            _uiState.update { it.copy(civitaiStatus = webPictures.civitaiStatus(safety.adultEnabled)) }
        }
    }

    fun checkGelbooru() {
        viewModelScope.launch {
            _uiState.update { it.copy(gelbooruStatus = webPictures.gelbooruStatus(safety.adultEnabled)) }
        }
    }

    fun addFictionalCreator(template: SocialCreatorTemplate) {
        viewModelScope.launch {
            val created = SocialCreatorTemplates.addToCodex(db, template)
            cast = castResolver.allChatContacts()
            publish(db.socialDao().observeAllPosts().first(), allFollows().first(), safety)
            _uiState.update { it.copy(
                addedCreatorIds = it.addedCreatorIds + SocialCreatorTemplates.id(template.slug),
                notice = if (created) "${template.name} joined your Codex and social cast." else "${template.name} is already in your Codex.",
            ) }
        }
    }

    /** Author's override: lift a character's block on the writer. */
    fun liftBlockOnYou(characterId: String) {
        viewModelScope.launch { relations.setBlockedBy(characterId, false) }
    }

    fun dismissNotice() {
        _uiState.update { it.copy(notice = "") }
    }

    fun dismissMediaNotice() {
        _uiState.update { it.copy(mediaNotice = "") }
    }

    private fun nameOf(characterId: String): String = personById(characterId)?.name ?: "They"

    /** A cast member, or one of the made-up everyday people. */
    private fun personById(id: String): RpCharacterEntity? =
        cast.firstOrNull { it.id == id } ?: SocialNpcs.characters.firstOrNull { it.id == id }

    private fun blockNotice(characterId: String): String =
        if (characterId in safety.blockedBy) "${nameOf(characterId)} blocked you." else "You blocked ${nameOf(characterId)}. Unblock them first."

    /** A character decided to block the writer mid-reply. */
    private suspend fun blockedByCharacter(characterId: String) {
        relations.setBlockedBy(characterId, true)
        _uiState.update { it.copy(notice = "${nameOf(characterId)} blocked you.") }
    }

    // --------------------------------------------------------------- generation

    /** Refresh / pull-to-refresh: a few new posts from the cast. */
    fun refreshFeed(count: Int = 4) {
        if (_uiState.value.generating) return
        viewModelScope.launch {
            cast = castResolver.allChatContacts()
            if (!ensureReady()) {
                // No cast or no API key: the followed accounts can still post.
                if (_uiState.value.webPosts) pullWebPosts((count * 3 / 4).coerceIn(3, 6))
                return@launch
            }
            val adultAllowed = safety.adultEnabled
            val posters = pickCast(count.coerceAtMost(cast.size), prefer = _uiState.value.followingIds)
            val history = db.socialDao().observeAllPosts().first()
                .filter { it.parentId == null && it.authorCharacterId != null }
                .take(300)
            val batchText = mutableListOf<String>()
            val topicDirections = List(posters.size) { index ->
                val direction = SOCIAL_TOPIC_DIRECTIONS[(nextTopicDirection++).mod(SOCIAL_TOPIC_DIRECTIONS.size)]
                val assigned = if ((!adultAllowed || UNDERAGE_TERMS.containsMatchIn(
                    listOf(posters[index].description, posters[index].personality).joinToString(" "))) &&
                    direction.startsWith("[18+]"))
                    "A game, pet, trip, hobby, or ordinary moment from today's life." else direction
                assigned + if (index % 4 == 2 || (assigned.startsWith("[18+]") && index != 0))
                    " This is a reaction to or reshare of public media by someone else; do not say it depicts you."
                else ""
            }
            val recent = history.take(20)
                .joinToString("\n") { "${it.authorName}: ${it.text.take(160)}" }
            val formats = listOf("first-person anecdote about a new event", "opinion or hot take with a concrete reason", "short question to followers", "specific update with a number or detail", "reaction to another event or post", "small discovery or recommendation")
            val system = buildList {
                add(platformVoice())
                add("Make WeaverSocial feel like a busy social feed. Follow each assigned direction and keep every person in character. " +
                    (if (mediaFromPost) "Most posts come with a photo or GIF, and the post text itself is used to find or create it: say plainly in the post what the picture or clip shows (the subject, place or moment — \"look at this storm rolling over the harbor\", \"my cat mid-zoomies\"). Gaming clips, market charts, pets, travel scenes, everyday photos, sports, cars, technology and fitness all work. "
                    else "Use a specific [pic: …] or [gif: …] tag on nearly every post; include gaming screenshots or GIFs, market charts, pets, travel scenes, everyday photos, sports, cars, technology and fitness as appropriate. Mix GIFs and photos; occasionally suggest two attachments. ") +
                    "Let characters disagree and have distinct opinions, including politics when natural. ${if (adultAllowed) "[18+] directions are for clearly adult fictional people and may be sexually explicit. Adult creator and subscription-style teasers are welcome. Label every such post [cw: sexual]. Never depict minors or imply a real person is this fictional character." else "Keep this feed free of sexual content and do not write adult media queries."}")
                add("The people posting (stay true to each). Give each person their assigned direction as a natural part of their own life; it is a topic nudge, not a forced ad or a change to their personality:")
                posters.forEachIndexed { index, character ->
                    add(cardFor(character))
                    add("Post direction for ${character.name}: ${topicDirections[index]}.")
                    add("Format for ${character.name}: ${formats[(nextTopicDirection + index).mod(formats.size)]}. Give a new event, detail, or angle.")
                    history.filter { it.authorCharacterId == character.id }.take(5).forEach { add("Earlier ${character.name} post to avoid echoing: ${it.text.take(200)}") }
                }
                if (recent.isNotBlank()) add("Already on the timeline (do not repeat; people may react to these):\n$recent")
                add(outputRules())
            }
            val user = "Write ${posters.size} new posts, one from each of: " +
                posters.joinToString(", ") { it.name } +
                ". Follow each assigned direction, vary the formats and viewpoints, and " +
                (if (mediaFromPost) "make each post name what its picture or GIF shows." else "make image/GIF queries specific to the scene or subject in the post.")
            val raw = complete(system, user, maxTokens = 3_000) ?: return@launch
            val lines = parseSocialLines(raw, cast)
            val characterPhotoIndex = lines.indexOfFirst { line ->
                SELF_PHOTO_TERMS.containsMatchIn(line.text)
            }.takeIf { it >= 0 } ?: 0
            val base = System.currentTimeMillis()
            library = runCatching { mediaRepository.observeAll().first() }.getOrDefault(emptyList())
                .filter { it.type == "image" }
            var gifAssigned = false
            val planned = mutableListOf<PlannedMedia>()
            lines.forEachIndexed { index, line ->
                val author = line.character ?: return@forEachIndexed
                val checked = reviewIfRepeated(line.text, author, history.map { it.text } + batchText) ?: return@forEachIndexed
                batchText += checked
                val (feeling, tagged) = splitFeeling(checked)
                val social = SocialTags.parse(tagged)
                val (body, tags) = MediaTags.extract(social.text)
                if (body.isBlank() && tags.isEmpty()) return@forEachIndexed
                val directedAdult = topicDirections.getOrNull(posters.indexOfFirst { it.id == author.id })?.startsWith("[18+]") == true
                val labels = social.labels + if (directedAdult) setOf(ContentLabel.Sexual) else emptySet()
                if (!adultAllowed && (ContentLabel.Sexual in labels || SEXUAL_TERMS.containsMatchIn(body))) return@forEachIndexed
                // Prefer a character's own saved picture when available. Otherwise search
                // the web for a relevant attachment, so media does not depend on the model
                // remembering to emit a tag or the user having a populated local library.
                val selfPhoto = SELF_PHOTO_TERMS.containsMatchIn(body) || tags.any { SELF_PHOTO_TERMS.containsMatchIn(it.query) }
                val personalPhoto = if (selfPhoto || (tags.isEmpty() && Random.nextFloat() < PERSONAL_PHOTO_CHANCE))
                    pictureOf(author) else null
                val mediaTags = tags.ifEmpty {
                    if (personalPhoto != null) emptyList()
                    else listOfNotNull(automaticMediaTag(body, labels, AUTO_MEDIA_POST_CHANCE))
                }.let { selected ->
                    // A public stranger's face cannot stand in for this fictional person's selfie.
                    if (selfPhoto && personalPhoto == null) return@let emptyList()
                    val withAppearance = if (ContentLabel.Sexual in labels) selected.map { tag ->
                        val keywords = appearanceSearchWords(author)
                        if (keywords.isBlank()) tag else tag.copy(query = (tag.query.take(55) + " " + keywords).take(80))
                    } else selected
                    if (!mediaFromPost && !gifAssigned && index != characterPhotoIndex && withAppearance.isNotEmpty() &&
                        ContentLabel.Sexual !in labels) {
                        gifAssigned = true
                        listOf(withAppearance.first().copy(kind = WebSearchKind.Gifs)) + withAppearance.drop(1)
                    } else withAppearance
                }
                val post = generatedPost(author, body, feeling, base - (lines.size - index) * 97_000L, parentId = null)
                    .copy(mediaId = personalPhoto?.id, contentTags = SocialTags.store(labels))
                val publicReshare = (index % 4 == 2 || (ContentLabel.Sexual in labels && index != 0)) && personalPhoto == null
                if (mediaFromPost) {
                    // Text first; the picture is chosen from the finished post below.
                    db.socialDao().upsert(post)
                    if (personalPhoto == null) planned += PlannedMedia(post, author, body, labels, selfPhoto,
                        characterPhoto = index == characterPhotoIndex, publicReshare = publicReshare)
                    return@forEachIndexed
                }
                saveWithMedia(
                    post,
                    mediaTags,
                    publicReshare = publicReshare,
                    generatePrompt = if (index == characterPhotoIndex && personalPhoto == null &&
                        !UNDERAGE_TERMS.containsMatchIn(author.description + " " + author.personality)) {
                        "Original social-media photograph of the same fictional adult character. " +
                            "Preserve the Codex identity: face, skin tone, hair, eye color, body build, height, and distinguishing features. " +
                            "Character appearance and context: ${author.description.take(1000)}. " +
                            "Post: ${body.take(300)}. ${if (adultAllowed) "All people are clearly 18+." else "Nonsexual image."} No real person's likeness, no text overlays."
                    } else null,
                )
            }
            if (planned.isNotEmpty()) attachPlannedMedia(planned)
            if (_uiState.value.webPosts) shareWebPosts((count * 3 / 4).coerceIn(2, 6))
            finish()
        }
    }

    // ---------------------------------------------------- post-as-prompt media

    private class PlannedMedia(
        val post: SocialPostEntity,
        val author: RpCharacterEntity,
        val body: String,
        val labels: Set<ContentLabel>,
        val selfPhoto: Boolean,
        val characterPhoto: Boolean,
        val publicReshare: Boolean,
    )

    /** What a post's attachment should be: kind (null = none), search words, and a scene for an image model. */
    private data class MediaPlan(val kind: WebSearchKind?, val query: String, val scene: String)

    /**
     * Uses each finished post as the prompt for its media: the model reads the post and
     * names what its picture or GIF shows, which becomes both the web search and the AI
     * image prompt. The picture follows the words instead of a guess made alongside them.
     */
    private suspend fun attachPlannedMedia(planned: List<PlannedMedia>) {
        _uiState.update { it.copy(status = "Matching pictures to posts…") }
        val plans = planMediaFromPosts(planned)
        var generations = if (imageGenerator.isConfigured()) GENERATED_PER_REFRESH else 0
        planned.forEachIndexed { index, item ->
            val plan = plans[index]
            val kind = plan.kind ?: return@forEachIndexed
            val fresh = db.socialDao().getPost(item.post.id) ?: return@forEachIndexed
            val adultOnly = UNDERAGE_TERMS.containsMatchIn(item.author.description + " " + item.author.personality)
            val showsAuthor = item.selfPhoto || item.characterPhoto
            val prompt = if (kind != WebSearchKind.Gifs && generations > 0 && !adultOnly &&
                (showsAuthor || !item.publicReshare)) {
                generations--
                buildString {
                    append("Candid social-media photograph. It shows: ${plan.scene}. ")
                    append("It illustrates this post by ${item.author.name}: \"${item.body.take(300)}\". ")
                    if (showsAuthor) append("The person in the photo is ${item.author.name}; keep their Codex look — face, skin tone, hair, eyes, build, distinguishing features: ${item.author.description.take(700)}. ")
                    append(if (safety.adultEnabled) "Everyone shown is clearly 18+. " else "Nonsexual image. ")
                    append("No text overlays, no real person's likeness.")
                }
            } else null
            // A stranger's face from the web can't stand in for this character's selfie.
            if (item.selfPhoto && prompt == null) return@forEachIndexed
            val query = if (ContentLabel.Sexual in item.labels) {
                (plan.query.take(55) + " " + appearanceSearchWords(item.author)).trim().take(80)
            } else plan.query
            saveWithMedia(fresh, listOf(MediaTag(kind, query)),
                publicReshare = item.publicReshare && prompt == null, generatePrompt = prompt)
        }
    }

    private suspend fun planMediaFromPosts(items: List<PlannedMedia>): List<MediaPlan> {
        val fallback = items.map { item ->
            automaticMediaTag(item.body, item.labels, 1f)?.let { MediaPlan(it.kind, it.query, item.body) }
                ?: MediaPlan(null, "", "")
        }
        val raw = runCatching {
            aiGeneration.complete(
                userMessage = "Posts:\n" + items.withIndex().joinToString("\n") { (i, item) ->
                    "${i + 1}. ${item.author.name}: ${item.body.replace('\n', ' ').take(400)}" +
                        if (item.selfPhoto || item.characterPhoto) " [a photo of ${item.author.name} themself]" else ""
                },
                assembled = AssembledPrompt(
                    systemBlocks = listOf(MEDIA_PLANNER_PROMPT + if (safety.adultEnabled)
                        " Adult posts may get explicit adult search words." else " Keep every search nonsexual."),
                    messages = emptyList(), usedEntries = emptyList(), tokenBreakdown = emptyList(),
                ),
                maxTokens = 1_200,
                temperature = 0.4,
            ).text
        }.getOrNull().orEmpty()
        val line = Regex("^\\s*(\\d+)\\s*[|.):]\\s*(gif|photo|picture|meme|none)\\s*\\|\\s*([^|]*)\\|?\\s*(.*)$", RegexOption.IGNORE_CASE)
        val parsed = raw.lines().mapNotNull { line.find(it.trim().removePrefix("- ")) }.associate { m ->
            val kind = when (m.groupValues[2].lowercase()) {
                "gif" -> WebSearchKind.Gifs
                "meme" -> WebSearchKind.Memes
                "none" -> null
                else -> WebSearchKind.Pictures
            }
            m.groupValues[1].toInt() - 1 to MediaPlan(kind, m.groupValues[3].trim().trim('"').take(80), m.groupValues[4].trim())
        }
        return items.indices.map { i ->
            parsed[i]?.takeIf { it.kind == null || it.query.isNotBlank() }
                ?.let { it.copy(scene = it.scene.ifBlank { items[i].body }) } ?: fallback[i]
        }
    }

    // --------------------------------------------------------- around the web

    /**
     * Made-up everyday people reshare real public posts — with their picture or GIF and a
     * link back — then others argue and joke underneath, the way a real timeline fills up.
     */
    private suspend fun shareWebPosts(count: Int) {
        _uiState.update { it.copy(status = "Finding posts around the web…") }
        lastWebPull = System.currentTimeMillis()
        val seen = entities.values.map { it.sourceUrl }.filter { it.isNotBlank() }.toSet()
        // Read straight from settings: on open this can run before the UI state has them.
        val adultFeeds = safety.adultEnabled && settings.socialString("adult_feeds_confirmed").first() == "yes"
        // Followed kinds and the writer's own accounts come first, like a real timeline.
        val followed = decodeCategories(settings.socialString("feed_categories").first())
            .filter { adultFeeds || !it.adult }.sortedBy { it.ordinal }
        val picked = List(minOf(CATEGORIES_PER_PULL, followed.size)) { followed[(nextCategory++).mod(followed.size)] }.distinct()
        val custom = CustomFeedSource.parseAll(settings.socialString("custom_feeds").first())
        val fromFollowed = if (picked.isEmpty() && custom.isEmpty()) emptyList()
        else runCatching { realWeb.fetchFollowed(picked, custom, adultFeeds, seen) }.getOrDefault(emptyList())
        // Plus the odd meme or pet picture from the wider web.
        val topics = (listOf("memes", "pets", "games") + RealWebFeed.TOPIC_HASHTAGS.keys.shuffled().take(3)).shuffled()
            .take(if (fromFollowed.isEmpty()) 3 else 1)
        val found = runCatching { realWeb.fetch(topics, safety.adultEnabled, seen) }.getOrDefault(emptyList())
        // Mostly posts with a picture or GIF, the odd headline.
        val wide = (found.filter { it.media != null }.take(count - 1) + found.filter { it.media == null }.take(1) +
            found.filter { it.media != null }.drop(count - 1))
        val wideShare = if (fromFollowed.isEmpty()) count else 1
        val items = (fromFollowed.take(count - wideShare) + wide.take(wideShare)).let { chosen ->
            chosen + (fromFollowed + wide).filterNot { it in chosen }.take(count - chosen.size)
        }.take(count).shuffled()
        if (items.isEmpty()) return
        val used = mutableSetOf<String>()
        val pairs = items.mapNotNull { item ->
            // Adult posts only go to the adults who follow adult accounts.
            val topic = if (item.adult) SocialNpcs.ADULT_TOPIC else item.topic
            val pool = SocialNpcs.forTopic(topic).filterNot { it.id in safety.unseen || it.id in used }
                .ifEmpty { if (item.adult) emptyList() else SocialNpcs.characters.filterNot { it.id in safety.unseen || it.id in used } }
            pool.randomOrNull()?.also { used += it.id }?.let { item to it }
        }
        if (pairs.isEmpty()) return
        val canWrite = aiGeneration.hasApiKey(null)
        val system = buildList {
            add(platformVoice())
            add("Ordinary people on WeaverSocial are resharing real posts they found on news sites, YouTube, Reddit, 9GAG, Mastodon, Bluesky and other sites. " +
                "Each writes their own short caption: a reaction, joke, opinion or why they're sharing it. " +
                "Never copy or summarise the original, and never claim they made it. Stay true to each card:")
            pairs.forEach { add(cardFor(it.second)) }
            if (pairs.any { it.first.adult }) add("Posts marked [18+] are adult content from adult creators and communities. " +
                "Those captions may be flirty, thirsty or explicit, the way people react on adult social media. Everyone involved is an adult; " +
                "never suggest anyone is under 18. Label each such caption [cw: sexual].")
            add(outputRules())
        }
        val user = "Write one caption per person:\n" + pairs.joinToString("\n") { (item, npc) ->
            "${npc.name} is sharing this ${item.site} post${if (item.adult) " [18+]" else ""}: \"${item.text.take(300).replace('\n', ' ')}\"" +
                when { item.media?.isGif == true -> " [with a GIF]"; item.media != null -> " [with a picture]"; else -> "" }
        }
        // Without a model the reshare still lands, with a quick caption like people really post.
        val raw = if (canWrite) complete(system, user, maxTokens = 1_500).orEmpty() else ""
        val captions = parseSocialLines(raw, pairs.map { it.second })
            .mapNotNull { line -> line.character?.let { it.id to line.text } }.toMap()
        val base = System.currentTimeMillis()
        val created = pairs.mapIndexed { index, (item, npc) ->
            val caption = captions[npc.id]?.let { MediaTags.extract(SocialTags.parse(splitFeeling(it).second).text).first }
                ?.takeIf { it.isNotBlank() } ?: QUICK_CAPTIONS.random()
            generatedPost(npc, caption, "", base - index * 53_000L - 20_000L, parentId = null).copy(
                originKind = "web_share",
                sourceUrl = item.url,
                sourceSite = listOf(item.site, item.credit).filter { it.isNotBlank() }.joinToString(" · "),
                sourceTitle = item.text,
                contentTags = SocialTags.store(if (item.adult) setOf(ContentLabel.Sexual) else emptySet()),
            ).also { post ->
                db.socialDao().upsert(post)
                item.media?.let { attachShared(post.id, it, item.text) }
            }
        }
        if (canWrite) generateWebReplies(created.shuffled().take(3))
    }

    private fun attachShared(postId: String, picture: WebPicture, text: String) {
        if (!mediaFetchInFlight.add(postId)) return
        _uiState.update { it.copy(loadingMediaIds = it.loadingMediaIds + postId) }
        viewModelScope.launch {
            try {
                val media = characterMedia.downloadShared(picture, text) ?: return@launch
                val fresh = db.socialDao().getPost(postId) ?: return@launch
                db.socialDao().upsert(fresh.copy(mediaId = joinMediaIds((mediaIdsOf(fresh.mediaId) + media.id).distinct()),
                    sourceMediaUrl = picture.fullUrl))
            } finally {
                mediaFetchInFlight.remove(postId)
                _uiState.update { it.copy(loadingMediaIds = it.loadingMediaIds - postId) }
            }
        }
    }

    /** One call writes a couple of replies under each shared post, from the cast and everyday people. */
    private suspend fun generateWebReplies(posts: List<SocialPostEntity>) {
        if (posts.isEmpty()) return
        val pool = (pickCast(3, prefer = _uiState.value.followingIds) +
            SocialNpcs.characters.filterNot { it.id in safety.unseen }.shuffled().take(4)).distinctBy { it.id }
        val system = buildList {
            add(platformVoice())
            add("People reply under posts on WeaverSocial. Stay true to each card:")
            pool.forEach { add(cardFor(it)) }
            add("Output format, no exceptions: one reply per line as \"N | Name: reply\", where N is the post's number. " +
                "One or two replies per post, from different people and never its own author. Replies agree, argue, joke, " +
                "add a fact or just react, in one or two lines; a [gif: …] tag may end a reaction. No narration or markdown.")
        }
        val user = posts.withIndex().joinToString("\n") { (i, post) ->
            "${i + 1}. ${post.authorName}: ${post.text.take(200)} (sharing ${post.sourceSite}: \"${post.sourceTitle.take(200).replace('\n', ' ')}\")"
        }
        val raw = complete(system, user, maxTokens = 1_500) ?: return
        val numbered = Regex("^\\s*(\\d+)\\s*[|.)]\\s*(.+)$")
        raw.lines().forEachIndexed { i, text ->
            val m = numbered.find(text) ?: return@forEachIndexed
            val post = posts.getOrNull(m.groupValues[1].toInt() - 1) ?: return@forEachIndexed
            val line = parseSocialLines(m.groupValues[2], pool).firstOrNull() ?: return@forEachIndexed
            val author = line.character ?: return@forEachIndexed
            if (author.id == post.authorCharacterId) return@forEachIndexed
            val social = SocialTags.parse(splitFeeling(line.text).second)
            val (body, tags) = MediaTags.extract(social.text)
            if (body.isBlank() && tags.isEmpty()) return@forEachIndexed
            saveWithMedia(
                generatedPost(author, body, "", post.createdAt + (i + 1) * 37_000L, parentId = post.id, small = true)
                    .copy(contentTags = SocialTags.store(social.labels)),
                tags,
            )
        }
    }

    private suspend fun generateComments(post: SocialPostEntity) {
        if (!ensureReady()) return
        val named = matchNamedCharacters(post.text, cast) +
            cast.filter { post.text.contains("@" + handleFor(it.name), ignoreCase = true) }
        val strangers = SocialNpcs.characters.filterNot { it.id in safety.unseen }.shuffled().take(1)
        val repliers = (named.filterNot { it.id in safety.unseen } + pickCast(2, prefer = _uiState.value.followingIds) + strangers)
            .distinctBy { it.id }.take(4)
        val quoted = post.repostOfId?.let { entities[it] }
        val system = buildList {
            add(platformVoice())
            add("These people are replying to a post by ${_uiState.value.personaName} (the writer). Stay true to each card:")
            add(SocialTags.BLOCK_PROMPT)
            repliers.forEach { add(cardFor(it)) }
            add(outputRules(reply = true))
        }
        val user = buildString {
            append("${_uiState.value.personaName} posted: \"${post.text}\"")
            if (post.feeling.isNotBlank()) append(" (feeling ${post.feeling})")
            mediaIdsOf(post.mediaId).size.takeIf { it > 0 }?.let { n ->
                append(if (n == 1) " [with a photo]" else " [with $n photos]")
            }
            quoted?.let { append("\nThey were sharing ${it.authorName}'s post: \"${it.text.take(200)}\"") }
            append("\nWrite ${repliers.size.coerceIn(1, 4)} replies, ")
            append("one per person, from: ${repliers.joinToString(", ") { it.name }}.")
        }
        val raw = complete(system, user, maxTokens = 2_000) ?: return
        val base = System.currentTimeMillis()
        val lines = parseSocialLines(raw, cast + strangers)
        val replyHistory = db.socialDao().getReplies(post.id).filter { it.authorCharacterId != null }.takeLast(60).map { it.text }.toMutableList()
        lines.forEachIndexed { index, line ->
            val author = line.character ?: return@forEachIndexed
            val checked = reviewIfRepeated(line.text, author, replyHistory, reply = true) ?: return@forEachIndexed
            replyHistory += checked
            val social = SocialTags.parse(splitFeeling(checked).second)
            val (body, tags) = MediaTags.extract(social.text)
            if (social.blocksWriter) blockedByCharacter(author.id)
            if (body.isBlank() && tags.isEmpty()) return@forEachIndexed
            val mediaTags = tags.ifEmpty {
                listOfNotNull(automaticMediaTag(body, social.labels, AUTO_REPLY_MEDIA_CHANCE))
            }
            saveWithMedia(
                generatedPost(author, body, "", base + (index + 1) * 41_000L, parentId = post.id, small = true)
                    .copy(contentTags = SocialTags.store(social.labels)),
                mediaTags,
            )
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
        val speaker = answerer ?: parent.authorCharacterId?.let(::personById) ?: pickCast(1).firstOrNull()
            ?: return
        val system = listOf(
            platformVoice(),
            "You are replying as ${speaker.name}:",
            SocialTags.BLOCK_PROMPT,
            cardFor(speaker),
            outputRules(reply = true),
        )
        val user = "Original post by ${parent.authorName}: \"${parent.text.take(300)}\"\n" +
            "Thread so far:\n$thread\n\n${_uiState.value.personaName} just wrote: \"$userText\"\n" +
            "Write ${speaker.name}'s reply."
        val raw = complete(system, user, maxTokens = 1_200) ?: return
        val line = parseSocialLines(raw, listOf(speaker)).firstOrNull()
            ?: com.ihy2ln.weaverse.feature.chatting.ParsedLine(speaker, speaker.name, raw.substringAfter(':').trim())
        val replyHistory = db.socialDao().getReplies(parent.id).filter { it.authorCharacterId != null }.takeLast(60).map { it.text }
        val checked = reviewIfRepeated(line.text, speaker, replyHistory, reply = true)
        if (checked == null) { finish(); return }
        val social = SocialTags.parse(splitFeeling(checked).second)
        val (body, tags) = MediaTags.extract(social.text)
        if (body.isNotBlank() || tags.isNotEmpty()) {
            val mediaTags = tags.ifEmpty {
                listOfNotNull(automaticMediaTag(body, social.labels, AUTO_REPLY_MEDIA_CHANCE))
            }
            saveWithMedia(
                generatedPost(speaker, body, "", System.currentTimeMillis() + 30_000L, parentId = parent.id, small = true)
                    .copy(contentTags = SocialTags.store(social.labels)),
                mediaTags,
            )
        }
        if (social.blocksWriter) blockedByCharacter(speaker.id)
        finish()
    }

    /**
     * Saves a character's post straight away, then fetches the memes or GIFs it asked for
     * and attaches them when they arrive, so text never waits on a download.
     */
    private suspend fun saveWithMedia(
        post: SocialPostEntity, tags: List<MediaTag>, publicReshare: Boolean = false, generatePrompt: String? = null,
    ) {
        db.socialDao().upsert(post)
        if ((tags.isEmpty() && generatePrompt == null) || !mediaFetchInFlight.add(post.id)) return
        _uiState.update { it.copy(loadingMediaIds = it.loadingMediaIds + post.id) }
        viewModelScope.launch {
            try {
                val adultAllowed = safety.adultEnabled
                val adultTopic = adultAllowed && (ContentLabel.Sexual in SocialTags.labelsOf(post.contentTags) ||
                    SEXUAL_TERMS.containsMatchIn(post.text))
                val used = entities.values.flatMap { listOf(it.sourceMediaUrl, it.sourceUrl) }
                    .filter { it.isNotBlank() }.toSet() + claimedMediaUrls
                val source = if (publicReshare && tags.isNotEmpty())
                    characterMedia.fetchPublic(tags.first(), adultAllowed, used, post.text, adultTopic) else null
                if (source != null && !claimedMediaUrls.add(source.second.fullUrl)) {
                    _uiState.update { it.copy(mediaNotice = "A media source repeated; trying new posts on the next refresh.") }
                    return@launch
                }
                source?.second?.pageUrl?.takeIf { it.isNotBlank() }?.let { claimedMediaUrls.add(it) }
                val generated = if (source == null && generatePrompt != null)
                    imageGenerator.generate(generatePrompt,
                        cast.firstOrNull { it.id == post.authorCharacterId }?.avatarMediaId) else null
                val uniqueGenerated = generated?.takeIf { characterMedia.claimLocalForSocial(it) }
                val found = when {
                    source != null -> listOf(source.first)
                    uniqueGenerated != null -> listOf(uniqueGenerated)
                    else -> tags.mapNotNull { characterMedia.fetch(it, adultAllowed, used, post.text, adultTopic) }
                }
                found.mapNotNull { it.sourceUrl.takeIf(String::isNotBlank) }.forEach { claimedMediaUrls.add(it) }
                if (found.isEmpty()) {
                    _uiState.update { it.copy(mediaNotice = if (generatePrompt != null && tags.isEmpty())
                        "No matching character photo was available. Set an image-capable OpenRouter model or ComfyUI workflow in Media sources & AI images."
                    else if (adultTopic && webPictures.key(KEY_BRAVE).isBlank())
                        "No matching adult media was available from the free public galleries. Try another topic or configure your existing image model in Media sources."
                    else "No media with a clear match was found for this post.") }
                    return@launch
                }
                val fresh = db.socialDao().getPost(post.id) ?: return@launch
                val ids = (mediaIdsOf(fresh.mediaId) + found.map { it.id }).distinct().take(MAX_ATTACHMENTS)
                val hasAdultMedia = found.any { "source_adult" in it.tags }
                db.socialDao().upsert(fresh.copy(mediaId = joinMediaIds(ids),
                    contentTags = if (hasAdultMedia) SocialTags.store(SocialTags.labelsOf(fresh.contentTags) + ContentLabel.Sexual) else fresh.contentTags,
                    originKind = if (source?.second?.videoPreview == true) "public_video" else if (source != null) "public_reshare" else fresh.originKind,
                    sourceUrl = source?.second?.pageUrl.orEmpty(), sourceSite = source?.second?.credit.orEmpty().ifBlank { source?.second?.source.orEmpty() },
                    sourceTitle = source?.second?.title.orEmpty(), sourceMediaUrl = source?.second?.fullUrl.orEmpty()))
                _uiState.update { it.copy(mediaNotice = "") }
            } finally {
                mediaFetchInFlight.remove(post.id)
                _uiState.update { it.copy(loadingMediaIds = it.loadingMediaIds - post.id) }
            }
        }
    }

    private suspend fun reviewIfRepeated(text: String, author: RpCharacterEntity, history: List<String>, reply: Boolean = false): String? {
        val similar = SocialRepeatGuard.repeated(text, history, reply) ?: return text
        val rewritten = runCatching {
            aiGeneration.complete(
                userMessage = "Rewrite this ${if (reply) "reply" else "post"} for ${author.name} with a genuinely new event, point, or story detail. Preserve the character and any applicable [cw:] label. Output only one line as Name: text.\nDraft: $text\nToo similar to: $similar",
                assembled = AssembledPrompt(
                    systemBlocks = listOf(platformVoice(), cardFor(author), outputRules(reply)),
                    messages = emptyList(), usedEntries = emptyList(), tokenBreakdown = emptyList(),
                ),
                maxTokens = 500, temperature = 0.95,
            ).text
        }.getOrNull()?.let { parseSocialLines(it, listOf(author)).firstOrNull()?.text ?: it.substringAfter(':').trim() }
        val safeRewrite = rewritten?.let { candidate ->
            val before = SocialTags.parse(text)
            val after = SocialTags.parse(candidate)
            val missing = before.labels - after.labels
            candidate + (if (missing.isNotEmpty()) " [cw: ${missing.joinToString(",") { it.id }}]" else "") +
                (if (before.blocksWriter && !after.blocksWriter) " [block]" else "")
        }
        return safeRewrite?.takeIf { it.isNotBlank() && SocialRepeatGuard.repeated(it, history, reply) == null }
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
        }.getOrNull()?.let { text ->
            // Reasoning models can spend the whole budget thinking and return nothing; the
            // spinner must still stop, and the writer should know why nothing appeared.
            if (text.isBlank()) {
                _uiState.update {
                    it.copy(
                        generating = false,
                        status = "",
                        error = "The model returned no text (it may have spent its budget thinking). Try again, or pick a different model.",
                    )
                }
                null
            } else {
                text
            }
        }

    private fun platformVoice(): String =
        "You write posts for WeaverSocial, one social network shared by fictional people from many " +
            "worlds. It mixes three habits: Twitter's short, punchy takes and live reactions (most posts " +
            "stay under 280 characters, with the odd #hashtag or @handle — handles are names in lowercase " +
            "with no spaces); Facebook's personal life updates, feelings, photos described in words and " +
            "questions to friends (now and then a longer, warmer post); and Discord's in-jokes and " +
            "community chatter about the groups and places people belong to. Everyone stays true to " +
            "their own world. A post may start with [feeling X] (for example [feeling blessed]) when the " +
            "person would set a feeling. Replies are one or two lines. Emoji where the person would use them.\n\n" +
            (if (mediaFromPost) "Pictures and GIFs are chosen from what the post says, so a post that comes with one " +
                "names what it shows. Replies may end with a [gif: search words] reaction tag.\n\n"
            else "Attach a specific relevant [pic: search words] or [gif: search words] tag to most posts. " +
                "Up to two tags may be used for a multi-picture post; tags are removed from visible text.\n\n") +
            SocialTags.LABEL_PROMPT + "\n\n" +
            if (safety.adultEnabled) "This is an adults-only fictional social feed. Posts may contain nudity and explicit sexuality between clearly adult fictional people; label those posts [cw: sexual]. Never sexualize minors or imply a real public person is a fictional character." else
                "WeaverSocial 18+ is off: no sexual posts, nudity, adult creator promotions, or sexual media searches. Other topics and candid opinions are welcome."

    private fun outputRules(reply: Boolean = false): String = buildString {
        appendLine("Output format, no exceptions:")
        appendLine("- One ${if (reply) "reply" else "post"} per line: \"Name: text\" and nothing else.")
        appendLine("- First person, in each person's own voice, about their own world and life.")
        appendLine("- No narration, no asterisk actions, no markdown, no quotation marks around the text.")
        appendLine("- A [gif: …], [meme: …] or [pic: …] tag, a [cw: …] label and [block] may end a line; they are read, not shown.")
        append("- Never write for ${_uiState.value.personaName}.")
    }

    private val mediaFromPost: Boolean get() = _uiState.value.mediaFromPost

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
        // Blocked, muted and blocked-you people don't turn up in the writer's feed.
        val pool = cast.filterNot { it.id in safety.unseen }
        val preferred = pool.filter { it.id in prefer }.shuffled()
        val rest = pool.filterNot { it.id in prefer }.shuffled()
        return (preferred + rest).take(count)
    }

    /**
     * A picture from the library that names this person in its title, tags or category,
     * so a character only ever posts pictures that are actually of them or theirs.
     */
    private suspend fun pictureOf(character: RpCharacterEntity): MediaEntity? {
        val full = character.name.trim().lowercase()
        return library.filter { media ->
            val hay = listOf(media.displayName, media.tags, media.category).joinToString(" ").lowercase()
            media.sourceUrl.isBlank() && hay.contains(full)
        }.shuffled().firstOrNull { characterMedia.claimLocalForSocial(it) }
    }

    private fun appearanceSearchWords(character: RpCharacterEntity): String =
        APPEARANCE_TERMS.findAll(character.description.lowercase()).map { it.value }.distinct().take(6).joinToString(" ")

    /** Supplies an attachment query when a generated post has no explicit media tag. */
    private fun automaticMediaTag(text: String, labels: Set<ContentLabel>, chance: Float): MediaTag? {
        if (text.isBlank() || Random.nextFloat() >= chance) return null
        val words = text.lowercase()
            .replace(Regex("https?://\\S+|@[a-z0-9_]+|#[a-z0-9_]+"), " ")
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 4 && it !in MEDIA_QUERY_STOP_WORDS }
            .distinct()
            .take(6)
            .joinToString(" ")
            .take(64)
        val political = ContentLabel.Politics in labels || POLITICAL_TERMS.containsMatchIn(text)
        val kindAndPrefix = when {
            political -> WebSearchKind.Pictures to "political editorial illustration"
            ContentLabel.Sexual in labels -> WebSearchKind.Pictures to "adult creator nude erotic photo clearly 18+"
            GAMING_TERMS.containsMatchIn(text) -> WebSearchKind.Pictures to "video game gaming screenshot"
            INVESTMENT_TERMS.containsMatchIn(text) -> WebSearchKind.Pictures to "investing stock market finance chart"
            ANIMAL_TERMS.containsMatchIn(text) -> WebSearchKind.Pictures to "animal pet wildlife photo"
            TRAVEL_TERMS.containsMatchIn(text) -> WebSearchKind.Pictures to "vacation travel destination photo"
            GUY_HOBBY_TERMS.containsMatchIn(text) -> WebSearchKind.Pictures to "sports cars technology fitness tools photo"
            ADULT_LIFE_TERMS.containsMatchIn(text) -> WebSearchKind.Pictures to "adult dating nightlife fashion editorial"
            DAILY_LIFE_TERMS.containsMatchIn(text) -> WebSearchKind.Pictures to "everyday lifestyle candid photo"
            else -> when (Random.nextInt(100)) {
                in 0..27 -> WebSearchKind.Gifs to "reaction GIF"
                in 28..47 -> WebSearchKind.Memes to "meme"
                else -> WebSearchKind.Pictures to "editorial photo illustration"
            }
        }
        val query = listOf(kindAndPrefix.second, words).filter { it.isNotBlank() }.joinToString(" ")
        return MediaTag(kindAndPrefix.first, query)
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
        /** How often a character's post comes with one of their pictures, when they have any. */
        private const val PERSONAL_PHOTO_CHANCE = 0.2f
        /** Applied when the model did not provide its own media query. */
        private const val AUTO_MEDIA_POST_CHANCE = 1f
        private const val AUTO_REPLY_MEDIA_CHANCE = 0.28f
        /** Original AI pictures per refresh when an image model is set; the rest come from the web. */
        private const val GENERATED_PER_REFRESH = 2
        private const val MEDIA_PLANNER_PROMPT =
            "You choose the picture or GIF for each social media post. The post text is the prompt: " +
                "the attachment must show exactly what that post is about, never something loosely related.\n" +
                "Answer with one line per post, nothing else: N | kind | search | scene\n" +
                "- kind: gif for reactions, feelings, jokes, motion, sports or gameplay moments (about a third of posts); " +
                "photo for things, places, food, pets, events and people; meme for meme-format jokes; " +
                "none only when nothing visual fits.\n" +
                "- search: 2 to 5 concrete English words naming the visible subject, taken from the post " +
                "(\"golden retriever beach\", \"cat knocking over glass\", \"rainy tokyo street night\"). " +
                "No fictional names, no filler like photo, image, picture or aesthetic.\n" +
                "- scene: one sentence describing exactly what the picture shows, for an image generator."
        /** Weighted, rotating prompts keep the feed's requested interests recurring across refreshes. */
        private val SOCIAL_TOPIC_DIRECTIONS = listOf(
            "[18+] An adult creator teaser, candid attraction, or explicit adult-only photo post, true to this character.",
            "Video games, gaming culture, a current session, screenshot, or gameplay GIF.",
            "Investing, markets, portfolio talk, or a character's considered take on money and risk.",
            "[18+] Adult romance, sexuality, nightlife, or a subscription-style creator preview.",
            "Pets, animals, wildlife, or a funny or tender animal encounter.",
            "A vacation, travel plan, memorable destination, or a discovery from the road.",
            "Sports, cars, tools, technology, fitness, or another hands-on hobby from their own perspective.",
            "[18+] An adult-only personal photo, desire, intimate humor, or creator-style post.",
            "Video games, esports, hardware, game releases, or a memorable in-game moment.",
            "An ordinary day-to-day moment, routine, meal, errand, hobby, or small personal win.",
            "[18+] Adult dating, sexuality, or a public creator-style teaser; clearly fictional and in character.",
            "Animals, travel, politics, investing, or daily life, whichever this person would actually share today.",
        )
        private val SEXUAL_TERMS = Regex("\\b(nude|nudity|porn|xxx|onlyfans|fansly|nsfw|erotic|sex|sexual)\\b", RegexOption.IGNORE_CASE)
        private val SELF_PHOTO_TERMS = Regex("\\b(selfie|photo of me|picture of me|here i am|this is me|my outfit|my body|my face|mirror pic|me on vacation|me at the|my vacation photo)\\b", RegexOption.IGNORE_CASE)
        private const val NO_CATEGORIES = "-"
        private const val CATEGORIES_PER_PULL = 4
        private const val WEB_PULL_COOLDOWN_MS = 2 * 60_000L
        private val QUICK_CAPTIONS = listOf("👀", "this", "ok this is good", "saw this and thought of you all", "well then", "lmao",
            "can't stop thinking about this", "thoughts?", "big if true", "😭", "obsessed", "had to share")
        private val UNDERAGE_TERMS =Regex("\\b(?:1[0-7][ -]?year[ -]?old|teenager|underage|minor|child|schoolgirl|schoolboy)\\b", RegexOption.IGNORE_CASE)
        private val APPEARANCE_TERMS = Regex("\\b(?:blonde|brunette|redhead|black hair|brown hair|blue eyes|green eyes|brown eyes|freckles|tattooed|curvy|athletic|muscular|petite|tall|dark skin|fair skin|elf|orc)\\b", RegexOption.IGNORE_CASE)
        private val GAMING_TERMS = Regex("\\b(game|games|gaming|gamer|esports|xbox|playstation|nintendo|steam|console|pc build)\\b", RegexOption.IGNORE_CASE)
        private val INVESTMENT_TERMS = Regex("\\b(invest|investing|investment|stocks?|shares|market|portfolio|crypto|bitcoin|finance|budget|trading)\\b", RegexOption.IGNORE_CASE)
        private val ANIMAL_TERMS = Regex("\\b(animal|animals|pet|pets|dog|dogs|cat|cats|puppy|kitten|wildlife|horse|bird|birds)\\b", RegexOption.IGNORE_CASE)
        private val TRAVEL_TERMS = Regex("\\b(vacation|travel|trip|traveling|travelling|hotel|beach|flight|destination|tourism|roadtrip)\\b", RegexOption.IGNORE_CASE)
        private val GUY_HOBBY_TERMS = Regex("\\b(sports?|football|basketball|cars?|garage|tools?|technology|gadgets?|fitness|gym|workout|motorcycle|racing)\\b", RegexOption.IGNORE_CASE)
        private val ADULT_LIFE_TERMS = Regex("\\b(dating|date night|nightlife|romance|relationship|relationships|attraction|adult humor|flirting)\\b", RegexOption.IGNORE_CASE)
        private val DAILY_LIFE_TERMS = Regex("\\b(day|today|morning|dinner|lunch|breakfast|work|errand|routine|friends|family|hobby|weekend)\\b", RegexOption.IGNORE_CASE)
        private val POLITICAL_TERMS = Regex(
            "\\b(election|government|parliament|president|congress|policy|politic|lawmakers?|legislation|" +
                "vote|voting|campaign|protest|democracy|rights|war|climate|economy|taxes|union)\\w*\\b",
            RegexOption.IGNORE_CASE,
        )
        private val MEDIA_QUERY_STOP_WORDS = setOf(
            "about", "after", "again", "also", "been", "being", "could", "from", "have", "here",
            "just", "more", "most", "much", "over", "said", "some", "than", "that", "their",
            "them", "then", "there", "these", "they", "this", "those", "very", "what", "when",
            "where", "which", "while", "will", "with", "would", "your",
        )
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

/** Several pictures share the one `mediaId` column, comma-separated, so no schema change is needed. */
internal fun mediaIdsOf(mediaId: String?): List<String> =
    mediaId.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }

internal fun joinMediaIds(ids: List<String>): String? = ids.takeIf { it.isNotEmpty() }?.joinToString(",")

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
