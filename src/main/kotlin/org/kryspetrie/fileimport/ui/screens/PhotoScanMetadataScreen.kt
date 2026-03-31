package org.kryspetrie.fileimport.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration

/**
 * Photo Scan Metadata Screen.
 *
 * Allows editing EXIF metadata overrides for each detected photo in a scan.
 *
 * ## Editable Fields
 * - Original Date Override
 * - Original Year Override
 * - Original Month Override
 * - Tags (comma-separated)
 * - Notes (free text)
 *
 * ## UI Layout
 *
 * ```
 * ┌─────────────────────────────────────────────────────────────┐
 * │  Photo Metadata                                    [3/10]   │
 * ├─────────────────────────────────────────────────────────────┤
 * │                                                             │
 * │  ┌──────────┐ ┌──────────┐ ┌──────────┐                    │
 * │  │ Photo 1  │ │ Photo 2* │ │ Photo 3  │  ← Select photo   │
 * │  │ ✓        │ │         │ │          │                    │
 * │  └──────────┘ └──────────┘ └──────────┘                    │
 * │                                                             │
 * │  ┌─ Metadata ─────────────────────────────────────────────┐│
 * │  │                                                         ││
 * │  │ Original Date:  [1995-06-15          ]                 ││
 * │  │                                                         ││
 * │  │ Year:          [1995                ]                  ││
 * │  │ Month:         [06                  ]                  ││
 * │  │                                                         ││
 * │  │ Tags:          [vacation, family    ]                  ││
 * │  │                                                         ││
 * │  │ Notes:                                                ││
 * │  │ ┌───────────────────────────────────────────────────┐ ││
 * │  │ │ Christmas morning, Grandma's house                │ ││
 * │  │ └───────────────────────────────────────────────────┘ ││
 * │  │                                                         ││
 * │  │ [Apply to All Photos]                                 ││
 * │  │                                                         ││
 * └─────────────────────────────────────────────────────────────┘│
 * │                                                             │
 * │  Image: scan_001.jpg                        [← Back] [Export →]│
 * └─────────────────────────────────────────────────────────────┘
 * ```
 *
 * @param photos List of detected photos
 * @param selectedPhotoId Currently selected photo ID
 * @param onPhotoSelect Callback when a photo is selected
 * @param onConfigurationChange Callback when metadata configuration changes
 * @param onApplyToAll Callback to apply current configuration to all photos
 * @param onBack Callback to go back to corner editing
 * @param onExport Callback to proceed to export
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoScanMetadataScreen(
    photos: List<DetectedPhoto>,
    selectedPhotoId: String?,
    onPhotoSelect: (String) -> Unit,
    onConfigurationChange: (String, PhotoScanConfiguration) -> Unit,
    onApplyToAll: (PhotoScanConfiguration) -> Unit,
    onBack: () -> Unit,
    onExport: () -> Unit
) {
  val selectedPhoto = photos.find { it.id == selectedPhotoId }
  var showApplyToAllDialog by remember { mutableStateOf(false) }

  Column(modifier = Modifier.fillMaxSize()) {
    // Top bar
    TopAppBar(
        title = { Text("Photo Metadata") },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } })

    LazyColumn(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
          // Photo thumbnail selector
          item {
            Text(
                "Select Photo",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
              items(photos) { photo ->
                PhotoThumbnail(
                    photo = photo,
                    isSelected = photo.id == selectedPhotoId,
                    onClick = { onPhotoSelect(photo.id) })
              }
            }
          }

          // Metadata form for selected photo
          item {
            if (selectedPhoto != null) {
              MetadataForm(
                  configuration = selectedPhoto.configuration,
                  onConfigurationChange = { config ->
                    onConfigurationChange(selectedPhoto.id, config)
                  },
                  onApplyToAll = { showApplyToAllDialog = true })
            } else {
              Card(
                  modifier = Modifier.fillMaxWidth(),
                  colors =
                      CardDefaults.cardColors(
                          containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center) {
                          Text(
                              "Select a photo to edit its metadata",
                              style = MaterialTheme.typography.bodyMedium,
                              color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                  }
            }
          }

          // Quick stats
          item {
            Card(modifier = Modifier.fillMaxWidth()) {
              Column(
                  modifier = Modifier.padding(16.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Summary",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary)
                    HorizontalDivider()

                    val photosWithDate =
                        photos.count { it.configuration.originalDateOverride?.isNotBlank() == true }
                    val photosWithTags = photos.count { it.configuration.tags.isNotBlank() }
                    val photosWithNotes = photos.count { it.configuration.notes.isNotBlank() }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly) {
                          StatItem("Photos", "${photos.size}")
                          StatItem("With Date", "$photosWithDate")
                          StatItem("With Tags", "$photosWithTags")
                          StatItem("With Notes", "$photosWithNotes")
                        }
                  }
            }
          }
        }

    // Bottom navigation
    Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 2.dp) {
      Row(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) {
              Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
              Spacer(Modifier.width(4.dp))
              Text("Back to Corners")
            }

            Spacer(Modifier.weight(1f))

            Button(onClick = onExport, enabled = photos.isNotEmpty()) {
              Icon(Icons.Default.Download, null, Modifier.size(18.dp))
              Spacer(Modifier.width(4.dp))
              Text("Export ${photos.size} Photo(s)")
            }
          }
    }
  }

  // Apply to all confirmation dialog
  if (showApplyToAllDialog) {
    AlertDialog(
        onDismissRequest = { showApplyToAllDialog = false },
        title = { Text("Apply to All Photos?") },
        text = {
          Text(
              "This will apply the current metadata to all ${photos.size} detected photos. This is useful for setting a common date or tags across all photos from the same scan.")
        },
        confirmButton = {
          Button(
              onClick = {
                selectedPhoto?.configuration?.let { onApplyToAll(it) }
                showApplyToAllDialog = false
              }) {
                Text("Apply to All")
              }
        },
        dismissButton = {
          TextButton(onClick = { showApplyToAllDialog = false }) { Text("Cancel") }
        })
  }
}

/** Photo thumbnail component with selection state. */
@Composable
private fun PhotoThumbnail(photo: DetectedPhoto, isSelected: Boolean, onClick: () -> Unit) {
  Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.width(80.dp).clickable(onClick = onClick)) {
        Box(
            modifier =
                Modifier.size(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color =
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center) {
              // Show corners preview
              Box(modifier = Modifier.size(60.dp)) {
                // Simple representation of the quadrilateral
                val offset = 10.dp
              }

              if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd))
              }
            }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "${photo.getWidth()}×${photo.getHeight()}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
}

