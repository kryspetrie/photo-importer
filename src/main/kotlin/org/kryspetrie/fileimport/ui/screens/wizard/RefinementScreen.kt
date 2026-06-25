package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.ui.wizard.state.PhotoScanWizardState
import org.kryspetrie.fileimport.ui.screens.wizard.overview.ZoomControls
import org.kryspetrie.fileimport.ui.screens.wizard.refinement.RefinementCanvas
import org.kryspetrie.fileimport.ui.screens.wizard.refinement.RefinementControls
import org.kryspetrie.fileimport.ui.screens.wizard.refinement.RefinementTopBar

/**
 * Refinement screen showing a zoomed view of a single bounding box for precise corner adjustment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefinementScreen(
    state: PhotoScanWizardState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // State flows
    val image by state.image.collectAsState()
    val refinementBoxIndex by state.boxes.refinementBoxIndex.collectAsState()
    val selectedCorner by state.boxes.selectedCorner.collectAsState()
    val zoomController by state.zoom.zoomController.collectAsState()
    val boundingBoxList by state.boundingBoxList.collectAsState()
    val boxCount by remember { derivedStateOf { boundingBoxList.size() } }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // Get current box
    val currentBox =
        remember(refinementBoxIndex, boundingBoxList) {
            if (refinementBoxIndex >= 0 && refinementBoxIndex < boundingBoxList.size()) {
                boundingBoxList.boxes[refinementBoxIndex]
            } else null
        }

    // Fit to box when canvas size is known
    androidx.compose.runtime.LaunchedEffect(canvasSize, refinementBoxIndex) {
        if (canvasSize.width > 0 && canvasSize.height > 0) {
            state.fitToBox(canvasSize.width.toDouble(), canvasSize.height.toDouble())
            state.boxes.syncDisplayBox()
        }
    }

    Scaffold(
        topBar = {
            RefinementTopBar(
                onDelete = {
                    if (refinementBoxIndex >= 0) {
                        showDeleteConfirmDialog = true
                    }
                },
                onUndo = { state.boxes.undo() },
                onRedo = { state.boxes.redo() },
                onShowHelp = { showHelpDialog = true },
                refocus = { focusRequester.requestFocus() },
            )
        },
        content = { paddingValues ->
            Column(
                modifier = modifier.fillMaxSize().padding(paddingValues),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Main canvas area
                Box(
                    modifier =
                        Modifier.weight(1f)
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .withWizardKeyboardShortcuts(
                                wizardState = state,
                                onProceed = onBack,
                                onCancel = onBack,
                                viewportCenterX = canvasSize.width.toDouble() / 2,
                                viewportCenterY = canvasSize.height.toDouble() / 2,
                            )
                ) {
                    // Auto-focus canvas so keyboard shortcuts work immediately
                    LaunchedEffect(Unit) { focusRequester.requestFocus() }

                    RefinementCanvas(
                        state = state,
                        image = image,
                        box = currentBox,
                        boxIndex = refinementBoxIndex,
                        canvasSize = canvasSize,
                        onCanvasSizeChanged = { canvasSize = it },
                        modifier = Modifier.fillMaxSize(),
                    )

                    // Zoom controls (top-right corner)
                    ZoomControls(
                        zoomController = zoomController,
                        onZoomIn = {
                            state.zoom.zoomIn(
                                canvasSize.width.toDouble() / 2,
                                canvasSize.height.toDouble() / 2,
                            )
                        },
                        onZoomOut = {
                            state.zoom.zoomOut(
                                canvasSize.width.toDouble() / 2,
                                canvasSize.height.toDouble() / 2,
                            )
                        },
                        onFitToView = {
                            if (canvasSize.width > 0) {
                                state.fitToBox(
                                    canvasSize.width.toDouble(),
                                    canvasSize.height.toDouble(),
                                )
                            }
                        },
                        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                    )
                }

                // Bottom controls
                RefinementControls(
                    currentIndex = refinementBoxIndex,
                    totalCount = boxCount,
                    selectedCorner = selectedCorner,
                    onPrevious = { state.previousBox() },
                    onNext = { state.nextBox() },
                    onDeselect = { state.boxes.deselectCorner() },
                    refocus = { focusRequester.requestFocus() },
                )
            }
        },
    )

    // Keyboard shortcut help dialog
    if (showHelpDialog) {
        KeyboardShortcutHelpDialog(
            onDismiss = { showHelpDialog = false },
            context = ShortcutContext.CANVAS,
        )
    }

    // Delete confirmation dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Photo") },
            text = {
                Text(
                    "Remove this photo box? This cannot be undone, but you can use Undo (Ctrl+Z) to restore it."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.boxes.removeBox(refinementBoxIndex)
                        state.exitRefinement()
                        showDeleteConfirmDialog = false
                        onBack()
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
