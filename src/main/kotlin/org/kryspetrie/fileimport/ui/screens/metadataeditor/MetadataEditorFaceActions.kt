package org.kryspetrie.fileimport.ui.screens.metadataeditor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.FaceRegion
import org.kryspetrie.fileimport.infrastructure.adapter.toProcessedImage

// ── Face name popup ──────────────────────────────────────────

internal fun MetadataEditorViewModel.showFaceNamePopupFor(coords: Triple<Int, Double, Double>) {
    pendingFaceCoords = coords
    showFaceNamePopup = true
}

internal fun MetadataEditorViewModel.confirmFaceName() {
    if (faceNameInput.isNotBlank() && pendingFaceCoords != null) {
        val (_, normX, normY) = pendingFaceCoords!!
        state.updateSelectedConfig { config ->
            val newRegions =
                config.faceRegions +
                    FaceRegion(
                        name = faceNameInput.trim(),
                        type = selectedRegionType.mwgRsValue,
                        x = normX,
                        y = normY,
                        w = 0.1,
                        h = 0.1,
                    )
            config.copy(
                faceRegions = newRegions,
                subjects =
                    MetadataEditorPanelController.mergeFaceNamesIntoSubjects(
                        config.subjects,
                        newRegions,
                    ),
            )
        }
        editState.subjects = state.selectedConfig.subjects
    }
    dismissFaceNamePopup()
}

internal fun MetadataEditorViewModel.requestKeywordsFocus() {
    keywordsFocusTrigger++
}

internal fun MetadataEditorViewModel.toggleFaceTagging() {
    showFaceTagging = !showFaceTagging
}

internal fun MetadataEditorViewModel.dismissFaceTagging() {
    showFaceTagging = false
    syncEditStateFromSelectedConfig()
}

internal fun MetadataEditorViewModel.syncEditStateFromSelectedConfig() {
    editState.subjects = state.selectedConfig.subjects
}

/** Optional auto-detect callback for [FaceSelectorOverlay], or null when unavailable. */
internal fun MetadataEditorViewModel.autoDetectFacesCallback(scope: CoroutineScope): (() -> Unit)? {
    val image = currentImage ?: return null
    if (!faceDetectionPort.isFaceDetectionAvailable()) return null
    return {
        scope.launch {
            try {
                val detections = faceDetectionPort.detectFaces(image.toProcessedImage())
                if (detections.isEmpty()) return@launch
                val imgW = image.width.toDouble()
                val imgH = image.height.toDouble()
                val detectedRegions =
                    detections.map { det ->
                        val centerX = ((det.x1 + det.x2) / 2.0 / imgW).coerceIn(0.0, 1.0)
                        val centerY = ((det.y1 + det.y2) / 2.0 / imgH).coerceIn(0.0, 1.0)
                        val width = ((det.x2 - det.x1) / imgW).coerceIn(0.01, 1.0)
                        FaceRegion(
                            name = "",
                            type =
                                org.kryspetrie.fileimport.domain.model.RegionType.FACE.mwgRsValue,
                            x = centerX,
                            y = centerY,
                            w = width,
                            h = width,
                        )
                    }
                faceRegionMutator.addDetectedFaceRegions(state.selectedIndex, detectedRegions)
                syncEditStateFromSelectedConfig()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Detection failed — user can still place faces manually
            }
        }
    }
}

internal fun MetadataEditorViewModel.removeFace(faceIndex: Int) {
    state.updateSelectedConfig { config ->
        config.copy(
            faceRegions = config.faceRegions.filterIndexed { index, _ -> index != faceIndex }
        )
    }
}

internal fun MetadataEditorViewModel.clearAllFaces() {
    state.updateSelectedConfig { it.copy(faceRegions = emptyList()) }
}

internal fun MetadataEditorViewModel.placeFaceAt(normalizedX: Double, normalizedY: Double) {
    showFaceNamePopupFor(Triple(state.selectedIndex, normalizedX, normalizedY))
}

internal fun MetadataEditorViewModel.toggleBrowserDrawer(onSettingsChange: (AppSettings) -> Unit) {
    browserDrawerOpen = !browserDrawerOpen
    onSettingsChange(
        currentSettings.withMetadataEditorLayoutPreferences(
            currentSettings.metadataEditorLayoutPreferences.withBrowserDrawerOpen(browserDrawerOpen)
        )
    )
}

internal fun MetadataEditorViewModel.updateBrowserPaneWidth(
    widthDp: Int,
    onSettingsChange: (AppSettings) -> Unit,
) {
    onSettingsChange(
        currentSettings.withMetadataEditorLayoutPreferences(
            currentSettings.metadataEditorLayoutPreferences.withBrowserPaneWidthDp(widthDp)
        )
    )
}

internal fun MetadataEditorViewModel.updatePreviewPaneWeight(
    weight: Float,
    onSettingsChange: (AppSettings) -> Unit,
) {
    onSettingsChange(
        currentSettings.withMetadataEditorLayoutPreferences(
            currentSettings.metadataEditorLayoutPreferences.withPreviewPaneWeight(weight)
        )
    )
}

internal fun MetadataEditorViewModel.handleMetadataShortcut(
    action: MetadataEditorShortcutAction,
    onSettingsChange: (AppSettings) -> Unit,
    scope: CoroutineScope,
): Boolean =
    when (action) {
        MetadataEditorShortcutAction.APPLY_MULTI_EDIT -> {
            if (isMultiEditMode) {
                applyMultiEdit(onSettingsChange)
                true
            } else false
        }
        MetadataEditorShortcutAction.LOCATION_PICKER -> {
            val indices =
                if (isMultiEditMode && selectedIndices.isNotEmpty()) selectedIndices.toList()
                else listOf(state.selectedIndex).filter { it >= 0 }
            if (indices.isNotEmpty()) {
                requestLocationPicker(indices)
                true
            } else false
        }
        MetadataEditorShortcutAction.TOGGLE_FACE_TAGGING -> {
            if (!isMultiEditMode || selectedIndices.size <= 1) {
                toggleFaceTagging()
                true
            } else false
        }
        MetadataEditorShortcutAction.TOGGLE_BROWSER_DRAWER -> {
            toggleBrowserDrawer(onSettingsChange)
            true
        }
        MetadataEditorShortcutAction.FOCUS_KEYWORDS -> {
            requestKeywordsFocus()
            true
        }
    }

internal fun MetadataEditorViewModel.dismissFaceNamePopup() {
    showFaceNamePopup = false
    pendingFaceCoords = null
    faceNameInput = ""
}
