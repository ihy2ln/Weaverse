package com.ihy2ln.weaverse.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.graphics.BitmapRegionDecoder
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.graphics.Rect
import com.ihy2ln.weaverse.data.db.entities.MediaEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Imports a whole manga/comic/webtoon file as individual page pictures:
 * - PDF (rendered page by page)
 * - CBZ / ZIP archives of images (entries sorted naturally by name)
 * - single images — a very tall one is treated as a webtoon strip and sliced
 *   into 2:3 page chunks via region decoding
 * Pages are streamed one at a time to [importPages]'s [onPage] callback so a
 * whole volume never sits in memory; every output page is registered through
 * MediaRepository and flows into the storyboard panel stack unchanged.
 */
@Singleton
class MangaFileImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
) {

    /** How a source file is split into pages. */
    private enum class SourceKind { Pdf, Archive, Strip, Single }

    suspend fun importPages(
        uri: Uri,
        onProgress: suspend (String) -> Unit = {},
        onPage: suspend (media: MediaEntity, label: String) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        val name = queryDisplayName(uri)
        onProgress("Reading $name…")
        val lower = name.lowercase()
        val kind = when {
            lower.endsWith(".pdf") -> SourceKind.Pdf
            lower.endsWith(".cbz") || lower.endsWith(".zip") -> SourceKind.Archive
            else -> SourceKind.Single // strip slicing decided after decoding bounds
        }
        var count = 0
        when (kind) {
            SourceKind.Pdf -> renderPdf(uri, onProgress) { bitmap, index, total ->
                onProgress("Importing page ${index + 1} of $total…")
                val media = importPage(bitmap)
                bitmap.recycle()
                onPage(media, "Page ${index + 1}")
                count++
            }
            SourceKind.Archive -> readZip(uri, onProgress) { bitmap, index, total ->
                onProgress("Importing page ${index + 1} of $total…")
                val media = importPage(bitmap)
                bitmap.recycle()
                onPage(media, "Page ${index + 1}")
                count++
            }
            SourceKind.Strip, SourceKind.Single -> decodeSingle(uri, kind, onProgress) { bitmap, index, total ->
                onProgress("Importing page ${index + 1} of $total…")
                val media = importPage(bitmap)
                bitmap.recycle()
                onPage(media, if (total == 1) "Page 1" else "Page ${index + 1}")
                count++
            }
        }
        count
    }

    fun queryDisplayName(uri: Uri): String {
        var name = "manga"
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val column = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && column >= 0) {
                    name = cursor.getString(column) ?: name
                }
            }
        }
        return name
    }

    private suspend fun importPage(bitmap: Bitmap): MediaEntity =
        mediaRepository.importFromBytes(
            bytes = toJpeg(bitmap),
            fileName = "page-${System.currentTimeMillis()}-${(0..999).random()}.jpg",
            mimeType = "image/jpeg",
        )

    // ------------------------------------------------------------------- pdf

    private suspend fun renderPdf(
        uri: Uri,
        onProgress: suspend (String) -> Unit,
        onPage: suspend (Bitmap, index: Int, total: Int) -> Unit,
    ) {
        val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: return
        try {
            PdfRenderer(pfd).use { renderer ->
                val total = renderer.pageCount
                for (i in 0 until total) {
                    onProgress("Rendering PDF page ${i + 1} of $total…")
                    renderer.openPage(i).use { page ->
                        val scale = max(1f, 1600f / max(page.width, page.height))
                        val width = (page.width * scale).roundToInt()
                        val height = (page.height * scale).roundToInt()
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        onPage(bitmap, i, total)
                    }
                }
            }
        } finally {
            runCatching { pfd.close() }
        }
    }

    // ------------------------------------------------------------------- zip

    private suspend fun readZip(
        uri: Uri,
        onProgress: suspend (String) -> Unit,
        onPage: suspend (Bitmap, index: Int, total: Int) -> Unit,
    ) {
        val stream = context.contentResolver.openInputStream(uri) ?: return
        val entries = mutableListOf<Pair<String, Long>>()
        val names = mutableListOf<String>()
        ZipInputStream(stream.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.substringAfterLast('.').lowercase() in IMAGE_EXTENSIONS) {
                    names.add(entry.name)
                }
                entry = zip.nextEntry
            }
        }
        if (names.isEmpty()) return
        // Natural-ish sort: pad number runs so page 2 sorts before page 10.
        val sorted = names.sortedWith(::compareComicPageNames)
        sorted.forEachIndexed { index, entryName ->
            onProgress("Decoding ${entryName.substringAfterLast('/')} (${index + 1}/${sorted.size})…")
            val bitmap = openImageEntry(uri, entryName) ?: return@forEachIndexed
            onPage(bitmap, index, sorted.size)
        }
    }

    /** Reads one zip entry in a second pass (zip streams cannot seek). */
    private fun openImageEntry(uri: Uri, entryName: String): Bitmap? {
        val stream = context.contentResolver.openInputStream(uri) ?: return null
        ZipInputStream(stream.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == entryName) {
                    val bytes = zip.readBytes()
                    return decodeBitmap(bytes)
                }
                entry = zip.nextEntry
            }
        }
        return null
    }

    // ---------------------------------------------------------------- single

    private suspend fun decodeSingle(
        uri: Uri,
        kind: SourceKind,
        onProgress: suspend (String) -> Unit,
        onPage: suspend (Bitmap, index: Int, total: Int) -> Unit,
    ) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return
        val isStrip = bounds.outHeight > bounds.outWidth * 2.2f
        if (kind == SourceKind.Single && !isStrip) {
            decodeBitmap(context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0))
                ?.let { onPage(it, 0, 1) }
            return
        }
        // Long strip: region-decode 2:3 page slices instead of one huge bitmap.
        onProgress("Long strip detected — slicing into pages…")
        val decoder = context.contentResolver.openInputStream(uri)?.use {
            BitmapRegionDecoder.newInstance(it, false)
        } ?: return
        try {
            val pageHeight = (bounds.outWidth * 1.5f).roundToInt()
            val total = ((bounds.outHeight + pageHeight - 1) / pageHeight)
            var index = 0
            var y = 0
            while (y < bounds.outHeight) {
                val h = minOf(pageHeight, bounds.outHeight - y)
                if (h > bounds.outWidth / 4) {
                    onProgress("Slicing strip page ${index + 1} of $total…")
                    val bitmap = decoder.decodeRegion(Rect(0, y, bounds.outWidth, y + h), sampleOptions(bounds.outWidth))
                    onPage(bitmap, index, total)
                }
                y += h
                index++
            }
        } finally {
            decoder.recycle()
        }
    }

    private fun sampleOptions(width: Int): BitmapFactory.Options {
        var sample = 1
        while (width / (sample * 2) >= 1000) sample *= 2
        return BitmapFactory.Options().apply { inSampleSize = sample }
    }

    private fun decodeBitmap(bytes: ByteArray): Bitmap? {
        if (bytes.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        var longEdge = max(bounds.outWidth, bounds.outHeight)
        while (longEdge / (sample * 2) >= 1000) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private fun toJpeg(bitmap: Bitmap): ByteArray =
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }.toByteArray()

    private companion object {
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")
    }
}

private val ComicPageNumberRun = Regex("\\d+")

internal fun compareComicPageNames(left: String, right: String): Int {
    fun key(name: String): String = ComicPageNumberRun.replace(name.lowercase()) {
        it.value.padStart(10, '0')
    }
    return compareValuesBy(left, right, { key(it) }, { it.lowercase() })
}
