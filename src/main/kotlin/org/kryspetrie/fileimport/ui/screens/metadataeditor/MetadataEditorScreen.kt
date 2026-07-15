package org.kryspetrie.fileimport.ui.screens.metadataeditor

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.kryspetrie.fileimport.application.export.MetadataWritingService
import org.kryspetrie.fileimport.application.metadata.MetadataEditService
import org.kryspetrie.fileimport.application.metadata.MetadataEditUndoService
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.FaceRegion
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.ImageFile
import org.kryspetrie.fileimport.domain.model.LocationResult
import org.kryspetrie.fileimport.domain.model.OverrideState
import org.kryspetrie.fileimport.domain.model.RegionType
import org.kryspetrie.fileimport.domain.port.DispatcherProvider
import org.kryspetrie.fileimport.domain.port.FaceRegionTransformerPort
import org.kryspetrie.fileimport.domain.port.FileSystemPort
import org.kryspetrie.fileimport.domain.port.GeocodingPort
import org.kryspetrie.fileimport.domain.port.ImageProcessingPort
import org.kryspetrie.fileimport.domain.port.ImageRepositoryPort
import org.kryspetrie.fileimport.domain.port.LocationSearchPort
import org.kryspetrie.fileimport.application.OrientationCorrectionService
import org.kryspetrie.fileimport.domain.model.RotationAngle
import org.kryspetrie.fileimport.domain.port.SettingsPort
import org.kryspetrie.fileimport.ui.components.FolderSelectionField
import org.kryspetrie.fileimport.ui.components.SourcePathField
import org.kryspetrie.fileimport.ui.components.isImageFile
import org.kryspetrie.fileimport.ui.components.pickFolder
import org.kryspetrie.fileimport.ui.components.pickImageFile
import org.kryspetrie.fileimport.ui.screens.wizard.BackImagePickerDialog
import org.kryspetrie.fileimport.ui.screens.wizard.metadata.LocationPickerOverlay
import org.kryspetrie.fileimport.ui.screens.wizard.edit.EditDialog
import org.kryspetrie.fileimport.ui.screens.wizard.edit.FaceNameEntryPanel
import org.kryspetrie.fileimport.ui.screens.wizard.isCtrlPressed
import org.kryspetrie.fileimport.ui.screens.wizard.metadata.MetadataEditState
import org.kryspetrie.fileimport.ui.wizard.state.FaceSize
import org.kryspetrie.fileimport.ui.wizard.state.SourceExifSummary

