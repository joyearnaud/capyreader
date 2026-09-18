package com.capyreader.app.ui.articles.summary

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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

                if (appPreferences.aiOptions.articleSummaryCacheEnabled.get() && !forceRefresh) {
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
                var streamed: String? = null
                var failure: Throwable? = null
                var full: String? = null

                // The collector owns network errors so a child failure cannot
                // surface as a silent cancellation of the animation loop below;
                // the cache write is decoupled from the display pace (best-effort:
                // a DB hiccup must not replace good text with an error).
                val collector = launch {
                    try {
                        summaryClient.summarizeStreaming(request).collect { streamed = it }
                        full = streamed
                        streamed?.let {
                            if (appPreferences.aiOptions.articleSummaryCacheEnabled.get()) {
                                runCatching {
                                    account.upsertSummary(
                                        articleID = target.id,
                                        providerKey = providerKey,
                                        promptHash = hash,
                                        content = it,
                                    )
                                }
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failure = e
                    }
                }

                // Typewriter: display advances frame-locked toward the streamed
                // text, independent of network chunk sizes.
                var displayed = 0
                while (collector.isActive || displayed < (streamed?.length ?: 0)) {
                    val current = streamed
                    if (current != null && current.length > displayed) {
                        displayed = advanceDisplayed(displayed, current.length)
                        val (stable, tail) = splitStreamText(current.substring(0, displayed))
                        holder.state = SummaryUiState(
                            text = stable,
                            streamTail = tail.ifBlank { null },
                            isTruncated = truncated,
                        )
                    }
                    withFrameNanos { it }
                }

                when {
                    failure != null -> throw failure!!
                    full != null -> holder.state = SummaryUiState(text = full, isTruncated = truncated)
                    else -> holder.state = SummaryUiState(error = "Empty response from the provider")
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

private const val CATCHUP_FRAMES = 5
private const val MAX_CHARS_PER_FRAME = 6

/** Frame step for the typewriter: a third of the backlog (self-balancing),
 *  capped so a buffered burst stays visible, never below one char. */
internal fun advanceDisplayed(displayed: Int, targetLength: Int): Int {
    if (targetLength <= displayed) return displayed

    val remaining = targetLength - displayed
    val step = maxOf(1, minOf(remaining / CATCHUP_FRAMES, MAX_CHARS_PER_FRAME))

    return displayed + minOf(step, remaining)
}

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
