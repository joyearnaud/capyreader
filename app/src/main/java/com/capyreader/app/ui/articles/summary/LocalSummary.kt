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
    val streamTail: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isTruncated: Boolean = false,
) {
    val isVisible: Boolean get() = isLoading || text != null || error != null
}

class SummaryStateHolder {
    var state by mutableStateOf(SummaryUiState())
}

class SummaryController(
    private val holder: SummaryStateHolder = SummaryStateHolder(),
    val isConfigured: Boolean = false,
    val summarize: (forceRefresh: Boolean) -> Unit = {},
    val dismiss: () -> Unit = {},
) {
    // Stable identity: state lives in the holder, so streaming writes only
    // recompose the scopes that actually read it (the card), not every
    // SummaryController consumer.
    val state: SummaryUiState get() = holder.state
}

@Composable
fun rememberSummary(
    article: Article?,
    summaryClient: SummaryClient = koinInject(),
    appPreferences: AppPreferences = koinInject(),
    account: Account = koinInject(),
): SummaryController {
    val scope = rememberCoroutineScope()
    val holder = remember(article?.id) { SummaryStateHolder() }
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
                holder.state = SummaryUiState(isLoading = true)

                if (!forceRefresh) {
                    val cached = account.findSummary(
                        articleID = target.id,
                        providerKey = providerKey,
                        promptHash = hash,
                    )

                    if (cached != null) {
                        holder.state = SummaryUiState(text = cached.content, isTruncated = truncated)
                        return@launch
                    }
                }

            try {
                var last: String? = null
                var lastRenderNanos = 0L
                summaryClient.summarizeStreaming(request).collect { cumulative ->
                    last = cumulative
                    val now = System.nanoTime()
                    if (now - lastRenderNanos >= RENDER_INTERVAL_NANOS) {
                        lastRenderNanos = now
                        val (stable, tail) = splitStreamText(cumulative)
                        holder.state = SummaryUiState(
                            text = stable,
                            streamTail = tail.ifBlank { null },
                            isTruncated = truncated,
                        )
                    }
                }
                last?.let {
                    holder.state = SummaryUiState(text = it, isTruncated = truncated)
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
                    holder.state = SummaryUiState(error = e.message ?: "Summary failed")
                }
            }
        }
    }

    return SummaryController(
        holder = holder,
        isConfigured = canSummarize,
        summarize = run,
        dismiss = {
            activeJob?.cancel()
            holder.state = SummaryUiState()
        },
    )
}

private const val RENDER_INTERVAL_NANOS = 60L * 1_000_000

/**
 * Complete blocks vs the block still being written: markdown syntax only
 * closes once per block, so rendering the stable prefix through the markdown
 * renderer never reflows already-shown text.
 */
fun splitStreamText(cumulative: String): Pair<String, String> {
    val normalized = cumulative.replace("\r\n", "\n")
    val index = normalized.lastIndexOf("\n\n")

    return if (index == -1) {
        "" to normalized
    } else {
        normalized.substring(0, index) to normalized.substring(index + 2)
    }
}

/** Cheap marker removal for the plain-text tail: inline markers anywhere,
 *  line-start markers at line starts only. Worst case is one stray marker
 *  for a single render tick. */
fun stripStreamTailMarkers(tail: String): String =
    tail.replace(Regex("[*`|]"), "")
        .replace(Regex("(?m)^[#>]+\\s*"), "")

private fun promptHash(prompt: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(prompt.trim().replace("\r\n", "\n").toByteArray())
        .joinToString("") { "%02x".format(it) }
