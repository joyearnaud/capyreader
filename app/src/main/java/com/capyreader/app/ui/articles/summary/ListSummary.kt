package com.capyreader.app.ui.articles.summary

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.capyreader.app.preferences.AppPreferences
import com.jocmp.aiclient.SummaryClient
import com.jocmp.capy.Account
import com.jocmp.capy.ArticleFilter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

class ListSummaryStateHolder {
    var state by mutableStateOf(SummaryUiState())
}

class ListSummaryController(
    private val holder: ListSummaryStateHolder = ListSummaryStateHolder(),
    val isConfigured: Boolean = false,
    val summarize: () -> Unit = {},
    val dismiss: () -> Unit = {},
) {
    val state: SummaryUiState get() = holder.state
}

@Composable
fun rememberListSummary(
    filter: ArticleFilter,
    scopeLabel: String,
    account: Account = koinInject(),
    summaryClient: SummaryClient = koinInject(),
    appPreferences: AppPreferences = koinInject(),
): ListSummaryController {
    val scope = rememberCoroutineScope()
    val holder = remember(filter) { ListSummaryStateHolder() }
    var activeJob by remember { mutableStateOf<Job?>(null) }

    val isConfigured = appPreferences.aiOptions.apiKey.get().isNotBlank()

    val run: () -> Unit = {
        activeJob?.cancel()
        activeJob = scope.launch {
            holder.state = SummaryUiState(isLoading = true)

            try {
                val fetched = withContext(Dispatchers.IO) {
                    account.findRecentForDigest(filter)
                }
                val selection = selectDigestDays(fetched.map { buildDigestEntry(it) })

                if (selection.isEmpty()) {
                    holder.state = SummaryUiState(error = "No articles to summarize")
                    return@launch
                }

                val request = buildListSummaryRequest(scopeLabel, selection)

                var streamed: String? = null
                var failure: Throwable? = null
                var full: String? = null

                val collector = launch {
                    try {
                        summaryClient.summarizeStreaming(request).collect { streamed = it }
                        full = streamed
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failure = e
                    }
                }

                var displayed = 0
                while (collector.isActive || displayed < (streamed?.length ?: 0)) {
                    val current = streamed
                    if (current != null && current.length > displayed) {
                        displayed = advanceDisplayed(displayed, current.length)
                        val (stable, tail) = splitStreamText(current.substring(0, displayed))
                        holder.state = SummaryUiState(
                            text = stable,
                            streamTail = tail.ifBlank { null },
                        )
                    }
                    withFrameNanos { it }
                }

                when {
                    failure != null -> throw failure!!
                    full != null -> holder.state = SummaryUiState(text = full)
                    else -> holder.state = SummaryUiState(error = "Empty response from the provider")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                holder.state = SummaryUiState(error = e.message ?: "Summary failed")
            }
        }
    }

    return ListSummaryController(
        holder = holder,
        isConfigured = isConfigured,
        summarize = run,
        dismiss = {
            activeJob?.cancel()
            holder.state = SummaryUiState()
        },
    )
}
