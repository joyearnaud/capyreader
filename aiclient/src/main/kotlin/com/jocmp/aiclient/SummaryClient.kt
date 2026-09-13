package com.jocmp.aiclient

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
    suspend fun summarize(request: SummaryRequest): Result<String>
}

class SummaryException(message: String) : Exception(message)
