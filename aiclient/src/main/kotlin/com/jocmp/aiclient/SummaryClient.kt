package com.jocmp.aiclient

import kotlinx.coroutines.flow.Flow

data class ProviderConfig(
    val baseURL: String,
    val model: String,
    val apiKey: String,
)

data class SummaryRequest(
    val systemPrompt: String,
    val title: String,
    val text: String,
)

interface SummaryClient {
    /** One-shot call; never throws — failures come back inside the Result. */
    suspend fun summarize(request: SummaryRequest): Result<String>

    /** SSE stream of the cumulative text so far; throws SummaryException on failure. */
    fun summarizeStreaming(request: SummaryRequest): Flow<String>
}

class SummaryException(message: String) : Exception(message)
