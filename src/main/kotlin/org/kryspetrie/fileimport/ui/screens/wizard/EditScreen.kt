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
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Sell
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import java.awt.image.BufferedImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.port.SettingsPort
import org.koin.compose.koinInject
import org.kryspetrie.fileimport.domain.port.FaceRegionTransformerPort
import org.kryspetrie.fileimport.domain.port.PerspectiveCorrectionPort
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
import org.kryspetrie.fileimport.infrastructure.adapter.correctPerspective
import org.kryspetrie.fileimport.infrastructure.adapter.transformFaceRegionsFromSource
import org.kryspetrie.fileimport.domain.model.geometry.BoundingBoxList
import org.kryspetrie.fileimport.ui.wizard.state.FaceSize
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.ui.wizard.state.PhotoScanWizardState
import org.kryspetrie.fileimport.ui.wizard.state.SourceExifSummary
import org.kryspetrie.fileimport.ui.components.PreviewCache

import org.kryspetrie.fileimport.ui.screens.wizard.edit.EditDialog
import org.kryspetrie.fileimport.ui.screens.wizard.edit.FaceNameEntryPanel
import org.kryspetrie.fileimport.ui.screens.wizard.edit.MetadataEditorPanel
import org.kryspetrie.fileimport.ui.screens.wizard.edit.PhotoSidebar
import org.kryspetrie.fileimport.ui.screens.wizard.edit.RotationSection
import org.kryspetrie.fileimport.ui.screens.wizard.metadata.LoadSourceExifEffect
import org.kryspetrie.fileimport.ui.screens.wizard.metadata.LocationPickerOverlay
import org.kryspetrie.fileimport.domain.model.RecentMetadataSet

