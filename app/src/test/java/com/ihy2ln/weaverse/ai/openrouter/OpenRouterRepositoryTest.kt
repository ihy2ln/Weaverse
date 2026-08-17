package com.ihy2ln.weaverse.ai.openrouter

import com.ihy2ln.weaverse.ai.AIError
import com.ihy2ln.weaverse.data.settings.SecureKeyStore
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class OpenRouterRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var settings: SettingsRepository
    private lateinit var modelCache: OpenRouterModelCache
    private lateinit var repository: OpenRouterRepository

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        settings = mockk(relaxed = true)
        modelCache = mockk(relaxed = true)
        coEvery { modelCache.getCachedModels() } returns emptyList()
        every { modelCache.toModelInfo(any()) } returns emptyList()
        coEvery { modelCache.save(any()) } returns Unit
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        repository = OpenRouterRepository(settings, modelCache, client)
        repository.baseUrl = server.url("/api/v1").toString().trimEnd('/')
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun validateKeyReturnsDataOn200() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {"data":{"label":"my-key","usage":1.5,"limit":10,"is_free_tier":false}}
                    """.trimIndent(),
                ),
        )
        val result = repository.validateKey("sk-test-valid")
        assertEquals("my-key", result.label)
        assertEquals(1.5, result.usage)
    }

    @Test
    fun validateKeyThrows401ForInvalidKey() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error":{"message":"Invalid API key"}}"""),
        )
        assertThrows<AIError.InvalidKey> {
            repository.validateKey("sk-bad")
        }
        verify(exactly = 0) { settings.setApiKey(any(), any()) }
    }

    @Test
    fun testStoredKeyUsesSettingsKey() = runTest {
        every { settings.apiKey(SecureKeyStore.OPENROUTER) } returns "sk-stored"
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"data":{"label":"stored"}}"""),
        )
        val result = repository.testStoredKey()
        assertEquals("stored", result.label)
        val recorded = server.takeRequest()
        assertEquals("Bearer sk-stored", recorded.getHeader("Authorization"))
    }
}
