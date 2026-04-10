package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage
import org.kryspetrie.fileimport.infrastructure.wizard.*

/** Common aspect ratios for photo correction. */
enum class AspectRatio(val displayName: String, val value: Double) {
  CURRENT("Current", 0.0),
  SQUARE("1:1", 1.0),
  PORTRAIT_2_3("2:3", 2.0 / 3.0),
  LANDSCAPE_3_2("3:2", 3.0 / 2.0),
  PORTRAIT_3_4("3:4", 3.0 / 4.0),
  LANDSCAPE_4_3("4:3", 4.0 / 3.0),
  PORTRAIT_4_5("4:5", 4.0 / 5.0),
  LANDSCAPE_5_4("5:4", 5.0 / 4.0),
  WIDE_16_9("16:9", 16.0 / 9.0),
  WIDE_3_1("3:1 (Panorama)", 3.0)
}

/**
 * Summary screen showing all detected photos with correction options. Allows per-photo and bulk
 * configuration of perspective, rotation, and aspect ratio.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    state: PhotoScanWizardState,
    image: BufferedImage,
    onBack: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
    exportDestination: String = System.getProperty("user.home") + "/Pictures/PhotoScan",
    onDestinationChange: ((String) -> Unit)? = null
) {
  val boundingBoxList by state.boundingBoxList.collectAsState()
  val photoConfigurations by state.photoConfigurations.collectAsState()

  // Preview image for selected photo
  var selectedPreviewIndex by remember { mutableIntStateOf(0) }
  val currentBox =
      remember(boundingBoxList, selectedPreviewIndex) {
        if (selectedPreviewIndex >= 0 && selectedPreviewIndex < boundingBoxList.size()) {
          boundingBoxList.boxes[selectedPreviewIndex]
        } else null
      }

  // Cropped preview
  val previewImage =
      remember(image, currentBox) { currentBox?.let { box -> cropBoundingBox(image, box) } }

  Scaffold(
      topBar = {
        TopAppBar(
            title = { Text("Photo Summary") },
            navigationIcon = {
              IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            },
            actions = {
              Text(
                  "${boundingBoxList.size()} photo(s)",
                  style = MaterialTheme.typography.labelLarge,
                  modifier = Modifier.padding(horizontal = 16.dp))
            })
      },
      content = { paddingValues ->
        Row(modifier = modifier.fillMaxSize().padding(paddingValues)) {
          // Left panel: Photo list and options
          Column(modifier = Modifier.weight(0.55f).fillMaxHeight().padding(16.dp)) {
            // Bulk action buttons
            BulkActionButtons(
                onRotateAllCW = { state.rotateAllBoxes(90) },
                onRotateAllCCW = { state.rotateAllBoxes(-90) },
                onPerspectiveAll = { state.setPerspectiveCorrectionAll(true) },
                onClearAll = { state.clearAllConfigurations() },
                modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(16.dp))

            // Destination folder selector
            DestinationSelector(
                destination = exportDestination,
                onDestinationChange = { onDestinationChange?.invoke(it) },
                modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(16.dp))

            // Photo list
            LazyColumn(
                modifier =
                    Modifier.weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(8.dp)),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(8.dp)) {
                  itemsIndexed(boundingBoxList.boxes) { index, box ->
                    PhotoSummaryCard(
                        box = box,
                        index = index,
                        isSelected = index == selectedPreviewIndex,
                        config = photoConfigurations[box.id] ?: PhotoConfiguration(),
                        onSelect = { selectedPreviewIndex = index },
                        onConfigChange = { config -> state.setPhotoConfiguration(box.id, config) },
                        onDelete = {
                          state.removeBox(index)
                          if (selectedPreviewIndex >= boundingBoxList.size()) {
                            selectedPreviewIndex = maxOf(0, boundingBoxList.size() - 1)
                          }
                        })
                  }
                }
          }

          // Right panel: Preview
          Box(
              modifier =
                  Modifier.weight(0.45f)
                      .fillMaxHeight()
                      .padding(16.dp)
                      .clip(RoundedCornerShape(8.dp))
                      .border(
                          1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                      .background(Color.DarkGray)) {
                if (previewImage != null) {
                  Image(
                      bitmap = previewImage.toComposeImageBitmap(),
                      contentDescription = "Photo preview",
                      modifier = Modifier.fillMaxSize().padding(16.dp),
                      contentScale = ContentScale.Fit)

                  // Click to return to overview hint
                  Surface(
                      modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                      shape = RoundedCornerShape(4.dp),
                      color = Color.Black.copy(alpha = 0.6f)) {
                        Text(
                            "Click on photo in list to preview",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White)
                      }
                } else {
                  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Select a photo to preview",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.7f))
                  }
                }
              }
        }
      },
      bottomBar = {
        Surface(tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
          Row(
              modifier = Modifier.fillMaxWidth().padding(16.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Ready to export ${boundingBoxList.size()} photo(s)",
                    style = MaterialTheme.typography.bodyMedium)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  OutlinedButton(onClick = onBack, modifier = Modifier.height(48.dp)) {
                    Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Back to Overview")
                  }

                  Button(
                      onClick = onExport,
                      enabled = boundingBoxList.size() > 0,
                      modifier = Modifier.height(48.dp)) {
                        Icon(Icons.Default.Download, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Export Photos")
                      }
                }
              }
        }
      })
}

@Composable
private fun BulkActionButtons(
    onRotateAllCW: () -> Unit,
    onRotateAllCCW: () -> Unit,
    onPerspectiveAll: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
  // Track if perspective was just enabled to enforce mutex
  var perspectiveJustEnabled by remember { mutableStateOf(false) }

  Surface(tonalElevation = 1.dp, shape = RoundedCornerShape(8.dp), modifier = modifier) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
          Text(
              "Bulk Actions:",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant)

          OutlinedButton(onClick = onRotateAllCW, modifier = Modifier.height(32.dp)) {
            Icon(Icons.Default.RotateRight, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Rotate CW", style = MaterialTheme.typography.labelSmall)
          }

          OutlinedButton(onClick = onRotateAllCCW, modifier = Modifier.height(32.dp)) {
            Icon(Icons.Default.RotateLeft, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Rotate CCW", style = MaterialTheme.typography.labelSmall)
          }

          OutlinedButton(onClick = onPerspectiveAll, modifier = Modifier.height(32.dp)) {
            Icon(Icons.Default.CropFree, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Perspective", style = MaterialTheme.typography.labelSmall)
          }

          OutlinedButton(
              onClick = onClearAll,
              modifier = Modifier.height(32.dp),
              colors =
                  ButtonDefaults.outlinedButtonColors(
                      contentColor = MaterialTheme.colorScheme.error)) {
                Icon(Icons.Default.Clear, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Clear All", style = MaterialTheme.typography.labelSmall)
              }

          // Tooltip explaining the mutex behavior
          var showTooltip by remember { mutableStateOf(false) }
          Box {
            IconButton(
                onClick = { showTooltip = true },
                modifier = Modifier.size(32.dp)) {
                  Icon(
                      Icons.Default.Info,
                      "Correction info",
                      modifier = Modifier.size(18.dp),
                      tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            if (showTooltip) {
              AlertDialog(
                  onDismissRequest = { showTooltip = false },
                  title = { Text("Correction Options") },
                  text = {
                    Text(
                        "Perspective and Rotation corrections are mutually exclusive. " +
                            "Enabling one will disable the other. Use Perspective to correct " +
                            "keystone distortion, or Rotation to fix scan orientation.")
                  },
                  confirmButton = {
                    TextButton(onClick = { showTooltip = false }) { Text("OK") }
                  })
            }
          }
        }
  }
}

@Composable
private fun DestinationSelector(
    destination: String,
    onDestinationChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
  var showFolderPicker by remember { mutableStateOf(false) }

  Surface(
      tonalElevation = 1.dp,
      shape = RoundedCornerShape(8.dp),
      modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
              Icon(
                  Icons.Default.Folder,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onSurfaceVariant)

              Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Export Destination",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    destination,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1)
              }

              OutlinedButton(
                  onClick = { showFolderPicker = true },
                  modifier = Modifier.height(32.dp)) {
                    Text("Change", style = MaterialTheme.typography.labelSmall)
                  }
            }
      }

  // Folder picker dialog
  if (showFolderPicker) {
    FolderPickerDialog(
        initialPath = destination,
        onPathSelected = { path ->
          onDestinationChange(path)
          showFolderPicker = false
        },
        onDismiss = { showFolderPicker = false })
  }
}

@Composable
private fun FolderPickerDialog(
    initialPath: String,
    onPathSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
  var folderPath by remember { mutableStateOf(initialPath) }

  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("Select Export Destination") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(
              "Enter the folder path where photos will be exported:",
              style = MaterialTheme.typography.bodyMedium)

          OutlinedTextField(
              value = folderPath,
              onValueChange = { folderPath = it },
              label = { Text("Folder path") },
              placeholder = { Text("/path/to/folder") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth())

          // Quick access buttons
          Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = { folderPath = System.getProperty("user.home") + "/Pictures" }) {
                      Text("Pictures")
                    }
                TextButton(
                    onClick = { folderPath = System.getProperty("user.home") + "/Pictures/PhotoScan" }) {
                      Text("PhotoScan")
                    }
                TextButton(
                    onClick = { folderPath = System.getProperty("user.home") + "/Desktop" }) {
                      Text("Desktop")
                    }
              }
        }
      },
      confirmButton = {
        Button(
            onClick = {
              val path = folderPath.trim()
              if (path.isNotBlank()) {
                // Create directory if it doesn't exist
                val dir = java.io.File(path)
                if (!dir.exists()) {
                  dir.mkdirs()
                }
                onPathSelected(path)
              }
            },
            enabled = folderPath.isNotBlank()) {
              Text("Select")
            }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoSummaryCard(
    box: BoundingBox,
    index: Int,
    isSelected: Boolean,
    config: PhotoConfiguration,
    onSelect: () -> Unit,
    onConfigChange: (PhotoConfiguration) -> Unit,
    onDelete: () -> Unit
) {
  val borderColor =
      if (isSelected) {
        MaterialTheme.colorScheme.primary
      } else {
        MaterialTheme.colorScheme.outlineVariant
      }

  Card(
      modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
      border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
      colors =
          CardDefaults.cardColors(
              containerColor =
                  if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                  } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                  })) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
              // Header row
              Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically) {
                    Text("Photo ${index + 1}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${box.width().toInt()} x ${box.height().toInt()} px",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }

              // Correction options row
              Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalAlignment = Alignment.CenterVertically) {
                    // Perspective correction toggle
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Checkbox(
                          checked = config.perspectiveCorrectionEnabled,
                          onCheckedChange = { enabled ->
                            // Mutex: If enabling perspective, disable rotation
                            onConfigChange(
                                config.copy(
                                    perspectiveCorrectionEnabled = enabled,
                                    rotationCorrectionEnabled =
                                        if (enabled) false else config.rotationCorrectionEnabled
                                )
                            )
                          },
                          modifier = Modifier.height(32.dp))
                      Text("Perspective", style = MaterialTheme.typography.labelSmall)
                    }

                    // Rotation controls
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Checkbox(
                          checked = config.rotationCorrectionEnabled,
                          onCheckedChange = { enabled ->
                            // Mutex: If enabling rotation, disable perspective
                            onConfigChange(
                                config.copy(
                                    rotationCorrectionEnabled = enabled,
                                    perspectiveCorrectionEnabled =
                                        if (enabled) false else config.perspectiveCorrectionEnabled
                                )
                            )
                          },
                          modifier = Modifier.height(32.dp))
                      Text("Rotation", style = MaterialTheme.typography.labelSmall)
                    }

                    Spacer(Modifier.weight(1f))

                    // Aspect ratio dropdown
                    AspectRatioDropdown(
                        selectedRatio = config.aspectRatio,
                        onRatioChange = { ratio ->
                          onConfigChange(config.copy(aspectRatio = ratio))
                        },
                        boxAspectRatio = box.aspectRatio())

                    // Delete button
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                      Icon(
                          Icons.Default.Delete,
                          "Delete",
                          modifier = Modifier.size(18.dp),
                          tint = MaterialTheme.colorScheme.error)
                    }
                  }

              // Rotation angle selection (when rotation enabled)
              if (config.rotationCorrectionEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                      Text("Angle:", style = MaterialTheme.typography.labelSmall)
                      listOf(-90 to "CCW", -180 to "180", 90 to "CW").forEach { (angle, label) ->
                        FilterChip(
                            selected = config.rotationDegrees == angle,
                            onClick = { onConfigChange(config.copy(rotationDegrees = angle)) },
                            label = { Text(label) },
                            modifier = Modifier.height(28.dp))
                      }
                    }
              }
            }
      }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AspectRatioDropdown(
    selectedRatio: Double,
    onRatioChange: (Double) -> Unit,
    boxAspectRatio: Double
) {
  var expanded by remember { mutableStateOf(false) }

  // Find best matching ratio
  val currentRatioName =
      remember(selectedRatio, boxAspectRatio) {
        if (selectedRatio == 0.0) {
          // Auto-select closest ratio
          val bestMatch =
              AspectRatio.entries.minByOrNull { ratio ->
                if (ratio.value == 0.0) Double.MAX_VALUE
                else kotlin.math.abs(ratio.value - boxAspectRatio)
              }
          bestMatch?.displayName ?: "Current"
        } else {
          AspectRatio.entries.find { it.value == selectedRatio }?.displayName ?: "Custom"
        }
      }

  ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
    OutlinedTextField(
        value = currentRatioName,
        onValueChange = {},
        readOnly = true,
        label = { Text("Aspect Ratio") },
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        modifier = Modifier.menuAnchor().width(140.dp).height(40.dp),
        textStyle = MaterialTheme.typography.labelSmall)

    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      AspectRatio.entries.forEach { ratio ->
        DropdownMenuItem(
            text = { Text(ratio.displayName, style = MaterialTheme.typography.labelSmall) },
            onClick = {
              onRatioChange(ratio.value)
              expanded = false
            })
      }
    }
  }
}

private fun cropBoundingBox(image: BufferedImage, box: BoundingBox): BufferedImage? {
  return try {
    val bounds = box.corners
    val minX = minOf(bounds.topLeft.x, bounds.bottomLeft.x).toInt().coerceIn(0, image.width - 1)
    val minY = minOf(bounds.topLeft.y, bounds.topRight.y).toInt().coerceIn(0, image.height - 1)
    val maxX = maxOf(bounds.topRight.x, bounds.bottomRight.x).toInt().coerceIn(0, image.width)
    val maxY = maxOf(bounds.bottomLeft.y, bounds.bottomRight.y).toInt().coerceIn(0, image.height)

    val cropWidth = maxX - minX
    val cropHeight = maxY - minY

    if (cropWidth <= 0 || cropHeight <= 0) return null

    image.getSubimage(minX, minY, cropWidth, cropHeight)
  } catch (e: Exception) {
    null
  }
}
