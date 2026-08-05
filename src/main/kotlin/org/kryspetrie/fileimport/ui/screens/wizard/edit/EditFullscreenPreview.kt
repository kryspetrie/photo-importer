package org.kryspetrie.fileimport.ui.screens.wizard.edit

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import java.awt.image.BufferedImage
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.domain.model.geometry.BoundingBoxList
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.components.PreviewCache
import org.kryspetrie.fileimport.ui.i18n.strings

@Composable
internal fun EditFullscreenPreviewHost(
    fullscreenPreviewIndex: Int?,
    boundingBoxList: BoundingBoxList,
    photoConfigurations: Map<String, PhotoScanConfiguration>,
    previewCache: PreviewCache,
    image: BufferedImage,
    onDismiss: () -> Unit,
) {
    if (fullscreenPreviewIndex == null || fullscreenPreviewIndex >= boundingBoxList.size()) return

    val s = strings()
    val box = boundingBoxList.boxes[fullscreenPreviewIndex]
    val config = photoConfigurations[box.id] ?: PhotoScanConfiguration()
    val fullPreview = previewCache.getFullPreview(image, box, config)
    val fullscreenBitmap = remember(fullPreview) { fullPreview?.toComposeImageBitmap() }

    Popup(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (fullscreenBitmap != null) {
                Image(
                    bitmap = fullscreenBitmap,
                    contentDescription =
                        s.t(StringKey.ACC_FULLSCREEN, "index" to "${fullscreenPreviewIndex + 1}"),
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentScale = ContentScale.Fit,
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
            ) {
                Icon(
                    Icons.Default.Close,
                    s.t(StringKey.ACTION_CLOSE),
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}
