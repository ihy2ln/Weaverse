package com.ihy2ln.weaverse.feature.help

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.core.ui.util.alwaysScrollEndSpacer

private const val WIKI_LINK_TAG = "wiki_link"

/**
 * The in-app wiki manual: a web-wiki-style reader with a navigation sidebar,
 * search, and a markdown renderer (headings, bullets, tables, **bold** and
 * [[links]] between pages). Opened from Settings.
 */
@Composable
fun WikiScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = inkTokens()
    var currentPageId by rememberSaveable { mutableStateOf("home") }
    var query by rememberSaveable { mutableStateOf("") }
    val page = WikiContent.findById(currentPageId) ?: WikiContent.pages.first()
    val results = WikiContent.search(query)

    Row(modifier = modifier.fillMaxSize().background(tokens.background)) {
        // ---------------------------------------------------------------- sidebar
        Column(
            modifier = Modifier
                .width(224.dp)
                .fillMaxHeight()
                .background(tokens.panel)
                .padding(vertical = InkSpacing.md),
        ) {
            Text(
                "Weaverse Wiki",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = InkSpacing.lg),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = InkSpacing.lg, vertical = InkSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "⌕",
                    color = tokens.secondaryText,
                    style = MaterialTheme.typography.bodyMedium,
                )
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = tokens.primaryText),
                    cursorBrush = SolidColor(tokens.primaryText),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = InkSpacing.xs),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) {
                                Text(
                                    "Search pages",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.secondaryText,
                                )
                            }
                            inner()
                        }
                    },
                )
            }
            Text(
                "‹ Close wiki",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onClose)
                    .padding(horizontal = InkSpacing.lg, vertical = InkSpacing.xxs),
            )
            Spacer(Modifier.height(InkSpacing.xs))
            LazyColumn(modifier = Modifier.weight(1f)) {
                if (query.isBlank()) {
                    item(key = "hdr") {
                        Text(
                            "PAGES",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = tokens.secondaryText,
                            modifier = Modifier.padding(horizontal = InkSpacing.lg, vertical = InkSpacing.xxs),
                        )
                    }
                }
                items(results, key = { it.id }) { entry ->
                    val selected = entry.id == page.id
                    Text(
                        entry.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) tokens.activePillLabel else tokens.primaryText,
                        maxLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = InkSpacing.sm, vertical = 2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) tokens.activePill else tokens.panel)
                            .clickable {
                                currentPageId = entry.id
                                query = ""
                            }
                            .padding(horizontal = InkSpacing.sm, vertical = InkSpacing.xs),
                    )
                }
                if (results.isEmpty()) {
                    item("none") {
                        Text(
                            "No pages match.",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.secondaryText,
                            modifier = Modifier.padding(horizontal = InkSpacing.lg),
                        )
                    }
                }
                alwaysScrollEndSpacer()
            }
        }

        // ------------------------------------------------------------------ page
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            Text(
                "Wiki › ${page.title}",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.secondaryText,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tokens.panel)
                    .padding(horizontal = InkSpacing.lg, vertical = InkSpacing.xs),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = InkSpacing.xl, vertical = InkSpacing.lg),
            ) {
                Text(
                    page.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    page.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.secondaryText,
                    modifier = Modifier.padding(top = InkSpacing.xxs, bottom = InkSpacing.md),
                )
                WikiMarkdown(
                    markdown = page.markdown,
                    onLinkClick = { title ->
                        WikiContent.findByTitle(title)?.let { currentPageId = it.id }
                    },
                )
                Text(
                    "Pages: " + WikiContent.pages.joinToString(" · ") { it.title },
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.secondaryText,
                    modifier = Modifier.padding(top = InkSpacing.xl),
                )
            }
        }
    }
}

/**
 * Small markdown renderer: #/##/### headings, "- " bullets, | tables |,
 * **bold**, and [[Page Title]] links. Anything else renders as a paragraph.
 */
