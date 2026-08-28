package com.ihy2ln.weaverse.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ihy2ln.weaverse.core.ui.theme.InkActivePill
import com.ihy2ln.weaverse.core.ui.theme.InkActivePillLabel
import com.ihy2ln.weaverse.core.ui.theme.InkPrimaryText
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing

data class SegmentedOption(
    val id: String,
    val label: String,
)

@Composable
fun InkSegmentedPill(
    options: List<SegmentedOption>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
    compact: Boolean = false,
) {
    Row(
        modifier = modifier
            .then(if (scrollable) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
            .clip(RoundedCornerShape(999.dp))
            .background(Color.Transparent),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 0.dp else InkSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { option ->
            val selected = option.id == selectedId
            Text(
                text = option.label,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (selected) InkActivePill else Color.Transparent)
                    .clickable { onSelect(option.id) }
                    .padding(
                        horizontal = if (compact) InkSpacing.md else InkSpacing.lg,
                        vertical = if (compact) InkSpacing.xs else InkSpacing.sm,
                    ),
                color = if (selected) InkActivePillLabel else InkPrimaryText,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}
