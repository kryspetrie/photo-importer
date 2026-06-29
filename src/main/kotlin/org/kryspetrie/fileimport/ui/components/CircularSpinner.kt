package org.kryspetrie.fileimport.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.min

/**
 * Indeterminate circular progress indicator using time-based animation.
 *
 * Visually similar to Material3's `CircularProgressIndicator` but uses
 * `System.nanoTime() + delay()` instead of `InfiniteTransition.animateFloat()`,
 * avoiding `MonotonicFrameClock` issues in Compose Desktop AWT contexts.
 */
@Composable
fun CircularSpinner(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF1A73E8),
    strokeWidth: Dp = 4.dp,
    size: Dp = 48.dp,
) {
    var rotation by remember { mutableFloatStateOf(0f) }
    var arcLength by remember { mutableFloatStateOf(45f) }

    LaunchedEffect(Unit) {
        val rotationSpeed = 6f   // degrees per frame (~1.5s per revolution)
        val arcSpeed = 1.5f     // arc degrees change per frame
        var growing = true

        while (true) {
            rotation = (rotation + rotationSpeed) % 360f
            if (growing) {
                arcLength += arcSpeed
                if (arcLength >= 270f) growing = false
            } else {
                arcLength -= arcSpeed
                if (arcLength <= 30f) growing = true
            }
            delay(16) // ~60fps
        }
    }

    val strokeWidthPx = with(LocalDensity.current) { strokeWidth.toPx() }
    val sizePx = with(LocalDensity.current) { size.toPx() }

    Canvas(modifier = modifier.size(size)) {
        val radius = (min(sizePx, sizePx) - strokeWidthPx) / 2f
        drawArc(
            color = color,
            startAngle = rotation - arcLength / 2f,
            sweepAngle = arcLength,
            useCenter = false,
            style = Stroke(
                width = strokeWidthPx,
                cap = StrokeCap.Round,
            ),
        )
    }
}