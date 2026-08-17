package com.ihy2ln.weaverse.core.media

import com.ihy2ln.weaverse.core.text.MediaKind
import javax.inject.Inject
import javax.inject.Singleton

data class MediaClipboardPayload(
    val mediaId: String,
    val kind: MediaKind,
    val widthPercent: Float = 100f,
    val gridColSpan: Int = 1,
    val gridRowSpan: Int = 1,
    val stackedMediaIds: List<String> = emptyList(),
)

/** App-wide cut/copy buffer for media blocks (Write, Roleplay, Notes). */
@Singleton
class MediaClipboard @Inject constructor() {
    @Volatile
    var payload: MediaClipboardPayload? = null
        private set

    val hasPayload: Boolean get() = payload != null

    fun set(item: MediaClipboardPayload) {
        payload = item
    }

    fun clear() {
        payload = null
    }
}
