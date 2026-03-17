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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.awt.Cursor
import java.io.File
import org.kryspetrie.fileimport.domain.model.ImageFile
import org.kryspetrie.fileimport.domain.model.ImageMetadata
import org.kryspetrie.fileimport.ui.components.ThumbnailImage
import org.kryspetrie.fileimport.ui.components.formatFileSize

private enum class ViewMode {
  LIST,
  GRID
}

private const val THUMB_PX = 80
private const val TILE_PX = 300
private const val PREVIEW_PX = 1200

private const val PANE_MIN_DP = 220f
private const val PANE_MAX_DP = 600f
private const val PANE_DEFAULT_DP = 320f

private enum class FileFilter {
  ALL,
  PHOTOS,
  VIDEOS,
  RAW
}

private enum class SortMode {
  NAME,
  DATE,
  SIZE,
  TYPE
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
    selectedCount: Int
) {
  var viewMode by remember { mutableStateOf(ViewMode.GRID) }
  var previewImage by remember { mutableStateOf<ImageFile?>(null) }
  var fullScreenImage by remember { mutableStateOf<ImageFile?>(null) }
  var paneWidthDp by remember { mutableFloatStateOf(PANE_DEFAULT_DP) }
  val totalSelectedSize = images.filter { it.isSelected }.sumOf { it.fileSize }
  val density = LocalDensity.current

  // Filter and sort state
  var filterType by remember { mutableStateOf(FileFilter.ALL) }
  var sortMode by remember { mutableStateOf(SortMode.NAME) }
  var sortAscending by remember { mutableStateOf(true) }
  var searchQuery by remember { mutableStateOf("") }

  val filteredAndSorted =
      remember(images, filterType, sortMode, sortAscending, searchQuery) {
        var result = images
        if (searchQuery.isNotBlank()) {
          val q = searchQuery.lowercase()
          result = result.filter { it.fileName.lowercase().contains(q) }
        }
        result =
            when (filterType) {
              FileFilter.ALL -> result
              FileFilter.PHOTOS -> result.filter { !it.fileType.isVideo && !it.fileType.isRaw }
              FileFilter.VIDEOS -> result.filter { it.fileType.isVideo }
              FileFilter.RAW -> result.filter { it.fileType.isRawFormat }
            }
        val sorted =
            when (sortMode) {
              SortMode.NAME -> result.sortedBy { it.fileName.lowercase() }
              SortMode.DATE -> result.sortedBy { it.dateTaken }
              SortMode.SIZE -> result.sortedBy { it.fileSize }
              SortMode.TYPE -> result.sortedBy { it.fileType.displayName }
            }
        if (sortAscending) sorted else sorted.reversed()
      }

  Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
      // Header
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically) {
            Text("Select Files to Import", style = MaterialTheme.typography.headlineSmall)
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically) {
                  IconButton(
                      onClick = { viewMode = ViewMode.LIST }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.ViewList,
                            "List view",
                            tint =
                                if (viewMode == ViewMode.LIST) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                      }
                  IconButton(
                      onClick = { viewMode = ViewMode.GRID }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.GridView,
                            "Grid view",
                            tint =
                                if (viewMode == ViewMode.GRID) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                      }
                  Spacer(Modifier.width(8.dp))
                  TextButton(onClick = onSelectAll) { Text("Select All") }
                  TextButton(onClick = onSelectNone) { Text("Select None") }
                }
          }

      Spacer(Modifier.height(4.dp))

      // Filter and sort bar
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                  Text("Search files...", style = MaterialTheme.typography.bodySmall)
                },
                modifier = Modifier.weight(1f).height(40.dp),
                textStyle = MaterialTheme.typography.bodySmall,
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(16.dp)) },
                trailingIcon = {
                  if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                      Icon(Icons.Default.Clear, "Clear", Modifier.size(14.dp))
                    }
                  }
                })
            FileFilter.entries.forEach { filter ->
              FilterChip(
                  selected = filterType == filter,
                  onClick = { filterType = filter },
                  label = {
                    Text(
                        filter.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall)
                  },
                  modifier = Modifier.height(28.dp))
            }
            // Sort dropdown
            var sortMenuExpanded by remember { mutableStateOf(false) }
            Box {
              FilterChip(
                  selected = false,
                  onClick = { sortMenuExpanded = true },
                  label = {
                    Text(
                        "Sort: ${sortMode.name.lowercase().replaceFirstChar { it.uppercase() }}",
                        style = MaterialTheme.typography.labelSmall)
                  },
                  trailingIcon = {
                    Icon(
                        if (sortAscending) Icons.Default.ArrowUpward
                        else Icons.Default.ArrowDownward,
                        null,
                        Modifier.size(14.dp))
                  },
                  modifier = Modifier.height(28.dp))
              DropdownMenu(
                  expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                    SortMode.entries.forEach { mode ->
                      DropdownMenuItem(
                          text = {
                            Text(
                                mode.name.lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodySmall)
                          },
                          onClick = {
                            if (sortMode == mode) sortAscending = !sortAscending
                            else {
                              sortMode = mode
                              sortAscending = true
                            }
                            sortMenuExpanded = false
                          },
                          trailingIcon = {
                            if (sortMode == mode)
                                Icon(
                                    if (sortAscending) Icons.Default.ArrowUpward
                                    else Icons.Default.ArrowDownward,
                                    null,
                                    Modifier.size(14.dp))
                          })
                    }
                  }
            }
          }

      Spacer(Modifier.height(8.dp))

      OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
              Text(
                  "$selectedCount of ${images.size} files selected" +
                      if (filteredAndSorted.size != images.size)
                          " (showing ${filteredAndSorted.size})"
                      else "",
                  style = MaterialTheme.typography.bodyMedium)
              Text(
                  formatFileSize(totalSelectedSize),
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
      }

      Spacer(Modifier.height(8.dp))

      // Content area
      if (filteredAndSorted.isEmpty()) {
        EmptyState(modifier = Modifier.weight(1f).fillMaxWidth())
      } else {
        Row(modifier = Modifier.weight(1f)) {
          Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            when (viewMode) {
              ViewMode.LIST ->
                  ImageListView(
                      filteredAndSorted,
                      onToggle = onToggleSelection,
                      onPreview = { previewImage = it })
              ViewMode.GRID ->
                  ImageGridView(
                      filteredAndSorted,
                      onToggle = onToggleSelection,
                      onPreview = { previewImage = it })
            }
          }

          previewImage?.let { img ->
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
                                  paneWidthDp =
                                      (paneWidthDp - deltaDp).coerceIn(PANE_MIN_DP, PANE_MAX_DP)
                                }),
                contentAlignment = Alignment.Center) {
                  Box(
                      modifier =
                          Modifier.width(2.dp)
                              .fillMaxHeight(0.3f)
                              .clip(MaterialTheme.shapes.small)
                              .background(
                                  if (isDragHovered)
                                      MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                  else MaterialTheme.colorScheme.outlineVariant))
                }

            PreviewSidePane(
                image = img,
                modifier = Modifier.width(paneWidthDp.dp).fillMaxHeight(),
                onClose = { previewImage = null },
                onFullScreen = { fullScreenImage = img })
          }
        }
      }

      Spacer(Modifier.height(8.dp))

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        OutlinedButton(onClick = onBack) { Text("Back") }
        Button(onClick = onContinue, enabled = selectedCount > 0) { Text("Continue") }
      }
    }

    fullScreenImage?.let { img ->
      FullScreenOverlay(image = img, onDismiss = { fullScreenImage = null })
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
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(
              Icons.Default.Image,
              null,
              Modifier.size(48.dp),
              tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
          Text(
              "No files found",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    onPreview: (ImageFile) -> Unit
) {
  Column {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
          Spacer(Modifier.width(80.dp))
          Text("Name", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(3f))
          Text("Type", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
          Text("Size", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
          Text(
              "Date",
              style = MaterialTheme.typography.labelMedium,
              modifier = Modifier.weight(1.5f))
        }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    LazyColumn {
      items(images, key = { it.id }) { image ->
        ImageListRow(
            image = image, onToggle = { onToggle(image.id) }, onPreview = { onPreview(image) })
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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
      verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = image.isSelected,
            onCheckedChange = { onToggle() },
            modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(8.dp))
        ThumbnailImage(
            file = image.file,
            maxPx = THUMB_PX,
            modifier = Modifier.size(36.dp).clip(MaterialTheme.shapes.small),
            contentScale = ContentScale.Crop,
            durationText = image.metadata?.durationFormatted)
        Spacer(Modifier.width(8.dp))
        Text(
            image.fileName,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(3f))
        Text(
            image.fileType.displayName,
            style = MaterialTheme.typography.bodySmall,
            color =
                when {
                  image.fileType.isVideo -> MaterialTheme.colorScheme.secondary
                  image.fileType.isRaw -> MaterialTheme.colorScheme.tertiary
                  else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            modifier = Modifier.weight(1f))
        Text(
            formatFileSize(image.fileSize),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f))
        Text(
            image.dateTakenFormatted,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1.5f))
      }
}

// ---------------------------------------------------------------------------
// Grid / tile view
// ---------------------------------------------------------------------------

@Composable
private fun ImageGridView(
    images: List<ImageFile>,
    onToggle: (String) -> Unit,
    onPreview: (ImageFile) -> Unit
) {
  LazyVerticalGrid(
      columns = GridCells.Adaptive(minSize = 150.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(images, key = { it.id }) { image ->
          ImageGridTile(
              image = image, onToggle = { onToggle(image.id) }, onPreview = { onPreview(image) })
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
              else MaterialTheme.colorScheme.outlineVariant)) {
        Column {
          Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .clickable(onClick = onPreview)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                  ThumbnailImage(
                      file = image.file,
                      maxPx = TILE_PX,
                      modifier = Modifier.fillMaxSize(),
                      contentScale = ContentScale.Crop,
                      durationText = image.metadata?.durationFormatted)
                }
            Box(
                modifier =
                    Modifier.align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(28.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center) {
                  Checkbox(
                      checked = image.isSelected,
                      onCheckedChange = { onToggle() },
                      modifier = Modifier.size(24.dp))
                }
          }
          Column(modifier = Modifier.padding(8.dp)) {
            Text(
                image.fileName,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Text(
                "${formatFileSize(image.fileSize)} \u00B7 ${image.fileType.displayName}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    onFullScreen: () -> Unit
) {
  Surface(
      modifier = modifier,
      color = MaterialTheme.colorScheme.surface,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      shape = MaterialTheme.shapes.medium) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically) {
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
              onFullScreen = onFullScreen)

          Spacer(Modifier.height(12.dp))

          // Scrollable metadata area
          Column(
              modifier = Modifier.verticalScroll(rememberScrollState()),
              verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Core metadata — always visible
                Text(
                    image.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                MetadataRow("Path", image.file.parent ?: "")
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
                        title = if (image.fileType.isVideo) "Video Details" else "EXIF Details")
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
    onFullScreen: () -> Unit
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
      contentAlignment = Alignment.Center) {
        ThumbnailImage(
            file = file,
            maxPx = PREVIEW_PX,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit)

        // Hover overlay
        if (isHovered) {
          Box(
              modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
              contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                      Icon(
                          Icons.Default.Fullscreen,
                          null,
                          modifier = Modifier.size(32.dp),
                          tint = Color.White.copy(alpha = 0.9f))
                      Text(
                          "Click to enlarge",
                          style = MaterialTheme.typography.labelSmall,
                          color = Color.White.copy(alpha = 0.8f))
                    }
              }
        }
      }
}

private fun buildDetailEntries(m: ImageMetadata, isVideo: Boolean): List<Pair<String, String>> =
    buildList {
      if (isVideo) {
        m.durationFormatted?.let { add("Duration" to it) }
        m.frameRate?.let { add("Frame Rate" to "%.1f fps".format(it)) }
        m.videoCodec?.let { add("Video Codec" to it) }
        m.audioCodec?.let { add("Audio Codec" to it) }
        m.bitrate?.let { add("Bitrate" to "${it / 1000} kbps") }
        m.rotation?.let { add("Rotation" to "${it}\u00B0") }
      } else {
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
      m.software?.let { add("Software" to it) }
      m.artist?.let { add("Artist" to it) }
      m.copyright?.let { add("Copyright" to it) }
      m.description?.let { add("Description" to it) }
      if (m.hasGpsData) {
        add("Latitude" to String.format("%.6f", m.latitude))
        add("Longitude" to String.format("%.6f", m.longitude))
        m.altitude?.let { add("Altitude" to String.format("%.1fm", it)) }
      }
      m.dateTimeDigitized?.let { add("Date Digitized" to it.toString()) }
      m.dateTimeModified?.let { add("Date Modified" to it.toString()) }
    }

@Composable
private fun CollapsibleExifSection(
    entries: List<Pair<String, String>>,
    title: String = "EXIF Details"
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
        verticalAlignment = Alignment.CenterVertically) {
          Icon(
              if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
              "Toggle details",
              modifier = Modifier.size(18.dp),
              tint = MaterialTheme.colorScheme.onSurfaceVariant)
          Spacer(Modifier.width(4.dp))
          Text(
              "$title (${entries.size})",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    if (expanded) {
      Column(
          modifier = Modifier.padding(start = 4.dp),
          verticalArrangement = Arrangement.spacedBy(3.dp)) {
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
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(
        value,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis)
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
      contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().padding(20.dp)) {
              Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        image.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                      Icon(Icons.Default.Close, "Close", tint = Color.White.copy(alpha = 0.8f))
                    }
                  }

              Box(
                  modifier = Modifier.weight(1f).fillMaxWidth(),
                  contentAlignment = Alignment.Center) {
                    ThumbnailImage(
                        file = image.file,
                        maxPx = PREVIEW_PX,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit)
                  }

              Spacer(Modifier.height(8.dp))
              Text(
                  "${formatFileSize(image.fileSize)} \u00B7 ${image.fileType.displayName}",
                  style = MaterialTheme.typography.bodySmall,
                  color = Color.White.copy(alpha = 0.5f))
            }
      }
}
