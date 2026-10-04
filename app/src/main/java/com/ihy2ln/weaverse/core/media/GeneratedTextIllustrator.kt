package com.ihy2ln.weaverse.core.media

import android.content.Context
import com.ihy2ln.weaverse.core.text.Block
import com.ihy2ln.weaverse.core.text.MediaBlock
import com.ihy2ln.weaverse.core.text.MediaKind
import com.ihy2ln.weaverse.core.text.Paragraph
import com.ihy2ln.weaverse.core.text.Span
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Something a generated passage can show: a Codex character or place (its picture), or a building
 * from the bundled art. [words] are what the prose may call it; [priority] breaks ties (people first).
 */
data class IllustrationSubject(
    val key: String,
    val words: List<String>,
    val mediaId: String,
    val caption: String,
    val priority: Int = 0,
)

/** Where one picture goes: after paragraph [afterParagraph] (-1 = above the passage). */
data class IllustrationPick(val subject: IllustrationSubject, val afterParagraph: Int)

/** The passage's paragraphs: blank-line separated, or line by line when it has no blank lines. */
fun splitIntoParagraphs(text: String): List<String> {
    val trimmed = text.trim()
    if (trimmed.isBlank()) return emptyList()
    val byBlank = trimmed.split(Regex("\\n\\s*\\n")).map(String::trim).filter(String::isNotBlank)
    return if (byBlank.size > 1) byBlank else trimmed.lines().map(String::trim).filter(String::isNotBlank)
}

/** Whether [word] appears in [text] as a whole word or phrase (any case, plural allowed). */
fun mentions(text: String, word: String): Boolean {
    val w = word.trim()
    if (w.length < 2) return false
    return Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(w) + "(?:s|es|'s)?(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
        .containsMatchIn(text)
}

/**
 * Which pictures a passage gets and where: each subject goes after the first paragraph that names
 * it, so a picture sits above, between or below the prose wherever the thing appears. A subject only
 * the writer's prompt named (the prose never says it) goes above the passage. At most [max], one per
 * picture, in reading order.
 */
fun pickIllustrations(
    paragraphs: List<String>,
    prompt: String,
    subjects: List<IllustrationSubject>,
    max: Int = 3,
): List<IllustrationPick> {
    val picks = subjects.mapNotNull { subject ->
        val inProse = paragraphs.indexOfFirst { p -> subject.words.any { mentions(p, it) } }
        when {
            inProse >= 0 -> IllustrationPick(subject, inProse)
            subject.words.any { mentions(prompt, it) } -> IllustrationPick(subject, -1)
            else -> null
        }
    }
    return picks
        .sortedWith(compareBy<IllustrationPick> { it.afterParagraph }.thenByDescending { it.subject.priority })
        .distinctBy { it.subject.mediaId }
        .distinctBy { it.subject.key }
        .take(max)
}

/** The passage as blocks: its paragraphs with each picked picture in place. */
fun illustratedBlocks(text: String, picks: List<IllustrationPick>, pageId: String? = null): List<Block> {
    val paragraphs = splitIntoParagraphs(text)
    fun media(pick: IllustrationPick) = MediaBlock(
        id = "illus-${UUID.randomUUID()}",
        mediaId = pick.subject.mediaId,
        kind = MediaKind.Image,
        widthPercent = 70f,
        caption = listOf(Span(pick.subject.caption)),
        pageId = pageId,
    )
    val blocks = mutableListOf<Block>()
    picks.filter { it.afterParagraph < 0 }.forEach { blocks += media(it) }
    paragraphs.forEachIndexed { i, p ->
        blocks += Paragraph("p-${UUID.randomUUID()}", listOf(Span(p)))
        picks.filter { it.afterParagraph == i }.forEach { blocks += media(it) }
    }
    return blocks
}

@Serializable
private data class BuildingArtCatalog(val buildings: List<BuildingArt> = emptyList())

@Serializable
private data class BuildingArt(
    val id: String,
    val name: String,
    val category: String = "",
    val words: List<String> = emptyList(),
    val tiers: Map<String, String> = emptyMap(),
)

