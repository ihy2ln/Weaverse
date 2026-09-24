package com.ihy2ln.weaverse.feature.novel.write.editor

import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** Save only parcel-safe text/caret. Styled annotations are rebuilt from the manuscript. */
internal val NovelTextFieldSaver = listSaver<TextFieldValue, Any>(
    save = { listOf(it.text, it.selection.start, it.selection.end) },
    restore = {
        val text = it[0] as String
        TextFieldValue(text, TextRange((it[1] as Int).coerceIn(0, text.length), (it[2] as Int).coerceIn(0, text.length)))
    },
)
