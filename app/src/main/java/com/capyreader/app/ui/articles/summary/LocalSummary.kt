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
import com.jocmp.capy.Account
import com.jocmp.capy.Article
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    val summarize: (forceRefresh: Boolean) -> Unit = {},
    val dismiss: () -> Unit = {},
)

@Composable
fun rememberSummary(
    article: Article?,
    summaryClient: SummaryClient = koinInject(),
    appPreferences: AppPreferences = koinInject(),
    account: Account = koinInject(),
): SummaryController {
    val scope = rememberCoroutineScope()
    var state by remember(article?.id) { mutableStateOf(SummaryUiState()) }
    var activeJob by remember { mutableStateOf<Job?>(null) }

    val isConfigured = appPreferences.aiOptions.apiKey.get().isNotBlank()
    val canSummarize = article != null &&
            isConfigured &&
            article.content.isNotBlank() &&
            article.fullContent != Article.FullContentState.LOADING

    val providerKey = "${appPreferences.aiOptions.baseURL.get().trimEnd('/')}/" +
            appPreferences.aiOptions.model.get()
    val hash = promptHash(appPreferences.aiOptions.prompt.get())

    val run: (forceRefresh: Boolean) -> Unit = { forceRefresh ->
        val target = article
        if (target != null) {
            val request = buildSummaryRequest(
                systemPrompt = appPreferences.aiOptions.prompt.get(),
                title = target.title,
                contentHTML = target.content,
            )
            val truncated = isTruncated(target.content)

            activeJob?.cancel()
            activeJob = scope.launch {
                state = SummaryUiState(isLoading = true)

                if (!forceRefresh) {
                    val cached = account.findSummary(
                        articleID = target.id,
                        providerKey = providerKey,
                        promptHash = hash,
                    )

                    if (cached != null) {
                        state = SummaryUiState(text = cached.content, isTruncated = truncated)
                        return@launch
                    }
                }

                try {
                    var last: String? = null
                    summaryClient.summarizeStreaming(request).collect { cumulative ->
                        last = cumulative
                        state = SummaryUiState(text = cumulative, isTruncated = truncated)
                    }
                    last?.let {
                        account.upsertSummary(
                            articleID = target.id,
                            providerKey = providerKey,
                            promptHash = hash,
                            content = it,
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    state = SummaryUiState(error = e.message ?: "Summary failed")
                }
            }
        }
    }

    return SummaryController(
        state = state,
        isConfigured = canSummarize,
        summarize = run,
        dismiss = {
            activeJob?.cancel()
            state = SummaryUiState()
        },
    )
}

private fun promptHash(prompt: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(prompt.trim().replace("\r\n", "\n").toByteArray())
        .joinToString("") { "%02x".format(it) }
