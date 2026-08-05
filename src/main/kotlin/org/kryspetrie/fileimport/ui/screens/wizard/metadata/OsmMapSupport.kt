package org.kryspetrie.fileimport.ui.screens.wizard.metadata

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope

internal data class TileLoadKey(
    val lat: Double,
    val lon: Double,
    val zoom: Double,
    val width: Int,
    val height: Int,
    val style: MapStyle,
)

/** Bridges [Canvas] draw-scope dimensions and tile-load invalidation to Compose state. */
@Stable
internal class SizeTracker {
    var width by mutableStateOf(0)
    var height by mutableStateOf(0)

    fun update(w: Int, h: Int) {
        if (w != width || h != height) {
            width = w
            height = h
        }
    }

    var revision by mutableStateOf(0)
        private set

    fun invalidate() {
        revision++
    }
}

internal fun DrawScope.drawPinMarker(x: Float, y: Float) {
    drawCircle(color = Color(0xFFF44336), radius = 10f, center = Offset(x, y - 12f))
    drawCircle(color = Color.White, radius = 5f, center = Offset(x, y - 12f))
    val path =
        Path().apply {
            moveTo(x, y)
            lineTo(x - 7f, y - 8f)
            lineTo(x + 7f, y - 8f)
            close()
        }
    drawPath(path = path, color = Color(0xFFF44336))
}
