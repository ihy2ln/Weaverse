package com.ihy2ln.weaverse.feature.help

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusSm
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.core.ui.util.alwaysScrollEndSpacer

/**
 * The built-in guide: searchable, one collapsible card per topic. Content lives
 * in [HelpContent] so it stays in step with docs/GUIDE.md.
 */
@Composable
fun HelpScreen(modifier: Modifier = Modifier) {
    val tokens = inkTokens()
    var query by rememberSaveable { mutableStateOf("") }
    var openSection by rememberSaveable { mutableStateOf<String?>(null) }
    val sections = HelpContent.search(query)

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            "Help",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(
                start = InkSpacing.lg,
                end = InkSpacing.lg,
                top = InkSpacing.lg,
            ),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(InkSpacing.lg)
                .clip(RoundedCornerShape(percent = 50))
                .background(tokens.hover)
                .padding(horizontal = InkSpacing.md, vertical = InkSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("⌕", color = tokens.secondaryText, style = MaterialTheme.typography.bodyLarge)
            Box(modifier = Modifier.padding(start = InkSpacing.sm)) {
                if (query.isEmpty()) {
                    Text(
                        "Search the guide",
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.secondaryText,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = tokens.primaryText),
                    cursorBrush = SolidColor(tokens.primaryText),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (sections.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(InkSpacing.xl),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Nothing in the guide matches \"$query\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.secondaryText,
                    textAlign = TextAlign.Center,
                )
            }
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(sections, key = { it.id }) { section ->
                // A search narrows to matching entries, so keep those open.
                val expanded = openSection == section.id || query.isNotBlank()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = InkSpacing.lg, vertical = InkSpacing.xs)
                        .clip(RoundedCornerShape(inkRadiusSm()))
                        .background(tokens.panel)
                        .border(1.dp, tokens.hairline, RoundedCornerShape(inkRadiusSm()))
                        .clickable {
                            openSection = if (openSection == section.id) null else section.id
                        }
                        .padding(InkSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(InkSpacing.xs),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (expanded) "⌄" else "›",
                            style = MaterialTheme.typography.labelMedium,
                            color = tokens.secondaryText,
                            modifier = Modifier.padding(end = InkSpacing.sm),
                        )
                        Text(
                            section.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        section.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.secondaryText,
                    )
                    if (expanded) {
                        section.entries.forEach { entry ->
                            Column(modifier = Modifier.padding(top = InkSpacing.sm)) {
                                Text(
                                    entry.heading.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    letterSpacing = 1.sp,
                                    color = tokens.activePill,
                                )
                                Text(
                                    entry.body,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
            alwaysScrollEndSpacer()
        }
    }
}
