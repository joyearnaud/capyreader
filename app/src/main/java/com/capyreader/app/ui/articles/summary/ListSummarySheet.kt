package com.capyreader.app.ui.articles.summary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import android.content.Intent
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Share
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
        // The position survives close/reopen (link taps): saved per scroll
        // frame on the holder (plain var), applied at creation.
        val scrollState = rememberScrollState(initial = controller.savedScroll)
        LaunchedEffect(scrollState) {
            snapshotFlow { scrollState.value }.collect { controller.saveScroll(it) }
        }

        val defaultUriHandler = LocalUriHandler.current
        val context = LocalContext.current
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
                    // Fixed from the first frame: a growing heightIn made the
                    // sheet itself resize on every typewriter tick. A min-only
                    // heightIn also let the sheet measure the scroll column
                    // with infinite max height — a hard crash. Fixed size is
                    // clamped to the screen in landscape by the incoming
                    // constraints.
                    .height(560.dp)
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
                        modifier = Modifier.weight(1f),
                    ) {
                        val pulse by rememberInfiniteTransition(label = "skeleton").animateFloat(
                            initialValue = 0.4f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                tween(900, easing = LinearEasing),
                                RepeatMode.Reverse,
                            ),
                            label = "pulse",
                        )

                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(Modifier.size(28.dp))
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.list_summary_generating),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(32.dp))
                            repeat(5) { line ->
                                Box(
                                    Modifier
                                        .fillMaxWidth(1f - line * 0.08f)
                                        .height(14.dp)
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(
                                            MaterialTheme.colorScheme.onSurface.copy(
                                                alpha = 0.10f * pulse,
                                            )
                                        )
                                )
                                Spacer(Modifier.height(12.dp))
                            }
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
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton(
                            onClick = {
                                val text = listOfNotNull(
                                    state.text?.let { stripStreamMarkers(it) },
                                    controller.shareSources.takeIf { it.isNotBlank() },
                                ).joinToString("\n\n")

                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
                                context.startActivity(Intent.createChooser(intent, null))
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.list_summary_share))
                        }
                        Button(
                            onClick = {
                                onMarkAllRead()
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
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
                }

                Spacer(Modifier.padding(bottom = 32.dp))
            }
        }
    }
}
