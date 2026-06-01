package org.kryspetrie.fileimport.ui.screens.wizard.metadata

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import java.awt.image.BufferedImage
import org.kryspetrie.fileimport.application.PerspectiveCorrectionService
import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.PhotoCorner
import org.kryspetrie.fileimport.domain.model.RotationAngle
import org.kryspetrie.fileimport.infrastructure.wizard.BoundingBox
import org.kryspetrie.fileimport.infrastructure.wizard.BoundingBoxList
import org.kryspetrie.fileimport.infrastructure.wizard.PhotoConfiguration
import org.kryspetrie.fileimport.infrastructure.wizard.PhotoScanWizardState

/**
 * Metadata screen for editing EXIF metadata to apply to exported photos.
 *
 * Layout (top-to-bottom):
 * 1. **Top pane**: horizontal scrollable photo thumbnail strip with selection controls
 * 2. **Bottom section**: split into left (preview) and right (editor)
 *    - Left: large preview of selected photo (click to fullscreen), or "No preview" for multi-select
 *    - Right: metadata editor (immediate for single, buffered for multi-edit)
 *
 * Selection modes:
 * - **Single-select** (default): click a photo to select it for editing. Clicking same photo deselects.
 * - **Multi-edit**: entered via "Multi-Edit" button. Checkboxes appear. Edits buffered, applied on button.
 *
 * Clicking the preview image opens a fullscreen overlay with an X close button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataScreen(
    state: PhotoScanWizardState,
    image: BufferedImage,
    perspectiveService: PerspectiveCorrectionService,
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val boundingBoxList by state.boundingBoxList.collectAsState()
    val photoConfigurations by state.photoConfigurations.collectAsState()
    val selectedIndices by state.selectedMetadataIndices.collectAsState()

    // Multi-edit mode: when true, checkboxes appear and clicking toggles selection
    var isMultiEditMode by remember { mutableStateOf(false) }

    // Fullscreen preview state
    var fullscreenPreviewIndex by remember { mutableStateOf<Int?>(null) }

    // Fullscreen overlay
    if (fullscreenPreviewIndex != null && fullscreenPreviewIndex!! < boundingBoxList.size()) {
        val idx = fullscreenPreviewIndex!!
        val box = boundingBoxList.boxes[idx]
        val config = photoConfigurations[box.id] ?: PhotoConfiguration()
        // Fullscreen preview: only depends on visual config (rotation), not text metadata
        val fullPreview = remember(image, box.id, config.rotationDegrees) {
            cropAndRotateBoundingBox(image, box, config, perspectiveService)
        }

        Popup(
            onDismissRequest = { fullscreenPreviewIndex = null },
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (fullPreview != null) {
                    Image(
                        bitmap = fullPreview.toComposeImageBitmap(),
                        contentDescription = "Photo ${idx + 1} fullscreen",
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
                // Close button in top-right corner
                IconButton(
                    onClick = { fullscreenPreviewIndex = null },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close preview",
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Metadata") },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Back")
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = onNext,
                        enabled = boundingBoxList.size() > 0,
                    ) {
                        Text("Next")
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
                    }
                }
            }
        },
    ) { paddingValues ->
        Column(
            modifier = modifier.fillMaxSize().padding(paddingValues),
        ) {
            // ═══ Top pane: photo thumbnail strip ═══
            Column(modifier = Modifier.fillMaxWidth()) {
                // Selection mode header row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isMultiEditMode) {
                        Text(
                            "${selectedIndices.size} selected",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedButton(
                                onClick = { state.selectAllMetadata() },
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) {
                                Text("All", style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = { state.deselectAllMetadata() },
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) {
                                Text("None", style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = {
                                    isMultiEditMode = false
                                    state.deselectAllMetadata()
                                },
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) {
                                Text("Done", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    } else {
                        Text(
                            if (selectedIndices.isEmpty()) "Select a photo to edit"
                            else "Photo ${selectedIndices.first() + 1} of ${boundingBoxList.size()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (boundingBoxList.size() > 1) {
                            OutlinedButton(
                                onClick = { isMultiEditMode = true },
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) {
                                Text("Multi-Edit", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                // Horizontal scrollable thumbnail strip
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(boundingBoxList.boxes) { index, box ->
                        val config = photoConfigurations[box.id] ?: PhotoConfiguration()
                        // Thumbnail: only depends on image, box identity, and rotation (visual),
                        // NOT on text metadata — avoids recomputing crop on every keystroke
                        val previewImage = remember(image, box.id, config.rotationDegrees) {
                            val visualConfig = PhotoConfiguration(rotationDegrees = config.rotationDegrees)
                            cropAndRotateBoundingBox(image, box, visualConfig, perspectiveService)
                        }
                        val isSelected = index in selectedIndices

                        Card(
                            modifier = Modifier
                                .width(120.dp)
                                .height(100.dp)
                                .clickable {
                                    if (isMultiEditMode) {
                                        state.toggleMetadataSelection(index)
                                    } else {
                                        if (isSelected && selectedIndices.size == 1) {
                                            state.deselectAllMetadata()
                                        } else {
                                            state.selectSingleMetadata(index)
                                        }
                                    }
                                },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(
                                if (isSelected) 3.dp else 1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                            ),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = if (isSelected) 2.dp else 0.dp,
                            ),
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                // Thumbnail image
                                if (previewImage != null) {
                                    Image(
                                        bitmap = previewImage.toComposeImageBitmap(),
                                        contentDescription = "Photo ${index + 1}",
                                        modifier = Modifier.fillMaxSize().padding(4.dp),
                                        contentScale = ContentScale.Fit,
                                    )
                                }
                                // Selection indicator
                                if (isSelected) {
                                    if (isMultiEditMode) {
                                        Checkbox(
                                            checked = true,
                                            onCheckedChange = { state.toggleMetadataSelection(index) },
                                            modifier = Modifier.align(Alignment.TopStart).size(20.dp),
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.align(Alignment.TopEnd).size(18.dp),
                                        )
                                    }
                                }
                                // Metadata indicator at bottom
                                if (config.hasMetadata()) {
                                    Text(
                                        "✓",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp),
                                    )
                                }
                                // Photo number label at bottom-left
                                Text(
                                    "${index + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.BottomStart).padding(2.dp),
                                )
                            }
                        }
                    }
                }
            }

            // ═══ Bottom section: preview + editor ═══
            if (selectedIndices.isEmpty()) {
                // No selection — placeholder
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (isMultiEditMode) "Select photos to apply metadata"
                        else "Click a photo above to edit its metadata",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) {
                    // Left pane: preview (single-select) or placeholder (multi-select)
                    if (selectedIndices.size == 1 && !isMultiEditMode) {
                        val selectedIndex = selectedIndices.first()
                        val box = boundingBoxList.boxes[selectedIndex]
                        val config = photoConfigurations[box.id] ?: PhotoConfiguration()
                        // Preview: only depends on visual properties (rotation), not text metadata
                        val previewImage = remember(image, box.id, config.rotationDegrees) {
                            val visualConfig = PhotoConfiguration(rotationDegrees = config.rotationDegrees)
                            cropAndRotateBoundingBox(image, box, visualConfig, perspectiveService)
                        }

                        Box(
                            modifier = Modifier.weight(1f).fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { fullscreenPreviewIndex = selectedIndex },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (previewImage != null) {
                                Image(
                                    bitmap = previewImage.toComposeImageBitmap(),
                                    contentDescription = "Photo ${selectedIndex + 1} preview — click to enlarge",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit,
                                )
                            }
                        }
                    } else {
                        // Multi-select: no preview, just a label
                        Box(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${selectedIndices.size} photos selected",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // Right pane: metadata editor
                    MetadataEditorPane(
                        state = state,
                        boundingBoxList = boundingBoxList,
                        photoConfigurations = photoConfigurations,
                        selectedIndices = selectedIndices,
                        isMultiEditMode = isMultiEditMode,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

/**
 * Metadata editor pane for selected photo(s).
 *
 * - Single-select: immediate-edit fields, values sync to PhotoConfiguration on every keystroke.
 * - Multi-edit: buffered fields, applied only on "Apply to Selected" button press.
 */
