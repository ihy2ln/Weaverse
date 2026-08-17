package com.ihy2ln.weaverse.core.text

import com.ihy2ln.weaverse.core.util.newId
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Shared (de)serializer for every `docJson` column (`scenes`, `codex_entries`, chat/rp messages). */
val DocumentJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "type"
}

fun Document.toJson(): String = DocumentJson.encodeToString(this)

/**
 * One empty paragraph, not zero blocks — a document with no blocks renders as
 * a genuinely empty [androidx.compose.foundation.lazy.LazyColumn] in
 * [com.ihy2ln.weaverse.feature.novel.write.editor.BlockEditor] (Priority Zero
 * bug: nothing to tap, no cursor, no visible sign an editor is even there).
 * `EditorState`'s own class default already made this assumption; this was
 * the one path that bypassed it.
 */
private fun blankDocument(): Document = Document(listOf(Paragraph(newId())))

/** Falls back to [blankDocument] for blank `docJson` columns rather than throwing on
 * malformed/legacy data. */
fun String.toDocument(): Document =
    if (isBlank()) blankDocument() else runCatching { DocumentJson.decodeFromString<Document>(this) }.getOrDefault(blankDocument())
