package org.kryspetrie.fileimport.ui.screens.wizard.summary

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.kryspetrie.fileimport.ui.components.FolderSelectionField

/** Selector for the export destination folder with a browse button in the field. */
@Composable
fun DestinationSelector(
    destination: String,
    onDestinationChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FolderSelectionField(
        value = destination,
        onValueChange = onDestinationChange,
        modifier = modifier.fillMaxWidth(),
        label = "Export Destination",
        placeholder = "Select destination...",
        title = "Select Export Destination",
        supportingText = {
            if (destination.isNotBlank()) {
                Text(destination, style = MaterialTheme.typography.labelSmall)
            } else {
                Text("Browse or type a path", style = MaterialTheme.typography.labelSmall)
            }
        },
    )
}
