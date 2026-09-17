package com.capyreader.app.ui.articles.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography

/**
 * The streamed summary body: per-block Markdown plus the plain stripped
 * tail, shared by the article card and the list digest sheet.
 */
@Composable
fun SummaryContent(
    state: SummaryUiState,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        state.text?.let {
            val body = MaterialTheme.typography.bodyLarge
            // One Markdown per block: the renderer flips to an async Loading
            // state (rendered as nothing) whenever its content string changes,
            // so a single growing Markdown call flashes the card shut every
            // tick. A completed block's string never changes, hitting the
            // early-return cache instead; only the newest block parses.
            val typography = markdownTypography(
                text = body,
                paragraph = body,
                list = body,
                h1 = MaterialTheme.typography.titleMedium,
                h2 = MaterialTheme.typography.titleMedium,
                h3 = body.copy(fontWeight = FontWeight.SemiBold),
                h4 = body.copy(fontWeight = FontWeight.SemiBold),
                h5 = body.copy(fontWeight = FontWeight.SemiBold),
                h6 = body.copy(fontWeight = FontWeight.SemiBold),
                quote = body.copy(fontStyle = FontStyle.Italic),
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                it.split(Regex("\n\n+"))
                    .filter { block -> block.isNotBlank() }
                    .forEach { block ->
                        Markdown(block, typography = typography)
                    }
            }
        }

        state.streamTail?.takeIf { it.isNotBlank() }?.let { tail ->
            Text(
                text = stripStreamTailMarkers(tail),
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        state.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
