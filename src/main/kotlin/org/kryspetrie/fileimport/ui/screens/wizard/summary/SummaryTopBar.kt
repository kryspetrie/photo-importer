package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.strings

/**
 * Top bar with rotation controls and a destructive "Reset" button that requires confirmation.
 */
@Composable
internal fun SummaryTopAppBar(
    photoCount: Int,
    onRotateAllCW: () -> Unit,
    onRotateAllCCW: () -> Unit,
    onClearAll: () -> Unit,
) {
    val s = strings()
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(s.t(StringKey.SCAN_TITLE), style = MaterialTheme.typography.labelLarge)
            TopAppBarActions(
                onRotateAllCCW = onRotateAllCCW,
                onRotateAllCW = onRotateAllCW,
                onReset = { showResetConfirmDialog = true },
            )
        }
    }

    if (showResetConfirmDialog) {
        ResetConfirmDialog(
            photoCount = photoCount,
            onConfirm = {
                onClearAll()
                showResetConfirmDialog = false
            },
            onDismiss = { showResetConfirmDialog = false },
        )
    }
}

/** Action buttons in the top bar. */
@Composable
internal fun TopAppBarActions(
    onRotateAllCCW: () -> Unit,
    onRotateAllCW: () -> Unit,
    onReset: () -> Unit,
) {
    val s = strings()
    IconButton(onClick = onRotateAllCCW) {
        Icon(Icons.AutoMirrored.Filled.RotateLeft, s.t(StringKey.SCAN_ROTATE_ALL_CCW))
    }
    IconButton(onClick = onRotateAllCW) {
        Icon(Icons.AutoMirrored.Filled.RotateRight, s.t(StringKey.SCAN_ROTATE_ALL_CW))
    }
    OutlinedButton(
        onClick = onReset,
        modifier = Modifier.height(32.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) {
        Text(s.reset, style = MaterialTheme.typography.labelSmall)
    }
}

/** Confirmation dialog for the destructive "Reset" action. */
@Composable
internal fun ResetConfirmDialog(photoCount: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val s = strings()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.t(StringKey.SCAN_RESET_CONFIRM_TITLE)) },
        text = {
            Text(s.t(StringKey.SCAN_RESET_CONFIRM_MESSAGE, "count" to photoCount.toString()))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(s.reset, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } },
    )
}
