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
import com.ihy2ln.weaverse.core.text.plainText
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
        check(overlay.anchorBlockId == null || anchored != null) { "The target passage was removed. Regenerate for the current scene." }
        if (overlay.sourceParagraphText != null && replaceIndex != null) {
            val current = (blocks.getOrNull(replaceIndex) as? Paragraph)?.spans?.plainText()
            check(current == overlay.sourceParagraphText) { "The passage changed after this candidate was requested. Your edits were kept; regenerate before replacing." }
        }
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
        if (overlay.cursorOffset != null && anchored != null) {
            val paragraph = blocks[anchored] as? Paragraph ?: error("The target is no longer a paragraph.")
            check(paragraph.plainText() == overlay.sourceParagraphText) { "The insertion passage changed. Retarget at the current cursor; your candidate is kept." }
            val offset = overlay.cursorOffset
            check(offset in 0..paragraph.plainText().length) { "Invalid cursor target." }
            return blocks.toMutableList().also { it[anchored] = paragraph.copy(spans = paragraph.spans.replaceRangeText(offset, offset, text)) }
        }
        return blocks.insertGeneratedProseAfter(
            insertAfterIndex = anchored ?: overlay.insertAfterIndex,
            generatedText = text,
            beatPrompt = null,
        )
    }

    suspend fun prepareStream(
        overlay: AiOverlayState,
        sceneText: String,
        scene: SceneEntity?,
        bookId: String,
        hasApiKey: Boolean,
        modelSupportsImages: Boolean,
        contextLimit: Int = ContextMeter.DEFAULT_LIMIT,
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
        val book = db.bookDao().getById(bookId)
        val writing = db.novelWritingDao().settings(bookId)
        val entries = db.codexDao().getAllEntries().filter { !it.disabled && (it.scopeId == bookId || it.scopeId == book?.seriesId || it.scopeId == "global") }
        val pinned = scene?.id?.let { db.novelMediaDao().contextIds(it).toSet() }.orEmpty()
        val maxTokens = (overlay.outputWords * 1.7 + 192).toInt().coerceIn(192, 8192)
        val attachments = if (hasImage) listOfNotNull(loadImageAttachment(overlay.imagePath!!)) else emptyList()
        if (hasImage && attachments.isEmpty()) return WriteGenerationPrep.Failed("Attached image is missing or unreadable. Reattach it or remove the attachment.")
        val excluded = mutableSetOf<String>()
        while (true) {
            val context = contextBuilder.build(entries, ContextBuildRequest(
                scanText = sceneText + " " + overlay.prompt + " " + scene?.pov.orEmpty(),
                manualIncludeIds = pinned, manualExcludeIds = excluded,
                maxContextTokens = contextLimit, reserveResponseTokens = maxTokens,
            ))
            val included = entries.filter { entry -> context.usedEntries.any { it.entryId == entry.id } }
            val render = promptAssembler.buildPromptRenderContext(bookId, sceneText, scene, included,
                context.codexBlock, overlay.prompt, overlay.outputWords)
            val fresh = try {
                promptAssembler.libraryPromptBundle(overlay.commandId, render, overlay.promptIds)
            } catch (error: IllegalStateException) {
                return WriteGenerationPrep.Failed(error.message.orEmpty())
            }
            val system = buildList {
                add("You are a creative writing assistant. Return only the requested prose.")
                add(fresh.systemInstructions)
                val renderedText = fresh.systemInstructions + fresh.historyMessages.joinToString { it.second } + fresh.finalUserMessage.orEmpty()
                if (context.codexBlock.isNotBlank() && !renderedText.contains(context.codexBlock)) add(context.codexBlock)
                promptAssembler.buildPovSystemBlock(scene, included).takeIf { it.isNotBlank() }?.let(::add)
                book?.styleGuide?.takeIf { it.isNotBlank() }?.let { add("Style guide:\n$it") }
                writing?.memory?.takeIf { it.isNotBlank() }?.let { add("Book memory (persistent facts):\n$it") }
                writing?.authorNote?.takeIf { it.isNotBlank() }?.let { add("Author's note (current direction):\n$it") }
            }
            val user = buildString {
                fresh.finalUserMessage?.let { appendLine(it) }
                appendLine(promptAssembler.buildUserMessage(overlay, sceneText, hasImage))
                appendLine("Action: ${overlay.commandId}")
                appendLine("Explicit instruction: ${overlay.prompt}")
                if (overlay.replaceBlockIndex != null) {
                    val source = overlay.sourceParagraphText.orEmpty()
                    val start = overlay.replaceStart ?: 0
                    val end = overlay.replaceEnd ?: 0
                    if (start == end || start < 0 || end > source.length) return WriteGenerationPrep.Failed("Select a passage before replacing text.")
                    appendLine("Selected target (replace only this text):\n${source.substring(start, end)}")
                } else appendLine("Target: insert new prose at the saved cursor; do not rewrite the whole scene.")
            }
            val assembled = AssembledPrompt(
                systemBlocks = com.ihy2ln.weaverse.ai.prompt.PromptAddOns.applyTo(system),
                messages = fresh.historyMessages, usedEntries = context.usedEntries,
                tokenBreakdown = emptyList(), droppedEntryIds = (context.droppedEntryIds + excluded).distinct(),
                codexBlock = context.codexBlock,
            )
            // Include message framing and a conservative image allowance in the estimate.
            val used = ContextMeter.used(assembled, user) + 64 + attachments.size * 4096
            if (used + maxTokens <= contextLimit) return WriteGenerationPrep.Ready(WriteStreamPlan(
                overlay.copy(systemInstructions = fresh.systemInstructions, promptIds = fresh.promptIds,
                    contextMeter = ContextMeterReading(used + maxTokens, contextLimit)),
                assembled, user, maxTokens, attachments,
            ))
            val drop = context.usedEntries.lastOrNull { it.entryId !in pinned } ?: context.usedEntries.lastOrNull()
            if (drop == null) return WriteGenerationPrep.Failed(
                "Required scene, instructions and target need about ${used + maxTokens} tokens; this model allows $contextLimit. Choose a larger-context model, shorten the instructions/scene, reduce output length or remove the attachment. Nothing was sent.")
            excluded.add(drop.entryId)
        }
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
