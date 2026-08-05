package org.kryspetrie.fileimport.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.ImageFile
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.components.ThumbnailImage
import org.kryspetrie.fileimport.ui.components.formatFileSize
import org.kryspetrie.fileimport.ui.i18n.strings

@Composable
internal fun FullScreenOverlay(image: ImageFile, onDismiss: () -> Unit) {
    val s = strings()

    Box(
        modifier =
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
                .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        // Info text: top-left
        Column(modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
            Text(
                image.fileName,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${formatFileSize(image.fileSize)} \u00B7 ${image.fileType.displayName}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f),
            )
        }
        // Close button: top-right
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
        ) {
            Icon(Icons.Default.Close, s.t(StringKey.ACTION_CLOSE), modifier = Modifier.size(28.dp))
        }
        // Image: centered
        ThumbnailImage(
            file = image.file,
            maxPx = IMAGE_PREVIEW_MAX_PX,
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentScale = ContentScale.Fit,
        )
    }
}