@Composable
private fun MetadataEditorPane(
    state: PhotoScanWizardState,
    boundingBoxList: BoundingBoxList,
    photoConfigurations: Map<String, PhotoConfiguration>,
    selectedIndices: Set<Int>,
    isMultiEditMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val isMultiSelect = selectedIndices.size > 1 || isMultiEditMode

    // Buffered values for multi-edit
    var bufferedDescription by remember { mutableStateOf("") }
    var bufferedKeywords by remember { mutableStateOf("") }
    var bufferedOriginalDate by remember { mutableStateOf("") }
    var bufferedYear by remember { mutableStateOf("") }
    var bufferedCameraModel by remember { mutableStateOf("") }
    var bufferedCameraMake by remember { mutableStateOf("") }
    var bufferedLensModel by remember { mutableStateOf("") }
    var bufferedFocalLength by remember { mutableStateOf("") }
    var bufferedAperture by remember { mutableStateOf("") }
    var bufferedShutterSpeed by remember { mutableStateOf("") }
    var bufferedIso by remember { mutableStateOf("") }
    var showAdvanced by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.padding(12.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (isMultiSelect) {
            // Multi-edit header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${selectedIndices.size} photo(s) selected",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Button(
                    onClick = {
                        state.applyMetadataToSelected(
                            description = bufferedDescription,
                            keywords = bufferedKeywords,
                            originalDate = bufferedOriginalDate,
                            year = bufferedYear,
                            cameraModel = bufferedCameraModel,
                            cameraMake = bufferedCameraMake,
                            lensModel = bufferedLensModel,
                            focalLength = bufferedFocalLength,
                            aperture = bufferedAperture,
                            shutterSpeed = bufferedShutterSpeed,
                            iso = bufferedIso,
                        )
                    },
                    modifier = Modifier.height(32.dp),
                ) {
                    Text("Apply to ${selectedIndices.size} Photo(s)", style = MaterialTheme.typography.labelSmall)
                }
            }

            Text(
                "Only filled fields will be applied. Leave blank to keep existing values.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            MetadataField(
                label = "Description",
                placeholder = "Leave blank to keep existing...",
                value = bufferedDescription,
                onValueChange = { bufferedDescription = it },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MetadataField(
                    label = "Keywords",
                    placeholder = "vacation, family, holiday",
                    value = bufferedKeywords,
                    onValueChange = { bufferedKeywords = it },
                    modifier = Modifier.weight(2f),
                )
                MetadataField(
                    label = "Year",
                    placeholder = "1995",
                    value = bufferedYear,
                    onValueChange = { bufferedYear = it.filter { c -> c.isDigit() }.take(4) },
                    modifier = Modifier.weight(1f),
                    keyboardType = KeyboardType.Number,
                )
            }
            MetadataField(
                label = "Original Date",
                placeholder = "YYYY-MM-DD or YYYY-MM-DD HH:MM:SS",
                value = bufferedOriginalDate,
                onValueChange = { bufferedOriginalDate = it },
            )

            AdvancedMetadataSection(
                showAdvanced = showAdvanced,
                onToggleAdvanced = { showAdvanced = !showAdvanced },
                cameraModel = bufferedCameraModel,
                onCameraModelChange = { bufferedCameraModel = it },
                cameraMake = bufferedCameraMake,
                onCameraMakeChange = { bufferedCameraMake = it },
                lensModel = bufferedLensModel,
                onLensModelChange = { bufferedLensModel = it },
                focalLength = bufferedFocalLength,
                onFocalLengthChange = { bufferedFocalLength = it },
                aperture = bufferedAperture,
                onApertureChange = { bufferedAperture = it },
                shutterSpeed = bufferedShutterSpeed,
                onShutterSpeedChange = { bufferedShutterSpeed = it },
                iso = bufferedIso,
                onIsoChange = { bufferedIso = it },
            )
        } else {
            // Single-select: immediate-edit fields
            val selectedIndex = selectedIndices.first()
            val box = boundingBoxList.boxes[selectedIndex]
            val config = photoConfigurations[box.id] ?: PhotoConfiguration()

            Text(
                "Photo ${selectedIndex + 1}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            MetadataField(
                label = "Description",
                placeholder = "Photo description...",
                value = config.description,
                onValueChange = { newValue ->
                    state.updatePhotoConfiguration(box.id) { it.copy(description = newValue) }
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MetadataField(
                    label = "Keywords",
                    placeholder = "vacation, family, holiday",
                    value = config.keywords,
                    onValueChange = { newValue ->
                        state.updatePhotoConfiguration(box.id) { it.copy(keywords = newValue) }
                    },
                    modifier = Modifier.weight(2f),
                )
                MetadataField(
                    label = "Year",
                    placeholder = "1995",
                    value = config.year,
                    onValueChange = { newValue ->
                        state.updatePhotoConfiguration(box.id) {
                            it.copy(year = newValue.filter { c -> c.isDigit() }.take(4))
                        }
                    },
                    modifier = Modifier.weight(1f),
                    keyboardType = KeyboardType.Number,
                )
            }
            MetadataField(
                label = "Original Date",
                placeholder = "YYYY-MM-DD or YYYY-MM-DD HH:MM:SS",
                value = config.originalDate,
                onValueChange = { newValue ->
                    state.updatePhotoConfiguration(box.id) { it.copy(originalDate = newValue) }
                },
            )

            AdvancedMetadataSection(
                showAdvanced = showAdvanced,
                onToggleAdvanced = { showAdvanced = !showAdvanced },
                cameraModel = config.cameraModel,
                onCameraModelChange = { newValue ->
                    state.updatePhotoConfiguration(box.id) { it.copy(cameraModel = newValue) }
                },
                cameraMake = config.cameraMake,
                onCameraMakeChange = { newValue ->
                    state.updatePhotoConfiguration(box.id) { it.copy(cameraMake = newValue) }
                },
                lensModel = config.lensModel,
                onLensModelChange = { newValue ->
                    state.updatePhotoConfiguration(box.id) { it.copy(lensModel = newValue) }
                },
                focalLength = config.focalLength,
                onFocalLengthChange = { newValue ->
                    state.updatePhotoConfiguration(box.id) { it.copy(focalLength = newValue) }
                },
                aperture = config.aperture,
                onApertureChange = { newValue ->
                    state.updatePhotoConfiguration(box.id) { it.copy(aperture = newValue) }
                },
                shutterSpeed = config.shutterSpeed,
                onShutterSpeedChange = { newValue ->
                    state.updatePhotoConfiguration(box.id) { it.copy(shutterSpeed = newValue) }
                },
                iso = config.iso,
                onIsoChange = { newValue ->
                    state.updatePhotoConfiguration(box.id) { it.copy(iso = newValue) }
                },
            )
        }
    }
}

