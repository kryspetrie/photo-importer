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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import java.awt.image.BufferedImage
import org.koin.compose.koinInject
import org.kryspetrie.fileimport.application.PerspectiveCorrectionService
import org.kryspetrie.fileimport.domain.model.FaceRegion
import org.kryspetrie.fileimport.domain.model.GeometryUtils
import org.kryspetrie.fileimport.domain.model.MetadataHistory
import org.kryspetrie.fileimport.domain.model.OverrideState
import org.kryspetrie.fileimport.domain.model.RegionType
import org.kryspetrie.fileimport.domain.port.DispatcherProvider
import org.kryspetrie.fileimport.domain.port.GeocodingPort
import org.kryspetrie.fileimport.domain.port.ImageRepositoryPort
import org.kryspetrie.fileimport.domain.port.LocationSearchPort
import org.kryspetrie.fileimport.infrastructure.wizard.BoundingBoxList
import org.kryspetrie.fileimport.infrastructure.wizard.FaceSize
import org.kryspetrie.fileimport.infrastructure.wizard.PhotoConfiguration
import org.kryspetrie.fileimport.infrastructure.wizard.PhotoScanWizardState
import org.kryspetrie.fileimport.infrastructure.wizard.SourceExifSummary
import org.kryspetrie.fileimport.ui.components.ChunkyScrollbar
import org.kryspetrie.fileimport.ui.screens.wizard.FaceSelectorOverlay
import org.kryspetrie.fileimport.ui.screens.wizard.boxToDetectedPhoto
import org.kryspetrie.fileimport.ui.screens.wizard.cropAndRotateBoundingBox
import org.kryspetrie.fileimport.ui.screens.wizard.regionTypeIcon

