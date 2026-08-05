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
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.strings

@Composable
fun DuplicateResolveConfirmDialog(
    duplicateCount: Int,
    resolveAction: DuplicateAction,
    moveToTrash: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val s = strings()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.t(StringKey.DUP_RESOLVE_ALL_TITLE)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(s.t(StringKey.DUP_RESOLVE_ALL_MESSAGE, "count" to duplicateCount.toString()))
                Text(s.t(StringKey.DUP_STRATEGY, "strategy" to s.t(resolveAction.labelKey())))
                if (moveToTrash) {
                    Text(
                        s.t(StringKey.DUP_REVIEW_FOLDER_NOTE),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        s.t(StringKey.DUP_DELETE_WARNING),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    s.t(StringKey.DUP_RESOLVE),
                    color =
                        if (moveToTrash) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.t(StringKey.ACTION_CANCEL)) } },
    )
}