/** A single metadata text field with label. */
@Composable
private fun MetadataField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, style = MaterialTheme.typography.labelSmall) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        textStyle = MaterialTheme.typography.bodyMedium,
    )
}

/** Collapsible advanced camera metadata section. */
@Composable
private fun AdvancedMetadataSection(
    showAdvanced: Boolean,
    onToggleAdvanced: () -> Unit,
    cameraModel: String,
    onCameraModelChange: (String) -> Unit,
    cameraMake: String,
    onCameraMakeChange: (String) -> Unit,
    lensModel: String,
    onLensModelChange: (String) -> Unit,
    focalLength: String,
    onFocalLengthChange: (String) -> Unit,
    aperture: String,
    onApertureChange: (String) -> Unit,
    shutterSpeed: String,
    onShutterSpeedChange: (String) -> Unit,
    iso: String,
    onIsoChange: (String) -> Unit,
) {
    Column {
        OutlinedButton(
            onClick = onToggleAdvanced,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (showAdvanced) "Hide" else "Show",
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text("Camera Settings", style = MaterialTheme.typography.labelSmall)
        }

        AnimatedVisibility(visible = showAdvanced) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MetadataField(
                        label = "Camera Make",
                        placeholder = "Canon",
                        value = cameraMake,
                        onValueChange = onCameraMakeChange,
                        modifier = Modifier.weight(1f),
                    )
                    MetadataField(
                        label = "Camera Model",
                        placeholder = "EOS 5D Mark IV",
                        value = cameraModel,
                        onValueChange = onCameraModelChange,
                        modifier = Modifier.weight(1f),
                    )
                }
                MetadataField(
                    label = "Lens Model",
                    placeholder = "24-70mm f/2.8L",
                    value = lensModel,
                    onValueChange = onLensModelChange,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MetadataField(
                        label = "Focal Length",
                        placeholder = "50mm",
                        value = focalLength,
                        onValueChange = onFocalLengthChange,
                        modifier = Modifier.weight(1f),
                    )
                    MetadataField(
                        label = "Aperture",
                        placeholder = "f/2.8",
                        value = aperture,
                        onValueChange = onApertureChange,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MetadataField(
                        label = "Shutter Speed",
                        placeholder = "1/125",
                        value = shutterSpeed,
                        onValueChange = onShutterSpeedChange,
                        modifier = Modifier.weight(1f),
                    )
                    MetadataField(
                        label = "ISO",
                        placeholder = "400",
                        value = iso,
                        onValueChange = onIsoChange,
                        modifier = Modifier.weight(1f),
                        keyboardType = KeyboardType.Number,
                    )
                }
            }
        }
    }
}