/**
 * Edit screen with vertical thumbnail sidebar on the left, large preview in the center,
 * and metadata panel on the right. Rotation controls are inline in the metadata panel.
 *
 * Layout: [Thumbnail sidebar | Preview | Metadata panel]
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    state: PhotoScanWizardState,
    image: BufferedImage,
    perspectiveService: PerspectiveCorrectionPort,
    previewCache: PreviewCache,
    metadataHistory: MetadataHistory,
    onMetadataHistoryUpdate: (String, String) -> Unit,
    onMetadataHistoryRemove: (String, String) -> Unit,
    onRecordMetadataSet: (RecentMetadataSet) -> Unit = {},
    onBack: () -> Unit,
    onExport: () -> Unit,
    startWithMetadata: Boolean = false,
    modifier: Modifier = Modifier,
    faceRegionTransformer: FaceRegionTransformerPort? = null,
) {
    val locationSearchService: LocationSearchPort = koinInject()
    val geocodingPort: GeocodingPort = koinInject()
    val dispatcherProvider: DispatcherProvider = koinInject()
    val imageRepository: ImageRepositoryPort = koinInject()
    val faceDetectionPort: FaceDetectionPort = koinInject()
    val settingsPort: SettingsPort = koinInject()
    val settings by settingsPort.observeSettings().collectAsState(initial = AppSettings())
    val coroutineScope = rememberCoroutineScope()

    val boundingBoxList by state.boundingBoxList.collectAsState()
    val photoConfigurations by state.photoConfigurations.collectAsState()
    val selectedIndices by state.configs.selectedMetadataIndices.collectAsState()
    val sourceExif by state.sourceExif.collectAsState()
    val currentImageFile by state.imageFile.collectAsState()

    var isMultiEditMode by remember { mutableStateOf(false) }
    var fullscreenPreviewIndex by remember { mutableStateOf<Int?>(null) }
    var showLocationPicker by remember { mutableStateOf(false) }
    var locationPickerTargetIndices by remember { mutableStateOf(emptyList<Int>()) }

    // Face selection state
    var faceSelectIndex by remember { mutableStateOf<Int?>(null) }
    var pendingFaceCoords by remember { mutableStateOf<Triple<Int, Double, Double>?>(null) }
    var showFaceNamePopup by remember { mutableStateOf(false) }
    var faceNameInput by remember { mutableStateOf("") }
    var selectedRegionType by remember { mutableStateOf(RegionType.FACE) }
    var selectedFaceSize by remember { mutableStateOf(FaceSize.DEFAULT) }
    var inheritedFaceRegions by remember { mutableStateOf<List<FaceRegion>>(emptyList()) }
    var autoStartNaming by remember { mutableStateOf(false) }

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
            state.configs.selectSingleMetadata(0)
        }
    }

    // ── Fullscreen preview overlay ──
    if (fullscreenPreviewIndex != null && fullscreenPreviewIndex!! < boundingBoxList.size()) {
        val idx = fullscreenPreviewIndex!!
        val box = boundingBoxList.boxes[idx]
        val config = photoConfigurations[box.id] ?: PhotoScanConfiguration()
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
        val config = photoConfigurations[box.id] ?: PhotoScanConfiguration()
        val fullPreview = previewCache.getFullPreview(image, box, config)
        val sourceFile = state.imageFile.value
        LaunchedEffect(faceSelectIndex, sourceFile) {
            inheritedFaceRegions =
                if (
                    faceSelectIndex != null && faceRegionTransformer != null && sourceFile != null
                ) {
                    try {
                        val marginFraction = state.exportSettings.exportMarginPercent.value
                        val perspectiveEnabled = state.exportSettings.perspectiveCorrectionEnabled.value
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
                    } catch (e: CancellationException) {
                        throw e
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
                onDismiss = {
                    faceSelectIndex = null
                    autoStartNaming = false
                },
                inheritedFaceRegions = inheritedFaceRegions,
                autoStartNaming = autoStartNaming,
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
                                state.faceRegions.addDetectedFaceRegions(idx, detectedRegions)
                                autoStartNaming = true                            }
                        } catch (e: CancellationException) {
                            // Cancellation must propagate to preserve coroutine lifecycle
                            throw e
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
                        state.faceRegions.addFaceRegion(
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


    // ── Back-of-photo image picker dialog ──
    if (showBackImagePicker) {
        BackImagePickerDialog(
            batchFiles = state.batch.sourceFiles.value.ifEmpty { null },
            onConfirm = { sourcePath, cropRect, rotation, mode ->
                val idx = selectedIndices.firstOrNull() ?: return@BackImagePickerDialog
                if (idx < boundingBoxList.size()) {
                    val boxId = boundingBoxList.boxes[idx].id
                    state.configs.updatePhotoScanConfiguration(boxId) {
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
                    state.batch.sourceFiles.value
                        .indexOfFirst { it.absolutePath == sourcePath }
                        .takeIf { it >= 0 }
                        ?.let { state.batch.markBatchIndexSkipped(it) }
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
                            if (currentIdx > 0) state.configs.selectSingleMetadata(currentIdx - 1)
                            true
                        }
                        isMeta && keyEvent.key == Key.Period -> {
                            val currentIdx =
                                if (selectedIndices.size == 1) selectedIndices.first() else -1
                            if (currentIdx >= 0 && currentIdx < boundingBoxList.size() - 1)
                                state.configs.selectSingleMetadata(currentIdx + 1)
                            true
                        }
                        // Ctrl+Enter / Cmd+Enter: trigger export ("Next" action)
                        isMeta && keyEvent.key == Key.Enter -> {
                            if (boundingBoxList.size() > 0) onExport()
                            true
                        }
                        else -> false
                    }
                } else false
            },
        topBar = {
            TopAppBar(
                title = {
                    Text("Edit Photos", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = onBack, modifier = Modifier.height(32.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Back")
                    }
                    // Center: photo count text
                    Text(
                        if (boundingBoxList.size() <= 1)
                            "${boundingBoxList.size()} ${if (boundingBoxList.size() == 1) "photo" else "photos"}"
                        else {
                            val currentIdx =
                                if (selectedIndices.size == 1) selectedIndices.first() else -1
                            if (currentIdx >= 0)
                                "Photo ${currentIdx + 1} of ${boundingBoxList.size()}"
                            else "${boundingBoxList.size()} photos"
                        },
                        style = MaterialTheme.typography.labelSmall,
                    )
                    // Right: export/next button
                    Button(
                        onClick = onExport,
                        enabled = boundingBoxList.size() > 0,
                        modifier = Modifier.height(32.dp),
                    ) {
                        Text("Next", style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(16.dp))
                    }
                }
            }
        },
    ) { paddingValues ->
        Row(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // ═══ Left sidebar: vertical thumbnail strip + multi-edit toggle ═══
            PhotoSidebar(
                image = image,
                perspectiveService = perspectiveService,
                previewCache = previewCache,
                boundingBoxList = boundingBoxList,
                photoConfigurations = photoConfigurations,
                selectedIndices = selectedIndices,
                isMultiEditMode = isMultiEditMode,
                onToggleMultiEdit = { isMultiEditMode = !isMultiEditMode },
                onSelect = { index ->
                    if (isMultiEditMode) {
                        state.configs.toggleMetadataSelection(index)
                    } else {
                        if (index in selectedIndices && selectedIndices.size == 1) {
                            // Don't deselect — clicking selected item in single mode does nothing
                        } else {
                            state.configs.selectSingleMetadata(index)
                        }
                    }
                },
                onDeselectAll = { state.configs.deselectAllMetadata() },
            )

            // ═══ Center: preview area ═══
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                if (selectedIndices.size == 1 && !isMultiEditMode) {
                    val selectedIndex = selectedIndices.first()
                    val box = boundingBoxList.boxes[selectedIndex]
                    val config = photoConfigurations[box.id] ?: PhotoScanConfiguration()
                    val visualConfig = PhotoScanConfiguration(rotationDegrees = config.rotationDegrees)
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
                            // Back-of-photo button (only in the preview window)
                            if (config.hasBackImage()) {
                                // Tag Photo button — opens face selection overlay
                                OutlinedButton(
                                    onClick = { faceSelectIndex = selectedIndex },
                                    modifier =
                                        Modifier.align(Alignment.BottomStart)
                                            .padding(6.dp)
                                            .height(24.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Sell,
                                        "Tag people in photo",
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "Tag Photo",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
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
                                                state.configs.updatePhotoScanConfiguration(box.id) {
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
                                // Tag Photo button — opens face selection overlay
                                OutlinedButton(
                                    onClick = { faceSelectIndex = selectedIndex },
                                    modifier =
                                        Modifier.align(Alignment.BottomStart)
                                            .padding(6.dp)
                                            .height(24.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Sell,
                                        "Tag people in photo",
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "Tag Photo",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                                OutlinedButton(
                                    onClick = { showBackImagePicker = true },
                                    modifier =
                                        Modifier.align(Alignment.BottomEnd)
                                            .padding(6.dp)
                                            .height(24.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp),
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

                // ── Inline rotation controls below preview ──
                if (selectedIndices.size == 1 && !isMultiEditMode) {
                    val selectedIndex = selectedIndices.first()
                    val box = boundingBoxList.boxes[selectedIndex]
                    val config = photoConfigurations[box.id] ?: PhotoScanConfiguration()
                    RotationSection(
                        rotationDegrees = config.rotationDegrees,
                        onRotateCW = {
                            state.configs.updatePhotoScanConfiguration(box.id) { it.cycleRotationCW() }
                        },
                        onRotateCCW = {
                            state.configs.updatePhotoScanConfiguration(box.id) { it.cycleRotationCCW() }
                        },
                        onRotate180 = {
                            state.configs.updatePhotoScanConfiguration(box.id) {
                                it.rotate180()
                            }
                        },
                    )
                } else if (isMultiEditMode && selectedIndices.size > 1) {
                    // Batch rotation controls for multi-select
                    Surface(
                        tonalElevation = 1.dp,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Rotate all:", style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.weight(1f))
                            IconButton(
                                onClick = {
                                    selectedIndices.forEach { idx ->
                                        if (idx < boundingBoxList.size()) {
                                            val box = boundingBoxList.boxes[idx]
                                            state.configs.updatePhotoScanConfiguration(box.id) { it.cycleRotationCCW() }
                                        }
                                    }
                                },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.RotateLeft, "CCW", Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = {
                                    selectedIndices.forEach { idx ->
                                        if (idx < boundingBoxList.size()) {
                                            val box = boundingBoxList.boxes[idx]
                                            state.configs.updatePhotoScanConfiguration(box.id) {
                                                it.rotate180()
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(Icons.Default.Refresh, "Rotate 180°", Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = {
                                    selectedIndices.forEach { idx ->
                                        if (idx < boundingBoxList.size()) {
                                            val box = boundingBoxList.boxes[idx]
                                            state.configs.updatePhotoScanConfiguration(box.id) { it.cycleRotationCW() }
                                        }
                                    }
                                },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.RotateRight, "CW", Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // ═══ Right pane: metadata editor ═══
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
                sourceExif = sourceExif,
                onSelectFaces = { idx -> faceSelectIndex = idx },
                onPickLocation = { indices ->
                    locationPickerTargetIndices = indices
                    showLocationPicker = true
                },
                onRecordMetadataSet = onRecordMetadataSet,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }

    // ── Location picker (full-screen overlay within the same window) ──
    if (showLocationPicker && locationPickerTargetIndices.isNotEmpty()) {
        LocationPickerOverlay(
            locationSearchService = locationSearchService,
            geocodingPort = geocodingPort,
            dispatcherProvider = dispatcherProvider,
            initialLat = settings.lastMapLat,
            initialLon = settings.lastMapLon,
            initialZoom = settings.lastMapZoom,
            onLocationSelected = { result ->
                for (idx in locationPickerTargetIndices) {
                    if (idx < boundingBoxList.size()) {
                        val boxId = boundingBoxList.boxes[idx].id
                        state.configs.updatePhotoScanConfiguration(boxId) {
                            it.copy(
                                locationName = result.name,
                                address = result.displayName,
                                city = result.city ?: it.city,
                                state = result.state ?: it.state,
                                country = result.country ?: it.country,
                                gpsLatitude = result.latitude.toString(),
                                gpsLongitude = result.longitude.toString(),
                            )
                        }
                    }
                }
                showLocationPicker = false
                locationPickerTargetIndices = emptyList()
            },
            onDismiss = {
                showLocationPicker = false
                locationPickerTargetIndices = emptyList()
            },
            onMapLocationChanged = { lat, lon, zoom ->
                coroutineScope.launch {
                    val current = settingsPort.observeSettings().first()
                    settingsPort.saveSettings(
                        current.copy(lastMapLat = lat, lastMapLon = lon, lastMapZoom = zoom)
                    )
                }
            },
        )
    }
}