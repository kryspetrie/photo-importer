package org.kryspetrie.fileimport.ui.screens.mediaimport

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.strings

@Composable
fun ClearCacheConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val s = strings()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.t(StringKey.IMPORT_CLEAR_CACHE_TITLE)) },
        text = { Text(s.t(StringKey.IMPORT_CLEAR_CACHE_MESSAGE)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(s.t(StringKey.IMPORT_CLEAR_CACHE_BUTTON), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.t(StringKey.ACTION_CANCEL)) } },
    )
}
