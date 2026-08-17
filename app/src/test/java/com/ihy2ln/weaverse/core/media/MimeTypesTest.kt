package com.ihy2ln.weaverse.core.media

import com.ihy2ln.weaverse.data.db.entity.MediaType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MimeTypesTest {
    @Test
    fun `classifies image, video, and audio prefixes`() {
        assertEquals(MediaType.Image, MimeTypes.mediaTypeFor("image/jpeg"))
        assertEquals(MediaType.Video, MimeTypes.mediaTypeFor("video/mp4"))
        assertEquals(MediaType.Audio, MimeTypes.mediaTypeFor("audio/mpeg"))
    }

    @Test
    fun `unknown mime types default to Image`() {
        assertEquals(MediaType.Image, MimeTypes.mediaTypeFor("application/octet-stream"))
    }

    @Test
    fun `maps known mime types to their conventional extension`() {
        assertEquals("jpg", MimeTypes.extensionFor("image/jpeg"))
        assertEquals("png", MimeTypes.extensionFor("image/png"))
        assertEquals("mp4", MimeTypes.extensionFor("video/mp4"))
        assertEquals("webm", MimeTypes.extensionFor("video/webm"))
    }

    @Test
    fun `falls back to the mime subtype for unrecognized types`() {
        assertEquals("avif", MimeTypes.extensionFor("image/avif"))
    }
}
