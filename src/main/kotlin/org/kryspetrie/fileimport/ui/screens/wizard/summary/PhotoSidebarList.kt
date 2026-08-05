package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage
import org.kryspetrie.fileimport.domain.model.AspectRatio
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.domain.model.geometry.BoundingBox
import org.kryspetrie.fileimport.domain.model.geometry.BoundingBoxList
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.components.PreviewCache
import org.kryspetrie.fileimport.ui.components.RotationBadge
import org.kryspetrie.fileimport.ui.i18n.strings
import org.kryspetrie.fileimport.ui.screens.wizard.summary.BulkActionButtons

/**
 * Left sidebar: scrollable list of photo cards with thumbnails, bulk action buttons, and selection
 * state.
 */
@Composable
internal fun PhotoSidebarList(
    image: BufferedImage,
    previewCache: PreviewCache,
    boundingBoxList: BoundingBoxList,
    photoConfigurations: Map<String, PhotoScanConfiguration>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onRotateAllCW: () -> Unit,
    onRotateAllCCW: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    var pendingDeleteIndex by remember { mutableStateOf<Int?>(null) }

    Column(modifier = modifier) {
        BulkActionButtons(
            onRotateAllCW = onRotateAllCW,
            onRotateAllCCW = onRotateAllCCW,
            onClearAll = onClearAll,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier =
                Modifier.weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(8.dp),
                    ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(4.dp),
        ) {
            itemsIndexed(boundingBoxList.boxes) { index, box ->
                val config = photoConfigurations[box.id] ?: PhotoScanConfiguration()
                val thumbnail =
                    remember(image, box, config) { previewCache.getThumbnail(image, box, config) }

                SidebarPhotoCard(
                    index = index,
                    box = box,
                    config = config,
                    thumbnail = thumbnail,
                    isSelected = index == selectedIndex,
                    onSelect = { onSelectedIndexChange(index) },
                    onDelete = { pendingDeleteIndex = index },
                )
            }
        }
    }

    // Delete confirmation dialog
    if (pendingDeleteIndex != null) {
        val deleteIndex = pendingDeleteIndex!!
        AlertDialog(
            onDismissRequest = { pendingDeleteIndex = null },
            title = { Text(s.t(StringKey.WIZARD_DELETE_PHOTO_QUESTION)) },
            text = { Text(s.t(StringKey.WIZARD_REMOVE_PHOTO, "index" to "${deleteIndex + 1}")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(deleteIndex)
                        pendingDeleteIndex = null
                    }
                ) {
                    Text(s.delete, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteIndex = null }) { Text(s.cancel) }
            },
        )
    }
}

/**
 * A single card in the sidebar list. Shows a small thumbnail, photo number, and rotation state.
 * Selected cards are highlighted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SidebarPhotoCard(
    index: Int,
    box: BoundingBox,
    config: PhotoScanConfiguration,
    thumbnail: ImageBitmap?,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    val s = strings()
    val borderColor =
        if (isSelected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.surface
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SidebarThumbnail(index = index, thumbnail = thumbnail)
            SidebarInfoColumn(
                index = index,
                box = box,
                config = config,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDelete, modifier = Modifier.height(24.dp).width(24.dp)) {
                Icon(
                    Icons.Default.Delete,
                    s.t(StringKey.ACC_DELETE_PHOTO),
                    modifier = Modifier.height(16.dp).width(16.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** Thumbnail box within a sidebar card. */
@Composable
internal fun SidebarThumbnail(index: Int, thumbnail: ImageBitmap?) {
    val s = strings()
    Box(
        modifier =
            Modifier.width(60.dp)
                .height(60.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.DarkGray),
        contentAlignment = Alignment.Center,
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail,
                contentDescription = s.t(StringKey.ACC_THUMBNAIL, "index" to "${index + 1}"),
                modifier = Modifier.fillMaxSize().padding(2.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text("?", style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Info column within a sidebar card showing photo name and dimensions. */
@Composable
internal fun SidebarInfoColumn(
    index: Int,
    box: BoundingBox,
    config: PhotoScanConfiguration,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                s.t(StringKey.ACC_THUMBNAIL, "index" to "${index + 1}"),
                style = MaterialTheme.typography.labelMedium,
            )
            RotationBadge(rotationDegrees = config.rotationDegrees)
            if (config.aspectRatio != 0.0) {
                val ratioLabel =
                    AspectRatio.entries.find { it.value == config.aspectRatio }?.displayName
                        ?: String.format("%.2f", config.aspectRatio)
                Text(
                    ratioLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (config.correctionStrategy != null) {
                Text(
                    config.correctionStrategy.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            "${box.width().toInt()} × ${box.height().toInt()} px",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
