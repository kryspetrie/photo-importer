package org.kryspetrie.fileimport.ui.screens.wizard.summary

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.strings

/** Tooltip dialog explaining the mutual exclusivity of perspective and rotation corrections. */
@Composable
fun CorrectionInfoTooltip(modifier: Modifier = Modifier) {
    val s = strings()
    var showTooltip by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { showTooltip = true }, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Info,
                s.t(StringKey.ACC_CORRECTION_INFO),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showTooltip) {
            AlertDialog(
                onDismissRequest = { showTooltip = false },
                title = { Text(s.t(StringKey.WIZARD_CORRECTION_OPTIONS)) },
                text = { Text(s.t(StringKey.WIZARD_CORRECTION_BODY)) },
                confirmButton = { TextButton(onClick = { showTooltip = false }) { Text(s.ok) } },
            )
        }
    }
}
