package com.capyreader.app.ui.articles.summary

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.capyreader.app.R
import com.mikepenz.markdown.m3.Markdown

@Composable
fun SummaryCard(
    summary: SummaryController,
    modifier: Modifier = Modifier,
) {
    val state = summary.state
    if (!state.isVisible) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.summary_card_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (state.isLoading) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(Modifier.size(16.dp))
                }
            }

            state.text?.let {
                Markdown(it)
            }

            state.error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (state.isTruncated) {
                Text(
                    text = stringResource(R.string.summary_card_truncated),
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            Row {
                TextButton(
                    onClick = summary.summarize,
                    enabled = !state.isLoading,
                ) {
                    Text(stringResource(R.string.summary_card_resummarize))
                }
                TextButton(onClick = summary.dismiss) {
                    Text(stringResource(R.string.summary_card_dismiss))
                }
            }
        }
    }
}
