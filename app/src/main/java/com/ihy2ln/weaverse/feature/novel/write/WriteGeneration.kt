package com.ihy2ln.weaverse.feature.novel.write

import android.util.Base64
import com.ihy2ln.weaverse.ai.AIError
import com.ihy2ln.weaverse.ai.ImageAttachment
import com.ihy2ln.weaverse.ai.context.AssembledPrompt
import com.ihy2ln.weaverse.ai.context.ContextBuilder
import com.ihy2ln.weaverse.ai.context.ContextBuildRequest
import com.ihy2ln.weaverse.ai.context.ContextMeter
import com.ihy2ln.weaverse.ai.context.ContextMeterReading
import com.ihy2ln.weaverse.core.text.Block
import com.ihy2ln.weaverse.core.text.Paragraph
import com.ihy2ln.weaverse.core.text.insertGeneratedProseAfter
import com.ihy2ln.weaverse.core.text.replaceRangeText
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.SceneEntity
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

data class WriteStreamPlan(
    val overlay: AiOverlayState,
    val assembled: AssembledPrompt,
    val userMessage: String,
    val maxTokens: Int,
    val imageAttachments: List<ImageAttachment>,
)

data class WriteSummarizePlan(
    val assembled: AssembledPrompt,
    val userMessage: String,
    val maxTokens: Int = 400,
)

sealed class WriteGenerationPrep {
    data class Ready(val plan: WriteStreamPlan) : WriteGenerationPrep()
    data class Failed(val message: String) : WriteGenerationPrep()
}

