package com.ihy2ln.weaverse.data.repo

import com.ihy2ln.weaverse.core.text.Document
import com.ihy2ln.weaverse.core.text.Paragraph
import com.ihy2ln.weaverse.core.text.Span
import com.ihy2ln.weaverse.core.text.appendSceneBeat
import com.ihy2ln.weaverse.core.text.documentFromJson
import com.ihy2ln.weaverse.core.text.plainText
import com.ihy2ln.weaverse.core.text.toJson
import com.ihy2ln.weaverse.core.text.wordCount
import com.ihy2ln.weaverse.core.ui.theme.CodexCategoryColors
import com.ihy2ln.weaverse.core.ui.theme.toHexString
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.ActEntity
import com.ihy2ln.weaverse.data.db.entities.BookEntity
import com.ihy2ln.weaverse.data.db.entities.ChapterEntity
import com.ihy2ln.weaverse.data.db.entities.CodexCategoryEntity
import com.ihy2ln.weaverse.data.db.entities.CodexEntryEntity
import com.ihy2ln.weaverse.data.db.entities.PromptEntity
import com.ihy2ln.weaverse.data.db.entities.PromptFolderEntity
import com.ihy2ln.weaverse.data.db.entities.SceneEntity
import com.ihy2ln.weaverse.data.db.entities.SeriesEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeriesRepository @Inject constructor(
    private val db: WeaverseDatabase,
) {
    fun observeSeries(): Flow<List<SeriesEntity>> = db.seriesDao().observeAll()
    fun observeSeries(id: String): Flow<SeriesEntity?> = db.seriesDao().observeById(id)
    suspend fun getSeries(id: String): SeriesEntity? = db.seriesDao().observeById(id).first()

    suspend fun createSeries(title: String, description: String = ""): SeriesEntity {
        val now = System.currentTimeMillis()
        val entity = SeriesEntity(
            id = "series-${UUID.randomUUID()}",
            title = title.ifBlank { "Untitled Series" },
            description = description,
            createdAt = now,
        )
        db.seriesDao().upsert(entity)
        return entity
    }

    suspend fun updateSeries(entity: SeriesEntity) = db.seriesDao().upsert(entity)

    suspend fun deleteSeries(id: String) {
        db.bookDao().observeBySeries(id).first().forEach { book ->
            db.bookDao().upsert(book.copy(seriesId = null, updatedAt = System.currentTimeMillis()))
        }
        db.seriesDao().deleteById(id)
    }
}

