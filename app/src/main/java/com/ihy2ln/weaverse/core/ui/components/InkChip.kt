package com.ihy2ln.weaverse.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ihy2ln.weaverse.core.ui.theme.InkHairline
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.InkSecondaryText

@Composable
fun InkChip(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(999.dp)
    val bg = color.copy(alpha = if (selected) 0.22f else 0.12f)
    Box(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
            )
            .border(1.dp, if (selected) color else InkHairline, shape)
            .padding(horizontal = InkSpacing.md, vertical = InkSpacing.xs),
    ) {
        Text(
            text = if (onRemove != null) "$label ×" else label,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun InkGhostChip(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .border(1.dp, InkHairline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = InkSpacing.md, vertical = InkSpacing.xs),
    ) {
        Text(text = label, color = InkSecondaryText, fontSize = 12.sp)
    }
}
