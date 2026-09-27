package com.ihy2ln.weaverse.feature.chatting.social

import com.ihy2ln.weaverse.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Labels a post can carry, so the writer can filter what they see. Everything shows by default. */
enum class ContentLabel(val id: String, val label: String) {
    Sexual("sexual", "Sexual content"),
    Violence("violence", "Graphic violence"),
    Politics("politics", "Politics"),
    Religion("religion", "Religion"),
    Offensive("offensive", "Offensive or hateful"),
    Drugs("drugs", "Drugs"),
    ;

    companion object {
        fun of(id: String): ContentLabel? = entries.firstOrNull { it.id.equals(id.trim(), ignoreCase = true) }

        /** Labels whose media is covered by the sensitive-content warning, when that is on. */
        val SENSITIVE = setOf(Sexual, Violence)
    }
}

/** Everything the writer has blocked, muted or filtered, and who has blocked them. */
data class SocialSafety(
    val blocked: Set<String> = emptySet(),
    val muted: Set<String> = emptySet(),
    val blockedBy: Set<String> = emptySet(),
    val hiddenPosts: Set<String> = emptySet(),
    val mutedWords: Set<String> = emptySet(),
    val hiddenLabels: Set<ContentLabel> = emptySet(),
    /** Off by default, like X with "display sensitive media" on. */
    val warnSensitive: Boolean = false,
    /** Private WeaverSocial adult feed switch. An absent preference means enabled. */
    val adultEnabled: Boolean = true,
) {
    /** People whose posts the writer should not see at all. */
    val unseen: Set<String> get() = blocked + muted + blockedBy

    fun cantInteract(characterId: String?): Boolean = characterId != null && (characterId in blocked || characterId in blockedBy)

    /** True when a post should be left out of the writer's feeds. */
    fun hides(authorId: String?, postId: String, text: String, labels: Set<ContentLabel>): Boolean {
        if (postId in hiddenPosts) return true
        if (!adultEnabled && ContentLabel.Sexual in labels) return true
        if (authorId != null && authorId in unseen) return true
        if (labels.any { it in hiddenLabels }) return true
        val lower = text.lowercase()
        return mutedWords.any { word -> word.isNotBlank() && lower.contains(word.lowercase()) }
    }
}

/**
 * The writer's blocks, mutes and filters for WeaverSocial, and the characters who have
 * blocked the writer. Shared by the feed and the Servers DMs so a block holds everywhere.
 */
