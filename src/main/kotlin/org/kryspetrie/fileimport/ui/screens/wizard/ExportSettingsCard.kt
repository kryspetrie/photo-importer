package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.CorrectionStrategy
import org.kryspetrie.fileimport.domain.model.DetectionMode
import org.kryspetrie.fileimport.ui.wizard.state.PhotoScanWizardState
import org.kryspetrie.fileimport.ui.screens.wizard.summary.CorrectionStrategyDropdown

/**
 * Card with export settings for the photo scan import screen: perspective correction toggle,
 * detection mode selector, correction strategy selector, margin slider, and metadata preferences.
 */
@Composable
fun ExportSettingsCard(
    state: PhotoScanWizardState,
    skipCropAndRotate: Boolean = false,
    onSkipCropAndRotateChange: ((Boolean) -> Unit)? = null,
    defaultDetectionMode: DetectionMode = DetectionMode.PERSPECTIVE_CORRECTION,
    onDetectionModeChange: ((DetectionMode) -> Unit)? = null,
    onStrategyChange: ((CorrectionStrategy) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val perspectiveEnabled by state.exportSettings.perspectiveCorrectionEnabled.collectAsState()
    val marginPercent by state.exportSettings.exportMarginPercent.collectAsState()
    val defaultStrategy by state.exportSettings.defaultCorrectionStrategy.collectAsState()

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
                        Text("Perspective correction", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (perspectiveEnabled)
                                "Warp-stretch removes skew and preserves all content"
                            else "Simple crop: axis-aligned rectangle, no skew removal",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(
                    checked = perspectiveEnabled,
                    onCheckedChange = { state.exportSettings.setPerspectiveCorrectionEnabled(it) },
                )
            }

            // Detection mode selector (Simple Crop vs Perspective Crop)
            if (onDetectionModeChange != null) {
                Column(Modifier.fillMaxWidth()) {
                    Text("Detection Mode", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DetectionMode.entries
                            .filter { it != DetectionMode.BOUNDING_BOX }
                            .forEach { mode ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    RadioButton(
                                        selected = defaultDetectionMode == mode,
                                        onClick = { onDetectionModeChange(mode) },
                                    )
                                    Column {
                                        Text(mode.displayName, style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            mode.description,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                    }
                }
            }

            // Correction strategy selector
            if (!perspectiveEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.CropFree,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Correction strategy", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "How to handle skew and rotation when perspective correction is off",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    CorrectionStrategyDropdown(
                        selectedStrategy = defaultStrategy,
                        onStrategyChange = { strategy: CorrectionStrategy ->
                            state.exportSettings.setDefaultCorrectionStrategy(strategy)
                            onStrategyChange?.invoke(strategy)
                        },
                    )
                }
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
                        onValueChange = { state.exportSettings.setExportMarginPercent(it.toDouble()) },
                        valueRange = 0f..0.1f,
                        steps = 9,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Skip Crop & Rotate checkbox
            if (onSkipCropAndRotateChange != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = skipCropAndRotate,
                        onCheckedChange = onSkipCropAndRotateChange,
                    )
                    Column {
                        Text(
                            "Skip Crop & Rotate",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "Go directly to metadata editing after selecting photos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }


        }
    }
}