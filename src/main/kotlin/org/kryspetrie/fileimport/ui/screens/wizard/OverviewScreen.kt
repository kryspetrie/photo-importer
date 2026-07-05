package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import org.kryspetrie.fileimport.ui.components.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import org.kryspetrie.fileimport.ui.wizard.state.FourPointState
import org.kryspetrie.fileimport.ui.wizard.state.PhotoScanWizardState

import org.kryspetrie.fileimport.ui.wizard.state.WizardMode
import org.kryspetrie.fileimport.ui.wizard.state.ZoomController

import org.kryspetrie.fileimport.ui.screens.wizard.overview.FourPointStatusBar
import org.kryspetrie.fileimport.ui.screens.wizard.overview.OverviewCanvas
import org.kryspetrie.fileimport.ui.screens.wizard.overview.OverviewControlsPanel
import org.kryspetrie.fileimport.ui.screens.wizard.overview.ZoomControls

/**
 * Overview screen showing the full scanned image with all detected bounding boxes. Users can
 * select, add, remove, and navigate to refinement for individual boxes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    state: PhotoScanWizardState,
    onBack: () -> Unit,
    onToSummary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Collect state flows
    val wizardMode by state.wizardMode.collectAsState()
    val fourPointState by state.fourPointState.collectAsState()
    val boundingBoxList by state.boundingBoxList.collectAsState()
    val selectedBoxIndex by state.boxes.selectedBoxIndex.collectAsState()
    val zoomController by state.zoom.zoomController.collectAsState()
    val image by state.image.collectAsState()

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showBoxRejectedMessage by remember { mutableStateOf(false) }
    var hasFittedToView by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Focus requester for auto-focus on load (so keyboard works immediately without clicking)
    // Also used to re-grab focus after button clicks steal it from the canvas
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

    // Launch snackbar when box is rejected
    LaunchedEffect(showBoxRejectedMessage) {
        if (showBoxRejectedMessage) {
            snackbarHostState.showSnackbar(
                message = "Box too small - image must be at least 100x67 pixels for a box",
                duration = SnackbarDuration.Short,
            )
            showBoxRejectedMessage = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            OverviewTopBar(
                wizardMode = wizardMode,
                fourPointState = fourPointState,
                selectedBoxIndex = selectedBoxIndex,
                onDeleteSelected = { showDeleteConfirmDialog = true },
                onShowHelp = { showHelpDialog = true },
            )
        },
        content = { paddingValues ->
            Column(
                modifier =
                    modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .background(MaterialTheme.colorScheme.surface)
            ) {
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

                // BOTTOM HALF: All controls consolidated
                OverviewControlsPanel(
                    state = state,
                    wizardMode = wizardMode,
                    fourPointState = fourPointState,
                    selectedBoxIndex = selectedBoxIndex,
                    boxCount = boundingBoxList.size(),
                    onBack = onBack,
                    onToSummary = onToSummary,
                    refocus = { focusRequester.requestFocus() },
                    viewportWidth = containerSize.width.toDouble(),
                    viewportHeight = containerSize.height.toDouble(),
                )
            }
        },
    )

    // Help dialog
    if (showHelpDialog) {
        KeyboardShortcutHelpDialog(onDismiss = { showHelpDialog = false })
    }

    // Delete confirmation dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Photo") },
            text = {
                Text(
                    "Remove this photo box? This cannot be undone, " +
                        "but you can use Undo (Ctrl+Z) to restore it."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.removeSelectedBox()
                        showDeleteConfirmDialog = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("Cancel") }
            },
        )
    }
}

/** Top app bar with mode indicator, delete, and help actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverviewTopBar(
    wizardMode: WizardMode,
    fourPointState: FourPointState,
    selectedBoxIndex: Int,
    onDeleteSelected: () -> Unit,
    onShowHelp: () -> Unit,
) {
    TopAppBar(
        title = { Text("Select Photos", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {},
        actions = {
            // Mode indicator
            when (wizardMode) {
                WizardMode.NORMAL -> {
                    // No mode indicator
                }
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
                WizardMode.ADD_BOX -> {
                    // Handled via fourPointState
                }
                WizardMode.REFINEMENT -> {
                    // Handled in refinement screen
                }
            }

            // Delete button (only when box selected)
            if (selectedBoxIndex >= 0) {
                IconButton(onClick = onDeleteSelected) {
                    Icon(
                        Icons.Default.Delete,
                        "Delete selected",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            // Help button
            IconButton(onClick = onShowHelp) { Icon(Icons.Default.Info, "Help") }
        },
    )
}

/** Canvas section occupying the top half of the overview screen. */
@Composable
private fun ColumnScope.OverviewCanvasSection(
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
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .weight(1f)
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
                    "Loading image...",
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