/**
 * Crops the bounding box region from the source image, applies perspective correction
 * (warp-stretch, always on), then rotates according to the [PhotoConfiguration].
 */
private fun cropAndRotateBoundingBox(
    image: BufferedImage,
    box: BoundingBox,
    config: PhotoConfiguration,
    perspectiveService: PerspectiveCorrectionService,
): BufferedImage? {
    return try {
        val detectedPhoto = boxToDetectedPhoto(box)
        val corrected = perspectiveService.correctPerspective(image, detectedPhoto)
        if (config.rotationDegrees != 0) {
            rotateBufferedImage(corrected, rotationFromDegrees(config.rotationDegrees))
        } else {
            corrected
        }
    } catch (_: Exception) {
        null
    }
}

/** Rotates a [BufferedImage] by the given [RotationAngle]. */
private fun rotateBufferedImage(image: BufferedImage, rotation: RotationAngle): BufferedImage {
    if (rotation == RotationAngle.NONE) return image
    val newWidth: Int
    val newHeight: Int
    when (rotation) {
        RotationAngle.CW_90,
        RotationAngle.CCW_90 -> {
            newWidth = image.height
            newHeight = image.width
        }
        else -> {
            newWidth = image.width
            newHeight = image.height
        }
    }
    val rotated = BufferedImage(
        newWidth.coerceAtLeast(1),
        newHeight.coerceAtLeast(1),
        BufferedImage.TYPE_INT_RGB,
    )
    val graphics = rotated.createGraphics()
    graphics.background = java.awt.Color.BLACK
    when (rotation) {
        RotationAngle.CW_90 -> {
            graphics.translate(newWidth, 0)
            graphics.rotate(Math.PI / 2)
        }
        RotationAngle.CCW_90 -> {
            graphics.translate(0, newHeight)
            graphics.rotate(-Math.PI / 2)
        }
        RotationAngle.CW_180 -> {
            graphics.translate(newWidth / 2.0, newHeight / 2.0)
            graphics.rotate(Math.PI)
            graphics.translate(-image.width / 2.0, -image.height / 2.0)
        }
        RotationAngle.NONE -> {
            // No rotation
        }
    }
    graphics.drawImage(image, 0, 0, null)
    graphics.dispose()
    return rotated
}

/** Converts degrees (0, 90, 180, 270) to RotationAngle. */
private fun rotationFromDegrees(degrees: Int): RotationAngle {
    return when (degrees) {
        90 -> RotationAngle.CW_90
        180 -> RotationAngle.CW_180
        270 -> RotationAngle.CCW_90
        -90 -> RotationAngle.CCW_90
        else -> RotationAngle.NONE
    }
}

/** Converts a [BoundingBox] to a [DetectedPhoto] for perspective correction. */
private fun boxToDetectedPhoto(box: BoundingBox): DetectedPhoto {
    return DetectedPhoto(
        topLeft = PhotoCorner(box.corners.topLeft.x.toFloat(), box.corners.topLeft.y.toFloat()),
        topRight = PhotoCorner(box.corners.topRight.x.toFloat(), box.corners.topRight.y.toFloat()),
        bottomLeft = PhotoCorner(box.corners.bottomLeft.x.toFloat(), box.corners.bottomLeft.y.toFloat()),
        bottomRight = PhotoCorner(box.corners.bottomRight.x.toFloat(), box.corners.bottomRight.y.toFloat()),
    )
}