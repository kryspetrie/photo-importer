package org.kryspetrie.fileimport.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.io.File
import java.util.Locale
import org.kryspetrie.fileimport.domain.model.ImageFileType

fun formatFileSize(bytes: Long): String =
    when {
        bytes >= 1_000_000_000 -> String.format(Locale.US, "%.1f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> String.format(Locale.US, "%.1f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }

@Composable
fun ThumbnailImage(
    file: File,
    maxPx: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    durationText: String? = null,
) {
    val fileType = ImageFileType.fromExtension(file.extension)

    if (fileType.isVideo) {
        VideoThumbnail(
            file = file,
            maxPx = maxPx,
            modifier = modifier,
            contentScale = contentScale,
            durationText = durationText,
        )
    } else {
        ImageThumbnail(file = file, maxPx = maxPx, modifier = modifier, contentScale = contentScale)
    }
}

@Composable
private fun ImageThumbnail(
    file: File,
    maxPx: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    var bitmap by remember(file.absolutePath, maxPx) { mutableStateOf<ImageBitmap?>(null) }
    var loaded by remember(file.absolutePath, maxPx) { mutableStateOf(false) }

    LaunchedEffect(file.absolutePath, maxPx) {
        bitmap = ThumbnailCache.load(file, maxPx)
        loaded = true
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = file.name,
            modifier = modifier,
            contentScale = contentScale,
        )
    } else {
        Box(
            modifier =
                modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center,
        ) {
            if (!loaded) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    Icons.Default.Image,
                    null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
            }
        }
    }
}

@Composable
private fun VideoThumbnail(
    file: File,
    maxPx: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    durationText: String? = null,
) {
    var bitmap by remember(file.absolutePath, maxPx) { mutableStateOf<ImageBitmap?>(null) }
    var loaded by remember(file.absolutePath, maxPx) { mutableStateOf(false) }

    LaunchedEffect(file.absolutePath, maxPx) {
        bitmap = ThumbnailCache.loadVideo(file, maxPx)
        loaded = true
    }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            // Real video frame thumbnail
            Image(
                bitmap = bitmap!!,
                contentDescription = file.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        } else if (!loaded) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        } else {
            // Fallback icon when FFmpeg is unavailable
            Icon(
                Icons.Default.Videocam,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }

        // Play button overlay (always shown for videos)
        Box(
            modifier =
                Modifier.size(32.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Video",
                modifier = Modifier.size(20.dp),
                tint = Color.White,
            )
        }

        // Duration badge
        if (!durationText.isNullOrBlank()) {
            Box(
                modifier =
                    Modifier.align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.7f), MaterialTheme.shapes.extraSmall)
                        .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = durationText,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    textAlign = TextAlign.End,
                )
            }
        }

        // Extension badge
        Box(
            modifier =
                Modifier.align(Alignment.TopStart)
                    .padding(4.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.shapes.extraSmall,
                    )
                    .padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
            Text(
                text = file.extension.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
