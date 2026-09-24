package com.ihy2ln.weaverse.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusMd
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens

/** A content card. On the theme's panel color it is drawn as glass; a custom [background] stays solid. */
@Composable
fun InkCard(
    modifier: Modifier = Modifier,
    background: Color = inkTokens().panel,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(inkRadiusMd())
    Column(
        modifier = modifier
            .then(
                if (background == inkTokens().panel) Modifier.glassPanel(shape) else Modifier.background(background, shape),
            )
            .padding(InkSpacing.lg),
        content = content,
    )
}
