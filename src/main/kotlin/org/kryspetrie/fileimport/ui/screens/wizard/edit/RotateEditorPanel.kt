package org.kryspetrie.fileimport.ui.screens.wizard.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.infrastructure.wizard.BoundingBoxList
import org.kryspetrie.fileimport.infrastructure.wizard.PhotoConfiguration
import org.kryspetrie.fileimport.infrastructure.wizard.PhotoScanWizardState
import org.kryspetrie.fileimport.ui.components.ChunkyScrollbar

/**
 * Rotation-only editor panel — shown in Rotate mode. Displays per-photo rotation controls with a
 * large preview.
 */
@Composable
internal fun RotateEditorPanel(
    state: PhotoScanWizardState,
    boundingBoxList: BoundingBoxList,
    photoConfigurations: Map<String, PhotoConfiguration>,
    selectedIndices: Set<Int>,
    isMultiEditMode: Boolean,
    onAddBackImage: () -> Unit,
    onRemoveBackImage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isMultiSelect = selectedIndices.size > 1 || isMultiEditMode

    ChunkyScrollbar(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isMultiSelect) {
                Text(
                    "${selectedIndices.size} photos selected",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                // Batch rotation controls for multi-select
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Rotate all:", style = MaterialTheme.typography.labelMedium)
                    IconButton(
                        onClick = {
                            selectedIndices.forEach { idx ->
                                if (idx < boundingBoxList.size()) {
                                    val box = boundingBoxList.boxes[idx]
                                    state.updatePhotoConfiguration(box.id) { it.cycleRotationCCW() }
                                }
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.RotateLeft, "CCW", Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = {
                            selectedIndices.forEach { idx ->
                                if (idx < boundingBoxList.size()) {
                                    val box = boundingBoxList.boxes[idx]
                                    state.updatePhotoConfiguration(box.id) {
                                        it.copy(rotationDegrees = (it.rotationDegrees + 180) % 360)
                                    }
                                }
                            }
                        }
                    ) {
                        Text("180°", style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(
                        onClick = {
                            selectedIndices.forEach { idx ->
                                if (idx < boundingBoxList.size()) {
                                    val box = boundingBoxList.boxes[idx]
                                    state.updatePhotoConfiguration(box.id) { it.cycleRotationCW() }
                                }
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.RotateRight, "CW", Modifier.size(18.dp))
                    }
                }
            } else if (selectedIndices.size == 1) {
                val selectedIndex = selectedIndices.first()
                val box = boundingBoxList.boxes[selectedIndex]
                val config = photoConfigurations[box.id] ?: PhotoConfiguration()

                Text(
                    "Photo ${selectedIndex + 1}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                RotationSection(
                    rotationDegrees = config.rotationDegrees,
                    onRotateCW = {
                        state.updatePhotoConfiguration(box.id) { it.cycleRotationCW() }
                    },
                    onRotateCCW = {
                        state.updatePhotoConfiguration(box.id) { it.cycleRotationCCW() }
                    },
                    onRotate180 = {
                        state.updatePhotoConfiguration(box.id) {
                            it.copy(rotationDegrees = (it.rotationDegrees + 180) % 360)
                        }
                    },
                )

                // Additional per-photo settings
                Surface(
                    tonalElevation = 1.dp,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Photo Settings", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "Rotation: ${config.rotationDegrees}°",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        // ── Back-of-photo image management ──
                        if (config.hasBackImage()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Image,
                                    "Back image assigned",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    "Back: ${if (config.backImageMode == "combine") "Combined" else "Appended"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.weight(1f))
                                OutlinedButton(
                                    onClick = onAddBackImage,
                                    modifier = Modifier.height(28.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                ) {
                                    Text("Change", style = MaterialTheme.typography.labelSmall)
                                }
                                OutlinedButton(
                                    onClick = onRemoveBackImage,
                                    modifier = Modifier.height(28.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                ) {
                                    Text(
                                        "Remove",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = onAddBackImage,
                                modifier = Modifier.fillMaxWidth().height(32.dp),
                            ) {
                                Icon(
                                    Icons.Default.Image,
                                    "Select back of photo",
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Add Back Image", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // Help text
            Surface(
                tonalElevation = 0.dp,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "Switch to Metadata tab to edit IPTC fields, location, and subjects.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
