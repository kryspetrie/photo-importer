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
import org.kryspetrie.fileimport.domain.model.CorrectionStrategy

/**
 * Dropdown for selecting how a photo should be geometrically corrected on export.
 *
 * Offers three strategies plus an optional "Default" option:
 * - Default (null): Inherits from the global scan-level setting
 * - CROP: Simple axis-aligned crop for nearly-rectangular photos
 * - CROP_AND_ROTATE: Crop + rotation for slightly rotated photos
 * - PERSPECTIVE: Full 4-point perspective transform for skewed/trapezoidal photos
 *
 * Use the nullable overload [CorrectionStrategyDropdown] with [selectedStrategy] as
 * [CorrectionStrategy?] when the caller needs to distinguish "use global default" from an
 * explicit per-photo override. Use the non-nullable overload when a default must always be
 * selected (e.g., the scan-level global setting).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorrectionStrategyDropdown(
    selectedStrategy: CorrectionStrategy?,
    onStrategyChange: (CorrectionStrategy?) -> Unit,
    modifier: Modifier = Modifier,
    defaultLabel: String = "Default",
) {
    var expanded by remember { mutableStateOf(false) }

    val currentLabel = selectedStrategy?.displayName ?: defaultLabel

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = currentLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Correction") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = modifier.menuAnchor().width(180.dp).height(40.dp),
            textStyle = MaterialTheme.typography.labelSmall,
        )

        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            // "Default" option — clears per-photo override and inherits from global setting
            DropdownMenuItem(
                text = {
                    Column {
                        Text(defaultLabel, style = MaterialTheme.typography.labelSmall)
                        Text(
                            "Use scan-level setting",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                onClick = {
                    onStrategyChange(null)
                    expanded = false
                },
            )
            CorrectionStrategy.entries.forEach { strategy ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(strategy.displayName, style = MaterialTheme.typography.labelSmall)
                            Text(
                                strategy.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        onStrategyChange(strategy)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * Non-nullable overload for contexts that always require a concrete strategy (e.g., the global
 * scan-level setting in [ExportSettingsCard]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorrectionStrategyDropdown(
    selectedStrategy: CorrectionStrategy,
    onStrategyChange: (CorrectionStrategy) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedStrategy.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Correction") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = modifier.menuAnchor().width(180.dp).height(40.dp),
            textStyle = MaterialTheme.typography.labelSmall,
        )

        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CorrectionStrategy.entries.forEach { strategy ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(strategy.displayName, style = MaterialTheme.typography.labelSmall)
                            Text(
                                strategy.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        onStrategyChange(strategy)
                        expanded = false
                    },
                )
            }
        }
    }
}