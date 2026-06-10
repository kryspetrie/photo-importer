package org.kryspetrie.fileimport.ui.screens.wizard.summary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.CorrectionStrategy
import org.kryspetrie.fileimport.infrastructure.wizard.BoundingBox
import org.kryspetrie.fileimport.infrastructure.wizard.PhotoConfiguration

/**
 * Card showing summary and rotation/correction options for a single detected photo. Displays
 * detection mode badge, correction strategy selector, and rotation controls.
 */
@Composable
fun PhotoSummaryCard(
    box: BoundingBox,
    index: Int,
    isSelected: Boolean,
    config: PhotoConfiguration,
    onSelect: () -> Unit,
    onConfigChange: (PhotoConfiguration) -> Unit,
    onDelete: () -> Unit,
) {
    val borderColor =
        if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    }
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PhotoCardHeader(box = box, index = index, config = config)

            // Correction strategy selector per photo
            CorrectionStrategyRow(config = config, onConfigChange = onConfigChange)

            PhotoCorrectionRow(
                config = config,
                onConfigChange = onConfigChange,
                onDelete = onDelete,
            )
        }
    }
}

/** Header row showing photo number, detection mode badge, pixel dimensions, and rotation state. */
@Composable
private fun PhotoCardHeader(box: BoundingBox, index: Int, config: PhotoConfiguration) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Photo ${index + 1}", style = MaterialTheme.typography.titleSmall)
            // Detection mode badge (only shown when a mode is set)
            config.detectionMode?.let { mode -> DetectionModeBadge(mode = mode) }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (config.rotationDegrees != 0) {
                Text(
                    "Rotated ${config.rotationDegrees}°",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                "${box.width().toInt()} × ${box.height().toInt()} px",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Row with correction strategy dropdown, allowing per-photo override. Shows "Default" as first
 * option (null = use global setting), plus the three strategy choices.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CorrectionStrategyRow(
    config: PhotoConfiguration,
    onConfigChange: (PhotoConfiguration) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Correction:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = config.correctionStrategy?.displayName ?: "Default",
                onValueChange = {},
                readOnly = true,
                label = { Text("Correction") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.height(40.dp).menuAnchor().width(160.dp),
                textStyle = MaterialTheme.typography.labelSmall,
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = {
                        Column {
                            Text("Default", style = MaterialTheme.typography.labelSmall)
                            Text(
                                "Use the correction strategy from settings",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        onConfigChange(config.copy(correctionStrategy = null))
                        expanded = false
                    },
                )
                CorrectionStrategy.entries.forEach { strategy ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    strategy.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                Text(
                                    strategy.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = {
                            onConfigChange(config.copy(correctionStrategy = strategy))
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/** Row with rotation button and delete button. Warp-stretch is always applied. */
@Composable
private fun PhotoCorrectionRow(
    config: PhotoConfiguration,
    onConfigChange: (PhotoConfiguration) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Rotation cycles: 0° → 90° → 180° → 270° → 0°
        IconButton(
            onClick = {
                onConfigChange(
                    config.copy(rotationDegrees = config.cycleRotationCW().rotationDegrees)
                )
            },
            modifier = Modifier.size(36.dp),
        ) {
            Icon(Icons.Default.RotateRight, "Rotate clockwise", modifier = Modifier.size(20.dp))
        }
        Text(
            "${config.rotationDegrees}°",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(36.dp),
        )

        Spacer(Modifier.weight(1f))

        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Delete,
                "Delete",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}
