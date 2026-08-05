package org.kryspetrie.fileimport.ui.screens.wizard.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import java.awt.image.BufferedImage
import kotlinx.coroutines.CancellationException
import org.kryspetrie.fileimport.domain.model.FaceRegion
import org.kryspetrie.fileimport.domain.model.GeometryUtils
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.domain.model.RegionType
import org.kryspetrie.fileimport.domain.model.geometry.BoundingBoxList
import org.kryspetrie.fileimport.domain.port.FaceDetectionPort
import org.kryspetrie.fileimport.domain.port.FaceRegionTransformerPort
import org.kryspetrie.fileimport.domain.port.PerspectiveCorrectionPort
import org.kryspetrie.fileimport.infrastructure.adapter.correctPerspective
import org.kryspetrie.fileimport.infrastructure.adapter.toProcessedImage
import org.kryspetrie.fileimport.infrastructure.adapter.transformFaceRegionsFromSource
import org.kryspetrie.fileimport.infrastructure.logging.AppLogger
import org.kryspetrie.fileimport.ui.components.PreviewCache
import org.kryspetrie.fileimport.ui.screens.wizard.FaceSelectorOverlay
import org.kryspetrie.fileimport.ui.screens.wizard.boxToDetectedPhoto
import org.kryspetrie.fileimport.ui.wizard.state.FaceSize
import org.kryspetrie.fileimport.ui.wizard.state.PhotoScanWizardState

internal data class EditFaceFlowUiState(
    val faceSelectIndex: Int?,
    val pendingFaceCoords: Triple<Int, Double, Double>?,
    val showFaceNamePopup: Boolean,
    val faceNameInput: String,
    val selectedRegionType: RegionType,
    val selectedFaceSize: FaceSize,
    val inheritedFaceRegions: List<FaceRegion>,
    val autoStartNaming: Boolean,
    val nameSuggestions: Map<Int, String>,
)

internal data class EditFaceFlowCallbacks(
    val onFaceSelectIndexChange: (Int?) -> Unit,
    val onPendingFaceCoordsChange: (Triple<Int, Double, Double>?) -> Unit,
    val onShowFaceNamePopupChange: (Boolean) -> Unit,
    val onFaceNameInputChange: (String) -> Unit,
    val onRegionTypeChange: (RegionType) -> Unit,
    val onFaceSizeChange: (FaceSize) -> Unit,
    val onInheritedFaceRegionsChange: (List<FaceRegion>) -> Unit,
    val onAutoStartNamingChange: (Boolean) -> Unit,
)

