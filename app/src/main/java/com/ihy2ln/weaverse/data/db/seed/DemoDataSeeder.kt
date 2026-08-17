package com.ihy2ln.weaverse.data.db.seed

import com.ihy2ln.weaverse.core.text.Document
import com.ihy2ln.weaverse.core.text.Paragraph
import com.ihy2ln.weaverse.core.text.Span
import com.ihy2ln.weaverse.core.text.toJson
import com.ihy2ln.weaverse.data.db.AppDatabase
import com.ihy2ln.weaverse.data.db.entity.ActEntity
import com.ihy2ln.weaverse.data.db.entity.BookEntity
import com.ihy2ln.weaverse.data.db.entity.ChapterEntity
import com.ihy2ln.weaverse.data.db.entity.ChatMessageEntity
import com.ihy2ln.weaverse.data.db.entity.ChatRole
import com.ihy2ln.weaverse.data.db.entity.ChatThreadEntity
import com.ihy2ln.weaverse.data.db.entity.CodexCategoryEntity
import com.ihy2ln.weaverse.data.db.entity.CodexEntryEntity
import com.ihy2ln.weaverse.data.db.entity.CodexEntryLoreEntity
import com.ihy2ln.weaverse.data.db.entity.CodexLinkSource
import com.ihy2ln.weaverse.data.db.entity.RpCharacterEntity
import com.ihy2ln.weaverse.data.db.entity.SceneEntity
import com.ihy2ln.weaverse.data.db.entity.SceneStatus
import com.ihy2ln.weaverse.data.db.entity.ScopeType
import com.ihy2ln.weaverse.data.db.entity.SnippetEntity
import com.ihy2ln.weaverse.data.db.entity.newId
import com.ihy2ln.weaverse.data.repo.ChatRepository
import com.ihy2ln.weaverse.data.repo.CodexRepository
import com.ihy2ln.weaverse.data.repo.LibraryRepository
import com.ihy2ln.weaverse.data.repo.RoleplayRepository
import com.ihy2ln.weaverse.data.repo.SnippetLabelRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * First-run demo library (spec §12 Phase 3 checkpoint): 1 book / 1 act / 1
 * chapter / 2 scenes, ~6 codex entries across 4 categories (one with
 * aliases + alwaysInclude), 1 snippet, 2 chat threads, 1 roleplay character
 * with codex entries — so first launch shows real content, not a blank screen
 * (spec §13 acceptance criterion).
 */
