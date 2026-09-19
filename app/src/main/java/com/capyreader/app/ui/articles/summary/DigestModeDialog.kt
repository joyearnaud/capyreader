package com.capyreader.app.ui.articles.summary


import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.capyreader.app.R

@Composable
fun DigestModeDialog(
    onDayWindow: () -> Unit,
    onAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.list_summary_action)) },
        text = { Text(stringResource(R.string.digest_mode_text)) },
        dismissButton = {
            TextButton(onClick = {
                onDismiss()
                onDayWindow()
            }) {
                Text(stringResource(R.string.digest_mode_days))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onAll()
            }) {
                Text(stringResource(R.string.digest_mode_all))
            }
        },
    )
}