/**
 * Metadata screen for editing EXIF metadata to apply to exported photos.
 *
 * Layout (top-to-bottom):
 * 1. **Top pane**: horizontal scrollable photo thumbnail strip with selection controls
 * 2. **Bottom section**: split into left (preview) and right (editor)
 *     - Left: large preview of selected photo (click to fullscreen), or "No preview" for
 *       multi-select
 *     - Right: metadata editor (immediate for single, buffered for multi-edit)
 *
 * Selection modes:
 * - **Single-select** (default): click a photo to select it for editing. Clicking same photo
 *   deselects.
 * - **Multi-edit**: entered via "Multi-Edit" button. Checkboxes appear. Edits buffered, applied on
 *   button.
 *
 * Clicking the preview image opens a fullscreen overlay with an X close button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataScreen(
    state: PhotoScanWizardState,
    image: BufferedImage,
    perspectiveService: PerspectiveCorrectionService,
    metadataHistory: MetadataHistory,
    onMetadataHistoryUpdate: (String, String) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    faceRegionTransformer: org.kryspetrie.fileimport.application.FaceRegionTransformer? = null,
) {
    val locationSearchService: LocationSearchPort = koinInject()
    val geocodingPort: GeocodingPort = koinInject()
    val dispatcherProvider: DispatcherProvider = koinInject()
    val imageRepository: ImageRepositoryPort = koinInject()
    var showLocationPicker by remember { mutableStateOf(false) }
    var locationPickerTargetIndex by remember { mutableStateOf<Int?>(null) }

    val boundingBoxList by state.boundingBoxList.collectAsState()
    val photoConfigurations by state.photoConfigurations.collectAsState()
    val selectedIndices by state.selectedMetadataIndices.collectAsState()
    val sourceExif by state.sourceExif.collectAsState()

    // Auto-select first photo when entering the metadata screen
    LaunchedEffect(boundingBoxList.size()) {
        if (selectedIndices.isEmpty() && boundingBoxList.size() > 0) {
            state.selectSingleMetadata(0)
        }
    }

    // Read source EXIF when entering the metadata screen (only once per source file)
    LoadSourceExifEffect(
        imageFile = state.imageFile.value,
        sourceExif = sourceExif,
        state = state,
        imageRepository = imageRepository,
        dispatcherProvider = dispatcherProvider,
    )

    // Multi-edit mode: when true, checkboxes appear and clicking toggles selection
    var isMultiEditMode by remember { mutableStateOf(false) }

    // Fullscreen preview state
    var fullscreenPreviewIndex by remember { mutableStateOf<Int?>(null) }

    // Face selection state
    var faceSelectIndex by remember { mutableStateOf<Int?>(null) }
    // Stores (photoIndex, normalizedX, normalizedY) for pending face placement
    var pendingFaceCoords by remember { mutableStateOf<Triple<Int, Double, Double>?>(null) }
    var showFaceNamePopup by remember { mutableStateOf(false) }
    var faceNameInput by remember { mutableStateOf("") }
    var selectedRegionType by remember { mutableStateOf(RegionType.FACE) }
    var selectedFaceSize by remember { mutableStateOf(FaceSize.DEFAULT) }

    // Inherited face regions from source image XMP (read when face select opens)
    var inheritedFaceRegions by remember { mutableStateOf<List<FaceRegion>>(emptyList()) }

    // Fullscreen overlay (either preview or face selection)
    val showFullscreenPreview =
        fullscreenPreviewIndex != null && fullscreenPreviewIndex!! < boundingBoxList.size()
    val showFaceSelect = faceSelectIndex != null && faceSelectIndex!! < boundingBoxList.size()

    if (showFaceSelect && faceSelectIndex != null) {
        val idx = faceSelectIndex!!
        val box = boundingBoxList.boxes[idx]
        val config = photoConfigurations[box.id] ?: PhotoConfiguration()
        val fullPreview =
            remember(image, box.id, config.rotationDegrees) {
                cropAndRotateBoundingBox(image, box, config, perspectiveService)
            }

        // Compute inherited face regions from source image XMP
        val sourceFile = state.imageFile.value
        LaunchedEffect(faceSelectIndex, sourceFile) {
            inheritedFaceRegions =
                if (
                    faceSelectIndex != null && faceRegionTransformer != null && sourceFile != null
                ) {
                    try {
                        val marginFraction = state.exportMarginPercent.value
                        val perspectiveEnabled = state.perspectiveCorrectionEnabled.value
                        val detectedPhoto =
                            boxToDetectedPhoto(box, perspectiveEnabled, config.rotationDegrees)
                        val marginedPhoto =
                            if (marginFraction > 0.0) {
                                GeometryUtils.applyMargin(detectedPhoto, marginFraction)
                            } else detectedPhoto
                        // preRotationWidth/Height: compute from corrected image (before rotation)
                        val corrected = perspectiveService.correctPerspective(image, marginedPhoto)
                        val regions =
                            faceRegionTransformer.transformFaceRegionsFromSource(
                                sourceFile = sourceFile,
                                detectedPhoto = marginedPhoto,
                                outputWidth = corrected.width,
                                outputHeight = corrected.height,
                                sourceWidth = image.width,
                                sourceHeight = image.height,
                                marginFraction = marginFraction,
                            )
                        // Filter out regions whose names already exist as user-specified regions
                        val existingNames = config.faceRegions.map { it.name }.toSet()
                        regions
                            .filter { it.name !in existingNames }
                            .map { region ->
                                FaceRegion(
                                    name = region.name,
                                    type = region.type,
                                    x = region.x,
                                    y = region.y,
                                    w = region.w,
                                    h = region.h,
                                )
                            }
                    } catch (_: Exception) {
                        emptyList()
                    }
                } else emptyList()
        }

        if (faceSelectIndex != null && fullPreview != null) {
            FaceSelectorOverlay(
                fullPreview = fullPreview,
                idx = idx,
                photoConfig = config,
                state = state,
                selectedRegionType = selectedRegionType,
                selectedFaceSize = selectedFaceSize,
                onRegionTypeChange = { selectedRegionType = it },
                onFaceSizeChange = { selectedFaceSize = it },
                onPlaceFace = { normX, normY ->
                    pendingFaceCoords = Triple(idx, normX, normY)
                    showFaceNamePopup = true
                    faceNameInput = ""
                },
                onDismiss = { faceSelectIndex = null },
                inheritedFaceRegions = inheritedFaceRegions,
            )
        }

        // Face name entry popup — simplified (type and size selected from overlay toolbar)
        if (showFaceNamePopup && pendingFaceCoords != null) {
            Dialog(
                onDismissRequest = {
                    showFaceNamePopup = false
                    pendingFaceCoords = null
                }
            ) {
                Surface(
                    modifier = Modifier.width(260.dp),
                    tonalElevation = 8.dp,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            "Name this \${selectedRegionType.displayName.lowercase()}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                regionTypeIcon(selectedRegionType),
                                contentDescription = selectedRegionType.displayName,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "\${selectedRegionType.displayName} • \${selectedFaceSize.displayName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextField(
                            value = faceNameInput,
                            onValueChange = { faceNameInput = it },
                            placeholder = {
                                Text("Name…", style = MaterialTheme.typography.bodySmall)
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyMedium,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    showFaceNamePopup = false
                                    pendingFaceCoords = null
                                    faceNameInput = ""
                                }
                            ) {
                                Text("Cancel", style = MaterialTheme.typography.labelSmall)
                            }
                            Button(
                                onClick = {
                                    if (faceNameInput.isNotBlank()) {
                                        val (photoIdx, normX, normY) = pendingFaceCoords!!
                                        state.addFaceRegion(
                                            photoIdx,
                                            faceNameInput.trim(),
                                            normX,
                                            normY,
                                            selectedRegionType,
                                            selectedFaceSize,
                                        )
                                    }
                                    showFaceNamePopup = false
                                    pendingFaceCoords = null
                                    faceNameInput = ""
                                },
                                enabled = faceNameInput.isNotBlank(),
                            ) {
                                Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Save", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    } else if (showFullscreenPreview) {

        // Location picker popup
        if (showLocationPicker && locationPickerTargetIndex != null) {
            // Full-screen overlay for the map-based location picker
            Popup(
                onDismissRequest = {
                    showLocationPicker = false
                    locationPickerTargetIndex = null
                }
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        tonalElevation = 8.dp,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        LocationPickerDialog(
                            locationSearchService = locationSearchService,
                            geocodingPort = geocodingPort,
                            dispatcherProvider = dispatcherProvider,
                            onLocationSelected = { result ->
                                val idx = locationPickerTargetIndex
                                if (idx != null && idx < boundingBoxList.size()) {
                                    val boxId = boundingBoxList.boxes[idx].id
                                    state.updatePhotoConfiguration(boxId) {
                                        it.copy(
                                            city = result.city ?: it.city,
                                            state = result.state ?: it.state,
                                            country = result.country ?: it.country,
                                            gpsLatitude = result.latitude.toString(),
                                            gpsLongitude = result.longitude.toString(),
                                        )
                                    }
                                }
                                showLocationPicker = false
                                locationPickerTargetIndex = null
                            },
                            onDismiss = {
                                showLocationPicker = false
                                locationPickerTargetIndex = null
                            },
                        )
                    }
                }
            }
        }
        val idx = fullscreenPreviewIndex!!
        val box = boundingBoxList.boxes[idx]
        val config = photoConfigurations[box.id] ?: PhotoConfiguration()
        val fullPreview =
            remember(image, box.id, config.rotationDegrees) {
                cropAndRotateBoundingBox(image, box, config, perspectiveService)
            }

        Popup(onDismissRequest = { fullscreenPreviewIndex = null }) {
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
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
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
        topBar = { TopAppBar(title = { Text("Metadata") }) },
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
                    Button(onClick = onNext, enabled = boundingBoxList.size() > 0) {
                        Text("Next")
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
                    }
                }
            }
        },
    ) { paddingValues ->
        Column(modifier = modifier.fillMaxSize().padding(paddingValues)) {
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
                            else
                                "Photo ${selectedIndices.first() + 1} of ${boundingBoxList.size()}",
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
                        val previewImage =
                            remember(image, box.id, config.rotationDegrees) {
                                val visualConfig =
                                    PhotoConfiguration(rotationDegrees = config.rotationDegrees)
                                cropAndRotateBoundingBox(
                                    image,
                                    box,
                                    visualConfig,
                                    perspectiveService,
                                )
                            }
                        val isSelected = index in selectedIndices

                        Card(
                            modifier =
                                Modifier.width(120.dp).height(100.dp).clickable {
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
                            border =
                                BorderStroke(
                                    if (isSelected) 3.dp else 1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                ),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor =
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant
                                ),
                            elevation =
                                CardDefaults.cardElevation(
                                    defaultElevation = if (isSelected) 2.dp else 0.dp
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
                                            onCheckedChange = {
                                                state.toggleMetadataSelection(index)
                                            },
                                            modifier =
                                                Modifier.align(Alignment.TopStart).size(20.dp),
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
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    // Left pane: preview (single-select) or placeholder (multi-select)
                    if (selectedIndices.size == 1 && !isMultiEditMode) {
                        val selectedIndex = selectedIndices.first()
                        val box = boundingBoxList.boxes[selectedIndex]
                        val config = photoConfigurations[box.id] ?: PhotoConfiguration()
                        // Preview: only depends on visual properties (rotation), not text metadata
                        val previewImage =
                            remember(image, box.id, config.rotationDegrees) {
                                val visualConfig =
                                    PhotoConfiguration(rotationDegrees = config.rotationDegrees)
                                cropAndRotateBoundingBox(
                                    image,
                                    box,
                                    visualConfig,
                                    perspectiveService,
                                )
                            }

                        Box(
                            modifier =
                                Modifier.weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { fullscreenPreviewIndex = selectedIndex },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (previewImage != null) {
                                Image(
                                    bitmap = previewImage.toComposeImageBitmap(),
                                    contentDescription =
                                        "Photo ${selectedIndex + 1} preview — click to enlarge",
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
                        metadataHistory = metadataHistory,
                        onMetadataHistoryUpdate = onMetadataHistoryUpdate,
                        sourceExif = sourceExif,
                        onSelectFaces = { photoIndex -> faceSelectIndex = photoIndex },
                        onSearchLocation = { photoIndex ->
                            locationPickerTargetIndex = photoIndex
                            showLocationPicker = true
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

/**
 * Metadata editor pane for selected photo(s).
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
    metadataHistory: MetadataHistory,
    onMetadataHistoryUpdate: (String, String) -> Unit,
    onSelectFaces: (Int) -> Unit,
    onSearchLocation: ((Int) -> Unit)? = null,
    sourceExif: SourceExifSummary? = null,
    modifier: Modifier = Modifier,
) {
    val isMultiSelect = selectedIndices.size > 1 || isMultiEditMode

    // Buffered values for multi-edit
    val editState = remember { MetadataEditState() }
    var showAdvanced by remember { mutableStateOf(false) }

    // Focus requesters for Tab navigation (single-select mode)
    val descriptionFocusRequester = remember { FocusRequester() }
    val keywordsFocusRequester = remember { FocusRequester() }
    val yearFocusRequester = remember { FocusRequester() }
    val originalDateFocusRequester = remember { FocusRequester() }
    val subjectInputFocusRequester = remember { FocusRequester() }
    val cameraModelFocusRequester = remember { FocusRequester() }
    val cameraMakeFocusRequester = remember { FocusRequester() }
    val lensModelFocusRequester = remember { FocusRequester() }
    val focalLengthFocusRequester = remember { FocusRequester() }
    val apertureFocusRequester = remember { FocusRequester() }
    val shutterSpeedFocusRequester = remember { FocusRequester() }
    val isoFocusRequester = remember { FocusRequester() }

    ChunkyScrollbar(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
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
                        onClick = { state.applyMetadataToSelected(editState) },
                        modifier = Modifier.height(32.dp),
                    ) {
                        Text(
                            "Apply to ${selectedIndices.size} Photo(s)",
                            style = MaterialTheme.typography.labelSmall,
                        )
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
                    value = editState.description,
                    onValueChange = { editState.description = it },
                    suggestions = metadataHistory.description,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MetadataField(
                        label = "Keywords",
                        placeholder = "vacation, family, holiday",
                        value = editState.keywords,
                        onValueChange = { editState.keywords = it },
                        modifier = Modifier.weight(2f),
                        suggestions = metadataHistory.keywords,
                    )
                    MetadataField(
                        label = "Year",
                        placeholder = "1995",
                        value = editState.year,
                        onValueChange = { editState.year = it.filter { c -> c.isDigit() }.take(4) },
                        modifier = Modifier.weight(1f),
                        keyboardType = KeyboardType.Number,
                        suggestions = metadataHistory.year,
                    )
                }
                MetadataField(
                    label = "Original Date",
                    placeholder = "YYYY-MM-DD or YYYY-MM-DD HH:MM:SS",
                    value = editState.originalDate,
                    onValueChange = { editState.originalDate = it },
                    suggestions = metadataHistory.originalDate,
                )

                AdvancedMetadataSection(
                    showAdvanced = showAdvanced,
                    onToggleAdvanced = { showAdvanced = !showAdvanced },
                    cameraModel = editState.cameraModel,
                    onCameraModelChange = { editState.cameraModel = it },
                    cameraMake = editState.cameraMake,
                    onCameraMakeChange = { editState.cameraMake = it },
                    lensModel = editState.lensModel,
                    onLensModelChange = { editState.lensModel = it },
                    focalLength = editState.focalLength,
                    onFocalLengthChange = { editState.focalLength = it },
                    aperture = editState.aperture,
                    onApertureChange = { editState.aperture = it },
                    shutterSpeed = editState.shutterSpeed,
                    onShutterSpeedChange = { editState.shutterSpeed = it },
                    iso = editState.iso,
                    onIsoChange = { editState.iso = it },
                    metadataHistory = metadataHistory,
                    onMetadataHistoryUpdate = onMetadataHistoryUpdate,
                    sourceExif = sourceExif,
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
                    suggestions = metadataHistory.description,
                    onCommit = { onMetadataHistoryUpdate("description", config.description) },
                    fieldIncluded = config.overrideDescription != OverrideState.NULL_OUT,
                    onFieldIncludedChange = { included ->
                        state.updatePhotoConfiguration(box.id) {
                            it.copy(
                                overrideDescription =
                                    if (included) OverrideState.KEEP_SOURCE
                                    else OverrideState.NULL_OUT
                            )
                        }
                    },
                    focusRequester = descriptionFocusRequester,
                    sourceHint = sourceExif?.description,
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
                        suggestions = metadataHistory.keywords,
                        onCommit = { onMetadataHistoryUpdate("keywords", config.keywords) },
                        fieldIncluded = config.overrideKeywords != OverrideState.NULL_OUT,
                        onFieldIncludedChange = { included ->
                            state.updatePhotoConfiguration(box.id) {
                                it.copy(
                                    overrideKeywords =
                                        if (included) OverrideState.KEEP_SOURCE
                                        else OverrideState.NULL_OUT
                                )
                            }
                        },
                        focusRequester = keywordsFocusRequester,
                        sourceHint = null, // Keywords not typically in source EXIF
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
                        suggestions = metadataHistory.year,
                        onCommit = { onMetadataHistoryUpdate("year", config.year) },
                        fieldIncluded = config.overrideYear != OverrideState.NULL_OUT,
                        onFieldIncludedChange = { included ->
                            state.updatePhotoConfiguration(box.id) {
                                it.copy(
                                    overrideYear =
                                        if (included) OverrideState.KEEP_SOURCE
                                        else OverrideState.NULL_OUT
                                )
                            }
                        },
                        focusRequester = yearFocusRequester,
                        sourceHint = sourceExif?.dateOriginal?.take(4),
                    )
                }
                MetadataField(
                    label = "Original Date",
                    placeholder = "YYYY-MM-DD or YYYY-MM-DD HH:MM:SS",
                    value = config.originalDate,
                    onValueChange = { newValue ->
                        state.updatePhotoConfiguration(box.id) { it.copy(originalDate = newValue) }
                    },
                    suggestions = metadataHistory.originalDate,
                    onCommit = { onMetadataHistoryUpdate("originalDate", config.originalDate) },
                    fieldIncluded = config.overrideOriginalDate != OverrideState.NULL_OUT,
                    onFieldIncludedChange = { included ->
                        state.updatePhotoConfiguration(box.id) {
                            it.copy(
                                overrideOriginalDate =
                                    if (included) OverrideState.KEEP_SOURCE
                                    else OverrideState.NULL_OUT
                            )
                        }
                    },
                    focusRequester = originalDateFocusRequester,
                    sourceHint = sourceExif?.dateOriginal,
                )

                // ═══ Subjects & Faces section ═══
                SubjectsFacesSection(
                    subjects = config.subjects,
                    faceRegions = config.faceRegions,
                    onSubjectsChange = { newValue ->
                        state.updatePhotoConfiguration(box.id) { it.copy(subjects = newValue) }
                    },
                    onRemoveFaceRegion = { faceIndex ->
                        state.removeFaceRegion(selectedIndex, faceIndex)
                    },
                    onSelectFaces = { onSelectFaces(selectedIndex) },
                    metadataHistory = metadataHistory,
                    onMetadataHistoryUpdate = onMetadataHistoryUpdate,
                    subjectInputFocusRequester = subjectInputFocusRequester,
                )

                // ═══ Location & GPS section ═══
                LocationSection(
                    locationName = config.locationName,
                    onLocationNameChange = { newValue ->
                        state.updatePhotoConfiguration(box.id) { it.copy(locationName = newValue) }
                    },
                    city = config.city,
                    onCityChange = { newValue ->
                        state.updatePhotoConfiguration(box.id) { it.copy(city = newValue) }
                    },
                    stateVal = config.state,
                    onStateChange = { newValue ->
                        state.updatePhotoConfiguration(box.id) { it.copy(state = newValue) }
                    },
                    country = config.country,
                    onCountryChange = { newValue ->
                        state.updatePhotoConfiguration(box.id) { it.copy(country = newValue) }
                    },
                    gpsLatitude = config.gpsLatitude,
                    onGpsLatitudeChange = { newValue ->
                        state.updatePhotoConfiguration(box.id) { it.copy(gpsLatitude = newValue) }
                    },
                    gpsLongitude = config.gpsLongitude,
                    onGpsLongitudeChange = { newValue ->
                        state.updatePhotoConfiguration(box.id) { it.copy(gpsLongitude = newValue) }
                    },
                    overrideGps = config.overrideGps != OverrideState.NULL_OUT,
                    onOverrideGpsChange = { included ->
                        state.updatePhotoConfiguration(box.id) {
                            it.copy(
                                overrideGps =
                                    if (included) OverrideState.KEEP_SOURCE
                                    else OverrideState.NULL_OUT
                            )
                        }
                    },
                    metadataHistory = metadataHistory,
                    onMetadataHistoryUpdate = onMetadataHistoryUpdate,
                    sourceExif = sourceExif,
                    onSearchLocation =
                        if (onSearchLocation != null) {
                            { onSearchLocation!!(selectedIndex) }
                        } else null,
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
                    metadataHistory = metadataHistory,
                    onMetadataHistoryUpdate = onMetadataHistoryUpdate,
                    // Override tri-states
                    overrideCameraMake = config.overrideCameraMake != OverrideState.NULL_OUT,
                    onOverrideCameraMakeChange = { included ->
                        state.updatePhotoConfiguration(box.id) {
                            it.copy(
                                overrideCameraMake =
                                    if (included) OverrideState.KEEP_SOURCE
                                    else OverrideState.NULL_OUT
                            )
                        }
                    },
                    overrideCameraModel = config.overrideCameraModel != OverrideState.NULL_OUT,
                    onOverrideCameraModelChange = { included ->
                        state.updatePhotoConfiguration(box.id) {
                            it.copy(
                                overrideCameraModel =
                                    if (included) OverrideState.KEEP_SOURCE
                                    else OverrideState.NULL_OUT
                            )
                        }
                    },
                    overrideLensModel = config.overrideLensModel != OverrideState.NULL_OUT,
                    onOverrideLensModelChange = { included ->
                        state.updatePhotoConfiguration(box.id) {
                            it.copy(
                                overrideLensModel =
                                    if (included) OverrideState.KEEP_SOURCE
                                    else OverrideState.NULL_OUT
                            )
                        }
                    },
                    overrideFocalLength = config.overrideFocalLength != OverrideState.NULL_OUT,
                    onOverrideFocalLengthChange = { included ->
                        state.updatePhotoConfiguration(box.id) {
                            it.copy(
                                overrideFocalLength =
                                    if (included) OverrideState.KEEP_SOURCE
                                    else OverrideState.NULL_OUT
                            )
                        }
                    },
                    overrideAperture = config.overrideAperture != OverrideState.NULL_OUT,
                    onOverrideApertureChange = { included ->
                        state.updatePhotoConfiguration(box.id) {
                            it.copy(
                                overrideAperture =
                                    if (included) OverrideState.KEEP_SOURCE
                                    else OverrideState.NULL_OUT
                            )
                        }
                    },
                    overrideShutterSpeed = config.overrideShutterSpeed != OverrideState.NULL_OUT,
                    onOverrideShutterSpeedChange = { included ->
                        state.updatePhotoConfiguration(box.id) {
                            it.copy(
                                overrideShutterSpeed =
                                    if (included) OverrideState.KEEP_SOURCE
                                    else OverrideState.NULL_OUT
                            )
                        }
                    },
                    overrideIso = config.overrideIso != OverrideState.NULL_OUT,
                    onOverrideIsoChange = { included ->
                        state.updatePhotoConfiguration(box.id) {
                            it.copy(
                                overrideIso =
                                    if (included) OverrideState.KEEP_SOURCE
                                    else OverrideState.NULL_OUT
                            )
                        }
                    },
                    cameraModelFocusRequester = cameraModelFocusRequester,
                    cameraMakeFocusRequester = cameraMakeFocusRequester,
                    lensModelFocusRequester = lensModelFocusRequester,
                    focalLengthFocusRequester = focalLengthFocusRequester,
                    apertureFocusRequester = apertureFocusRequester,
                    shutterSpeedFocusRequester = shutterSpeedFocusRequester,
                    isoFocusRequester = isoFocusRequester,
                    sourceExif = sourceExif,
                )
            }
        }
    }
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
    metadataHistory: MetadataHistory = MetadataHistory(),
    onMetadataHistoryUpdate: (String, String) -> Unit = { _, _ -> },
    // Override checkboxes (null = no checkbox shown)
    overrideCameraMake: Boolean? = null,
    onOverrideCameraMakeChange: ((Boolean) -> Unit)? = null,
    overrideCameraModel: Boolean? = null,
    onOverrideCameraModelChange: ((Boolean) -> Unit)? = null,
    overrideLensModel: Boolean? = null,
    onOverrideLensModelChange: ((Boolean) -> Unit)? = null,
    overrideFocalLength: Boolean? = null,
    onOverrideFocalLengthChange: ((Boolean) -> Unit)? = null,
    overrideAperture: Boolean? = null,
    onOverrideApertureChange: ((Boolean) -> Unit)? = null,
    overrideShutterSpeed: Boolean? = null,
    onOverrideShutterSpeedChange: ((Boolean) -> Unit)? = null,
    overrideIso: Boolean? = null,
    onOverrideIsoChange: ((Boolean) -> Unit)? = null,
    cameraModelFocusRequester: FocusRequester? = null,
    cameraMakeFocusRequester: FocusRequester? = null,
    lensModelFocusRequester: FocusRequester? = null,
    focalLengthFocusRequester: FocusRequester? = null,
    apertureFocusRequester: FocusRequester? = null,
    shutterSpeedFocusRequester: FocusRequester? = null,
    isoFocusRequester: FocusRequester? = null,
    sourceExif: SourceExifSummary? = null,
) {
    Column {
        OutlinedButton(onClick = onToggleAdvanced, modifier = Modifier.fillMaxWidth()) {
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
                        suggestions = metadataHistory.cameraMake,
                        onCommit = { onMetadataHistoryUpdate("cameraMake", cameraMake) },
                        fieldIncluded = overrideCameraMake,
                        onFieldIncludedChange = onOverrideCameraMakeChange,
                        focusRequester = cameraMakeFocusRequester,
                        sourceHint = sourceExif?.cameraMake,
                    )
                    MetadataField(
                        label = "Camera Model",
                        placeholder = "EOS 5D Mark IV",
                        value = cameraModel,
                        onValueChange = onCameraModelChange,
                        modifier = Modifier.weight(1f),
                        suggestions = metadataHistory.cameraModel,
                        onCommit = { onMetadataHistoryUpdate("cameraModel", cameraModel) },
                        fieldIncluded = overrideCameraModel,
                        onFieldIncludedChange = onOverrideCameraModelChange,
                        focusRequester = cameraModelFocusRequester,
                        sourceHint = sourceExif?.cameraModel,
                    )
                }
                MetadataField(
                    label = "Lens Model",
                    placeholder = "24-70mm f/2.8L",
                    value = lensModel,
                    onValueChange = onLensModelChange,
                    suggestions = metadataHistory.lensModel,
                    onCommit = { onMetadataHistoryUpdate("lensModel", lensModel) },
                    fieldIncluded = overrideLensModel,
                    onFieldIncludedChange = onOverrideLensModelChange,
                    focusRequester = lensModelFocusRequester,
                    sourceHint = sourceExif?.lensModel,
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
                        suggestions = metadataHistory.focalLength,
                        onCommit = { onMetadataHistoryUpdate("focalLength", focalLength) },
                        fieldIncluded = overrideFocalLength,
                        onFieldIncludedChange = onOverrideFocalLengthChange,
                        focusRequester = focalLengthFocusRequester,
                        sourceHint = sourceExif?.focalLength,
                    )
                    MetadataField(
                        label = "Aperture",
                        placeholder = "f/2.8",
                        value = aperture,
                        onValueChange = onApertureChange,
                        modifier = Modifier.weight(1f),
                        suggestions = metadataHistory.aperture,
                        onCommit = { onMetadataHistoryUpdate("aperture", aperture) },
                        fieldIncluded = overrideAperture,
                        onFieldIncludedChange = onOverrideApertureChange,
                        focusRequester = apertureFocusRequester,
                        sourceHint = sourceExif?.aperture,
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
                        suggestions = metadataHistory.shutterSpeed,
                        onCommit = { onMetadataHistoryUpdate("shutterSpeed", shutterSpeed) },
                        fieldIncluded = overrideShutterSpeed,
                        onFieldIncludedChange = onOverrideShutterSpeedChange,
                        focusRequester = shutterSpeedFocusRequester,
                        sourceHint = sourceExif?.shutterSpeed,
                    )
                    MetadataField(
                        label = "ISO",
                        placeholder = "400",
                        value = iso,
                        onValueChange = onIsoChange,
                        modifier = Modifier.weight(1f),
                        keyboardType = KeyboardType.Number,
                        suggestions = metadataHistory.iso,
                        onCommit = { onMetadataHistoryUpdate("iso", iso) },
                        fieldIncluded = overrideIso,
                        onFieldIncludedChange = onOverrideIsoChange,
                        focusRequester = isoFocusRequester,
                        sourceHint = sourceExif?.iso,
                    )
                }
            }
        }
    }
}

/** Collapsible Location & GPS section with override support for GPS coordinates. */
@Composable
private fun LocationSection(
    locationName: String,
    onLocationNameChange: (String) -> Unit,
    city: String,
    onCityChange: (String) -> Unit,
    stateVal: String,
    onStateChange: (String) -> Unit,
    country: String,
    onCountryChange: (String) -> Unit,
    gpsLatitude: String,
    onGpsLatitudeChange: (String) -> Unit,
    gpsLongitude: String,
    onGpsLongitudeChange: (String) -> Unit,
    overrideGps: Boolean = true,
    onOverrideGpsChange: ((Boolean) -> Unit)? = null,
    onSearchLocation: (() -> Unit)? = null,
    metadataHistory: MetadataHistory = MetadataHistory(),
    onMetadataHistoryUpdate: (String, String) -> Unit = { _, _ -> },
    sourceExif: SourceExifSummary? = null,
) {
    var showExpanded by remember { mutableStateOf(false) }

    Column {
        OutlinedButton(
            onClick = { showExpanded = !showExpanded },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                if (showExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (showExpanded) "Hide" else "Show",
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.LocationOn, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Location & GPS", style = MaterialTheme.typography.labelSmall)
            if (!overrideGps) {
                Spacer(Modifier.width(4.dp))
                Text(
                    "(GPS nulled)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                )
            }
        }

        AnimatedVisibility(visible = showExpanded) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MetadataField(
                    label = "Location Name",
                    placeholder = "Grandma's house",
                    value = locationName,
                    onValueChange = onLocationNameChange,
                    suggestions = metadataHistory.locationName,
                    onCommit = { onMetadataHistoryUpdate("locationName", locationName) },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MetadataField(
                        label = "City",
                        placeholder = "Worcester",
                        value = city,
                        onValueChange = onCityChange,
                        modifier = Modifier.weight(1f),
                        suggestions = metadataHistory.city,
                        onCommit = { onMetadataHistoryUpdate("city", city) },
                    )
                    MetadataField(
                        label = "State",
                        placeholder = "MA",
                        value = stateVal,
                        onValueChange = onStateChange,
                        modifier = Modifier.weight(1f),
                        suggestions = metadataHistory.state,
                        onCommit = { onMetadataHistoryUpdate("state", stateVal) },
                    )
                }
                MetadataField(
                    label = "Country",
                    placeholder = "United States",
                    value = country,
                    onValueChange = onCountryChange,
                    suggestions = metadataHistory.country,
                    onCommit = { onMetadataHistoryUpdate("country", country) },
                )

                // GPS coordinates section with override indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("GPS Coordinates", style = MaterialTheme.typography.labelMedium)
                    if (onSearchLocation != null) {
                        Button(
                            onClick = { onSearchLocation() },
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) {
                            Icon(Icons.Default.LocationOn, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Map Search", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Text(
                    "Enter decimal degrees (e.g. 42.2626, -71.8023). Negative = South/West.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = gpsLatitude,
                        onValueChange = onGpsLatitudeChange,
                        label = { Text("Latitude") },
                        placeholder = {
                            Text("42.2626", style = MaterialTheme.typography.labelSmall)
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle =
                            if (!overrideGps)
                                MaterialTheme.typography.bodyMedium.copy(
                                    textDecoration =
                                        androidx.compose.ui.text.style.TextDecoration.LineThrough
                                )
                            else MaterialTheme.typography.bodyMedium,
                        enabled = overrideGps,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        trailingIcon =
                            if (onOverrideGpsChange != null) {
                                {
                                    OverrideCheckbox(
                                        included = overrideGps,
                                        onIncludedChange = onOverrideGpsChange,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            } else null,
                    )
                    OutlinedTextField(
                        value = gpsLongitude,
                        onValueChange = onGpsLongitudeChange,
                        label = { Text("Longitude") },
                        placeholder = {
                            Text("-71.8023", style = MaterialTheme.typography.labelSmall)
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle =
                            if (!overrideGps)
                                MaterialTheme.typography.bodyMedium.copy(
                                    textDecoration =
                                        androidx.compose.ui.text.style.TextDecoration.LineThrough
                                )
                            else MaterialTheme.typography.bodyMedium,
                        enabled = overrideGps,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                if (
                    sourceExif != null &&
                        (sourceExif.gpsLatitude != null || sourceExif.gpsLongitude != null) &&
                        overrideGps
                ) {
                    val gpsHint =
                        listOfNotNull(
                                sourceExif.gpsLatitude?.let { "Lat $it" },
                                sourceExif.gpsLongitude?.let { "Lon $it" },
                            )
                            .joinToString(", ")
                    Text(
                        text = "Source: $gpsHint",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(start = 8.dp, top = 1.dp),
                    )
                }
            }
        }
    }
}

/**
 * Subjects & Faces section with face pills and "Select Faces" button. Shows face regions as
 * deletable pill chips and provides a button to open fullscreen face selection.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SubjectsFacesSection(
    subjects: String,
    faceRegions: List<FaceRegion>,
    onSubjectsChange: (String) -> Unit,
    onRemoveFaceRegion: (Int) -> Unit,
    onSelectFaces: () -> Unit,
    metadataHistory: MetadataHistory,
    onMetadataHistoryUpdate: (String, String) -> Unit,
    subjectInputFocusRequester: FocusRequester? = null,
) {
    val subjectList =
        remember(subjects) { subjects.split(",").map { it.trim() }.filter { it.isNotBlank() } }
    var subjectInput by remember { mutableStateOf("") }
    var suggestionsExpanded by remember { mutableStateOf(false) }

    // Face names derived from faceRegions (coordinates hidden from UI)
    val faceNames = remember(faceRegions) { faceRegions.map { it.name }.filter { it.isNotBlank() } }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Section header with Select Faces button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Person,
                    null,
                    Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text("Subjects & Faces", style = MaterialTheme.typography.titleSmall)
            }
            Button(
                onClick = onSelectFaces,
                modifier = Modifier.height(28.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Icon(Icons.Default.Face, null, Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Select Faces", style = MaterialTheme.typography.labelSmall)
            }
        }

        // Face region pills (deletable)
        if (faceNames.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                faceRegions.forEachIndexed { index, region ->
                    if (region.name.isNotBlank()) {
                        Surface(
                            modifier = Modifier,
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Icon(
                                    regionTypeIcon(RegionType.fromMwgRs(region.type)),
                                    null,
                                    Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                                Text(
                                    region.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                                // X to remove — hover shows "Remove"
                                @OptIn(ExperimentalMaterial3Api::class)
                                TooltipBox(
                                    positionProvider =
                                        TooltipDefaults.rememberTooltipPositionProvider(
                                            TooltipAnchorPosition.Above
                                        ),
                                    tooltip = {
                                        Surface(
                                            tonalElevation = 4.dp,
                                            shape = MaterialTheme.shapes.extraSmall,
                                        ) {
                                            Text(
                                                "Remove",
                                                modifier =
                                                    Modifier.padding(
                                                        horizontal = 6.dp,
                                                        vertical = 3.dp,
                                                    ),
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        }
                                    },
                                    state = rememberTooltipState(),
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove ${region.name}",
                                        modifier =
                                            Modifier.size(14.dp).clickable {
                                                onRemoveFaceRegion(index)
                                            },
                                        tint =
                                            MaterialTheme.colorScheme.onTertiaryContainer.copy(
                                                alpha = 0.7f
                                            ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Subject name input with autocomplete suggestions
        val availableSuggestions =
            remember(metadataHistory.subjects, subjectList) {
                metadataHistory.subjects.filter { it !in subjectList && it !in faceNames }
            }
        val filteredSuggestions =
            remember(availableSuggestions, subjectInput) {
                if (subjectInput.isBlank()) availableSuggestions
                else availableSuggestions.filter { it.contains(subjectInput, ignoreCase = true) }
            }

        ExposedDropdownMenuBox(
            expanded = suggestionsExpanded && filteredSuggestions.isNotEmpty(),
            onExpandedChange = { suggestionsExpanded = it },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = subjectInput,
                    onValueChange = {
                        subjectInput = it
                        suggestionsExpanded = true
                    },
                    placeholder = {
                        Text("Add person…", style = MaterialTheme.typography.labelSmall)
                    },
                    modifier =
                        Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .weight(1f)
                            .then(
                                if (subjectInputFocusRequester != null)
                                    Modifier.focusRequester(subjectInputFocusRequester)
                                else Modifier
                            ),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    trailingIcon = {
                        if (subjectInput.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    val updated =
                                        if (subjects.isBlank()) subjectInput.trim()
                                        else "${subjects.trim()}, ${subjectInput.trim()}"
                                    onSubjectsChange(updated)
                                    onMetadataHistoryUpdate("subjects", subjectInput.trim())
                                    subjectInput = ""
                                },
                                modifier = Modifier.size(20.dp),
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    "Add subject",
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    },
                    keyboardActions =
                        KeyboardActions(
                            onDone = {
                                if (subjectInput.isNotBlank()) {
                                    val updated =
                                        if (subjects.isBlank()) subjectInput.trim()
                                        else "${subjects.trim()}, ${subjectInput.trim()}"
                                    onSubjectsChange(updated)
                                    onMetadataHistoryUpdate("subjects", subjectInput.trim())
                                    subjectInput = ""
                                }
                            }
                        ),
                )
            }
            if (filteredSuggestions.isNotEmpty()) {
                ExposedDropdownMenu(
                    expanded = suggestionsExpanded && filteredSuggestions.isNotEmpty(),
                    onDismissRequest = { suggestionsExpanded = false },
                ) {
                    filteredSuggestions.take(10).forEach { suggestion ->
                        DropdownMenuItem(
                            text = { Text(suggestion, style = MaterialTheme.typography.bodySmall) },
                            onClick = {
                                val updated =
                                    if (subjects.isBlank()) suggestion
                                    else "${subjects.trim()}, $suggestion"
                                onSubjectsChange(updated)
                                onMetadataHistoryUpdate("subjects", suggestion)
                                subjectInput = ""
                                suggestionsExpanded = false
                            },
                        )
                    }
                }
            }
        }

        // Subjects text field (raw comma-separated, syncs both ways)
        if (subjects.isNotBlank() || subjectList.isNotEmpty()) {
            OutlinedTextField(
                value = subjects,
                onValueChange = onSubjectsChange,
                label = { Text("All subjects", style = MaterialTheme.typography.labelSmall) },
                placeholder = {
                    Text("Comma-separated names", style = MaterialTheme.typography.labelSmall)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