@Composable
internal fun EditFaceFlowHost(
    uiState: EditFaceFlowUiState,
    callbacks: EditFaceFlowCallbacks,
    state: PhotoScanWizardState,
    image: BufferedImage,
    boundingBoxList: BoundingBoxList,
    photoConfigurations: Map<String, PhotoScanConfiguration>,
    previewCache: PreviewCache,
    perspectiveService: PerspectiveCorrectionPort,
    faceRegionTransformer: FaceRegionTransformerPort?,
    faceDetectionPort: FaceDetectionPort,
    appLogger: AppLogger,
) {
    val idx = uiState.faceSelectIndex
    val showFaceSelect = idx != null && idx < boundingBoxList.size()
    if (showFaceSelect && idx != null) {
        val box = boundingBoxList.boxes[idx]
        val config = photoConfigurations[box.id] ?: PhotoScanConfiguration()
        val fullPreview = previewCache.getFullPreview(image, box, config)
        val sourceFile = state.imageFile.value

        LaunchedEffect(idx, sourceFile) {
            callbacks.onInheritedFaceRegionsChange(
                if (faceRegionTransformer != null && sourceFile != null) {
                    try {
                        val marginFraction = state.exportSettings.exportMarginPercent.value
                        val perspectiveEnabled =
                            state.exportSettings.perspectiveCorrectionEnabled.value
                        val detectedPhoto =
                            boxToDetectedPhoto(box, perspectiveEnabled, config.rotationDegrees)
                        val marginedPhoto =
                            if (marginFraction > 0.0) {
                                GeometryUtils.applyMargin(detectedPhoto, marginFraction)
                            } else {
                                detectedPhoto
                            }
                        val corrected = perspectiveService.correctPerspective(image, marginedPhoto)
                        val existingNames = config.faceRegions.map { it.name }.toSet()
                        faceRegionTransformer
                            .transformFaceRegionsFromSource(
                                sourceFile = sourceFile,
                                detectedPhoto = marginedPhoto,
                                outputWidth = corrected.width,
                                outputHeight = corrected.height,
                                sourceWidth = image.width,
                                sourceHeight = image.height,
                                marginFraction = marginFraction,
                            )
                            .filter { it.name !in existingNames }
                            .map { region ->
                                FaceRegion(
                                    name = region.name,
                                    type = region.type,
                                    x = region.x,
                                    y = region.y,
                                    w = region.w,
                                    h = region.h,
                                )
                            }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        appLogger.warn("Face detection skipped: ${e.message}")
                        emptyList()
                    }
                } else {
                    emptyList()
                }
            )
        }

        if (fullPreview != null) {
            FaceSelectorOverlay(
                fullPreview = fullPreview,
                idx = idx,
                photoConfig = config,
                faceRegionMutator = state.faceRegions,
                selectedRegionType = uiState.selectedRegionType,
                selectedFaceSize = uiState.selectedFaceSize,
                onRegionTypeChange = callbacks.onRegionTypeChange,
                onFaceSizeChange = callbacks.onFaceSizeChange,
                onPlaceFace = { normX, normY ->
                    callbacks.onPendingFaceCoordsChange(Triple(idx, normX, normY))
                    callbacks.onShowFaceNamePopupChange(true)
                    callbacks.onFaceNameInputChange("")
                },
                onDismiss = {
                    callbacks.onFaceSelectIndexChange(null)
                    callbacks.onAutoStartNamingChange(false)
                },
                inheritedFaceRegions = uiState.inheritedFaceRegions,
                autoStartNaming = uiState.autoStartNaming,
                nameSuggestions = uiState.nameSuggestions,
                onNameConfirmed = { _, _ -> },
                onAutoDetectFaces =
                    if (faceDetectionPort.isFaceDetectionAvailable()) {
                        {
                            try {
                                val detections =
                                    faceDetectionPort.detectFaces(fullPreview.toProcessedImage())
                                if (detections.isNotEmpty()) {
                                    val imgW = fullPreview.width.toDouble()
                                    val imgH = fullPreview.height.toDouble()
                                    val detectedRegions =
                                        detections.map { detection ->
                                            val centerX =
                                                ((detection.x1 + detection.x2) / 2.0 / imgW)
                                                    .coerceIn(0.0, 1.0)
                                            val centerY =
                                                ((detection.y1 + detection.y2) / 2.0 / imgH)
                                                    .coerceIn(0.0, 1.0)
                                            val width =
                                                ((detection.x2 - detection.x1) / imgW).coerceIn(
                                                    0.01,
                                                    1.0,
                                                )
                                            FaceRegion(
                                                name = "",
                                                type = RegionType.FACE.mwgRsValue,
                                                x = centerX,
                                                y = centerY,
                                                w = width,
                                                h = width,
                                            )
                                        }
                                    state.faceRegions.addDetectedFaceRegions(idx, detectedRegions)
                                    callbacks.onAutoStartNamingChange(true)
                                }
                            } catch (ex: Exception) {
                                appLogger.warn("Face detection failed: ${ex.message}")
                            }
                        }
                    } else {
                        null
                    },
            )
        }
    }

    val pendingCoords = uiState.pendingFaceCoords
    if (uiState.showFaceNamePopup && pendingCoords != null) {
        EditDialog(
            onDismissRequest = {
                callbacks.onShowFaceNamePopupChange(false)
                callbacks.onPendingFaceCoordsChange(null)
            }
        ) {
            FaceNameEntryPanel(
                faceNameInput = uiState.faceNameInput,
                onFaceNameInputChange = callbacks.onFaceNameInputChange,
                selectedRegionType = uiState.selectedRegionType,
                selectedFaceSize = uiState.selectedFaceSize,
                onConfirm = {
                    if (uiState.faceNameInput.isNotBlank()) {
                        val (photoIdx, normX, normY) = pendingCoords
                        state.faceRegions.addFaceRegion(
                            photoIdx,
                            uiState.faceNameInput.trim(),
                            normX,
                            normY,
                            uiState.selectedRegionType,
                            uiState.selectedFaceSize,
                        )
                    }
                    callbacks.onShowFaceNamePopupChange(false)
                    callbacks.onPendingFaceCoordsChange(null)
                    callbacks.onFaceNameInputChange("")
                },
                onCancel = {
                    callbacks.onShowFaceNamePopupChange(false)
                    callbacks.onPendingFaceCoordsChange(null)
                    callbacks.onFaceNameInputChange("")
                },
            )
        }
    }
}
