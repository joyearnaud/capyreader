package com.jocmp.aiclient

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

@Serializable
private data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double,
    @SerialName("max_tokens") val maxTokens: Int,
    val stream: Boolean,
)

@Serializable
private data class ChatMessage(val role: String, val content: String)

@Serializable
private data class ChatResponse(val choices: List<ChatChoice> = emptyList())

@Serializable
private data class ChatChoice(
    val message: ChatResponseMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
private data class ChatResponseMessage(val role: String? = null, val content: String? = null)

@Serializable
private data class ErrorResponse(val error: ErrorBody? = null)

@Serializable
private data class ErrorBody(val message: String? = null, val code: String? = null)

internal fun summaryError(status: Int, body: String): SummaryException {
    val message = runCatching {
        json.decodeFromString<ErrorResponse>(body).error?.message
    }.getOrNull()

    return SummaryException(message ?: "Provider error (HTTP $status)")
}

class OpenAiCompatibleClient(
    private val httpClient: OkHttpClient,
    private val config: () -> ProviderConfig,
    private val maxTokens: Int = 4096,
    private val temperature: Double = 0.2,
) : SummaryClient {

    override suspend fun summarize(request: SummaryRequest): Result<String> {
        val provider = config()

        val body = json.encodeToString(
            ChatRequest(
                model = provider.model,
                messages = listOf(
                    ChatMessage(role = "system", content = request.systemPrompt),
                    ChatMessage(role = "user", content = userMessage(request)),
                ),
                temperature = temperature,
                maxTokens = maxTokens,
                stream = false,
            )
        )

        return withContext(Dispatchers.IO) {
            runCatching {
                // Built inside runCatching: url() rejects a scheme-less or empty baseURL with an
                // IllegalArgumentException, and the contract is to fail the Result, not to throw.
                val httpRequest = Request.Builder()
                    .url(provider.baseURL.trimEnd('/') + "/chat/completions")
                    .header("Authorization", "Bearer ${provider.apiKey}")
                    .post(body.toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                httpClient.newCall(httpRequest).execute().use { response ->
                    val text = response.body.string()
                    if (!response.isSuccessful) throw summaryError(response.code, text)

                    val choice = json.decodeFromString<ChatResponse>(text)
                        .choices.firstOrNull()
                    val content = choice?.message?.content?.takeIf { it.isNotBlank() }
                        ?: throw when (choice?.finishReason) {
                            "length" -> SummaryException("Empty response (token limit reached)")
                            else -> SummaryException("Empty response from the provider")
                        }

                    if (choice.finishReason == "length") content + "\n[…]" else content
                }
            }.onFailure { if (it is CancellationException) throw it }
        }
    }

    private fun userMessage(request: SummaryRequest): String = buildString {
        appendLine("Article title: ${request.title}")
        appendLine()
        append(request.text)
    }
}
