package com.ihy2ln.weaverse.core.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.core.ui.util.horizontalScrollIfNeeded

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InkToolbar(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    canGoBack: Boolean = true,
    canGoForward: Boolean = false,
    onBack: () -> Unit = {},
    onForward: () -> Unit = {},
    onSettings: () -> Unit = {},
    trailing: @Composable () -> Unit = {},
    /** Optional second row inside the title panel (e.g. mode/destination pills). */
    belowContent: (@Composable () -> Unit)? = null,
) {
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.screenHeightDp > configuration.screenWidthDp
    val hPad = if (isPortrait) InkSpacing.sm else InkSpacing.md
    val vPad = if (isPortrait) InkSpacing.xs else InkSpacing.sm

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(inkTokens().panel)
            .border(width = InkSpacing.hairline, color = inkTokens().hairline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = hPad, vertical = vPad),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
        ) {
            IconButton(onClick = onBack, enabled = canGoBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            IconButton(onClick = onForward, enabled = canGoForward) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(min = 48.dp),
            ) {
                Text(
                    text = listOf(title, subtitle)
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .joinToString(" · "),
                    modifier = Modifier.basicMarquee(
                        iterations = Int.MAX_VALUE,
                        repeatDelayMillis = 1_200,
                    ),
                    color = inkTokens().primaryText,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.sp,
                    fontSize = if (isPortrait) 14.sp else 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    softWrap = false,
                )
            }
            // widthIn before scroll — bare horizontalScroll in a Row gets Infinity maxWidth and crashes.
            Row(
                modifier = Modifier.horizontalScrollIfNeeded(maxWidthFraction = 0.55f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                trailing()
            }
        }
        belowContent?.invoke()
    }
}

@Composable
fun InkOutlineButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        modifier = modifier
            .border(1.dp, inkTokens().hairline, RoundedCornerShape(InkSpacing.radiusSm))
            .clickable(onClick = onClick)
            .padding(horizontal = InkSpacing.md, vertical = InkSpacing.sm),
        color = inkTokens().primaryText,
        fontSize = 13.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
    )
}
