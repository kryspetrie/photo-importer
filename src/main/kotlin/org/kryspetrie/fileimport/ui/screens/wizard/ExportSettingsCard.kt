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
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.ui.components.SettingsToggle
import org.kryspetrie.fileimport.ui.wizard.state.PhotoScanWizardState

/**
 * Card with export settings for the photo scan import screen: perspective correction toggle,
 * margin slider, and skip crop & rotate toggle.
 */
@Composable
fun ExportSettingsCard(
    state: PhotoScanWizardState,
    skipCropAndRotate: Boolean = false,
    onSkipCropAndRotateChange: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val perspectiveEnabled by state.exportSettings.perspectiveCorrectionEnabled.collectAsState()
    val marginPercent by state.exportSettings.exportMarginPercent.collectAsState()

    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Export Settings", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))

            // Perspective correction + Skip Crop & Rotate toggles side-by-side
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    SettingsToggle(
                        checked = perspectiveEnabled,
                        onCheckedChange = { state.exportSettings.setPerspectiveCorrectionEnabled(it) },
                        label = "Perspective correction",
                        description =
                            if (perspectiveEnabled)
                                "Warp-stretch removes skew"
                            else "Simple crop, no skew removal",
                        icon = Icons.Default.Transform,
                    )
                }
                if (onSkipCropAndRotateChange != null) {
                    Column(Modifier.weight(1f)) {
                        SettingsToggle(
                            checked = skipCropAndRotate,
                            onCheckedChange = onSkipCropAndRotateChange,
                            label = "Skip Crop & Rotate",
                            description = "Go straight to metadata",
                            icon = Icons.Default.SkipNext,
                        )
                    }
                }
            }

            // Margin slider
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Default.CropFree,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Additional margin", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "${(marginPercent * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = marginPercent.toFloat(),
                        onValueChange = { state.exportSettings.setExportMarginPercent(it.toDouble()) },
                        valueRange = 0f..0.1f,
                        steps = 9,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}