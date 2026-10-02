package com.ihy2ln.weaverse.feature.browser

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ihy2ln.weaverse.core.ui.theme.inkTokens

/** WeaverBrowser's colors: Brave's layout in Weaverse's streaming palette. */
@Immutable
data class BrowserColors(
    val private: Boolean,
    val bar: Color,
    val field: Color,
    val menu: Color,
    val page: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val hairline: Color,
) {
    val shieldOrange = Color(0xFFFB542B)
    val shieldRed = Color(0xFFE2052A)
    val statOrange = Color(0xFFFF6A47)
    val statPurple = Color(0xFFA0A5EB)
    val cardScrim = Color.Black.copy(alpha = .55f)
}

@Composable
fun browserColors(private: Boolean): BrowserColors {
    val t = inkTokens()
    return if (!private) BrowserColors(
        private = false,
        bar = t.panel,
        field = t.hover,
        menu = Color(0xFF1E1D29),
        page = t.background,
        text = t.primaryText,
        muted = t.secondaryText,
        accent = t.activePill,
        onAccent = t.activePillLabel,
        hairline = t.hairline,
    ) else BrowserColors(
        private = true,
        bar = Color(0xFF1A1230),
        field = Color(0xFF2B1F4A),
        menu = Color(0xFF221836),
        page = Color(0xFF120B22),
        text = Color.White,
        muted = Color(0xFFB9AEDB),
        accent = Color(0xFFC8B6FF),
        onAccent = Color(0xFF120B22),
        hairline = Color(0xFF3A2E5C),
    )
}

/** The Shields button: an orange shield, greyed out when Shields are down for the site. */
@Composable
fun ShieldGlyph(up: Boolean, modifier: Modifier = Modifier, size: Dp = 26.dp, mono: Color? = null) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val shield = Path().apply {
            moveTo(w * .5f, h * .04f)
            lineTo(w * .9f, h * .2f)
            cubicTo(w * .92f, h * .55f, w * .78f, h * .8f, w * .5f, h * .97f)
            cubicTo(w * .22f, h * .8f, w * .08f, h * .55f, w * .1f, h * .2f)
            close()
        }
        when {
            mono != null -> drawPath(shield, mono, style = Stroke(width = w * .09f, join = StrokeJoin.Round))
            up -> drawPath(shield, Brush.verticalGradient(listOf(Color(0xFFFF7A3D), Color(0xFFE2052A))))
            else -> drawPath(shield, Color(0xFF8E8D99))
        }
        // A loom's weave across the face: the Weaverse mark.
        val ink = if (mono != null) mono else Color.White
        val stroke = Stroke(width = w * .075f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val weave = Path().apply {
            moveTo(w * .28f, h * .36f)
            lineTo(w * .38f, h * .66f)
            lineTo(w * .5f, h * .44f)
            lineTo(w * .62f, h * .66f)
            lineTo(w * .72f, h * .36f)
        }
        drawPath(weave, ink, style = stroke)
    }
}

/** Private tab glasses, as Brave draws them. */
@Composable
fun GlassesGlyph(tint: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val lensW = w * .38f
        val lensH = h * .3f
        val top = h * .38f
        drawRoundRect(tint, Offset(w * .06f, top), Size(lensW, lensH), CornerRadius(lensH * .45f))
        drawRoundRect(tint, Offset(w * .56f, top), Size(lensW, lensH), CornerRadius(lensH * .45f))
        drawLine(tint, Offset(w * .42f, top + lensH * .3f), Offset(w * .58f, top + lensH * .3f), strokeWidth = h * .07f)
        drawLine(tint, Offset(0f, top + lensH * .1f), Offset(w * .1f, top), strokeWidth = h * .06f)
        drawLine(tint, Offset(w, top + lensH * .1f), Offset(w * .9f, top), strokeWidth = h * .06f)
    }
}

/** The tab-count button: a rounded square with the number in it. */
@Composable
fun TabCountGlyph(count: Int, tint: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.size(24.dp).border(2.dp, tint, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (count > 99) ":D" else count.toString(), color = tint, fontSize = if (count > 9) 10.sp else 12.sp, fontWeight = FontWeight.Bold)
    }
}
