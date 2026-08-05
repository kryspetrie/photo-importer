package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage
import kotlinx.coroutines.delay
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.components.LoadingIndicator
import org.kryspetrie.fileimport.ui.i18n.strings
import org.kryspetrie.fileimport.ui.screens.wizard.overview.FourPointStatusBar
import org.kryspetrie.fileimport.ui.screens.wizard.overview.OverviewCanvas
import org.kryspetrie.fileimport.ui.screens.wizard.overview.OverviewControlsPanel
import org.kryspetrie.fileimport.ui.screens.wizard.overview.ZoomControls
import org.kryspetrie.fileimport.ui.wizard.state.FourPointState
import org.kryspetrie.fileimport.ui.wizard.state.PhotoScanWizardState
import org.kryspetrie.fileimport.ui.wizard.state.WizardMode
import org.kryspetrie.fileimport.ui.wizard.state.ZoomController

/**
 * Overview screen showing the full scanned image with all detected bounding boxes. Users can
 * select, add, remove, and navigate to refinement for individual boxes.
 */
@Composable
fun OverviewScreen(
    state: PhotoScanWizardState,
    onBack: () -> Unit,
    onToSummary: () -> Unit,
    onSkipCurrentPhoto: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    // Collect state flows
    val wizardMode by state.wizardMode.collectAsState()
    val fourPointState by state.fourPointState.collectAsState()
    val boundingBoxList by state.boundingBoxList.collectAsState()
    val selectedBoxIndex by state.boxes.selectedBoxIndex.collectAsState()
    val zoomController by state.zoom.zoomController.collectAsState()
    val image by state.image.collectAsState()
    // Gate here too: a single file chosen by path must never show Skip Photo.
    val batchSourceFiles by state.batch.sourceFiles.collectAsState()
    val skipCurrentPhoto =
        if (batchSourceFiles.size > 1) onSkipCurrentPhoto else null

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showBoxRejectedMessage by remember { mutableStateOf(false) }
    var hasFittedToView by remember { mutableStateOf(false) }

    // Focus requester for auto-focus on load (so keyboard works immediately without clicking)
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // Fit view to image on initial load (only once, not on every resize)
    LaunchedEffect(containerSize, image) {
        if (
            !hasFittedToView && containerSize.width > 0 && containerSize.height > 0 && image != null
        ) {
            state.fitToView(containerSize.width.toDouble(), containerSize.height.toDouble())
            hasFittedToView = true
        }
    }

    // Auto-dismiss the box-rejected message after a short delay
    LaunchedEffect(showBoxRejectedMessage) {
        if (showBoxRejectedMessage) {
            delay(2000)
            showBoxRejectedMessage = false
        }
    }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        OverviewTopBar(
            wizardMode = wizardMode,
            fourPointState = fourPointState,
            selectedBoxIndex = selectedBoxIndex,
            onDeleteSelected = { showDeleteConfirmDialog = true },
            onShowHelp = { showHelpDialog = true },
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            OverviewCanvasSection(
                state = state,
                image = image,
                containerSize = containerSize,
                wizardMode = wizardMode,
                fourPointState = fourPointState,
                zoomController = zoomController,
                focusRequester = focusRequester,
                onContainerSizeChanged = { containerSize = it },
                onBoxRejected = { showBoxRejectedMessage = true },
                onToSummary = onToSummary,
                onBack = onBack,
            )

            // Inline box-rejected banner
            if (showBoxRejectedMessage) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    tonalElevation = 4.dp,
                ) {
                    Text(
                        s.t(StringKey.WIZARD_BOX_TOO_SMALL),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }

        // Bottom controls
        OverviewControlsPanel(
            state = state,
            wizardMode = wizardMode,
            fourPointState = fourPointState,
            selectedBoxIndex = selectedBoxIndex,
            boxCount = boundingBoxList.size(),
            onBack = onBack,
            onToSummary = onToSummary,
            onSkipCurrentPhoto = skipCurrentPhoto,
            refocus = { focusRequester.requestFocus() },
            viewportWidth = containerSize.width.toDouble(),
            viewportHeight = containerSize.height.toDouble(),
        )
    }

    // Help dialog
    if (showHelpDialog) {
        KeyboardShortcutHelpDialog(onDismiss = { showHelpDialog = false })
    }

    // Delete confirmation dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(s.t(StringKey.WIZARD_DELETE_PHOTO)) },
            text = { Text(s.t(StringKey.WIZARD_DELETE_PHOTO_MESSAGE)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.removeSelectedBox()
                        showDeleteConfirmDialog = false
                    }
                ) {
                    Text(s.delete, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) { Text(s.cancel) }
            },
        )
    }
}

