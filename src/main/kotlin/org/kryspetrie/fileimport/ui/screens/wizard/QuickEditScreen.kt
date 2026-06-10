package org.kryspetrie.fileimport.ui.screens.wizard

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
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import java.awt.image.BufferedImage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.koin.compose.koinInject
import org.kryspetrie.fileimport.application.FaceRegionTransformer
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
import org.kryspetrie.fileimport.ui.screens.wizard.metadata.LoadSourceExifEffect
import org.kryspetrie.fileimport.ui.screens.wizard.metadata.LocationPickerDialog
import org.kryspetrie.fileimport.ui.screens.wizard.metadata.MetadataField
import org.kryspetrie.fileimport.ui.screens.wizard.metadata.OverrideCheckbox

/**
 * Quick Edit screen combining crop preview, rotation controls, metadata editing, location capture,
 * and subject/face tagging into a single streamlined view.
 *
 * Layout:
 * - Left: Large cropped preview with thumbnail strip at top
 * - Right: Scrolled editor panel with rotation, metadata, location, subjects sections
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickEditScreen(
    state: PhotoScanWizardState,
    image: BufferedImage,
    perspectiveService: PerspectiveCorrectionService,
    metadataHistory: MetadataHistory,
    onMetadataHistoryUpdate: (String, String) -> Unit,
    onMetadataHistoryRemove: (String, String) -> Unit,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onSkipToExport: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    faceRegionTransformer: FaceRegionTransformer? = null,
) {
    val locationSearchService: LocationSearchPort = koinInject()
    val geocodingPort: GeocodingPort = koinInject()
    val dispatcherProvider: DispatcherProvider = koinInject()
    val imageRepository: ImageRepositoryPort = koinInject()

    val boundingBoxList by state.boundingBoxList.collectAsState()
    val photoConfigurations by state.photoConfigurations.collectAsState()
    val selectedIndices by state.selectedMetadataIndices.collectAsState()
    val sourceExif by state.sourceExif.collectAsState()
    val currentImageFile by state.imageFile.collectAsState()

    var isMultiEditMode by remember { mutableStateOf(false) }
    var fullscreenPreviewIndex by remember { mutableStateOf<Int?>(null) }
    var showLocationSection by remember { mutableStateOf(false) }
    var showSubjectsSection by remember { mutableStateOf(false) }
    var showCameraSection by remember { mutableStateOf(false) }
    var showLocationPicker by remember { mutableStateOf(false) }
    var locationPickerTargetIndex by remember { mutableStateOf<Int?>(null) }

    // Face selection state
    var faceSelectIndex by remember { mutableStateOf<Int?>(null) }
    var pendingFaceCoords by remember { mutableStateOf<Triple<Int, Double, Double>?>(null) }
    var showFaceNamePopup by remember { mutableStateOf(false) }
    var faceNameInput by remember { mutableStateOf("") }
    var selectedRegionType by remember { mutableStateOf(RegionType.FACE) }
    var selectedFaceSize by remember { mutableStateOf(FaceSize.DEFAULT) }
    var inheritedFaceRegions by remember { mutableStateOf<List<FaceRegion>>(emptyList()) }

    // Back-of-photo selection state
    var showBackImagePicker by remember { mutableStateOf(false) }

    // Read source EXIF when entering the screen (only once per source file)
    LoadSourceExifEffect(
        imageFile = currentImageFile,
        sourceExif = sourceExif,
        state = state,
        imageRepository = imageRepository,
        dispatcherProvider = dispatcherProvider,
    )

    // Auto-select first photo when entering the screen
    LaunchedEffect(boundingBoxList.size()) {
        if (selectedIndices.isEmpty() && boundingBoxList.size() > 0) {
            state.selectSingleMetadata(0)
        }
    }

    // Fullscreen overlay
    if (fullscreenPreviewIndex != null && fullscreenPreviewIndex!! < boundingBoxList.size()) {
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
                IconButton(
                    onClick = { fullscreenPreviewIndex = null },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                ) {
                    Icon(Icons.Default.Close, "Close preview", modifier = Modifier.size(36.dp))
                }
            }
        }
    }

    // ── Face selection full-screen overlay ──
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
    }

    // Face name entry popup — simplified (type and size selected from side toolbar)
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
                        "Name this ${selectedRegionType.displayName.lowercase()}",
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
                            "${selectedRegionType.displayName} • ${selectedFaceSize.displayName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextField(
                        value = faceNameInput,
                        onValueChange = { faceNameInput = it },
                        placeholder = { Text("Name…", style = MaterialTheme.typography.bodySmall) },
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

    // ── Location picker dialog ──
    if (showLocationPicker && locationPickerTargetIndex != null) {
        Dialog(
            onDismissRequest = {
                showLocationPicker = false
                locationPickerTargetIndex = null
            },
            properties = DialogProperties(usePlatformDefaultWidth = false),
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
                                    state = it.state,
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

    // ── Back-of-photo image picker dialog ──
    if (showBackImagePicker) {
        BackImagePickerDialog(
            batchFiles = state.sourceFiles.value.ifEmpty { null },
            onConfirm = { sourcePath, cropRect, rotation, mode ->
                val idx = selectedIndices.firstOrNull() ?: return@BackImagePickerDialog
                if (idx < boundingBoxList.size()) {
                    val boxId = boundingBoxList.boxes[idx].id
                    state.updatePhotoConfiguration(boxId) {
                        it.copy(
                            backImageMode = mode,
                            backImageSourcePath = sourcePath,
                            backCropNormalized =
                                cropRect?.let { rect ->
                                    listOf(
                                        rect.left.toFloat(),
                                        rect.top.toFloat(),
                                        rect.right.toFloat(),
                                        rect.bottom.toFloat(),
                                    )
                                },
                            backCropRotation = rotation,
                        )
                    }
                    // Auto-skip this back image from batch processing if it's in the batch
                    state.sourceFiles.value
                        .indexOfFirst { it.absolutePath == sourcePath }
                        .takeIf { it >= 0 }
                        ?.let { state.markBatchIndexSkipped(it) }
                }
                showBackImagePicker = false
            },
            onDismiss = { showBackImagePicker = false },
        )
    }

    Scaffold(
        modifier =
            Modifier.onPreviewKeyEvent { keyEvent ->
                // Cmd+, = Previous photo, Cmd+. = Next photo
                if (keyEvent.type == KeyEventType.KeyDown) {
                    val isMeta = isCtrlPressed(keyEvent)
                    when {
                        isMeta && keyEvent.key == Key.Comma -> {
                            val currentIdx =
                                if (selectedIndices.size == 1) selectedIndices.first() else -1
                            if (currentIdx > 0) {
                                state.selectSingleMetadata(currentIdx - 1)
                            }
                            true
                        }
                        isMeta && keyEvent.key == Key.Period -> {
                            val currentIdx =
                                if (selectedIndices.size == 1) selectedIndices.first() else -1
                            if (currentIdx >= 0 && currentIdx < boundingBoxList.size() - 1) {
                                state.selectSingleMetadata(currentIdx + 1)
                            }
                            true
                        }
                        else -> false
                    }
                } else false
            },
        topBar = {
            TopAppBar(
                title = { Text("Quick Edit") },
                actions = {
                    if (onSkipToExport != null) {
                        OutlinedButton(
                            onClick = onSkipToExport,
                            modifier = Modifier.height(32.dp),
                        ) {
                            Text("Export Now", style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Button(
                        onClick = onExport,
                        enabled = boundingBoxList.size() > 0,
                        modifier = Modifier.height(32.dp),
                    ) {
                        Text("Next", style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(16.dp))
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Back button
                    OutlinedButton(onClick = onBack, modifier = Modifier.height(40.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Back")
                    }

                    // Photo navigation
                    if (boundingBoxList.size() > 1) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val currentIdx =
                                if (selectedIndices.size == 1) selectedIndices.first() else -1
                            OutlinedButton(
                                onClick = {
                                    if (currentIdx > 0) state.selectSingleMetadata(currentIdx - 1)
                                },
                                enabled = currentIdx > 0,
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) {
                                Text("◄ Prev", style = MaterialTheme.typography.labelSmall)
                            }
                            Text(
                                if (currentIdx >= 0)
                                    "Photo ${currentIdx + 1} of ${boundingBoxList.size()}"
                                else "${boundingBoxList.size()} photos",
                                style = MaterialTheme.typography.labelSmall,
                            )
                            OutlinedButton(
                                onClick = {
                                    if (currentIdx < boundingBoxList.size() - 1 && currentIdx >= 0)
                                        state.selectSingleMetadata(currentIdx + 1)
                                },
                                enabled =
                                    currentIdx >= 0 && currentIdx < boundingBoxList.size() - 1,
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) {
                                Text("Next ►", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    } else {
                        Text(
                            "${boundingBoxList.size()} photo(s)",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    // Multi-edit toggle
                    if (boundingBoxList.size() > 1) {
                        if (isMultiEditMode) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "${selectedIndices.size} selected",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.align(Alignment.CenterVertically),
                                )
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
            }
        },
    ) { paddingValues ->
        if (selectedIndices.isEmpty() && !isMultiEditMode) {
            // No selection — show thumbnail strip and prompt
            Column(modifier = modifier.fillMaxSize().padding(paddingValues)) {
                ThumbnailStrip(
                    image = image,
                    perspectiveService = perspectiveService,
                    boundingBoxList = boundingBoxList,
                    photoConfigurations = photoConfigurations,
                    selectedIndices = selectedIndices,
                    isMultiEditMode = isMultiEditMode,
                    onSelect = { index ->
                        if (isMultiEditMode) state.toggleMetadataSelection(index)
                        else state.selectSingleMetadata(index)
                    },
                    onDeselectAll = { state.deselectAllMetadata() },
                )
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Click a photo above to edit",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            Row(modifier = modifier.fillMaxSize().padding(paddingValues)) {
                // ═══ Left pane: preview + thumbnails ═══
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    // Thumbnail strip at top
                    ThumbnailStrip(
                        image = image,
                        perspectiveService = perspectiveService,
                        boundingBoxList = boundingBoxList,
                        photoConfigurations = photoConfigurations,
                        selectedIndices = selectedIndices,
                        isMultiEditMode = isMultiEditMode,
                        onSelect = { index ->
                            if (isMultiEditMode) {
                                state.toggleMetadataSelection(index)
                            } else {
                                if (index in selectedIndices && selectedIndices.size == 1) {
                                    state.deselectAllMetadata()
                                } else {
                                    state.selectSingleMetadata(index)
                                }
                            }
                        },
                        onDeselectAll = { state.deselectAllMetadata() },
                    )

                    // Large preview
                    if (selectedIndices.size == 1 && !isMultiEditMode) {
                        val selectedIndex = selectedIndices.first()
                        val box = boundingBoxList.boxes[selectedIndex]
                        val config = photoConfigurations[box.id] ?: PhotoConfiguration()
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
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { fullscreenPreviewIndex = selectedIndex },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (previewImage != null) {
                                Image(
                                    bitmap = previewImage.toComposeImageBitmap(),
                                    contentDescription =
                                        "Photo ${selectedIndex + 1} — click to enlarge",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit,
                                )
                                // Back-of-photo button — appears in bottom-right of preview
                                if (config.hasBackImage()) {
                                    Surface(
                                        modifier =
                                            Modifier.align(Alignment.BottomEnd).padding(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(4.dp),
                                    ) {
                                        Row(
                                            modifier =
                                                Modifier.padding(
                                                    horizontal = 8.dp,
                                                    vertical = 4.dp,
                                                ),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                Icons.Default.Image,
                                                "Back image assigned",
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                if (config.backImageMode == "combine")
                                                    "Back: Combined"
                                                else "Back: Appended",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            TextButton(
                                                onClick = { showBackImagePicker = true },
                                                contentPadding = PaddingValues(0.dp),
                                            ) {
                                                Text(
                                                    "Change",
                                                    style = MaterialTheme.typography.labelSmall,
                                                )
                                            }
                                            TextButton(
                                                onClick = {
                                                    state.updatePhotoConfiguration(box.id) {
                                                        it.copy(
                                                            backImageMode = null,
                                                            backImageSourcePath = null,
                                                            backCropNormalized = null,
                                                            backCropRotation = 0,
                                                        )
                                                    }
                                                },
                                                contentPadding = PaddingValues(0.dp),
                                            ) {
                                                Text(
                                                    "Remove",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.error,
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { showBackImagePicker = true },
                                        modifier =
                                            Modifier.align(Alignment.BottomEnd)
                                                .padding(8.dp)
                                                .height(28.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.Image,
                                            "Select back of photo",
                                            modifier = Modifier.size(14.dp),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            "Add Back",
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${selectedIndices.size} photos selected",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // ═══ Right pane: editor ═══
                QuickEditEditor(
                    state = state,
                    image = image,
                    perspectiveService = perspectiveService,
                    boundingBoxList = boundingBoxList,
                    photoConfigurations = photoConfigurations,
                    selectedIndices = selectedIndices,
                    isMultiEditMode = isMultiEditMode,
                    metadataHistory = metadataHistory,
                    onMetadataHistoryUpdate = onMetadataHistoryUpdate,
                    onMetadataHistoryRemove = onMetadataHistoryRemove,
                    showCameraSection = showCameraSection,
                    onToggleCameraSection = { showCameraSection = !showCameraSection },
                    showLocationSection = showLocationSection,
                    onToggleLocationSection = { showLocationSection = !showLocationSection },
                    showSubjectsSection = showSubjectsSection,
                    onToggleSubjectsSection = { showSubjectsSection = !showSubjectsSection },
                    sourceExif = sourceExif,
                    onSelectFaces = { idx -> faceSelectIndex = idx },
                    onPickLocation = { idx ->
                        locationPickerTargetIndex = idx
                        showLocationPicker = true
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

/** Horizontal scrollable thumbnail strip for photo selection. */
@Composable
private fun ThumbnailStrip(
    image: BufferedImage,
    perspectiveService: PerspectiveCorrectionService,
    boundingBoxList: BoundingBoxList,
    photoConfigurations: Map<String, PhotoConfiguration>,
    selectedIndices: Set<Int>,
    isMultiEditMode: Boolean,
    onSelect: (Int) -> Unit,
    onDeselectAll: () -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(boundingBoxList.boxes) { index, box ->
            val config = photoConfigurations[box.id] ?: PhotoConfiguration()
            val previewImage =
                remember(image, box.id, config.rotationDegrees) {
                    val visualConfig = PhotoConfiguration(rotationDegrees = config.rotationDegrees)
                    cropAndRotateBoundingBox(image, box, visualConfig, perspectiveService)
                }
            val isSelected = index in selectedIndices

            Card(
                modifier = Modifier.width(100.dp).height(80.dp).clickable { onSelect(index) },
                shape = RoundedCornerShape(6.dp),
                border =
                    BorderStroke(
                        if (isSelected) 2.dp else 1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                    ),
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                    ),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (previewImage != null) {
                        Image(
                            bitmap = previewImage.toComposeImageBitmap(),
                            contentDescription = "Photo ${index + 1}",
                            modifier = Modifier.fillMaxSize().padding(2.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    if (isSelected) {
                        if (isMultiEditMode) {
                            Checkbox(
                                checked = true,
                                onCheckedChange = { onSelect(index) },
                                modifier = Modifier.align(Alignment.TopStart).size(18.dp),
                            )
                        } else {
                            Icon(
                                Icons.Default.CheckCircle,
                                "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.align(Alignment.TopEnd).size(16.dp),
                            )
                        }
                    }
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.BottomStart).padding(2.dp),
                    )
                    if (config.hasMetadata()) {
                        Text(
                            "✓",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp),
                        )
                    }
                }
            }
        }
    }
}

