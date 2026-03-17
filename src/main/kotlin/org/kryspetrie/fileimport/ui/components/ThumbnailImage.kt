package org.kryspetrie.fileimport.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.imgscalr.Scalr
import org.jetbrains.skia.Image as SkiaImage
import org.kryspetrie.fileimport.domain.model.ImageFileType
import org.kryspetrie.fileimport.infrastructure.adapter.RawThumbnailExtractor
import org.kryspetrie.fileimport.infrastructure.adapter.VideoThumbnailAdapter

object ThumbnailCache {
  private val cache = ConcurrentHashMap<String, ImageBitmap>()

  suspend fun load(file: File, maxPx: Int): ImageBitmap? {
    val key = "${file.absolutePath}:$maxPx"
    cache[key]?.let {
      return it
    }
    return withContext(Dispatchers.IO) {
      try {
        val fileType = ImageFileType.fromExtension(file.extension)
        val original =
            if (fileType.isRaw) {
              RawThumbnailExtractor.extractEmbeddedThumbnail(file)
            } else {
              ImageIO.read(file)
            } ?: return@withContext null
        val scaled = Scalr.resize(original, Scalr.Method.BALANCED, maxPx)
        original.flush()
        val baos = ByteArrayOutputStream()
        ImageIO.write(scaled, "jpg", baos)
        scaled.flush()
        SkiaImage.makeFromEncoded(baos.toByteArray()).toComposeImageBitmap().also {
          cache[key] = it
        }
      } catch (_: Exception) {
        null
      }
    }
  }

  suspend fun loadVideo(file: File, maxPx: Int): ImageBitmap? {
    val key = "video:${file.absolutePath}:$maxPx"
    cache[key]?.let {
      return it
    }
    return withContext(Dispatchers.IO) {
      try {
        val frame = VideoThumbnailAdapter.extractThumbnail(file, maxPx) ?: return@withContext null
        val baos = ByteArrayOutputStream()
        ImageIO.write(frame, "jpg", baos)
        frame.flush()
        SkiaImage.makeFromEncoded(baos.toByteArray()).toComposeImageBitmap().also {
          cache[key] = it
        }
      } catch (_: Exception) {
        null
      }
    }
  }
}

fun formatFileSize(bytes: Long): String =
    when {
      bytes >= 1_000_000_000 -> String.format("%.1f GB", bytes / 1_000_000_000.0)
      bytes >= 1_000_000 -> String.format("%.1f MB", bytes / 1_000_000.0)
      bytes >= 1_000 -> String.format("%.1f KB", bytes / 1_000.0)
      else -> "$bytes B"
    }

@Composable
fun ThumbnailImage(
    file: File,
    maxPx: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    durationText: String? = null
) {
  val fileType = ImageFileType.fromExtension(file.extension)

  if (fileType.isVideo) {
    VideoThumbnail(
        file = file,
        maxPx = maxPx,
        modifier = modifier,
        contentScale = contentScale,
        durationText = durationText)
  } else {
    ImageThumbnail(file = file, maxPx = maxPx, modifier = modifier, contentScale = contentScale)
  }
}

@Composable
private fun ImageThumbnail(
    file: File,
    maxPx: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
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
        contentScale = contentScale)
  } else {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center) {
          if (!loaded) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
          } else {
            Icon(
                Icons.Default.Image,
                null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
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
    durationText: String? = null
) {
  var bitmap by remember(file.absolutePath, maxPx) { mutableStateOf<ImageBitmap?>(null) }
  var loaded by remember(file.absolutePath, maxPx) { mutableStateOf(false) }

  LaunchedEffect(file.absolutePath, maxPx) {
    bitmap = ThumbnailCache.loadVideo(file, maxPx)
    loaded = true
  }

  Box(
      modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
      contentAlignment = Alignment.Center) {
        if (bitmap != null) {
          // Real video frame thumbnail
          Image(
              bitmap = bitmap!!,
              contentDescription = file.name,
              modifier = Modifier.fillMaxSize(),
              contentScale = contentScale)
        } else if (!loaded) {
          CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        } else {
          // Fallback icon when FFmpeg is unavailable
          Icon(
              Icons.Default.Videocam,
              contentDescription = null,
              modifier = Modifier.size(28.dp),
              tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }

        // Play button overlay (always shown for videos)
        Box(
            modifier =
                Modifier.size(32.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center) {
              Icon(
                  Icons.Default.PlayArrow,
                  contentDescription = "Video",
                  modifier = Modifier.size(20.dp),
                  tint = Color.White)
            }

        // Duration badge
        if (!durationText.isNullOrBlank()) {
          Box(
              modifier =
                  Modifier.align(Alignment.BottomEnd)
                      .padding(4.dp)
                      .background(Color.Black.copy(alpha = 0.7f), MaterialTheme.shapes.extraSmall)
                      .padding(horizontal = 4.dp, vertical = 1.dp)) {
                Text(
                    text = durationText,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    textAlign = TextAlign.End)
              }
        }

        // Extension badge
        Box(
            modifier =
                Modifier.align(Alignment.TopStart)
                    .padding(4.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = 4.dp, vertical = 1.dp)) {
              Text(
                  text = file.extension.uppercase(),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
      }
}
