package com.ihy2ln.weaverse.core.manga

import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

/**
 * Reader for Mihon / Tachiyomi library backups (`.tachibk`, and the older `.proto.gz`).
 * Both are gzip-compressed protobuf with the same field numbers, so a small wire-format
 * reader covers them without adding a protobuf dependency. Only the fields Weaverse keeps
 * are read; everything else (trackers, preferences, extension repos) is skipped.
 */
object MihonBackup {
    data class Category(val name: String, val order: Long)
    data class Source(val id: Long, val name: String)
    data class Chapter(
        val url: String,
        val name: String,
        val scanlator: String = "",
        val read: Boolean = false,
        val bookmark: Boolean = false,
        val lastPageRead: Long = 0,
        val dateUpload: Long = 0,
        val chapterNumber: Float = -1f,
    )
    data class History(val url: String, val lastRead: Long)
    data class Manga(
        val source: Long,
        val url: String,
        val title: String,
        val artist: String = "",
        val author: String = "",
        val description: String = "",
        val genres: List<String> = emptyList(),
        val status: Int = 0,
        val thumbnailUrl: String = "",
        val dateAdded: Long = 0,
        val chapters: List<Chapter> = emptyList(),
        val categories: List<Long> = emptyList(),
        val favorite: Boolean = true,
        val history: List<History> = emptyList(),
    )
    data class Backup(val manga: List<Manga>, val categories: List<Category>, val sources: List<Source>)

    fun parse(bytes: ByteArray): Backup {
        val raw = if (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
            GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
        } else bytes
        val manga = mutableListOf<Manga>()
        val categories = mutableListOf<Category>()
        val sources = mutableListOf<Source>()
        Reader(raw).fields { field, reader ->
            when (field) {
                1 -> manga += manga(reader.message())
                2 -> categories += category(reader.message())
                101 -> sources += source(reader.message())
                else -> reader.skip()
            }
        }
        require(manga.isNotEmpty() || categories.isNotEmpty() || sources.isNotEmpty()) {
            "This file is not a Mihon or Tachiyomi backup."
        }
        return Backup(manga, categories, sources)
    }

    /** Mihon's SManga status codes, in the words Weaverse's catalog uses. */
    fun statusLabel(status: Int): String = when (status) {
        1 -> "Ongoing"
        2, 4 -> "Completed"
        3 -> "Licensed"
        5 -> "Cancelled"
        6 -> "On hiatus"
        else -> ""
    }

    private fun manga(r: Reader): Manga {
        var m = Manga(source = 0, url = "", title = "")
        val genres = mutableListOf<String>()
        val chapters = mutableListOf<Chapter>()
        val categories = mutableListOf<Long>()
        val history = mutableListOf<History>()
        r.fields { field, f ->
            when (field) {
                1 -> m = m.copy(source = f.varint())
                2 -> m = m.copy(url = f.string())
                3 -> m = m.copy(title = f.string())
                4 -> m = m.copy(artist = f.string())
                5 -> m = m.copy(author = f.string())
                6 -> m = m.copy(description = f.string())
                7 -> genres += f.string()
                8 -> m = m.copy(status = f.varint().toInt())
                9 -> m = m.copy(thumbnailUrl = f.string())
                13 -> m = m.copy(dateAdded = f.varint())
                16 -> chapters += chapter(f.message())
                17 -> categories += f.varints()
                100 -> m = m.copy(favorite = f.varint() != 0L)
                104 -> history += history(f.message())
                else -> f.skip()
            }
        }
        return m.copy(genres = genres, chapters = chapters, categories = categories, history = history)
    }

    private fun chapter(r: Reader): Chapter {
        var c = Chapter(url = "", name = "")
        r.fields { field, f ->
            when (field) {
                1 -> c = c.copy(url = f.string())
                2 -> c = c.copy(name = f.string())
                3 -> c = c.copy(scanlator = f.string())
                4 -> c = c.copy(read = f.varint() != 0L)
                5 -> c = c.copy(bookmark = f.varint() != 0L)
                6 -> c = c.copy(lastPageRead = f.varint())
                8 -> c = c.copy(dateUpload = f.varint())
                9 -> c = c.copy(chapterNumber = f.float())
                else -> f.skip()
            }
        }
        return c
    }

    private fun history(r: Reader): History {
        var h = History("", 0)
        r.fields { field, f ->
            when (field) {
                1 -> h = h.copy(url = f.string())
                2 -> h = h.copy(lastRead = f.varint())
                else -> f.skip()
            }
        }
        return h
    }

    private fun category(r: Reader): Category {
        var c = Category("", 0)
        r.fields { field, f ->
            when (field) {
                1 -> c = c.copy(name = f.string())
                2 -> c = c.copy(order = f.varint())
                else -> f.skip()
            }
        }
        return c
    }

    private fun source(r: Reader): Source {
        var s = Source(0, "")
        r.fields { field, f ->
            when (field) {
                1 -> s = s.copy(name = f.string())
                2 -> s = s.copy(id = f.varint())
                else -> f.skip()
            }
        }
        return s
    }

    /** Minimal protobuf wire-format reader over one message's bytes. */
    private class Reader(private val buf: ByteArray, private var pos: Int = 0, private val end: Int = buf.size) {
        private var wireType = 0

        fun fields(each: (field: Int, reader: Reader) -> Unit) {
            while (pos < end) {
                val tag = rawVarint()
                wireType = (tag and 7).toInt()
                each((tag ushr 3).toInt(), this)
            }
        }

        fun varint(): Long {
            require(wireType == 0) { "Unexpected protobuf wire type $wireType" }
            return rawVarint()
        }

        /** A repeated integer field, written either packed or one value per tag. */
        fun varints(): List<Long> = if (wireType == 2) {
            val length = rawVarint().toInt()
            val stop = pos + length
            buildList { while (pos < stop) add(rawVarint()) }
        } else listOf(varint())

        fun float(): Float {
            require(wireType == 5) { "Unexpected protobuf wire type $wireType" }
            val bits = (buf[pos].toInt() and 0xff) or ((buf[pos + 1].toInt() and 0xff) shl 8) or
                ((buf[pos + 2].toInt() and 0xff) shl 16) or ((buf[pos + 3].toInt() and 0xff) shl 24)
            pos += 4
            return java.lang.Float.intBitsToFloat(bits)
        }

        fun string(): String = String(bytes(), Charsets.UTF_8)

        fun message(): Reader {
            val length = lengthPrefix()
            return Reader(buf, pos, pos + length).also { pos += length }
        }

        fun skip() {
            when (wireType) {
                0 -> rawVarint()
                1 -> pos += 8
                2 -> {
                    // Read the length first: `pos += lengthPrefix()` would add it to the old pos.
                    val length = lengthPrefix()
                    pos += length
                }
                5 -> pos += 4
                else -> error("Unsupported protobuf wire type $wireType")
            }
            require(pos <= end) { "The backup file is truncated." }
        }

        private fun bytes(): ByteArray {
            val length = lengthPrefix()
            return buf.copyOfRange(pos, pos + length).also { pos += length }
        }

        private fun lengthPrefix(): Int {
            require(wireType == 2) { "Unexpected protobuf wire type $wireType" }
            val length = rawVarint().toInt()
            require(length >= 0 && pos + length <= end) { "The backup file is truncated." }
            return length
        }

        private fun rawVarint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                require(pos < end && shift < 64) { "The backup file is truncated." }
                val b = buf[pos++].toInt()
                result = result or ((b and 0x7f).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
            }
        }
    }
}
