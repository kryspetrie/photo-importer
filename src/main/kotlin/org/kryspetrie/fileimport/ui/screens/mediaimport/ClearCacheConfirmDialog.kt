package org.kryspetrie.fileimport.ui.screens.mediaimport

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun ClearCacheConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clear Index Cache") },
        text = { Text("Clear all cached folder indexes? Folders will be re-indexed on next use.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Clear", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