/** Metadata editing form. */
@Composable
private fun MetadataForm(
    configuration: PhotoScanConfiguration,
    onConfigurationChange: (PhotoScanConfiguration) -> Unit,
    onApplyToAll: () -> Unit
) {
  var originalDate by
      remember(configuration) { mutableStateOf(configuration.originalDateOverride ?: "") }
  var originalYear by
      remember(configuration) { mutableStateOf(configuration.originalYearOverride ?: "") }
  var originalMonth by
      remember(configuration) { mutableStateOf(configuration.originalMonthOverride ?: "") }
  var tags by remember(configuration) { mutableStateOf(configuration.tags) }
  var notes by remember(configuration) { mutableStateOf(configuration.notes) }

  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
      Text(
          "Metadata Override",
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.primary)

      // Date fields
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = originalDate,
            onValueChange = {
              originalDate = it
              onConfigurationChange(configuration.copy(originalDateOverride = it.ifBlank { null }))
            },
            label = { Text("Original Date") },
            placeholder = { Text("YYYY-MM-DD") },
            modifier = Modifier.weight(2f),
            singleLine = true)

        OutlinedTextField(
            value = originalYear,
            onValueChange = {
              originalYear = it.filter { c -> c.isDigit() }.take(4)
              onConfigurationChange(configuration.copy(originalYearOverride = it.ifBlank { null }))
            },
            label = { Text("Year") },
            placeholder = { Text("1995") },
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))

        OutlinedTextField(
            value = originalMonth,
            onValueChange = {
              originalMonth = it.filter { c -> c.isDigit() }.take(2)
              onConfigurationChange(configuration.copy(originalMonthOverride = it.ifBlank { null }))
            },
            label = { Text("Month") },
            placeholder = { Text("06") },
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
      }

      // Tags
      OutlinedTextField(
          value = tags,
          onValueChange = {
            tags = it
            onConfigurationChange(configuration.copy(tags = it))
          },
          label = { Text("Tags") },
          placeholder = { Text("vacation, family, holiday") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
          supportingText = { Text("Comma-separated tags") })

      // Notes
      OutlinedTextField(
          value = notes,
          onValueChange = {
            notes = it
            onConfigurationChange(configuration.copy(notes = it))
          },
          label = { Text("Notes") },
          placeholder = { Text("Additional notes about this photo...") },
          modifier = Modifier.fillMaxWidth(),
          minLines = 3,
          maxLines = 5)

      // Actions
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onApplyToAll) {
          Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp))
          Spacer(Modifier.width(4.dp))
          Text("Apply to All")
        }
      }

      // Clear button
      if (hasOverrides(configuration)) {
        TextButton(
            onClick = {
              originalDate = ""
              originalYear = ""
              originalMonth = ""
              tags = ""
              notes = ""
              onConfigurationChange(PhotoScanConfiguration())
            },
            colors =
                ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
              Icon(Icons.Default.Clear, null, Modifier.size(18.dp))
              Spacer(Modifier.width(4.dp))
              Text("Clear Overrides")
            }
      }
    }
  }
}

/** Statistics item. */
@Composable
private fun StatItem(label: String, value: String) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
        value,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary)
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

/** Checks if configuration has any overrides set. */
private fun hasOverrides(config: PhotoScanConfiguration): Boolean {
  return config.originalDateOverride?.isNotBlank() == true ||
      config.originalYearOverride?.isNotBlank() == true ||
      config.originalMonthOverride?.isNotBlank() == true ||
      config.tags.isNotBlank() ||
      config.notes.isNotBlank()
}
