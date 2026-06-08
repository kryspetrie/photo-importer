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
import org.kryspetrie.fileimport.ui.components.ChunkyScrollbar
import org.kryspetrie.fileimport.ui.components.ThumbnailImage
import org.kryspetrie.fileimport.ui.components.formatFileSize

private const val THUMB_PX = 80
private const val TILE_PX = 300
private const val PREVIEW_PX = 1200

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
                OutlinedButton(onClick = onBack) { Text("Back") }
                Button(onClick = onContinue, enabled = selectedCount > 0) { Text("Continue") }
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Select Files to Import", style = MaterialTheme.typography.headlineSmall)
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
                    "List view",
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
                    "Grid view",
                    tint =
                        if (viewModel.viewMode == ImagePreviewViewModel.ViewMode.GRID)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onSelectAll) { Text("Select All") }
            TextButton(onClick = onSelectNone) { Text("Select None") }
        }
    }
}

@Composable
private fun FilterAndSortBar(viewModel: ImagePreviewViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = viewModel.searchQuery,
            onValueChange = { viewModel.searchQuery = it },
            placeholder = { Text("Search files...", style = MaterialTheme.typography.bodySmall) },
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
                        Icon(Icons.Default.Clear, "Clear", Modifier.size(14.dp))
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
                        filter.name.lowercase().replaceFirstChar { it.uppercase() },
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
                        "Sort: ${viewModel.sortMode.name.lowercase().replaceFirstChar { it.uppercase() }}",
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
                                mode.name.lowercase().replaceFirstChar { it.uppercase() },
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
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "$selectedCount of $totalImages files selected" +
                    if (filteredCount != totalImages) " (showing $filteredCount)" else "",
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
                "No files found",
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
                "Name",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(3f),
            )
            Text(
                "Type",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                "Size",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                "Date",
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
                Text("Preview", style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, "Close preview", modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

            // Image preview with hover fullscreen hint
            PreviewImageWithHover(
                file = image.file,
                modifier = Modifier.fillMaxWidth().weight(1f),
                onFullScreen = onFullScreen,
            )

            Spacer(Modifier.height(12.dp))

            // Scrollable metadata area
            ChunkyScrollbar {
                Column(modifier = Modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Core metadata — always visible
                    Text(
                        image.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    MetadataRow("Path", image.file.parent.orEmpty())
                    MetadataRow("Type", image.fileType.displayName)
                    MetadataRow("Size", formatFileSize(image.fileSize))
                    MetadataRow("Date", image.dateTakenFormatted)
                    image.metadata?.let { m ->
                        m.cameraModel.takeIf { it.isNotBlank() }?.let { MetadataRow("Camera", it) }
                        m.lensInfo.takeIf { it != "Unknown" }?.let { MetadataRow("Lens", it) }
                        if (m.imageWidth != null && m.imageHeight != null) {
                            MetadataRow("Dimensions", "${m.imageWidth} \u00D7 ${m.imageHeight}")
                        }
                        m.durationFormatted?.let { MetadataRow("Duration", it) }
                        m.videoCodec?.let { MetadataRow("Codec", it) }
                        m.frameRate?.let { MetadataRow("Frame Rate", "%.1f fps".format(it)) }
                    }

                    // Collapsible full details section
                    image.metadata?.let { m ->
                        val detailEntries = buildDetailEntries(m, image.fileType.isVideo)
                        if (detailEntries.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            CollapsibleExifSection(
                                entries = detailEntries,
                                title =
                                    if (image.fileType.isVideo) "Video Details" else "EXIF Details",
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
                        "Click to enlarge",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

private fun buildDetailEntries(m: ImageMetadata, isVideo: Boolean): List<Pair<String, String>> {
    val specific = if (isVideo) buildVideoDetailEntries(m) else buildPhotoDetailEntries(m)
    return specific + buildCommonDetailEntries(m)
}

private fun buildVideoDetailEntries(m: ImageMetadata): List<Pair<String, String>> = buildList {
    m.durationFormatted?.let { add("Duration" to it) }
    m.frameRate?.let { add("Frame Rate" to "%.1f fps".format(it)) }
    m.videoCodec?.let { add("Video Codec" to it) }
    m.audioCodec?.let { add("Audio Codec" to it) }
    m.bitrate?.let { add("Bitrate" to "${it / 1000} kbps") }
    m.rotation?.let { add("Rotation" to "${it}\u00B0") }
}

private fun buildPhotoDetailEntries(m: ImageMetadata): List<Pair<String, String>> = buildList {
    m.iso?.let { add("ISO" to it.toString()) }
    m.aperture?.let { add("Aperture" to "f/$it") }
    m.shutterSpeed?.let { add("Shutter Speed" to it) }
    m.focalLength?.let {
        val text = buildString {
            append("${it}mm")
            m.focalLength35mm?.let { eq -> append(" (${eq}mm eq.)") }
        }
        add("Focal Length" to text)
    }
    m.exposureProgram?.let { add("Exposure Program" to it) }
    m.exposureCompensation?.let { add("Exposure Comp." to "${it} EV") }
    m.meteringMode?.let { add("Metering" to it) }
    m.flash?.let { add("Flash" to it) }
    m.whiteBalance?.let { add("White Balance" to it) }
    m.colorSpace?.let { add("Color Space" to it) }
    m.orientation?.let { add("Orientation" to it.toString()) }
}

private fun buildCommonDetailEntries(m: ImageMetadata): List<Pair<String, String>> = buildList {
    m.software?.let { add("Software" to it) }
    m.artist?.let { add("Artist" to it) }
    m.copyright?.let { add("Copyright" to it) }
    m.description?.let { add("Description" to it) }
    if (m.hasGpsData) {
        add("Latitude" to String.format(Locale.US, "%.6f", m.latitude))
        add("Longitude" to String.format(Locale.US, "%.6f", m.longitude))
        m.altitude?.let { add("Altitude" to String.format(Locale.US, "%.1fm", it)) }
    }
    m.dateTimeDigitized?.let { add("Date Digitized" to it.toString()) }
    m.dateTimeModified?.let { add("Date Modified" to it.toString()) }
}

@Composable
private fun CollapsibleExifSection(
    entries: List<Pair<String, String>>,
    title: String = "EXIF Details",
) {
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
                "Toggle details",
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
    Box(
        modifier =
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
                .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    image.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Close", tint = Color.White.copy(alpha = 0.8f))
                }
            }

            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                ThumbnailImage(
                    file = image.file,
                    maxPx = PREVIEW_PX,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "${formatFileSize(image.fileSize)} \u00B7 ${image.fileType.displayName}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f),
            )
        }
    }
}
