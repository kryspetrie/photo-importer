package org.kryspetrie.fileimport.ui.screens.wizard.refinement

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import java.awt.image.BufferedImage
import org.kryspetrie.fileimport.infrastructure.wizard.BoundingBox
import org.kryspetrie.fileimport.infrastructure.wizard.BoundingBoxCorners
import org.kryspetrie.fileimport.infrastructure.wizard.Corner
import org.kryspetrie.fileimport.infrastructure.wizard.Point

/** Transform an image-space point to screen-space coordinates. */
internal fun imageToScreen(point: Point, zoom: Float, panX: Float, panY: Float): Offset {
    return Offset((panX + point.x * zoom).toFloat(), (panY + point.y * zoom).toFloat())
}

/** Transform a screen-space offset to image-space coordinates. */
internal fun screenToImage(screen: Offset, zoom: Float, panX: Float, panY: Float): Point {
    return Point(((screen.x - panX) / zoom).toDouble(), ((screen.y - panY) / zoom).toDouble())
}

/** Check if an image-space point is inside a bounding box's quadrilateral. */
internal fun isPointInBox(imgX: Double, imgY: Double, box: BoundingBox): Boolean {
    return isPointInQuadrilateral(imgX, imgY, box.corners)
}

/** Check if a point (px, py) is inside the quadrilateral defined by corners. Uses ray casting. */
private fun isPointInQuadrilateral(px: Double, py: Double, corners: BoundingBoxCorners): Boolean {
    val points = corners.toList()
    var inside = false
    var j = points.size - 1
    for (i in points.indices) {
        val xi = points[i].x
        val yi = points[i].y
        val xj = points[j].x
        val yj = points[j].y
        val intersect = ((yi > py) != (yj > py)) && (px < (xj - xi) * (py - yi) / (yj - yi) + xi)
        if (intersect) inside = !inside
        j = i
    }
    return inside
}

/** Find which corner of a bounding box is near the given screen offset, if any. */
internal fun findCornerHit(
    offset: Offset,
    box: BoundingBox,
    zoom: Float,
    panX: Float,
    panY: Float,
): Corner? {
    val hitRadius = 25f
    val corners =
        listOf(
            Corner.TOP_LEFT to box.corners.topLeft,
            Corner.TOP_RIGHT to box.corners.topRight,
            Corner.BOTTOM_LEFT to box.corners.bottomLeft,
            Corner.BOTTOM_RIGHT to box.corners.bottomRight,
        )

    for ((corner, point) in corners) {
        val screenPos = Offset((panX + point.x * zoom).toFloat(), (panY + point.y * zoom).toFloat())
        if ((offset - screenPos).getDistance() < hitRadius) {
            return corner
        }
    }
    return null
}

/** Create a downsampled copy of [image] at the given [scale], with optional explicit dimensions. */
internal fun createSampledImageForRefinement(
    image: BufferedImage,
    scale: Double,
    targetWidth: Int = (image.width * scale).toInt(),
    targetHeight: Int = (image.height * scale).toInt(),
): BufferedImage? {
    return try {
        val w = targetWidth.coerceIn(100, 4000)
        val h = targetHeight.coerceIn(100, 4000)
        if (w <= 0 || h <= 0) return null
        val result = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = result.createGraphics()
        g.drawImage(image, 0, 0, w, h, null)
        g.dispose()
        result
    } catch (_: Exception) {
        null
    }
}

