package com.ihy2ln.weaverse.core.media.ui

import androidx.compose.runtime.compositionLocalOf
import com.ihy2ln.weaverse.core.media.VideoPlayerPool

/** Provided near the app root (once a real screen exists to provide it — Phase 10/11) so every [InlineVideoPlayer] shares the same singleton pool. */
val LocalVideoPlayerPool = compositionLocalOf<VideoPlayerPool?> { null }