/** Words that pick a building's grand top tier, or its humble first one; otherwise the middle tier. */
private val GRAND = listOf("grand", "great", "legendary", "celestial", "majestic", "massive", "upgraded", "magnificent", "palatial")
private val HUMBLE = listOf("small", "little", "tiny", "rundown", "run-down", "ramshackle", "crude", "simple", "humble", "old", "shabby")

/** The tier [text] suggests for a building: top for grand words, first for humble ones, else the middle. */
fun buildingTierFor(text: String, tiers: List<String>): String? {
    if (tiers.isEmpty()) return null
    return when {
        GRAND.any { mentions(text, it) } -> tiers.last()
        HUMBLE.any { mentions(text, it) } -> tiers.first()
        else -> tiers[tiers.size / 2]
    }
}

/**
 * Illustrates AI-generated Novel and RPG prose: Codex characters and places it names get their
 * Codex picture, and buildings it names ("summons a shack") get the bundled building art, each
 * placed after the paragraph that first mentions it.
 */
@Singleton
class GeneratedTextIllustrator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: WeaverseDatabase,
    private val mediaRepository: MediaRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var buildings: List<BuildingArt>? = null

    /**
     * [text] split into paragraphs with pictures in place; null when nothing it names has a picture
     * (callers then keep their usual single-paragraph insert).
     */
    suspend fun illustrate(text: String, prompt: String = "", pageId: String? = null, max: Int = 3): List<Block>? =
        withContext(Dispatchers.IO) {
            if (text.isBlank()) return@withContext null
            val subjects = codexSubjects() + buildingSubjects("$prompt\n$text")
            val picks = pickIllustrations(splitIntoParagraphs(text), prompt, subjects, max)
            if (picks.isEmpty()) null else illustratedBlocks(text, picks, pageId)
        }

    private suspend fun codexSubjects(): List<IllustrationSubject> {
        val categories = db.codexDao().getAllCategories().associateBy({ it.id }, { it.name.lowercase() })
        return db.codexDao().getAllEntries().filter { !it.disabled && it.name.length >= 2 }.mapNotNull { entry ->
            val mediaId = CodexMediaIds.parse(entry.imageMediaId).firstOrNull() ?: return@mapNotNull null
            val media = db.mediaDao().getById(mediaId)?.takeIf { it.type == "image" } ?: return@mapNotNull null
            if (!mediaRepository.resolveFile(media).let { it.isFile && it.length() > 0 }) return@mapNotNull null
            val aliases = runCatching { json.decodeFromString<List<String>>(entry.aliasesJson) }.getOrDefault(emptyList())
            val isCharacter = categories[entry.categoryId].orEmpty().contains("character")
            IllustrationSubject(
                key = "codex:${entry.id}",
                words = (listOf(entry.name) + aliases).filter { it.length >= 3 },
                mediaId = mediaId,
                caption = entry.name,
                priority = if (isCharacter) 2 else 1,
            )
        }
    }

    private suspend fun buildingSubjects(text: String): List<IllustrationSubject> = loadBuildings().mapNotNull { b ->
        if (b.words.none { mentions(text, it) }) return@mapNotNull null
        val tier = buildingTierFor(text, b.tiers.keys.toList()) ?: return@mapNotNull null
        val path = b.tiers[tier] ?: return@mapNotNull null
        val file = path.substringAfterLast('/')
        val mediaId = runCatching {
            mediaRepository.registerBundledImage(
                assetPath = path,
                id = "adams-haven-building-${file.substringBeforeLast('.')}",
                relativePath = "images/adams_haven/buildings/$file",
                displayName = "${b.name} ($tier)",
                category = "Adams Haven / Scene / Buildings",
                tags = (listOf("building", "scene:${b.id}", b.category) + b.words).filter(String::isNotBlank).joinToString(","),
            ).id
        }.getOrNull() ?: return@mapNotNull null
        IllustrationSubject(key = "building:${b.id}", words = b.words, mediaId = mediaId, caption = b.name, priority = 0)
    }

    private fun loadBuildings(): List<BuildingArt> = buildings ?: runCatching {
        json.decodeFromString(BuildingArtCatalog.serializer(), context.assets.open(CATALOG).use { it.readBytes().decodeToString() }).buildings
    }.getOrDefault(emptyList()).also { buildings = it }

    companion object {
        const val CATALOG = "images/adams_haven/buildings/catalog.json"
    }
}
