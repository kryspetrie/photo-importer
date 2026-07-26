package org.kryspetrie.fileimport.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.awt.Cursor
import java.io.File
import java.util.Locale
import org.kryspetrie.fileimport.domain.model.ImageFile
import org.kryspetrie.fileimport.domain.model.ImageMetadata
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.components.ChunkyScrollbar
import org.kryspetrie.fileimport.ui.components.ThumbnailImage
import org.kryspetrie.fileimport.ui.components.formatFileSize
import org.kryspetrie.fileimport.ui.i18n.Strings
import org.kryspetrie.fileimport.ui.i18n.strings

private const val THUMB_PX = 80
private const val TILE_PX = 300
private const val PREVIEW_PX = 1200

private fun ImagePreviewViewModel.FileFilter.labelKey(): StringKey =
    when (this) {
        ImagePreviewViewModel.FileFilter.ALL -> StringKey.IMPORT_FILTER_ALL
        ImagePreviewViewModel.FileFilter.PHOTOS -> StringKey.IMPORT_FILTER_PHOTOS
        ImagePreviewViewModel.FileFilter.VIDEOS -> StringKey.IMPORT_FILTER_VIDEOS
        ImagePreviewViewModel.FileFilter.RAW -> StringKey.IMPORT_FILTER_RAW
    }

private fun ImagePreviewViewModel.SortMode.labelKey(): StringKey =
    when (this) {
        ImagePreviewViewModel.SortMode.NAME -> StringKey.IMPORT_SORT_NAME
        ImagePreviewViewModel.SortMode.DATE -> StringKey.IMPORT_SORT_DATE
        ImagePreviewViewModel.SortMode.SIZE -> StringKey.IMPORT_SORT_SIZE
        ImagePreviewViewModel.SortMode.TYPE -> StringKey.IMPORT_SORT_TYPE
    }

// ---------------------------------------------------------------------------
// Main screen
// ---------------------------------------------------------------------------

@Composable
fun ImagePreviewScreen(
    images: List<ImageFile>,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    selectedCount: Int,
    viewModel: ImagePreviewViewModel = remember { ImagePreviewViewModel() },
) {
    val s = strings()
    val totalSelectedSize = images.filter { it.isSelected }.sumOf { it.fileSize }
    val density = LocalDensity.current
    val filteredAndSorted =
        remember(
            images,
            viewModel.filterType,
            viewModel.sortMode,
            viewModel.sortAscending,
            viewModel.searchQuery,
        ) {
            viewModel.filteredAndSorted(images)
        }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            ImagePreviewHeader(
                viewModel = viewModel,
                onSelectAll = onSelectAll,
                onSelectNone = onSelectNone,
            )
            Spacer(Modifier.height(4.dp))
            FilterAndSortBar(viewModel = viewModel)
            Spacer(Modifier.height(8.dp))
            SelectionStatusBar(
                selectedCount = selectedCount,
                totalImages = images.size,
                filteredCount = filteredAndSorted.size,
                totalSelectedSize = totalSelectedSize,
            )
            Spacer(Modifier.height(8.dp))
            ImageContentArea(
                filteredAndSorted = filteredAndSorted,
                viewModel = viewModel,
                density = density,
                onToggleSelection = onToggleSelection,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                OutlinedButton(onClick = onBack) { Text(s.t(StringKey.ACTION_BACK)) }
                Button(onClick = onContinue, enabled = selectedCount > 0) {
                    Text(s.t(StringKey.IMPORT_CONTINUE))
                }
            }
        }
        viewModel.fullScreenImage?.let { img ->
            FullScreenOverlay(image = img, onDismiss = { viewModel.fullScreenImage = null })
        }
    }
}

// ---------------------------------------------------------------------------
// Extracted sub-composables
// ---------------------------------------------------------------------------

