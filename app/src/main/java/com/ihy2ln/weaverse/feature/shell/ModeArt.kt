package com.ihy2ln.weaverse.feature.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.ihy2ln.weaverse.core.ui.theme.inkTokens

/**
 * One mode's identity on Home, in its banner and on the browser's New Tab Page: key art,
 * an eyebrow, a one-line pitch and a glyph. The art is painted, so [ModeArtImage] washes it
 * in the appearance profile's colors; changing the profile restyles every mode at once.
 */
data class ModeBrand(
    val key: String,
    val title: String,
    val eyebrow: String,
    val tagline: String,
    val icon: ImageVector,
) {
    val art: String get() = "file:///android_asset/images/weaverse/modes/$key.webp"
}

object ModeArt {
    val home = ModeBrand(
        key = "home",
        title = "Weaverse",
        eyebrow = "EVERY WORLD YOU MAKE",
        tagline = "Write it, play it, draw it, live in it.",
        icon = Icons.Default.Home,
    )

    fun of(mode: AppMode): ModeBrand = when (mode) {
        AppMode.Novel -> ModeBrand(
            "novel", mode.label, "WRITE & READ",
            "Plan, write and read your books, with every codex entry beside you.",
            Icons.AutoMirrored.Filled.MenuBook,
        )
        AppMode.Roleplay -> ModeBrand(
            "rpg", mode.label, "ADVENTURE",
            "Run campaigns with your party, roster and lore. The AI keeps the table.",
            Icons.Default.AutoAwesome,
        )
        AppMode.Games -> ModeBrand(
            "games", mode.label, "PLAY",
            "Adams Haven: a card roguelite and tower tycoon.",
            Icons.Default.SportsEsports,
        )
        AppMode.Chatting -> ModeBrand(
            "browser", mode.label, "BROWSE & CHAT",
            "The web behind Shields, with WeaverSocial and your chats in tabs.",
            Icons.Default.Public,
        )
        AppMode.Storyboard -> ModeBrand(
            "manga", mode.label, "DRAW & READ",
            "Read manga, then make your own: pages, panels and lettering.",
            Icons.Default.Collections,
        )
        AppMode.Notes -> ModeBrand(
            "notes", "Brainstorm", "IDEAS",
            "Think out loud with AI, then keep the ideas worth keeping.",
            Icons.Default.Lightbulb,
        )
    }

    /** Splash choices for Appearance, in picker order. Blank means "Weaverse". */
    val splashChoices: List<ModeBrand> get() = listOf(home) + AppMode.entries.map(::of)

    fun splash(key: String): ModeBrand = splashChoices.firstOrNull { it.key == key } ?: home
}

/**
 * Painted key art washed in the active profile: a light color grade toward the accent, then
 * a fade into the page background, so the same painting sits naturally on Streaming black,
 * Sakura cream or Arcade green. [fade] is how far down the fade starts (1 = no fade).
 */
@Composable
fun ModeArtImage(
    brand: ModeBrand,
    modifier: Modifier = Modifier,
    fade: Float = 0.45f,
    alignment: Alignment = Alignment.Center,
    model: Any? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val tokens = inkTokens()
    val background = tokens.background
    val accent = tokens.activePill
    val light = background.luminance() > 0.5f
    Box(modifier) {
        AsyncImage(
            model = model ?: brand.art,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = alignment,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    // Color grade: pulls the painting's hue toward the profile accent. On
                    // API < 29 BlendMode.Color falls back to a plain translucent tint.
                    drawRect(accent.copy(alpha = if (light) 0.18f else 0.24f), blendMode = BlendMode.Color)
                    if (light) drawRect(background.copy(alpha = 0.22f))
                },
        )
        if (fade < 1f) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to background.copy(alpha = if (light) 0.35f else 0.18f),
                        fade to background.copy(alpha = if (light) 0.25f else 0.08f),
                        1f to background,
                    ),
                ),
            )
        }
        content()
    }
}

/** The Weaverse "W", the launcher icon's path, filled with the profile's accent. */
@Composable
fun WeaverseMark(modifier: Modifier = Modifier, color: Color = inkTokens().activePill) {
    Canvas(modifier) {
        val s = size.minDimension / 108f
        val dx = (size.width - 108f * s) / 2f
        val dy = (size.height - 108f * s) / 2f
        val points = listOf(
            22f to 32f, 34f to 32f, 42f to 64f, 50f to 42f, 58f to 42f, 66f to 64f, 74f to 32f,
            86f to 32f, 72f to 80f, 60f to 80f, 54f to 58f, 48f to 80f, 36f to 80f,
        )
        val path = Path().apply {
            points.forEachIndexed { index, (x, y) ->
                if (index == 0) moveTo(dx + x * s, dy + y * s) else lineTo(dx + x * s, dy + y * s)
            }
            close()
        }
        drawPath(
            path,
            Brush.linearGradient(
                listOf(lighten(color, 0.45f), color),
                start = Offset(dx + 22f * s, dy + 32f * s),
                end = Offset(dx + 86f * s, dy + 80f * s),
            ),
        )
    }
}

/** [color] moved [amount] of the way to white. */
private fun lighten(color: Color, amount: Float): Color = Color(
    red = color.red + (1f - color.red) * amount,
    green = color.green + (1f - color.green) * amount,
    blue = color.blue + (1f - color.blue) * amount,
    alpha = color.alpha,
)
