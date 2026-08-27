package com.ihy2ln.weaverse.core.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class MediaPathsTest {
    @Test
    fun storedMediaPathOrNull_rejectsBlank() {
        assertNull(MediaPaths.storedMediaPathOrNull(null))
        assertNull(MediaPaths.storedMediaPathOrNull("   "))
        assertEquals("/tmp/a.png", MediaPaths.storedMediaPathOrNull(" /tmp/a.png "))
    }

    @Test
    fun isRemoteOrContentUri_detectsSchemes() {
        assertTrue(MediaPaths.isRemoteOrContentUri("content://media/1"))
        assertTrue(MediaPaths.isRemoteOrContentUri("https://example.com/a.png"))
        assertTrue(MediaPaths.isRemoteOrContentUri("file:///tmp/a.png"))
        assertFalse(MediaPaths.isRemoteOrContentUri("/data/user/0/app/files/media/a.png"))
    }

    @Test
    fun localFileIfReadable_skipsEmpty(@TempDir dir: File) {
        val missing = File(dir, "gone.png")
        assertNull(MediaPaths.localFileIfReadable(missing.absolutePath))
        val empty = File(dir, "empty.png").also { it.writeBytes(ByteArray(0)) }
        assertNull(MediaPaths.localFileIfReadable(empty.absolutePath))
        val ok = File(dir, "ok.png").also { it.writeBytes(byteArrayOf(1, 2, 3)) }
        assertEquals(ok.absolutePath, MediaPaths.localFileIfReadable(ok.absolutePath)?.absolutePath)
        assertEquals(ok, MediaPaths.mediaLoadTarget(ok.absolutePath))
    }
}
