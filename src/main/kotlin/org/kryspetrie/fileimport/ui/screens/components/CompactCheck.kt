package org.kryspetrie.fileimport.ui.screens.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.ui.components.CircularSpinner
import org.kryspetrie.fileimport.ui.components.SettingsToggle

@Composable
fun CompactCheck(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String) {
    SettingsToggle(checked = checked, onCheckedChange = onCheckedChange, label = label)
}

/**
 * A card showing progress with an optional step icon.
 *
 * @param title The step description (e.g., "Scanning source folder...")
 * @param current Items processed so far
 * @param total Total items to process
 * @param currentFile Name of the file currently being processed
 * @param icon Optional icon to visually identify the step type
 */
@Composable
fun ProgressCard(
    title: String,
    current: Int,
    total: Int,
    currentFile: String,
    icon: ImageVector? = null,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (icon != null) {
                    Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                }
                CircularSpinner(size = 24.dp, strokeWidth = 2.5.dp)
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            if (total > 0) {
                LinearProgressIndicator(
                    progress = { current.toFloat() / total },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "$current / $total",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (currentFile.isNotBlank()) {
                Text(
                    currentFile,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
