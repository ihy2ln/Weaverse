package com.ihy2ln.weaverse.data.export

import com.ihy2ln.weaverse.core.roleplay.CharacterCardPng
import com.ihy2ln.weaverse.core.roleplay.CharacterCardSpec
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SillyTavernProbeTest {
    @Test
    fun looksLike_pngCard() {
        val json = CharacterCardPng.cardJson(CharacterCardSpec(name = "Mira", firstMes = "Hi"))
        val png = CharacterCardPng.embedCardJson(CharacterCardPng.minimalPng(), json)
        assertTrue(SillyTavernProbe.looksLike(png, "mira.png"))
    }

    @Test
    fun looksLike_chatJsonl() {
        val jsonl = """{"is_user":true,"mes":"Hello","send_date":1}"""
        assertTrue(SillyTavernProbe.looksLikeChatJsonl(jsonl))
        assertTrue(SillyTavernProbe.looksLike(jsonl.toByteArray(), "chat.jsonl"))
    }

    @Test
    fun looksLike_worldInfo() {
        val world = """{"entries":{"0":{"keys":["harbor"],"content":"A port.","constant":false}}}"""
        assertTrue(SillyTavernProbe.looksLikeWorldInfo(world))
        assertTrue(SillyTavernProbe.looksLike(world.toByteArray(), "world.json"))
    }

    @Test
    fun looksLike_ignoresGenericProjectJson() {
        val project = """{"kind":"novel","scenes":[],"entries":[]}"""
        assertFalse(SillyTavernProbe.looksLike(project.toByteArray(), "project.json"))
    }

    @Test
    fun zipHasStLayout_detectsChatsFolder() {
        val bytes = zipOf("chats/Mira/log.jsonl" to """{"is_user":true,"mes":"hi"}""")
        assertTrue(SillyTavernProbe.zipHasStLayout(bytes))
        assertTrue(SillyTavernProbe.looksLike(bytes, "st-data.zip"))
    }

    private fun zipOf(vararg files: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            files.forEach { (name, body) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(body.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