@Singleton
class BookRepository @Inject constructor(
    private val db: WeaverseDatabase,
) {
    private val defaultCategoryNames = listOf(
        "Characters", "Locations", "Objects/Items", "Lore",
        "Factions", "Subplots", "Magic/Tech Systems", "Events/Timeline",
        "Organizations", "Notes",
    )

    fun observeBooks(): Flow<List<BookEntity>> = db.bookDao().observeAll()
    fun observeBook(id: String): Flow<BookEntity?> = db.bookDao().observeById(id)
    suspend fun getBook(id: String): BookEntity? = db.bookDao().getById(id)
    fun observeBooksInSeries(seriesId: String): Flow<List<BookEntity>> = db.bookDao().observeBySeries(seriesId)

    suspend fun createBook(
        title: String,
        seriesId: String? = null,
        genre: String = "",
        pov: String = "",
        tense: String = "",
        styleGuide: String = "",
        workType: String = "novel",
    ): BookEntity {
        val now = System.currentTimeMillis()
        val bookId = "book-${UUID.randomUUID()}"
        val actId = "act-$bookId"
        val chapterId = "chapter-$bookId"
        val sceneId = "scene-$bookId-1"
        val book = BookEntity(
            id = bookId,
            seriesId = seriesId,
            title = title.ifBlank { "Untitled Book" },
            genre = genre,
            pov = pov,
            tense = tense,
            styleGuide = styleGuide,
            createdAt = now,
            updatedAt = now,
            workType = workType,
        )
        db.bookDao().upsert(book)
        db.manuscriptDao().upsertAct(ActEntity(actId, bookId, "Act I", 0))
        db.manuscriptDao().upsertChapter(ChapterEntity(chapterId, actId, "Chapter 1", 0))
        val doc = Document(listOf(Paragraph("p-$sceneId", listOf(Span("")))))
        db.manuscriptDao().upsertScene(
            SceneEntity(
                id = sceneId,
                chapterId = chapterId,
                title = "Scene 1",
                sortOrder = 0,
                docJson = doc.toJson(),
                plainText = doc.plainText(),
                wordCount = doc.wordCount(),
                createdAt = now,
                updatedAt = now,
            ),
        )
        defaultCategoryNames.forEachIndexed { index, name ->
            db.codexDao().upsertCategory(
                CodexCategoryEntity(
                    id = "cat-$bookId-$index",
                    scopeType = "book",
                    scopeId = bookId,
                    name = name,
                    colorHex = CodexCategoryColors[index % CodexCategoryColors.size].toHexString(),
                    sortOrder = index,
                    isSystem = index < 6,
                    isBuiltIn = true,
                ),
            )
        }
        return book
    }

    suspend fun updateBook(entity: BookEntity) =
        db.bookDao().upsert(entity.copy(updatedAt = System.currentTimeMillis()))

    /** Deep-copy a work so long-press Copy includes its manuscript, codex, notes, and workshop chats. */
    suspend fun duplicateBook(bookId: String): BookEntity? {
        val source = db.bookDao().getById(bookId) ?: return null
        val now = System.currentTimeMillis()
        val copyId = "book-${UUID.randomUUID()}"
        val copy = source.copy(
            id = copyId,
            title = "${source.title} Copy",
            createdAt = now,
            updatedAt = now,
        )
        db.bookDao().upsert(copy)

        db.manuscriptDao().getActs(bookId).forEach { act ->
            val actId = "act-${UUID.randomUUID()}"
            db.manuscriptDao().upsertAct(act.copy(id = actId, bookId = copyId))
            db.manuscriptDao().getChapters(act.id).forEach { chapter ->
                val chapterId = "chapter-${UUID.randomUUID()}"
                db.manuscriptDao().upsertChapter(chapter.copy(id = chapterId, actId = actId))
                db.manuscriptDao().getScenes(chapter.id).forEach { scene ->
                    db.manuscriptDao().upsertScene(
                        scene.copy(
                            id = "scene-${UUID.randomUUID()}",
                            chapterId = chapterId,
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                }
            }
        }

        val categoryIds = mutableMapOf<String, String>()
        db.codexDao().getCategories(bookId).forEach { category ->
            val categoryId = "cat-${UUID.randomUUID()}"
            categoryIds[category.id] = categoryId
            db.codexDao().upsertCategory(category.copy(id = categoryId, scopeId = copyId))
        }
        db.codexDao().getEntries(bookId).forEach { entry ->
            db.codexDao().upsertEntry(
                entry.copy(
                    id = "entry-${UUID.randomUUID()}",
                    categoryId = categoryIds[entry.categoryId] ?: entry.categoryId,
                    scopeId = copyId,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
        db.snippetDao().get(bookId).forEach { snippet ->
            db.snippetDao().upsert(
                snippet.copy(id = "snippet-${UUID.randomUUID()}", scopeId = copyId, createdAt = now),
            )
        }
        db.workshopChatDao().getThreads(bookId).forEach { thread ->
            val threadId = "thread-${UUID.randomUUID()}"
            db.workshopChatDao().upsertThread(
                thread.copy(id = threadId, scopeId = copyId, createdAt = now, updatedAt = now),
            )
            db.workshopChatDao().getMessages(thread.id).forEach { message ->
                db.workshopChatDao().upsertMessage(
                    message.copy(id = "message-${UUID.randomUUID()}", threadId = threadId),
                )
            }
        }
        return copy
    }

    suspend fun setBookSeries(bookId: String, seriesId: String?) {
        val book = db.bookDao().observeById(bookId).first() ?: return
        db.bookDao().upsert(book.copy(seriesId = seriesId, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteBook(bookId: String) {
        db.codexDao().deleteEntriesForScope(bookId)
        db.codexDao().deleteCategoriesForScope(bookId)
        db.bookDao().deleteById(bookId)
    }

    suspend fun firstSceneId(bookId: String): String? {
        val acts = db.manuscriptDao().observeActs(bookId).first()
        val act = acts.firstOrNull() ?: return null
        val chapters = db.manuscriptDao().observeChapters(act.id).first()
        val chapter = chapters.firstOrNull() ?: return null
        return db.manuscriptDao().observeScenes(chapter.id).first().firstOrNull()?.id
    }

    suspend fun primaryChapterId(bookId: String): String? {
        val acts = db.manuscriptDao().observeActs(bookId).first()
        val act = acts.firstOrNull() ?: return null
        return db.manuscriptDao().observeChapters(act.id).first().firstOrNull()?.id
    }

    suspend fun primaryActId(bookId: String): String? =
        db.manuscriptDao().observeActs(bookId).first().firstOrNull()?.id
}

@Singleton
class ManuscriptRepository @Inject constructor(
    private val db: WeaverseDatabase,
    private val writeStamps: SceneWriteStamps,
) {
    fun observeScene(id: String): Flow<SceneEntity?> = db.manuscriptDao().observeScene(id)
    suspend fun getScene(id: String): SceneEntity? = db.manuscriptDao().getScene(id)
    fun observeScenes(chapterId: String) = db.manuscriptDao().observeScenes(chapterId)
    fun observeActs(bookId: String) = db.manuscriptDao().observeActs(bookId)
    fun observeChapters(actId: String) = db.manuscriptDao().observeChapters(actId)
    suspend fun saveScene(scene: SceneEntity) = db.manuscriptDao().upsertScene(scene)

    suspend fun saveChapter(chapter: ChapterEntity) = db.manuscriptDao().upsertChapter(chapter)

    suspend fun deleteScene(sceneId: String) = db.manuscriptDao().deleteScene(sceneId)

    suspend fun deleteChapter(chapterId: String) {
        db.manuscriptDao().getScenes(chapterId).forEach { db.manuscriptDao().deleteScene(it.id) }
        db.manuscriptDao().deleteChapter(chapterId)
    }

    suspend fun ensureAct(bookId: String): ActEntity {
        val existing = db.manuscriptDao().getActs(bookId)
        existing.firstOrNull()?.let { return it }
        val act = ActEntity(
            id = "act-${UUID.randomUUID()}",
            bookId = bookId,
            title = "Act I",
            sortOrder = 0,
        )
        db.manuscriptDao().upsertAct(act)
        return act
    }

    suspend fun createScene(chapterId: String): SceneEntity {
        val existing = db.manuscriptDao().getScenes(chapterId)
        val nextOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val now = System.currentTimeMillis()
        val id = "scene-${UUID.randomUUID()}"
        val doc = Document(listOf(Paragraph("p-$id", listOf(Span("")))))
        val entity = SceneEntity(
            id = id,
            chapterId = chapterId,
            title = "Scene ${nextOrder + 1}",
            sortOrder = nextOrder,
            docJson = doc.toJson(),
            plainText = "",
            wordCount = 0,
            pov = "3rd Person",
            createdAt = now,
            updatedAt = now,
        )
        db.manuscriptDao().upsertScene(entity)
        return entity
    }

    suspend fun createChapter(actId: String): Pair<ChapterEntity, SceneEntity> {
        val existing = db.manuscriptDao().getChapters(actId)
        val nextOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val chapter = ChapterEntity(
            id = "chapter-${UUID.randomUUID()}",
            actId = actId,
            title = "Chapter ${nextOrder + 1}",
            sortOrder = nextOrder,
        )
        db.manuscriptDao().upsertChapter(chapter)
        return chapter to createScene(chapter.id)
    }

    suspend fun appendSceneBeat(sceneId: String): SceneEntity? {
        val scene = db.manuscriptDao().getScene(sceneId) ?: return null
        val doc = documentFromJson(scene.docJson)
        val next = Document(blocks = doc.blocks.appendSceneBeat())
        val updated = scene.copy(
            docJson = next.toJson(),
            plainText = next.plainText(),
            wordCount = next.wordCount(),
            updatedAt = writeStamps.next(),
        )
        db.manuscriptDao().upsertScene(updated)
        return updated
    }
}

@Singleton
class CodexRepository @Inject constructor(
    private val db: WeaverseDatabase,
) {
    fun observeCategories(scopeId: String) = db.codexDao().observeCategories(scopeId)
    fun observeEntries(scopeId: String) = db.codexDao().observeEntries(scopeId)
    fun observeAllCategories() = db.codexDao().observeAllCategories()
    fun observeAllEntries() = db.codexDao().observeAllEntries()
    fun observeEntry(id: String) = db.codexDao().observeEntry(id)

    suspend fun upsertEntry(entity: CodexEntryEntity) = db.codexDao().upsertEntry(entity)

    /**
     * Fold every book's Codex into one shared library so Plan / Write / Roleplay / Notes
     * all see the same characters, locations, and lore.
     */
    suspend fun ensureGlobalAndMigrate() {
        val categories = db.codexDao().getAllCategories()
        val entries = db.codexDao().getAllEntries()
        if (categories.isEmpty() && entries.isEmpty()) return
        val remap = linkedMapOf<String, String>()
        categories.groupBy { it.name.trim().lowercase() }.forEach { (_, group) ->
            val keep = group.firstOrNull { it.scopeId == CodexScopes.ID } ?: group.first()
            val global = keep.copy(scopeType = CodexScopes.TYPE, scopeId = CodexScopes.ID)
            if (global != keep) db.codexDao().upsertCategory(global)
            group.forEach { remap[it.id] = global.id }
            group.filter { it.id != global.id }.forEach { extra ->
                db.codexDao().deleteCategory(extra.id)
            }
        }
        entries.forEach { entry ->
            val newCat = remap[entry.categoryId] ?: entry.categoryId
            if (entry.scopeId != CodexScopes.ID ||
                entry.scopeType != CodexScopes.TYPE ||
                entry.categoryId != newCat
            ) {
                db.codexDao().upsertEntry(
                    entry.copy(
                        categoryId = newCat,
                        scopeType = CodexScopes.TYPE,
                        scopeId = CodexScopes.ID,
                    ),
                )
            }
        }
    }

    /** Finds a category by name in the shared codex, creating it when missing. */
    suspend fun ensureCategory(name: String): CodexCategoryEntity {
        val existing = db.codexDao().getAllCategories()
            .firstOrNull { it.name.equals(name, ignoreCase = true) }
        if (existing != null) return existing
        val entity = CodexCategoryEntity(
            id = "cat-${UUID.randomUUID()}",
            scopeType = CodexScopes.TYPE,
            scopeId = CodexScopes.ID,
            name = name,
            colorHex = if (name.equals("Characters", ignoreCase = true)) "#3F7A5A" else "#6B5B95",
            sortOrder = 100,
            updatedAt = System.currentTimeMillis(),
        )
        db.codexDao().upsertCategory(entity)
        return entity
    }

    /** Creates a user-defined category in the shared codex; name collisions return the existing one. */
    suspend fun createCategory(name: String, colorHex: String): CodexCategoryEntity {
        val trimmed = name.trim()
        val categories = db.codexDao().getAllCategories()
        categories.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }?.let { return it }
        val entity = CodexCategoryEntity(
            id = "cat-${UUID.randomUUID()}",
            scopeType = CodexScopes.TYPE,
            scopeId = CodexScopes.ID,
            name = trimmed,
            colorHex = colorHex,
            sortOrder = (categories.maxOfOrNull { it.sortOrder } ?: 0) + 1,
            updatedAt = System.currentTimeMillis(),
        )
        db.codexDao().upsertCategory(entity)
        return entity
    }

    /** One bundled game-world entry for the "Worlds" codex section. */
    private data class WorldSeed(val title: String, val text: String)

    private val worldSeeds = listOf(
        WorldSeed(
            "Genshin Impact · Teyvat",
            "Teyvat is a world of seven nations, each ruled by an Archon and shaped by an ideal: " +
                "Mondstadt (Freedom, the Anemo Archon Barbatos), Liyue (Contracts, the Geo Archon Morax/Rex Lapis), " +
                "Inazuma (Eternity, the Electro Archon Beelzebul/Raiden Shogun), Sumeru (Wisdom, the Dendro " +
                "Archon Lesser Lord Kusanali), Fontaine (Justice, the Hydro Archon Focalors), Natlan (War, the " +
                "Pyro Archon Murata), and Snezhnaya (the Cryo Tsaritsa). Mortals blessed by the gods receive " +
                "Visions — crystal foci that let them channel one of seven elements: Anemo, Geo, Electro, " +
                "Dendro, Hydro, Pyro, Cryo. Elements react on contact (Vaporize, Melt, Overloaded, Superconduct, " +
                "Swirl, Crystallize, Bloom), shaping both combat and daily craft. Key powers: the Fatui of " +
                "Snezhnaya and their Eleven Harbingers seek the Archons' Gnoses; the Abyss Order breeds monsters " +
                "in the ruins beneath; Celestia, the floating island, watches (and punishes) forbidden knowledge; " +
                "Khaenri'ah, a godless nation destroyed five hundred years ago, haunts the world's history. " +
                "Culture: guilds (Adventurers' Guild), festivals, knights (Knights of Favonius, Millelith, " +
                "Tenryou Commission), cuisine as a point of pride, and ley lines that record memory. Tones: " +
                "bright adventure hiding old grief; travel, festivals, ancient ruins, and companionship.",
        ),
        WorldSeed(
            "Wuthering Waves · Solaris-3",
            "Solaris-3 is a world recovering from the Lament, a catastrophe that scattered destructive " +
                "frequencies across the land and erased whole civilizations. Human enclaves — most notably " +
                "the city of Jinzhou with its Midnight Rangers — survive behind Resonance Beacons that repel " +
                "Tacet Discords: beasts of condensed negative frequency born from the Lament's echoes. " +
                "Resonators are rare people whose bodies attune to frequencies, granting elemental-flavored " +
                "powers (Aero, Electro, Glacio, Fusion, Havoc, Spectro) and the ability to absorb defeated " +
                "Tacet Discords as Echoes — wearable shapes that grant their forms and skills. Factions: " +
                "the Fractsidus, who weaponize the Lament and encourage its spread; the ruling Tianhu of " +
                "Jinzhou; wandering pioneers charting the No Man's Land; Sentinels — ancient guardian constructs " +
                "left by lost civilizations. Ruins (Sonance Caskets, reverberating tanks) hold pre-Lament " +
                "technology and memory recordings. Tones: kinetic, agile action; sound and vibration motifs; " +
                "mystery archaeology; hope rebuilt from catastrophe.",
        ),
        WorldSeed(
            "Brown Dust 2",
            "Brown Dust 2 unfolds on a mercenary-torn dark-fantasy continent where sellsword companies, " +
                "not knightly orders, decide the fate of nations. The great wars left bands of veterans — " +
                "companies like the Steel Rainbow — famous, feared, and often broke. Powers: the Veltrin " +
                "imperial remnants, ambitious duchies, the Church's inquisitors, merchant guilds that hire " +
                "whole armies, and villages that pay in crops and favors. Ancient evils stir beneath the " +
                "politics: demons bound by old contracts, witch covens, cursed relics, and forgotten labyrinths. " +
                "Adventures read as episodes: a caravan run through bandit country, a haunted mining town, an " +
                "arena conspiracy, a plague with a human source. The tone is mature but warm — loyal companions, " +
                "flirtatious banter, hard bargains, morally gray contracts, and small victories that matter. " +
                "Combat favors tactical squads: each specialist (front-line tanks, ranged snipers, healers, " +
                "magical damage-dealers) covers another's weakness; positioning and skill order win fights " +
                "that raw strength cannot.",
        ),
        WorldSeed(
            "World of Warcraft · Azeroth",
            "Azeroth is a high-adventure world split between two great factions: the Alliance (Stormwind " +
                "humans, Ironforge dwarves, Gnomeregan gnomes, night elves of Darnassus, draenei, worgen, " +
                "and more) and the Horde (Orgrimmar orcs, Thunder Bluff tauren, Undercity Forsaken, Sen'jin " +
                "trolls, blood elves of Silvermoon, goblins). Magic is real and dangerous: arcane draws on " +
                "the Twisting Nether and the ley lines; divine light empowers paladins and priests; shamanism " +
                "speaks with the elements; druids walk the Emerald Dream; warlocks bargain with demons; death " +
                "knights and demon hunters carry their own curses. Iconic threats: the undead Scourge and the " +
                "Lich King, the Burning Legion's demons, the Old Gods (C'Thun, Yogg-Saron, N'Zoth) whose " +
                "whispers corrupt from beneath the earth, and the dragon Aspects. Set-pieces: dungeons and " +
                "raids as expedition sites, battlegrounds between factions, traveling by gryphon, wyvern, boat, " +
                "and mount. Culture: taverns and quest boards, professions (blacksmithing, alchemy, " +
                "enchanting, cooking), honor and glory, and a fragile peace that war could shatter at any time.",
        ),
        WorldSeed(
            "Final Fantasy XIV · Hydaelyn",
            "Hydaelyn is a world of aether — the energy of all souls — and of shards: reflections of the " +
                "source world, sundered in an ancient calamity. The players' stage is Eorzea on the source: " +
                "the forest city-state of Gridania, the sea port Limsa Lominsa, the desert sultanate Ul'dah, " +
                "the theocracy of Ishgard, the far eastern lands of Doma and Kugane, and the Garlean Empire " +
                "ruthlessly conquering from the north with magitek. Adventurers join guilds, take jobs — " +
                "paladin, warrior, white mage, black mage, dragoon, monk, summoner, scholar, ninja, samurai, " +
                "red mage, sage, reaper, and more — each a discipline with its own soul crystal. Great threats: " +
                "primals, gods summoned from belief and aether (Ifrit, Garuda, Titan, Ramuh, Leviathan, " +
                "Bahamut, Zodiark, Hydaelyn) whose tempering enslaves worshippers; the Ascians, masked " +
                "immortals scheming to rejoin and rule the shards; the Final Days that once ended worlds. " +
                "The Warrior of Light bears the Echo — the ability to survive primal tempering and witness " +
                "the past. Themes: found fellowship (the Scions of the Seventh Dawn), duty and sacrifice, " +
                "crystals as memory, hope rebuilt after calamity. Structure: grand emotional story arcs " +
                "punctuated by small-party trials, dungeons, and raids.",
        ),
    )

    /**
     * Creates the "Worlds" codex section and seeds the bundled game-world
     * entries once — reference lore the AI can pull whenever a campaign touches
     * one of these settings. Users add more worlds with the category's "+".
     */
    suspend fun ensureWorldsCategory() {
        val categories = db.codexDao().getAllCategories()
        var category = categories.firstOrNull { it.name.equals("Worlds", ignoreCase = true) }
        if (category == null) {
            category = CodexCategoryEntity(
                id = "cat-worlds",
                scopeType = CodexScopes.TYPE,
                scopeId = CodexScopes.ID,
                name = "Worlds",
                colorHex = "#2F7BBF",
                sortOrder = (categories.maxOfOrNull { it.sortOrder } ?: 0) + 1,
                updatedAt = System.currentTimeMillis(),
            )
            db.codexDao().upsertCategory(category)
        }
        val existing = db.codexDao().getAllEntries()
            .filter { it.categoryId == category.id }
            .map { it.name.lowercase() }
            .toSet()
        worldSeeds.forEach { seed ->
            if (seed.title.lowercase() in existing) return@forEach
            val now = System.currentTimeMillis()
            db.codexDao().upsertEntry(
                CodexEntryEntity(
                    id = "entry-world-" + seed.title.lowercase()
                        .replace(Regex("[^a-z0-9]+"), "-")
                        .trim('-'),
                    categoryId = category.id,
                    scopeType = CodexScopes.TYPE,
                    scopeId = CodexScopes.ID,
                    name = seed.title,
                    docJson = Document.fromPlainText(seed.text).toJson(),
                    plainText = seed.text,
                    trackMentions = true,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    suspend fun getEntry(id: String): CodexEntryEntity? = db.codexDao().observeEntry(id).first()

    suspend fun saveEntry(entity: CodexEntryEntity) = db.codexDao().upsertEntry(entity)

    suspend fun addEntry(categoryId: String, scopeId: String = CodexScopes.ID, name: String = "New entry"): CodexEntryEntity {
        val now = System.currentTimeMillis()
        val doc = Document(listOf(Paragraph(UUID.randomUUID().toString(), listOf(Span("")))))
        val entity = CodexEntryEntity(
            id = "entry-${UUID.randomUUID()}",
            categoryId = categoryId,
            scopeType = CodexScopes.TYPE,
            scopeId = CodexScopes.ID,
            name = name,
            docJson = doc.toJson(),
            plainText = "",
            createdAt = now,
            updatedAt = now,
        )
        db.codexDao().upsertEntry(entity)
        return entity
    }

    suspend fun deleteEntry(id: String) {
        db.codexDao().deleteLore(id)
        db.codexDao().deleteEntry(id)
    }

    suspend fun updateEntryText(id: String, name: String, plainText: String) {
        updateEntry(id, name, plainText)
    }

    suspend fun updateEntry(
        id: String,
        name: String,
        plainText: String,
        aliases: List<String>? = null,
        alwaysInclude: Boolean? = null,
        trackMentions: Boolean? = null,
        caseSensitiveMatching: Boolean? = null,
        imageMediaId: String? = null,
        clearImageMediaId: Boolean = false,
        sheetJson: String? = null,
        inventoryJson: String? = null,
    ) {
        val current = db.codexDao().observeEntry(id).first() ?: return
        val doc = Document.fromPlainText(plainText)
        db.codexDao().upsertEntry(
            current.copy(
                name = name,
                docJson = doc.toJson(),
                plainText = plainText,
                aliasesJson = aliases?.let { com.ihy2ln.weaverse.core.text.encodeAliases(it) } ?: current.aliasesJson,
                alwaysInclude = alwaysInclude ?: current.alwaysInclude,
                trackMentions = trackMentions ?: current.trackMentions,
                caseSensitiveMatching = caseSensitiveMatching ?: current.caseSensitiveMatching,
                imageMediaId = when {
                    clearImageMediaId -> null
                    imageMediaId != null -> imageMediaId
                    else -> current.imageMediaId
                },
                sheetJson = sheetJson ?: current.sheetJson,
                inventoryJson = inventoryJson ?: current.inventoryJson,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }


    suspend fun setEntryMediaIds(id: String, mediaIds: List<String>) {
        val current = db.codexDao().observeEntry(id).first() ?: return
        db.codexDao().upsertEntry(
            current.copy(
                imageMediaId = com.ihy2ln.weaverse.core.media.CodexMediaIds.encode(mediaIds),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }
}

@Singleton
class PromptRepository @Inject constructor(
    private val db: WeaverseDatabase,
) {
    fun observeFolders() = db.promptDao().observeFolders()
    fun observePrompts() = db.promptDao().observeAll()
    fun observePrompt(id: String) = db.promptDao().observeById(id)
    fun observeByType(type: String) = db.promptDao().observeByType(type)

    suspend fun upsertFolder(entity: PromptFolderEntity) = db.promptDao().upsertFolder(entity)
    suspend fun upsert(entity: PromptEntity) = db.promptDao().upsert(entity)
    suspend fun deletePrompt(id: String) = db.promptDao().deleteById(id)
    suspend fun deleteFolder(id: String) = db.promptDao().deleteFolder(id)
    suspend fun getPrompt(id: String): PromptEntity? = db.promptDao().observeById(id).first()

    suspend fun createFolder(name: String, type: String = "custom"): PromptFolderEntity {
        val folder = PromptFolderEntity(
            id = "folder-${UUID.randomUUID()}",
            name = name.ifBlank { "New folder" },
            type = type,
        )
        db.promptDao().upsertFolder(folder)
        return folder
    }

    suspend fun createPrompt(
        folderId: String,
        name: String,
        type: String = "scene_beat",
        description: String = "",
        instructionsJson: String = "[]",
        advancedJson: String = "{}",
    ): PromptEntity {
        val entity = PromptEntity(
            id = "prompt-${UUID.randomUUID()}",
            folderId = folderId,
            name = name.ifBlank { "New prompt" },
            type = type,
            description = description,
            instructionsJson = instructionsJson,
            advancedJson = advancedJson,
            createdAt = System.currentTimeMillis(),
        )
        db.promptDao().upsert(entity)
        return entity
    }
}
