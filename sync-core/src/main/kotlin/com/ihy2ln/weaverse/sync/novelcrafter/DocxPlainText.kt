package com.ihy2ln.weaverse.sync.novelcrafter

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/** Pulls visible paragraph text from a .docx (Office Open XML) package. */
object DocxPlainText {
    fun extract(bytes: ByteArray): String {
        require(bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
            "Not a DOCX (ZIP) file"
        }
        var documentXml: String? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.replace('\\', '/') == "word/document.xml") {
                    documentXml = zip.readBytes().toString(Charsets.UTF_8)
                    break
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val xml = documentXml ?: error("DOCX missing word/document.xml")
        val paragraphs = Regex("(?s)<w:p[\\s>].*?</w:p>").findAll(xml).map { match ->
            Regex("<w:t[^>]*>(.*?)</w:t>").findAll(match.value)
                .joinToString("") { decodeXml(it.groupValues[1]) }
        }.filter { it.isNotBlank() }.toList()
        return paragraphs.joinToString("\n\n")
    }

    fun decodeXml(raw: String): String = raw
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace("&nbsp;", " ")
}
