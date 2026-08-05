package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowLeft
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import java.awt.Cursor
import java.awt.image.BufferedImage
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.domain.model.geometry.BoundingBox
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.components.PreviewCache
import org.kryspetrie.fileimport.ui.components.RotationBadge
import org.kryspetrie.fileimport.ui.i18n.strings

/**
 * Right panel: large preview of the selected photo with detailed controls. Clicking the image opens
 * a full-screen preview dialog.
 */
@Composable
internal fun DetailPreviewPanel(
    image: BufferedImage,
    previewCache: PreviewCache,
    box: BoundingBox?,
    config: PhotoScanConfiguration,
    index: Int,
    totalPhotos: Int,
    onConfigChange: (PhotoScanConfiguration) -> Unit,
    onRotateCW: () -> Unit,
    onRotateCCW: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showFullscreen by remember { mutableStateOf(false) }
    val preview =
        box?.let { remember(image, it, config) { previewCache.getFullPreview(image, it, config) } }
    val previewBitmap = remember(preview) { preview?.toComposeImageBitmap() }

    Surface(modifier = modifier, tonalElevation = 1.dp, shape = RoundedCornerShape(0.dp)) {
        if (box != null) {
            Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                DetailPreviewImage(
                    previewBitmap = previewBitmap,
                    index = index,
                    onImageClick = { showFullscreen = true },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                DetailControlsRow(
                    config = config,
                    box = box,
                    index = index,
                    totalPhotos = totalPhotos,
                    onPrev = onPrev,
                    onNext = onNext,
                    onConfigChange = onConfigChange,
                    onRotateCW = onRotateCW,
                    onRotateCCW = onRotateCCW,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }

            if (showFullscreen && previewBitmap != null) {
                SummaryFullscreenPreviewDialog(
                    photoIndex = index,
                    totalCount = totalPhotos,
                    rotationDegrees = config.rotationDegrees,
                    bitmap = previewBitmap,
                    onDismiss = { showFullscreen = false },
                )
            }
        } else {
            EmptyPreviewPlaceholder()
        }
    }
}

/** Image area in the detail preview panel, with a zoom hint overlay. */
@Composable
internal fun DetailPreviewImage(
    previewBitmap: ImageBitmap?,
    index: Int,
    onImageClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.DarkGray)
                .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
                .clickable(onClick = onImageClick),
        contentAlignment = Alignment.Center,
    ) {
        if (previewBitmap != null) {
            Image(
                bitmap = previewBitmap,
                contentDescription = s.t(StringKey.ACC_PREVIEW_IMAGE, "index" to "${index + 1}"),
                modifier = Modifier.fillMaxSize().padding(8.dp),
                contentScale = ContentScale.Fit,
            )
            ZoomHintOverlay(modifier = Modifier.align(Alignment.BottomEnd))
        } else {
            Text(
                s.t(StringKey.WIZARD_COULD_NOT_RENDER),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Small "Click to zoom" hint overlay in the bottom-right of the preview. */
@Composable
internal fun ZoomHintOverlay(modifier: Modifier = Modifier) {
    val s = strings()
    Surface(
        modifier = modifier.padding(8.dp),
        shape = RoundedCornerShape(4.dp),
        color = Color.Black.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.ZoomIn,
                contentDescription = null,
                modifier = Modifier.height(14.dp).width(14.dp),
                tint = Color.White,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                s.t(StringKey.WIZARD_CLICK_ZOOM),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
            )
        }
    }
}

/** Controls row below the detail preview: photo label, navigation, rotation, dropdowns. */
@Composable
internal fun DetailControlsRow(
    config: PhotoScanConfiguration,
    box: BoundingBox,
    index: Int,
    totalPhotos: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onConfigChange: (PhotoScanConfiguration) -> Unit,
    onRotateCW: () -> Unit,
    onRotateCCW: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(tonalElevation = 2.dp, shape = RoundedCornerShape(8.dp), modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DetailLabelAndRotation(
                config = config,
                index = index,
                totalPhotos = totalPhotos,
                onPrev = onPrev,
                onNext = onNext,
                onRotateCW = onRotateCW,
                onRotateCCW = onRotateCCW,
            )
        }
    }
}

/** Photo label with prev/next navigation and rotation buttons. */
@Composable
internal fun DetailLabelAndRotation(
    config: PhotoScanConfiguration,
    index: Int,
    totalPhotos: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onRotateCW: () -> Unit,
    onRotateCCW: () -> Unit,
) {
    val s = strings()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrev, enabled = index > 0) {
                Icon(Icons.AutoMirrored.Filled.ArrowLeft, s.t(StringKey.WIZARD_PREVIOUS_PHOTO))
            }
            Text(
                s.t(
                    StringKey.SCAN_PHOTO_LABEL,
                    "index" to "${index + 1}",
                    "total" to "$totalPhotos",
                ),
                style = MaterialTheme.typography.titleSmall,
            )
            IconButton(onClick = onNext, enabled = index < totalPhotos - 1) {
                Icon(Icons.AutoMirrored.Filled.ArrowRight, s.t(StringKey.WIZARD_NEXT_PHOTO_NAV))
            }
            RotationBadge(rotationDegrees = config.rotationDegrees)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onRotateCCW) {
                Icon(
                    Icons.AutoMirrored.Filled.RotateLeft,
                    s.t(StringKey.ACC_ROTATE_CCW),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onRotateCW) {
                Icon(
                    Icons.AutoMirrored.Filled.RotateRight,
                    s.t(StringKey.ACC_ROTATE_CW),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** Placeholder shown when no photo is selected in the detail panel. */
@Composable
internal fun EmptyPreviewPlaceholder() {
    val s = strings()
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            s.t(StringKey.WIZARD_SELECT_TO_PREVIEW),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
