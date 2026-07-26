package org.kryspetrie.fileimport.ui.screens.wizard.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.strings
import org.kryspetrie.fileimport.ui.wizard.state.FourPointState
import org.kryspetrie.fileimport.ui.wizard.state.ZoomController

/** Zoom controls overlay for the overview canvas. */
@Composable
fun ZoomControls(
    zoomController: ZoomController,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onFitToView: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(4.dp),
            ) {
                IconButton(onClick = onZoomOut, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ZoomOut, s.t(StringKey.ACC_ZOOM_OUT), Modifier.size(18.dp))
                }

                Text(
                    "${(zoomController.zoom * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(48.dp),
                    textAlign = TextAlign.Center,
                )

                IconButton(onClick = onZoomIn, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ZoomIn, s.t(StringKey.ACC_ZOOM_IN), Modifier.size(18.dp))
                }

                IconButton(onClick = onFitToView, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.FitScreen, s.t(StringKey.ACC_ZOOM_FIT), Modifier.size(18.dp))
                }
            }
        }
    }
}

/** 4-Point status indicator overlay. */
@Composable
fun FourPointStatusBar(
    state: FourPointState,
    onRemoveLast: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    val isRectangle = state.isRectangle()
    val pointText =
        if (isRectangle) {
            when (state.points.size) {
                0 -> s.t(StringKey.WIZARD_CLICK_FIRST_CORNER)
                1 -> s.t(StringKey.WIZARD_CLICK_OPPOSITE)
                else -> s.t(StringKey.META_DONE)
            }
        } else {
            s.t(StringKey.WIZARD_POINT_OF_FOUR, "current" to "${state.points.size + 1}")
        }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(pointText, style = MaterialTheme.typography.titleMedium)

            // Undo button - only in 4-point mode or if there's a point to undo
            if (!isRectangle && state.points.isNotEmpty()) {
                OutlinedButton(onClick = onRemoveLast, modifier = Modifier.height(32.dp)) {
                    Text(s.t(StringKey.META_UNDO), style = MaterialTheme.typography.labelSmall)
                }
            }

            if (!isRectangle && state.canConfirm()) {
                Button(onClick = onConfirm, modifier = Modifier.height(32.dp)) {
                    Text(s.t(StringKey.WIZARD_CONFIRM), style = MaterialTheme.typography.labelSmall)
                }
            }

            OutlinedButton(onClick = onCancel, modifier = Modifier.height(32.dp)) {
                Text(s.cancel, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/**
 * Creates a scaled version of the image for rendering at the given zoom level.
 *
 * When zoomed out (scale < 1), the image is downscaled for performance. When zoomed in (scale > 1),
 * the image is rendered at full resolution to avoid warping — the canvas positions elements using
 * `image.width * scale`, so the displayed image must match those dimensions exactly.
 */
fun createSampledImage(image: BufferedImage, scale: Double): BufferedImage? {
    return try {
        if (scale <= 0) return null

        if (scale >= 1.0) {
            // Zoomed in: return the original image at full resolution.
            // The caller will draw it at (image.width*scale) x (image.height*scale),
            // so we need the full source to avoid quality loss.
            return image
        }

        // Zoomed out: downscale for performance, but cap at 2000px max dimension
        val targetWidth = (image.width * scale).toInt().coerceIn(100, 2000)
        val targetHeight = (image.height * scale).toInt().coerceIn(100, 2000)
        if (targetWidth <= 0 || targetHeight <= 0) return null

        val sampled = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)
        val graphics = sampled.createGraphics()
        graphics.drawImage(image, 0, 0, targetWidth, targetHeight, null)
        graphics.dispose()
        sampled
    } catch (_: Exception) {
        null
    }
}
