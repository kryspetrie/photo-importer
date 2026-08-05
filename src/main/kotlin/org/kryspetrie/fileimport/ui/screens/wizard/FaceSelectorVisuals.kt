@file:Suppress("MagicNumber")

package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlin.math.pow
import kotlin.math.sqrt
import org.kryspetrie.fileimport.domain.model.FaceRegion
import org.kryspetrie.fileimport.domain.model.RegionType
import org.kryspetrie.fileimport.ui.wizard.state.FaceSize

/** Color for each region type when drawn on the canvas. */
internal fun regionTypeColor(type: RegionType): Color =
    when (type) {
        RegionType.FACE -> Color.Yellow
        RegionType.PET -> Color(0xFF4FC3F7)
        RegionType.BODY -> Color(0xFF81C784)
        RegionType.OBJECT -> Color(0xFFFFB74D)
    }

internal data class FaceSelectorHoverState(val faceIdx: Int = -1, val isOverDelete: Boolean = false)

@Immutable
private data class FaceRenderData(
    val name: String,
    val type: String,
    val x: Double,
    val y: Double,
    val w: Double,
)

private fun FaceRegion.toRenderData(): FaceRenderData =
    FaceRenderData(name = name, type = type, x = x, y = y, w = w)

internal fun faceDeleteButtonPosition(
    region: FaceRegion,
    bounds: Rect,
    offset: Offset = Offset.Zero,
): Offset {
    val cx = bounds.left + (region.x * bounds.width).toFloat() + offset.x
    val cy = bounds.top + (region.y * bounds.height).toFloat() + offset.y
    val radius = (region.w / 2.0 * bounds.height).toFloat()
    val angle = -Math.PI / 4.0
    return Offset(
        cx + (radius * kotlin.math.cos(angle)).toFloat(),
        cy + (radius * kotlin.math.sin(angle)).toFloat(),
    )
}

internal fun findClosestFace(offset: Offset, faceRegions: List<FaceRegion>, bounds: Rect): Int {
    if (bounds.width <= 0f || bounds.height <= 0f) return -1
    var bestIdx = -1
    var bestDist = Float.MAX_VALUE
    for (idx in faceRegions.indices) {
        val region = faceRegions[idx]
        val cx = bounds.left + (region.x * bounds.width).toFloat()
        val cy = bounds.top + (region.y * bounds.height).toFloat()
        val radius = (region.w / 2.0 * bounds.height).toFloat()
        val dist = sqrt((offset.x - cx).pow(2) + (offset.y - cy).pow(2))
        if (dist < radius + 10f && dist < bestDist) {
            bestDist = dist
            bestIdx = idx
        }
    }
    return bestIdx
}

