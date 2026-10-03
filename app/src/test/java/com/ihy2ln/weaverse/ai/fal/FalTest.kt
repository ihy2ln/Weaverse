package com.ihy2ln.weaverse.ai.fal

import com.ihy2ln.weaverse.ai.AIError
import com.ihy2ln.weaverse.core.media.MangaColorFallback
import com.ihy2ln.weaverse.feature.prompt.PromptModelSelection
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Fixtures are real fal.ai answers (catalog page and OpenAPI schemas, 2026-10-03). */
class FalTest {
    private fun fixture(name: String) = javaClass.getResource("/fal/$name")!!.readText()

    @Test
    fun catalogKeepsPictureModelsAndDropsUtilities() {
        val (models, next) = Fal.parseCatalog(fixture("catalog-i2i.json"))
        val ids = models.map { it.id }
        assertTrue("fal/fal-ai/flux-pro/kontext" in ids)
        assertTrue("fal/fal-ai/qwen-image-edit-2511" in ids)
        assertTrue("fal/fal-ai/nano-banana-pro/edit" in ids)
        assertFalse(ids.any { "birefnet" in it || "esrgan" in it || "sam-3" in it })
        assertTrue(models.all { it.supportsImages && it.generatesImages && Fal.TAG in it.tags })
        assertTrue(next != null)
    }

    @Test
    fun kontextTakesOneImageAndTheMostPermissiveSafetyTolerance() {
        val schema = Fal.parseSchema(fixture("schema-fal-ai_flux-pro_kontext.json"))
        assertTrue("image_url" in schema.fields)
        assertFalse(schema.takesImageList)
        assertTrue("ratio:2:3" in Fal.ratioTags(schema))
        val body = Fal.input(schema, "color this page", listOf("data:image/png;base64,AAAA"), "2:3")
        assertEquals("data:image/png;base64,AAAA", body["image_url"]!!.jsonPrimitive.content)
        assertEquals(schema.safetyTolerances.maxBy { it.toInt() }, body["safety_tolerance"]!!.jsonPrimitive.content)
        assertEquals("6", body["safety_tolerance"]!!.jsonPrimitive.content)
        assertEquals("2:3", body["aspect_ratio"]!!.jsonPrimitive.content)
        assertEquals("png", body["output_format"]!!.jsonPrimitive.content)
        assertNull(body["enable_safety_checker"])
    }

    @Test
    fun qwenEditTakesAnImageListWithItsSafetyCheckerOff() {
        val schema = Fal.parseSchema(fixture("schema-fal-ai_qwen-image-edit-2511.json"))
        assertTrue(schema.takesImageList)
        val body = Fal.input(schema, "color", listOf("data:image/png;base64,AAAA"), "2:3")
        assertEquals(1, body["image_urls"]!!.jsonArray.size)
        assertEquals(JsonPrimitive(false), body["enable_safety_checker"])
        // Qwen picks sizes by name, not ratio: an unknown ratio is simply not sent.
        assertNull(body["aspect_ratio"])
    }

    @Test
    fun resultsAndErrors() {
        val (url, type) = Fal.resultImage(
            """{"images":[{"url":"https://v3.fal.media/files/x.png","content_type":"image/png"}],"has_nsfw_concepts":[false]}""",
        )
        assertEquals("https://v3.fal.media/files/x.png", url)
        assertEquals("image/png", type)
        assertThrows<AIError.BadRequest> { Fal.resultImage("""{"detail":[{"loc":["body","image_url"],"msg":"field required"}]}""") }
        assertTrue(Fal.errorFor(401, """{"detail":"invalid key credentials"}""", null) is AIError.InvalidKey)
        assertTrue(Fal.errorFor(403, """{"detail":"User is locked. Reason: Exhausted balance."}""", null) is AIError.OutOfCredits)
        val refusal = Fal.errorFor(422, """{"detail":[{"msg":"content policy violation","type":"content_policy_violation"}]}""", null)
        assertTrue(MangaColorFallback.isRefusal(refusal))
    }

    @Test
    fun falRefsStayWholeAndCountAsOneProvider() {
        assertEquals("fal/fal-ai/flux-pro/kontext", PromptModelSelection.modelRef("fal/fal-ai/flux-pro/kontext"))
        assertEquals("fal-llm/anthropic/claude-sonnet-5.5", PromptModelSelection.modelRef("fal-llm/anthropic/claude-sonnet-5.5"))
        assertEquals("fal", MangaColorFallback.provider("fal/fal-ai/flux-pro/kontext"))
        // A page Gemini declined is retried on fal's FLUX Kontext first.
        val order = MangaColorFallback.fallbacks(
            "openrouter/google/gemini-2.5-flash-image",
            listOf("fal/fal-ai/qwen-image-edit-2511", "fal/fal-ai/flux-pro/kontext", "openrouter/openai/gpt-5-image-mini"),
        )
        assertEquals("fal/fal-ai/flux-pro/kontext", order.first())
    }
}
