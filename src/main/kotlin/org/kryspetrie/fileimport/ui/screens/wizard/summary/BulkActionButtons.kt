package org.kryspetrie.fileimport.ui.screens.wizard.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.strings

/**
 * Row of bulk action buttons. Warp-stretch is always applied; only rotation and clear are needed.
 */
@Composable
fun BulkActionButtons(
    onRotateAllCW: () -> Unit,
    onRotateAllCCW: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(tonalElevation = 1.dp, shape = RoundedCornerShape(8.dp), modifier = modifier) {
        ActionButtonRow(
            onRotateAllCW = onRotateAllCW,
            onRotateAllCCW = onRotateAllCCW,
            onClearAll = onClearAll,
        )
    }
}

@Composable
private fun ActionButtonRow(
    onRotateAllCW: () -> Unit,
    onRotateAllCCW: () -> Unit,
    onClearAll: () -> Unit,
) {
    val s = strings()
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            s.t(StringKey.WIZARD_BULK_ACTIONS),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedButton(onClick = onRotateAllCW, modifier = Modifier.height(32.dp)) {
            Icon(Icons.Default.RotateRight, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(s.t(StringKey.ACC_ROTATE_CW), style = MaterialTheme.typography.labelSmall)
        }

        OutlinedButton(onClick = onRotateAllCCW, modifier = Modifier.height(32.dp)) {
            Icon(Icons.Default.RotateLeft, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(s.t(StringKey.ACC_ROTATE_CCW), style = MaterialTheme.typography.labelSmall)
        }

        OutlinedButton(
            onClick = onClearAll,
            modifier = Modifier.height(32.dp),
            colors =
                ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Icon(Icons.Default.Clear, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(s.t(StringKey.WIZARD_CLEAR_ALL), style = MaterialTheme.typography.labelSmall)
        }
    }
}
