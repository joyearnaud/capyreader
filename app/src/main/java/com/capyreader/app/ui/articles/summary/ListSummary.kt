package com.capyreader.app.ui.articles.summary

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
    var referenceTargets by mutableStateOf(emptyList<String>())
    var lastScrollPosition: Int = 0
    var shareSources: String = ""
}

class ListSummaryController(
    private val holder: ListSummaryStateHolder = ListSummaryStateHolder(),
    val isConfigured: Boolean = false,
    val summarize: () -> Unit = {},
    val dismiss: () -> Unit = {},
) {
    val state: SummaryUiState get() = holder.state
    val referenceTargets: List<String> get() = holder.referenceTargets
    val savedScroll: Int get() = holder.lastScrollPosition
    val shareSources: String get() = holder.shareSources

    fun saveScroll(position: Int) {
        holder.lastScrollPosition = position
    }
}

@Composable
fun rememberListSummary(
    filter: ArticleFilter,
    scopeLabel: String,
    account: Account = koinInject(),
    summaryClient: SummaryClient = koinInject(),
    appPreferences: AppPreferences = koinInject(),
    cache: ListSummaryCache = koinInject(),
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
                val listPrompt = appPreferences.aiOptions.listPrompt.get()
                val entries = withContext(Dispatchers.IO) {
                    selectDigestArticles(account.findRecentForDigest(filter))
                        .map { buildDigestEntry(it) }
                }

                if (entries.isEmpty()) {
                    holder.state = SummaryUiState(error = "No articles to summarize")
                    return@launch
                }

                val targets = entries.map { it.id }
                holder.referenceTargets = targets
                holder.shareSources = entries.mapIndexed { index, entry ->
                    "(${index + 1}) ${entry.title}" + (entry.url?.let { " — $it" } ?: "")
                }.joinToString("\n")

                val cacheEnabled = appPreferences.aiOptions.listDigestCacheEnabled.get()
                if (cacheEnabled) {
                    cache.get(filter, targets, listPrompt)?.let { entry ->
                        holder.state = SummaryUiState(text = entry.text)
                        return@launch
                    }
                }

                val request = buildListSummaryRequest(scopeLabel, entries, listPrompt)

                var streamed: String? = null
                var failure: Throwable? = null
                var full: String? = null

                // No live text in the sheet: per-frame re-layout in a
                // ModalBottomSheet never stabilizes (5 rounds of artifacts).
                // The collector accumulates silently; the digest lands whole.
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

                collector.join()

                val completed = full
                when {
                    failure != null -> throw failure!!
                    completed != null -> {
                        if (appPreferences.aiOptions.listDigestCacheEnabled.get()) {
                            cache.put(
                                filter,
                                targets,
                                listPrompt,
                                ListSummaryCache.Entry(text = completed, articleIds = targets),
                            )
                        }
                        holder.state = SummaryUiState(text = completed)
                    }
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
