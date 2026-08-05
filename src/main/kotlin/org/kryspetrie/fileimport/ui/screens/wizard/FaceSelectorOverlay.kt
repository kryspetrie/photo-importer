@file:Suppress("TooManyFunctions", "MagicNumber")

package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Pets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.awt.image.BufferedImage
import org.kryspetrie.fileimport.domain.model.FaceRegion
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.domain.model.RegionType
import org.kryspetrie.fileimport.ui.shared.face.FaceRegionMutator
import org.kryspetrie.fileimport.ui.wizard.state.FaceSize

/** Returns an appropriate Material icon for the given [RegionType]. */
fun regionTypeIcon(type: RegionType): ImageVector =
    when (type) {
        RegionType.FACE -> Icons.Default.Face
        RegionType.PET -> Icons.Default.Pets
        RegionType.BODY -> Icons.Default.Accessibility
        RegionType.OBJECT -> Icons.Default.Category
    }

/**
 * The face selection overlay, drawn inside a Dialog.
 *
 * Interaction model:
 * - Click on empty space to place a region and immediately name it
 * - Drag a region to move it; click its delete button to remove it
 * - Enter/Tab advances naming, Escape closes naming or the overlay
 * - Delete, or Backspace with an empty name, removes the selected region
 *
 * [onPlaceFace] remains part of the public API for the legacy external popup.
 */
