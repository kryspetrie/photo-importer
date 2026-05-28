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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.infrastructure.wizard.BoundingBox
import org.kryspetrie.fileimport.infrastructure.wizard.PhotoConfiguration

/**
 * Card showing summary and rotation options for a single detected photo. Warp-stretch is always
 * applied.
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

            PhotoCorrectionRow(
                config = config,
                onConfigChange = onConfigChange,
                onDelete = onDelete,
            )
        }
    }
}

/** Header row showing photo number, pixel dimensions, and current rotation state. */
@Composable
private fun PhotoCardHeader(box: BoundingBox, index: Int, config: PhotoConfiguration) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Photo ${index + 1}", style = MaterialTheme.typography.titleSmall)
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
 * Row with rotation button and delete button. Warp-stretch is always applied, no checkbox needed.
 */
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
