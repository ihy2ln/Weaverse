package com.ihy2ln.weaverse.core.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "only one player is active at a time" (spec §7) — every inline video block
 * asks this pool for a player; acquiring for a different media id releases
 * whatever was previously playing first. There's exactly one [ExoPlayer]
 * instance alive at once, not a pool of several recycled instances — simpler,
 * and sufficient since only one inline video can be "the active one" per spec.
 */
@Singleton
class VideoPlayerPool @Inject constructor(@ApplicationContext private val context: Context) {
    private var activePlayer: ExoPlayer? = null
    private var activeMediaId: String? = null

    /** Returns the shared player, (re)pointed at [uri] if [mediaId] differs from whatever was last active. */
    fun acquire(mediaId: String, uri: Uri): ExoPlayer {
        val current = activePlayer
        if (current != null && activeMediaId == mediaId) return current

        current?.release()
        val player = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
        }
        activePlayer = player
        activeMediaId = mediaId
        return player
    }

    /** Called when the currently-active block's video scrolls off-screen or is disposed. */
    fun releaseIfActive(mediaId: String) {
        if (activeMediaId == mediaId) {
            activePlayer?.release()
            activePlayer = null
            activeMediaId = null
        }
    }
}
