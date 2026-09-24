package com.ihy2ln.weaverse.ai.openrouter

import com.ihy2ln.weaverse.ai.AIError
import com.ihy2ln.weaverse.ai.ImageAttachment
import com.ihy2ln.weaverse.ai.MangaEditorModels
import com.ihy2ln.weaverse.ai.ModelInfo
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Base64

class OpenRouterImageApiTest {
    @Test fun excludesGenerationOnlyUnknownAndBatchModels() {
        fun row(id: String, input: String = "image", refs: Int = 1) = """
            {"id":"$id","architecture":{"input_modalities":["text","$input"],"output_modalities":["image"]},
            "supported_parameters":{"input_references":{"max":$refs}}}
        """
        val models = OpenRouterImageApi.editingModels("""{"data":[${row("flux-edit")},${row("no-ref", refs=0)},
            ${row("text-only", input="text")},${row("edit:batch")},{"id":"unknown"}]}""")
        assertEquals(listOf("flux-edit"), models.map { it.id })
    }
    @Test fun usesReferenceImagesNotChatModalities() {
        val body = Json.parseToJsonElement(OpenRouterImageApi.request("seedream", "preserve art",
            listOf(ImageAttachment("image/png", "abc")), "auto")).jsonObject
        assertFalse(body.containsKey("messages"))
        assertFalse(body.containsKey("modalities"))
        assertEquals("auto", body["aspect_ratio"]!!.jsonPrimitive.content)
        assertEquals("data:image/png;base64,abc", body["input_references"]!!.jsonArray[0].jsonObject
            ["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content)
    }
    @Test fun decodesRasterAndRejectsMissingOrVectorOutput() {
        val png = byteArrayOf(137.toByte(),80,78,71,13,10,26,10)
        val encoded = Base64.getEncoder().encodeToString(png)
        val result = OpenRouterImageApi.decode("""{"data":[{"b64_json":"$encoded"}]}""")
        assertArrayEquals(png, result.first)
        assertEquals("image/png", result.second)
        assertThrows<AIError.EmbeddedError> { OpenRouterImageApi.decode("""{"data":[]}""") }
        assertThrows<AIError.EmbeddedError> { OpenRouterImageApi.decode("""{"data":[{"b64_json":"PHN2Zz4="}]}""") }
    }
    @Test fun ocrExcludesImageGeneratorsAndTranslationRequiresText() {
        val text = ModelInfo("text", "Text", tags=listOf("Text output"))
        val vision = text.copy(id="vision", supportsImages=true)
        val rows = listOf(text, vision, vision.copy(id="image", generatesImages=true),
            vision.copy(id="vision:batch"), text.copy(id="unknown", tags=emptyList()), text.copy(id="audio", isTts=true))
        assertEquals(setOf("text", "vision"), MangaEditorModels.text(rows).map { it.id }.toSet())
        assertEquals(listOf("vision"), MangaEditorModels.vision(rows).map { it.id })
    }
}
