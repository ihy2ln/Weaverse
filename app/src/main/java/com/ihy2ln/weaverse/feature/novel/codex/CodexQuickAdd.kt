package com.ihy2ln.weaverse.feature.novel.codex

import com.ihy2ln.weaverse.ai.AiGenerationService
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.RpItem
import com.ihy2ln.weaverse.data.db.entities.encodeItems
import com.ihy2ln.weaverse.data.repo.CodexRepository
import com.ihy2ln.weaverse.feature.roleplay.characters.RpgCharacterSheet
import com.ihy2ln.weaverse.feature.roleplay.characters.encodeRpgSheet
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** What a `!kind …` command produced. */
data class CodexQuickAddResult(
    val entryId: String,
    val name: String,
    val kind: CodexEntryKind,
    /** The prose to drop into the manuscript or the chat — rendered from the entry. */
    val text: String,
    /** "Added Location · Blackreach Harbour to the Codex". */
    val status: String,
)

/**
 * Runs a `!location …` style command: one AI call fills the codex template for
 * that kind, the entry (and, for the kinds that carry, its roster character and
 * pack) is written, and the prose handed back is *rendered from that entry*.
 * The text and the codex record are the same fields, so they can never drift.
 */
@Singleton
class CodexQuickAdd @Inject constructor(
    private val aiGeneration: AiGenerationService,
    private val codexRepository: CodexRepository,
    private val rosterLink: CodexRosterLink,
    private val db: WeaverseDatabase,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Serializable
    private data class Draft(
        val name: String = "",
        val description: String = "",
        val personality: String = "",
        val gear: List<String> = emptyList(),
        val fields: JsonObject = JsonObject(emptyMap()),
    )

    class NoApiKey : IllegalStateException("Add an API key in Settings to use ! commands")
    class NothingGenerated : IllegalStateException("The model did not return a usable entry")

    /**
     * @param command the parsed `!kind brief`
     * @param sceneContext nearby prose (the open scene, the last few messages) so
     *   the entry fits what is actually being written
     */
    suspend fun run(command: CodexBangCommand, sceneContext: String = ""): CodexQuickAddResult {
        if (!aiGeneration.hasApiKey()) throw NoApiKey()
        val draft = requestDraft(command, sceneContext) ?: throw NothingGenerated()
        val name = draft.name.trim().ifBlank { command.brief.take(60).ifBlank { command.kind.label } }
        val category = codexRepository.ensureCategory(categoryFor(command.kind))
        val entry = codexRepository.addEntry(category.id, name = name)

        val text: String
        var sheetJson: String? = null
        if (command.kind == CodexEntryKind.Character) {
            val rpgSheet = decodeFields(RpgCharacterSheet.serializer(), draft) ?: RpgCharacterSheet()
            text = CodexEntryText.renderCharacter(
                name = name,
                sheet = rpgSheet,
                description = draft.description,
                personality = draft.personality,
                gear = draft.gear,
            )
            writeRosterCharacter(entry.id, name, rpgSheet, draft)
        } else {
            val sheet = sheetFor(command.kind, draft)
            text = CodexEntryText.render(command.kind, name, sheet)
            sheetJson = encodeCodexSheet(sheet.copy(kind = command.kind.name))
        }

        // The entry stores exactly the prose the writer just received.
        codexRepository.updateEntry(
            id = entry.id,
            name = name,
            plainText = text,
            sheetJson = sheetJson,
        )
        return CodexQuickAddResult(
            entryId = entry.id,
            name = name,
            kind = command.kind,
            text = text,
            status = "Added ${command.kind.label} · $name to the Codex",
        )
    }

    /** Builds the kind's sheet from the model's `fields` object. */
    private fun sheetFor(kind: CodexEntryKind, draft: Draft): CodexSheetData = when (kind) {
        CodexEntryKind.Location -> CodexSheetData(
            location = decodeFields(LocationSheet.serializer(), draft)
                ?: LocationSheet(description = draft.description),
        )
        CodexEntryKind.Item -> CodexSheetData(
            item = decodeFields(ItemSheet.serializer(), draft)
                ?: ItemSheet(description = draft.description),
        )
        CodexEntryKind.Lore -> CodexSheetData(
            lore = decodeFields(LoreSheet.serializer(), draft)
                ?: LoreSheet(explanation = draft.description),
        )
        else -> CodexSheetData(
            other = decodeFields(OtherSheet.serializer(), draft)
                ?: OtherSheet(description = draft.description),
        )
    }.let { sheet ->
        // A model that answered only in `description` still lands in the body field.
        if (sheet.entryTextFor(kind).isBlank()) sheet.seededFrom(kind, draft.description) else sheet
    }

    private fun <T> decodeFields(serializer: KSerializer<T>, draft: Draft): T? =
        runCatching { json.decodeFromJsonElement(serializer, draft.fields) }.getOrNull()

    /** Characters become a real roster character with their sheet and starting gear. */
    private suspend fun writeRosterCharacter(
        entryId: String,
        name: String,
        sheet: RpgCharacterSheet,
        draft: Draft,
    ) {
        val character = rosterLink.ensureCharacterFor(entryId) ?: return
        val items = draft.gear
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { RpItem(id = "item-${UUID.randomUUID()}", name = it, quantity = 1) }
        db.roleplayDao().upsertCharacter(
            character.copy(
                name = name,
                description = draft.description,
                personality = draft.personality,
                extensionsJson = encodeRpgSheet(character.extensionsJson, sheet),
                inventoryJson = encodeItems(items),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun categoryFor(kind: CodexEntryKind): String = when (kind) {
        CodexEntryKind.Character -> "Characters"
        CodexEntryKind.Location -> "Locations"
        CodexEntryKind.Item -> "Objects/Items"
        CodexEntryKind.Lore -> "Lore"
        CodexEntryKind.Other -> "Notes"
    }

    private suspend fun requestDraft(command: CodexBangCommand, sceneContext: String): Draft? {
        val fieldNames = fieldNamesFor(command.kind)
        val instruction = buildString {
            append("You are the codex keeper for a writing app. Write one new ")
            append("${command.kind.label.lowercase()} entry. Reply with ONLY a JSON object, no prose:\n")
            append("{\"name\":\"\",\"description\":\"\"")
            if (command.kind == CodexEntryKind.Character) {
                append(",\"personality\":\"\",\"gear\":[\"\"]")
            }
            append(",\"fields\":{}}\n")
            append("\"fields\" uses exactly these keys, and only these keys:\n")
            append(fieldNames.joinToString(", "))
            append("\n")
            append("Rules:\n")
            append("- PRE-FILL EVERYTHING: fill EVERY single field with rich, specific, invented detail. ")
            append("The user prefers deleting what they do not want over adding missing content themselves. ")
            append("Only omit a field when inventing it would contradict the brief or the context.\n")
            append("- Write warm, readable prose in each text field — full sentences, not notes.\n")
            append("- Numeric fields must be plain numbers, booleans plain true/false.\n")
            when (command.kind) {
                CodexEntryKind.Location -> append(
                    "- A place needs its census: population, who lives there, which factions hold it.\n",
                )
                CodexEntryKind.Item -> append(
                    "- If the object has game statistics, fill the stat fields and set hasStats true; " +
                        "leave them out for a mundane object.\n",
                )
                CodexEntryKind.Lore -> append(
                    "- Lore is the long one: the explanation field should run several paragraphs.\n",
                )
                CodexEntryKind.Character -> append(
                    "- Give a playable sheet: class, species, level, hit points, armor class and ability scores.\n",
                )
                CodexEntryKind.Other -> append("- Use customFields for anything the fixed fields miss.\n")
            }
            append("\nBrief: ${command.brief.ifBlank { "invent something that fits the story so far" }}\n")
            if (sceneContext.isNotBlank()) {
                append("\nWhat is being written right now, for tone and continuity:\n")
                append("<context>\n${sceneContext.trim().takeLast(3000)}\n</context>")
            }
        }
        val result = aiGeneration.complete(
            userMessage = instruction,
            maxTokens = if (command.kind == CodexEntryKind.Lore) 3200 else 2400,
            temperature = 0.8,
        )
        return parse(result.text)
    }

    /**
     * AI pre-fill for an entry that already exists (created via "+"): invents a
     * brief from the entry's own name and category, then writes the same rich
     * sheet a `!` command would have produced. Silent no-op without an API key.
     */
    suspend fun fillExisting(entryId: String): Boolean {
        if (!aiGeneration.hasApiKey()) return false
        val entry = codexRepository.getEntry(entryId) ?: return false
        val category = db.codexDao().getAllCategories().firstOrNull { it.id == entry.categoryId }
        val kind = CodexEntryKind.forCategory(category?.name.orEmpty())
        val command = CodexBangCommand(
            kind = kind,
            keyword = "!${kind.name.lowercase()}",
            brief = entry.name.ifBlank { category?.name.orEmpty() }.ifBlank { kind.label },
        )
        val draft = requestDraft(command, sceneContext = entry.plainText) ?: return false
        val name = draft.name.trim().ifBlank { entry.name }
        val text: String
        var sheetJson: String? = null
        if (kind == CodexEntryKind.Character) {
            val rpgSheet = decodeFields(RpgCharacterSheet.serializer(), draft) ?: RpgCharacterSheet()
            text = CodexEntryText.renderCharacter(
                name = name,
                sheet = rpgSheet,
                description = draft.description,
                personality = draft.personality,
                gear = draft.gear,
            )
            writeRosterCharacter(entryId, name.ifBlank { entry.name }, rpgSheet, draft)
        } else {
            val sheet = sheetFor(kind, draft)
            text = CodexEntryText.render(kind, name, sheet)
            sheetJson = encodeCodexSheet(sheet.copy(kind = kind.name))
        }
        codexRepository.updateEntry(
            id = entryId,
            name = name,
            plainText = text,
            sheetJson = sheetJson,
        )
        return true
    }

    /** The template's own field names, so the model fills the codex sheet one-to-one. */
    private fun fieldNamesFor(kind: CodexEntryKind): List<String> {
        val descriptor = when (kind) {
            CodexEntryKind.Character -> RpgCharacterSheet.serializer().descriptor
            CodexEntryKind.Location -> LocationSheet.serializer().descriptor
            CodexEntryKind.Item -> ItemSheet.serializer().descriptor
            CodexEntryKind.Lore -> LoreSheet.serializer().descriptor
            CodexEntryKind.Other -> OtherSheet.serializer().descriptor
        }
        return (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }
    }

    /** Models wrap JSON in prose or code fences; take the outermost braces. */
    private fun parse(raw: String): Draft? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { json.decodeFromString<Draft>(raw.substring(start, end + 1)) }.getOrNull()
    }
}
