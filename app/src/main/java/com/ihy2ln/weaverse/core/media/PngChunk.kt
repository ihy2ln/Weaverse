package com.ihy2ln.weaverse.core.media

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32

/**
 * Minimal PNG chunk reader/writer — just enough to read/insert/remove ancillary
 * chunks (spec §11's PNG character-card round-trip needs a `tEXt` chunk with
 * keyword `chara`, SillyTavern-card-style). Deliberately doesn't touch pixel
 * data (IHDR/IDAT/PLTE) at all — Android's own `Bitmap.compress(PNG, ...)`
 * already produces a fully valid PNG byte stream, this only needs to insert
 * or read one extra chunk into that stream. Pure JVM (no Android types), so
 * it's unit-testable without Robolectric — see `PngChunkTest`.
 */
object PngChunkIO {
    private val SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    data class Chunk(val type: String, val data: ByteArray)

    fun readChunks(png: ByteArray): List<Chunk> {
        require(png.size >= 8 && png.copyOfRange(0, 8).contentEquals(SIGNATURE)) { "Not a PNG file" }
        val chunks = mutableListOf<Chunk>()
        var offset = 8
        while (offset + 8 <= png.size) {
            val length = readInt32BE(png, offset)
            val type = String(png, offset + 4, 4, Charsets.US_ASCII)
            val dataStart = offset + 8
            require(dataStart + length + 4 <= png.size) { "Truncated PNG chunk '$type'" }
            chunks.add(Chunk(type, png.copyOfRange(dataStart, dataStart + length)))
            offset = dataStart + length + 4 // skip the trailing CRC — recomputed on write
        }
        return chunks
    }

    fun writeChunks(chunks: List<Chunk>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(SIGNATURE)
        for (chunk in chunks) {
            val typeBytes = chunk.type.toByteArray(Charsets.US_ASCII)
            writeInt32BE(out, chunk.data.size)
            out.write(typeBytes)
            out.write(chunk.data)
            val crc = CRC32()
            crc.update(typeBytes)
            crc.update(chunk.data)
            writeInt32BE(out, crc.value.toInt())
        }
        return out.toByteArray()
    }

    private fun readInt32BE(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private fun writeInt32BE(out: ByteArrayOutputStream, value: Int) {
        out.write((value ushr 24) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }
}
