package com.ihy2ln.weaverse.feature.novel.write.editor

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.withTimeoutOrNull
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

    // A pure re-style (Bold/Color/Font via the toolbar, Format menu, or a color/font
    // dialog) rewrites paragraph.spans, which forces the resync effect below to assign
    // a brand-new TextFieldValue. Compose treats that as reason to call showMenu()
    // again — and showMenu also fires after a dialog closes while the selection
    // handles are still on screen. A 400ms suppress window is not enough for that
    // (canceling Text color is well past 400ms). EditMenuGate keeps the popup closed
    // for the same selection until the caret collapses or the range changes.
    val menuGate = remember { EditMenuGate() }
    val haptic = LocalHapticFeedback.current
    val touchSlop = LocalViewConfiguration.current.touchSlop

    // Sync external paragraph updates (undo/AI accept) without clobbering caret during typing
    LaunchedEffect(paragraph.id, plain, paragraph.spans, codexMentionTargets) {
        if (value.text != plain || codexMentionTargets.isNotEmpty()) {
            val nextAnnotated = paragraph.spans.toAnnotatedString(
                textColor,
                mentions = mentionsFor(plain),
                linkColor = InkAccentBlue,
            )
            if (value.text == plain && value.annotatedString == nextAnnotated) {
                // A typing round-trip echoing back content that's already on screen —
                // nothing to do. Reassigning value anyway would still create a new
                // TextFieldValue object, which is exactly what can spuriously
                // re-trigger the system's showMenu() callback mid-selection.
                return@LaunchedEffect
            }
            val sel = value.selection
            val capped = TextRange(
                sel.start.coerceIn(0, plain.length),
                sel.end.coerceIn(0, plain.length),
            )
            value = TextFieldValue(annotatedString = nextAnnotated, selection = capped)
        }
    }

    menuGate.setSelection(value.selection)
    menuGate.setExpanded(showEditPopup)
    menuGate.setOpenHandler {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onShowEditPopupChange(true)
    }
    val toolbar = remember(menuGate) {
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
                menuGate.onSystemShowMenu()
            }

            override fun hide() {
                status = TextToolbarStatus.Hidden
            }
        }
    }

    Box(
        modifier = modifier.pointerInput(codexMentionTargets, touchSlop) {
            // PointerEventPass.Initial for mention hit-testing; Final for drag tracking so
            // we never steal LazyColumn scroll. Do not treat scroll-cancel as a long-press —
            // Format opens only via TextToolbar.showMenu or a clean tap in a highlight.
            awaitEachGesture {
                val down = awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Initial,
                )
                val layout = layoutResult
                val offset = layout?.getOffsetForPosition(down.position)
                val annotation = if (offset != null) {
                    value.annotatedString
                        .getStringAnnotations(CodexMentionTag, offset, offset)
                        .firstOrNull()
                } else {
                    null
                }
                if (annotation != null) {
                    val up = withTimeoutOrNull(EditorGestures.SELECTION_TAP_MS) {
                        waitForUpOrCancellation(PointerEventPass.Initial)
                    }
                    if (up != null) {
                        down.consume()
                        onMentionClick(annotation.item)
                    } else {
                        menuGate.notePointerDrag()
                    }
                    return@awaitEachGesture
                }

                var dragged = false
                val downPos = down.position
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    val dx = kotlin.math.abs(change.position.x - downPos.x)
                    val dy = kotlin.math.abs(change.position.y - downPos.y)
                    // Vertical-dominant move = scroll intent. Ignore small long-press jitter
                    // and horizontal drag-select so those can still open Format.
                    if (!dragged && dy > touchSlop && dy >= dx) {
                        dragged = true
                        menuGate.notePointerDrag()
                    }
                    if (!change.pressed) break
                }
                if (dragged) {
                    return@awaitEachGesture
                }
                if (offset != null) {
                    val sel = value.selection
                    val inSelection = !sel.collapsed && offset >= sel.min && offset < sel.max
                    if (inSelection) {
                        menuGate.onUserPressInSelection()
                    }
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
                            menuGate.onSelectionChange(next.selection)
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
            onDismiss = {
                menuGate.onDismiss()
                onShowEditPopupChange(false)
            },
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
