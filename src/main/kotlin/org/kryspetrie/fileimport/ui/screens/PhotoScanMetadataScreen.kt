package org.kryspetrie.fileimport.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration

/**
 * Photo Scan Metadata Screen (legacy — superseded by QuickEditScreen).
 *
 * Allows editing EXIF metadata overrides for each detected photo in a scan. This screen uses the
 * old separate date/tags/notes fields. The QuickEditScreen now provides a more streamlined UX.
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
    onExport: () -> Unit,
) {
    val selectedPhoto = photos.find { it.id == selectedPhotoId }
    var showApplyToAllDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        TopAppBar(
            title = { Text("Photo Metadata") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            },
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Photo thumbnail selector
            item {
                Text(
                    "Select Photo",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(photos) { photo ->
                        PhotoThumbnail(
                            photo = photo,
                            isSelected = photo.id == selectedPhotoId,
                            onClick = { onPhotoSelect(photo.id) },
                        )
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
                        onApplyToAll = { showApplyToAllDialog = true },
                    )
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "Select a photo to edit its metadata",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Quick stats
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Summary",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        HorizontalDivider()

                        val photosWithDate =
                            photos.count { it.configuration.originalDate.isNotBlank() }
                        val photosWithKeywords =
                            photos.count { it.configuration.keywords.isNotBlank() }
                        val photosWithDescription =
                            photos.count { it.configuration.description.isNotBlank() }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            StatItem("Photos", "${photos.size}")
                            StatItem("With Date", "$photosWithDate")
                            StatItem("With Keywords", "$photosWithKeywords")
                            StatItem("With Desc", "$photosWithDescription")
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
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                    "This will apply the current metadata to all ${photos.size} detected photos. " +
                        "This is useful for setting a common date or tags across all photos from the same scan."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedPhoto?.configuration?.let { onApplyToAll(it) }
                        showApplyToAllDialog = false
                    }
                ) {
                    Text("Apply to All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showApplyToAllDialog = false }) { Text("Cancel") }
            },
        )
    }
}

/** Photo thumbnail component with selection state. */
@Composable
private fun PhotoThumbnail(photo: DetectedPhoto, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(80.dp).clickable(onClick = onClick),
    ) {
        Box(
            modifier =
                Modifier.size(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color =
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            // Show corners preview
            Box(modifier = Modifier.size(60.dp)) {
                // Simple representation of the quadrilateral
            }

            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "${photo.getWidth()}×${photo.getHeight()}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Metadata editing form. */
@Composable
private fun MetadataForm(
    configuration: PhotoScanConfiguration,
    onConfigurationChange: (PhotoScanConfiguration) -> Unit,
    onApplyToAll: () -> Unit,
) {
    var originalDate by remember(configuration) { mutableStateOf(configuration.originalDate) }
    var year by remember(configuration) { mutableStateOf(configuration.year) }
    var keywords by remember(configuration) { mutableStateOf(configuration.keywords) }
    var description by remember(configuration) { mutableStateOf(configuration.description) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Metadata Override",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            // Date fields
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = originalDate,
                    onValueChange = {
                        originalDate = it
                        onConfigurationChange(configuration.copy(originalDate = it))
                    },
                    label = { Text("Original Date") },
                    placeholder = { Text("YYYY-MM-DD") },
                    modifier = Modifier.weight(2f),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = year,
                    onValueChange = {
                        year = it.filter { c -> c.isDigit() }.take(4)
                        onConfigurationChange(
                            configuration.copy(year = it.filter { c -> c.isDigit() }.take(4))
                        )
                    },
                    label = { Text("Year") },
                    placeholder = { Text("1995") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }

            // Keywords
            OutlinedTextField(
                value = keywords,
                onValueChange = {
                    keywords = it
                    onConfigurationChange(configuration.copy(keywords = it))
                },
                label = { Text("Keywords") },
                placeholder = { Text("vacation, family, holiday") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = { Text("Comma-separated keywords") },
            )

            // Description
            OutlinedTextField(
                value = description,
                onValueChange = {
                    description = it
                    onConfigurationChange(configuration.copy(description = it))
                },
                label = { Text("Description") },
                placeholder = { Text("Additional description about this photo...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
            )

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
                        year = ""
                        keywords = ""
                        description = ""
                        onConfigurationChange(PhotoScanConfiguration())
                    },
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                ) {
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
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Checks if configuration has any overrides set. */
private fun hasOverrides(config: PhotoScanConfiguration): Boolean {
    return config.originalDate.isNotBlank() ||
        config.year.isNotBlank() ||
        config.keywords.isNotBlank() ||
        config.description.isNotBlank()
}
