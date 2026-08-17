package com.ihy2ln.weaverse.data.seed

import com.ihy2ln.weaverse.ai.openrouter.WritingModelSeeds
import com.ihy2ln.weaverse.ai.prompt.DefaultAiGuides
import com.ihy2ln.weaverse.core.text.Document
import com.ihy2ln.weaverse.core.text.Paragraph
import com.ihy2ln.weaverse.core.text.Span
import com.ihy2ln.weaverse.core.text.toJson
import com.ihy2ln.weaverse.core.text.plainText
import com.ihy2ln.weaverse.core.text.wordCount
import com.ihy2ln.weaverse.core.ui.theme.CodexCategoryColors
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.ihy2ln.weaverse.data.db.entities.ActEntity
import com.ihy2ln.weaverse.data.db.entities.BookEntity
import com.ihy2ln.weaverse.data.db.entities.ChapterEntity
import com.ihy2ln.weaverse.data.db.entities.ChatMessageEntity
import com.ihy2ln.weaverse.data.db.entities.ChatThreadEntity
import com.ihy2ln.weaverse.data.db.entities.CodexCategoryEntity
import com.ihy2ln.weaverse.data.db.entities.CodexEntryEntity
import com.ihy2ln.weaverse.data.db.entities.CodexEntryLoreEntity
import com.ihy2ln.weaverse.data.db.entities.RpCharacterEntity
import com.ihy2ln.weaverse.data.db.entities.RpChatEntity
import com.ihy2ln.weaverse.data.db.entities.RpMessageEntity
import com.ihy2ln.weaverse.data.db.entities.RpPersonaEntity
import com.ihy2ln.weaverse.data.db.entities.SceneEntity
import com.ihy2ln.weaverse.data.db.entities.SeriesEntity
import com.ihy2ln.weaverse.data.db.entities.SnippetEntity
import com.ihy2ln.weaverse.core.ui.theme.toHexString
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import javax.inject.Inject
import javax.inject.Singleton

private val json = Json { ignoreUnknownKeys = true }

