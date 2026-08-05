package org.kryspetrie.fileimport.ui.screens.metadataeditor

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.awt.image.BufferedImage
import java.io.File
import org.kryspetrie.fileimport.application.OrientationCorrectionService
import org.kryspetrie.fileimport.domain.model.RotationAngle
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.components.RotationBadge
import org.kryspetrie.fileimport.ui.i18n.strings

/** Pre-computed view data for a single file's rotation preview. */
data class RotationPreviewItem(
    val file: File,
    val result: OrientationCorrectionService.CorrectionResult?,
    val isChecked: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RotationPreviewOverlay(
    files: List<File>,
    orientationResults: Map<String, OrientationCorrectionService.CorrectionResult>,
    excludedPaths: Set<String>,
    previewIndex: Int,
    thumbnailCache: java.util.concurrent.ConcurrentHashMap<String, BufferedImage>,
    thumbnailCacheRevision: Int,
    onEnsureThumbnail: suspend (File) -> Unit,
    currentImage: BufferedImage?,
    onToggleExclusion: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onSetPreviewIndex: (Int) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    val items =
        files.mapIndexed { index, file ->
            val result = orientationResults[file.absolutePath]
            val isChecked = file.absolutePath !in excludedPaths
            RotationPreviewItem(file, result, isChecked)
        }
    val checkedCount = items.count { it.isChecked && it.result != null }
    val totalDetected = items.count { it.result != null }
    // The file shown in the large preview
    val previewFile = if (previewIndex in files.indices) files[previewIndex] else null
    val previewResult = previewFile?.let { orientationResults[it.absolutePath] }

    Surface(
        modifier = modifier.fillMaxSize(),
        tonalElevation = 6.dp,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Header ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AutoFixHigh,
                        contentDescription = s.t(StringKey.ACC_AUTO_ROTATE),
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        s.t(StringKey.META_ROTATION_PREVIEW_TITLE),
                        style =
                            MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onSelectAll, modifier = Modifier.height(32.dp)) {
                        Icon(Icons.Default.SelectAll, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(s.t(StringKey.ACTION_ALL), style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(onClick = onDeselectAll, modifier = Modifier.height(32.dp)) {
                        Icon(Icons.Default.Deselect, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            s.t(StringKey.ACTION_NONE),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, s.close) }
                }
            }
            Text(
                s.t(
                    StringKey.META_ROTATION_PREVIEW_SUMMARY,
                    "checked" to checkedCount.toString(),
                    "total" to totalDetected.toString(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // ── Main content: grid + preview ──
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // Left: thumbnail grid with checkboxes
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(130.dp),
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(items) { item ->
                        val result = item.result
                        val rotationDeg =
                            result?.let { nearestCorrectionDegrees(it.nearestRotation) } ?: 0
                        val needsRotation =
                            result != null && result.nearestRotation != RotationAngle.NONE

                        Card(
                            modifier =
                                Modifier.height(120.dp).clickable {
                                    val idx =
                                        files.indexOfFirst {
                                            it.absolutePath == item.file.absolutePath
                                        }
                                    if (idx >= 0) onSetPreviewIndex(idx)
                                },
                            shape = RoundedCornerShape(6.dp),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor =
                                        if (previewFile?.absolutePath == item.file.absolutePath)
                                            MaterialTheme.colorScheme.primaryContainer
                                        else if (!item.isChecked)
                                            MaterialTheme.colorScheme.surfaceVariant.copy(
                                                alpha = 0.5f
                                            )
                                        else MaterialTheme.colorScheme.surfaceVariant
                                ),
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                LaunchedEffect(item.file.absolutePath, thumbnailCacheRevision) {
                                    onEnsureThumbnail(item.file)
                                }
                                val thumb = thumbnailCache[item.file.absolutePath]
                                if (thumb != null) {
                                    val bitmap = remember(thumb) { thumb.toComposeImageBitmap() }
                                    Image(
                                        bitmap = bitmap,
                                        contentDescription = item.file.name,
                                        modifier =
                                            Modifier.fillMaxSize().padding(2.dp).let { mod ->
                                                if (rotationDeg != 0)
                                                    mod.clip(RoundedCornerShape(4.dp))
                                                else mod
                                            },
                                        contentScale = ContentScale.Fit,
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Image,
                                        s.t(StringKey.ACC_LOADING),
                                        modifier = Modifier.align(Alignment.Center).size(32.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                // Checkbox overlay
                                Checkbox(
                                    checked = item.isChecked,
                                    onCheckedChange = { onToggleExclusion(item.file.absolutePath) },
                                    modifier = Modifier.align(Alignment.TopStart).size(20.dp),
                                    enabled = result != null,
                                )
                                // Rotation badge overlay
                                if (needsRotation && item.isChecked) {
                                    RotationBadge(
                                        rotationDegrees = rotationDeg,
                                        isAutoDetected = true,
                                        modifier = Modifier.align(Alignment.TopEnd).padding(2.dp),
                                    )
                                }
                                // "No rotation needed" badge
                                if (
                                    result != null && result.nearestRotation == RotationAngle.NONE
                                ) {
                                    Surface(
                                        modifier = Modifier.align(Alignment.TopEnd).padding(2.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(4.dp),
                                    ) {
                                        Row(
                                            modifier =
                                                Modifier.padding(
                                                    horizontal = 3.dp,
                                                    vertical = 1.dp,
                                                ),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                s.t(StringKey.META_ROTATION_ALREADY_UPRIGHT),
                                                modifier = Modifier.size(8.dp),
                                                tint = MaterialTheme.colorScheme.outline,
                                            )
                                            Text(
                                                "0°",
                                                style =
                                                    MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 8.sp
                                                    ),
                                                color = MaterialTheme.colorScheme.outline,
                                            )
                                        }
                                    }
                                }
                                // File name at bottom
                                Text(
                                    item.file.name,
                                    style =
                                        MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.align(Alignment.BottomStart).padding(2.dp),
                                )
                            }
                        }
                    }
                }

                // Right: large preview of the selected file
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (previewFile != null && currentImage != null) {
                        val previewBitmap =
                            remember(currentImage) { currentImage.toComposeImageBitmap() }
                        Box(
                            modifier =
                                Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            val displayRotation =
                                previewResult?.let {
                                    nearestCorrectionDegrees(it.nearestRotation).toFloat()
                                } ?: 0f

                            Image(
                                bitmap = previewBitmap,
                                contentDescription = previewFile.name,
                                modifier =
                                    Modifier.fillMaxSize().let { mod ->
                                        if (displayRotation != 0f) {
                                            mod.then(
                                                Modifier.graphicsLayer {
                                                    rotationZ = displayRotation
                                                }
                                            )
                                        } else mod
                                    },
                                contentScale = ContentScale.Fit,
                            )
                        }

                        // Preview info
                        Surface(
                            tonalElevation = 1.dp,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    previewFile.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(4.dp))
                                if (previewResult != null) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            s.t(
                                                StringKey.ORIENTATION_DETECTED_ANGLE,
                                                "angle" to
                                                    previewResult.orientationDegrees
                                                        .toInt()
                                                        .toString(),
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        Text(
                                            s.t(
                                                StringKey.ORIENTATION_DIALOG_CONFIDENCE,
                                                "confidence" to
                                                    (previewResult.confidence * 100)
                                                        .toInt()
                                                        .toString(),
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 4.dp),
                                    ) {
                                        if (previewResult.nearestRotation == RotationAngle.NONE) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(4.dp),
                                            ) {
                                                Text(
                                                    s.t(StringKey.META_ROTATION_UPRIGHT_DETAIL),
                                                    modifier =
                                                        Modifier.padding(
                                                            horizontal = 6.dp,
                                                            vertical = 2.dp,
                                                        ),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color =
                                                        MaterialTheme.colorScheme.onPrimaryContainer,
                                                )
                                            }
                                        } else {
                                            RotationBadge(
                                                rotationDegrees =
                                                    nearestCorrectionDegrees(
                                                        previewResult.nearestRotation
                                                    ),
                                                isAutoDetected = true,
                                            )
                                            Text(
                                                s.t(
                                                    StringKey.ORIENTATION_DIALOG_CORRECTION,
                                                    "correction" to
                                                        previewResult.correctionDegrees
                                                            .toInt()
                                                            .toString(),
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                    }
                                    if (
                                        OrientationCorrectionService.isJpegFile(
                                            previewFile.absolutePath
                                        )
                                    ) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.errorContainer,
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.padding(top = 4.dp),
                                        ) {
                                            Text(
                                                s.t(StringKey.META_ROTATION_JPEG_METADATA_ONLY),
                                                modifier =
                                                    Modifier.padding(
                                                        horizontal = 6.dp,
                                                        vertical = 2.dp,
                                                    ),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                            )
                                        }
                                    }
                                } else {
                                    Text(
                                        s.t(StringKey.META_ROTATION_NOT_DETECTED),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    s.t(StringKey.META_ROTATION_CLICK_PREVIEW),
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    s.t(StringKey.META_ROTATION_CLICK_HINT),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            // ── Footer ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    s.t(StringKey.META_ROTATION_WILL_ROTATE_N, "count" to checkedCount.toString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(16.dp))
                TextButton(onClick = onDismiss) { Text(s.cancel) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onApply, enabled = checkedCount > 0) {
                    Icon(
                        Icons.Default.AutoFixHigh,
                        s.t(StringKey.ACC_APPLY_ROTATION),
                        Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (checkedCount == 1) s.t(StringKey.META_ROTATE_ONE_PHOTO)
                        else s.t(StringKey.META_ROTATE_N_PHOTOS, "count" to checkedCount.toString())
                    )
                }
            }
        }
    }
}

/** Convert a RotationAngle to its correction degrees. */
private fun nearestCorrectionDegrees(rotation: RotationAngle): Int =
    when (rotation) {
        RotationAngle.NONE -> 0
        RotationAngle.CW_90 -> 90
        RotationAngle.CW_180 -> 180
        RotationAngle.CCW_90 -> 270
    }
