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
import androidx.compose.material.icons.filled.SkipNext
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
import org.kryspetrie.fileimport.domain.model.geometry.Corner
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.strings
import org.kryspetrie.fileimport.ui.wizard.state.FourPointState
import org.kryspetrie.fileimport.ui.wizard.state.PhotoScanWizardState
import org.kryspetrie.fileimport.ui.wizard.state.WizardMode

/** Mode toggle buttons and box-count info row. */
@Composable
private fun ModeControlsRow(
    state: PhotoScanWizardState,
    wizardMode: WizardMode,
    boxCount: Int,
    refocus: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val s = strings()
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 4-Point mode toggle
        when (wizardMode) {
            WizardMode.FOUR_POINT -> {
                OutlinedButton(
                    onClick = {
                        state.exitFourPointMode()
                        refocus()
                    },
                    modifier = Modifier.height(40.dp),
                ) {
                    Text(s.t(StringKey.WIZARD_CANCEL_FOUR_POINT))
                }
            }
            else -> {
                OutlinedButton(
                    onClick = {
                        state.enterFourPointMode()
                        refocus()
                    },
                    modifier = Modifier.height(40.dp),
                ) {
                    Icon(Icons.Default.GridOn, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(s.t(StringKey.WIZARD_FOUR_POINT))
                }
            }
        }

        // Add Box mode toggle (2-click rectangle)
        when (wizardMode) {
            WizardMode.FOUR_POINT -> {
                OutlinedButton(
                    onClick = {
                        state.exitFourPointMode()
                        refocus()
                    },
                    modifier = Modifier.height(40.dp),
                ) {
                    Text(s.cancel)
                }
            }
            else -> {
                OutlinedButton(
                    onClick = {
                        state.enterRectangleMode()
                        refocus()
                    },
                    modifier = Modifier.height(40.dp),
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(s.t(StringKey.WIZARD_ADD_BOX))
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Text(
            s.t(StringKey.WIZARD_IMAGE_COUNT, "count" to "$boxCount"),
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
    onSkipCurrentPhoto: (() -> Unit)? = null,
    refocus: () -> Unit = {},
    viewportWidth: Double = 800.0,
    viewportHeight: Double = 600.0,
    modifier: Modifier = Modifier,
) {
    val s = strings()
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
                    state.boxes.selectBox(prevIndex)
                    state.fitToSelectedBox(viewportWidth, viewportHeight)
                    refocus()
                },
                enabled = boxCount > 1,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    s.t(StringKey.WIZARD_PREVIOUS_PHOTO),
                    Modifier.size(18.dp),
                )
            }
            Text(
                s.t(
                    StringKey.SCAN_PHOTO_LABEL,
                    "index" to "${selectedBoxIndex + 1}",
                    "total" to "$boxCount",
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            IconButton(
                onClick = {
                    val nextIndex = (selectedBoxIndex + 1) % boxCount
                    state.boxes.selectBox(nextIndex)
                    state.fitToSelectedBox(viewportWidth, viewportHeight)
                    refocus()
                },
                enabled = boxCount > 1,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    s.t(StringKey.WIZARD_NEXT_PHOTO_NAV),
                    Modifier.size(18.dp),
                )
            }

            IconButton(
                onClick = {
                    state.boxes.undo()
                    refocus()
                },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Undo, s.t(StringKey.META_UNDO), Modifier.size(18.dp))
            }
            IconButton(
                onClick = {
                    state.boxes.redo()
                    refocus()
                },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Redo, s.t(StringKey.META_REDO), Modifier.size(18.dp))
            }

            IconButton(
                onClick = {
                    state.removeSelectedBox()
                    refocus()
                },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Default.Delete,
                    s.t(StringKey.ACC_DELETE_PHOTO),
                    tint = MaterialTheme.colorScheme.error,
                )
            }

            if (selectedCorner != null) {
                Text(
                    s.t(StringKey.WIZARD_CORNER, "name" to selectedCorner.name),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        if (onSkipCurrentPhoto != null) {
            OutlinedButton(
                onClick = onSkipCurrentPhoto,
                modifier = Modifier.height(40.dp),
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
            ) {
                Icon(Icons.Default.SkipNext, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(s.t(StringKey.WIZARD_SKIP_PHOTO))
            }
        }

        OutlinedButton(onClick = onBack, modifier = Modifier.height(40.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(s.back)
        }

        Button(onClick = onToSummary, enabled = boxCount > 0, modifier = Modifier.height(40.dp)) {
            Text(s.next)
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
    onSkipCurrentPhoto: (() -> Unit)? = null,
    refocus: () -> Unit = {},
    viewportWidth: Double = 800.0,
    viewportHeight: Double = 600.0,
) {
    val selectedCorner by state.boxes.selectedCorner.collectAsState()

    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            ModeControlsRow(
                state = state,
                wizardMode = wizardMode,
                boxCount = boxCount,
                refocus = refocus,
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
                onSkipCurrentPhoto = onSkipCurrentPhoto,
                refocus = refocus,
                modifier = Modifier.fillMaxWidth(),
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
            )
        }
    }
}
