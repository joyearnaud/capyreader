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
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * Survives navigation (nav3 disposes the list while the reader is on top),
 * so the sheet restores its text and scroll position after the user returns
 * from an article opened out of a reference.
 */
var digestSession: Pair<ArticleFilter, ListSummaryStateHolder>? = null

/** Set when a digest reference is tapped: the list reopens the sheet on return. */
var digestSheetReopenPending = false

class ListSummaryStateHolder {
    var state by mutableStateOf(SummaryUiState())
    var referenceTargets by mutableStateOf(emptyList<String>())
    var lastScrollPosition: Int = 0
    var shareSources: String = ""
    // Articles included in the current digest — the mark-read button targets
    // exactly these, not the whole scope.
    var articleIds: List<String> = emptyList()
    // Frozen at reference-tap time: the sheet dismissal clamps the scroll to
    // 0 frame by frame, and those teardown saves would erase the tap position.
    var scrollFrozen: Boolean = false
    // True from sheet open until the restore effect lands — the open clamp
    // (partial content, small max) must not overwrite the saved position.
    var scrollRestoring: Boolean = false
}

class ListSummaryController(
    private val holder: ListSummaryStateHolder = ListSummaryStateHolder(),
    val isConfigured: Boolean = false,
    val summarize: (dayWindow: Boolean) -> Unit = {},
    val dismiss: () -> Unit = {},
) {
    val state: SummaryUiState get() = holder.state
    val referenceTargets: List<String> get() = holder.referenceTargets
    val savedScroll: Int get() = holder.lastScrollPosition
    val digestArticleIds: List<String> get() = holder.articleIds
    val shareSources: String get() = holder.shareSources

    fun freezeScroll() {
        holder.scrollFrozen = true
    }

    fun unfreezeScroll() {
        holder.scrollFrozen = false
        holder.scrollRestoring = true
    }

    fun restoreDone() {
        holder.scrollRestoring = false
    }

    fun saveScroll(position: Int) {
        // Frozen at reference-tap time (the dismissal clamps the scroll to 0
        // frame by frame), and 0 is never a real reading position anyway.
        if (holder.scrollFrozen || holder.scrollRestoring || position <= 0) return

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
    val holder = remember(filter) {
        digestSession?.takeIf { it.first == filter }?.second ?: ListSummaryStateHolder()
    }
    // Publish the live holder so the sheet survives the reader being on top.
    digestSession = filter to holder
    var activeJob by remember { mutableStateOf<Job?>(null) }

    val isConfigured = appPreferences.aiOptions.apiKey.get().isNotBlank()

    val run: (dayWindow: Boolean) -> Unit = { dayWindow ->
        activeJob?.cancel()
        activeJob = scope.launch {
            holder.state = SummaryUiState(isLoading = true)

            try {
                val listPrompt = appPreferences.aiOptions.listPrompt.get()
                val entries = withContext(Dispatchers.IO) {
                    val fetched = account.findRecentForDigest(filter)
                    if (dayWindow) {
                        val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
                        fetched.filter { it.publishedAt.toEpochSecond() >= todayStart }
                    } else {
                        fetched
                    }.map { buildDigestEntry(it) }
                }

                if (entries.isEmpty()) {
                    holder.state = SummaryUiState(error = "No articles to summarize")
                    return@launch
                }
                holder.articleIds = entries.map { it.id }

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

                    // Persisted layer: survives process death, valid 3 days.
                    val persisted = withContext(Dispatchers.IO) {
                        account.findDigest(
                            id = digestCacheKey(filter, targets, listPrompt),
                            cutoff = ZonedDateTime.now().minusDays(3),
                        )
                    }
                    if (persisted != null) {
                        holder.articleIds = persisted.articleIds
                        holder.referenceTargets = persisted.articleIds
                        cache.put(
                            filter,
                            targets,
                            listPrompt,
                            ListSummaryCache.Entry(text = persisted.content, articleIds = persisted.articleIds),
                        )
                        holder.state = SummaryUiState(text = persisted.content)
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
                            // created_at is set here, at completion: the 3-day
                            // window measures availability from when the digest
                            // finished. Purge of >3d rows happens inside.
                            runCatching {
                                account.upsertDigest(
                                    id = digestCacheKey(filter, targets, listPrompt),
                                    scopeLabel = scopeLabel,
                                    articleCount = targets.size,
                                    articleIds = targets,
                                    content = completed,
                                )
                            }
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
