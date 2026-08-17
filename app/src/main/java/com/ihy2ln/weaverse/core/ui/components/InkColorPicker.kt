package com.ihy2ln.weaverse.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ihy2ln.weaverse.core.ui.theme.CodexCategoryColors
import com.ihy2ln.weaverse.core.ui.theme.InkHairline
import com.ihy2ln.weaverse.core.ui.theme.InkPrimaryText
import com.ihy2ln.weaverse.core.ui.theme.InkSecondaryText
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InkColorPicker(
    selected: Color,
    onSelect: (Color) -> Unit,
    modifier: Modifier = Modifier,
    customColors: List<Color> = CodexCategoryColors,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(InkSpacing.sm)) {
        Text(text = "Color", color = InkSecondaryText, fontSize = 12.sp)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(InkSpacing.sm),
        ) {
            customColors.forEach { color ->
                val selectedThis = color == selected
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (selectedThis) 2.dp else 1.dp,
                            color = if (selectedThis) InkPrimaryText else InkHairline,
                            shape = CircleShape,
                        )
                        .clickable { onSelect(color) },
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(InkSpacing.radiusSm))
                .border(1.dp, InkHairline, RoundedCornerShape(InkSpacing.radiusSm))
                .padding(InkSpacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = "Selected", color = InkSecondaryText, fontSize = 12.sp)
            Text(
                text = "#%08X".format(selected.value.toInt()),
                color = InkPrimaryText,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
            )
        }
    }
}