/** Draw a faint outline of another bounding box (not the one being edited). */
internal fun DrawScope.drawOtherBoxOutline(box: BoundingBox, zoom: Float) {
    fun toScreen(p: Point) = imageToScreen(p, zoom, 0f, 0f) // Pan is handled by translate()

    val tl = toScreen(box.corners.topLeft)
    val tr = toScreen(box.corners.topRight)
    val bl = toScreen(box.corners.bottomLeft)
    val br = toScreen(box.corners.bottomRight)

    val path =
        Path().apply {
            moveTo(tl.x, tl.y)
            lineTo(tr.x, tr.y)
            lineTo(br.x, br.y)
            lineTo(bl.x, bl.y)
            close()
        }

    // Faint fill to show clickable area
    drawPath(path, Color(0xFF4CAF50).copy(alpha = 0.08f), style = Fill)
    // Dashed border outline
    drawPath(
        path,
        Color(0xFF4CAF50).copy(alpha = 0.5f),
        style =
            Stroke(
                width = 1.5f,
                pathEffect =
                    androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
            ),
    )

    // Small corner dots
    val dotRadius = 4f
    listOf(tl, tr, bl, br).forEach { corner ->
        drawCircle(Color(0xFF4CAF50).copy(alpha = 0.5f), radius = dotRadius, center = corner)
    }
}

/** Draw a bounding box with corner handles onto the canvas. */
internal fun DrawScope.drawRefinementBox(box: BoundingBox, selected: Corner?, zoom: Float) {
    fun toScreen(p: Point) = imageToScreen(p, zoom, 0f, 0f) // Pan is handled by translate()

    val tl = toScreen(box.corners.topLeft)
    val tr = toScreen(box.corners.topRight)
    val bl = toScreen(box.corners.bottomLeft)
    val br = toScreen(box.corners.bottomRight)

    // Fill
    val path =
        Path().apply {
            moveTo(tl.x, tl.y)
            lineTo(tr.x, tr.y)
            lineTo(br.x, br.y)
            lineTo(bl.x, bl.y)
            close()
        }
    drawPath(path, Color(0xFF2196F3).copy(alpha = 0.15f), style = Fill)
    drawPath(path, Color(0xFF2196F3), style = Stroke(width = 2f))

    // Center cross
    val cx = (tl.x + tr.x + bl.x + br.x) / 4
    val cy = (tl.y + tr.y + bl.y + br.y) / 4
    drawLine(Color(0xFF2196F3).copy(alpha = 0.5f), Offset(cx - 15f, cy), Offset(cx + 15f, cy), 1f)
    drawLine(Color(0xFF2196F3).copy(alpha = 0.5f), Offset(cx, cy - 15f), Offset(cx, cy + 15f), 1f)

    // Corner handles
    val handleRadius = 12f
    listOf(
            Corner.TOP_LEFT to tl,
            Corner.TOP_RIGHT to tr,
            Corner.BOTTOM_LEFT to bl,
            Corner.BOTTOM_RIGHT to br,
        )
        .forEach { (corner, pos) ->
            val r = if (corner == selected) handleRadius * 1.3f else handleRadius
            drawCircle(Color(0xFF2196F3), r, pos)
            drawCircle(Color.White, r - 3f, pos)
        }
}

/** Handle arrow-key events for refinement screen: move selected corner or pan. */
internal fun handleRefinementKeyEvent(
    event: KeyEvent,
    selectedCorner: Corner?,
    onMoveSelectedCorner: (dx: Double, dy: Double) -> Unit,
    onPan: (dx: Double, dy: Double) -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false

    val cornerDelta = 10.0
    val panDelta = 50.0

    return when (event.key) {
        Key.DirectionUp -> {
            if (selectedCorner != null) onMoveSelectedCorner(0.0, -cornerDelta)
            else onPan(0.0, panDelta)
            true
        }
        Key.DirectionDown -> {
            if (selectedCorner != null) onMoveSelectedCorner(0.0, cornerDelta)
            else onPan(0.0, -panDelta)
            true
        }
        Key.DirectionLeft -> {
            if (selectedCorner != null) onMoveSelectedCorner(-cornerDelta, 0.0)
            else onPan(panDelta, 0.0)
            true
        }
        Key.DirectionRight -> {
            if (selectedCorner != null) onMoveSelectedCorner(cornerDelta, 0.0)
            else onPan(-panDelta, 0.0)
            true
        }
        else -> false
    }
}
