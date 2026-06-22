package org.kryspetrie.fileimport.ui.screens.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.MetadataHistory
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.domain.model.RecentMetadataSet
import org.kryspetrie.fileimport.ui.screens.wizard.metadata.MetadataEditState
import org.kryspetrie.fileimport.ui.screens.wizard.metadata.MetadataField
import org.kryspetrie.fileimport.ui.screens.wizard.metadata.RecentValuesDropdown

/**
 * A simplified photo editing dialog for the scan workflow.
 *
 * Displays metadata fields with optional recent value suggestions, and corner coordinate info.
 *
 * @param photo The detected photo being edited.
 * @param metadataHistory Optional metadata history for recent value suggestions.
 * @param onRecordMetadataSet Optional callback to record a metadata set when the user saves.
 * @param onClose Callback when the dialog is dismissed.
 * @param onConfigChange Callback with the updated configuration when saved.
 */
@Composable
fun EditPhotoDialog(
    photo: DetectedPhoto,
    metadataHistory: MetadataHistory? = null,
    onRecordMetadataSet: ((RecentMetadataSet) -> Unit)? = null,
    onClose: () -> Unit,
    onConfigChange: (PhotoScanConfiguration) -> Unit,
) {
    val editState = remember { MetadataEditState().apply { loadFrom(photo.configuration) } }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Edit Photo") },
        text = {
            Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                // ── Recent Values ──
                if (metadataHistory != null && metadataHistory.recentSets.isNotEmpty()) {
                    RecentValuesDropdown(
                        recentSets = metadataHistory.recentSets,
                        onApplySet = { set -> editState.loadFromSet(set) },
                    )
                }

                MetadataField(
                    label = "Description",
                    placeholder = "Photo description...",
                    value = editState.description,
                    onValueChange = { editState.description = it },
                    suggestions = metadataHistory?.description ?: emptyList(),
                    onCommit = {
                        metadataHistory?.let { _ -> /* handled by onRecordMetadataSet */ }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MetadataField(
                        label = "Keywords",
                        placeholder = "vacation, family, holiday",
                        value = editState.keywords,
                        onValueChange = { editState.keywords = it },
                        suggestions = metadataHistory?.keywords ?: emptyList(),
                        modifier = Modifier.weight(2f),
                    )
                    MetadataField(
                        label = "Year",
                        placeholder = "1995",
                        value = editState.year,
                        onValueChange = { editState.year = it.filter { c -> c.isDigit() }.take(4) },
                        keyboardType = KeyboardType.Number,
                        suggestions = metadataHistory?.year ?: emptyList(),
                        modifier = Modifier.weight(1f),
                    )
                }
                MetadataField(
                    label = "Original Date",
                    placeholder = "YYYY-MM-DD or YYYY-MM-DD HH:MM:SS",
                    value = editState.originalDate,
                    onValueChange = { editState.originalDate = it },
                    suggestions = metadataHistory?.originalDate ?: emptyList(),
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    "Corners:",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    "Drag corners on the image to adjust.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF666666),
                )
                Text(
                    "Top Left: (${photo.topLeft.x.toInt()}, ${photo.topLeft.y.toInt()})",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Top Right: (${photo.topRight.x.toInt()}, ${photo.topRight.y.toInt()})",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Bottom Left: (${photo.bottomLeft.x.toInt()}, ${photo.bottomLeft.y.toInt()})",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Bottom Right: (${photo.bottomRight.x.toInt()}, ${photo.bottomRight.y.toInt()})",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Size: ${photo.getBounds().getWidth()}x${photo.getBounds().getHeight()} px",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val newConfig = editState.applyToConfig(photo.configuration)
                    onConfigChange(newConfig)
                    // Record the metadata set for future reuse
                    val set = RecentMetadataSet.fromConfig(newConfig)
                    onRecordMetadataSet?.invoke(set)
                    onClose()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}