package dev.hachifridge.hachidroid.ui.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.hachifridge.hachidroid.R

@Composable
fun SimpleOkCancelDialog(
    title: String,
    onClose: (Boolean) -> Unit,
    confirmText: String = stringResource(R.string.ok),
    content: @Composable ColumnScope.() -> Unit
) {
    AlertDialog(
        onDismissRequest = { onClose(false) },
        title = { Text(title) },
        text = {
            androidx.compose.foundation.layout.Column {
                content()
            }
        },
        confirmButton = {
            TextButton(onClick = { onClose(true) }) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = { onClose(false) }) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}