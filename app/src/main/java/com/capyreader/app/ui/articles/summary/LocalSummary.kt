package com.capyreader.app.ui.articles.summary

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.capyreader.app.preferences.AppPreferences
import com.capyreader.app.summaries.buildSummaryRequest
import com.capyreader.app.summaries.isTruncated
import com.jocmp.aiclient.SummaryClient
import com.jocmp.capy.Article
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

val LocalSummary = compositionLocalOf { SummaryController() }

data class SummaryUiState(
    val text: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isTruncated: Boolean = false,
) {
    val isVisible: Boolean get() = isLoading || text != null || error != null
}

class SummaryController(
    val state: SummaryUiState = SummaryUiState(),
    val isConfigured: Boolean = false,
    val summarize: () -> Unit = {},
    val dismiss: () -> Unit = {},
)

@Composable
fun rememberSummary(
    article: Article?,
    summaryClient: SummaryClient = koinInject(),
    appPreferences: AppPreferences = koinInject(),
): SummaryController {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(SummaryUiState()) }

    val isConfigured = appPreferences.aiOptions.apiKey.get().isNotBlank()
    val canSummarize = article != null &&
            isConfigured &&
            article.content.isNotBlank() &&
            article.fullContent != Article.FullContentState.LOADING

    val run: () -> Unit = {
        val target = article
        if (target != null) {
            val request = buildSummaryRequest(
                systemPrompt = appPreferences.aiOptions.prompt.get(),
                title = target.title,
                contentHTML = target.content,
            )
            val truncated = isTruncated(target.content)

            scope.launch {
                state = SummaryUiState(isLoading = true)
                val result = summaryClient.summarize(request)
                state = result.fold(
                    onSuccess = { SummaryUiState(text = it, isTruncated = truncated) },
                    onFailure = { SummaryUiState(error = it.message ?: "Summary failed") },
                )
            }
        }
    }

    return SummaryController(
        state = state,
        isConfigured = canSummarize,
        summarize = run,
        dismiss = { state = SummaryUiState() },
    )
}