@Singleton
class DemoDataSeeder @Inject constructor(
    private val db: AppDatabase,
    private val library: LibraryRepository,
    private val codex: CodexRepository,
    private val snippets: SnippetLabelRepository,
    private val chat: ChatRepository,
    private val roleplay: RoleplayRepository,
) {
    suspend fun seedIfNeeded() {
        if (db.sceneDao().count() > 0) return

        val book = BookEntity(
            title = "Adams Haven",
            genre = "Fantasy",
            pov = "Third Limited",
            tense = "Past",
            styleGuide = "Warm, grounded prose. Short paragraphs in dialogue-heavy scenes.",
            targetWordCount = 90_000,
        )
        library.upsertBook(book)

        val act = ActEntity(bookId = book.id, title = "Act One", sortOrder = 0)
        library.upsertAct(act)

        val chapter = ChapterEntity(
            actId = act.id,
            title = "Chapter One",
            sortOrder = 0,
            summary = "John Zhao arrives in Adams Haven and finds it isn't what the map promised.",
        )
        library.upsertChapter(chapter)

        val sceneOneText = "The bus dropped John Zhao at the edge of Adams Haven just after dusk, " +
            "and for a long moment he simply stood there with Zhao's Compass warm in his pocket, " +
            "watching the lights of the Old Mill flicker across the water."
        val sceneOneDocument = Document(listOf(Paragraph(id = newId(), spans = listOf(Span(sceneOneText)))))
        val sceneOne = SceneEntity(
            chapterId = chapter.id,
            title = "The Arrival",
            sortOrder = 0,
            docJson = sceneOneDocument.toJson(),
            plainText = sceneOneText,
            summary = "John arrives in town and gets his first look at the Old Mill.",
            wordCount = sceneOneText.split(Regex("\\s+")).size,
            status = SceneStatus.Draft,
            pov = "John Zhao",
        )
        library.upsertScene(sceneOne)

        val sceneTwoText = "Mara Voss met him on the porch before he'd even knocked, like she'd been " +
            "waiting since The Founding itself. \"You're late,\" she said, and didn't smile."
        val sceneTwoDocument = Document(listOf(Paragraph(id = newId(), spans = listOf(Span(sceneTwoText)))))
        val sceneTwo = SceneEntity(
            chapterId = chapter.id,
            title = "Old Secrets",
            sortOrder = 1,
            docJson = sceneTwoDocument.toJson(),
            plainText = sceneTwoText,
            summary = "Mara Voss confronts John about why he's really come to town.",
            wordCount = sceneTwoText.split(Regex("\\s+")).size,
            status = SceneStatus.Draft,
            pov = "John Zhao",
        )
        library.upsertScene(sceneTwo)

        val charactersCategory = CodexCategoryEntity(
            scopeType = ScopeType.Book,
            scopeId = book.id,
            name = "Characters",
            colorHex = "#4A90D9",
            icon = "person",
            sortOrder = 0,
            isSystem = true,
        )
        val locationsCategory = CodexCategoryEntity(
            scopeType = ScopeType.Book,
            scopeId = book.id,
            name = "Locations",
            colorHex = "#3FA66A",
            icon = "place",
            sortOrder = 1,
            isSystem = true,
        )
        val itemsCategory = CodexCategoryEntity(
            scopeType = ScopeType.Book,
            scopeId = book.id,
            name = "Objects/Items",
            colorHex = "#8B6FD1",
            icon = "inventory",
            sortOrder = 2,
            isSystem = true,
        )
        val loreCategory = CodexCategoryEntity(
            scopeType = ScopeType.Book,
            scopeId = book.id,
            name = "Lore",
            colorHex = "#D98A3F",
            icon = "menu_book",
            sortOrder = 3,
            isSystem = true,
        )
        listOf(charactersCategory, locationsCategory, itemsCategory, loreCategory).forEach {
            codex.upsertCategory(it)
        }
        // The other six of the ten built-in categories (Revision 02 §2) — this demo book only
        // hand-picks entries for the four above, but every new book gets all ten, so the demo
        // should show the full set too, not just the categories with seeded content.
        codex.seedBuiltInCategories(ScopeType.Book, book.id)

        val johnZhao = CodexEntryEntity(
            categoryId = charactersCategory.id,
            scopeType = ScopeType.Book,
            scopeId = book.id,
            name = "John Zhao",
            aliases = listOf("John", "Zhao"),
            plainText = "The book's POV character. Arrives in Adams Haven carrying more questions than answers.",
            alwaysInclude = true,
        )
        val maraVoss = CodexEntryEntity(
            categoryId = charactersCategory.id,
            scopeType = ScopeType.Book,
            scopeId = book.id,
            name = "Mara Voss",
            aliases = listOf("Mara"),
            plainText = "Keeper of the Old Mill and the last person in town who remembers The Founding firsthand.",
        )
        val adamsHavenTown = CodexEntryEntity(
            categoryId = locationsCategory.id,
            scopeType = ScopeType.Book,
            scopeId = book.id,
            name = "Adams Haven",
            aliases = listOf("the Haven"),
            plainText = "A small lakeside town that doesn't appear on any map printed after 1970.",
        )
        val oldMill = CodexEntryEntity(
            categoryId = locationsCategory.id,
            scopeType = ScopeType.Book,
            scopeId = book.id,
            name = "The Old Mill",
            plainText = "Mara Voss's home, and the only building in town older than the founding families.",
        )
        val zhaosCompass = CodexEntryEntity(
            categoryId = itemsCategory.id,
            scopeType = ScopeType.Book,
            scopeId = book.id,
            name = "Zhao's Compass",
            plainText = "A brass compass that doesn't point north inside Adams Haven's town limits.",
        )
        val theFounding = CodexEntryEntity(
            categoryId = loreCategory.id,
            scopeType = ScopeType.Book,
            scopeId = book.id,
            name = "The Founding",
            plainText = "The (disputed) story of how Adams Haven came to be, and why it's hard to leave.",
        )
        listOf(johnZhao, maraVoss, adamsHavenTown, oldMill, zhaosCompass, theFounding).forEach {
            codex.upsertEntry(it)
        }

        library.linkSceneToCodexEntry(sceneOne.id, johnZhao.id, CodexLinkSource.Manual)
        library.linkSceneToCodexEntry(sceneOne.id, zhaosCompass.id, CodexLinkSource.Manual)
        library.linkSceneToCodexEntry(sceneOne.id, oldMill.id, CodexLinkSource.Manual)
        library.linkSceneToCodexEntry(sceneTwo.id, maraVoss.id, CodexLinkSource.Manual)
        library.linkSceneToCodexEntry(sceneTwo.id, theFounding.id, CodexLinkSource.Manual)

        // A real World Info payload on Mara Voss's entry, so switching to Roleplay mode
        // and opening her Codex tab shows populated fields, not empty defaults.
        codex.upsertLore(
            CodexEntryLoreEntity(
                entryId = maraVoss.id,
                keys = listOf("Mara", "Mara Voss", "the Old Mill"),
                insertionOrder = 10,
                probability = 100,
                isConstant = false,
            ),
        )

        snippets.upsertSnippet(
            SnippetEntity(
                scopeType = ScopeType.Book,
                scopeId = book.id,
                title = "Lakeside weather",
                body = "Fog off the lake by mid-morning, burned away by noon, back again with the cold at dusk.",
                category = "Setting",
                pinned = true,
            ),
        )

        val developmentalEditorThread = ChatThreadEntity(
            scopeId = book.id,
            name = "Developmental Editor",
            pinned = true,
        )
        val brainstormThread = ChatThreadEntity(
            scopeId = book.id,
            name = "Brainstorm: Act Two",
        )
        chat.upsertThread(developmentalEditorThread)
        chat.upsertThread(brainstormThread)
        chat.upsertMessage(
            ChatMessageEntity(
                threadId = developmentalEditorThread.id,
                role = ChatRole.Assistant,
                contentJson = "",
                plainText = "Welcome! Ask me about pacing, character arcs, or anything else in the manuscript.",
                wordCount = 12,
            ),
        )

        // Cross-mode demo (spec §5): the same character exists as both a codex
        // entry (above) and a roleplay character, linked via defaultCodexCategoryId
        // pointing at her category so "Use in roleplay" / "Link to codex entry"
        // have something real to show.
        roleplay.upsertCharacter(
            RpCharacterEntity(
                id = newId(),
                name = "Mara Voss",
                description = "Keeper of the Old Mill in Adams Haven.",
                personality = "Guarded, dry-humored, protective of the town's secrets.",
                scenario = "Mara has just met {{user}} on the porch of the Old Mill at dusk.",
                firstMes = "\"You're late,\" she says, not smiling. \"Come in before someone sees you.\"",
                mesExample = "<START>\n{{user}}: Who are you?\n{{char}}: *She doesn't answer right away.* " +
                    "Someone who's been expecting you longer than you'd believe.",
                defaultCodexCategoryId = charactersCategory.id,
                colorHex = "#4A90D9",
            ),
        )
    }
}
