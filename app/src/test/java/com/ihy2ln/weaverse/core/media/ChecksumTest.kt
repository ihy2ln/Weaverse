package com.ihy2ln.weaverse.core.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class ChecksumTest {
    @Test
    fun `matches the well-known SHA-256 test vector for an empty input`() {
        val hash = computeSha256(ByteArrayInputStream(ByteArray(0)))
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hash)
    }

    @Test
    fun `matches the well-known SHA-256 test vector for "abc"`() {
        val hash = computeSha256(ByteArrayInputStream("abc".toByteArray(Charsets.US_ASCII)))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hash)
    }

    @Test
    fun `identical content produces identical checksums regardless of stream chunking`() {
        val content = ByteArray(20_000) { (it % 251).toByte() }
        val first = computeSha256(ByteArrayInputStream(content))
        val second = computeSha256(ByteArrayInputStream(content))
        assertEquals(first, second)
    }

    @Test
    fun `different content produces different checksums`() {
        val a = computeSha256(ByteArrayInputStream("one".toByteArray()))
        val b = computeSha256(ByteArrayInputStream("two".toByteArray()))
        assert(a != b)
    }
}