@Composable
private fun ImagePreviewHeader(
    viewModel: ImagePreviewViewModel,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
) {
    val s = strings()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(s.t(StringKey.IMPORT_SELECT_FILES), style = MaterialTheme.typography.headlineSmall)
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { viewModel.viewMode = ImagePreviewViewModel.ViewMode.LIST },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ViewList,
                    s.t(StringKey.ACC_LIST_VIEW),
                    tint =
                        if (viewModel.viewMode == ImagePreviewViewModel.ViewMode.LIST)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = { viewModel.viewMode = ImagePreviewViewModel.ViewMode.GRID },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Default.GridView,
                    s.t(StringKey.ACC_GRID_VIEW),
                    tint =
                        if (viewModel.viewMode == ImagePreviewViewModel.ViewMode.GRID)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onSelectAll) { Text(s.t(StringKey.ACTION_SELECT_ALL)) }
            TextButton(onClick = onSelectNone) { Text(s.t(StringKey.IMPORT_SELECT_NONE)) }
        }
    }
}

@Composable
private fun FilterAndSortBar(viewModel: ImagePreviewViewModel) {
    val s = strings()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = viewModel.searchQuery,
            onValueChange = { viewModel.searchQuery = it },
            placeholder = {
                Text(s.t(StringKey.IMPORT_SEARCH_PLACEHOLDER), style = MaterialTheme.typography.bodySmall)
            },
            modifier = Modifier.weight(1f).height(40.dp),
            textStyle = MaterialTheme.typography.bodySmall,
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(16.dp)) },
            trailingIcon = {
                if (viewModel.searchQuery.isNotBlank()) {
                    IconButton(
                        onClick = { viewModel.searchQuery = "" },
                        modifier = Modifier.size(20.dp),
                    ) {
                        Icon(Icons.Default.Clear, s.t(StringKey.ACC_CLEAR), Modifier.size(14.dp))
                    }
                }
            },
        )
        ImagePreviewViewModel.FileFilter.entries.forEach { filter ->
            FilterChip(
                selected = viewModel.filterType == filter,
                onClick = { viewModel.filterType = filter },
                label = {
                    Text(
                        s.t(filter.labelKey()),
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                modifier = Modifier.height(28.dp),
            )
        }
        // Sort dropdown
        var sortMenuExpanded by remember { mutableStateOf(false) }
        Box {
            FilterChip(
                selected = false,
                onClick = { sortMenuExpanded = true },
                label = {
                    Text(
                        s.t(
                            StringKey.IMPORT_SORT_LABEL,
                            "mode" to s.t(viewModel.sortMode.labelKey()),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                trailingIcon = {
                    Icon(
                        if (viewModel.sortAscending) Icons.Default.ArrowUpward
                        else Icons.Default.ArrowDownward,
                        null,
                        Modifier.size(14.dp),
                    )
                },
                modifier = Modifier.height(28.dp),
            )
            DropdownMenu(
                expanded = sortMenuExpanded,
                onDismissRequest = { sortMenuExpanded = false },
            ) {
                ImagePreviewViewModel.SortMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                s.t(mode.labelKey()),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        onClick = {
                            viewModel.sortMode = mode
                            sortMenuExpanded = false
                        },
                        trailingIcon = {
                            if (viewModel.sortMode == mode)
                                Icon(
                                    if (viewModel.sortAscending) Icons.Default.ArrowUpward
                                    else Icons.Default.ArrowDownward,
                                    null,
                                    Modifier.size(14.dp),
                                )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionStatusBar(
    selectedCount: Int,
    totalImages: Int,
    filteredCount: Int,
    totalSelectedSize: Long,
) {
    val s = strings()
    val filteredSuffix =
        if (filteredCount != totalImages) " (showing $filteredCount)" else ""

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                s.t(
                    StringKey.IMPORT_FILES_SELECTED,
                    "selected" to selectedCount.toString(),
                    "total" to totalImages.toString(),
                    "filtered" to filteredSuffix,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                formatFileSize(totalSelectedSize),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ImageContentArea(
    filteredAndSorted: List<ImageFile>,
    viewModel: ImagePreviewViewModel,
    density: Density,
    onToggleSelection: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (filteredAndSorted.isEmpty()) {
        EmptyState(modifier = modifier.fillMaxWidth())
    } else {
        Row(modifier = modifier) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                when (viewModel.viewMode) {
                    ImagePreviewViewModel.ViewMode.LIST ->
                        ImageListView(
                            filteredAndSorted,
                            onToggle = onToggleSelection,
                            onPreview = { viewModel.previewImage = it },
                        )
                    ImagePreviewViewModel.ViewMode.GRID ->
                        ImageGridView(
                            filteredAndSorted,
                            onToggle = onToggleSelection,
                            onPreview = { viewModel.previewImage = it },
                        )
                }
            }

            viewModel.previewImage?.let { img ->
                // Drag handle
                val dragInteraction = remember { MutableInteractionSource() }
                val isDragHovered by dragInteraction.collectIsHoveredAsState()
                Box(
                    modifier =
                        Modifier.width(8.dp)
                            .fillMaxHeight()
                            .hoverable(dragInteraction)
                            .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                            .draggable(
                                orientation = Orientation.Horizontal,
                                state =
                                    rememberDraggableState { deltaPx ->
                                        val deltaDp = with(density) { deltaPx.toDp().value }
                                        viewModel.paneWidthDp =
                                            (viewModel.paneWidthDp - deltaDp).coerceIn(
                                                ImagePreviewViewModel.PANE_MIN_DP,
                                                ImagePreviewViewModel.PANE_MAX_DP,
                                            )
                                    },
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier =
                            Modifier.width(2.dp)
                                .fillMaxHeight(0.3f)
                                .clip(MaterialTheme.shapes.small)
                                .background(
                                    if (isDragHovered)
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                    )
                }

                PreviewSidePane(
                    image = img,
                    modifier = Modifier.width(viewModel.paneWidthDp.dp).fillMaxHeight(),
                    onClose = { viewModel.previewImage = null },
                    onFullScreen = { viewModel.fullScreenImage = img },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Empty state
// ---------------------------------------------------------------------------

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    val s = strings()

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Default.Image,
                null,
                Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Text(
                s.t(StringKey.ERROR_NO_FILES_FOUND),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// List view
// ---------------------------------------------------------------------------

@Composable
private fun ImageListView(
    images: List<ImageFile>,
    onToggle: (String) -> Unit,
    onPreview: (ImageFile) -> Unit,
) {
    val s = strings()

    Column {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(80.dp))
            Text(
                s.t(StringKey.IMPORT_SORT_NAME),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(3f),
            )
            Text(
                s.t(StringKey.IMPORT_TYPE),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                s.t(StringKey.IMPORT_SIZE),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                s.t(StringKey.IMPORT_DATE),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1.5f),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        LazyColumn {
            items(images, key = { it.id }) { image ->
                ImageListRow(
                    image = image,
                    onToggle = { onToggle(image.id) },
                    onPreview = { onPreview(image) },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun ImageListRow(image: ImageFile, onToggle: () -> Unit, onPreview: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable(onClick = onPreview)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = image.isSelected,
            onCheckedChange = { onToggle() },
            modifier = Modifier.size(32.dp),
        )
        Spacer(Modifier.width(8.dp))
        ThumbnailImage(
            file = image.file,
            maxPx = THUMB_PX,
            modifier = Modifier.size(36.dp).clip(MaterialTheme.shapes.small),
            contentScale = ContentScale.Crop,
            durationText = image.metadata?.durationFormatted,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            image.fileName,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(3f),
        )
        Text(
            image.fileType.displayName,
            style = MaterialTheme.typography.bodySmall,
            color =
                when {
                    image.fileType.isVideo -> MaterialTheme.colorScheme.secondary
                    image.fileType.isRaw -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            modifier = Modifier.weight(1f),
        )
        Text(
            formatFileSize(image.fileSize),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            image.dateTakenFormatted,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1.5f),
        )
    }
}

// ---------------------------------------------------------------------------
// Grid / tile view
// ---------------------------------------------------------------------------

@Composable
private fun ImageGridView(
    images: List<ImageFile>,
    onToggle: (String) -> Unit,
    onPreview: (ImageFile) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(images, key = { it.id }) { image ->
            ImageGridTile(
                image = image,
                onToggle = { onToggle(image.id) },
                onPreview = { onPreview(image) },
            )
        }
    }
}

@Composable
private fun ImageGridTile(image: ImageFile, onToggle: () -> Unit, onPreview: () -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        border =
            BorderStroke(
                1.dp,
                if (image.isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                else MaterialTheme.colorScheme.outlineVariant,
            ),
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                Box(
                    modifier =
                        Modifier.fillMaxSize()
                            .clickable(onClick = onPreview)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    ThumbnailImage(
                        file = image.file,
                        maxPx = TILE_PX,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        durationText = image.metadata?.durationFormatted,
                    )
                }
                Box(
                    modifier =
                        Modifier.align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(28.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Checkbox(
                        checked = image.isSelected,
                        onCheckedChange = { onToggle() },
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    image.fileName,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${formatFileSize(image.fileSize)} \u00B7 ${image.fileType.displayName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Side preview pane
// ---------------------------------------------------------------------------

@Composable
private fun PreviewSidePane(
    image: ImageFile,
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
    onFullScreen: () -> Unit,
) {
    val s = strings()

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(s.t(StringKey.IMPORT_PREVIEW), style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Close,
                        s.t(StringKey.IMPORT_CLOSE_PREVIEW),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            PreviewImageWithHover(
                file = image.file,
                modifier = Modifier.fillMaxWidth().weight(1f),
                onFullScreen = onFullScreen,
            )

            Spacer(Modifier.height(12.dp))

            ChunkyScrollbar {
                Column(modifier = Modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        image.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    MetadataRow(s, StringKey.IMPORT_PATH, image.file.parent.orEmpty())
                    MetadataRow(s, StringKey.IMPORT_TYPE, image.fileType.displayName)
                    MetadataRow(s, StringKey.IMPORT_SIZE, formatFileSize(image.fileSize))
                    MetadataRow(s, StringKey.IMPORT_DATE, image.dateTakenFormatted)
                    image.metadata?.let { m ->
                        m.cameraModel.takeIf { it.isNotBlank() }?.let {
                            MetadataRow(s, StringKey.IMPORT_CAMERA, it)
                        }
                        m.lensInfo.takeIf { it != "Unknown" }?.let {
                            MetadataRow(s, StringKey.IMPORT_LENS, it)
                        }
                        if (m.imageWidth != null && m.imageHeight != null) {
                            MetadataRow(
                                s,
                                StringKey.IMPORT_DIMENSIONS,
                                "${m.imageWidth} \u00D7 ${m.imageHeight}",
                            )
                        }
                        m.durationFormatted?.let { MetadataRow(s, StringKey.IMPORT_DURATION, it) }
                        m.videoCodec?.let { MetadataRow(s, StringKey.IMPORT_CODEC, it) }
                        m.frameRate?.let {
                            MetadataRow(s, StringKey.IMPORT_FRAME_RATE, "%.1f fps".format(it))
                        }
                    }

                    image.metadata?.let { m ->
                        val detailEntries = buildDetailEntries(s, m, image.fileType.isVideo)
                        if (detailEntries.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            CollapsibleExifSection(
                                entries = detailEntries,
                                title =
                                    if (image.fileType.isVideo) s.t(StringKey.IMPORT_VIDEO_DETAILS)
                                    else s.t(StringKey.IMPORT_EXIF_DETAILS),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewImageWithHover(
    file: File,
    modifier: Modifier = Modifier,
    onFullScreen: () -> Unit,
) {
    val s = strings()
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()

    Box(
        modifier =
            modifier
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .hoverable(hoverInteraction)
                .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
                .clickable(onClick = onFullScreen),
        contentAlignment = Alignment.Center,
    ) {
        ThumbnailImage(
            file = file,
            maxPx = PREVIEW_PX,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )

        // Hover overlay
        if (isHovered) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Default.Fullscreen,
                        null,
                        modifier = Modifier.size(32.dp),
                        tint = Color.White.copy(alpha = 0.9f),
                    )
                    Text(
                        s.t(StringKey.IMPORT_CLICK_ENLARGE),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

private fun buildDetailEntries(s: Strings, m: ImageMetadata, isVideo: Boolean): List<Pair<String, String>> {
    val specific = if (isVideo) buildVideoDetailEntries(s, m) else buildPhotoDetailEntries(s, m)
    return specific + buildCommonDetailEntries(s, m)
}

private fun buildVideoDetailEntries(s: Strings, m: ImageMetadata): List<Pair<String, String>> = buildList {
    m.durationFormatted?.let { add(s.t(StringKey.IMPORT_DURATION) to it) }
    m.frameRate?.let { add(s.t(StringKey.IMPORT_FRAME_RATE) to "%.1f fps".format(it)) }
    m.videoCodec?.let { add(s.t(StringKey.IMPORT_CODEC) to it) }
    m.audioCodec?.let { add(s.t(StringKey.FIELD_AUDIO_CODEC) to it) }
    m.bitrate?.let { add(s.t(StringKey.FIELD_BITRATE) to "${it / 1000} kbps") }
    m.rotation?.let { add(s.t(StringKey.FIELD_ROTATION) to "${it}\u00B0") }
}

private fun buildPhotoDetailEntries(s: Strings, m: ImageMetadata): List<Pair<String, String>> = buildList {
    m.iso?.let { add(s.t(StringKey.FIELD_ISO) to it.toString()) }
    m.aperture?.let { add(s.t(StringKey.FIELD_APERTURE) to "f/$it") }
    m.shutterSpeed?.let { add(s.t(StringKey.FIELD_SHUTTER_SPEED) to it) }
    m.focalLength?.let {
        val text = buildString {
            append("${it}mm")
            m.focalLength35mm?.let { eq -> append(" (${eq}mm eq.)") }
        }
        add(s.t(StringKey.FIELD_FOCAL_LENGTH) to text)
    }
    m.exposureProgram?.let { add(s.t(StringKey.FIELD_EXPOSURE_PROGRAM) to it) }
    m.exposureCompensation?.let { add(s.t(StringKey.FIELD_EXPOSURE_COMP) to "${it} EV") }
    m.meteringMode?.let { add(s.t(StringKey.FIELD_METERING) to it) }
    m.flash?.let { add(s.t(StringKey.FIELD_FLASH) to it) }
    m.whiteBalance?.let { add(s.t(StringKey.FIELD_WHITE_BALANCE) to it) }
    m.colorSpace?.let { add(s.t(StringKey.FIELD_COLOR_SPACE) to it) }
    m.orientation?.let { add(s.t(StringKey.FIELD_ORIENTATION) to it.toString()) }
}

private fun buildCommonDetailEntries(s: Strings, m: ImageMetadata): List<Pair<String, String>> = buildList {
    m.software?.let { add(s.t(StringKey.FIELD_SOFTWARE) to it) }
    m.artist?.let { add(s.t(StringKey.FIELD_ARTIST) to it) }
    m.copyright?.let { add(s.t(StringKey.FIELD_COPYRIGHT) to it) }
    m.description?.let { add(s.t(StringKey.FIELD_DESCRIPTION) to it) }
    if (m.hasGpsData) {
        add(s.t(StringKey.FIELD_LAT) to String.format(Locale.US, "%.6f", m.latitude))
        add(s.t(StringKey.FIELD_LON) to String.format(Locale.US, "%.6f", m.longitude))
        m.altitude?.let { add(s.t(StringKey.FIELD_ALTITUDE) to String.format(Locale.US, "%.1fm", it)) }
    }
    m.dateTimeDigitized?.let { add(s.t(StringKey.FIELD_DATE_DIGITIZED) to it.toString()) }
    m.dateTimeModified?.let { add(s.t(StringKey.META_MODIFIED) to it.toString()) }
}

@Composable
private fun CollapsibleExifSection(
    entries: List<Pair<String, String>>,
    title: String,
) {
    val s = strings()
    var expanded by remember { mutableStateOf(false) }

    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(2.dp))
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                s.t(StringKey.IMPORT_TOGGLE_DETAILS),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "$title (${entries.size})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier.padding(start = 4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                entries.forEach { (label, value) -> MetadataRow(label, value) }
            }
        }
    }
}

@Composable
private fun MetadataRow(s: Strings, labelKey: StringKey, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            "${s.t(labelKey)}: ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            "$label: ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------------------------------------------------------------------
// Full-screen overlay
// ---------------------------------------------------------------------------

@Composable
private fun FullScreenOverlay(image: ImageFile, onDismiss: () -> Unit) {
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
            maxPx = PREVIEW_PX,
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentScale = ContentScale.Fit,
        )
    }
}
