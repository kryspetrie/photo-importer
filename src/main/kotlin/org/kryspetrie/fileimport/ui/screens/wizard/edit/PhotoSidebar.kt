package org.kryspetrie.fileimport.ui.screens.wizard.edit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage
import org.kryspetrie.fileimport.domain.model.geometry.BoundingBoxList
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.domain.port.PerspectiveCorrectionPort
import org.kryspetrie.fileimport.ui.components.PreviewCache

/**
 * Vertical sidebar showing photo thumbnails stacked top-to-bottom.
 * Includes the multi-edit toggle button at the top.
 * Thumbnails scale down when there are many to fit the vertical space.
 */
@Composable
internal fun PhotoSidebar(
    image: BufferedImage,
    perspectiveService: PerspectiveCorrectionPort,
    previewCache: PreviewCache,
    boundingBoxList: BoundingBoxList,
    photoConfigurations: Map<String, PhotoScanConfiguration>,
    selectedIndices: Set<Int>,
    isMultiEditMode: Boolean,
    onToggleMultiEdit: () -> Unit,
    onSelect: (Int) -> Unit,
    onDeselectAll: () -> Unit,
) {
    val photoCount = boundingBoxList.size()
    // Scale thumbnails based on how many there are — smaller when crowded
    val thumbHeight = when {
        photoCount <= 3 -> 80.dp
        photoCount <= 6 -> 64.dp
        photoCount <= 10 -> 52.dp
        else -> 44.dp
    }
    val thumbWidth = when {
        photoCount <= 3 -> 100.dp
        photoCount <= 6 -> 80.dp
        photoCount <= 10 -> 66.dp
        else -> 56.dp
    }

    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxHeight().width(thumbWidth + 16.dp),
        shape = RoundedCornerShape(0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxHeight().padding(vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // Multi-edit toggle at top of sidebar
            if (photoCount > 1) {
                if (isMultiEditMode) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    ) {
                        Text(
                            "${selectedIndices.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        OutlinedButton(
                            onClick = {
                                onDeselectAll()
                                onToggleMultiEdit()
                            },
                            modifier = Modifier.height(24.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp),
                        ) {
                            Text("Done", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = onToggleMultiEdit,
                        modifier = Modifier.height(24.dp).padding(horizontal = 4.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp),
                    ) {
                        Text("Multi", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Vertical scrollable thumbnail list
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                itemsIndexed(boundingBoxList.boxes) { index, box ->
                    val config = photoConfigurations[box.id] ?: PhotoScanConfiguration()
                    val visualConfig = PhotoScanConfiguration(rotationDegrees = config.rotationDegrees)
                    val thumbnail = previewCache.getThumbnail(image, box, visualConfig)
                    val isSelected = index in selectedIndices
                    Card(
                        modifier = Modifier.width(thumbWidth).height(thumbHeight).clickable { onSelect(index) },
                        shape = RoundedCornerShape(6.dp),
                        border =
                            BorderStroke(
                                if (isSelected) 2.dp else 1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                            ),
                        colors =
                            CardDefaults.cardColors(
                                containerColor =
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                            ),
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (thumbnail != null) {
                                Image(
                                    bitmap = thumbnail,
                                    contentDescription = "Photo ${index + 1}",
                                    modifier = Modifier.fillMaxSize().padding(2.dp),
                                    contentScale = ContentScale.Fit,
                                )
                            }
                            if (isSelected) {
                                if (isMultiEditMode) {
                                    Checkbox(
                                        checked = true,
                                        onCheckedChange = { onSelect(index) },
                                        modifier = Modifier.align(Alignment.TopStart).size(18.dp),
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.align(Alignment.TopEnd).size(16.dp),
                                    )
                                }
                            }
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.BottomStart).padding(2.dp),
                            )
                            if (config.hasMetadata()) {
                                Text(
                                    "✓",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}