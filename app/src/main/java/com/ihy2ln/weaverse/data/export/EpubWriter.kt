package com.ihy2ln.weaverse.data.export

import java.io.File
import java.io.FileOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Minimal EPUB 2.0 writer so a finished book opens in any reader app. */
object EpubWriter {
    fun write(file: File, title: String, chapters: List<EpubChapter>) {
        file.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            putStored(zip, "mimetype", "application/epub+zip".toByteArray(Charsets.US_ASCII))
            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write(CONTAINER_XML.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zip.write(contentOpf(title, chapters).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("OEBPS/nav.xhtml"))
            zip.write(navXhtml(title, chapters).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("OEBPS/toc.ncx"))
            zip.write(tocNcx(title, chapters).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            chapters.forEachIndexed { index, chapter ->
                zip.putNextEntry(ZipEntry("OEBPS/${chapterFile(index)}"))
                zip.write(chapterXhtml(chapter).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
    }

    private fun putStored(zip: ZipOutputStream, name: String, data: ByteArray) {
        val entry = ZipEntry(name)
        entry.method = ZipEntry.STORED
        entry.size = data.size.toLong()
        entry.compressedSize = data.size.toLong()
        val crc = CRC32()
        crc.update(data)
        entry.crc = crc.value
        zip.putNextEntry(entry)
        zip.write(data)
        zip.closeEntry()
    }

    private fun chapterFile(index: Int): String = "chapter-${index + 1}.xhtml"

    private fun contentOpf(title: String, chapters: List<EpubChapter>): String {
        val manifest = chapters.indices.joinToString("\n") { i ->
            """    <item id="ch${i + 1}" href="${chapterFile(i)}" media-type="application/xhtml+xml"/>"""
        }
        val spine = chapters.indices.joinToString("\n") { i ->
            """    <itemref idref="ch${i + 1}"/>"""
        }
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" unique-identifier="BookId" version="2.0">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>${escape(title)}</dc:title>
                <dc:language>en</dc:language>
                <dc:identifier id="BookId">weaverse-${title.hashCode()}</dc:identifier>
              </metadata>
              <manifest>
                <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
            $manifest
              </manifest>
              <spine toc="ncx">
            $spine
              </spine>
            </package>
        """.trimIndent()
    }

    private fun tocNcx(title: String, chapters: List<EpubChapter>): String {
        val nav = chapters.mapIndexed { i, ch ->
            """
              <navPoint id="nav${i + 1}" playOrder="${i + 1}">
                <navLabel><text>${escape(ch.title)}</text></navLabel>
                <content src="${chapterFile(i)}"/>
              </navPoint>
            """.trimIndent()
        }.joinToString("\n")
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
              <head><meta name="dtb:uid" content="weaverse-${title.hashCode()}"/></head>
              <docTitle><text>${escape(title)}</text></docTitle>
              <navMap>
            $nav
              </navMap>
            </ncx>
        """.trimIndent()
    }

    private fun navXhtml(title: String, chapters: List<EpubChapter>): String {
        val items = chapters.mapIndexed { i, ch ->
            """<li><a href="${chapterFile(i)}">${escape(ch.title)}</a></li>"""
        }.joinToString("\n")
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
            <head><title>${escape(title)}</title></head>
            <body>
            <nav epub:type="toc"><ol>
            $items
            </ol></nav>
            </body>
            </html>
        """.trimIndent()
    }

    private fun chapterXhtml(chapter: EpubChapter): String {
        val paras = chapter.body.split("\n\n")
            .filter { it.isNotBlank() }
            .joinToString("\n") { "<p>${escape(it).replace("\n", "<br/>")}</p>" }
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head><title>${escape(chapter.title)}</title></head>
            <body>
            <h1>${escape(chapter.title)}</h1>
            $paras
            </body>
            </html>
        """.trimIndent()
    }

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private val CONTAINER_XML = """
        <?xml version="1.0" encoding="UTF-8"?>
        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles>
            <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
          </rootfiles>
        </container>
    """.trimIndent()
}

data class EpubChapter(
    val title: String,
    val body: String,
)
