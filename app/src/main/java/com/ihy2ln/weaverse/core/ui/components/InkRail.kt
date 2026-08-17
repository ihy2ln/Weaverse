package com.ihy2ln.weaverse.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ihy2ln.weaverse.core.ui.theme.InkHairline
import com.ihy2ln.weaverse.core.ui.theme.InkPrimaryText
import com.ihy2ln.weaverse.core.ui.theme.InkSecondaryText
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing

@Composable
fun InkRail(
    width: Dp,
    title: String,
    tabs: List<String>,
    selectedTab: String,
    onTabSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .background(Color.White)
            .padding(vertical = InkSpacing.sm),
    ) {
        Text(
            text = title.uppercase(),
            modifier = Modifier.padding(horizontal = InkSpacing.lg, vertical = InkSpacing.sm),
            color = InkPrimaryText,
            fontWeight = FontWeight.Bold,
        )
        InkSegmentedPill(
            options = tabs.map { SegmentedOption(it, it) },
            selectedId = selectedTab,
            onSelect = onTabSelect,
            modifier = Modifier.padding(horizontal = InkSpacing.md, vertical = InkSpacing.sm),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = InkSpacing.md),
        ) {
            content()
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(InkHairline.copy(alpha = 0.35f))
                .padding(top = 1.dp),
        ) {
            bottomBar()
        }
    }
}

@Composable
fun InkEmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier.padding(InkSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(InkSpacing.sm),
    ) {
        Text(text = title, color = InkPrimaryText, fontWeight = FontWeight.SemiBold)
        Text(text = subtitle, color = InkSecondaryText)
        action()
    }
}
