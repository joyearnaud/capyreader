package com.jocmp.aiclient

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenAiCompatibleClientTest {
    private lateinit var server: MockWebServer

    private val config = ProviderConfig(
        baseURL = "http://example.com/v1",
        model = "deepseek-chat",
        apiKey = "secret-key",
    )

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun client(config: () -> ProviderConfig = { this.config }): OpenAiCompatibleClient {
        val httpClient = OkHttpClient.Builder()
            .callTimeout(Duration.ofSeconds(5))
            .build()
        return OpenAiCompatibleClient(httpClient = httpClient, config = config)
    }

    private fun baseURL() = server.url("/v1").toString().removeSuffix("/")

    private fun request() = SummaryRequest(
        systemPrompt = "Summarize.",
        title = "A title",
        text = "Some article body",
    )

    @Test
    fun `posts an OpenAI-compatible chat completion request`() = runTest {
        server.enqueue(
            MockResponse(body = """{"choices":[{"message":{"role":"assistant","content":"Résumé"}}]}""")
        )

        val result = client({ config.copy(baseURL = baseURL()) }).summarize(request())

        assertEquals("Résumé", result.getOrNull())

        val recorded = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("POST", recorded.method)
        assertEquals("/v1/chat/completions", recorded.url.encodedPath)
        assertEquals("Bearer secret-key", recorded.headers["Authorization"])
        assertTrue(recorded.headers["Content-Type"].orEmpty().startsWith("application/json"))

        val body = recorded.body?.utf8().orEmpty()
        assertTrue(body.contains("\"model\":\"deepseek-chat\""), body)
        assertTrue(body.contains("\"stream\":false"), body)
        assertTrue(body.contains("\"max_tokens\":4096"), body)
        assertTrue(body.contains("Some article body"), body)
        assertTrue(body.contains("A title"), body)
    }

    @Test
    fun `ignores reasoning_content and reads message content`() = runTest {
        server.enqueue(
            MockResponse(
                body = """{"choices":[{"message":{"role":"assistant","content":"Short answer","reasoning_content":"long private thinking"}}]}"""
            )
        )

        val result = client({ config.copy(baseURL = baseURL()) }).summarize(request())

        assertEquals("Short answer", result.getOrNull())
    }

    @Test
fun `fails when content is null`() = runTest {
        server.enqueue(
            MockResponse(body = """{"choices":[{"finish_reason":"length","message":{"content":null}}]}""")
        )

        val result = client({ config.copy(baseURL = baseURL()) }).summarize(request())

        assertTrue(result.isFailure, result.toString())
        assertTrue(result.exceptionOrNull() is SummaryException)
    }

    @Test
    fun `marks a response cut by the token limit`() = runTest {
        server.enqueue(
            MockResponse(
                body = """{"choices":[{"finish_reason":"length","message":{"content":"Résumé coupé au mil"}}]}"""
            )
        )

        val result = client({ config.copy(baseURL = baseURL()) }).summarize(request())

        assertEquals("Résumé coupé au mil\n[…]", result.getOrNull())
    }

    @Test
    fun `fails when content is blank`() = runTest {
        server.enqueue(
            MockResponse(body = """{"choices":[{"message":{"content":"   "}}]}""")
        )

        val result = client({ config.copy(baseURL = baseURL()) }).summarize(request())

        assertTrue(result.isFailure, result.toString())
        assertTrue(result.exceptionOrNull() is SummaryException)
    }

    @Test
    fun `surfaces the provider error message on 401`() = runTest {
        server.enqueue(
            MockResponse(
                code = 401,
                body = """{"error":{"message":"Authentication Fails","type":"authentication_error"}}""",
            )
        )

        val result = client({ config.copy(baseURL = baseURL()) }).summarize(request())

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("Authentication Fails"))
    }

    @Test
    fun `maps 429 and 5xx to summary exceptions`() = runTest {
        server.enqueue(MockResponse(code = 429, body = """{"error":{"message":"Rate limit"}}"""))
        server.enqueue(MockResponse(code = 500, body = "boom"))

        val client = client({ config.copy(baseURL = baseURL()) })
        val rateLimited = client.summarize(request())
        val serverError = client.summarize(request())

        assertTrue(rateLimited.exceptionOrNull()!!.message!!.contains("Rate limit"))
        assertTrue(serverError.exceptionOrNull() is SummaryException)
    }

    @Test
    fun `fails instead of throwing when the endpoint is unreachable`() = runTest {
        val result = client({
            config.copy(baseURL = "http://127.0.0.1:1/v1")
        }).summarize(request())

        assertTrue(result.isFailure)
    }

    @Test
    fun `fails instead of throwing when the base url has no scheme`() = runTest {
        val result = client({
            config.copy(baseURL = "api.deepseek.com/v1")
        }).summarize(request())

        assertTrue(result.isFailure, result.toString())
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `summaryError prefers the provider message`() {
        val error = summaryError(400, """{"error":{"message":"Invalid model","code":"1210"}}""")
        assertEquals("Invalid model", error.message)
    }

    @Test
    fun `summaryError falls back to the status code`() {
        val error = summaryError(503, "not json")
        assertTrue(error.message!!.contains("503"), error.message)
    }

    @Test
    fun `streams cumulative content deltas`() = runTest {
        server.enqueue(
            MockResponse(
                body = """
                    data: {"choices":[{"delta":{"role":"assistant"}}]}

                    : keep-alive

                    data:{"choices":[{"delta":{"content":"Bon"}}]}

                    data: {"choices":[{"delta":{"content":"jour"}}]}

                    data: {"choices":[{"delta":{},"finish_reason":"stop"}]}

                    data: [DONE]

                """.trimIndent()
            )
        )

        val emissions = client({ config.copy(baseURL = baseURL()) })
            .summarizeStreaming(request())
            .toList()

        assertEquals(listOf("Bon", "Bonjour"), emissions)

        val recorded = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertTrue(recorded.body?.utf8().orEmpty().contains("\"stream\":true"))
    }

    @Test
    fun `ignores reasoning deltas in a stream`() = runTest {
        server.enqueue(
            MockResponse(
                body = """
                    data: {"choices":[{"delta":{"reasoning_content":"thinking..."}}]}

                    data: {"choices":[{"delta":{"content":"Réponse"}}]}

                    data: [DONE]

                """.trimIndent()
            )
        )

        val emissions = client({ config.copy(baseURL = baseURL()) })
            .summarizeStreaming(request())
            .toList()

        assertEquals(listOf("Réponse"), emissions)
    }

    @Test
    fun `marks a stream cut by the token limit`() = runTest {
        server.enqueue(
            MockResponse(
                body = """
                    data: {"choices":[{"delta":{"content":"Coupé au mil"}}]}

                    data: {"choices":[{"delta":{},"finish_reason":"length"}]}

                    data: [DONE]

                """.trimIndent()
            )
        )

        val emissions = client({ config.copy(baseURL = baseURL()) })
            .summarizeStreaming(request())
            .toList()

        assertEquals(listOf("Coupé au mil", "Coupé au mil\n[…]"), emissions)
    }

    @Test
    fun `fails on http error in a stream`() = runTest {
        server.enqueue(
            MockResponse(code = 401, body = """{"error":{"message":"Authentication Fails"}}""")
        )

        val outcome = runCatching {
            client({ config.copy(baseURL = baseURL()) }).summarizeStreaming(request()).toList()
        }

        assertTrue(outcome.isFailure)
        assertTrue(outcome.exceptionOrNull() is SummaryException)
    }

    @Test
    fun `fails when a stream ends without content`() = runTest {
        server.enqueue(MockResponse(body = "data: [DONE]\n\n"))

        val outcome = runCatching {
            client({ config.copy(baseURL = baseURL()) }).summarizeStreaming(request()).toList()
        }

        assertTrue(outcome.isFailure)
        assertTrue(outcome.exceptionOrNull() is SummaryException)
    }

}