/** The right-side editor panel containing rotation, metadata, location, and subjects sections. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun QuickEditEditor(
    state: PhotoScanWizardState,
    image: BufferedImage,
    perspectiveService: PerspectiveCorrectionService,
    boundingBoxList: BoundingBoxList,
    photoConfigurations: Map<String, PhotoConfiguration>,
    selectedIndices: Set<Int>,
    isMultiEditMode: Boolean,
    metadataHistory: MetadataHistory,
    onMetadataHistoryUpdate: (String, String) -> Unit,
    onMetadataHistoryRemove: (String, String) -> Unit,
    showCameraSection: Boolean,
    onToggleCameraSection: () -> Unit,
    showLocationSection: Boolean,
    onToggleLocationSection: () -> Unit,
    showSubjectsSection: Boolean,
    onToggleSubjectsSection: () -> Unit,
    sourceExif: SourceExifSummary?,
    onSelectFaces: (Int) -> Unit,
    onPickLocation: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editTab by remember { mutableStateOf(0) } // 0 = Rotation, 1 = Metadata {
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
    var bufferedLocationName by remember { mutableStateOf("") }
    var bufferedCity by remember { mutableStateOf("") }
    var bufferedState by remember { mutableStateOf("") }
    var bufferedCountry by remember { mutableStateOf("") }
    var bufferedGpsLatitude by remember { mutableStateOf("") }
    var bufferedGpsLongitude by remember { mutableStateOf("") }
    var bufferedSubjects by remember { mutableStateOf("") }

    ChunkyScrollbar(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── Edit tab selector ──
            if (!isMultiSelect) {
                TabRow(
                    selectedTabIndex = editTab,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Tab(
                        selected = editTab == 0,
                        onClick = { editTab = 0 },
                        text = { Text("Rotation") },
                    )
                    Tab(
                        selected = editTab == 1,
                        onClick = { editTab = 1 },
                        text = { Text("Metadata") },
                    )
                }
            }

            if (isMultiSelect) {
                // ── Multi-edit mode ──
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
                                locationName = bufferedLocationName,
                                city = bufferedCity,
                                state = bufferedState,
                                country = bufferedCountry,
                                gpsLatitude = bufferedGpsLatitude,
                                gpsLongitude = bufferedGpsLongitude,
                                subjects = bufferedSubjects,
                            )
                        },
                        modifier = Modifier.height(32.dp),
                    ) {
                        Text("Apply", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Text(
                    "Only filled fields will be applied. Leave blank to keep existing values.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                QuickEditMetadataFields(
                    description = bufferedDescription,
                    onDescriptionChange = { bufferedDescription = it },
                    keywords = bufferedKeywords,
                    onKeywordsChange = { bufferedKeywords = it },
                    originalDate = bufferedOriginalDate,
                    onOriginalDateChange = { bufferedOriginalDate = it },
                    year = bufferedYear,
                    onYearChange = { bufferedYear = it },
                    metadataHistory = metadataHistory,
                    onMetadataHistoryUpdate = onMetadataHistoryUpdate,
                    onMetadataHistoryRemove = onMetadataHistoryRemove,
                    sourceExif = sourceExif,
                )

                // Collapsible sections
                CameraSection(
                    showExpanded = showCameraSection,
                    onToggle = onToggleCameraSection,
                    cameraMake = bufferedCameraMake,
                    onCameraMakeChange = { bufferedCameraMake = it },
                    cameraModel = bufferedCameraModel,
                    onCameraModelChange = { bufferedCameraModel = it },
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
                    metadataHistory = metadataHistory,
                    onMetadataHistoryUpdate = onMetadataHistoryUpdate,
                    sourceExif = sourceExif,
                )

                LocationSection(
                    showExpanded = showLocationSection,
                    onToggle = onToggleLocationSection,
                    locationName = bufferedLocationName,
                    onLocationNameChange = { bufferedLocationName = it },
                    city = bufferedCity,
                    onCityChange = { bufferedCity = it },
                    stateVal = bufferedState,
                    onStateChange = { bufferedState = it },
                    country = bufferedCountry,
                    onCountryChange = { bufferedCountry = it },
                    gpsLatitude = bufferedGpsLatitude,
                    onGpsLatitudeChange = { bufferedGpsLatitude = it },
                    gpsLongitude = bufferedGpsLongitude,
                    onGpsLongitudeChange = { bufferedGpsLongitude = it },
                    metadataHistory = metadataHistory,
                    onMetadataHistoryUpdate = onMetadataHistoryUpdate,
                    sourceGpsHint =
                        sourceExif?.let {
                            val parts = mutableListOf<String>()
                            it.gpsLatitude?.let { lat -> parts.add("Lat: $lat") }
                            it.gpsLongitude?.let { lon -> parts.add("Lon: $lon") }
                            if (parts.isNotEmpty()) "Source: ${parts.joinToString(", ")}" else null
                        },
                )

                SubjectsSection(
                    showExpanded = showSubjectsSection,
                    onToggle = onToggleSubjectsSection,
                    subjects = bufferedSubjects,
                    onSubjectsChange = { bufferedSubjects = it },
                    metadataHistory = metadataHistory,
                    onMetadataHistoryUpdate = onMetadataHistoryUpdate,
                    onMetadataHistoryRemove = onMetadataHistoryRemove,
                )
            } else {
                // ── Single-select: immediate-edit mode ──
                val selectedIndex = selectedIndices.first()
                val box = boundingBoxList.boxes[selectedIndex]
                val config = photoConfigurations[box.id] ?: PhotoConfiguration()

                Text(
                    "Photo ${selectedIndex + 1}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )

                // ═══ Rotation tab content ═══
                if (editTab == 0) {
                // ── Rotation section ──
                RotationSection(
                    rotationDegrees = config.rotationDegrees,
                    onRotateCW = {
                        state.updatePhotoConfiguration(box.id) { it.cycleRotationCW() }
                    },
                    onRotateCCW = {
                        state.updatePhotoConfiguration(box.id) { it.cycleRotationCCW() }
                    },
                    onRotate180 = {
                        state.updatePhotoConfiguration(box.id) {
                            it.copy(rotationDegrees = (it.rotationDegrees + 180) % 360)
                        }
                    },
                )
                } // end Rotation tab

                // ═══ Metadata tab content ═══
                if (editTab == 1) {
                // ── Metadata fields ──
                QuickEditMetadataFields(
                    description = config.description,
                    onDescriptionChange = { newValue ->
                        state.updatePhotoConfiguration(box.id) { it.copy(description = newValue) }
                    },
                    keywords = config.keywords,
                    onKeywordsChange = { newValue ->
                        state.updatePhotoConfiguration(box.id) { it.copy(keywords = newValue) }
                    },
                    originalDate = config.originalDate,
                    onOriginalDateChange = { newValue ->
                        state.updatePhotoConfiguration(box.id) { it.copy(originalDate = newValue) }
                    },
                    year = config.year,
                    onYearChange = { newValue ->
                        state.updatePhotoConfiguration(box.id) {
                            it.copy(year = newValue.filter { c -> c.isDigit() }.take(4))
                        }
                    },
                    metadataHistory = metadataHistory,
                    onMetadataHistoryUpdate = onMetadataHistoryUpdate,
                    onMetadataHistoryRemove = onMetadataHistoryRemove,
                    onCommitKeyword = { keyword ->
                        // Save keyword to history on commit
                        onMetadataHistoryUpdate("keywords", keyword)
                    },
                    boxId = box.id,
                    state = state,
                    // Override checkboxes from PhotoConfiguration
                    overrideDescription = config.overrideDescription != OverrideState.NULL_OUT,
                    onOverrideDescriptionChange = { included ->
                        state.updatePhotoConfiguration(box.id) {
                            it.copy(
                                overrideDescription =
                                    if (included) OverrideState.KEEP_SOURCE
                                    else OverrideState.NULL_OUT
                            )
                        }
                    },
                    overrideKeywords = config.overrideKeywords != OverrideState.NULL_OUT,
                    onOverrideKeywordsChange = { included ->
                        state.updatePhotoConfiguration(box.id) {
                            it.copy(
                                overrideKeywords =
                                    if (included) OverrideState.KEEP_SOURCE
                                    else OverrideState.NULL_OUT
                            )
                        }
                    },
                    overrideOriginalDate = config.overrideOriginalDate != OverrideState.NULL_OUT,
                    onOverrideOriginalDateChange = { included ->
                        state.updatePhotoConfiguration(box.id) {
                            it.copy(
                                overrideOriginalDate =
                                    if (included) OverrideState.KEEP_SOURCE
                                    else OverrideState.NULL_OUT
                            )
                        }
                    },
                    overrideYear = config.overrideYear != OverrideState.NULL_OUT,
                    onOverrideYearChange = { included ->
                        state.updatePhotoConfiguration(box.id) {
                            it.copy(
                                overrideYear =
                                    if (included) OverrideState.KEEP_SOURCE
                                    else OverrideState.NULL_OUT
                            )
                        }
                    },
                    sourceExif = sourceExif,
                )

                // ── Camera Settings ──
                CameraSection(
                    showExpanded = showCameraSection,
                    onToggle = onToggleCameraSection,
                    cameraMake = config.cameraMake,
                    onCameraMakeChange = { newValue ->
                        state.updatePhotoConfiguration(box.id) { it.copy(cameraMake = newValue) }
                    },
                    cameraModel = config.cameraModel,
                    onCameraModelChange = { newValue ->
                        state.updatePhotoConfiguration(box.id) { it.copy(cameraModel = newValue) }
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
                    // Override checkboxes from PhotoConfiguration
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
                    sourceExif = sourceExif,
                )

                // ── Location ──
                LocationSection(
                    showExpanded = showLocationSection,
                    onToggle = onToggleLocationSection,
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
                    metadataHistory = metadataHistory,
                    onMetadataHistoryUpdate = onMetadataHistoryUpdate,
                    onPickLocation = { onPickLocation(selectedIndex) },
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
                    sourceGpsHint =
                        sourceExif?.let {
                            val parts = mutableListOf<String>()
                            it.gpsLatitude?.let { lat -> parts.add("Lat: $lat") }
                            it.gpsLongitude?.let { lon -> parts.add("Lon: $lon") }
                            if (parts.isNotEmpty()) "Source: ${parts.joinToString(", ")}" else null
                        },
                )

                // ── Subjects & Faces ──
                SubjectsSection(
                    showExpanded = showSubjectsSection,
                    onToggle = onToggleSubjectsSection,
                    subjects = config.subjects,
                    onSubjectsChange = { newValue ->
                        state.updatePhotoConfiguration(box.id) { it.copy(subjects = newValue) }
                    },
                    metadataHistory = metadataHistory,
                    onMetadataHistoryUpdate = onMetadataHistoryUpdate,
                    onMetadataHistoryRemove = onMetadataHistoryRemove,
                    onSelectFaces = { onSelectFaces(selectedIndex) },
                    faceRegions = config.faceRegions,
                    onRemoveFace = { faceIdx -> state.removeFaceRegion(selectedIndex, faceIdx) },
                    onClearAllFaces = { state.clearAllFaceRegions(selectedIndex) },
                )
                } // end Metadata tab
            }
        }
    }
}

/** Rotation controls section. */
@Composable
private fun RotationSection(
    rotationDegrees: Int,
    onRotateCW: () -> Unit,
    onRotateCCW: () -> Unit,
    onRotate180: () -> Unit,
) {
    Surface(
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Rotation", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRotateCCW, modifier = Modifier.size(28.dp)) {
                Icon(Icons.AutoMirrored.Filled.RotateLeft, "CCW", Modifier.size(18.dp))
            }
            IconButton(onClick = onRotate180, modifier = Modifier.size(28.dp)) {
                Text("180°", style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = onRotateCW, modifier = Modifier.size(28.dp)) {
                Icon(Icons.AutoMirrored.Filled.RotateRight, "CW", Modifier.size(18.dp))
            }
            if (rotationDegrees != 0) {
                Text(
                    "${rotationDegrees}°",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * Core metadata fields: description, keywords (with chip UI + X removal), original date (with date
 * picker), year.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun QuickEditMetadataFields(
    description: String,
    onDescriptionChange: (String) -> Unit,
    keywords: String,
    onKeywordsChange: (String) -> Unit,
    originalDate: String,
    onOriginalDateChange: (String) -> Unit,
    year: String,
    onYearChange: (String) -> Unit,
    metadataHistory: MetadataHistory,
    onMetadataHistoryUpdate: (String, String) -> Unit,
    onMetadataHistoryRemove: (String, String) -> Unit,
    onCommitKeyword: ((String) -> Unit)? = null,
    boxId: String? = null,
    state: PhotoScanWizardState? = null,
    // Override tri-states (null = no override indicator shown)
    overrideDescription: Boolean? = null,
    onOverrideDescriptionChange: ((Boolean) -> Unit)? = null,
    overrideKeywords: Boolean? = null,
    onOverrideKeywordsChange: ((Boolean) -> Unit)? = null,
    overrideOriginalDate: Boolean? = null,
    onOverrideOriginalDateChange: ((Boolean) -> Unit)? = null,
    overrideYear: Boolean? = null,
    onOverrideYearChange: ((Boolean) -> Unit)? = null,
    sourceExif: SourceExifSummary? = null,
) {
    val focusManager = LocalFocusManager.current
    // Parse current keywords into chips
    val keywordList =
        remember(keywords) { keywords.split(",").map { it.trim() }.filter { it.isNotBlank() } }
    var keywordInput by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }

    // Description
    MetadataField(
        label = "Description",
        placeholder = "Photo description...",
        value = description,
        onValueChange = onDescriptionChange,
        suggestions = metadataHistory.description,
        onCommit = { onMetadataHistoryUpdate("description", description) },
        fieldIncluded = overrideDescription,
        onFieldIncludedChange = onOverrideDescriptionChange,
        sourceHint = sourceExif?.description,
    )

    // Keywords — chip/tag UI with X removal + suggestion dropdown
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Keywords", style = MaterialTheme.typography.labelMedium)

        // Current keyword chips
        if (keywordList.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                keywordList.forEach { keyword ->
                    RemovableChip(
                        text = keyword,
                        onRemove = {
                            val updated = keywordList.filter { it != keyword }
                            onKeywordsChange(updated.joinToString(", "))
                            onMetadataHistoryRemove("keywords", keyword)
                        },
                    )
                }
            }
        }

        // Add keyword input with suggestions
        var suggestionsExpanded by remember { mutableStateOf(false) }
        val availableSuggestions =
            remember(metadataHistory.keywords, keywordList) {
                metadataHistory.keywords.filter { it !in keywordList }
            }
        val filteredSuggestions =
            remember(availableSuggestions, keywordInput) {
                if (keywordInput.isBlank()) availableSuggestions
                else availableSuggestions.filter { it.contains(keywordInput, ignoreCase = true) }
            }

        if (availableSuggestions.isNotEmpty() || true) { // always show input
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
                        value = keywordInput,
                        onValueChange = {
                            keywordInput = it
                            suggestionsExpanded = true
                        },
                        placeholder = {
                            Text("Add keyword...", style = MaterialTheme.typography.labelSmall)
                        },
                        modifier =
                            Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).weight(1f),
                        singleLine = true,
                        keyboardActions =
                            KeyboardActions(
                                onDone = {
                                    // Add keyword on Enter
                                    if (keywordInput.isNotBlank()) {
                                        val updated =
                                            if (keywords.isBlank()) keywordInput
                                            else "$keywords, $keywordInput"
                                        onKeywordsChange(updated.trim())
                                        onMetadataHistoryUpdate("keywords", keywordInput.trim())
                                        onCommitKeyword?.invoke(keywordInput.trim())
                                        keywordInput = ""
                                    }
                                    focusManager.moveFocus(FocusDirection.Down)
                                }
                            ),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        trailingIcon = {
                            if (keywordInput.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        val updated =
                                            if (keywords.isBlank()) keywordInput
                                            else "$keywords, $keywordInput"
                                        onKeywordsChange(updated.trim())
                                        onMetadataHistoryUpdate("keywords", keywordInput.trim())
                                        onCommitKeyword?.invoke(keywordInput.trim())
                                        keywordInput = ""
                                    },
                                    modifier = Modifier.size(20.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        "Add keyword",
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        },
                    )
                }
                if (filteredSuggestions.isNotEmpty()) {
                    ExposedDropdownMenu(
                        expanded = suggestionsExpanded && filteredSuggestions.isNotEmpty(),
                        onDismissRequest = { suggestionsExpanded = false },
                    ) {
                        filteredSuggestions.take(10).forEach { suggestion ->
                            DropdownMenuItem(
                                text = {
                                    Text(suggestion, style = MaterialTheme.typography.bodySmall)
                                },
                                onClick = {
                                    val updated =
                                        if (keywords.isBlank()) suggestion
                                        else "$keywords, $suggestion"
                                    onKeywordsChange(updated)
                                    onMetadataHistoryUpdate("keywords", suggestion)
                                    keywordInput = ""
                                    suggestionsExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    // Original Date with date picker
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MetadataField(
                label = "Original Date",
                placeholder = "YYYY-MM-DD",
                value = originalDate,
                onValueChange = onOriginalDateChange,
                suggestions = metadataHistory.originalDate,
                onCommit = { onMetadataHistoryUpdate("originalDate", originalDate) },
                modifier = Modifier.weight(1f),
                fieldIncluded = overrideOriginalDate,
                onFieldIncludedChange = onOverrideOriginalDateChange,
                sourceHint = sourceExif?.dateOriginal,
            )
            IconButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.size(40.dp).padding(top = 24.dp),
            ) {
                Icon(Icons.Default.DateRange, "Pick date", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }

    // Date picker dialog
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            onDateSelected = { selectedDate ->
                onOriginalDateChange(selectedDate)
                onMetadataHistoryUpdate("originalDate", selectedDate)
                showDatePicker = false
            },
        )
    }

    // Year
    MetadataField(
        label = "Year",
        placeholder = "1995",
        value = year,
        onValueChange = { onYearChange(it.filter { c -> c.isDigit() }.take(4)) },
        keyboardType = KeyboardType.Number,
        suggestions = metadataHistory.year,
        onCommit = { onMetadataHistoryUpdate("year", year) },
        fieldIncluded = overrideYear,
        onFieldIncludedChange = onOverrideYearChange,
        sourceHint = sourceExif?.dateOriginal?.take(4),
    )
}

/** Collapsible camera settings section. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CameraSection(
    showExpanded: Boolean,
    onToggle: () -> Unit,
    cameraMake: String,
    onCameraMakeChange: (String) -> Unit,
    cameraModel: String,
    onCameraModelChange: (String) -> Unit,
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
    metadataHistory: MetadataHistory,
    onMetadataHistoryUpdate: (String, String) -> Unit,
    // Override checkboxes (null = no checkbox)
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
    sourceExif: SourceExifSummary? = null,
) {
    Column {
        OutlinedButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
            Icon(
                if (showExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (showExpanded) "Hide" else "Show",
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text("Camera Settings", style = MaterialTheme.typography.labelSmall)
        }
        AnimatedVisibility(visible = showExpanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                        sourceHint = sourceExif?.cameraMake,
                    )
                    MetadataField(
                        label = "Camera Model",
                        placeholder = "EOS 5D",
                        value = cameraModel,
                        onValueChange = onCameraModelChange,
                        modifier = Modifier.weight(1f),
                        suggestions = metadataHistory.cameraModel,
                        onCommit = { onMetadataHistoryUpdate("cameraModel", cameraModel) },
                        fieldIncluded = overrideCameraModel,
                        onFieldIncludedChange = onOverrideCameraModelChange,
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
                    sourceHint = sourceExif?.lensModel,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                        sourceHint = sourceExif?.aperture,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                        sourceHint = sourceExif?.iso,
                    )
                }
            }
        }
    }
}

/** Collapsible location section with IPTC structured fields and GPS coordinates. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationSection(
    showExpanded: Boolean,
    onToggle: () -> Unit,
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
    metadataHistory: MetadataHistory,
    onMetadataHistoryUpdate: (String, String) -> Unit,
    onPickLocation: (() -> Unit)? = null,
    overrideGps: Boolean? = null,
    onOverrideGpsChange: ((Boolean) -> Unit)? = null,
    sourceGpsHint: String? = null,
) {
    Column {
        OutlinedButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
            Icon(
                if (showExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (showExpanded) "Hide" else "Show",
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.LocationOn, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Location", style = MaterialTheme.typography.labelSmall)
        }
        AnimatedVisibility(visible = showExpanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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

                // GPS coordinates
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("GPS Coordinates", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                    if (overrideGps != null && onOverrideGpsChange != null) {
                        OverrideCheckbox(
                            included = overrideGps,
                            onIncludedChange = onOverrideGpsChange,
                        )
                    }
                }
                Text(
                    "Enter decimal degrees (e.g. 42.2626, -71.8023). Negative = South/West.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (sourceGpsHint != null) {
                    Text(
                        sourceGpsHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                        textStyle = MaterialTheme.typography.bodyMedium,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                        textStyle = MaterialTheme.typography.bodyMedium,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }

                // Pick on Map button
                if (onPickLocation != null) {
                    OutlinedButton(onClick = onPickLocation, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.LocationOn, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Pick on Map", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

/** Collapsible subjects/faces section with subject names and face region support. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SubjectsSection(
    showExpanded: Boolean,
    onToggle: () -> Unit,
    subjects: String,
    onSubjectsChange: (String) -> Unit,
    metadataHistory: MetadataHistory,
    onMetadataHistoryUpdate: (String, String) -> Unit,
    onMetadataHistoryRemove: (String, String) -> Unit,
    onSelectFaces: (() -> Unit)? = null,
    faceRegions: List<FaceRegion> = emptyList(),
    onRemoveFace: ((Int) -> Unit)? = null,
    onClearAllFaces: (() -> Unit)? = null,
) {
    val subjectList =
        remember(subjects) { subjects.split(",").map { it.trim() }.filter { it.isNotBlank() } }
    var subjectInput by remember { mutableStateOf("") }

    Column {
        OutlinedButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
            Icon(
                if (showExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (showExpanded) "Hide" else "Show",
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.Person, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Subjects & Faces", style = MaterialTheme.typography.labelSmall)
            if (faceRegions.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                Text(
                    "(${faceRegions.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        AnimatedVisibility(visible = showExpanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Subject names are written to EXIF/IPTC metadata and as MWG-RS face regions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Select Faces button
                if (onSelectFaces != null) {
                    OutlinedButton(onClick = onSelectFaces, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Face, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (faceRegions.isEmpty()) "Select Faces on Photo"
                            else "Edit Face Regions (${faceRegions.size})",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }

                // Face regions list
                if (faceRegions.isNotEmpty()) {
                    Surface(
                        tonalElevation = 1.dp,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Face Regions", style = MaterialTheme.typography.labelMedium)
                                if (onClearAllFaces != null) {
                                    Text(
                                        "Clear All",
                                        style =
                                            MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                        color = Color(0xFFFF6666),
                                        modifier = Modifier.clickable { onClearAllFaces() },
                                    )
                                }
                            }
                            faceRegions.forEachIndexed { idx, region ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Icon(
                                            regionTypeIcon(RegionType.fromMwgRs(region.type)),
                                            contentDescription = region.type,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            region.name,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        Text(
                                            "(${kotlin.math.round(region.x * 100).toInt()}%," +
                                                " ${kotlin.math.round(region.y * 100).toInt()}%)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (onRemoveFace != null) {
                                        IconButton(
                                            onClick = { onRemoveFace(idx) },
                                            modifier = Modifier.size(20.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                "Remove",
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Current subject chips
                if (subjectList.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        subjectList.forEach { subject ->
                            RemovableChip(
                                text = subject,
                                onRemove = {
                                    val updated = subjectList.filter { it != subject }
                                    onSubjectsChange(updated.joinToString(", "))
                                    onMetadataHistoryRemove("subjects", subject)
                                },
                            )
                        }
                    }
                }

                // Add subject input with suggestions
                var suggestionsExpanded by remember { mutableStateOf(false) }
                val availableSuggestions =
                    remember(metadataHistory.subjects, subjectList) {
                        metadataHistory.subjects.filter { it !in subjectList }
                    }
                val filteredSuggestions =
                    remember(availableSuggestions, subjectInput) {
                        if (subjectInput.isBlank()) availableSuggestions
                        else
                            availableSuggestions.filter {
                                it.contains(subjectInput, ignoreCase = true)
                            }
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
                                Text("Add person...", style = MaterialTheme.typography.labelSmall)
                            },
                            modifier =
                                Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).weight(1f),
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
                        )
                    }
                    if (filteredSuggestions.isNotEmpty()) {
                        ExposedDropdownMenu(
                            expanded = suggestionsExpanded && filteredSuggestions.isNotEmpty(),
                            onDismissRequest = { suggestionsExpanded = false },
                        ) {
                            filteredSuggestions.take(10).forEach { suggestion ->
                                DropdownMenuItem(
                                    text = {
                                        Text(suggestion, style = MaterialTheme.typography.bodySmall)
                                    },
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
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerDialog(onDismissRequest: () -> Unit, onDateSelected: (String) -> Unit) {
    val datePickerState = rememberDatePickerState()

    Dialog(onDismissRequest = onDismissRequest) {
        Card(modifier = Modifier.padding(16.dp)) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DatePicker(state = datePickerState)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismissRequest) { Text("Cancel") }
                    TextButton(
                        onClick = {
                            val selectedDate =
                                datePickerState.selectedDateMillis?.let { millis ->
                                    val instant = Instant.ofEpochMilli(millis)
                                    val localDate =
                                        instant.atZone(ZoneId.systemDefault()).toLocalDate()
                                    localDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                                } ?: ""
                            if (selectedDate.isNotBlank()) {
                                onDateSelected(selectedDate)
                            }
                            onDismissRequest()
                        }
                    ) {
                        Text("OK")
                    }
                }
            }
        }
    }
}

/** Wrapper for Dialog that works in Compose Desktop. */
@Composable
private fun Dialog(onDismissRequest: () -> Unit, content: @Composable () -> Unit) {
    Popup(onDismissRequest = onDismissRequest, alignment = Alignment.Center) {
        Surface(
            modifier = Modifier.padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 6.dp,
        ) {
            Box(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) { content() }
        }
    }
}

/** A removable chip/tag for keywords and subjects. Shows text with an X button to remove. */
@Composable
private fun RemovableChip(text: String, onRemove: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onRemove),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove $text",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
