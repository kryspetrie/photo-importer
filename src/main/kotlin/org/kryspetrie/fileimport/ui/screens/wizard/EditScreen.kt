package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import java.awt.image.BufferedImage
import kotlinx.coroutines.flow.first
import org.koin.compose.koinInject
import org.kryspetrie.fileimport.application.FaceRegionTransformer
import org.kryspetrie.fileimport.application.PerspectiveCorrectionService
import org.kryspetrie.fileimport.domain.model.FaceRegion
import org.kryspetrie.fileimport.domain.model.GeometryUtils
import org.kryspetrie.fileimport.domain.model.MetadataHistory
import org.kryspetrie.fileimport.domain.model.RegionType
import org.kryspetrie.fileimport.domain.port.DispatcherProvider
import org.kryspetrie.fileimport.domain.port.GeocodingPort
import org.kryspetrie.fileimport.domain.port.ImageRepositoryPort
import org.kryspetrie.fileimport.domain.port.LocationSearchPort
import org.kryspetrie.fileimport.domain.port.FaceDetectionPort
import org.kryspetrie.fileimport.infrastructure.adapter.toProcessedImage
import org.kryspetrie.fileimport.infrastructure.wizard.BoundingBoxList
import org.kryspetrie.fileimport.infrastructure.wizard.FaceSize
import org.kryspetrie.fileimport.infrastructure.wizard.PhotoConfiguration
import org.kryspetrie.fileimport.infrastructure.wizard.PhotoScanWizardState
import org.kryspetrie.fileimport.infrastructure.wizard.WizardStep
import org.kryspetrie.fileimport.infrastructure.wizard.SourceExifSummary
import org.kryspetrie.fileimport.ui.components.PreviewCache
import org.kryspetrie.fileimport.ui.components.WizardStepIndicator
import org.kryspetrie.fileimport.ui.screens.wizard.edit.EditDialog
import org.kryspetrie.fileimport.ui.screens.wizard.edit.EditMode
import org.kryspetrie.fileimport.ui.screens.wizard.edit.EditModeTab
import org.kryspetrie.fileimport.ui.screens.wizard.edit.FaceNameEntryPanel
import org.kryspetrie.fileimport.ui.screens.wizard.edit.MetadataEditorPanel
import org.kryspetrie.fileimport.ui.screens.wizard.edit.RotateEditorPanel
import org.kryspetrie.fileimport.ui.screens.wizard.edit.ThumbnailStrip
import org.kryspetrie.fileimport.ui.screens.wizard.metadata.LoadSourceExifEffect
import org.kryspetrie.fileimport.ui.screens.wizard.metadata.LocationPickerDialog
import org.kryspetrie.fileimport.domain.model.RecentMetadataSet

