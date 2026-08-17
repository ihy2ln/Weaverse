package com.ihy2ln.weaverse.core.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens

@Composable
fun InkCard(
    modifier: Modifier = Modifier,
    background: Color = inkTokens().panel,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = inkTokens()
    Column(
        modifier = modifier
            .background(background, RoundedCornerShape(InkSpacing.radiusMd))
            .border(1.dp, tokens.hairline, RoundedCornerShape(InkSpacing.radiusMd))
            .padding(InkSpacing.lg),
        content = content,
    )
}
