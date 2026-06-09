package org.kryspetrie.fileimport.ui.screens.duplicatescanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.DuplicateAction

@Composable
fun DuplicateResolveConfirmDialog(
    duplicateCount: Int,
    resolveAction: DuplicateAction,
    moveToTrash: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Resolve All Duplicates") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("This will process $duplicateCount duplicate groups.")
                val actionText =
                    when (resolveAction) {
                        DuplicateAction.KEEP_HIGHEST_RES -> "Keep highest resolution"
                        DuplicateAction.KEEP_RAW_OVER_JPEG -> "Keep RAW over JPEG"
                        DuplicateAction.KEEP_NEWEST -> "Keep newest file"
                        DuplicateAction.KEEP_OLDEST -> "Keep oldest file"
                        DuplicateAction.KEEP_LARGEST -> "Keep largest file"
                    }
                Text("Strategy: $actionText")
                if (moveToTrash) {
                    Text(
                        "Removed files will be moved to duplicates_review/ for safe review.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "WARNING: Removed files will be permanently deleted!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    "Resolve",
                    color = if (moveToTrash) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
