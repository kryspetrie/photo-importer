package org.kryspetrie.fileimport.ui.screens.wizard.summary

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.DetectionMode

/**
 * Dropdown for selecting how a photo was detected.
 *
 * When set to null, the detection mode is inferred from the pipeline that produced the photo.
 * Selecting a specific mode overrides that inference.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetectionModeDropdown(
    selectedMode: DetectionMode?,
    onModeChange: (DetectionMode?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    val currentLabel = selectedMode?.displayName ?: "Auto"

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = currentLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Detection Mode") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = modifier.menuAnchor().width(200.dp).height(40.dp),
            textStyle = MaterialTheme.typography.labelSmall,
        )

        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            // Auto option — let the pipeline decide
            DropdownMenuItem(
                text = {
                    Column {
                        Text("Auto", style = MaterialTheme.typography.labelSmall)
                        Text(
                            "Infer from pipeline",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                onClick = {
                    onModeChange(null)
                    expanded = false
                },
            )
            // Only show user-facing detection modes (BOUNDING_BOX is internal)
            DetectionMode.entries
                .filter { it != DetectionMode.BOUNDING_BOX }
                .forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(mode.displayName, style = MaterialTheme.typography.labelSmall)
                            Text(
                                mode.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        onModeChange(mode)
                        expanded = false
                    },
                )
            }
        }
    }
}
