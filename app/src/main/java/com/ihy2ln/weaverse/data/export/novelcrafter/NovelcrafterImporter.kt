package com.ihy2ln.weaverse.data.export.novelcrafter

import com.ihy2ln.weaverse.ai.prompt.DefaultAiGuides
import com.ihy2ln.weaverse.core.media.MediaRepository
import com.ihy2ln.weaverse.core.text.Document
import com.ihy2ln.weaverse.core.text.MediaBlock
import com.ihy2ln.weaverse.core.text.MediaKind
import com.ihy2ln.weaverse.core.text.Paragraph
import com.ihy2ln.weaverse.core.text.Span
import com.ihy2ln.weaverse.core.text.plainText
import com.ihy2ln.weaverse.core.text.toJson
import com.ihy2ln.weaverse.core.text.wordCount
import com.ihy2ln.weaverse.core.ui.theme.CodexCategoryColors
import com.ihy2ln.weaverse.core.ui.theme.toHexString
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.ActEntity
import com.ihy2ln.weaverse.data.db.entities.BookEntity
import com.ihy2ln.weaverse.data.db.entities.ChapterEntity
import com.ihy2ln.weaverse.data.db.entities.ChatMessageEntity
import com.ihy2ln.weaverse.data.db.entities.ChatThreadEntity
import com.ihy2ln.weaverse.data.db.entities.CodexCategoryEntity
import com.ihy2ln.weaverse.data.db.entities.CodexEntryEntity
import com.ihy2ln.weaverse.data.db.entities.RpCharacterEntity
import com.ihy2ln.weaverse.data.db.entities.RpChatEntity
import com.ihy2ln.weaverse.data.db.entities.RpMessageEntity
import com.ihy2ln.weaverse.data.db.entities.RpPersonaEntity
import com.ihy2ln.weaverse.data.db.entities.SceneEntity
import com.ihy2ln.weaverse.data.db.entities.SeriesEntity
import com.ihy2ln.weaverse.data.db.entities.SnippetEntity
import com.ihy2ln.weaverse.sync.novelcrafter.ImportArt
import com.ihy2ln.weaverse.sync.novelcrafter.NovelcrafterCategories
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class NovelcrafterImportResult(
    val bookId: String,
    val bookTitle: String,
    val actCount: Int,
    val chapterCount: Int,
    val sceneCount: Int,
    val codexCount: Int,
    val chatCount: Int,
    val snippetCount: Int,
    val rpCharacterCount: Int = 0,
    val rpChatCount: Int = 0,
    val mediaCount: Int = 0,
)

/**
 * Maps a parsed Novelcrafter export into Room as a **new** book/series.
 *
 * Overwrite rules:
 * - Always creates new series + book IDs (`nc-<uuid>`).
 * - Codex / chat / snippet IDs use `nc-` + Novelcrafter id (upsert-safe if re-imported with same NC ids
 *   under a *different* book only when NC ids collide globally — rare; re-import creates a parallel book).
 * - Never mutates an existing Weaverse book in place.
 */
