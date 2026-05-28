package org.kryspetrie.fileimport.ui.screens.scan

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration

@Composable
fun EditPhotoDialog(
    photo: DetectedPhoto,
    onClose: () -> Unit,
    onConfigChange: (PhotoScanConfiguration) -> Unit,
) {
    var originalDate by remember {
        mutableStateOf(photo.configuration.originalDateOverride.orEmpty())
    }
    var originalYear by remember {
        mutableStateOf(photo.configuration.originalYearOverride.orEmpty())
    }
    var originalMonth by remember {
        mutableStateOf(photo.configuration.originalMonthOverride.orEmpty())
    }
    var tags by remember { mutableStateOf(photo.configuration.tags) }
    var notes by remember { mutableStateOf(photo.configuration.notes) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Edit Photo") },
        text = {
            Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = originalDate,
                    onValueChange = { originalDate = it },
                    label = { Text("Original Date") },
                    placeholder = { Text("YYYY-MM-DD HH:MM:SS") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = originalYear,
                    onValueChange = { originalYear = it },
                    label = { Text("Year") },
                    placeholder = { Text("YYYY") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = originalMonth,
                    onValueChange = { originalMonth = it },
                    label = { Text("Month") },
                    placeholder = { Text("MM") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags (comma-separated)") },
                    placeholder = { Text("tag1, tag2") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    placeholder = { Text("Additional notes") },
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
            Button(
                onClick = {
                    val newConfig =
                        photo.configuration.copy(
                            originalDateOverride =
                                if (originalDate.isNotBlank()) originalDate else null,
                            originalYearOverride =
                                if (originalYear.isNotBlank()) originalYear else null,
                            originalMonthOverride =
                                if (originalMonth.isNotBlank()) originalMonth else null,
                            tags = tags,
                            notes = notes,
                        )
                    onConfigChange(newConfig)
                    onClose()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}