@Composable
fun FaceSelectorOverlay(
    fullPreview: BufferedImage,
    idx: Int,
    photoConfig: PhotoScanConfiguration,
    faceRegionMutator: FaceRegionMutator,
    selectedRegionType: RegionType,
    selectedFaceSize: FaceSize,
    onRegionTypeChange: (RegionType) -> Unit,
    onFaceSizeChange: (FaceSize) -> Unit,
    onPlaceFace: (Double, Double) -> Unit,
    onDismiss: () -> Unit,
    inheritedFaceRegions: List<FaceRegion>,
    onAutoDetectFaces: (() -> Unit)? = null,
    autoStartNaming: Boolean = false,
    nameSuggestions: Map<Int, String> = emptyMap(),
    onNameConfirmed: ((faceIndex: Int, name: String) -> Unit)? = null,
) {
    val faceRegions = photoConfig.faceRegions
    var namingFaceIndex by remember { mutableStateOf(-1) }
    var namingInput by remember { mutableStateOf("") }
    val namingFocusRequester = remember { FocusRequester() }

    fun commitName() {
        if (namingFaceIndex in faceRegions.indices && namingInput.isNotBlank()) {
            val name = namingInput.trim()
            faceRegionMutator.updateFaceRegionName(idx, namingFaceIndex, name)
            onNameConfirmed?.invoke(namingFaceIndex, name)
        }
    }

    fun advanceToNextUnnamedFace(): Boolean {
        if (faceRegions.isEmpty()) {
            namingFaceIndex = -1
            namingInput = ""
            return false
        }
        commitName()
        val isFaceNamed: (Int) -> Boolean = { i ->
            if (i == namingFaceIndex) {
                namingInput.isNotBlank()
            } else {
                faceRegions.getOrNull(i)?.name?.isNotBlank() == true
            }
        }
        for (i in (namingFaceIndex + 1) until faceRegions.size) {
            if (!isFaceNamed(i)) {
                namingFaceIndex = i
                namingInput = ""
                return true
            }
        }
        for (i in 0 until namingFaceIndex) {
            if (!isFaceNamed(i)) {
                namingFaceIndex = i
                namingInput = ""
                return true
            }
        }
        namingFaceIndex = -1
        namingInput = ""
        return false
    }

    fun skipCurrentFace() {
        if (namingFaceIndex !in faceRegions.indices) return
        var nextUnnamed: Int? = null
        for (i in (namingFaceIndex + 1) until faceRegions.size) {
            if (faceRegions[i].name.isBlank()) {
                nextUnnamed = i - 1
                break
            }
        }
        if (nextUnnamed == null) {
            for (i in 0 until namingFaceIndex) {
                if (faceRegions[i].name.isBlank()) {
                    nextUnnamed = i
                    break
                }
            }
        }
        faceRegionMutator.removeFaceRegion(idx, namingFaceIndex)
        if (nextUnnamed != null) {
            namingFaceIndex = nextUnnamed
            namingInput = ""
        } else {
            namingFaceIndex = -1
            namingInput = ""
        }
    }

    fun goToPreviousFace() {
        if (faceRegions.isEmpty()) {
            namingFaceIndex = -1
            return
        }
        commitName()
        val prevIdx = if (namingFaceIndex <= 0) faceRegions.size - 1 else namingFaceIndex - 1
        namingFaceIndex = prevIdx.coerceIn(0, faceRegions.size - 1)
        namingInput = faceRegions.getOrNull(namingFaceIndex)?.name ?: ""
    }

    fun deleteSelectedFace() {
        if (namingFaceIndex !in faceRegions.indices) return
        val wasLastFace = faceRegions.size <= 1
        val newFacesCount = faceRegions.size - 1
        val shiftedName = faceRegions.getOrNull(namingFaceIndex + 1)?.name ?: ""
        faceRegionMutator.removeFaceRegion(idx, namingFaceIndex)
        if (wasLastFace) {
            namingFaceIndex = -1
            namingInput = ""
        } else {
            namingFaceIndex = namingFaceIndex.coerceAtMost(newFacesCount - 1)
            namingInput = shiftedName
        }
    }

    LaunchedEffect(namingFaceIndex) {
        if (namingFaceIndex >= 0) namingFocusRequester.requestFocus()
    }

    // Deliberately mirrors the original one-way auto-start behavior during composition.
    if (autoStartNaming && namingFaceIndex < 0) {
        val firstUnnamed = faceRegions.indexOfFirst { it.name.isBlank() }
        if (firstUnnamed >= 0) {
            namingFaceIndex = firstUnnamed
            namingInput = faceRegions[firstUnnamed].name
        }
    }

    val unnamedCount = faceRegions.count { it.name.isBlank() }
    val isLastUnnamed = namingFaceIndex >= 0 && unnamedCount <= 1

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            FaceSelectorSidebar(
                faceRegions = faceRegions,
                inheritedFaceRegions = inheritedFaceRegions,
                namingFaceIndex = namingFaceIndex,
                selectedRegionType = selectedRegionType,
                selectedFaceSize = selectedFaceSize,
                onRegionTypeChange = onRegionTypeChange,
                onFaceSizeChange = onFaceSizeChange,
                onAutoDetectFaces = onAutoDetectFaces,
                onClearAll = {
                    faceRegionMutator.clearAllFaceRegions(idx)
                    namingFaceIndex = -1
                    namingInput = ""
                },
                onAdoptRegion = { region ->
                    faceRegionMutator.addFaceRegion(
                        idx,
                        region.name,
                        region.x,
                        region.y,
                        RegionType.fromMwgRs(region.type),
                    )
                },
                onDismiss = onDismiss,
            )
            FaceSelectorCanvas(
                fullPreview = fullPreview,
                idx = idx,
                faceRegions = faceRegions,
                inheritedFaceRegions = inheritedFaceRegions,
                faceRegionMutator = faceRegionMutator,
                selectedRegionType = selectedRegionType,
                selectedFaceSize = selectedFaceSize,
                namingFaceIndex = namingFaceIndex,
                namingInput = namingInput,
                namingFocusRequester = namingFocusRequester,
                isLastUnnamed = isLastUnnamed,
                nameSuggestions = nameSuggestions,
                onNamingInputChange = { namingInput = it },
                onSelectNaming = { faceIndex, name ->
                    namingFaceIndex = faceIndex
                    namingInput = name
                },
                onTapDelete = { faceIndex ->
                    faceRegionMutator.removeFaceRegion(idx, faceIndex)
                    if (namingFaceIndex == faceIndex) {
                        namingFaceIndex = -1
                        namingInput = ""
                    } else if (namingFaceIndex > faceIndex) {
                        namingFaceIndex--
                    }
                },
                onEscape = {
                    if (namingFaceIndex >= 0) {
                        commitName()
                        namingFaceIndex = -1
                        namingInput = ""
                    } else {
                        onDismiss()
                    }
                },
                onNext = { advanceToNextUnnamedFace() },
                onPrevious = { goToPreviousFace() },
                onKeyboardDelete = { deleteSelectedFace() },
                onDeleteEmpty = { deleteSelectedFace() },
                onSaveAndAdvance = {
                    // Preserve the original explicit commit followed by the advancing commit.
                    commitName()
                    advanceToNextUnnamedFace()
                },
                onSkip = { skipCurrentFace() },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    @Suppress("UNUSED_EXPRESSION") onPlaceFace
}
