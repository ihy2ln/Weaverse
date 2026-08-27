package com.ihy2ln.weaverse.feature.novel.write

import com.ihy2ln.weaverse.ai.prompt.PromptComponents
import com.ihy2ln.weaverse.ai.prompt.PromptRenderContext
import com.ihy2ln.weaverse.ai.prompt.PromptRenderer
import com.ihy2ln.weaverse.ai.prompt.PromptTokenContext
import com.ihy2ln.weaverse.ai.prompt.PromptTokens
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.CodexEntryEntity
import com.ihy2ln.weaverse.data.db.entities.SceneEntity
import com.ihy2ln.weaverse.data.repo.PromptRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class LibraryPromptBundle(
    val promptId: String?,
    val systemInstructions: String,
    val historyMessages: List<Pair<String, String>> = emptyList(),
    val finalUserMessage: String? = null,
)

@Singleton
class WritePromptAssembler @Inject constructor(
    private val promptRepository: PromptRepository,
    private val db: WeaverseDatabase,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun defaultPromptFor(commandId: String): String = when (commandId) {
        "scene_beat" -> "Write a pivotal scene beat where something important changes."
        "describe_image" -> "Describe the attached picture and turn it into scene-beat prose."
        "continue" -> "Continue writing from the current scene."
        "expand", "extend" -> "Expand the current passage with richer detail."
        "shorten" -> "Shorten the passage while preserving voice and meaning."
        "replace" -> "Rewrite the passage with improved clarity and flow."
        else -> "Continue the scene."
    }

    suspend fun libraryPromptBundle(
        commandId: String,
        renderCtx: PromptRenderContext,
    ): LibraryPromptBundle {
        val type = when (commandId) {
            "extend" -> "expand"
            else -> commandId
        }
        val prompts = promptRepository.observeByType(type).first()
            .ifEmpty { promptRepository.observeByType(commandId).first() }
        val prompt = prompts.firstOrNull { it.isDefault } ?: prompts.firstOrNull()
        if (prompt == null) {
            return LibraryPromptBundle(
                promptId = null,
                systemInstructions = PromptTokens.apply(defaultPromptFor(commandId), tokenContext(renderCtx)),
            )
        }
        val rendered = PromptRenderer.render(prompt, renderCtx)
        val advanced = runCatching { json.parseToJsonElement(prompt.advancedJson).jsonObject }.getOrNull()
        val guidance = advanced?.get("guidance")?.jsonPrimitive?.contentOrNull.orEmpty()
        val bias = advanced?.get("bias")?.jsonPrimitive?.contentOrNull.orEmpty()
        val systemInstructions = buildString {
            append(rendered.systemText.ifBlank { prompt.description })
            if (guidance.isNotBlank()) append("\n\nGuidance: ").append(guidance)
            if (bias.isNotBlank()) append("\n\nBias: ").append(bias)
        }
        val lastTurn = rendered.messages.lastOrNull()
        val endsInUserTurn = lastTurn?.first == "user"
        return LibraryPromptBundle(
            promptId = prompt.id,
            systemInstructions = systemInstructions,
            historyMessages = if (endsInUserTurn) rendered.messages.dropLast(1) else rendered.messages,
            finalUserMessage = if (endsInUserTurn) lastTurn?.second else null,
        )
    }

    suspend fun buildPromptRenderContext(
        bookId: String,
        sceneText: String,
        scene: SceneEntity?,
        entries: List<CodexEntryEntity>,
        codexBlock: String,
        message: String = "",
        outputWords: Int = 200,
    ): PromptRenderContext {
        val book = db.bookDao().getById(bookId)
        val series = book?.seriesId?.let { id -> db.seriesDao().observeById(id).first() }
        val povCharacter = scene?.povCharacterId
            ?.let { id -> entries.firstOrNull { it.id == id }?.name }
            .orEmpty()
        val componentBlocks = PromptComponents.build(promptRepository, codexBlock, book)
        return PromptRenderContext(
            novelTense = book?.tense?.ifBlank { "past tense" } ?: "past tense",
            novelTitle = book?.title.orEmpty(),
            seriesTitle = series?.title.orEmpty(),
            seriesDescription = listOfNotNull(
                series?.description?.takeIf { it.isNotBlank() },
                series?.premise?.takeIf { it.isNotBlank() },
            ).joinToString("\n"),
            pov = scene?.pov.orEmpty(),
            povType = scene?.pov.orEmpty(),
            povCharacter = povCharacter,
            sceneFullTextCurrent = sceneText,
            textBefore = sceneText,
            message = message,
            outputWords = outputWords,
            componentBlocks = componentBlocks,
        )
    }

    fun tokenContext(ctx: PromptRenderContext): PromptTokenContext = PromptTokenContext(
        tense = ctx.novelTense,
        bookTitle = ctx.novelTitle,
        seriesTitle = ctx.seriesTitle,
        seriesDescription = ctx.seriesDescription,
    )

    fun buildPovSystemBlock(
        scene: SceneEntity?,
        entries: List<CodexEntryEntity>,
    ): String {
        if (scene == null) return ""
        val characterName = scene.povCharacterId
            ?.let { id -> entries.firstOrNull { it.id == id }?.name }
            .orEmpty()
        if (scene.pov.isBlank() && characterName.isBlank()) return ""
        return buildString {
            append("Point of view: ")
            append(scene.pov.ifBlank { "unspecified" })
            if (characterName.isNotBlank()) {
                append(" — focal character: ")
                append(characterName)
            }
            append(". Write consistently in this POV.")
        }
    }

    fun buildUserMessage(
        overlay: AiOverlayState,
        sceneText: String,
        hasImage: Boolean = false,
    ): String = buildString {
        val userBeat = overlay.prompt.trim()
        if (hasImage) {
            append(
                if (userBeat.isNotBlank()) {
                    "Using the attached image, describe it and write scene-beat prose. Notes: $userBeat"
                } else {
                    defaultPromptFor("describe_image")
                },
            )
            append("\n\n")
        } else if (userBeat.isNotBlank()) {
            append(userBeat)
            append("\n\n")
        } else {
            append(defaultPromptFor(overlay.commandId))
            append("\n\n")
        }
        append("Target length: about ${overlay.outputWords} words.\n\n")
        append("Current scene:\n")
        append(sceneText.take(6000))
    }
}