@Composable
internal fun FaceRegionsCanvas(
    faceRegions: List<FaceRegion>,
    inheritedFaceRegions: List<FaceRegion>,
    bounds: Rect,
    hoverOffset: Offset?,
    hoverState: FaceSelectorHoverState,
    draggingFaceIdx: Int,
    dragOffsetPx: Offset,
    namingFaceIndex: Int,
    selectedRegionType: RegionType,
    selectedFaceSize: FaceSize,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    Canvas(modifier = modifier) {
        if (bounds.width <= 0f || bounds.height <= 0f) return@Canvas
        for (faceIdx in faceRegions.indices) {
            val region = faceRegions[faceIdx]
            val renderData = region.toRenderData()
            val color = regionTypeColor(RegionType.fromMwgRs(renderData.type))
            val isDragging = faceIdx == draggingFaceIdx
            val isNamingSelected = faceIdx == namingFaceIndex
            val isHovered = faceIdx == hoverState.faceIdx && !hoverState.isOverDelete
            val isCursorInCircle = faceIdx == hoverState.faceIdx
            val currentDragOffset = if (isDragging) dragOffsetPx else Offset.Zero
            val cx = bounds.left + (renderData.x * bounds.width).toFloat() + currentDragOffset.x
            val cy = bounds.top + (renderData.y * bounds.height).toFloat() + currentDragOffset.y
            val radius = (renderData.w / 2.0 * bounds.height).toFloat()

            when {
                isDragging -> drawCircle(color.copy(alpha = 0.4f), radius, Offset(cx, cy))
                isNamingSelected -> drawCircle(color.copy(alpha = 0.25f), radius, Offset(cx, cy))
                isHovered -> drawCircle(color.copy(alpha = 0.15f), radius, Offset(cx, cy))
            }
            drawCircle(
                color = color,
                radius = radius,
                center = Offset(cx, cy),
                style = Stroke(width = if (isNamingSelected) 3f else 2f),
            )
            if (isNamingSelected) {
                drawCircle(
                    color = Color.White,
                    radius = radius + 4f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2f),
                )
            }

            if (renderData.name.isNotBlank()) {
                val nameLayout =
                    textMeasurer.measure(
                        renderData.name,
                        TextStyle(
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                val labelWidth = nameLayout.size.width.toFloat() + 8f
                val labelHeight = nameLayout.size.height.toFloat() + 4f
                val labelX = cx - labelWidth / 2f
                val labelY = cy - radius - 18f
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.75f),
                    topLeft = Offset(labelX, labelY),
                    size = Size(labelWidth, labelHeight),
                    cornerRadius = CornerRadius(4f),
                )
                drawRoundRect(
                    color = color,
                    topLeft = Offset(labelX, labelY),
                    size = Size(labelWidth, labelHeight),
                    cornerRadius = CornerRadius(4f),
                    style = Stroke(width = 1.5f),
                )
                drawText(
                    textLayoutResult = nameLayout,
                    topLeft = Offset(cx - nameLayout.size.width.toFloat() / 2f, labelY + 2f),
                )
            }

            if (!isDragging && (isCursorInCircle || isNamingSelected)) {
                val delPos = faceDeleteButtonPosition(region, bounds, currentDragOffset)
                val isDeleteHover = isCursorInCircle && hoverState.isOverDelete
                val emphasized = isDeleteHover || isNamingSelected
                val btnRadius = if (emphasized) 20f else 16f
                val xSize = if (emphasized) 10f else 8f
                val xStroke = if (emphasized) 3.5f else 2.5f
                val btnAlpha = if (emphasized) 1.0f else 0.7f
                val btnColor = if (isDeleteHover) Color(0xFFFF5555) else Color.Red
                drawCircle(btnColor.copy(alpha = btnAlpha), btnRadius, delPos)
                drawLine(
                    Color.White,
                    Offset(delPos.x - xSize, delPos.y - xSize),
                    Offset(delPos.x + xSize, delPos.y + xSize),
                    xStroke,
                    StrokeCap.Round,
                )
                drawLine(
                    Color.White,
                    Offset(delPos.x + xSize, delPos.y - xSize),
                    Offset(delPos.x - xSize, delPos.y + xSize),
                    xStroke,
                    StrokeCap.Round,
                )
            }
        }

        if (hoverState.faceIdx in faceRegions.indices) {
            val hoveredRegion = faceRegions[hoverState.faceIdx]
            if (hoveredRegion.name.isNotBlank() && !hoverState.isOverDelete) {
                val hovCx = bounds.left + (hoveredRegion.x * bounds.width).toFloat()
                val hovCy = bounds.top + (hoveredRegion.y * bounds.height).toFloat()
                val hovRadius = (hoveredRegion.w / 2.0 * bounds.height).toFloat()
                val tooltipLayout =
                    textMeasurer.measure(
                        hoveredRegion.name,
                        TextStyle(
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                val tooltipWidth = tooltipLayout.size.width.toFloat() + 16f
                val tooltipHeight = tooltipLayout.size.height.toFloat() + 10f
                val tooltipX =
                    (hovCx - tooltipWidth / 2f).coerceIn(bounds.left, bounds.right - tooltipWidth)
                val tooltipY =
                    (hovCy - hovRadius - tooltipHeight - 12f).coerceIn(
                        bounds.top,
                        bounds.bottom - tooltipHeight,
                    )
                drawRoundRect(
                    Color.Black.copy(alpha = 0.85f),
                    Offset(tooltipX, tooltipY),
                    Size(tooltipWidth, tooltipHeight),
                    CornerRadius(8f),
                )
                drawRoundRect(
                    color = regionTypeColor(RegionType.fromMwgRs(hoveredRegion.type)),
                    topLeft = Offset(tooltipX, tooltipY),
                    size = Size(tooltipWidth, tooltipHeight),
                    cornerRadius = CornerRadius(8f),
                    style = Stroke(width = 2f),
                )
                drawText(
                    textLayoutResult = tooltipLayout,
                    topLeft = Offset(tooltipX + 8f, tooltipY + 5f),
                )
            }
        }

        if (hoverOffset != null && hoverState.faceIdx < 0 && draggingFaceIdx < 0) {
            val previewRadius = (selectedFaceSize.radius * bounds.height).toFloat()
            drawCircle(
                regionTypeColor(selectedRegionType).copy(alpha = 0.4f),
                previewRadius,
                hoverOffset,
            )
            drawCircle(
                color = regionTypeColor(selectedRegionType).copy(alpha = 0.7f),
                radius = previewRadius,
                center = hoverOffset,
                style = Stroke(width = 2f),
            )
        }

        for (region in inheritedFaceRegions) {
            val cx = bounds.left + (region.x * bounds.width).toFloat()
            val cy = bounds.top + (region.y * bounds.height).toFloat()
            val radius = (region.w / 2.0 * bounds.height).toFloat()
            drawCircle(Color.Cyan, radius, Offset(cx, cy), style = Stroke(width = 1.5f))
            val inheritedLayout =
                textMeasurer.measure(region.name, TextStyle(color = Color.White, fontSize = 10.sp))
            drawRoundRect(
                color = Color.Cyan.copy(alpha = 0.7f),
                topLeft =
                    Offset(cx - inheritedLayout.size.width.toFloat() / 2f - 4f, cy - radius - 18f),
                size =
                    Size(
                        inheritedLayout.size.width.toFloat() + 8f,
                        inheritedLayout.size.height.toFloat() + 4f,
                    ),
            )
            drawText(
                textLayoutResult = inheritedLayout,
                topLeft = Offset(cx - inheritedLayout.size.width.toFloat() / 2f, cy - radius - 16f),
            )
            val plusY = cy + radius + 8f
            drawCircle(Color.Cyan.copy(alpha = 0.7f), 8f, Offset(cx, plusY))
            drawLine(
                Color.White,
                Offset(cx - 4f, plusY),
                Offset(cx + 4f, plusY),
                2f,
                StrokeCap.Round,
            )
            drawLine(
                Color.White,
                Offset(cx, plusY - 4f),
                Offset(cx, plusY + 4f),
                2f,
                StrokeCap.Round,
            )
        }
    }
}
