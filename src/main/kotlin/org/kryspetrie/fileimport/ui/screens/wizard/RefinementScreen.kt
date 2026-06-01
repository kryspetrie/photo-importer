package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
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
import org.kryspetrie.fileimport.infrastructure.wizard.PhotoScanWizardState
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
    val refinementBoxIndex by state.refinementBoxIndex.collectAsState()
    val selectedCorner by state.selectedCorner.collectAsState()
    val zoomController by state.zoomController.collectAsState()
    val boundingBoxList by state.boundingBoxList.collectAsState()
    val boxCount by remember { derivedStateOf { boundingBoxList.size() } }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
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
            state.syncDisplayBox()
        }
    }

    Scaffold(
        topBar = {
            RefinementTopBar(
                onDelete = {
                    if (refinementBoxIndex >= 0) {
                        state.removeBox(refinementBoxIndex)
                        state.exitRefinement()
                        onBack()
                    }
                },
                onUndo = { state.undo() },
                onRedo = { state.redo() },
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
                            state.zoomIn(
                                canvasSize.width.toDouble() / 2,
                                canvasSize.height.toDouble() / 2,
                            )
                        },
                        onZoomOut = {
                            state.zoomOut(
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
                    onDeselect = { state.deselectCorner() },
                    refocus = { focusRequester.requestFocus() },
                )
            }
        },
    )
}