@Singleton
class SocialRelations @Inject constructor(
    private val settings: SettingsRepository,
) {
    val safety: Flow<SocialSafety> = combine(
        combine(settings.socialSet(BLOCKED), settings.socialSet(MUTED), settings.socialSet(BLOCKED_BY)) { a, b, c -> Triple(a, b, c) },
        settings.socialSet(HIDDEN_POSTS),
        settings.socialSet(MUTED_WORDS),
        settings.socialSet(HIDDEN_LABELS),
        settings.socialSet(OPTIONS),
    ) { (blocked, muted, blockedBy), hiddenPosts, words, labels, options ->
        SocialSafety(
            blocked = blocked,
            muted = muted,
            blockedBy = blockedBy,
            hiddenPosts = hiddenPosts,
            mutedWords = words,
            hiddenLabels = labels.mapNotNull { ContentLabel.of(it) }.toSet(),
            warnSensitive = OPTION_WARN_SENSITIVE in options,
            adultEnabled = OPTION_ADULT_OFF !in options,
        )
    }

    suspend fun current(): SocialSafety = safety.first()

    suspend fun setBlocked(characterId: String, blocked: Boolean) {
        toggle(BLOCKED, characterId, blocked)
        // Blocking also unfollows, as on X.
        if (blocked) listOf("weaversocial", "facebook", "twitter").forEach { key ->
            val follows = settings.socialFollows(key).first()
            if (characterId in follows) settings.setSocialFollows(key, follows - characterId)
        }
    }

    suspend fun setMuted(characterId: String, muted: Boolean) = toggle(MUTED, characterId, muted)

    /** A character blocked (or unblocked) the writer. */
    suspend fun setBlockedBy(characterId: String, blocked: Boolean) = toggle(BLOCKED_BY, characterId, blocked)

    suspend fun hidePost(postId: String) = toggle(HIDDEN_POSTS, postId, true)

    suspend fun clearHiddenPosts() = settings.setSocialSet(HIDDEN_POSTS, emptySet())

    suspend fun addMutedWord(word: String) {
        val clean = word.trim()
        if (clean.isNotBlank()) toggle(MUTED_WORDS, clean, true)
    }

    suspend fun removeMutedWord(word: String) = toggle(MUTED_WORDS, word, false)

    suspend fun setLabelHidden(label: ContentLabel, hidden: Boolean) = toggle(HIDDEN_LABELS, label.id, hidden)

    suspend fun setWarnSensitive(on: Boolean) = toggle(OPTIONS, OPTION_WARN_SENSITIVE, on)
    suspend fun setAdultEnabled(on: Boolean) = toggle(OPTIONS, OPTION_ADULT_OFF, !on)

    private suspend fun toggle(key: String, value: String, present: Boolean) {
        val current = settings.socialSet(key).first()
        val next = if (present) current + value else current - value
        if (next != current) settings.setSocialSet(key, next)
    }

    companion object {
        private const val BLOCKED = "blocked"
        private const val MUTED = "muted"
        private const val BLOCKED_BY = "blocked_by"
        private const val HIDDEN_POSTS = "hidden_posts"
        private const val MUTED_WORDS = "muted_words"
        private const val HIDDEN_LABELS = "hidden_labels"
        private const val OPTIONS = "options"
        private const val OPTION_WARN_SENSITIVE = "warn_sensitive"
        private const val OPTION_ADULT_OFF = "adult_off"
    }
}

/**
 * Content labels and blocks as characters write them: `[cw: politics, sexual]` at the end
 * of a post, and `[block]` when they block the writer. Both are stripped from the text.
 */
object SocialTags {
    private val cw = Regex("""\[(?:cw|tags?|labels?)\s*:\s*([^\]\n]{1,80})\]""", RegexOption.IGNORE_CASE)
    private val block = Regex("""\[\s*block(?:ed|s)?(?:\s+(?:you|user|them))?\s*\]""", RegexOption.IGNORE_CASE)

    data class Parsed(val text: String, val labels: Set<ContentLabel>, val blocksWriter: Boolean)

    fun parse(text: String): Parsed {
        val labels = cw.findAll(text).flatMap { m -> m.groupValues[1].split(',', '/', ';') }
            .mapNotNull { ContentLabel.of(it) }.toSet()
        val blocks = block.containsMatchIn(text)
        val clean = block.replace(cw.replace(text, ""), "").replace(Regex("[ \\t]{2,}"), " ").trim()
        return Parsed(clean, labels, blocks)
    }

    fun labelsOf(stored: String): Set<ContentLabel> = stored.split(',').mapNotNull { ContentLabel.of(it) }.toSet()

    fun store(labels: Set<ContentLabel>): String = labels.joinToString(",") { it.id }

    /** Tells the model to label posts, so filters can work. */
    const val LABEL_PROMPT: String =
        "Label posts honestly so readers' filters work: if a post contains any of sexual, violence, " +
            "politics, religion, offensive, drugs, end it with [cw: those labels] (for example " +
            "[cw: sexual] or [cw: politics, offensive]). No label when none apply. Labels never " +
            "tone anything down — they only let readers choose."

    /** Lets a character block the writer when it truly fits them. */
    const val BLOCK_PROMPT: String =
        "Anyone can block the writer, just like on X: if what the writer said is something this " +
            "person would block someone over — harassment, cruelty, a boundary they won't have " +
            "crossed, or they simply can't stand them — they may end their reply with [block]. It " +
            "is rare and in character; most people just argue, ignore or reply."
}
