package com.ihy2ln.weaverse.feature.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.ihy2ln.weaverse.core.ui.components.ExpandableSection
import com.ihy2ln.weaverse.core.ui.components.InkCard
import com.ihy2ln.weaverse.core.ui.components.InkSegmentedPill
import com.ihy2ln.weaverse.core.ui.components.SegmentedOption
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.core.ui.util.AlwaysScrollEndPadding
import com.ihy2ln.weaverse.core.ui.util.adaptiveContentPadding

@Composable
fun HelpScreen(
    modifier: Modifier = Modifier,
    initialTab: HelpContent.Tab = HelpContent.Tab.Tutorial,
) {
    val tokens = inkTokens()
    val contentPad = adaptiveContentPadding()
    var tabName by rememberSaveable { mutableStateOf(initialTab.name) }
    val tab = runCatching { HelpContent.Tab.valueOf(tabName) }.getOrDefault(HelpContent.Tab.Tutorial)
    val sections = HelpContent.sectionsFor(tab)
    var expandedTitle by rememberSaveable {
        mutableStateOf(sections.firstOrNull()?.title.orEmpty())
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPad)
            .padding(bottom = AlwaysScrollEndPadding),
        verticalArrangement = Arrangement.spacedBy(InkSpacing.sm),
    ) {
        Text(
            text = "Help",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Tutorial for first sessions, Manual for reference, What's new for this build.",
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.secondaryText,
        )
        InkSegmentedPill(
            options = HelpContent.Tab.entries.map { SegmentedOption(it.name, it.label) },
            selectedId = tab.name,
            onSelect = { id ->
                tabName = id
                expandedTitle = HelpContent.sectionsFor(
                    runCatching { HelpContent.Tab.valueOf(id) }.getOrDefault(HelpContent.Tab.Tutorial),
                ).firstOrNull()?.title.orEmpty()
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(InkSpacing.xs))
        sections.forEach { section ->
            val open = expandedTitle == section.title
            InkCard(modifier = Modifier.fillMaxWidth()) {
                ExpandableSection(
                    title = section.title,
                    expanded = open,
                    onToggle = {
                        expandedTitle = if (open) "" else section.title
                    },
                ) {
                    Text(
                        text = section.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.primaryText,
                        modifier = Modifier.padding(bottom = InkSpacing.sm),
                    )
                }
            }
        }
    }
}