/** Top bar with mode indicator, delete, and help actions. */
@Composable
private fun OverviewTopBar(
    wizardMode: WizardMode,
    fourPointState: FourPointState,
    selectedBoxIndex: Int,
    onDeleteSelected: () -> Unit,
    onShowHelp: () -> Unit,
) {
    val s = strings()
    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                s.t(StringKey.WIZARD_SELECT_PHOTOS),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                // Mode indicator
                when (wizardMode) {
                    WizardMode.NORMAL -> { /* No mode indicator */ }
                    WizardMode.FOUR_POINT -> {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Text(
                                fourPointState.statusMessage(),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                    WizardMode.ADD_BOX -> { /* Handled via fourPointState */ }
                    WizardMode.REFINEMENT -> { /* Handled in refinement screen */ }
                }

                // Delete button (only when box selected)
                if (selectedBoxIndex >= 0) {
                    IconButton(onClick = onDeleteSelected) {
                        Icon(
                            Icons.Default.Delete,
                            s.t(StringKey.WIZARD_DELETE_SELECTED),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                // Help button
                IconButton(onClick = onShowHelp) {
                    Icon(Icons.Default.Info, s.t(StringKey.MENU_HELP))
                }
            }
        }
    }
}

/** Canvas section occupying the top half of the overview screen. */
@Composable
private fun OverviewCanvasSection(
    state: PhotoScanWizardState,
    image: BufferedImage?,
    containerSize: IntSize,
    wizardMode: WizardMode,
    fourPointState: FourPointState,
    zoomController: ZoomController,
    focusRequester: FocusRequester,
    onContainerSizeChanged: (IntSize) -> Unit,
    onBoxRejected: () -> Unit,
    onToSummary: () -> Unit,
    onBack: () -> Unit,
) {
    val s = strings()
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .fillMaxSize()
                .background(Color.DarkGray)
                .onSizeChanged { onContainerSizeChanged(it) }
                .focusRequester(focusRequester)
                .withWizardKeyboardShortcuts(
                    wizardState = state,
                    onProceed = onToSummary,
                    onCancel = onBack,
                    viewportCenterX = containerSize.width.toDouble() / 2,
                    viewportCenterY = containerSize.height.toDouble() / 2,
                )
    ) {
        if (image != null && containerSize.width > 0) {
            OverviewCanvas(
                state = state,
                image = image,
                containerSize = containerSize,
                wizardMode = wizardMode,
                fourPointState = fourPointState,
                onBoxRejected = onBoxRejected,
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator()
                Text(
                    s.t(StringKey.WIZARD_LOADING_IMAGE),
                    color = Color.White,
                    modifier = Modifier.padding(top = 60.dp),
                )
            }
        }

        // Zoom controls (top-right corner)
        ZoomControls(
            zoomController = zoomController,
            onZoomIn = {
                state.zoom.zoomIn(
                    containerSize.width.toDouble() / 2,
                    containerSize.height.toDouble() / 2,
                )
            },
            onZoomOut = {
                state.zoom.zoomOut(
                    containerSize.width.toDouble() / 2,
                    containerSize.height.toDouble() / 2,
                )
            },
            onFitToView = {
                state.fitToView(containerSize.width.toDouble(), containerSize.height.toDouble())
            },
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
        )

        // 4-Point status overlay (bottom center)
        if (wizardMode == WizardMode.FOUR_POINT) {
            FourPointStatusBar(
                state = fourPointState,
                onRemoveLast = { state.removeLastFourPoint() },
                onConfirm = { state.confirmFourPoint() },
                onCancel = { state.exitFourPointMode() },
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            )
        }
    }
}
