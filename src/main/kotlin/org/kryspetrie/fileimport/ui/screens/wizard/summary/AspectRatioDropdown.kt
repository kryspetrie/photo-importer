package org.kryspetrie.fileimport.ui.screens.wizard.summary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import org.kryspetrie.fileimport.domain.model.AspectRatio

/**
 * Dropdown for selecting a photo aspect ratio, auto-selecting the closest match when set to 0.0.
 *
 * Uses Box + DropdownMenu instead of ExposedDropdownMenuBox to avoid
 * MutatorMutex/MonotonicFrameClock crashes on Compose Desktop.
 */
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

    Box(modifier = Modifier.clickable { expanded = true }) {
        OutlinedTextField(
            value = currentRatioName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Aspect Ratio") },
            modifier = Modifier.width(140.dp).height(40.dp),
            textStyle = MaterialTheme.typography.labelSmall,
            trailingIcon = { Text("▾", style = MaterialTheme.typography.bodySmall) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
