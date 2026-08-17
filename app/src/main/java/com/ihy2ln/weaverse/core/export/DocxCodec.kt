package com.ihy2ln.weaverse.core.export

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * [ExportOutline] <-> a minimal but valid .docx (OOXML) file — no Apache POI or similar
 * dependency available, so this hand-writes just the three parts a .docx actually needs
 * (`[Content_Types].xml`, `_rels/.rels`, `word/document.xml`) using `java.util.zip` (a .docx
 * *is* a zip file) — same "no extra dependency for a binary format" approach as Phase 11's PNG
 * character-card codec. Headings use Word's built-in `Heading1`..`Heading6`/`Title` style ids,
 * which Word/LibreOffice/Google Docs render with their own default styling even with no
 * `word/styles.xml` part in the package — real Word compatibility is unverified (no Word
 * install in this build sandbox), but the produced XML is schema-valid OOXML and the encode/
 * decode round-trip against our own output is unit-tested.
 */
object DocxCodec {
    private const val CONTENT_TYPES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>"""

    private const val RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""

    fun encode(outline: ExportOutline): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            writeEntry(zip, "[Content_Types].xml", CONTENT_TYPES_XML)
            writeEntry(zip, "_rels/.rels", RELS_XML)
            writeEntry(zip, "word/document.xml", buildDocumentXml(outline))
        }
        return output.toByteArray()
    }

    fun decode(bytes: ByteArray): ExportOutline {
        var documentXml: String? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    documentXml = zip.readBytes().toString(Charsets.UTF_8)
                }
                entry = zip.nextEntry
            }
        }
        return documentXml?.let(::parseDocumentXml) ?: ExportOutline("Untitled", emptyList())
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun buildDocumentXml(outline: ExportOutline): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>")
        append(paragraphXml(outline.title, "Title"))
        outline.nodes.forEach { node ->
            when (node) {
                is ExportNode.Heading -> append(paragraphXml(node.text, "Heading${node.level.coerceIn(1, 6)}"))
                is ExportNode.Paragraph -> append(paragraphXml(node.text, null))
            }
        }
        append("<w:sectPr/></w:body></w:document>")
    }

    private fun paragraphXml(text: String, styleId: String?): String = buildString {
        append("<w:p>")
        if (styleId != null) append("<w:pPr><w:pStyle w:val=\"").append(styleId).append("\"/></w:pPr>")
        append("<w:r><w:t xml:space=\"preserve\">").append(escapeXml(text)).append("</w:t></w:r>")
        append("</w:p>")
    }

    private val paragraphPattern = Regex("<w:p[ >].*?</w:p>", RegexOption.DOT_MATCHES_ALL)
    private val stylePattern = Regex("<w:pStyle w:val=\"([^\"]*)\"")
    private val textRunPattern = Regex("<w:t[^>]*>(.*?)</w:t>", RegexOption.DOT_MATCHES_ALL)

    private fun parseDocumentXml(xml: String): ExportOutline {
        var title = "Untitled"
        var titleConsumed = false
        val nodes = mutableListOf<ExportNode>()

        paragraphPattern.findAll(xml).forEach { pMatch ->
            val pXml = pMatch.value
            val text = textRunPattern.findAll(pXml).joinToString("") { it.groupValues[1] }.let(::unescapeXml).trim()
            if (text.isEmpty()) return@forEach
            val style = stylePattern.find(pXml)?.groupValues?.get(1)
            when {
                style == "Title" && !titleConsumed -> {
                    title = text
                    titleConsumed = true
                }
                style?.startsWith("Heading") == true -> {
                    val level = style.removePrefix("Heading").toIntOrNull() ?: 1
                    nodes.add(ExportNode.Heading(level, text))
                }
                else -> nodes.add(ExportNode.Paragraph(text))
            }
        }
        return ExportOutline(title, nodes)
    }

    private fun escapeXml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun unescapeXml(text: String): String = text
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
}