@Composable
fun WikiMarkdown(
    markdown: String,
    onLinkClick: (String) -> Unit,
) {
    val tokens = inkTokens()
    val lines = markdown.lines()
    var index = 0
    Column(verticalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
        while (index < lines.size) {
            val line = lines[index]
            when {
                line.startsWith("### ") -> {
                    Text(
                        line.removePrefix("### "),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = InkSpacing.sm),
                    )
                }
                line.startsWith("## ") -> {
                    Text(
                        line.removePrefix("## "),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = tokens.primaryText,
                        modifier = Modifier.padding(top = InkSpacing.md),
                    )
                }
                line.startsWith("# ") -> Unit // page title already shown
                line.startsWith("{{figure:") -> {
                    val kind = line.removePrefix("{{figure:").trimEnd('}').trim()
                    WikiFigure(
                        kind = kind,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = InkSpacing.sm),
                    )
                }
                line.startsWith("- ") -> {
                    Row {
                        Text(
                            "•  ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.secondaryText,
                        )
                        InlineText(
                            line.removePrefix("- "),
                            onLinkClick,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                line.startsWith("|") -> {
                    val tableLines = lines.drop(index).takeWhile { it.startsWith("|") }
                    index += tableLines.size
                    WikiTable(tableLines, onLinkClick)
                    continue
                }
                line.isBlank() -> Unit
                else -> {
                    InlineText(
                        line,
                        onLinkClick,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                    )
                }
            }
            index++
        }
    }
}

@Composable
private fun WikiTable(
    rows: List<String>,
    onLinkClick: (String) -> Unit,
) {
    val tokens = inkTokens()
    val parsed = rows
        .map { row ->
            row.trim().trimStart('|').trimEnd('|').split('|').map { it.trim() }
        }
        .filterNot { cells -> cells.all { it.matches(Regex("[-: ]*")) } }
    if (parsed.isEmpty()) return
    val columnCount = parsed.maxOf { it.size }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, tokens.hairline, RoundedCornerShape(8.dp)),
    ) {
        parsed.forEachIndexed { rowIndex, cells ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (rowIndex == 0) tokens.hover else tokens.background)
                    .padding(horizontal = InkSpacing.sm, vertical = InkSpacing.xs),
            ) {
                cells.forEachIndexed { cellIndex, cell ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        InlineText(
                            cell,
                            onLinkClick,
                            style = MaterialTheme.typography.bodySmall,
                            bold = rowIndex == 0,
                        )
                    }
                    if (cellIndex < columnCount - 1 && cellIndex < cells.lastIndex) {
                        Spacer(Modifier.width(InkSpacing.sm))
                    }
                }
            }
        }
    }
}

/**
 * Stylized layout diagrams — schematic app "screenshots" drawn with the
 * current theme colors, so the wiki can show each workspace's shape without
 * bundling bitmaps. Kinds: chatting, rpg, novel, brainstorm, prompts.
 */
