package org.kryspetrie.fileimport.ui.shared.face

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import org.kryspetrie.fileimport.domain.model.FaceRegion
import org.kryspetrie.fileimport.domain.model.RegionType
import org.kryspetrie.fileimport.ui.shared.image.PreviewImageGeometry

/** Read-only face region circles drawn over a letterboxed preview. */
@Composable
fun FaceRegionPreviewOverlay(
    faceRegions: List<FaceRegion>,
    containerWidthPx: Int,
    containerHeightPx: Int,
    imageWidth: Int,
    imageHeight: Int,
    modifier: Modifier = Modifier,
) {
    if (faceRegions.isEmpty() || containerWidthPx <= 0 || containerHeightPx <= 0) return

    val fitBounds =
        PreviewImageGeometry.fitBounds(
            containerWidthPx.toDouble(),
            containerHeightPx.toDouble(),
            imageWidth,
            imageHeight,
        )

    Canvas(modifier = modifier.fillMaxSize()) {
        val left = fitBounds.offsetX.toFloat()
        val top = fitBounds.offsetY.toFloat()
        val width = fitBounds.displayWidth.toFloat()
        val height = fitBounds.displayHeight.toFloat()

        faceRegions.forEach { region ->
            val cx = left + (region.x * width).toFloat()
            val cy = top + (region.y * height).toFloat()
            val radius = (region.w / 2.0 * height).toFloat()
            val color =
                when (RegionType.fromMwgRs(region.type)) {
                    RegionType.FACE -> Color.Yellow
                    RegionType.PET -> Color(0xFF4FC3F7)
                    RegionType.BODY -> Color(0xFF81C784)
                    RegionType.OBJECT -> Color(0xFFFFB74D)
                }
            drawCircle(color = color.copy(alpha = 0.25f), radius = radius, center = Offset(cx, cy))
            drawCircle(
                color = color,
                radius = radius,
                center = Offset(cx, cy),
                style = Stroke(width = 2f),
            )
        }
    }
}
