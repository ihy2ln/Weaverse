package com.ihy2ln.weaverse.feature.novel.write.editor

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.sp
import com.ihy2ln.weaverse.core.text.CodexMention
import com.ihy2ln.weaverse.core.text.CodexMentionTag
import com.ihy2ln.weaverse.core.text.CodexMentionTarget
import com.ihy2ln.weaverse.core.text.Paragraph
import com.ihy2ln.weaverse.core.text.findCodexMentions
import com.ihy2ln.weaverse.core.text.plainText
import com.ihy2ln.weaverse.core.text.remapAfterPlainEdit
import com.ihy2ln.weaverse.core.text.toAnnotatedString
import com.ihy2ln.weaverse.core.ui.components.EditTextAction
import com.ihy2ln.weaverse.core.ui.components.EditTextPopup
import com.ihy2ln.weaverse.core.ui.components.EditTextPopupConfig
import com.ihy2ln.weaverse.core.ui.theme.InkAccentBlue

@Composable
fun BlockEditorField(
    paragraph: Paragraph,
    textColor: Color,
    onTextChange: (Paragraph) -> Unit,
    modifier: Modifier = Modifier,
    onSlashDetected: () -> Unit = {},
    onBackslashDetected: () -> Unit = {},
    onSelectionChange: (TextRange) -> Unit = {},
    onEditAction: (EditTextAction, TextFieldValue) -> Unit = { _, _ -> },
    popupConfig: EditTextPopupConfig = EditTextPopupConfig(),
    showEditPopup: Boolean = false,
    onShowEditPopupChange: (Boolean) -> Unit = {},
    showPromptPlaceholder: Boolean = false,
    /** Codex entries whose name/aliases should be hyperlinked when they appear in this text. */
    codexMentionTargets: List<CodexMentionTarget> = emptyList(),
    onMentionClick: (String) -> Unit = {},
) {
    val plain = paragraph.plainText()
    fun mentionsFor(text: String): List<CodexMention> = findCodexMentions(text, codexMentionTargets)

    var value by remember(paragraph.id) {
        mutableStateOf(
            TextFieldValue(
                annotatedString = paragraph.spans.toAnnotatedString(
                    textColor,
                    mentions = mentionsFor(plain),
                    linkColor = InkAccentBlue,
                ),
                selection = TextRange(plain.length),
            ),
        )
    }
    var layoutResult by remember(paragraph.id) { mutableStateOf<TextLayoutResult?>(null) }
    /** Targets already reflected in [value]; avoids rebuilding the field on every keystroke. */
    var appliedTargets by remember(paragraph.id) { mutableStateOf(codexMentionTargets) }

    // Sync external paragraph updates (undo/AI accept) without clobbering caret during typing
    LaunchedEffect(paragraph.id, plain, paragraph.spans, codexMentionTargets) {
        if (value.text != plain || appliedTargets != codexMentionTargets) {
            val sel = value.selection
            val capped = TextRange(
                sel.start.coerceIn(0, plain.length),
                sel.end.coerceIn(0, plain.length),
            )
            value = TextFieldValue(
                annotatedString = paragraph.spans.toAnnotatedString(
                    textColor,
                    mentions = mentionsFor(plain),
                    linkColor = InkAccentBlue,
                ),
                selection = capped,
            )
            appliedTargets = codexMentionTargets
        }
    }

    val latestOnShow by rememberUpdatedState(onShowEditPopupChange)
    val toolbar = remember {
        object : TextToolbar {
            override var status: TextToolbarStatus = TextToolbarStatus.Hidden
                private set

            override fun showMenu(
                rect: Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?,
            ) {
                status = TextToolbarStatus.Hidden
                latestOnShow(true)
            }

            override fun hide() {
                status = TextToolbarStatus.Hidden
            }
        }
    }

    Box(
        modifier = modifier.pointerInput(codexMentionTargets) {
            // PointerEventPass.Initial runs before BasicTextField's own gesture handling,
            // so a tap landing on a codex mention can be consumed here to navigate instead
            // of placing the text cursor / opening the keyboard.
            awaitEachGesture {
                val down = awaitFirstDown(pass = PointerEventPass.Initial)
                val layout = layoutResult ?: return@awaitEachGesture
                val offset = layout.getOffsetForPosition(down.position)
                val annotation = value.annotatedString
                    .getStringAnnotations(CodexMentionTag, offset, offset)
                    .firstOrNull()
                if (annotation != null) {
                    down.consume()
                    onMentionClick(annotation.item)
                } else {
                    // Plain tap: publish the tapped caret so the prompt dock's
                    // ⌖ insert target follows the user's chosen paragraph spot.
                    onSelectionChange(TextRange(offset))
                }
            }
        },
    ) {
        CompositionLocalProvider(LocalTextToolbar provides toolbar) {
            BasicTextField(
                value = value,
                onValueChange = { next ->
                    val oldText = value.text
                    val newText = next.text
                    val spans = if (oldText == newText) {
                        paragraph.spans
                    } else {
                        paragraph.spans.remapAfterPlainEdit(
                            oldText = oldText,
                            newText = newText,
                            selectionStart = value.selection.min,
                            selectionEnd = value.selection.max,
                        )
                    }
                    when {
                        newText == "/" || newText.endsWith("\n/") -> {
                            val textOut = if (newText == "/") "" else newText.dropLast(1)
                            val spansOut = listOf(com.ihy2ln.weaverse.core.text.Span(textOut))
                            value = next.copy(
                                annotatedString = spansOut.toAnnotatedString(textColor),
                                selection = TextRange(textOut.length),
                            )
                            onSelectionChange(TextRange(textOut.length))
                            onTextChange(paragraph.copy(spans = spansOut))
                            onSlashDetected()
                        }
                        newText == "\\" || newText.endsWith("\n\\") -> {
                            val textOut = if (newText == "\\") "" else newText.dropLast(1)
                            val spansOut = listOf(com.ihy2ln.weaverse.core.text.Span(textOut))
                            value = next.copy(
                                annotatedString = spansOut.toAnnotatedString(textColor),
                                selection = TextRange(textOut.length),
                            )
                            onSelectionChange(TextRange(textOut.length))
                            onTextChange(paragraph.copy(spans = spansOut))
                            onBackslashDetected()
                        }
                        else -> {
                            value = next.copy(
                                annotatedString = spans.toAnnotatedString(
                                    textColor,
                                    mentions = mentionsFor(newText),
                                    linkColor = InkAccentBlue,
                                ),
                            )
                            onSelectionChange(next.selection)
                            onTextChange(paragraph.copy(spans = spans))
                        }
                    }
                },
                textStyle = TextStyle(
                    color = textColor,
                    fontSize = 16.sp,
                    lineHeight = 26.sp,
                    fontFamily = FontFamily.Serif,
                ),
                cursorBrush = SolidColor(textColor),
                onTextLayout = { layoutResult = it },
                decorationBox = { inner ->
                    if (value.text.isEmpty() && showPromptPlaceholder) {
                        androidx.compose.material3.Text(
                            "Start writing — / AI prompt · \\ manual text…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 16.sp,
                        )
                    }
                    inner()
                },
            )
        }
        EditTextPopup(
            expanded = showEditPopup,
            onDismiss = { onShowEditPopupChange(false) },
            onAction = { action ->
                val nextValue = if (action == EditTextAction.SelectAll) {
                    value.copy(selection = TextRange(0, value.text.length)).also {
                        value = it
                        onSelectionChange(it.selection)
                    }
                } else {
                    value
                }
                onEditAction(action, nextValue)
            },
            config = popupConfig.copy(hasSelection = value.selection.min != value.selection.max),
        )
    }
}
