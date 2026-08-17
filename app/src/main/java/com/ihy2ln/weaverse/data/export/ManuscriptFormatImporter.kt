package com.ihy2ln.weaverse.data.export

import com.ihy2ln.weaverse.core.text.Document
import com.ihy2ln.weaverse.core.text.plainText
import com.ihy2ln.weaverse.core.text.toJson
import com.ihy2ln.weaverse.core.text.wordCount
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.ActEntity
import com.ihy2ln.weaverse.data.db.entities.BookEntity
import com.ihy2ln.weaverse.data.db.entities.ChapterEntity
import com.ihy2ln.weaverse.data.db.entities.SceneEntity
import com.ihy2ln.weaverse.data.db.entities.SeriesEntity
import com.ihy2ln.weaverse.data.export.novelcrafter.NovelcrafterZipParser
import com.ihy2ln.weaverse.sync.novelcrafter.DocxPlainText
import com.ihy2ln.weaverse.sync.novelcrafter.WordHeadingHeuristics
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Imports manuscript-only formats (Markdown / HTML / DOCX) as a **new** book.
 * Legacy `.doc` (OLE) is not supported — return a clear error from the caller.
 */
@Singleton
class ManuscriptFormatImporter @Inject constructor(
    private val db: WeaverseDatabase,
) {
    data class Result(val bookId: String, val bookTitle: String, val sceneCount: Int)

    suspend fun importMarkdown(bytes: ByteArray, suggestedTitle: String = "Imported Markdown"): Result {
        val text = bytes.toString(Charsets.UTF_8)
        val (title, _, acts) = NovelcrafterZipParser.parseNovelMd(text)
        val bookTitle = title.takeIf { it.isNotBlank() && it != "Imported Novel" } ?: suggestedTitle
        return if (acts.isNotEmpty()) {
            persistHierarchy(bookTitle, acts.map { act ->
                act.title to act.chapters.map { ch ->
                    ch.title to ch.scenes.map { sc -> sc.title to (sc.prose.ifBlank { sc.summary }) }
                }
            })
        } else {
            persistSingleScene(bookTitle, text)
        }
    }

    suspend fun importHtml(bytes: ByteArray, suggestedTitle: String = "Imported HTML"): Result {
        val raw = bytes.toString(Charsets.UTF_8)
        val title = Regex("(?is)<title[^>]*>(.*?)</title>").find(raw)?.groupValues?.get(1)
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.ifBlank { null }
            ?: suggestedTitle
        val body = raw
            .replace(Regex("(?is)<script[^>]*>.*?</script>"), " ")
            .replace(Regex("(?is)<style[^>]*>.*?</style>"), " ")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p>"), "\n\n")
            .replace(Regex("(?i)</h[1-6]>"), "\n\n")
            .replace(Regex("(?i)<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
        return persistSingleScene(title, body)
    }

    suspend fun importDocx(bytes: ByteArray, suggestedTitle: String = "Imported Word"): Result {
        val extracted = DocxPlainText.extract(bytes)
        val headed = WordHeadingHeuristics.apply(extracted)
        val (parsedTitle, _, acts) = NovelcrafterZipParser.parseNovelMd(headed)
        val bookTitle = parsedTitle.takeIf { it.isNotBlank() && it != "Imported Novel" } ?: suggestedTitle
        return if (acts.isNotEmpty()) {
            persistHierarchy(
                bookTitle,
                acts.map { act ->
                    act.title to act.chapters.map { ch ->
                        ch.title to ch.scenes.map { sc -> sc.title to (sc.prose.ifBlank { sc.summary }) }
                    }
                },
            )
        } else {
            persistSingleScene(bookTitle, extracted)
        }
    }

    private suspend fun persistSingleScene(bookTitle: String, prose: String): Result {
        return persistHierarchy(
            bookTitle,
            listOf("Act 1" to listOf("Chapter 1" to listOf("Scene 1" to prose))),
        )
    }

    private suspend fun persistHierarchy(
        bookTitle: String,
        acts: List<Pair<String, List<Pair<String, List<Pair<String, String>>>>>>,
    ): Result {
        val now = System.currentTimeMillis()
        val key = UUID.randomUUID().toString().take(8)
        val seriesId = "imp-series-$key"
        val bookId = "imp-book-$key"
        db.seriesDao().upsert(
            SeriesEntity(
                id = seriesId,
                title = bookTitle,
                description = "Imported manuscript",
                createdAt = now,
            ),
        )
        db.bookDao().upsert(
            BookEntity(
                id = bookId,
                seriesId = seriesId,
                title = bookTitle,
                createdAt = now,
                updatedAt = now,
            ),
        )
        var sceneCount = 0
        acts.forEachIndexed { actIndex, (actTitle, chapters) ->
            val actId = "imp-act-$key-$actIndex"
            db.manuscriptDao().upsertAct(ActEntity(actId, bookId, actTitle, actIndex))
            chapters.forEachIndexed { chIndex, (chTitle, scenes) ->
                val chapterId = "imp-ch-$key-$actIndex-$chIndex"
                db.manuscriptDao().upsertChapter(ChapterEntity(chapterId, actId, chTitle, chIndex))
                scenes.forEachIndexed { scIndex, (scTitle, prose) ->
                    val sceneId = "imp-sc-$key-$actIndex-$chIndex-$scIndex"
                    val doc = Document.fromPlainText(prose)
                    db.manuscriptDao().upsertScene(
                        SceneEntity(
                            id = sceneId,
                            chapterId = chapterId,
                            title = scTitle,
                            sortOrder = scIndex,
                            docJson = doc.toJson(),
                            plainText = doc.plainText(),
                            wordCount = doc.wordCount(),
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                    sceneCount++
                }
            }
        }
        return Result(bookId, bookTitle, sceneCount)
    }
}
