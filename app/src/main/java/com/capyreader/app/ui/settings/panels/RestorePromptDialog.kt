package com.capyreader.app.ui.settings.panels


import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.capyreader.app.R

@Composable
fun RestorePromptDialog(
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val title = stringResource(R.string.settings_ai_restore_prompt_title)
    val message = stringResource(R.string.settings_ai_restore_prompt_text)
    val confirmText = stringResource(R.string.settings_ai_restore_prompt_confirm)

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = { Text(message) },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText)
            }
        }
    )
}