/**
 * Edit screen combining rotation and metadata editing into a single view with mode selection.
 *
 * The user chooses between "Rotate" and "Metadata" modes via tabs in the top bar.
 * - Rotate mode: large preview with rotation controls
 * - Metadata mode: large preview with full metadata editing panel
 *
 * Both modes share: thumbnail strip, photo navigation, back image management, and export actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    state: PhotoScanWizardState,
    image: BufferedImage,
    perspectiveService: PerspectiveCorrectionService,
    previewCache: PreviewCache,
    metadataHistory: MetadataHistory,
    onMetadataHistoryUpdate: (String, String) -> Unit,
    onMetadataHistoryRemove: (String, String) -> Unit,
    onRecordMetadataSet: (RecentMetadataSet) -> Unit = {},
    onBack: () -> Unit,
    onExport: () -> Unit,
    onSkipToExport: (() -> Unit)? = null,
    startWithMetadata: Boolean = false,
    modifier: Modifier = Modifier,
    faceRegionTransformer: FaceRegionTransformer? = null,
) {
    val locationSearchService: LocationSearchPort = koinInject()
    val geocodingPort: GeocodingPort = koinInject()
    val dispatcherProvider: DispatcherProvider = koinInject()
    val imageRepository: ImageRepositoryPort = koinInject()
    val faceDetectionPort: FaceDetectionPort = koinInject()

    val boundingBoxList by state.boundingBoxList.collectAsState()
    val photoConfigurations by state.photoConfigurations.collectAsState()
    val selectedIndices by state.selectedMetadataIndices.collectAsState()
    val sourceExif by state.sourceExif.collectAsState()
    val currentImageFile by state.imageFile.collectAsState()

    var editMode by
        remember(startWithMetadata) {
            mutableStateOf(if (startWithMetadata) EditMode.METADATA else EditMode.ROTATE)
        }
    var isMultiEditMode by remember { mutableStateOf(false) }
    var fullscreenPreviewIndex by remember { mutableStateOf<Int?>(null) }
    var showLocationSection by remember { mutableStateOf(false) }
    var showCameraSection by remember { mutableStateOf(false) }
    var showSubjectsSection by remember { mutableStateOf(false) }
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

    // ── Fullscreen preview overlay ──
    if (fullscreenPreviewIndex != null && fullscreenPreviewIndex!! < boundingBoxList.size()) {
        val idx = fullscreenPreviewIndex!!
        val box = boundingBoxList.boxes[idx]
        val config = photoConfigurations[box.id] ?: PhotoConfiguration()
        val fullPreview = previewCache.getFullPreview(image, box, config)
        val fullscreenBitmap = remember(fullPreview) {
            fullPreview?.toComposeImageBitmap()
        }
        androidx.compose.ui.window.Popup(onDismissRequest = { fullscreenPreviewIndex = null }) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (fullscreenBitmap != null) {
                    Image(
                        bitmap = fullscreenBitmap,
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
        val fullPreview = previewCache.getFullPreview(image, box, config)
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
                            if (marginFraction > 0.0)
                                GeometryUtils.applyMargin(detectedPhoto, marginFraction)
                            else detectedPhoto
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
                onAutoDetectFaces = if (faceDetectionPort.isFaceDetectionAvailable()) {
                    {
                        try {
                            val detections = faceDetectionPort.detectFaces(fullPreview.toProcessedImage())
                            if (detections.isNotEmpty()) {
                                val imgW = fullPreview.width.toDouble()
                                val imgH = fullPreview.height.toDouble()
                                val detectedRegions = detections.map { det ->
                                    val centerX = ((det.x1 + det.x2) / 2.0 / imgW).coerceIn(0.0, 1.0)
                                    val centerY = ((det.y1 + det.y2) / 2.0 / imgH).coerceIn(0.0, 1.0)
                                    val width = ((det.x2 - det.x1) / imgW).coerceIn(0.01, 1.0)
                                    FaceRegion(
                                        name = "",
                                        type = RegionType.FACE.mwgRsValue,
                                        x = centerX,
                                        y = centerY,
                                        w = width,
                                        h = width,
                                    )
                                }
                                state.addDetectedFaceRegions(idx, detectedRegions)
                            }
                        } catch (_: Exception) {
                            // Detection failed silently — user can still place faces manually
                        }
                    }
                } else null,
            )
        }
    }

    // ── Face name entry popup ──
    if (showFaceNamePopup && pendingFaceCoords != null) {
        EditDialog(
            onDismissRequest = {
                showFaceNamePopup = false
                pendingFaceCoords = null
            }
        ) {
            FaceNameEntryPanel(
                faceNameInput = faceNameInput,
                onFaceNameInputChange = { faceNameInput = it },
                selectedRegionType = selectedRegionType,
                selectedFaceSize = selectedFaceSize,
                onConfirm = {
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
                onCancel = {
                    showFaceNamePopup = false
                    pendingFaceCoords = null
                    faceNameInput = ""
                },
            )
        }
    }

    // ── Location picker dialog ──
    if (showLocationPicker && locationPickerTargetIndex != null) {
        EditDialog(
            onDismissRequest = {
                showLocationPicker = false
                locationPickerTargetIndex = null
            },
        ) {
            // Note: LocationPickerDialog uses its own Dialog wrapper for platform compatibility
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
            modifier.onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    val isMeta = isCtrlPressed(keyEvent)
                    when {
                        isMeta && keyEvent.key == Key.Comma -> {
                            val currentIdx =
                                if (selectedIndices.size == 1) selectedIndices.first() else -1
                            if (currentIdx > 0) state.selectSingleMetadata(currentIdx - 1)
                            true
                        }
                        isMeta && keyEvent.key == Key.Period -> {
                            val currentIdx =
                                if (selectedIndices.size == 1) selectedIndices.first() else -1
                            if (currentIdx >= 0 && currentIdx < boundingBoxList.size() - 1)
                                state.selectSingleMetadata(currentIdx + 1)
                            true
                        }
                        else -> false
                    }
                } else false
            },
        topBar = {
            TopAppBar(
                title = { Text("Edit Metadata") },
                navigationIcon = { WizardStepIndicator(currentStep = WizardStep.EDIT) },
                actions = {
                    // ── Mode tabs ──
                    EditModeTab(
                        label = "Rotate",
                        selected = editMode == EditMode.ROTATE,
                        onClick = { editMode = EditMode.ROTATE },
                    )
                    EditModeTab(
                        label = "Metadata",
                        selected = editMode == EditMode.METADATA,
                        onClick = { editMode = EditMode.METADATA },
                    )
                    Spacer(Modifier.weight(1f))
                    if (editMode == EditMode.ROTATE && onSkipToExport != null) {
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
                    OutlinedButton(onClick = onBack, modifier = Modifier.height(40.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Back")
                    }
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
                            "${boundingBoxList.size()} ${if (boundingBoxList.size() == 1) "photo" else "photos"}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
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
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                ThumbnailStrip(
                    image = image,
                    perspectiveService = perspectiveService,
                    previewCache = previewCache,
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
            Row(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                // ═══ Left pane: preview + thumbnails ═══
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    ThumbnailStrip(
                        image = image,
                        perspectiveService = perspectiveService,
                        previewCache = previewCache,
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

                    // Large preview — only in single-select mode
                    if (selectedIndices.size == 1 && !isMultiEditMode) {
                        val selectedIndex = selectedIndices.first()
                        val box = boundingBoxList.boxes[selectedIndex]
                        val config = photoConfigurations[box.id] ?: PhotoConfiguration()
                        val visualConfig = PhotoConfiguration(rotationDegrees = config.rotationDegrees)
                        val previewImage = previewCache.getFullPreview(image, box, visualConfig)
                        val previewBitmap = remember(previewImage) {
                            previewImage?.toComposeImageBitmap()
                        }
                        Box(
                            modifier =
                                Modifier.weight(1f)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { fullscreenPreviewIndex = selectedIndex },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (previewBitmap != null) {
                                Image(
                                    bitmap = previewBitmap,
                                    contentDescription =
                                        "Photo ${selectedIndex + 1} — click to enlarge",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit,
                                )
                                // Back-of-photo button
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
                                            OutlinedButton(
                                                onClick = { showBackImagePicker = true },
                                                contentPadding = PaddingValues(0.dp),
                                            ) {
                                                Text(
                                                    "Change",
                                                    style = MaterialTheme.typography.labelSmall,
                                                )
                                            }
                                            OutlinedButton(
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

                // ═══ Right pane: content depends on edit mode ═══
                when (editMode) {
                    EditMode.ROTATE -> {
                        RotateEditorPanel(
                            state = state,
                            boundingBoxList = boundingBoxList,
                            photoConfigurations = photoConfigurations,
                            selectedIndices = selectedIndices,
                            isMultiEditMode = isMultiEditMode,
                            onAddBackImage = { showBackImagePicker = true },
                            onRemoveBackImage = {
                                val idx = selectedIndices.firstOrNull() ?: return@RotateEditorPanel
                                if (idx < boundingBoxList.size()) {
                                    val box = boundingBoxList.boxes[idx]
                                    state.updatePhotoConfiguration(box.id) {
                                        it.copy(
                                            backImageMode = null,
                                            backImageSourcePath = null,
                                            backCropNormalized = null,
                                            backCropRotation = 0,
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                    EditMode.METADATA -> {
                        MetadataEditorPanel(
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
                            onToggleLocationSection = {
                                showLocationSection = !showLocationSection
                            },
                            showSubjectsSection = showSubjectsSection,
                            onToggleSubjectsSection = {
                                showSubjectsSection = !showSubjectsSection
                            },
                            sourceExif = sourceExif,
                            onSelectFaces = { idx -> faceSelectIndex = idx },
                            onPickLocation = { idx ->
                                locationPickerTargetIndex = idx
                                showLocationPicker = true
                            },
                            onAddBackImage = { showBackImagePicker = true },
                            onRemoveBackImage = {
                                val idx =
                                    selectedIndices.firstOrNull() ?: return@MetadataEditorPanel
                                if (idx < boundingBoxList.size()) {
                                    val box = boundingBoxList.boxes[idx]
                                    state.updatePhotoConfiguration(box.id) {
                                        it.copy(
                                            backImageMode = null,
                                            backImageSourcePath = null,
                                            backCropNormalized = null,
                                            backCropRotation = 0,
                                        )
                                    }
                                }
                            },
                            onRecordMetadataSet = onRecordMetadataSet,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}
