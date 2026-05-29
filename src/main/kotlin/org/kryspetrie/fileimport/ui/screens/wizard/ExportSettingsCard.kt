package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.infrastructure.wizard.PhotoScanWizardState

/**
 * Card with export settings for the photo scan import screen: perspective correction toggle and
 * margin slider.
 */
@Composable
fun ExportSettingsCard(state: PhotoScanWizardState, modifier: Modifier = Modifier) {
    val perspectiveEnabled by state.perspectiveCorrectionEnabled.collectAsState()
    val marginPercent by state.exportMarginPercent.collectAsState()

    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Export Settings", style = MaterialTheme.typography.titleSmall)

            // Perspective correction toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.Transform,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column {
                        Text("Correct for perspective", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (perspectiveEnabled)
                                "Warp-stretch: removes skew, preserves all content"
                            else "Simple crop: axis-aligned rectangle, no skew removal",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(
                    checked = perspectiveEnabled,
                    onCheckedChange = { state.setPerspectiveCorrectionEnabled(it) },
                )
            }

            // Margin slider
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Default.CropFree,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Additional margin", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${(marginPercent * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        "Expands the crop outward to include edges that may be clipped",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = marginPercent.toFloat(),
                        onValueChange = { state.setExportMarginPercent(it.toDouble()) },
                        valueRange = 0f..0.1f,
                        steps = 9,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