private val THUMBNAIL_SIZE = 80
private const val MESSAGE_AUTO_CLEAR_MS = 5000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataEditorScreen(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = remember { BulkEditState() }
    val editState = remember { MetadataEditState() }
    val coroutineScope = rememberCoroutineScope()
    val dispatcherProvider: DispatcherProvider = koinInject()
    val imageRepository: ImageRepositoryPort = koinInject()
    val imageProcessing: ImageProcessingPort = koinInject()
    val locationSearchService: LocationSearchPort = koinInject()
    val geocodingPort: GeocodingPort = koinInject()
    val settingsPort: SettingsPort = koinInject()
    val editService: MetadataEditService = koinInject()
    val undoService: MetadataEditUndoService = koinInject()
    val faceRegionTransformer: FaceRegionTransformerPort = koinInject()
    val fileSystemAdapter: FileSystemPort = koinInject()
    val orientationCorrection: OrientationCorrectionService = koinInject()
    val currentSettings by settingsPort.observeSettings().collectAsState(initial = AppSettings())

    // Image loading state
    var currentImage by remember { mutableStateOf<BufferedImage?>(null) }
    var isLoadingImage by remember { mutableStateOf(false) }

    // Source EXIF for current file
    var sourceExif by remember { mutableStateOf<SourceExifSummary?>(null) }

    // Face selection / back image dialogs
    var showFaceNamePopup by remember { mutableStateOf(false) }
    var pendingFaceCoords by remember { mutableStateOf<Triple<Int, Double, Double>?>(null) }
    var faceNameInput by remember { mutableStateOf("") }
    var selectedRegionType by remember { mutableStateOf(RegionType.FACE) }
    var selectedFaceSize by remember { mutableStateOf(FaceSize.DEFAULT) }
    var showBackImagePicker by remember { mutableStateOf(false) }
    var showBulkSelectionDialog by remember { mutableStateOf(false) }
    var showAutoRotateDialog by remember { mutableStateOf(false) }
    var autoRotateResult by remember { mutableStateOf<OrientationCorrectionService.CorrectionResult?>(null) }
    var isDetectingOrientation by remember { mutableStateOf(false) }

    // Location picker
    var showLocationPicker by remember { mutableStateOf(false) }
    var locationPickerTargetIndices by remember { mutableStateOf(emptyList<Int>()) }

    // Thumbnail cache
    val thumbnailCache = remember {
        java.util.concurrent.ConcurrentHashMap<String, BufferedImage>()
    }

    // Multi-edit
    var isMultiEditMode by remember { mutableStateOf(false) }
    var selectedIndices by remember { mutableStateOf<Set<Int>>(emptySet()) }

    // Metadata history
    val metadataHistory = settings.metadataHistory

    // Auto-clear messages after timeout
    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(MESSAGE_AUTO_CLEAR_MS)
            state.clearMessage()
        }
    }

    // Load image when selection changes
    LaunchedEffect(state.selectedIndex, state.files) {
        val file = state.selectedFile
        if (file != null) {
            isLoadingImage = true
            try {
                val img = withContext(dispatcherProvider.io) { ImageIO.read(file) }
                currentImage = img
                try {
                    val meta =
                        withContext(dispatcherProvider.io) {
                            imageRepository.getMetadata(
                                ImageFile(
                                    path = FilePath(file.absolutePath),
                                    fileSize = file.length(),
                                )
                            )
                        }
                    sourceExif =
                        meta?.let {
                            SourceExifSummary(
                                cameraMake = it.make,
                                cameraModel = it.model,
                                lensModel = it.lensModel,
                                focalLength = it.focalLength?.let { f -> "${f}mm" },
                                aperture = it.aperture?.let { a -> "f/$a" },
                                shutterSpeed = it.shutterSpeed,
                                iso = it.iso?.toString(),
                                description = it.description,
                                dateOriginal = it.dateTimeOriginal?.toString(),
                                gpsLatitude = it.latitude?.toString(),
                                gpsLongitude = it.longitude?.toString(),
                            )
                        }
                    state.markSourceExifLoaded(file)
                } catch (_: Exception) {
                    sourceExif = null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                currentImage = null
                sourceExif = null
            } finally {
                isLoadingImage = false
            }
        } else {
            currentImage = null
            sourceExif = null
        }
    }

    // Load thumbnails for sidebar
    LaunchedEffect(state.files) {
        for (file in state.files) {
            if (!thumbnailCache.containsKey(file.absolutePath)) {
                try {
                    val thumb =
                        withContext(dispatcherProvider.io) {
                            val img = ImageIO.read(file) ?: return@withContext null
                            scaleToThumbnail(img)
                        }
                    if (thumb != null) {
                        thumbnailCache[file.absolutePath] = thumb
                    }
                } catch (_: Exception) {
                    /* skip failed thumbnails */
                }
            }
        }
    }

    // Sync editState from current config when selection changes (single-edit mode)
    LaunchedEffect(state.selectedIndex, state.selectedConfig) {
        if (!isMultiEditMode) {
            editState.loadFrom(state.selectedConfig)
        }
    }

    // Clear editState when switching to multi-edit
    LaunchedEffect(isMultiEditMode) {
        if (isMultiEditMode) {
            editState.clear()
        }
    }

    // ── Source loading (file or folder) ──

    val loadSourcePath: (String) -> Unit = { path ->
        state.isLoading = true
        state.message = null
        coroutineScope.launch {
            try {
                val source = File(path)
                if (source.isFile) {
                    if (!isImageFile(source)) {
                        state.showError("Not an image file: $path")
                        return@launch
                    }
                    state.loadSingleFile(source)
                    thumbnailCache.clear()
                    onSettingsChange(currentSettings.withMetadataEditorRecentPath(path))
                } else if (source.isDirectory) {
                    val imageFiles =
                        withContext(dispatcherProvider.io) {
                            source
                                .listFiles()
                                ?.filter { it.isFile && isImageFile(it) }
                                ?.sortedBy { it.name.lowercase() } ?: emptyList()
                        }
                    if (imageFiles.isEmpty()) {
                        state.showError("No image files found in: $path")
                        return@launch
                    }
                    state.sourcePath = path
                    state.loadFiles(imageFiles)
                    thumbnailCache.clear()
                    onSettingsChange(currentSettings.withMetadataEditorRecentPath(path))
                } else {
                    state.showError("Path does not exist: $path")
                }
            } catch (_: CancellationException) {
                // Cancellation must propagate
            } catch (e: Exception) {
                state.showError("Error loading: ${e.message}")
            } finally {
                state.isLoading = false
            }
        }
    }

    val onPickSourceFile: () -> Unit = {
        pickImageFile("Select Image File")?.let { loadSourcePath(it) }
    }

    val onPickSourceFolder: () -> Unit = {
        pickFolder("Select Image Folder")?.let { loadSourcePath(it) }
    }

    val onPickOutputFolder: () -> Unit = {
        pickFolder("Select Output Folder")?.let { state.outputDirectory = it }
    }

    // ── Save current file (delegates to MetadataEditService) ──
    val saveCurrentFile: () -> Unit = {
        val file = state.selectedFile
        if (file != null) {
            val config = state.selectedConfig
            coroutineScope.launch {
                try {
                    val result = editService.saveFile(
                        file = file,
                        config = config,
                        outputMode = state.outputMode.name,
                        outputDirectory = state.outputDirectory,
                    )
                    if (result != null) {
                        val journalPath = editService.saveJournal(
                            sourceFolderPath = state.sourcePath,
                            outputMode = state.outputMode.name,
                            entries = listOf(result.entry),
                        )
                        if (journalPath != null) {
                            state.lastJournalPath = journalPath
                            state.canUndo = true
                            state.canRedo = false
                        }
                        state.markSaved(file)
                        state.showInfo("Saved: ${file.name}")
                    } else {
                        state.showError("Could not read image: ${file.name}")
                    }
                } catch (_: CancellationException) {
                    // Cancellation must propagate
                } catch (e: Exception) {
                    state.showError("Error saving: ${e.message}")
                }
            }
        }
    }

    // ── Save All Modified (delegates to MetadataEditService) ──
    val saveAllModified: () -> Unit = {
        val modifiedEntries = state.fileConfigs.values.filter { it.isModified }
        if (modifiedEntries.isEmpty()) {
            state.showInfo("No unsaved changes")
        } else
        coroutineScope.launch {
            try {
                val entries = mutableListOf<org.kryspetrie.fileimport.domain.model.MetadataEditEntry>()
                var savedCount = 0

                for (entry in modifiedEntries) {
                    val file = entry.file
                    val config = entry.config
                    val result = editService.saveFile(
                        file = file,
                        config = config,
                        outputMode = state.outputMode.name,
                        outputDirectory = state.outputDirectory,
                    )
                    if (result != null) {
                        entries.add(result.entry)
                        state.markSaved(file)
                        savedCount++
                    }
                }

                if (entries.isNotEmpty()) {
                    val journalPath = editService.saveJournal(
                        sourceFolderPath = state.sourcePath,
                        outputMode = state.outputMode.name,
                        entries = entries,
                    )
                    if (journalPath != null) {
                        state.lastJournalPath = journalPath
                        state.canUndo = true
                        state.canRedo = false
                    }
                }

                state.showInfo("Saved $savedCount file${if (savedCount != 1) "s" else ""}")
            } catch (_: CancellationException) {
                // Cancellation must propagate
            } catch (e: Exception) {
                state.showError("Error saving: ${e.message}")
            }
        }
    }

    // ── Undo ──
    val undoLast: () -> Unit = {
        val journalId = state.lastJournalPath
        if (journalId != null) {
            coroutineScope.launch {
                try {
                    val undoResult = undoService.undo(journalId)
                    if (undoResult > 0) {
                        state.showInfo("Undone: $undoResult file${if (undoResult != 1) "s" else ""} restored")
                        state.canUndo = false
                        state.canRedo = true
                        // Reload current image
                        state.selectedIndex = state.selectedIndex
                    } else {
                        state.showError("Undo failed")
                    }
                } catch (e: Exception) {
                    state.showError("Error undoing: ${e.message}")
                }
            }
        }
    }

    // ── Redo (delegates to MetadataEditUndoService) ──
    val redoLast: () -> Unit = {
        val journalId = state.lastJournalPath
        if (journalId != null && state.canRedo) {
            val writer = MetadataWritingService(
                faceRegionTransformer = faceRegionTransformer,
                imageProcessing = imageProcessing,
                fileSystem = fileSystemAdapter,
            )
            coroutineScope.launch {
                try {
                    val redoResult = undoService.redo(journalId) { outputPath, config, sourcePath ->
                        val processedImage = withContext(dispatcherProvider.io) {
                            imageProcessing.readImage(outputPath)
                        }
                        if (processedImage != null) {
                            writer.writeImageWithMetadata(
                                image = processedImage,
                                outputPath = outputPath,
                                config = config,
                                sourcePath = sourcePath ?: outputPath,
                                preRotationWidth = processedImage.width,
                                preRotationHeight = processedImage.height,
                            )
                        }
                    }
                    if (redoResult > 0) {
                        state.showInfo("Redone: $redoResult file${if (redoResult != 1) "s" else ""}")
                        state.canUndo = true
                        state.canRedo = false
                        state.selectedIndex = state.selectedIndex
                    } else {
                        state.showError("Redo failed")
                    }
                } catch (e: Exception) {
                    state.showError("Error redoing: ${e.message}")
                }
            }
        }
    }

    // ── Clear edit fields ──
    val clearEditFields: () -> Unit = {
        editState.clear()
        if (!isMultiEditMode && state.selectedFile != null) {
            editState.loadFrom(state.selectedConfig)
        }
    }

    // ── Apply multi-edit ──
    val applyMultiEdit: () -> Unit = {
        selectedIndices.forEach { idx ->
            state.updateConfig(idx) { config ->
                editState.applyNonBlankTo(config)
            }
        }
        onSettingsChange(currentSettings.addMetadataSet(editState.toRecentMetadataSet()))
        // Clear fields after applying so user can apply different values to another group
        editState.clear()
    }

    // ── Back image picker dialog ──
    if (showBackImagePicker) {
        val currentImageFile = state.selectedFile
        val batchFiles = state.files
        val preSelectedPath =
            state.selectedConfig.backImageSourcePath
                ?: run {
                    val currentPath = currentImageFile?.absolutePath
                    if (currentPath != null) {
                        val currentIdx = state.files.indexOfFirst { it.absolutePath == currentPath }
                        if (currentIdx >= 0 && currentIdx + 1 < state.files.size) {
                            state.files[currentIdx + 1].absolutePath
                        } else null
                    } else null
                }

        BackImagePickerDialog(
            batchFiles = batchFiles.ifEmpty { null },
            preSelectedPath = preSelectedPath,
            onConfirm = { sourcePath, cropResult, rotation, mode ->
                state.updateSelectedConfig { config ->
                    config.copy(
                        backImageMode = mode,
                        backImageSourcePath = sourcePath,
                        backCropNormalized = cropResult?.toNormalizedList(),
                        backCropRotation = rotation,
                    )
                }
                showBackImagePicker = false
            },
            onDismiss = { showBackImagePicker = false },
        )
    }

    // Face name entry popup
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
                        val (_, normX, normY) = pendingFaceCoords!!
                        state.updateSelectedConfig { config ->
                            config.copy(
                                faceRegions =
                                    config.faceRegions +
                                        FaceRegion(
                                            name = faceNameInput.trim(),
                                            type = selectedRegionType.mwgRsValue,
                                            x = normX,
                                            y = normY,
                                            w = 0.1,
                                            h = 0.1,
                                        )
                            )
                        }
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

    // Location picker overlay
    if (showLocationPicker && locationPickerTargetIndices.isNotEmpty()) {
        LocationPickerOverlay(
            locationSearchService = locationSearchService,
            geocodingPort = geocodingPort,
            dispatcherProvider = dispatcherProvider,
            initialLat = currentSettings.lastMapLat,
            initialLon = currentSettings.lastMapLon,
            initialZoom = currentSettings.lastMapZoom,
            onLocationSelected = { result ->
                for (idx in locationPickerTargetIndices) {
                    state.updateConfig(idx) { config ->
                        config.copy(
                            locationName = result.name,
                            address = result.displayName,
                            city = result.city ?: config.city,
                            state = result.state ?: config.state,
                            country = result.country ?: config.country,
                            gpsLatitude = result.latitude.toString(),
                            gpsLongitude = result.longitude.toString(),
                        )
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

    // ── Bulk selection dialog ──
    if (showBulkSelectionDialog && isMultiEditMode) {
        BulkSelectionDialog(
            state = state,
            thumbnailCache = thumbnailCache,
            selectedIndices = selectedIndices,
            onToggleSelection = { index ->
                selectedIndices =
                    if (index in selectedIndices) selectedIndices - index
                    else selectedIndices + index
            },
            onSelectAll = { selectedIndices = state.files.indices.toSet() },
            onSelectNone = { selectedIndices = emptySet() },
            onConfirm = { showBulkSelectionDialog = false },
            onDismiss = { showBulkSelectionDialog = false },
        )
    }

    Scaffold(
        modifier =
            modifier.onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    val isMeta = isCtrlPressed(keyEvent)
                    when {
                        isMeta && keyEvent.key == Key.Comma -> {
                            state.prevFile()
                            true
                        }
                        isMeta && keyEvent.key == Key.Period -> {
                            state.nextFile()
                            true
                        }
                        isMeta && keyEvent.key == Key.Z && !keyEvent.isShiftPressed -> {
                            undoLast()
                            true
                        }
                        isMeta && keyEvent.key == Key.Z && keyEvent.isShiftPressed -> {
                            redoLast()
                            true
                        }
                        isMeta && keyEvent.key == Key.S -> {
                            saveCurrentFile()
                            true
                        }
                        else -> false
                    }
                } else false
            },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Bulk Metadata Editor",
                        style =
                            MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    )
                },
                actions = {
                    Row(
                        modifier = Modifier.padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = state.outputMode == OutputMode.OVERWRITE,
                            onClick = { state.outputMode = OutputMode.OVERWRITE },
                            modifier = Modifier.size(24.dp),
                        )
                        Text("Overwrite", style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.width(8.dp))
                        RadioButton(
                            selected = state.outputMode == OutputMode.SAVE_NEW,
                            onClick = { state.outputMode = OutputMode.SAVE_NEW },
                            modifier = Modifier.size(24.dp),
                        )
                        Text("Save New", style = MaterialTheme.typography.labelSmall)
                    }
                    if (state.outputMode == OutputMode.SAVE_NEW) {
                        FolderSelectionField(
                            value = state.outputDirectory,
                            onValueChange = { state.outputDirectory = it },
                            modifier = Modifier.width(220.dp).height(48.dp),
                            label = "Output",
                            placeholder = "Output folder...",
                            title = "Select Output Folder",
                        )
                    }
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
                    OutlinedButton(
                        onClick = { state.prevFile() },
                        enabled = state.selectedIndex > 0,
                        modifier = Modifier.height(32.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Previous", style = MaterialTheme.typography.labelSmall)
                    }
                    // Status message or file count
                    if (state.message != null) {
                        Text(
                            state.message!!.text,
                            style = MaterialTheme.typography.labelSmall,
                            color =
                                when (state.message!!.severity) {
                                    MessageSeverity.ERROR -> MaterialTheme.colorScheme.error
                                    MessageSeverity.INFO -> MaterialTheme.colorScheme.primary
                                },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false).padding(horizontal = 8.dp),
                        )
                    } else {
                        Text(
                            if (state.fileCount == 0) "No files loaded"
                            else if (state.modifiedCount > 0)
                                "${state.selectedIndex + 1} of ${state.fileCount} · ${state.modifiedCount} unsaved"
                            else "${state.selectedIndex + 1} of ${state.fileCount}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (state.canUndo) {
                            OutlinedButton(
                                onClick = { undoLast() },
                                modifier = Modifier.height(32.dp),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.RotateLeft, "Undo", Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Undo", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (state.canRedo) {
                            OutlinedButton(
                                onClick = { redoLast() },
                                modifier = Modifier.height(32.dp),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.RotateRight, "Redo", Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Redo", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (state.modifiedCount > 1) {
                            Button(
                                onClick = { saveAllModified() },
                                modifier = Modifier.height(32.dp),
                            ) {
                                Icon(Icons.Default.Save, "Save All", Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Save All (${state.modifiedCount})", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Button(
                            onClick = { saveCurrentFile() },
                            enabled = state.selectedFile != null,
                            modifier = Modifier.height(32.dp),
                        ) {
                            Icon(Icons.Default.Save, "Save", Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Save", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = { state.nextFile() },
                            enabled = state.selectedIndex < state.fileCount - 1,
                            modifier = Modifier.height(32.dp),
                        ) {
                            Text("Next", style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(16.dp))
                        }
                    }
                }
            }
        },
    ) { paddingValues ->
        if (state.files.isEmpty()) {
            // Empty state — show folder picker
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator()
                        Text("Loading files...", style = MaterialTheme.typography.bodyLarge)
                    } else {
                        Icon(
                            Icons.Default.FolderOpen,
                            "Open source",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Select a file or folder with images to edit metadata",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Column(
                            modifier = Modifier.width(400.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SourcePathField(
                                value = state.sourcePath,
                                onValueChange = { loadSourcePath(it) },
                                onPickFile = onPickSourceFile,
                                onPickFolder = onPickSourceFolder,
                                modifier = Modifier.fillMaxWidth(),
                                label = "Source",
                                placeholder = "Select file or folder...",
                                isError = state.message?.severity == MessageSeverity.ERROR,
                            )
                            val recentPaths = currentSettings.metadataEditorRecentPaths
                            if (recentPaths.isNotEmpty()) {
                                HorizontalDivider(modifier = Modifier.width(200.dp))
                                Text("Recent:", style = MaterialTheme.typography.labelMedium)
                                recentPaths.forEach { path ->
                                    OutlinedButton(
                                        onClick = { loadSourcePath(path) },
                                        modifier = Modifier.fillMaxWidth(0.6f),
                                    ) {
                                        Text(
                                            path,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                        state.message?.let { msg ->
                            if (msg.severity == MessageSeverity.ERROR) {
                                Text(
                                    msg.text,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 32.dp),
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Main editor layout: [Source bar] + [Sidebar | Preview | Metadata Panel]
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                // ── Source path bar ──
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SourcePathField(
                        value = state.sourcePath,
                        onValueChange = { loadSourcePath(it) },
                        onPickFile = onPickSourceFile,
                        onPickFolder = onPickSourceFolder,
                        modifier = Modifier.weight(1f),
                        label = "Source",
                        placeholder = "File or folder...",
                        isError = state.message?.severity == MessageSeverity.ERROR,
                    )
                    if (isMultiEditMode) {
                        OutlinedButton(
                            onClick = { showBulkSelectionDialog = true },
                            modifier = Modifier.height(40.dp),
                        ) {
                            Text("Select…", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxSize().weight(1f)) {
                    // ═══ Left sidebar: scrollable thumbnail strip ═══
                    MetadataEditorSidebar(
                        state = state,
                        thumbnailCache = thumbnailCache,
                        isMultiEditMode = isMultiEditMode,
                        selectedIndices = selectedIndices,
                        onSelect = { index ->
                            if (isMultiEditMode) {
                                selectedIndices =
                                    if (index in selectedIndices) selectedIndices - index
                                    else selectedIndices + index
                            } else {
                                state.selectFile(index)
                            }
                        },
                        onToggleMultiEdit = {
                            isMultiEditMode = !isMultiEditMode
                            if (!isMultiEditMode) {
                                if (selectedIndices.size == 1)
                                    state.selectFile(selectedIndices.first())
                                selectedIndices = emptySet()
                            } else {
                                if (state.selectedIndex >= 0)
                                    selectedIndices = setOf(state.selectedIndex)
                            }
                        },
                        onDeselectAll = { selectedIndices = emptySet() },
                        onOpenFolder = { onPickSourceFolder() },
                        modifier = Modifier.fillMaxHeight(),
                    )

                    // ═══ Center: image preview ═══
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        if (isLoadingImage) {
                            Box(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        } else if (currentImage != null && !isMultiEditMode) {
                            val previewBitmap =
                                remember(currentImage) { currentImage?.toComposeImageBitmap() }
                            Box(
                                modifier =
                                    Modifier.weight(1f)
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (previewBitmap != null) {
                                    val rotationDeg = state.selectedConfig.rotationDegrees.toFloat()
                                    Image(
                                        bitmap = previewBitmap,
                                        contentDescription = "Selected image",
                                        modifier = Modifier.fillMaxSize()
                                            .graphicsLayer { rotationZ = rotationDeg },
                                        contentScale = ContentScale.Fit,
                                    )

                                    val config = state.selectedConfig

                                    // Back-of-photo controls
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
                                                        state.updateSelectedConfig {
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
                                                modifier = Modifier.size(16.dp),
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

                            // Rotation controls
                            Surface(
                                tonalElevation = 1.dp,
                                shape = RoundedCornerShape(8.dp),
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Column(
                                    modifier =
                                        Modifier.fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text("Rotate:", style = MaterialTheme.typography.labelMedium)
                                        Spacer(Modifier.weight(1f))
                                        // Auto-rotation button
                                        val isAutoAvailable =
                                            orientationCorrection.isAvailable()
                                        IconButton(
                                            onClick = {
                                                val file = state.selectedFile
                                                if (file != null && isAutoAvailable) {
                                                    isDetectingOrientation = true
                                                    coroutineScope.launch {
                                                        try {
                                                            val img = withContext(dispatcherProvider.io) {
                                                                imageProcessing.readImage(
                                                                    FilePath(file.absolutePath)
                                                                )
                                                            }
                                                            if (img != null) {
                                                                val result =
                                                                    orientationCorrection.detectOnly(img)
                                                                if (result != null) {
                                                                    autoRotateResult = result
                                                                    showAutoRotateDialog = true
                                                                } else {
                                                                    state.showError("Could not detect orientation")
                                                                }
                                                            } else {
                                                                state.showError("Could not read image")
                                                            }
                                                        } catch (_: CancellationException) {
                                                            // Cancellation must propagate
                                                        } catch (e: Exception) {
                                                            state.showError("Orientation detection failed: ${e.message}")
                                                        } finally {
                                                            isDetectingOrientation = false
                                                        }
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(24.dp),
                                            enabled = isAutoAvailable && !isDetectingOrientation,
                                        ) {
                                            if (isDetectingOrientation) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(14.dp),
                                                    strokeWidth = 2.dp,
                                                )
                                            } else {
                                                Icon(
                                                    Icons.Default.AutoFixHigh,
                                                    "Auto-detect rotation",
                                                    Modifier.size(16.dp),
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = {
                                                state.updateSelectedConfig { it.cycleRotationCCW() }
                                            },
                                            modifier = Modifier.size(24.dp),
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.RotateLeft,
                                                "CCW",
                                                Modifier.size(16.dp),
                                            )
                                        }
                                        IconButton(
                                            onClick = { state.updateSelectedConfig { it.rotate180() } },
                                            modifier = Modifier.size(24.dp),
                                        ) {
                                            Icon(Icons.Default.Refresh, "180°", Modifier.size(16.dp))
                                        }
                                        IconButton(
                                            onClick = {
                                                state.updateSelectedConfig { it.cycleRotationCW() }
                                            },
                                            modifier = Modifier.size(24.dp),
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.RotateRight,
                                                "CW",
                                                Modifier.size(16.dp),
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "${state.selectedConfig.rotationDegrees}°",
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                    if (!orientationCorrection.isAvailable()) {
                                        Text(
                                            "Auto-rotate requires orientation model",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }

                            // Auto-rotation result dialog
                            if (showAutoRotateDialog && autoRotateResult != null) {
                                val result = autoRotateResult!!
                                val filePath = state.selectedFile?.absolutePath ?: ""
                                val isJpeg = OrientationCorrectionService.isJpegFile(filePath)
                                val currentRotation = state.selectedConfig.rotationDegrees
                                // Calculate corrected rotation: apply detected needed correction
                                val correctedRotation =
                                    (currentRotation + result.nearestRotation.degrees) % 360

                                AlertDialog(
                                    onDismissRequest = {
                                        showAutoRotateDialog = false
                                        autoRotateResult = null
                                    },
                                    title = {
                                        Text("Auto-Rotation Detected")
                                    },
                                    text = {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                "Detected orientation: " +
                                                    "${result.angleDegrees.toInt()}° " +
                                                    "(confidence: ${(result.confidence * 100).toInt()}%)"
                                            )
                                            Text(
                                                "Nearest correction: " +
                                                    "${result.nearestRotation.degrees}°"
                                            )
                                            if (result.nearestRotation == RotationAngle.NONE) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                    shape = RoundedCornerShape(4.dp),
                                                ) {
                                                    Text(
                                                        "Image appears upright — no rotation needed.",
                                                        modifier = Modifier.padding(8.dp),
                                                        style = MaterialTheme.typography.bodySmall,
                                                    )
                                                }
                                            } else {
                                                Text(
                                                    "New rotation would be: $currentRotation° → " +
                                                        "$correctedRotation°"
                                                )
                                                if (isJpeg) {
                                                    Surface(
                                                        color =
                                                            MaterialTheme.colorScheme.errorContainer,
                                                        shape = RoundedCornerShape(4.dp),
                                                    ) {
                                                        Text(
                                                            "⚠ JPEG rotation is lossy — re-encoding " +
                                                                "degrades image quality. This only " +
                                                                "updates metadata rotation, not pixels.",
                                                            modifier = Modifier.padding(8.dp),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color =
                                                                MaterialTheme.colorScheme.onErrorContainer,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    confirmButton = {
                                        if (result.nearestRotation != RotationAngle.NONE) {
                                            TextButton(
                                                onClick = {
                                                    state.updateSelectedConfig {
                                                        it.copy(
                                                            rotationDegrees = correctedRotation,
                                                            faceRegions = it.faceRegions.map { region ->
                                                                when (result.nearestRotation) {
                                                                    RotationAngle.CW_90 -> region.rotate90CW()
                                                                    RotationAngle.CCW_90 -> region.rotate90CCW()
                                                                    RotationAngle.CW_180 -> region.rotate180()
                                                                    RotationAngle.NONE -> region
                                                                }
                                                            }
                                                        )
                                                    }
                                                    showAutoRotateDialog = false
                                                    autoRotateResult = null
                                                    state.showInfo("Rotation corrected to $correctedRotation°")
                                                }
                                            ) {
                                                Text("Apply Rotation")
                                            }
                                        } else {
                                            TextButton(
                                                onClick = {
                                                    showAutoRotateDialog = false
                                                    autoRotateResult = null
                                                }
                                            ) {
                                                Text("OK")
                                            }
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(
                                            onClick = {
                                                showAutoRotateDialog = false
                                                autoRotateResult = null
                                            }
                                        ) {
                                            Text("Cancel")
                                        }
                                    },
                                )
                            }
                        } else if (isMultiEditMode && selectedIndices.isNotEmpty()) {
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
                        } else {
                            Box(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.Image,
                                        "No image",
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        "Select an image",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    // ═══ Right pane: metadata editor ═══
                    MetadataEditorPanel(
                        state = state,
                        editState = editState,
                        isMultiEditMode = isMultiEditMode,
                        selectedIndices = selectedIndices,
                        sourceExif = sourceExif,
                        metadataHistory = metadataHistory,
                        onSettingsChange = onSettingsChange,
                        currentSettings = currentSettings,
                        settingsPort = settingsPort,
                        coroutineScope = coroutineScope,
                        dispatcherProvider = dispatcherProvider,
                        onPickLocation = { indices ->
                            locationPickerTargetIndices = indices
                            showLocationPicker = true
                        },
                        onApply = applyMultiEdit,
                        onClear = clearEditFields,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

// ── Helpers ──

private fun scaleToThumbnail(img: BufferedImage): BufferedImage {
    val width = img.width
    val height = img.height
    if (width <= THUMBNAIL_SIZE && height <= THUMBNAIL_SIZE) return img
    val scale = minOf(THUMBNAIL_SIZE.toFloat() / width, THUMBNAIL_SIZE.toFloat() / height)
    val newWidth = (width * scale).toInt()
    val newHeight = (height * scale).toInt()
    val result = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB)
    val g = result.createGraphics()
    g.drawImage(img, 0, 0, newWidth, newHeight, null)
    g.dispose()
    return result
}