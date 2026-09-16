package com.ihy2ln.weaverse.core.ui.components

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import com.ihy2ln.weaverse.core.media.LetteringRenderer
import com.ihy2ln.weaverse.core.media.toTypesetLayer
import com.ihy2ln.weaverse.core.text.TextOverlay

/** Text-only local box preview; the outer box owns position and rotation. */
@Composable
fun OverlayLettering(overlay: TextOverlay, pageWidthPx: Float, modifier: Modifier = Modifier, showOverflow: Boolean = false) {
    Canvas(modifier) {
        val layer = overlay.toTypesetLayer().copy(
            normalized = RectF(0f, 0f, 1f, 1f), rotationDeg = 0f,
            referenceWidth = 0f,
            fontSizePx = overlay.fontSizeSp / .55f * pageWidthPx / 1000f,
            strokeWidthPx = overlay.strokeWidth * pageWidthPx / 1000f,
        )
        val overflow = LetteringRenderer.draw(drawContext.canvas.nativeCanvas, size.width, size.height, layer)
        if (showOverflow && overflow) {
            drawLine(Color.Red, androidx.compose.ui.geometry.Offset(0f, size.height - 2f),
                androidx.compose.ui.geometry.Offset(size.width, size.height - 2f), strokeWidth = 3f)
        }
    }
}
