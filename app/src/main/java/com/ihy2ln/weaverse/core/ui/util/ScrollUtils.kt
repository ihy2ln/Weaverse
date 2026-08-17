package com.ihy2ln.weaverse.core.ui.util

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ihy2ln.weaverse.core.ui.theme.inkTokens

/** Extra room so filled screens remain vertically scrollable. */
val AlwaysScrollEndPadding: Dp = 168.dp

/** Thin left/right strips so the finger can scroll without hitting text/media. */
val ScrollGutterWidth: Dp = 12.dp

fun scrollGutterPadding(): PaddingValues = PaddingValues(horizontal = ScrollGutterWidth)

/**
 * Draws hairline gutters behind a full-bleed scroll container.
 * Pair with [scrollGutterPadding] on the LazyColumn so content stays inset;
 * edge touches still hit the scrollable and scroll the parent.
 */
@Composable
fun ScrollGutterBackdrop(
    modifier: Modifier = Modifier,
    gutterWidth: Dp = ScrollGutterWidth,
    content: @Composable () -> Unit,
) {
    val line = inkTokens().hairline.copy(alpha = 0.55f)
    Box(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .width(gutterWidth)
                    .fillMaxHeight()
                    .background(line),
            )
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .width(gutterWidth)
                    .fillMaxHeight()
                    .background(line),
            )
        }
        content()
    }
}

/**
 * Horizontal scroll with a finite max width so it is safe inside a parent [Row]
 * (which measures unweighted children with Infinity max width).
 */
fun Modifier.horizontalScrollIfNeeded(
    maxWidthFraction: Float = 0.85f,
): Modifier = composed {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val fraction = maxWidthFraction.coerceIn(0.2f, 1f)
    val maxWidth = (screenWidth * fraction).dp
    this
        .widthIn(max = maxWidth)
        .horizontalScroll(rememberScrollState())
}

/** Tighter padding in portrait so content is not squashed by large fixed insets. */
@Composable
fun adaptiveContentPadding(): Dp {
    val configuration = LocalConfiguration.current
    return if (configuration.screenHeightDp > configuration.screenWidthDp) {
        10.dp
    } else {
        16.dp
    }
}

/** Trailing spacer item so LazyColumns can always scroll past filled content. */
fun LazyListScope.alwaysScrollEndSpacer(height: Dp = AlwaysScrollEndPadding) {
    item(key = "__always_scroll_end") {
        Spacer(modifier = Modifier.fillMaxWidth().height(height))
    }
}

/** Trailing spacer so two-column grids can still scroll past filled content. */
fun LazyGridScope.alwaysScrollEndSpacer(height: Dp = AlwaysScrollEndPadding) {
    item(
        key = "__always_scroll_end",
        span = { GridItemSpan(maxLineSpan) },
    ) {
        Spacer(modifier = Modifier.fillMaxWidth().height(height))
    }
}
