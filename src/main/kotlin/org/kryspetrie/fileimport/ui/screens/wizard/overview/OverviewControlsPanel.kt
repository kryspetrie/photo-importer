package org.kryspetrie.fileimport.ui.screens.wizard.overview

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.infrastructure.wizard.Corner
import org.kryspetrie.fileimport.infrastructure.wizard.FourPointState
import org.kryspetrie.fileimport.infrastructure.wizard.PhotoScanWizardState
import org.kryspetrie.fileimport.infrastructure.wizard.WizardMode

/** Mode toggle buttons and box-count info row. */
@Composable
private fun ModeControlsRow(
    state: PhotoScanWizardState,
    wizardMode: WizardMode,
    boxCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 4-Point mode toggle
        when (wizardMode) {
            WizardMode.FOUR_POINT -> {
                Button(
                    onClick = { state.exitFourPointMode() },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        ),
                    modifier = Modifier.height(40.dp),
                ) {
                    Text("Cancel 4-Point")
                }
            }
            else -> {
                OutlinedButton(
                    onClick = { state.enterFourPointMode() },
                    modifier = Modifier.height(40.dp),
                ) {
                    Icon(Icons.Default.GridOn, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("4-Point")
                }
            }
        }

        // Add Box mode toggle (2-click rectangle)
        when (wizardMode) {
            WizardMode.FOUR_POINT -> {
                Button(
                    onClick = { state.exitFourPointMode() },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        ),
                    modifier = Modifier.height(40.dp),
                ) {
                    Text("Cancel")
                }
            }
            else -> {
                OutlinedButton(
                    onClick = { state.enterRectangleMode() },
                    modifier = Modifier.height(40.dp),
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Box")
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Box count info
        Text(
            "$boxCount photo${if (boxCount != 1) "s" else ""}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Photo navigation, corner editing controls, and action buttons. */
@Composable
private fun ActionButtonsRow(
    state: PhotoScanWizardState,
    selectedBoxIndex: Int,
    selectedCorner: Corner?,
    boxCount: Int,
    onBack: () -> Unit,
    onToSummary: () -> Unit,
    viewportWidth: Double = 800.0,
    viewportHeight: Double = 600.0,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Photo navigation: ← Photo N of M →
        if (boxCount > 0 && selectedBoxIndex >= 0) {
            IconButton(
                onClick = {
                    val prevIndex = (selectedBoxIndex - 1 + boxCount) % boxCount
                    state.selectBox(prevIndex)
                    state.fitToSelectedBox(viewportWidth, viewportHeight)
                },
                enabled = boxCount > 1,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous photo", Modifier.size(18.dp))
            }
            Text(
                "Photo ${selectedBoxIndex + 1} of $boxCount",
                style = MaterialTheme.typography.titleMedium,
            )
            IconButton(
                onClick = {
                    val nextIndex = (selectedBoxIndex + 1) % boxCount
                    state.selectBox(nextIndex)
                    state.fitToSelectedBox(viewportWidth, viewportHeight)
                },
                enabled = boxCount > 1,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next photo", Modifier.size(18.dp))
            }

            // Undo / Redo
            IconButton(onClick = { state.undo() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.Undo, "Undo", Modifier.size(18.dp))
            }
            IconButton(onClick = { state.redo() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.Redo, "Redo", Modifier.size(18.dp))
            }

            // Delete selected box
            IconButton(onClick = { state.removeSelectedBox() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, "Delete photo", tint = MaterialTheme.colorScheme.error)
            }

            // Corner selection info
            if (selectedCorner != null) {
                Text(
                    "Corner: ${selectedCorner.name}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Cancel and Continue buttons
        OutlinedButton(onClick = onBack, modifier = Modifier.height(40.dp)) { Text("Cancel") }

        Button(onClick = onToSummary, enabled = boxCount > 0, modifier = Modifier.height(40.dp)) {
            Text("Continue to Summary")
            Spacer(Modifier.width(4.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
        }
    }
}

/**
 * Consolidated controls panel for the bottom of the overview screen. Contains mode selection, photo
 * navigation, editing controls, and action buttons.
 */
@Composable
fun OverviewControlsPanel(
    state: PhotoScanWizardState,
    wizardMode: WizardMode,
    fourPointState: FourPointState,
    selectedBoxIndex: Int,
    boxCount: Int,
    onBack: () -> Unit,
    onToSummary: () -> Unit,
    viewportWidth: Double = 800.0,
    viewportHeight: Double = 600.0,
) {
    val selectedCorner by state.selectedCorner.collectAsState()

    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            ModeControlsRow(
                state = state,
                wizardMode = wizardMode,
                boxCount = boxCount,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            ActionButtonsRow(
                state = state,
                selectedBoxIndex = selectedBoxIndex,
                selectedCorner = selectedCorner,
                boxCount = boxCount,
                onBack = onBack,
                onToSummary = onToSummary,
                modifier = Modifier.fillMaxWidth(),
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
            )
        }
    }
}
