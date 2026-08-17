package com.ihy2ln.weaverse.core.export

/**
 * A generic "structured document" — headings + paragraphs — that every readable export format
 * (Markdown/HTML/DOCX) serializes from and parses back into. JSON export doesn't go through
 * this: it uses each feature's own typed DTOs for full-fidelity round-tripping (status, word
 * counts, aliases, etc.), since flattening to headings/paragraphs would lose that structure.
 * [ExportOutline] is deliberately the lowest common denominator across three very different
 * target formats, so import from Markdown/HTML/DOCX is inherently best-effort/lossy compared to
 * JSON — see each `*BackupService`'s own KDoc for what specifically survives the round trip.
 */
sealed interface ExportNode {
    data class Heading(val level: Int, val text: String) : ExportNode
    data class Paragraph(val text: String) : ExportNode
}

data class ExportOutline(val title: String, val nodes: List<ExportNode>)

enum class ExportFormat(val label: String, val extension: String, val mimeType: String) {
    Json("JSON", "json", "application/json"),
    Markdown("Markdown", "md", "text/markdown"),
    Html("HTML", "html", "text/html"),
    Docx("Word (.docx)", "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
}