@Singleton
class NovelcrafterImporter @Inject constructor(
    private val db: WeaverseDatabase,
    private val mediaRepository: MediaRepository,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val folderToCategory = NovelcrafterCategories.folderToCategory

    suspend fun import(parsed: NovelcrafterParsedExport): NovelcrafterImportResult {
        val now = System.currentTimeMillis()
        val importKey = UUID.randomUUID().toString().take(8)
        val seriesId = "nc-series-$importKey"
        val bookId = "nc-book-$importKey"

        db.seriesDao().upsert(
            SeriesEntity(
                id = seriesId,
                title = parsed.bookTitle,
                description = if (parsed.author.isNotBlank()) "Imported from Novelcrafter · by ${parsed.author}" else "Imported from Novelcrafter",
                premise = "",
                createdAt = now,
            ),
        )
        db.bookDao().upsert(
            BookEntity(
                id = bookId,
                seriesId = seriesId,
                title = parsed.bookTitle,
                genre = "",
                styleGuide = if (parsed.author.isNotBlank()) "Author: ${parsed.author}" else "",
                createdAt = now,
                updatedAt = now,
            ),
        )

        // Reuse the shared Codex categories so every book and mode sees one library.
        val existingCats = db.codexDao().getAllCategories()
        val categoryIds = linkedMapOf<String, String>()
        folderToCategory.values.distinctBy { it.first }.forEach { (name, colorIndex) ->
            val found = existingCats.firstOrNull {
                it.name.equals(name, ignoreCase = true) &&
                    it.scopeId == com.ihy2ln.weaverse.data.repo.CodexScopes.ID
            }
            val catId = found?.id ?: "nc-cat-$importKey-$colorIndex"
            categoryIds[name] = catId
            if (found == null) {
                db.codexDao().upsertCategory(
                    CodexCategoryEntity(
                        id = catId,
                        scopeType = com.ihy2ln.weaverse.data.repo.CodexScopes.TYPE,
                        scopeId = com.ihy2ln.weaverse.data.repo.CodexScopes.ID,
                        name = name,
                        colorHex = CodexCategoryColors[colorIndex % CodexCategoryColors.size].toHexString(),
                        sortOrder = colorIndex,
                        isSystem = true,
                        isBuiltIn = true,
                    ),
                )
            }
        }

        var actCount = 0
        var chapterCount = 0
        var sceneCount = 0
        val acts = parsed.acts.ifEmpty {
            listOf(
                NcAct(
                    title = "Act 1",
                    chapters = listOf(
                        NcChapter(
                            title = "Chapter 1",
                            scenes = listOf(NcScene(title = "Scene 1", prose = "")),
                        ),
                    ),
                ),
            )
        }
        acts.forEachIndexed { actIndex, act ->
            val actId = "nc-act-$importKey-$actIndex"
            db.manuscriptDao().upsertAct(ActEntity(actId, bookId, act.title, actIndex))
            actCount++
            act.chapters.forEachIndexed { chIndex, chapter ->
                val chapterId = "nc-ch-$importKey-$actIndex-$chIndex"
                db.manuscriptDao().upsertChapter(
                    ChapterEntity(chapterId, actId, chapter.title, chIndex, summary = ""),
                )
                chapterCount++
                chapter.scenes.forEachIndexed { scIndex, scene ->
                    val sceneId = "nc-sc-$importKey-$actIndex-$chIndex-$scIndex"
                    val doc = Document.fromPlainText(scene.prose.ifBlank { scene.summary })
                    db.manuscriptDao().upsertScene(
                        SceneEntity(
                            id = sceneId,
                            chapterId = chapterId,
                            title = scene.title,
                            sortOrder = scIndex,
                            docJson = doc.toJson(),
                            plainText = doc.plainText(),
                            summary = scene.summary,
                            wordCount = doc.wordCount(),
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                    sceneCount++
                }
            }
        }

        parsed.codexEntries.forEach { entry ->
            val (catName, colorIndex) = folderToCategory[entry.categoryFolder.lowercase()]
                ?: ("Notes" to 9)
            val categoryId = categoryIds[catName] ?: categoryIds.values.first()
            val bodyText = buildString {
                append(entry.body.trim())
                if (entry.notes.isNotBlank()) {
                    if (isNotEmpty()) append("\n\n")
                    append("## Notes\n\n")
                    append(entry.notes.trim())
                }
            }
            val doc = Document.fromPlainText(bodyText.ifBlank { entry.name })
            val colorHex = entry.color?.let { namedColorToHex(it) }
                ?: CodexCategoryColors[colorIndex % CodexCategoryColors.size].toHexString()
            db.codexDao().upsertEntry(
                CodexEntryEntity(
                    id = "nc-entry-$importKey-${entry.id}",
                    categoryId = categoryId,
                    scopeType = com.ihy2ln.weaverse.data.repo.CodexScopes.TYPE,
                    scopeId = com.ihy2ln.weaverse.data.repo.CodexScopes.ID,
                    name = entry.name,
                    aliasesJson = json.encodeToString(entry.aliases),
                    docJson = doc.toJson(),
                    plainText = doc.plainText(),
                    colorHex = colorHex,
                    alwaysInclude = entry.alwaysInclude,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }

        parsed.chats.forEach { chat ->
            val threadId = "nc-chat-$importKey-${chat.id}"
            db.workshopChatDao().upsertThread(
                ChatThreadEntity(
                    id = threadId,
                    scopeId = bookId,
                    name = chat.title,
                    pinned = chat.favourite,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            chat.messages.forEachIndexed { index, msg ->
                val doc = Document.fromPlainText(msg.content)
                db.workshopChatDao().upsertMessage(
                    ChatMessageEntity(
                        id = "$threadId-m$index",
                        threadId = threadId,
                        role = msg.role,
                        contentJson = doc.toJson(),
                        wordCount = doc.wordCount(),
                        createdAt = now + index,
                    ),
                )
            }
        }

        parsed.snippets.forEach { snip ->
            db.snippetDao().upsert(
                SnippetEntity(
                    id = "nc-snip-$importKey-${snip.id}",
                    scopeType = "app",
                    scopeId = "global",
                    title = snip.title,
                    body = snip.body,
                    category = "notes",
                    createdAt = now,
                ),
            )
        }

        val art = attachBundledArt(bookId, parsed, now)
        val rp = seedRoleplay(importKey, parsed, art, now)

        return NovelcrafterImportResult(
            bookId = bookId,
            bookTitle = parsed.bookTitle,
            actCount = actCount,
            chapterCount = chapterCount,
            sceneCount = sceneCount,
            codexCount = parsed.codexEntries.size,
            chatCount = parsed.chats.size,
            snippetCount = parsed.snippets.size,
            rpCharacterCount = rp.first,
            rpChatCount = rp.second,
            mediaCount = art.size,
        )
    }

    private suspend fun attachBundledArt(
        bookId: String,
        parsed: NovelcrafterParsedExport,
        now: Long,
    ): Map<String, String> {
        val ids = linkedMapOf<String, String>()
        ImportArt.pieces.forEach { piece ->
            val bytes = ImportArt.loadBytes(piece.fileName) ?: return@forEach
            val entity = mediaRepository.importFromBytes(
                bytes = bytes,
                id = piece.id,
                fileName = piece.fileName,
                mimeType = ImportArt.mimeFor(piece.fileName),
            )
            ids[piece.attach] = entity.id
            ids[piece.id] = entity.id
        }
        ids["cover"]?.let { coverId ->
            db.bookDao().getById(bookId)?.let { book ->
                db.bookDao().upsert(book.copy(coverMediaId = coverId, updatedAt = now))
            }
        }
        ids["first-scene"]?.let { mediaId ->
            val piece = ImportArt.pieces.first { it.attach == "first-scene" }
            val firstScene = db.manuscriptDao().getActs(bookId).firstOrNull()?.let { act ->
                db.manuscriptDao().getChapters(act.id).firstOrNull()?.let { chapter ->
                    db.manuscriptDao().getScenes(chapter.id).firstOrNull()
                }
            }
            if (firstScene != null) {
                val prose = firstScene.plainText
                val doc = Document(
                    listOf(
                        MediaBlock(
                            id = "m-art",
                            mediaId = mediaId,
                            kind = MediaKind.Image,
                            caption = listOf(Span(piece.caption)),
                            gridColSpan = 2,
                            gridRowSpan = 2,
                        ),
                        Paragraph("p-1", listOf(Span(prose))),
                    ),
                )
                db.manuscriptDao().upsertScene(
                    firstScene.copy(
                        docJson = doc.toJson(),
                        updatedAt = now,
                    ),
                )
            }
        }
        suspend fun attachCodex(attachKey: String, vararg names: String) {
            val mediaId = ids[attachKey] ?: return
            val entry = NovelcrafterCategories.findEntry(parsed.codexEntries, *names) ?: return
            val stored = db.codexDao().getAllEntries().firstOrNull { it.name == entry.name } ?: return
            db.codexDao().upsertEntry(stored.copy(imageMediaId = mediaId, updatedAt = now))
        }
        attachCodex("location", "Adams Haven", "Elysium Vale")
        attachCodex("object", "Celestium", "Life Technology")
        return ids
    }

    private suspend fun seedRoleplay(
        importKey: String,
        parsed: NovelcrafterParsedExport,
        art: Map<String, String>,
        now: Long,
    ): Pair<Int, Int> {
        val personaId = "nc-persona-$importKey"
        db.roleplayDao().upsertPersona(
            RpPersonaEntity(
                id = personaId,
                name = "JD",
                description = "Writer persona for Isekai Gacha — John / JD.",
                isDefault = true,
            ),
        )
        val characters = parsed.codexEntries.filter { it.categoryFolder.equals("characters", ignoreCase = true) }
        characters.forEach { entry ->
            val first = entry.body.lineSequence().firstOrNull { it.isNotBlank() }?.take(240).orEmpty()
            db.roleplayDao().upsertCharacter(
                RpCharacterEntity(
                    id = "nc-rp-$importKey-${entry.id}",
                    name = entry.name,
                    description = entry.body.take(2000),
                    scenario = "Adams Haven / Elysium Vale",
                    firstMes = first.ifBlank { "You meet ${entry.name}." },
                    creatorNotes = "Imported from Novelcrafter characters/",
                    systemPrompt = DefaultAiGuides.characterSystemPrompt(
                        name = entry.name,
                        description = entry.body.take(2000),
                        scenario = "Adams Haven / Elysium Vale",
                    ),
                    tagsJson = """["isekai-gacha"]""",
                    colorHex = entry.color?.let { namedColorToHex(it) },
                    createdAt = now,
                ),
            )
        }
        fun charId(vararg names: String): String? {
            val entry = NovelcrafterCategories.findEntry(characters, *names) ?: return null
            return "nc-rp-$importKey-${entry.id}"
        }
        val iisId = charId("Isekai Incubus System", "IIS")
        val amaraId = charId("Amara")
        val elowenId = charId("Elowen")
        data class Seed(
            val id: String,
            val title: String,
            val mode: String,
            val characterId: String?,
            val backgroundId: String?,
            val text: String,
            val mediaId: String? = null,
            val caption: String = "",
        )
        val seeds = listOf(
            Seed(
                id = "nc-rpchat-$importKey-messenger",
                title = "IIS — wristband",
                mode = "messenger",
                characterId = iisId,
                backgroundId = null,
                text = "You have successfully transferred to Adams Haven. Congratulations. I am your Isekai Incubus System. Tutorial begins now.",
            ),
            Seed(
                id = "nc-rpchat-$importKey-dm",
                title = "Adams Haven — forest path",
                mode = "dungeonMaster",
                characterId = elowenId ?: iisId,
                backgroundId = art["location"] ?: art["rp-background"],
                text = "Sun cuts through the canopy. A dirt path crosses a clear stream on stepping stones. Somewhere ahead, stone ruins wait.",
            ),
            Seed(
                id = "nc-rpchat-$importKey-manga",
                title = "Pulled through the void",
                mode = "roleplay",
                characterId = amaraId ?: iisId,
                backgroundId = art["rp-background"],
                text = "White. Then the dark between worlds. Someone — or something — is pulling you through.",
                mediaId = art["manga-panel"],
                caption = "Pulled through the void",
            ),
        )
        seeds.forEach { seed ->
            db.roleplayDao().upsertChat(
                RpChatEntity(
                    id = seed.id,
                    characterId = seed.characterId,
                    personaId = personaId,
                    title = seed.title,
                    backgroundMediaId = seed.backgroundId,
                    displayMode = seed.mode,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            val doc = if (seed.mediaId != null) {
                Document(
                    listOf(
                        MediaBlock(
                            id = "m-art",
                            mediaId = seed.mediaId,
                            kind = MediaKind.Image,
                            caption = listOf(Span(seed.caption)),
                            gridColSpan = 3,
                            gridRowSpan = 3,
                        ),
                        Paragraph("p-1", listOf(Span(seed.text))),
                    ),
                )
            } else {
                Document.fromPlainText(seed.text)
            }
            db.roleplayDao().upsertMessage(
                RpMessageEntity(
                    id = "${seed.id}-m0",
                    chatId = seed.id,
                    swipeGroupId = "${seed.id}-swipe",
                    swipeIndex = 0,
                    isActiveSwipe = true,
                    role = "char",
                    speakerCharacterId = seed.characterId,
                    contentJson = doc.toJson(),
                    createdAt = now,
                    displayMode = seed.mode,
                ),
            )
        }
        return characters.size to seeds.size
    }

    private fun namedColorToHex(name: String): String = NovelcrafterCategories.namedColorToHex(name)
}
