package com.ihy2ln.weaverse.core.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.min

private data class ProfileArt(
    val top: Color,
    val middle: Color,
    val bottom: Color,
    val glows: List<Triple<Color, Offset, Float>>,
)

/**
 * Ambient background art per appearance profile, drawn with Compose brushes
 * (painterResource cannot load gradient layer-lists). Shown behind the whole
 * shell when no custom background image is set.
 */
@Composable
fun ProfileBackgroundArt(profile: AppearanceProfile, modifier: Modifier = Modifier) {
    val art = when (profile) {
        AppearanceProfile.Fantasy -> ProfileArt(
            top = Color(0xFF1B1533),
            middle = Color(0xFF241C46),
            bottom = Color(0xFF120E24),
            glows = listOf(
                Triple(Color(0x4DE8C872), Offset(0.82f, 0.85f), 0.9f),
                Triple(Color(0x267B6FD1), Offset(0.15f, 0.1f), 0.8f),
            ),
        )
        AppearanceProfile.Arcade -> ProfileArt(
            top = Color(0xFF0B1026),
            middle = Color(0xFF10152E),
            bottom = Color(0xFF141A38),
            glows = listOf(
                Triple(Color(0x4024D9E0), Offset(0.0f, 1.0f), 0.95f),
                Triple(Color(0x38FF4FA3), Offset(1.0f, 0.0f), 0.95f),
            ),
        )
        AppearanceProfile.Synthwave -> ProfileArt(
            top = Color(0xFF2A1246),
            middle = Color(0xFF4A1A6B),
            bottom = Color(0xFF7A1E5C),
            glows = listOf(
                Triple(Color(0x59FF5FA8), Offset(0.5f, 1.05f), 0.8f),
            ),
        )
        AppearanceProfile.Chill -> ProfileArt(
            top = Color(0xFFCFE8E2),
            middle = Color(0xFFE6F0E9),
            bottom = Color(0xFFF7EFE2),
            glows = listOf(
                Triple(Color(0x40FFD9A8), Offset(0.25f, 0.1f), 0.85f),
            ),
        )
        AppearanceProfile.Tabletop -> ProfileArt(
            top = Color(0xFFE9D9B8),
            middle = Color(0xFFF2E6CC),
            bottom = Color(0xFFD9C49A),
            glows = listOf(
                Triple(Color(0x4DFFFFFF), Offset(0.5f, 0.0f), 0.95f),
                Triple(Color(0x337A5C3A), Offset(0.5f, 1.1f), 1.0f),
            ),
        )
        AppearanceProfile.Classic -> ProfileArt(
            top = Color(0xFFEDEBE7),
            middle = Color(0xFFE4E1DB),
            bottom = Color(0xFFDDD9D2),
            glows = listOf(
                Triple(Color(0x33FFFFFF), Offset(0.5f, 0.15f), 0.9f),
            ),
        )
        AppearanceProfile.Noir -> ProfileArt(
            top = Color(0xFF141414),
            middle = Color(0xFF1C1C1C),
            bottom = Color(0xFF0A0A0A),
            glows = listOf(
                Triple(Color(0x33FFFFFF), Offset(0.75f, 0.1f), 0.7f),
                Triple(Color(0x22000000), Offset(0.2f, 1.0f), 0.9f),
            ),
        )
        AppearanceProfile.Storybook -> ProfileArt(
            top = Color(0xFF2C3E30),
            middle = Color(0xFF3B5240),
            bottom = Color(0xFF1A241C),
            glows = listOf(
                Triple(Color(0x4DD9B96A), Offset(0.15f, 0.85f), 0.85f),
                Triple(Color(0x26A3C48F), Offset(0.85f, 0.15f), 0.8f),
            ),
        )
        AppearanceProfile.Cyberdeck -> ProfileArt(
            top = Color(0xFF06121A),
            middle = Color(0xFF0B1E28),
            bottom = Color(0xFF04141C),
            glows = listOf(
                Triple(Color(0x4022D3EE), Offset(0.1f, 0.95f), 0.9f),
                Triple(Color(0x2E22D3EE), Offset(0.9f, 0.1f), 0.8f),
            ),
        )
        AppearanceProfile.Meadow -> ProfileArt(
            top = Color(0xFFD8EDB9),
            middle = Color(0xFFEAF4DC),
            bottom = Color(0xFFC2DFA0),
            glows = listOf(
                Triple(Color(0x4DFFF7C2), Offset(0.25f, 0.1f), 0.9f),
                Triple(Color(0x3376A83B), Offset(0.85f, 0.9f), 0.85f),
            ),
        )
        AppearanceProfile.Ember -> ProfileArt(
            top = Color(0xFF1B1008),
            middle = Color(0xFF2A1810),
            bottom = Color(0xFF140C06),
            glows = listOf(
                Triple(Color(0x59F97316), Offset(0.5f, 1.05f), 0.85f),
                Triple(Color(0x26FBBF24), Offset(0.85f, 0.2f), 0.7f),
            ),
        )
        AppearanceProfile.DeepSea -> ProfileArt(
            top = Color(0xFF061620),
            middle = Color(0xFF0A2230),
            bottom = Color(0xFF030A10),
            glows = listOf(
                Triple(Color(0x4006B6D4), Offset(0.2f, 0.9f), 0.9f),
                Triple(Color(0x2E0EA5E9), Offset(0.8f, 0.15f), 0.8f),
            ),
        )
        AppearanceProfile.Sakura -> ProfileArt(
            top = Color(0xFFF7D4DE),
            middle = Color(0xFFFBE4EA),
            bottom = Color(0xFFF2C7D3),
            glows = listOf(
                Triple(Color(0x4DFFFFFF), Offset(0.3f, 0.1f), 0.9f),
                Triple(Color(0x33C2527A), Offset(0.8f, 0.85f), 0.85f),
            ),
        )
        AppearanceProfile.Sunset -> ProfileArt(
            top = Color(0xFF3A1D52),
            middle = Color(0xFF7A3A50),
            bottom = Color(0xFFB4652A),
            glows = listOf(
                Triple(Color(0x59F59E0B), Offset(0.5f, 1.0f), 0.85f),
                Triple(Color(0x336D28D9), Offset(0.15f, 0.15f), 0.8f),
            ),
        )
        AppearanceProfile.Frost -> ProfileArt(
            top = Color(0xFFD6E8F7),
            middle = Color(0xFFE7F1FA),
            bottom = Color(0xFFC2DAEE),
            glows = listOf(
                Triple(Color(0x4DFFFFFF), Offset(0.5f, 0.1f), 0.95f),
                Triple(Color(0x264FA8E0), Offset(0.85f, 0.85f), 0.8f),
            ),
        )
        AppearanceProfile.Royal -> ProfileArt(
            top = Color(0xFF1C1233),
            middle = Color(0xFF2A1D4A),
            bottom = Color(0xFF140D24),
            glows = listOf(
                Triple(Color(0x4DF59E0B), Offset(0.5f, 0.08f), 0.75f),
                Triple(Color(0x338B5CF6), Offset(0.85f, 0.9f), 0.85f),
            ),
        )
    }
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawRect(brush = Brush.verticalGradient(listOf(art.top, art.middle, art.bottom)))
        val radius = min(w, h)
        art.glows.forEach { (color, centerFrac, radiusFrac) ->
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(color, color.copy(alpha = 0f)),
                    center = Offset(w * centerFrac.x, h * centerFrac.y),
                    radius = radius * radiusFrac,
                ),
            )
        }
    }
}
