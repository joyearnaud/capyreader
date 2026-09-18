package com.capyreader.app.ui.articles.summary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DoneAll
import com.capyreader.app.R

/**
 * The digest sheet lives in its own window composition, so the
 * LocalUriHandler override is provided HERE (inside the sheet content) —
 * an outer provider on the list screen does not reach this window.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListSummarySheet(
    controller: ListSummaryController,
    onDismiss: () -> Unit,
    onOpenArticle: (String) -> Unit,
    onMarkAllRead: () -> Unit,
) {
    val state = controller.state
    if (!state.isVisible) return

    ModalBottomSheet(onDismissRequest = onDismiss) {
        // Top-anchored: verticalScroll is px-anchored, so the growing text
        // simply extends below the fold — no follow, no jump.
        val scrollState = rememberScrollState()

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
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 560.dp)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.list_summary_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    AnimatedVisibility(
                        visible = state.isLoading,
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.width(8.dp))
                            CircularProgressIndicator(Modifier.size(16.dp))
                        }
                    }
                }

                AnimatedVisibility(
                    visible = state.text != null || state.streamText != null || state.error != null,
                    enter = fadeIn(tween(220)),
                ) {
                    SummaryContent(
                        state = state,
                        modifier = Modifier.padding(top = 12.dp),
                        referenceTargets = controller.referenceTargets,
                    )
                }

                Spacer(Modifier.height(20.dp))

                val generationComplete = state.text != null &&
                        state.streamText == null &&
                        !state.isLoading &&
                        state.error == null
                AnimatedVisibility(visible = generationComplete) {
                    Button(
                        onClick = {
                            onMarkAllRead()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DoneAll,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_mark_all_read))
                    }
                }

                Spacer(Modifier.padding(bottom = 32.dp))
            }
        }
    }
}
