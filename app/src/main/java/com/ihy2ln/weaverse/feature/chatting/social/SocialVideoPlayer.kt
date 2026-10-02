package com.ihy2ln.weaverse.feature.chatting.social

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File

/** Formats a post's playable video, from a link's address or a source's own variants. */
object SocialVideo {
    private val youtubeId = Regex("""(?:youtube\.com/(?:watch\?(?:.*&)?v=|shorts/|embed/|live/)|youtu\.be/)([A-Za-z0-9_-]{11})""")
    private val directVideo = Regex("""^https://\S+\.(?:mp4|webm|m4v|mov)(?:\?\S*)?$""", RegexOption.IGNORE_CASE)
    private val directImage = Regex("""^https://\S+\.(?:jpe?g|png|gif|webp)(?:\?\S*)?$""", RegexOption.IGNORE_CASE)
    private val link = Regex("""https://\S+""")

    fun youtubeIdIn(url: String): String? = youtubeId.find(url)?.groupValues?.get(1)

    /** The first link in [text] that is a picture, a video file or a YouTube video. */
    fun mediaLinkIn(text: String): LinkMedia? = link.findAll(text).map { it.value.trimEnd('.', ',', ')', ']', '!', '?') }
        .firstNotNullOfOrNull { url ->
            youtubeIdIn(url)?.let { LinkMedia.YouTube(it) }
                ?: url.takeIf { directVideo.matches(it) }?.let { LinkMedia.Video(it) }
                ?: url.takeIf { directImage.matches(it) }?.let { LinkMedia.Image(it) }
        }

    sealed interface LinkMedia {
        data class YouTube(val id: String) : LinkMedia
        data class Video(val url: String) : LinkMedia
        data class Image(val url: String) : LinkMedia
    }
}

/**
 * A post's video with the usual controls: the player's own play/pause, scrubbing, rewind and
 * fast-forward, plus mute, quality (when the source offers several) and playback speed.
 * Nothing loads until it's tapped; leaving the screen stops it.
 */
@Composable
fun SocialVideoPlayer(sources: List<Pair<String, String>>, poster: String?, description: String, modifier: Modifier = Modifier) {
    if (sources.isEmpty()) return
    val youtube = sources.firstOrNull { it.first == "youtube" }?.second
    if (youtube != null) {
        YouTubePlayer(youtube, modifier)
        return
    }
    var started by remember(sources) { mutableStateOf(false) }
    Box(modifier.fillMaxWidth().aspectRatio(16 / 9f).clip(RoundedCornerShape(12.dp)).background(Color.Black)) {
        if (!started) {
            Poster(poster, description) { started = true }
        } else {
            VideoSurface(sources)
        }
    }
}

@Composable
private fun Poster(poster: String?, description: String, onPlay: () -> Unit) {
    Box(Modifier.fillMaxSize().clickable(onClick = onPlay), contentAlignment = Alignment.Center) {
        if (!poster.isNullOrBlank()) {
            coil3.compose.AsyncImage(
                model = if (poster.startsWith("https://")) poster else File(poster),
                contentDescription = description.take(80),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(Modifier.size(58.dp).clip(CircleShape).background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
            Text("▶", color = Color.White, fontSize = 26.sp)
        }
    }
}

@Composable
private fun VideoSurface(sources: List<Pair<String, String>>) {
    val context = LocalContext.current
    var quality by remember(sources) { mutableStateOf(sources.last()) }
    var muted by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var qualityMenu by remember { mutableStateOf(false) }
    var speedMenu by remember { mutableStateOf(false) }
    val player = remember {
        ExoPlayer.Builder(context)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build().apply {
                setMediaItem(MediaItem.fromUri(Uri.parse(quality.second)))
                repeatMode = Player.REPEAT_MODE_ONE
                playWhenReady = true
                prepare()
            }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    setShowRewindButton(true)
                    setShowFastForwardButton(true)
                    setShowPreviousButton(false)
                    setShowNextButton(false)
                    controllerShowTimeoutMs = 2_500
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        Row(Modifier.align(Alignment.TopEnd).padding(6.dp)) {
            Chip(if (muted) "🔇" else "🔊") {
                muted = !muted
                player.volume = if (muted) 0f else 1f
            }
            if (sources.size > 1) {
                Box {
                    Chip(quality.first) { qualityMenu = true }
                    DropdownMenu(qualityMenu, { qualityMenu = false }) {
                        sources.forEach { option ->
                            DropdownMenuItem(text = { Text(option.first) }, onClick = {
                                qualityMenu = false
                                if (option != quality) {
                                    // Same spot, different file.
                                    val at = player.currentPosition
                                    quality = option
                                    player.setMediaItem(MediaItem.fromUri(Uri.parse(option.second)), at)
                                    player.prepare()
                                }
                            })
                        }
                    }
                }
            }
            Box {
                Chip("${if (speed % 1f == 0f) speed.toInt() else speed}×") { speedMenu = true }
                DropdownMenu(speedMenu, { speedMenu = false }) {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { option ->
                        DropdownMenuItem(text = { Text("${option}×") }, onClick = {
                            speedMenu = false
                            speed = option
                            player.setPlaybackSpeed(option)
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, onClick: () -> Unit) {
    Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp).clip(RoundedCornerShape(50)).background(Color(0x99000000))
            .clickable(onClick = onClick).padding(horizontal = 9.dp, vertical = 5.dp))
}

/**
 * YouTube's own embedded player, so its controls work as on YouTube: play/pause, seek, volume
 * and mute, quality, speed, captions and full screen.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubePlayer(videoId: String, modifier: Modifier = Modifier) {
    var started by remember(videoId) { mutableStateOf(false) }
    Box(modifier.fillMaxWidth().aspectRatio(16 / 9f).clip(RoundedCornerShape(12.dp)).background(Color.Black)) {
        if (!started) {
            Poster("https://i.ytimg.com/vi/$videoId/hqdefault.jpg", "YouTube video") { started = true }
            Text("YouTube", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFCC0000)).padding(horizontal = 6.dp, vertical = 2.dp))
        } else {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        webChromeClient = WebChromeClient()
                        val html = """
                            <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
                            <style>html,body{margin:0;height:100%;background:#000}iframe{border:0;width:100%;height:100%}</style></head>
                            <body><iframe src="https://www.youtube-nocookie.com/embed/$videoId?autoplay=1&playsinline=1&rel=0"
                            allow="autoplay; encrypted-media; picture-in-picture; fullscreen" allowfullscreen></iframe></body></html>
                        """.trimIndent()
                        loadDataWithBaseURL("https://weaverse.app/", html, "text/html", "utf-8", null)
                    }
                },
                onRelease = { it.destroy() },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
