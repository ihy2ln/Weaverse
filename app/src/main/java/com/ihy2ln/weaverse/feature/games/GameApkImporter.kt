package com.ihy2ln.weaverse.feature.games

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Converts the assets in an official Godot Android export into Weaverse's mountable pack. */
internal object GameApkImporter {
    fun convert(apkFile: File, output: File, version: String, onProgress: (Long, Long) -> Unit) {
        ZipFile(apkFile).use { apk ->
            val project = apk.getEntry("assets/project.binary")
                ?: error("This APK does not contain an Adams Haven Godot project.")
            val engine = apk.getEntry("lib/arm64-v8a/libgodot_android.so")
                ?: error("This APK has no compatible Godot runtime.")
            require(containsEngine(apk, engine)) {
                "This APK uses a different Godot version. Weaverse needs ${GodotRuntime.ENGINE_VERSION}."
            }
            val assets = apk.entries().asSequence().filter {
                !it.isDirectory && it.name.startsWith("assets/") && it.name != "assets/_cl_"
            }.toList()
            val total = assets.sumOf { it.size.coerceAtLeast(0) }
            val manifest = GamePackManifest(
                version = version,
                engine = GodotRuntime.ENGINE_VERSION,
                files = assets.size,
                byteSize = total,
                source = apkFile.name,
            )
            val override = projectOverride(apk.getInputStream(project).use { it.readBytes() })
            require(override.contains("config/name=\"Adams Haven")) {
                "This APK is not an Adams Haven game export."
            }
            var copied = 0L
            ZipOutputStream(output.outputStream().buffered()).use { pack ->
                pack.putNextEntry(ZipEntry("weaverse-game.json"))
                pack.write(kotlinx.serialization.json.Json.encodeToString(GamePackManifest.serializer(), manifest).toByteArray())
                pack.closeEntry()
                pack.putNextEntry(ZipEntry(GamePackStore.OVERRIDE_ENTRY))
                pack.write(override.toByteArray())
                pack.closeEntry()
                for (asset in assets) {
                    val entry = ZipEntry(asset.name.removePrefix("assets/"))
                    // Godot seeks into videos in the pack, so game assets must be uncompressed.
                    entry.method = ZipEntry.STORED
                    entry.size = asset.size
                    entry.compressedSize = asset.size
                    entry.crc = asset.crc
                    entry.time = asset.time
                    pack.putNextEntry(entry)
                    apk.getInputStream(asset).use { input ->
                        val buffer = ByteArray(1 shl 20)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            pack.write(buffer, 0, count)
                            copied += count
                            if (copied % (8L shl 20) < count) onProgress(copied, total)
                        }
                    }
                    pack.closeEntry()
                }
            }
            onProgress(copied, total)
        }
    }

    private fun containsEngine(apk: ZipFile, entry: ZipEntry): Boolean {
        val needle = GodotRuntime.ENGINE_VERSION.toByteArray()
        apk.getInputStream(entry).buffered().use { input ->
            var matched = 0
            while (true) {
                val byte = input.read()
                if (byte < 0) return false
                matched = if (byte == needle[matched].toInt()) matched + 1 else if (byte == needle[0].toInt()) 1 else 0
                if (matched == needle.size) return true
            }
        }
    }

    internal fun projectOverride(data: ByteArray): String {
        val binary = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        require(binary.int == 0x47464345) { "The APK has an invalid Godot project." } // ECFG
        val sections = linkedMapOf<String, MutableList<String>>()
        repeat(binary.int) {
            val key = ByteArray(binary.int).also { binary.get(it) }.toString(Charsets.UTF_8)
            val value = ByteArray(binary.int).also { binary.get(it) }
            val rendered = renderVariant(value) ?: return@repeat
            val section = key.substringBefore('/')
            val name = key.substringAfter('/')
            sections.getOrPut(section) { mutableListOf() }.add("$name=$rendered")
        }
        return buildString {
            appendLine("; Adams Haven project settings, applied by Weaverse over its boot project.")
            appendLine("config_version=5")
            appendLine()
            sections.forEach { (section, values) ->
                appendLine("[$section]")
                appendLine()
                values.forEach { appendLine(it) }
                appendLine()
            }
        }
    }

    private fun renderVariant(data: ByteArray): String? {
        val value = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val header = value.int
        val wide = header and (1 shl 16) != 0
        return when (header and 0xff) {
            1 -> if (value.int != 0) "true" else "false"
            2 -> if (wide) value.long.toString() else value.int.toString()
            3 -> if (wide) value.double.toString() else value.float.toString()
            4 -> quote(readString(value))
            34 -> {
                val count = value.int
                val items = (0 until count).map {
                    val length = value.int
                    val raw = ByteArray(length).also { value.get(it) }
                    value.position(value.position() + (-length and 3))
                    quote(raw.toString(Charsets.UTF_8).trimEnd('\u0000'))
                }
                "PackedStringArray(${items.joinToString(", ")})"
            }
            else -> null
        }
    }

    private fun readString(value: ByteBuffer): String =
        ByteArray(value.int).also { value.get(it) }.toString(Charsets.UTF_8)

    private fun quote(text: String): String = "\"${text.replace("\\", "\\\\").replace("\"", "\\\"")}\""
}
