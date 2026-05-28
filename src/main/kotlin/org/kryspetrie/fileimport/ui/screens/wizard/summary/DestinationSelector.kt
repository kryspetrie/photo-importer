package org.kryspetrie.fileimport.ui.screens.wizard.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Selector for the export destination folder with a change button that opens a folder picker. */
@Composable
fun DestinationSelector(
    destination: String,
    onDestinationChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showFolderPicker by remember { mutableStateOf(false) }

    Surface(tonalElevation = 1.dp, shape = RoundedCornerShape(8.dp), modifier = modifier) {
        DestinationRow(destination = destination, onChangeClick = { showFolderPicker = true })
    }

    if (showFolderPicker) {
        FolderPickerDialog(
            initialPath = destination,
            onPathSelected = { path ->
                onDestinationChange(path)
                showFolderPicker = false
            },
            onDismiss = { showFolderPicker = false },
        )
    }
}

@Composable
private fun DestinationRow(destination: String, onChangeClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Export Destination",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(destination, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }

        OutlinedButton(onClick = onChangeClick, modifier = Modifier.height(32.dp)) {
            Text("Change", style = MaterialTheme.typography.labelSmall)
        }
    }
}
