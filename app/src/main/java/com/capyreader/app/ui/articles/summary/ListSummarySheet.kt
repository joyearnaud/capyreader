package com.capyreader.app.ui.articles.summary

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.capyreader.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListSummarySheet(
    controller: ListSummaryController,
    onDismiss: () -> Unit,
) {
    val state = controller.state
    if (!state.isVisible) return

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp, max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.list_summary_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (state.isLoading) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(Modifier.size(16.dp))
                }
            }

            SummaryContent(
                state = state,
                modifier = Modifier.padding(top = 12.dp),
                referenceTargets = controller.referenceTargets,
            )

            Spacer(Modifier.padding(bottom = 32.dp))
        }
    }
}