@Singleton
class DatabaseSeeder @Inject constructor(
    private val db: WeaverseDatabase,
) {
    private val categoryNames = listOf(
        "Characters", "Locations", "Objects/Items", "Lore",
        "Factions", "Subplots", "Magic/Tech Systems", "Events/Timeline",
        "Organizations", "Notes",
    )

    suspend fun seedIfEmpty() {
        if (db.bookDao().count() > 0) {
            seedPromptsIfEmpty()
            return
        }
        val now = System.currentTimeMillis()
        val seriesId = "series-adams-haven"
        val bookId = "book-adams-haven-1"
        val actId = "act-1"
        val chapterId = "chapter-1"
        val scene1Id = "scene-1"
        val scene2Id = "scene-2"

        db.seriesDao().upsert(
            SeriesEntity(
                id = seriesId,
                title = "Adams Haven",
                description = "Demo series for Weaverse",
                premise = "A coastal town hides old secrets.",
                createdAt = now,
            ),
        )
        db.bookDao().upsert(
            BookEntity(
                id = bookId,
                seriesId = seriesId,
                title = "Book 1",
                genre = "Literary mystery",
                pov = "3rd Person",
                tense = "Past",
                styleGuide = "Close third, sensory detail, restrained dialogue.",
                targetWordCount = 80000,
                createdAt = now,
                updatedAt = now,
            ),
        )
        db.manuscriptDao().upsertAct(ActEntity(actId, bookId, "Act I", 0))
        db.manuscriptDao().upsertChapter(ChapterEntity(chapterId, actId, "Chapter 1", 0, "John arrives at Adams Haven."))

        val scene1Doc = Document(
            listOf(
                Paragraph("p1", listOf(Span("John Z stepped off the ferry into a salt-cold wind."))),
                Paragraph("p2", listOf(Span("Adams Haven looked unchanged — and that was the problem."))),
            ),
        )
        val scene2Doc = Document(
            listOf(
                Paragraph("p3", listOf(Span("The lighthouse beam swept the harbor, counting secrets it would never tell."))),
            ),
        )
        insertScene(scene1Id, chapterId, "Scene 1", 0, scene1Doc, now)
        insertScene(scene2Id, chapterId, "Scene 2", 1, scene2Doc, now)

        categoryNames.forEachIndexed { index, name ->
            db.codexDao().upsertCategory(
                CodexCategoryEntity(
                    id = "cat-$index",
                    scopeType = "book",
                    scopeId = bookId,
                    name = name,
                    colorHex = CodexCategoryColors[index].toHexString(),
                    sortOrder = index,
                    isSystem = index < 6,
                    isBuiltIn = true,
                ),
            )
        }

        val entries = listOf(
            Triple("John Z", 0, listOf("John", "Johnny")),
            Triple("Adams Haven", 1, emptyList()),
            Triple("The Lighthouse Key", 2, emptyList()),
            Triple("Old Harbor Legend", 3, listOf("harbor", "legend")),
            Triple("Town Council", 4, emptyList()),
            Triple("Missing Ledger", 5, emptyList()),
        )
        entries.forEachIndexed { i, (name, catIndex, aliases) ->
            val body = Document.fromPlainText("$name — seeded codex entry for demo.")
            val entryId = "entry-$i"
            db.codexDao().upsertEntry(
                CodexEntryEntity(
                    id = entryId,
                    categoryId = "cat-$catIndex",
                    scopeType = "book",
                    scopeId = bookId,
                    name = name,
                    aliasesJson = json.encodeToString(aliases),
                    docJson = body.toJson(),
                    plainText = body.plainText(),
                    colorHex = CodexCategoryColors[catIndex].toHexString(),
                    alwaysInclude = name == "John Z",
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            if (name == "Old Harbor Legend") {
                db.codexDao().upsertLore(
                    CodexEntryLoreEntity(
                        entryId = entryId,
                        keysJson = "[\"harbor\",\"legend\",\"lighthouse\"]",
                        insertionOrder = 10,
                        isConstant = false,
                    ),
                )
            }
        }

        db.snippetDao().upsert(
            SnippetEntity(
                id = "snippet-1",
                scopeType = "book",
                scopeId = bookId,
                title = "Opening mood",
                body = "Salt, rust, and the feeling of being watched.",
                pinned = true,
                createdAt = now,
            ),
        )

        val thread1 = "thread-1"
        val thread2 = "thread-2"
        db.workshopChatDao().upsertThread(
            ChatThreadEntity(thread1, bookId, "Developmental Editor", true, modelRef = WritingModelSeeds.DEFAULT_MODEL_REF, createdAt = now, updatedAt = now),
        )
        db.workshopChatDao().upsertThread(
            ChatThreadEntity(thread2, bookId, "Scene Beats", false, modelRef = WritingModelSeeds.DEFAULT_MODEL_REF, createdAt = now, updatedAt = now),
        )
        db.workshopChatDao().upsertMessage(
            ChatMessageEntity(
                id = "msg-1",
                threadId = thread1,
                role = "assistant",
                contentJson = Document.fromPlainText("Consider sharpening the ferry arrival beat.").toJson(),
                createdAt = now,
            ),
        )

        val personaId = "persona-default"
        db.roleplayDao().upsertPersona(
            RpPersonaEntity(personaId, "Writer", isDefault = true),
        )
        val charId = "char-mara"
        db.roleplayDao().upsertCharacter(
            RpCharacterEntity(
                id = charId,
                name = "Mara",
                description = "A sharp-eyed local historian.",
                personality = "Curious, dry humor, protective of town stories.",
                scenario = "You meet Mara at the harbor café.",
                firstMes = "You're new here. That makes you interesting — or trouble.",
                mesExample = "",
                systemPrompt = DefaultAiGuides.characterSystemPrompt(
                    name = "Mara",
                    description = "A sharp-eyed local historian.",
                    personality = "Curious, dry humor, protective of town stories.",
                    scenario = "You meet Mara at the harbor café.",
                ),
                createdAt = now,
            ),
        )
        val chatId = "rp-chat-1"
        db.roleplayDao().upsertChat(
            RpChatEntity(
                id = chatId,
                characterId = charId,
                personaId = personaId,
                title = "Mara — harbor café",
                createdAt = now,
                updatedAt = now,
            ),
        )
        db.roleplayDao().upsertMessage(
            RpMessageEntity(
                id = "rp-msg-1",
                chatId = chatId,
                swipeGroupId = "swipe-1",
                swipeIndex = 0,
                isActiveSwipe = true,
                role = "char",
                speakerCharacterId = charId,
                contentJson = Document.fromPlainText("You're new here. That makes you interesting — or trouble.").toJson(),
                createdAt = now,
            ),
        )
        seedPromptsIfEmpty()
    }

    private suspend fun seedPromptsIfEmpty() {
        val now = System.currentTimeMillis()
        ensureGuidingPrompts(now)
        ensureCharacterWritingGuides()
    }

    private suspend fun ensureGuidingPrompts(now: Long) {
        DefaultAiGuides.seedFolders().forEach { db.promptDao().upsertFolder(it) }
        val existingById = db.promptDao().getAll().associateBy { it.id }
        DefaultAiGuides.seedPrompts(now).forEach { incoming ->
            val existing = existingById[incoming.id]
            db.promptDao().upsert(
                incoming.copy(createdAt = existing?.createdAt ?: incoming.createdAt),
            )
        }
    }

    private suspend fun ensureCharacterWritingGuides() {
        db.roleplayDao().getCharacters().forEach { character ->
            if (!DefaultAiGuides.isThinSystemPrompt(character.name, character.systemPrompt)) return@forEach
            db.roleplayDao().upsertCharacter(
                character.copy(
                    systemPrompt = DefaultAiGuides.characterSystemPrompt(
                        name = character.name,
                        description = character.description,
                        personality = character.personality,
                        scenario = character.scenario,
                    ),
                ),
            )
        }
    }

    private suspend fun insertScene(
        id: String,
        chapterId: String,
        title: String,
        sortOrder: Int,
        doc: Document,
        now: Long,
    ) {
        db.manuscriptDao().upsertScene(
            SceneEntity(
                id = id,
                chapterId = chapterId,
                title = title,
                sortOrder = sortOrder,
                docJson = doc.toJson(),
                plainText = doc.plainText(),
                summary = "Demo scene for $title",
                wordCount = doc.wordCount(),
                pov = "3rd Person – John Z",
                createdAt = now,
                updatedAt = now,
            ),
        )
    }
}
