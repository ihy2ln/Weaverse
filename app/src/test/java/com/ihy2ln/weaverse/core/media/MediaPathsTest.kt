package com.ihy2ln.weaverse.core.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class MediaPathsTest {
    @TempDir
    lateinit var dir: File

    @Test
    fun storedMediaPathOrNull_rejectsBlank() {
        assertNull(storedMediaPathOrNull(null))
        assertNull(storedMediaPathOrNull(""))
        assertNull(storedMediaPathOrNull("   "))
        assertEquals("/tmp/a.jpg", storedMediaPathOrNull(" /tmp/a.jpg "))
    }

    @Test
    fun isRemoteOrContentUri_detectsSchemes() {
        assertTrue(isRemoteOrContentUri("content://media/1"))
        assertTrue(isRemoteOrContentUri("file:///data/a.jpg"))
        assertTrue(isRemoteOrContentUri("https://example.com/a.jpg"))
        assertTrue(!isRemoteOrContentUri("/data/user/0/app/files/media/a.jpg"))
    }

    @Test
    fun mediaLoadTarget_usesReadableFileAndSkipsEmpty() {
        val ok = File(dir, "ok.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val empty = File(dir, "empty.jpg").apply { writeBytes(ByteArray(0)) }
        assertEquals(ok, mediaLoadTarget(ok.absolutePath))
        assertNull(mediaLoadTarget(empty.absolutePath))
        assertNull(mediaLoadTarget("/no/such/file.jpg"))
        assertEquals("content://media/1", mediaLoadTarget("content://media/1"))
    }
}
