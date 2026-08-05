package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.strings

/** Full-screen preview dialog shown when a photo preview is clicked. */
@Composable
internal fun SummaryFullscreenPreviewDialog(
    photoIndex: Int,
    totalCount: Int,
    rotationDegrees: Int,
    bitmap: ImageBitmap,
    onDismiss: () -> Unit,
) {
    val s = strings()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
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
                    s.t(
                        StringKey.SCAN_PHOTO_LABEL,
                        "index" to (photoIndex + 1).toString(),
                        "total" to totalCount.toString(),
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White.copy(alpha = 0.8f),
                )
                if (rotationDegrees != 0) {
                    Text(
                        "${rotationDegrees}° rotation",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }
            }
            // Close button: top-right
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = s.close,
                    modifier = Modifier.size(28.dp),
                )
            }
            // Image: centered
            Image(
                bitmap = bitmap,
                contentDescription =
                    s.t(StringKey.ACC_PREVIEW_IMAGE, "index" to "${photoIndex + 1}"),
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
}
