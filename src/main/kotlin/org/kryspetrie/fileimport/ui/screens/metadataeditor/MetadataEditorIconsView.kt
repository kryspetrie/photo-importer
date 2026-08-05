package org.kryspetrie.fileimport.ui.screens.metadataeditor

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage
import java.io.File
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.components.RotationBadge
import org.kryspetrie.fileimport.ui.i18n.strings
import org.kryspetrie.fileimport.ui.theme.LocalUiDensityScale

@Composable
internal fun MetadataEditorIconsView(
    currentFolder: MetadataFolderNode,
    state: BulkEditState,
    thumbnailCache: java.util.concurrent.ConcurrentHashMap<String, BufferedImage>,
    thumbnailCacheRevision: Int,
    onEnsureThumbnail: suspend (File) -> Unit,
    isMultiEditMode: Boolean,
    selectedIndices: Set<Int>,
    focusedFolderPath: String?,
    onSelectIndex: (Int) -> Unit,
    onEnterFolderPath: (String) -> Unit,
    compact: Boolean,
) {
    val densityScale = LocalUiDensityScale.current
    val thumbCardSize = densityScale.thumbnailCardSize
    val folderCardHeight = thumbCardSize * 0.65f
    val gridMinCell = maxOf(METADATA_BROWSER_MIN_GRID_CELL_DP.dp, thumbCardSize * 0.9f)

    if (compact) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            currentFolder.children.forEach { child ->
                val path = child.folder?.absolutePath ?: child.name
                MetadataEditorFolderIconCard(
                    name = child.name,
                    isFocused = focusedFolderPath == path,
                    onEnterFolder = { onEnterFolderPath(path) },
                    cardWidth = thumbCardSize,
                    cardHeight = folderCardHeight,
                )
            }
            currentFolder.fileIndices.forEach { index ->
                val file = state.files.getOrNull(index) ?: return@forEach
                MetadataEditorThumbnailCard(
                    file = file,
                    index = index,
                    state = state,
                    thumbnailCache = thumbnailCache,
                    thumbnailCacheRevision = thumbnailCacheRevision,
                    onEnsureThumbnail = onEnsureThumbnail,
                    isMultiEditMode = isMultiEditMode,
                    selectedIndices = selectedIndices,
                    onSelectIndex = onSelectIndex,
                    cardWidth = thumbCardSize,
                    cardHeight = thumbCardSize,
                )
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = gridMinCell),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(currentFolder.children.size) { childIndex ->
                val child = currentFolder.children[childIndex]
                val path = child.folder?.absolutePath ?: child.name
                MetadataEditorFolderIconCard(
                    name = child.name,
                    isFocused = focusedFolderPath == path,
                    onEnterFolder = { onEnterFolderPath(path) },
                    cardWidth = gridMinCell,
                    cardHeight = folderCardHeight,
                )
            }
            items(currentFolder.fileIndices.size) { fileIndex ->
                val index = currentFolder.fileIndices[fileIndex]
                val file = state.files.getOrNull(index) ?: return@items
                MetadataEditorThumbnailCard(
                    file = file,
                    index = index,
                    state = state,
                    thumbnailCache = thumbnailCache,
                    thumbnailCacheRevision = thumbnailCacheRevision,
                    onEnsureThumbnail = onEnsureThumbnail,
                    isMultiEditMode = isMultiEditMode,
                    selectedIndices = selectedIndices,
                    onSelectIndex = onSelectIndex,
                    cardWidth = gridMinCell,
                    cardHeight = thumbCardSize,
                )
            }
        }
    }
}

@Composable
internal fun MetadataEditorFolderIconCard(
    name: String,
    onEnterFolder: () -> Unit,
    isFocused: Boolean = false,
    cardWidth: androidx.compose.ui.unit.Dp,
    cardHeight: androidx.compose.ui.unit.Dp,
) {
    val s = strings()
    Card(
        modifier =
            Modifier.width(cardWidth).height(cardHeight).pointerInput(onEnterFolder) {
                detectTapGestures(onDoubleTap = { onEnterFolder() })
            },
        shape = RoundedCornerShape(6.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isFocused) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                    else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Default.Folder,
                s.t(StringKey.ACC_ENTER_FOLDER),
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                name,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
internal fun MetadataEditorThumbnailCard(
    file: File,
    index: Int,
    state: BulkEditState,
    thumbnailCache: java.util.concurrent.ConcurrentHashMap<String, BufferedImage>,
    thumbnailCacheRevision: Int,
    onEnsureThumbnail: suspend (File) -> Unit,
    isMultiEditMode: Boolean,
    selectedIndices: Set<Int>,
    onSelectIndex: (Int) -> Unit,
    cardWidth: androidx.compose.ui.unit.Dp,
    cardHeight: androidx.compose.ui.unit.Dp,
) {
    val s = strings()
    LaunchedEffect(file.absolutePath, thumbnailCacheRevision) { onEnsureThumbnail(file) }
    val isSelected = if (isMultiEditMode) index in selectedIndices else index == state.selectedIndex
    val entry = state.fileConfigs[file.absolutePath]
    val isModified = entry?.isModified == true

    Card(
        modifier = Modifier.width(cardWidth).height(cardHeight).clickable { onSelectIndex(index) },
        shape = RoundedCornerShape(6.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    when {
                        isModified && isSelected -> MaterialTheme.colorScheme.tertiaryContainer
                        isSelected -> MaterialTheme.colorScheme.primaryContainer
                        isModified -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
            ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val thumb = thumbnailCache[file.absolutePath]
            val thumbRotation = entry?.config?.rotationDegrees?.toFloat() ?: 0f
            if (thumb != null) {
                val bitmap = remember(thumb) { thumb.toComposeImageBitmap() }
                Image(
                    bitmap = bitmap,
                    contentDescription = file.name,
                    modifier =
                        Modifier.fillMaxSize().padding(2.dp).let { mod ->
                            if (thumbRotation != 0f) mod.graphicsLayer { rotationZ = thumbRotation }
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
            if (isSelected && isMultiEditMode) {
                Checkbox(
                    checked = true,
                    onCheckedChange = { onSelectIndex(index) },
                    modifier = Modifier.align(Alignment.TopStart).size(16.dp),
                )
            }
            val rotationDeg = entry?.config?.rotationDegrees ?: 0
            if (rotationDeg != 0) {
                RotationBadge(
                    rotationDegrees = rotationDeg,
                    isAutoDetected = true,
                    modifier = Modifier.align(Alignment.TopEnd).padding(2.dp),
                )
            }
        }
    }
}
