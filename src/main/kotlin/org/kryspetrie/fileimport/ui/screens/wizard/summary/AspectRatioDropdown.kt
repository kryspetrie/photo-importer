package org.kryspetrie.fileimport.ui.screens.wizard.summary

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
import org.kryspetrie.fileimport.ui.screens.wizard.AspectRatio

/**
 * Dropdown for selecting a photo aspect ratio, auto-selecting the closest match when set to 0.0.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AspectRatioDropdown(
    selectedRatio: Double,
    onRatioChange: (Double) -> Unit,
    boxAspectRatio: Double,
) {
    var expanded by remember { mutableStateOf(false) }

    val currentRatioName =
        remember(selectedRatio, boxAspectRatio) {
            if (selectedRatio == 0.0) {
                val bestMatch =
                    AspectRatio.entries.minByOrNull { ratio ->
                        if (ratio.value == 0.0) Double.MAX_VALUE
                        else kotlin.math.abs(ratio.value - boxAspectRatio)
                    }
                bestMatch?.displayName ?: "Current"
            } else {
                AspectRatio.entries.find { it.value == selectedRatio }?.displayName ?: "Custom"
            }
        }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = currentRatioName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Aspect Ratio") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().width(140.dp).height(40.dp),
            textStyle = MaterialTheme.typography.labelSmall,
        )

        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AspectRatio.entries.forEach { ratio ->
                DropdownMenuItem(
                    text = { Text(ratio.displayName, style = MaterialTheme.typography.labelSmall) },
                    onClick = {
                        onRatioChange(ratio.value)
                        expanded = false
                    },
                )
            }
        }
    }
}