@Singleton
class WriteGeneration @Inject constructor(
    private val promptAssembler: WritePromptAssembler,
    private val db: WeaverseDatabase,
) {
    private val contextBuilder = ContextBuilder()

    fun meter(sceneText: String, extraPrompt: String, limitTokens: Int): ContextMeterReading {
        val assembled = AssembledPrompt(
            systemBlocks = listOf(sceneText),
            messages = listOfNotNull(extraPrompt.takeIf { it.isNotBlank() }?.let { "user" to it }),
            usedEntries = emptyList(),
            tokenBreakdown = emptyList(),
        )
        return ContextMeter.reading(assembled, extraUser = "", limitTokens = limitTokens)
    }

    fun formatError(err: Throwable): String = when (err) {
        is AIError.HttpFailure -> "HTTP ${err.statusCode}: ${err.message}"
        is AIError -> err.message.orEmpty()
        else -> err.message ?: err.toString()
    }

    fun loadImageAttachment(path: String): ImageAttachment? {
        val file = File(path)
        if (!file.exists() || file.length() == 0L) return null
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        val mime = mimeForExtension(file.extension)
        return ImageAttachment(
            mimeType = mime,
            base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP),
        )
    }

    fun acceptIntoBlocks(blocks: List<Block>, overlay: AiOverlayState, text: String): List<Block> {
        // Blocks can shift while the generation streams; re-resolve the anchor by
        // block id so the prose still lands next to the beat/selection it came from.
        val anchored = overlay.anchorBlockId
            ?.let { id -> blocks.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
        val replaceIndex = overlay.replaceBlockIndex?.let { anchored ?: it }
        val replaceStart = overlay.replaceStart
        val replaceEnd = overlay.replaceEnd
        if (replaceIndex != null && replaceStart != null && replaceEnd != null) {
            val block = blocks.getOrNull(replaceIndex) as? Paragraph
            if (block != null) {
                val next = blocks.toMutableList()
                val p = next[replaceIndex] as Paragraph
                next[replaceIndex] = p.copy(
                    spans = p.spans.replaceRangeText(replaceStart, replaceEnd, text),
                )
                return next
            }
        }
        return blocks.insertGeneratedProseAfter(
            insertAfterIndex = anchored ?: overlay.insertAfterIndex,
            generatedText = text,
            beatPrompt = overlay.prompt.takeIf { overlay.commandId == "scene_beat" },
        )
    }

    suspend fun prepareStream(
        overlay: AiOverlayState,
        sceneText: String,
        scene: SceneEntity?,
        bookId: String,
        hasApiKey: Boolean,
        modelSupportsImages: Boolean,
    ): WriteGenerationPrep {
        if (!hasApiKey) {
            return WriteGenerationPrep.Failed(AIError.NoApiKey().message.orEmpty())
        }
        val hasImage = !overlay.imageMediaId.isNullOrBlank() && !overlay.imagePath.isNullOrBlank()
        if (hasImage && !modelSupportsImages) {
            return WriteGenerationPrep.Failed(
                "Selected model does not support images. Pick a Vision-capable model in Settings, or clear the attached picture.",
            )
        }
        val commandForPrompt = if (hasImage) "describe_image" else overlay.commandId
        val entries = db.codexDao().observeEntries(bookId).first()
        val assembled = contextBuilder.build(
            entries,
            ContextBuildRequest(
                scanText = sceneText + " " + overlay.prompt + " " + (scene?.pov.orEmpty()),
                userMessage = overlay.prompt,
            ),
        )
        val renderCtx = promptAssembler.buildPromptRenderContext(
            bookId = bookId,
            sceneText = sceneText,
            scene = scene,
            entries = entries,
            codexBlock = assembled.codexBlock,
            message = overlay.prompt,
            outputWords = overlay.outputWords,
        )
        val fresh = promptAssembler.libraryPromptBundle(commandForPrompt, renderCtx).let { bundle ->
            if (bundle.systemInstructions.isBlank() && hasImage) {
                promptAssembler.libraryPromptBundle("scene_beat", renderCtx)
            } else {
                bundle
            }
        }
        val activeOverlay = overlay.copy(
            systemInstructions = fresh.systemInstructions.ifBlank { overlay.systemInstructions },
            promptId = fresh.promptId ?: overlay.promptId,
        )
        val usingMultiMessagePrompt = fresh.historyMessages.isNotEmpty() || fresh.finalUserMessage != null
        val systemBlocks = buildList {
            if (usingMultiMessagePrompt) {
                add("You are a creative writing assistant.")
            } else {
                addAll(assembled.systemBlocks)
            }
            val povBlock = promptAssembler.buildPovSystemBlock(scene, entries)
            if (povBlock.isNotBlank()) add(povBlock)
            if (activeOverlay.systemInstructions.isNotBlank()) {
                add("Prompt instructions:\n${activeOverlay.systemInstructions}")
            }
            if (hasImage) {
                add(
                    "Describe the attached picture as prose suitable for a scene beat. " +
                        "Turn visual detail into narrative text; do not mention that you are describing an image.",
                )
            }
        }
        val maxTokens = (activeOverlay.outputWords * 1.4).toInt().coerceIn(64, 8192)
        val userMessage = fresh.finalUserMessage
            ?: promptAssembler.buildUserMessage(activeOverlay, sceneText, hasImage)
        val imageAttachments = if (hasImage) {
            listOfNotNull(loadImageAttachment(activeOverlay.imagePath!!))
        } else {
            emptyList()
        }
        return WriteGenerationPrep.Ready(
            WriteStreamPlan(
                overlay = activeOverlay,
                assembled = AssembledPrompt(
                    systemBlocks = systemBlocks,
                    messages = fresh.historyMessages,
                    usedEntries = assembled.usedEntries,
                    tokenBreakdown = assembled.tokenBreakdown,
                ),
                userMessage = userMessage,
                maxTokens = maxTokens,
                imageAttachments = imageAttachments,
            ),
        )
    }

    suspend fun prepareSummarize(
        sceneText: String,
        scene: SceneEntity,
        bookId: String,
        hasApiKey: Boolean,
    ): Result<WriteSummarizePlan> {
        if (!hasApiKey) return Result.failure(AIError.NoApiKey())
        if (sceneText.isBlank()) return Result.failure(IllegalStateException("Nothing to summarize yet"))
        val entries = db.codexDao().observeEntries(bookId).first()
        val assembled = contextBuilder.build(
            entries,
            ContextBuildRequest(scanText = sceneText, userMessage = ""),
        )
        val renderCtx = promptAssembler.buildPromptRenderContext(
            bookId = bookId,
            sceneText = sceneText,
            scene = scene,
            entries = entries,
            codexBlock = assembled.codexBlock,
        )
        val fresh = promptAssembler.libraryPromptBundle("summarize", renderCtx)
        return Result.success(
            WriteSummarizePlan(
                assembled = AssembledPrompt(
                    systemBlocks = listOf(fresh.systemInstructions),
                    messages = fresh.historyMessages,
                    usedEntries = assembled.usedEntries,
                    tokenBreakdown = assembled.tokenBreakdown,
                ),
                userMessage = fresh.finalUserMessage ?: "Summarize the scene above in a few sentences.",
            ),
        )
    }

    companion object {
        fun mimeForExtension(ext: String): String = when (ext.lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "image/jpeg"
        }
    }
}
