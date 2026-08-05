package org.kryspetrie.fileimport.ui.screens.metadataeditor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import javax.imageio.ImageIO
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.kryspetrie.fileimport.application.OrientationCorrectionService
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.RotationAngle
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.domain.port.ModelDownloadPort
import org.kryspetrie.fileimport.domain.port.ModelDownloadState

/**
 * Detect orientation for all files in the current folder and show the rotation preview overlay. If
 * the orientation model is not available, requests a download instead.
 */
internal fun MetadataEditorViewModel.startBatchOrientationDetection(scope: CoroutineScope) {
    if (state.files.isEmpty()) {
        state.showError(t(StringKey.META_ERROR_NO_FILES))
        return
    }
    val modelAvailable = isOrientationModelAvailable || orientationCorrection.isAvailable()
    if (!modelAvailable) {
        requestModelDownload()
        return
    }
    isDetectingOrientation = true
    orientationResults = emptyMap()
    rotationExcludedPaths = emptySet()
    orientationDetectCurrent = 0
    orientationDetectTotal = state.files.size
    scope.launch {
        try {
            val results = mutableMapOf<String, OrientationCorrectionService.CorrectionResult>()
            for ((index, file) in state.files.withIndex()) {
                orientationDetectCurrent = index + 1
                try {
                    val img =
                        withContext(dispatcherProvider.io) {
                            imageProcessing.readImage(FilePath(file.absolutePath))
                        }
                    if (img != null) {
                        val result = orientationCorrection.detectOnly(img)
                        if (result != null) {
                            results[file.absolutePath] = result
                        }
                    }
                } catch (_: CancellationException) {
                    throw CancellationException()
                } catch (_: Exception) {
                    // Skip files that fail to load or detect
                }
            }
            if (results.isEmpty()) {
                state.showError(t(StringKey.META_ERROR_NO_ORIENTATION))
            } else {
                orientationResults = results
                // Pre-check: exclude files where no rotation is needed (NONE)
                rotationExcludedPaths =
                    results.filter { it.value.nearestRotation == RotationAngle.NONE }.keys.toSet()
                rotationPreviewIndex =
                    state.files.indexOfFirst { it.absolutePath in results }.coerceAtLeast(0)
                showRotationPreview = true
            }
        } catch (_: CancellationException) {
            // Cancellation must propagate
        } catch (e: Exception) {
            state.showError(
                t(StringKey.META_ERROR_ORIENTATION_DETECT, "message" to (e.message ?: ""))
            )
        } finally {
            isDetectingOrientation = false
            orientationDetectCurrent = 0
            orientationDetectTotal = 0
        }
    }
}

/** Toggle whether a file is excluded from rotation correction. */
internal fun MetadataEditorViewModel.toggleRotationExclusion(filePath: String) {
    rotationExcludedPaths =
        if (filePath in rotationExcludedPaths) rotationExcludedPaths - filePath
        else rotationExcludedPaths + filePath
}

/** Select all files for rotation (clear exclusions). */
internal fun MetadataEditorViewModel.selectAllForRotation() {
    rotationExcludedPaths = emptySet()
}

/** Deselect all files for rotation (exclude all). */
internal fun MetadataEditorViewModel.deselectAllForRotation() {
    rotationExcludedPaths = orientationResults.keys
}

/** Set the preview index for the rotation overlay, loading the full-resolution image. */
internal fun MetadataEditorViewModel.updateRotationPreviewIndex(
    index: Int,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    rotationPreviewIndex = index.coerceIn(-1, state.files.size - 1)
    // Load the full-resolution image for preview
    val file = if (index in state.files.indices) state.files[index] else null
    if (file != null) {
        scope.launch {
            try {
                val img = withContext(dispatcherProvider.io) { ImageIO.read(file) }
                rotationPreviewImage = img
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (_: Exception) {
                rotationPreviewImage = null
            }
        }
    } else {
        rotationPreviewImage = null
    }
}

/**
 * Apply rotation correction to all checked (non-excluded) files. Updates each file's
 * rotationDegrees in the bulk edit state.
 */
internal fun MetadataEditorViewModel.applyBatchRotationCorrection() {
    var appliedCount = 0
    for ((filePath, result) in orientationResults) {
        if (filePath in rotationExcludedPaths) continue
        if (result.nearestRotation == RotationAngle.NONE) continue
        val fileIndex = state.files.indexOfFirst { it.absolutePath == filePath }
        if (fileIndex < 0) continue
        val nearestCorrectionDeg =
            when (result.nearestRotation) {
                RotationAngle.NONE -> 0
                RotationAngle.CW_90 -> 90
                RotationAngle.CW_180 -> 180
                RotationAngle.CCW_90 -> 270
            }
        state.updateConfig(fileIndex) { config ->
            val correctedRotation = (config.rotationDegrees + nearestCorrectionDeg) % 360
            config.copy(
                rotationDegrees = correctedRotation,
                faceRegions =
                    config.faceRegions.map { region ->
                        when (result.nearestRotation) {
                            RotationAngle.CW_90 -> region.rotate90CW()
                            RotationAngle.CCW_90 -> region.rotate90CCW()
                            RotationAngle.CW_180 -> region.rotate180()
                            RotationAngle.NONE -> region
                        }
                    },
            )
        }
        appliedCount++
    }
    showRotationPreview = false
    orientationResults = emptyMap()
    rotationExcludedPaths = emptySet()
    rotationPreviewIndex = -1
    rotationPreviewImage = null
    state.showInfo(t(StringKey.META_APPLIED_ROTATION_N, "count" to appliedCount.toString()))
}

/** Dismiss the rotation preview overlay without applying changes. */
internal fun MetadataEditorViewModel.dismissRotationPreview() {
    showRotationPreview = false
    orientationResults = emptyMap()
    rotationExcludedPaths = emptySet()
    rotationPreviewIndex = -1
    rotationPreviewImage = null
}

// ── Model download ────────────────────────────────────────────

/** Download the orientation detection model. */
internal fun MetadataEditorViewModel.downloadOrientationModel(scope: CoroutineScope) {
    modelDownloadState = ModelDownloadState.Connecting
    scope.launch {
        modelDownloadPort.downloadModel(ModelDownloadPort.ORIENTATION_MODEL_ID).collect { state ->
            modelDownloadState = state
            if (state is ModelDownloadState.Completed) {
                showModelDownloadDialog = false
                modelDownloadState = null
            }
            if (state is ModelDownloadState.Failed || state is ModelDownloadState.Cancelled) {
                // Keep dialog open so user can see the error / retry
            }
        }
    }
}

/** Cancel the orientation model download, or dismiss if no download is active. */
internal fun MetadataEditorViewModel.cancelModelDownload() {
    val currentState = modelDownloadState
    if (
        currentState is ModelDownloadState.Downloading ||
            currentState is ModelDownloadState.Connecting
    ) {
        modelDownloadPort.cancelDownload(ModelDownloadPort.ORIENTATION_MODEL_ID)
        modelDownloadState = ModelDownloadState.Cancelled
    }
    showModelDownloadDialog = false
    modelDownloadState = null
}

/** Dismiss the model download dialog. */
internal fun MetadataEditorViewModel.dismissModelDownloadDialog() {
    showModelDownloadDialog = false
    modelDownloadState = null
}

/** Show the model download dialog. */
internal fun MetadataEditorViewModel.requestModelDownload() {
    showModelDownloadDialog = true
    modelDownloadState = null
}