@Composable
private fun WikiFigure(kind: String, modifier: Modifier = Modifier) {
    val tokens = inkTokens()
    val frame = tokens.panel
    val line = tokens.hairline
    val accent = MaterialTheme.colorScheme.primary
    val faint = tokens.secondaryText.copy(alpha = 0.35f)
    val label = when (kind) {
        "chatting" -> "Chatting · servers, channels, chat"
        "rpg" -> "RPG · scene art, story, action bar"
        "novel" -> "Novel · Plan, Write, Read, Chat, Review"
        "brainstorm" -> "Brainstorm · threads and AI chat"
        "prompts" -> "The prompt dock and composer controls"
        else -> ""
    }
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, line, RoundedCornerShape(10.dp))
                .background(frame),
        ) {
            when (kind) {
                "chatting" -> {
                    // Server rail
                    Column(
                        modifier = Modifier
                            .width(30.dp)
                            .fillMaxHeight()
                            .background(tokens.hover)
                            .padding(vertical = InkSpacing.xs),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(accent))
                        repeat(3) {
                            Box(Modifier.size(14.dp).clip(RoundedCornerShape(7.dp)).background(faint))
                        }
                    }
                    // Channel list
                    Column(
                        modifier = Modifier
                            .width(64.dp)
                            .fillMaxHeight()
                            .padding(InkSpacing.xs),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(accent.copy(alpha = 0.5f)))
                        repeat(4) {
                            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(2.dp)).background(faint))
                        }
                    }
                    // Chat pane
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(InkSpacing.xs),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Box(Modifier.fillMaxWidth(0.35f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(faint))
                        Box(
                            Modifier
                                .fillMaxWidth(0.62f)
                                .height(16.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(faint),
                        )
                        Box(
                            Modifier
                                .fillMaxWidth(0.5f)
                                .height(16.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(accent.copy(alpha = 0.35f))
                                .align(Alignment.End),
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(tokens.hover)
                                .border(1.dp, line, RoundedCornerShape(6.dp)),
                        )
                    }
                }
                "rpg" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(InkSpacing.xs),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(accent.copy(alpha = 0.28f)),
                        )
                        Box(Modifier.fillMaxWidth(0.9f).height(5.dp).clip(RoundedCornerShape(2.dp)).background(faint))
                        Box(Modifier.fillMaxWidth(0.75f).height(5.dp).clip(RoundedCornerShape(2.dp)).background(faint))
                        Box(Modifier.fillMaxWidth(0.82f).height(5.dp).clip(RoundedCornerShape(2.dp)).background(faint))
                        Spacer(Modifier.weight(1f))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(14.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(tokens.hover)
                                .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(7.dp)),
                        )
                    }
                }
                "novel" -> {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(InkSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("Plan", "Write", "Read", "Chat", "Review").forEach {
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (it == "Write") accent.copy(alpha = 0.4f) else tokens.hover)
                                        .padding(horizontal = 5.dp, vertical = 2.dp),
                                ) {
                                    Text(it, style = MaterialTheme.typography.labelSmall, fontSize = 6.sp, color = tokens.primaryText)
                                }
                            }
                        }
                        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(2.dp)).background(faint))
                        Box(Modifier.fillMaxWidth(0.92f).height(5.dp).clip(RoundedCornerShape(2.dp)).background(faint))
                        Row {
                            Box(Modifier.width(26.dp).height(5.dp).clip(RoundedCornerShape(2.dp)).background(accent))
                            Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(2.dp)).background(faint))
                        }
                        Box(Modifier.fillMaxWidth(0.8f).height(5.dp).clip(RoundedCornerShape(2.dp)).background(faint))
                    }
                }
                "brainstorm" -> {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .width(56.dp)
                                .fillMaxHeight()
                                .background(tokens.hover)
                                .padding(InkSpacing.xs),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(2.dp)).background(accent.copy(alpha = 0.5f)))
                            repeat(3) {
                                Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(2.dp)).background(faint))
                            }
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(InkSpacing.xs),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(0.55f)
                                    .height(16.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(faint),
                            )
                            Box(
                                Modifier
                                    .fillMaxWidth(0.7f)
                                    .height(22.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(accent.copy(alpha = 0.3f)),
                            )
                        }
                    }
                }
                "prompts" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(InkSpacing.xs),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(tokens.hover)
                                .border(1.dp, accent, RoundedCornerShape(8.dp))
                                .padding(horizontal = 6.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(faint))
                            Box(Modifier.width(14.dp).height(10.dp).clip(RoundedCornerShape(2.dp)).background(line))
                            Box(Modifier.width(4.dp))
                            Box(Modifier.width(14.dp).height(10.dp).clip(RoundedCornerShape(2.dp)).background(line))
                            Box(Modifier.width(16.dp).height(10.dp).clip(RoundedCornerShape(3.dp)).background(accent.copy(alpha = 0.4f)))
                            Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(accent))
                            Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(faint))
                        }
                    }
                }
            }
        }
        if (label.isNotBlank()) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = tokens.secondaryText,
                modifier = Modifier.padding(top = InkSpacing.xxs),
            )
        }
    }
}

/** Inline text with **bold** and [[links]]. */@Composable
private fun InlineText(
    text: String,
    onLinkClick: (String) -> Unit,
    style: androidx.compose.ui.text.TextStyle,
    bold: Boolean = false,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val textColor = inkTokens().primaryText
    val annotated = remember(text, linkColor) {
        buildAnnotatedString {
            var cursor = 0
            // Tokenise [[links]] and **bold** in one pass.
            val regex = Regex("\\[\\[([^]]+)]]|\\*\\*([^*]+)\\*\\*")
            regex.findAll(text).forEach { match ->
                append(text.substring(cursor, match.range.first))
                val link = match.groupValues[1]
                val boldText = match.groupValues[2]
                if (link.isNotEmpty()) {
                    pushStringAnnotation(WIKI_LINK_TAG, link.trim())
                    pushStyle(
                        SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                    )
                    append(link.trim())
                    pop()
                    pop()
                } else if (boldText.isNotEmpty()) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(boldText)
                    pop()
                }
                cursor = match.range.last + 1
            }
            append(text.substring(cursor))
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        annotated,
        style = style.copy(
            fontWeight = if (bold) FontWeight.Bold else style.fontWeight,
            color = textColor,
        ),
        onTextLayout = { layout = it },
        modifier = Modifier.pointerInput(annotated) {
            awaitEachGesture {
                val down = awaitFirstDown(pass = PointerEventPass.Initial)
                val current = layout ?: return@awaitEachGesture
                val offset = current.getOffsetForPosition(down.position)
                val found = annotated.getStringAnnotations(WIKI_LINK_TAG, offset, offset)
                    .firstOrNull()
                if (found != null) {
                    down.consume()
                    onLinkClick(found.item)
                }
            }
        },
    )
}
