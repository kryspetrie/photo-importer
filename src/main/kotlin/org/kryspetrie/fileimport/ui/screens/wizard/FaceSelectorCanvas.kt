@file:Suppress("MagicNumber")

package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import java.awt.Cursor
import java.awt.image.BufferedImage
import kotlin.math.pow
import kotlin.math.sqrt
import org.kryspetrie.fileimport.domain.model.FaceRegion
import org.kryspetrie.fileimport.domain.model.RegionType
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.strings
import org.kryspetrie.fileimport.ui.shared.face.FaceRegionMutator
import org.kryspetrie.fileimport.ui.wizard.state.FaceSize

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun FaceSelectorCanvas(
    fullPreview: BufferedImage,
    idx: Int,
    faceRegions: List<FaceRegion>,
    inheritedFaceRegions: List<FaceRegion>,
    faceRegionMutator: FaceRegionMutator,
    selectedRegionType: RegionType,
    selectedFaceSize: FaceSize,
    namingFaceIndex: Int,
    namingInput: String,
    namingFocusRequester: FocusRequester,
    isLastUnnamed: Boolean,
    nameSuggestions: Map<Int, String>,
    onNamingInputChange: (String) -> Unit,
    onSelectNaming: (Int, String) -> Unit,
    onTapDelete: (Int) -> Unit,
    onEscape: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onKeyboardDelete: () -> Unit,
    onDeleteEmpty: () -> Unit,
    onSaveAndAdvance: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    val imageBitmap = remember(fullPreview) { fullPreview.toComposeImageBitmap() }
    var imageDisplayBounds by remember { mutableStateOf(Rect.Zero) }
    var hoverOffset by remember { mutableStateOf<Offset?>(null) }
    var draggingFaceIdx by remember { mutableStateOf(-1) }
    var dragOffsetPx by remember { mutableStateOf(Offset.Zero) }
    var hoverState by remember { mutableStateOf(FaceSelectorHoverState()) }

    // These refs intentionally stay current inside pointerInput(Unit), whose coroutines never
    // restart. This preserves the overlay's stale-closure behavior.
    val currentFaceRegions by rememberUpdatedState(faceRegions)
    val currentNamingFaceIndex by rememberUpdatedState(namingFaceIndex)
    val currentRegionType by rememberUpdatedState(selectedRegionType)
    val currentFaceSize by rememberUpdatedState(selectedFaceSize)
    val currentSelectNaming by rememberUpdatedState(onSelectNaming)
    val currentTapDelete by rememberUpdatedState(onTapDelete)

    val cursorIcon =
        remember(hoverState, draggingFaceIdx) {
            when {
                draggingFaceIdx >= 0 -> PointerIcon(Cursor(Cursor.MOVE_CURSOR))
                hoverState.isOverDelete -> PointerIcon(Cursor(Cursor.DEFAULT_CURSOR))
                hoverState.faceIdx >= 0 -> PointerIcon(Cursor(Cursor.MOVE_CURSOR))
                else -> PointerIcon(Cursor(Cursor.CROSSHAIR_CURSOR))
            }
        }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .pointerHoverIcon(cursorIcon)
                    .onGloballyPositioned { layoutCoords ->
                        val imgW = fullPreview.width.toFloat()
                        val imgH = fullPreview.height.toFloat()
                        val containerW = layoutCoords.size.width.toFloat()
                        val containerH = layoutCoords.size.height.toFloat()
                        if (imgW > 0f && imgH > 0f && containerW > 0f && containerH > 0f) {
                            val scale = minOf(containerW / imgW, containerH / imgH)
                            val drawW = imgW * scale
                            val drawH = imgH * scale
                            val offsetX = (containerW - drawW) / 2f
                            val offsetY = (containerH - drawH) / 2f
                            imageDisplayBounds =
                                Rect(offsetX, offsetY, offsetX + drawW, offsetY + drawH)
                        }
                    }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                when (event.type) {
                                    PointerEventType.Move -> {
                                        val pos = event.changes.firstOrNull()?.position
                                        hoverOffset = pos
                                        hoverState =
                                            calculateHoverState(
                                                position = pos,
                                                faceRegions = currentFaceRegions,
                                                namingFaceIndex = currentNamingFaceIndex,
                                                bounds = imageDisplayBounds,
                                            )
                                    }
                                    PointerEventType.Exit -> {
                                        hoverOffset = null
                                        hoverState = FaceSelectorHoverState()
                                    }
                                    else -> Unit
                                }
                            }
                        }
                    }
                    .pointerInput(faceRegions.toList()) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val closestIdx =
                                    findClosestFace(offset, faceRegions, imageDisplayBounds)
                                if (closestIdx >= 0) {
                                    draggingFaceIdx = closestIdx
                                    dragOffsetPx = Offset.Zero
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (draggingFaceIdx in faceRegions.indices) {
                                    dragOffsetPx += dragAmount
                                }
                            },
                            onDragEnd = {
                                if (draggingFaceIdx in faceRegions.indices) {
                                    val bounds = imageDisplayBounds
                                    if (bounds.width > 0f && bounds.height > 0f) {
                                        val region = faceRegions[draggingFaceIdx]
                                        val newX =
                                            (region.x +
                                                    dragOffsetPx.x.toDouble() /
                                                        bounds.width.toDouble())
                                                .coerceIn(0.0, 1.0)
                                        val newY =
                                            (region.y +
                                                    dragOffsetPx.y.toDouble() /
                                                        bounds.height.toDouble())
                                                .coerceIn(0.0, 1.0)
                                        faceRegionMutator.updateFaceRegion(
                                            idx,
                                            draggingFaceIdx,
                                            x = newX,
                                            y = newY,
                                        )
                                    }
                                }
                                draggingFaceIdx = -1
                                dragOffsetPx = Offset.Zero
                            },
                            onDragCancel = {
                                draggingFaceIdx = -1
                                dragOffsetPx = Offset.Zero
                            },
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val bounds = imageDisplayBounds
                            val faceRegionsNow = currentFaceRegions
                            val namingIdxNow = currentNamingFaceIndex
                            if (bounds.width > 0f && bounds.height > 0f) {
                                val closestIdx = findClosestFace(offset, faceRegionsNow, bounds)
                                if (closestIdx >= 0) {
                                    val region = faceRegionsNow[closestIdx]
                                    val deletePos = faceDeleteButtonPosition(region, bounds)
                                    val distToDelete =
                                        sqrt(
                                            (offset.x - deletePos.x).pow(2) +
                                                (offset.y - deletePos.y).pow(2)
                                        )
                                    val btnRadius = if (closestIdx == namingIdxNow) 20f else 16f
                                    if (distToDelete < btnRadius + 12f) {
                                        currentTapDelete(closestIdx)
                                    } else {
                                        currentSelectNaming(closestIdx, region.name)
                                    }
                                } else {
                                    val normX =
                                        ((offset.x - bounds.left) / bounds.width)
                                            .toDouble()
                                            .coerceIn(0.0, 1.0)
                                    val normY =
                                        ((offset.y - bounds.top) / bounds.height)
                                            .toDouble()
                                            .coerceIn(0.0, 1.0)
                                    if (normX in 0.0..1.0 && normY in 0.0..1.0) {
                                        faceRegionMutator.addFaceRegion(
                                            idx,
                                            "",
                                            normX,
                                            normY,
                                            currentRegionType,
                                            currentFaceSize,
                                        )
                                        currentSelectNaming(faceRegionsNow.size, "")
                                    }
                                }
                            }
                        }
                    }
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type != KeyEventType.KeyDown) {
                            false
                        } else {
                            when (keyEvent.key) {
                                Key.Escape -> {
                                    onEscape()
                                    true
                                }
                                Key.Tab -> {
                                    if (namingFaceIndex >= 0 && faceRegions.isNotEmpty()) {
                                        if (keyEvent.isShiftPressed) onPrevious() else onNext()
                                        true
                                    } else {
                                        false
                                    }
                                }
                                Key.Delete -> {
                                    if (namingFaceIndex in faceRegions.indices) {
                                        onKeyboardDelete()
                                        true
                                    } else {
                                        false
                                    }
                                }
                                else -> false
                            }
                        }
                    }
        ) {
            Image(
                bitmap = imageBitmap,
                contentDescription = s.t(StringKey.WIZARD_SELECT_TAGS, "index" to "${idx + 1}"),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
            FaceRegionsCanvas(
                faceRegions = faceRegions,
                inheritedFaceRegions = inheritedFaceRegions,
                bounds = imageDisplayBounds,
                hoverOffset = hoverOffset,
                hoverState = hoverState,
                draggingFaceIdx = draggingFaceIdx,
                dragOffsetPx = dragOffsetPx,
                namingFaceIndex = namingFaceIndex,
                selectedRegionType = selectedRegionType,
                selectedFaceSize = selectedFaceSize,
                modifier = Modifier.fillMaxSize(),
            )
        }

        FaceSelectorNamingBar(
            faceRegions = faceRegions,
            namingFaceIndex = namingFaceIndex,
            namingInput = namingInput,
            namingFocusRequester = namingFocusRequester,
            isLastUnnamed = isLastUnnamed,
            nameSuggestions = nameSuggestions,
            onNamingInputChange = onNamingInputChange,
            onSaveAndAdvance = onSaveAndAdvance,
            onDeleteEmpty = onDeleteEmpty,
            onSkip = onSkip,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
        )
    }
}

private fun calculateHoverState(
    position: Offset?,
    faceRegions: List<FaceRegion>,
    namingFaceIndex: Int,
    bounds: Rect,
): FaceSelectorHoverState {
    if (position == null || bounds.width <= 0f) return FaceSelectorHoverState(faceIdx = -1)
    val closestIdx = findClosestFace(position, faceRegions, bounds)
    if (closestIdx < 0) return FaceSelectorHoverState(faceIdx = -1)
    val deletePos = faceDeleteButtonPosition(faceRegions[closestIdx], bounds)
    val distToDelete = sqrt((position.x - deletePos.x).pow(2) + (position.y - deletePos.y).pow(2))
    val btnRadius = if (closestIdx == namingFaceIndex) 20f else 16f
    return FaceSelectorHoverState(
        faceIdx = closestIdx,
        isOverDelete = distToDelete < btnRadius + 12f,
    )
}
