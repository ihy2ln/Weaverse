package com.ihy2ln.weaverse.feature.shell

import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.ihy2ln.weaverse.core.ui.components.LoopingVideoBackground
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.data.settings.ModeBackdrops
import java.io.File

/** Calm looping videos shipped with the app, for focus: rain, ocean, forest and more. */
object AmbientVideos {
    data class Ambient(val key: String, val title: String)

    val all = listOf(
        Ambient("rain", "Rain on glass"),
        Ambient("ocean", "Moonlit ocean"),
        Ambient("forest", "Swaying forest"),
        Ambient("space", "Deep space"),
        Ambient("snow", "Snowfall"),
        Ambient("aurora", "Aurora"),
    )

    fun uri(key: String) = "asset:///videos/ambient/$key.mp4"
    fun poster(key: String) = "file:///android_asset/videos/ambient/$key.jpg"
    fun title(key: String) = all.firstOrNull { it.key == key }?.title ?: key
}

/** What a page sits on. */
sealed interface BackdropSource {
    /** Stable identity, so changing it crossfades and keeping it doesn't restart a video. */
    val key: String

    /** Key art, or the user's own [picture] washed the same way. */
    data class Art(val brand: ModeBrand, val picture: Any? = null, override val key: String) : BackdropSource

    data class Video(val uri: String, override val key: String) : BackdropSource

    data object Wallpaper : BackdropSource {
        override val key = ModeBackdrops.WALLPAPER
    }
}

/**
 * How loud the backdrop is. Home and each mode's first page show it in full; past that it
 * goes quiet (blurred, dimmed, a video paused) so the work in front stays the focus.
 */
enum class BackdropLevel { Splash, Hero, Quiet }

/** A user's picture or video in the media library: its file, and whether it is a video. */
data class BackdropFile(val path: String, val video: Boolean)

/** Resolves one saved entry; null when it points at something that no longer exists. */
fun backdropSourceOf(entry: String, files: Map<String, BackdropFile>): BackdropSource? = when {
    entry == ModeBackdrops.WALLPAPER -> BackdropSource.Wallpaper
    entry.startsWith(ModeBackdrops.ART) -> BackdropSource.Art(ModeArt.splash(entry.removePrefix(ModeBackdrops.ART)), key = entry)
    entry.startsWith(ModeBackdrops.VIDEO) -> BackdropSource.Video(AmbientVideos.uri(entry.removePrefix(ModeBackdrops.VIDEO)), entry)
    entry.startsWith(ModeBackdrops.MEDIA) -> files[entry.removePrefix(ModeBackdrops.MEDIA)]?.let { file ->
        if (file.video) BackdropSource.Video(file.path, entry) else BackdropSource.Art(ModeArt.home, File(file.path), entry)
    }
    else -> null
}

/** The backdrop itself, under every page: [wallpaper] draws the appearance wallpaper. */
@Composable
fun ModeBackdropLayer(
    source: BackdropSource,
    level: BackdropLevel,
    wallpaper: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val quiet = level == BackdropLevel.Quiet
    // Blur needs Android 12; older phones get a heavier scrim instead.
    val blur by animateDpAsState(if (quiet && Build.VERSION.SDK_INT >= 31) 22.dp else 0.dp, tween(420), label = "backdropBlur")
    Box(modifier) {
        Crossfade(targetState = source, label = "backdrop", animationSpec = tween(700), modifier = Modifier.fillMaxSize().blur(blur)) { shown ->
            when (shown) {
                BackdropSource.Wallpaper -> Box(Modifier.fillMaxSize()) { wallpaper() }
                is BackdropSource.Video -> LoopingVideoBackground(path = shown.uri, playing = !quiet, modifier = Modifier.fillMaxSize())
                is BackdropSource.Art -> {
                    val drift = rememberInfiniteTransition(label = "backdropDrift")
                    val zoom by drift.animateFloat(
                        initialValue = 1.02f,
                        targetValue = 1.1f,
                        animationSpec = infiniteRepeatable(tween(32_000, easing = LinearEasing), RepeatMode.Reverse),
                        label = "backdropZoom",
                    )
                    ModeArtImage(
                        brand = shown.brand,
                        model = shown.picture,
                        fade = 1f,
                        alignment = Alignment.TopCenter,
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            val z = if (quiet) 1.04f else zoom
                            scaleX = z
                            scaleY = z
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.2f)
                        },
                    )
                }
            }
        }
        BackdropScrim(level, Modifier.fillMaxSize())
    }
}

/** Keeps text legible over the backdrop; each level its own weight. */
@Composable
private fun BackdropScrim(level: BackdropLevel, modifier: Modifier) {
    val background = inkTokens().background
    val light = background.luminance() > 0.5f
    val quietBoost = if (Build.VERSION.SDK_INT >= 31) 0f else 0.1f
    val heroWeight by animateFloatAsState(if (level == BackdropLevel.Hero) 1f else 0f, tween(420), label = "heroScrim")
    val quietWeight by animateFloatAsState(if (level == BackdropLevel.Quiet) 1f else 0f, tween(420), label = "quietScrim")
    val splashWeight by animateFloatAsState(if (level == BackdropLevel.Splash) 1f else 0f, tween(420), label = "splashScrim")
    Box(modifier) {
        // Home: readable top, open middle, solid bottom under the rows.
        if (splashWeight > 0f) Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = splashWeight }.background(
                Brush.verticalGradient(
                    0f to background.copy(alpha = if (light) 0.82f else 0.78f),
                    0.09f to background.copy(alpha = if (light) 0.5f else 0.42f),
                    0.2f to background.copy(alpha = if (light) 0.18f else 0.06f),
                    0.3f to background.copy(alpha = if (light) 0.3f else 0.22f),
                    0.5f to background.copy(alpha = if (light) 0.8f else 0.72f),
                    0.72f to background.copy(alpha = 0.95f),
                    1f to background,
                ),
            ),
        )
        // A mode's first page: the art stays in view behind its cards and rows.
        if (heroWeight > 0f) Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = heroWeight }.background(
                Brush.verticalGradient(
                    0f to background.copy(alpha = if (light) 0.78f else 0.7f),
                    0.18f to background.copy(alpha = if (light) 0.45f else 0.3f),
                    0.55f to background.copy(alpha = if (light) 0.55f else 0.45f),
                    1f to background.copy(alpha = if (light) 0.85f else 0.8f),
                ),
            ),
        )
        // Deeper pages: a quiet, even wash.
        if (quietWeight > 0f) Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = quietWeight }
                .background(background.copy(alpha = (if (light) 0.66f else 0.6f) + quietBoost)),
        )
    }
}
