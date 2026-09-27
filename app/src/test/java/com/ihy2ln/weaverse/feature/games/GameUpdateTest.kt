package com.ihy2ln.weaverse.feature.games

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class GameUpdateTest {
    @TempDir lateinit var temp: File

    @Test
    fun comparesReleaseVersionsNumerically() {
        assertTrue(GameReleaseChecker.isNewer("0.15.0", "0.11.0"))
        assertFalse(GameReleaseChecker.isNewer("0.15.0", "0.15.0"))
        assertFalse(GameReleaseChecker.isNewer("0.9.0", "0.15.0"))
    }

    @Test
    fun convertsGodotApkAssetsIntoMountablePack() {
        val apk = File(temp, "AdamsHaven-v0.15.0-Android.apk")
        val pack = File(temp, "adams-haven.zip")
        ZipOutputStream(apk.outputStream()).use { zip ->
            fun add(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            add("lib/arm64-v8a/libgodot_android.so", "Godot 4.7.1.stable".toByteArray())
            add("assets/project.binary", projectBinary())
            add("assets/video.webm", byteArrayOf(1, 2, 3))
            add("assets/_cl_", "ignored".toByteArray())
        }

        GameApkImporter.convert(apk, pack, "0.15.0") { _, _ -> }

        ZipFile(pack).use { zip ->
            val manifest = zip.getInputStream(zip.getEntry("weaverse-game.json")).bufferedReader().readText()
            assertTrue(manifest.contains("\"version\":\"0.15.0\""))
            assertTrue(zip.getEntry("project.binary") != null)
            assertTrue(zip.getEntry("assets/project.binary") == null)
            assertTrue(zip.getEntry("_cl_") == null)
            assertEquals(ZipEntry.STORED, zip.getEntry("video.webm").method)
            val override = zip.getInputStream(zip.getEntry(GamePackStore.OVERRIDE_ENTRY)).bufferedReader().readText()
            assertTrue(override.contains("config/name=\"Adams Haven Card Game\""))
        }
    }

    private fun projectBinary(): ByteArray {
        val key = "application/config/name".toByteArray()
        val name = "Adams Haven Card Game".toByteArray()
        val variant = ByteBuffer.allocate(8 + name.size).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(4).putInt(name.size).put(name).array()
        val bytes = ByteArrayOutputStream()
        fun writeInt(value: Int) { bytes.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()) }
        bytes.write("ECFG".toByteArray())
        writeInt(1)
        writeInt(key.size)
        bytes.write(key)
        writeInt(variant.size)
        bytes.write(variant)
        return bytes.toByteArray()
    }
}
