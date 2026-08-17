package com.ihy2ln.weaverse.feature.settings.backup

import com.ihy2ln.weaverse.core.export.ExportNode
import com.ihy2ln.weaverse.core.export.ExportOutline
import com.ihy2ln.weaverse.core.text.wordCount
import com.ihy2ln.weaverse.data.db.entity.SceneStatus
import kotlinx.serialization.Serializable

/**
 * A portable JSON snapshot of one book's structure + codex (spec §12
 * export/import — "no cloud sync" means this local file *is* the backup/
 * transfer mechanism). Deliberately its own DTO hierarchy rather than
 * marking the Room entities themselves `@Serializable` — keeps this format
 * stable and independent of schema changes, at the cost of scene content
 * round-tripping as plain text only (docJson/beatsJson/media links aren't
 * captured — see BUILD_NOTES "Phase 12 deviations/gaps").
 */
@Serializable
data class SceneBackup(
    val title: String,
    val plainText: String,
    val summary: String,
    val wordCount: Int,
    val status: SceneStatus,
    val pov: String,
    val sortOrder: Int,
)

@Serializable
data class ChapterBackup(
    val title: String,
    val summary: String,
    val sortOrder: Int,
    val scenes: List<SceneBackup>,
)

@Serializable
data class ActBackup(
    val title: String,
    val sortOrder: Int,
    val chapters: List<ChapterBackup>,
)

@Serializable
data class CodexEntryBackup(
    val name: String,
    val aliases: List<String>,
    val plainText: String,
    val colorHex: String?,
    val alwaysInclude: Boolean,
)

@Serializable
data class CodexCategoryBackup(
    val name: String,
    val colorHex: String,
    val entries: List<CodexEntryBackup>,
)

@Serializable
data class BookBackup(
    val formatVersion: Int = 1,
    val title: String,
    val genre: String,
    val pov: String,
    val tense: String,
    val styleGuide: String,
    val acts: List<ActBackup>,
    val codexCategories: List<CodexCategoryBackup>,
)

/** Manuscript only — codex doesn't map onto a linear "book" outline; it gets its own
 * export (see `CodexBackup.kt`). Empty chapter/scene bodies are skipped. */
fun BookBackup.toOutline(): ExportOutline = ExportOutline(
    title = title,
    nodes = acts.flatMap { act ->
        buildList {
            add(ExportNode.Heading(1, act.title))
            act.chapters.forEach { chapter ->
                add(ExportNode.Heading(2, chapter.title))
                if (chapter.summary.isNotBlank()) add(ExportNode.Paragraph(chapter.summary))
                chapter.scenes.forEach { scene ->
                    add(ExportNode.Heading(3, scene.title))
                    if (scene.plainText.isNotBlank()) add(ExportNode.Paragraph(scene.plainText))
                }
            }
        }
    },
)

/**
 * Reconstructs a [BookBackup] from a generic outline (Markdown/HTML/DOCX import) — inherently
 * best-effort compared to JSON: level-1 headings become acts, level-2 become chapters, level-3+
 * become scenes, and any paragraph text is attributed to whichever scene/chapter most recently
 * opened (a paragraph before any level-3 heading becomes that chapter's summary instead). A
 * heading deeper than 1/2/3 with no open parent auto-creates a placeholder "Act 1"/"Chapter 1"
 * so nothing from the source document is silently dropped. Status/POV/word-count-from-Room and
 * codex are lost in this direction — only JSON round-trips those.
 */
fun ExportOutline.toBookBackup(): BookBackup {
    class SceneAcc(val title: String) {
        val lines = mutableListOf<String>()
    }
    class ChapterAcc(val title: String) {
        var summary: String = ""
        val scenes = mutableListOf<SceneAcc>()
    }
    class ActAcc(val title: String) {
        val chapters = mutableListOf<ChapterAcc>()
    }

    val acts = mutableListOf<ActAcc>()
    var currentAct: ActAcc? = null
    var currentChapter: ChapterAcc? = null
    var currentScene: SceneAcc? = null

    fun ensureAct(): ActAcc = currentAct ?: ActAcc("Act 1").also { acts.add(it); currentAct = it }
    fun ensureChapter(): ChapterAcc = currentChapter ?: ChapterAcc("Chapter 1").also { ensureAct().chapters.add(it); currentChapter = it }

    nodes.forEach { node ->
        when (node) {
            is ExportNode.Heading -> when (node.level) {
                1 -> {
                    currentAct = ActAcc(node.text).also { acts.add(it) }
                    currentChapter = null
                    currentScene = null
                }
                2 -> {
                    currentChapter = ChapterAcc(node.text).also { ensureAct().chapters.add(it) }
                    currentScene = null
                }
                else -> {
                    currentScene = SceneAcc(node.text).also { ensureChapter().scenes.add(it) }
                }
            }
            is ExportNode.Paragraph -> {
                val scene = currentScene
                if (scene != null) {
                    scene.lines.add(node.text)
                } else if (currentChapter != null) {
                    val chapter = ensureChapter()
                    chapter.summary = listOf(chapter.summary, node.text).filter { it.isNotBlank() }.joinToString("\n")
                }
            }
        }
    }

    return BookBackup(
        title = title,
        genre = "",
        pov = "",
        tense = "",
        styleGuide = "",
        acts = acts.mapIndexed { actIndex, act ->
            ActBackup(
                title = act.title,
                sortOrder = actIndex,
                chapters = act.chapters.mapIndexed { chapterIndex, chapter ->
                    ChapterBackup(
                        title = chapter.title,
                        summary = chapter.summary,
                        sortOrder = chapterIndex,
                        scenes = chapter.scenes.mapIndexed { sceneIndex, scene ->
                            val text = scene.lines.joinToString("\n")
                            SceneBackup(
                                title = scene.title,
                                plainText = text,
                                summary = "",
                                wordCount = text.wordCount(),
                                status = SceneStatus.Draft,
                                pov = "",
                                sortOrder = sceneIndex,
                            )
                        },
                    )
                },
            )
        },
        codexCategories = emptyList(),
    )
}
