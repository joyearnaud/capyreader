package com.capyreader.app.ui.summaries

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import com.capyreader.app.R
import com.capyreader.app.ui.articles.summary.SummaryContent
import com.capyreader.app.ui.articles.summary.SummaryUiState
import com.jocmp.capy.Account
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The last 3 days of summaries: list digests (persisted) and article
 * summaries (already stored), newest first. Tapping an entry opens it for
 * reading; digest references open the referenced article.
 */
sealed class SummaryHistoryItem {
    abstract val id: String
    abstract val title: String
    abstract val createdAt: ZonedDateTime
    abstract val content: String
    abstract val referenceTargets: List<String>

    data class Digest(
        override val id: String,
        override val title: String,
        val articleCount: Int,
        override val createdAt: ZonedDateTime,
        override val content: String,
        override val referenceTargets: List<String>,
    ) : SummaryHistoryItem()

    data class Article(
        val articleID: String,
        override val title: String,
        override val createdAt: ZonedDateTime,
        override val content: String,
    ) : SummaryHistoryItem() {
        override val id: String = articleID
        override val referenceTargets: List<String> = emptyList()
    }
}

/**
 * Digest the user was last reading in the Summaries screen. Process-wide so
 * the detail reopens directly when an article opened from it is closed.
 */
private var lastSelectedSummaryId: String? = null

private val DIGEST_LIST_TTL: java.time.Duration = java.time.Duration.ofDays(3)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummariesScreen(
    onNavigateBack: () -> Unit,
    onOpenArticle: (String) -> Unit,
    account: Account = koinInject(),
) {
    var items by remember { mutableStateOf<List<SummaryHistoryItem>>(emptyList()) }
    var selected by remember { mutableStateOf<SummaryHistoryItem?>(null) }

    LaunchedEffect(Unit) {
        items = withContext(Dispatchers.IO) {
            val cutoff = ZonedDateTime.now().minus(DIGEST_LIST_TTL)
            val digests = account.recentDigests(cutoff).map {
                SummaryHistoryItem.Digest(
                    id = it.id,
                    title = it.scopeLabel,
                    articleCount = it.articleCount.toInt(),
                    createdAt = it.createdAt,
                    content = it.content,
                    referenceTargets = it.articleIds,
                )
            }
            val seen = mutableSetOf<String>()
            val summaries = account.recentSummaries(cutoff)
                // newest first; keep one row per article
                .filter { seen.add(it.articleID) }
                .map {
                    SummaryHistoryItem.Article(
                        articleID = it.articleID,
                        title = it.articleTitle,
                        createdAt = it.createdAt,
                        content = it.content,
                    )
                }
            (digests + summaries).sortedByDescending { it.createdAt }
        }
        // Returning from an article opened out of a digest: reopen it.
        if (selected == null && lastSelectedSummaryId != null) {
            selected = items.find { it.id == lastSelectedSummaryId }
        }
    }

    BackHandler(enabled = selected != null) {
        selected = null
        lastSelectedSummaryId = null
    }

    val title = stringResource(R.string.summaries_nav_title)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selected != null) {
                            selected = null
                            lastSelectedSummaryId = null
                        } else onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        val current = selected
        if (current != null) {
            SummaryDetail(
                item = current,
                onOpenArticle = onOpenArticle,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            )
        } else if (items.isEmpty()) {
            Text(
                stringResource(R.string.summaries_empty),
                modifier = Modifier
                    .padding(padding)
                    .padding(24.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            ) {
                items(items, key = { it.id }) { item ->
                    val formatter = remember {
                        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                            .withZone(ZoneId.systemDefault())
                    }
                    ListItem(
                        headlineContent = {
                            Text(
                                when (item) {
                                    is SummaryHistoryItem.Digest ->
                                        stringResource(R.string.summaries_digest_row, item.title)
                                    is SummaryHistoryItem.Article -> item.title
                                }
                            )
                        },
                        supportingContent = {
                            Text(
                                when (item) {
                                    is SummaryHistoryItem.Digest ->
                                        stringResource(R.string.summaries_digest_subtitle, item.articleCount)
                                    is SummaryHistoryItem.Article ->
                                        stringResource(R.string.summaries_article_subtitle)
                                } + " · " + formatter.format(item.createdAt)
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = when (item) {
                                    is SummaryHistoryItem.Digest -> Icons.AutoMirrored.Rounded.Notes
                                    is SummaryHistoryItem.Article -> Icons.Rounded.Description
                                },
                                contentDescription = null,
                            )
                        },
                        modifier = Modifier.clickable {
                            lastSelectedSummaryId = item.id
                            selected = item
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryDetail(
    item: SummaryHistoryItem,
    onOpenArticle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val defaultUriHandler = LocalUriHandler.current
    val uriHandler = remember(defaultUriHandler) {
        object : UriHandler {
            override fun openUri(uri: String) {
                if (uri.startsWith("capysummary://article/")) {
                    onOpenArticle(uri.substringAfterLast("/"))
                } else {
                    defaultUriHandler.openUri(uri)
                }
            }
        }
    }

    CompositionLocalProvider(LocalUriHandler provides uriHandler) {
        Column(
            modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            SummaryContent(
                state = SummaryUiState(text = item.content),
                referenceTargets = item.referenceTargets,
            )
        }
    }
}
