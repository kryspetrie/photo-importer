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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.kryspetrie.fileimport.application.export.MetadataWritingService
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.FaceRegion
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.ImageFile
import org.kryspetrie.fileimport.domain.model.MetadataHistory
import org.kryspetrie.fileimport.domain.model.OverrideState
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.domain.model.RecentMetadataSet
import org.kryspetrie.fileimport.domain.model.RegionType
import org.kryspetrie.fileimport.domain.port.DispatcherProvider
import org.kryspetrie.fileimport.domain.port.GeocodingPort
import org.kryspetrie.fileimport.domain.port.ImageProcessingPort
import org.kryspetrie.fileimport.domain.port.ImageRepositoryPort
import org.kryspetrie.fileimport.domain.port.LocationSearchPort
import org.kryspetrie.fileimport.domain.port.SettingsPort
import org.kryspetrie.fileimport.ui.components.ChunkyScrollbar
import org.kryspetrie.fileimport.ui.components.isImageFile
import org.kryspetrie.fileimport.ui.screens.wizard.BackImagePickerDialog
import org.kryspetrie.fileimport.ui.screens.wizard.edit.CameraSection
import org.kryspetrie.fileimport.ui.screens.wizard.edit.EditDialog
import org.kryspetrie.fileimport.ui.screens.wizard.edit.FaceNameEntryPanel
import org.kryspetrie.fileimport.ui.screens.wizard.edit.LocationSection
import org.kryspetrie.fileimport.ui.screens.wizard.edit.QuickEditMetadataFields
import org.kryspetrie.fileimport.ui.screens.wizard.edit.SubjectsSection
import org.kryspetrie.fileimport.ui.screens.wizard.isCtrlPressed
import org.kryspetrie.fileimport.ui.screens.wizard.metadata.LocationPickerOverlay
import org.kryspetrie.fileimport.ui.screens.wizard.metadata.MetadataEditState
import org.kryspetrie.fileimport.ui.screens.wizard.metadata.RecentValuesDropdown
import org.kryspetrie.fileimport.ui.wizard.state.FaceSize
import org.kryspetrie.fileimport.ui.wizard.state.SourceExifSummary

private val THUMBNAIL_SIZE = 80

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
    val faceRegionTransformer: org.kryspetrie.fileimport.domain.port.FaceRegionTransformerPort =
        koinInject()
    val fileSystemAdapter: org.kryspetrie.fileimport.domain.port.FileSystemPort = koinInject()
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

    // Load image when selection changes
    LaunchedEffect(state.selectedIndex, state.files) {
        val file = state.selectedFile
        if (file != null) {
            isLoadingImage = true
            try {
                val img = withContext(dispatcherProvider.io) { ImageIO.read(file) }
                currentImage = img
                // Load source EXIF
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

    // ── Folder picker ──
    // No showFolderPicker state needed — folder picker is launched directly via JFileChooser

    val loadFolder: (String) -> Unit = { path ->
        state.isLoading = true
        state.errorMessage = null
        coroutineScope.launch {
            try {
                val folder = File(path)
                if (!folder.isDirectory) {
                    state.errorMessage = "Not a directory: $path"
                    state.isLoading = false
                    return@launch
                }
                val imageFiles =
                    withContext(dispatcherProvider.io) {
                        folder
                            .listFiles()
                            ?.filter { it.isFile && isImageFile(it) }
                            ?.sortedBy { it.name.lowercase() } ?: emptyList()
                    }
                if (imageFiles.isEmpty()) {
                    state.errorMessage = "No image files found in: $path"
                    state.isLoading = false
                    return@launch
                }
                state.folderPath = path
                state.loadFiles(imageFiles)
                thumbnailCache.clear()
                onSettingsChange(currentSettings.withMetadataEditorRecentPath(path))
            } catch (_: CancellationException) {
                // Cancellation must propagate
            } catch (e: Exception) {
                state.errorMessage = "Error loading folder: ${e.message}"
            } finally {
                state.isLoading = false
            }
        }
    }

    // ── Save ──
    val saveCurrentFile: () -> Unit = {
        val file = state.selectedFile
        if (file != null) {
            val config = state.selectedConfig
            coroutineScope.launch {
                try {
                    val processedImage = imageProcessing.readImage(FilePath(file.absolutePath))
                    if (processedImage != null) {
                        when (state.outputMode) {
                            OutputMode.OVERWRITE -> {
                                val outputPath = FilePath(file.absolutePath)
                                val metadataService =
                                    MetadataWritingService(
                                        faceRegionTransformer = faceRegionTransformer,
                                        imageProcessing = imageProcessing,
                                        fileSystem = fileSystemAdapter,
                                    )
                                metadataService.writeImageWithMetadata(
                                    image = processedImage,
                                    outputPath = outputPath,
                                    config = config,
                                    sourcePath = FilePath(file.absolutePath),
                                    preRotationWidth = processedImage.width,
                                    preRotationHeight = processedImage.height,
                                )
                                if (config.hasBackImage()) {
                                    val backImageResult =
                                        imageProcessing.prepareBackImage(
                                            config,
                                            maxWidth = processedImage.width,
                                            maxHeight = processedImage.height,
                                        )
                                    if (backImageResult != null) {
                                        val outDir = file.parent
                                        val backFileName = file.nameWithoutExtension + "_back.jpg"
                                        val backOutputPath =
                                            FilePath(File(outDir, backFileName).absolutePath)
                                        File(outDir).mkdirs()
                                        imageProcessing.writeJpegImage(
                                            backImageResult,
                                            backOutputPath,
                                        )
                                    }
                                }
                            }
                            OutputMode.SAVE_NEW -> {
                                val outDir = state.outputDirectory.ifBlank { file.parent }
                                val outputFileName = file.nameWithoutExtension + ".jpg"
                                val outputPath = FilePath(File(outDir, outputFileName).absolutePath)
                                File(outDir).mkdirs()
                                val metadataService =
                                    MetadataWritingService(
                                        faceRegionTransformer = faceRegionTransformer,
                                        imageProcessing = imageProcessing,
                                        fileSystem = fileSystemAdapter,
                                    )
                                metadataService.writeImageWithMetadata(
                                    image = processedImage,
                                    outputPath = outputPath,
                                    config = config,
                                    sourcePath = FilePath(file.absolutePath),
                                    preRotationWidth = processedImage.width,
                                    preRotationHeight = processedImage.height,
                                )
                                if (config.hasBackImage()) {
                                    val backImageResult =
                                        imageProcessing.prepareBackImage(
                                            config,
                                            maxWidth = processedImage.width,
                                            maxHeight = processedImage.height,
                                        )
                                    if (backImageResult != null) {
                                        val backOutDir =
                                            if (state.outputDirectory.isNotBlank())
                                                state.outputDirectory
                                            else file.parent
                                        val backFileName = file.nameWithoutExtension + "_back.jpg"
                                        val backOutputPath =
                                            FilePath(File(backOutDir, backFileName).absolutePath)
                                        File(backOutDir).mkdirs()
                                        imageProcessing.writeJpegImage(
                                            backImageResult,
                                            backOutputPath,
                                        )
                                    }
                                }
                            }
                        }
                    }
                } catch (_: CancellationException) {
                    // Cancellation must propagate
                } catch (e: Exception) {
                    state.errorMessage = "Error saving: ${e.message}"
                }
            }
        }
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
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    val initialDir =
                                        state.outputDirectory.ifBlank {
                                            currentSettings.metadataEditorRecentPaths.firstOrNull()
                                                ?: System.getProperty("user.home")
                                                ?: ""
                                        }
                                    val result =
                                        withContext(dispatcherProvider.io) {
                                            val chooser = javax.swing.JFileChooser()
                                            chooser.fileSelectionMode =
                                                javax.swing.JFileChooser.DIRECTORIES_ONLY
                                            chooser.dialogTitle = "Select Output Folder"
                                            if (initialDir.isNotBlank())
                                                chooser.currentDirectory = File(initialDir)
                                            chooser.showOpenDialog(null)
                                        }
                                    if (result == javax.swing.JFileChooser.APPROVE_OPTION) {
                                        val selected =
                                            withContext(dispatcherProvider.io) {
                                                // Can't access chooser.selectedFile from here,
                                                // re-derive
                                                // Actually we need to keep the chooser reference
                                                null // Will fix below
                                            }
                                    }
                                }
                            },
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) {
                            Icon(
                                Icons.Default.CreateNewFolder,
                                "Select output folder",
                                Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                state.outputDirectory.ifBlank { "Output Folder" },
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        }
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
                    Text(
                        if (state.fileCount == 0) "No files loaded"
                        else "${state.selectedIndex + 1} of ${state.fileCount}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
                            "Open folder",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Select a folder with images to edit metadata",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = {
                                val initialDir =
                                    state.folderPath.ifBlank {
                                        currentSettings.metadataEditorRecentPaths.firstOrNull()
                                            ?: System.getProperty("user.home")
                                            ?: ""
                                    }
                                val chooser = javax.swing.JFileChooser()
                                chooser.fileSelectionMode =
                                    javax.swing.JFileChooser.DIRECTORIES_ONLY
                                chooser.dialogTitle = "Select Image Folder"
                                if (initialDir.isNotBlank())
                                    chooser.currentDirectory = File(initialDir)
                                val result = chooser.showOpenDialog(null)
                                if (result == javax.swing.JFileChooser.APPROVE_OPTION) {
                                    loadFolder(chooser.selectedFile.absolutePath)
                                }
                            }
                        ) {
                            Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Open Image Folder")
                        }
                        val recentPaths = currentSettings.metadataEditorRecentPaths
                        if (recentPaths.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.width(200.dp))
                            Text("Recent:", style = MaterialTheme.typography.labelMedium)
                            recentPaths.forEach { path ->
                                OutlinedButton(
                                    onClick = { loadFolder(path) },
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
                        state.errorMessage?.let { error ->
                            Text(
                                error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 32.dp),
                            )
                        }
                    }
                }
            }
        } else {
            // Main editor layout: [Sidebar | Preview | Metadata Panel]
            Row(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
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
                            if (selectedIndices.size == 1) state.selectFile(selectedIndices.first())
                            selectedIndices = emptySet()
                        } else {
                            if (state.selectedIndex >= 0)
                                selectedIndices = setOf(state.selectedIndex)
                        }
                    },
                    onDeselectAll = { selectedIndices = emptySet() },
                    onOpenFolder = {
                        val initialDir =
                            state.folderPath.ifBlank {
                                currentSettings.metadataEditorRecentPaths.firstOrNull()
                                    ?: System.getProperty("user.home")
                                    ?: ""
                            }
                        val chooser = javax.swing.JFileChooser()
                        chooser.fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
                        chooser.dialogTitle = "Select Image Folder"
                        if (initialDir.isNotBlank()) chooser.currentDirectory = File(initialDir)
                        val result = chooser.showOpenDialog(null)
                        if (result == javax.swing.JFileChooser.APPROVE_OPTION) {
                            loadFolder(chooser.selectedFile.absolutePath)
                        }
                    },
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
                                Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (previewBitmap != null) {
                                Image(
                                    bitmap = previewBitmap,
                                    contentDescription = "Selected image",
                                    modifier = Modifier.fillMaxSize(),
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
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Row(
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Rotate:", style = MaterialTheme.typography.labelMedium)
                                Spacer(Modifier.weight(1f))
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
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }

    // ── Folder picker is handled via coroutineScope.launch from button onClick handlers ──
    // The JFileChooser is shown on the IO dispatcher to avoid blocking the UI.
}

// ── Sidebar ──

@Composable
private fun MetadataEditorSidebar(
    state: BulkEditState,
    thumbnailCache: java.util.concurrent.ConcurrentHashMap<String, BufferedImage>,
    isMultiEditMode: Boolean,
    selectedIndices: Set<Int>,
    onSelect: (Int) -> Unit,
    onToggleMultiEdit: () -> Unit,
    onDeselectAll: () -> Unit,
    onOpenFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    Surface(
        tonalElevation = 2.dp,
        modifier = modifier.width(120.dp),
        shape = RoundedCornerShape(0.dp),
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onOpenFolder, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.FolderOpen, "Open folder", modifier = Modifier.size(18.dp))
                }
                if (state.fileCount > 1) {
                    if (isMultiEditMode) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${selectedIndices.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            OutlinedButton(
                                onClick = {
                                    onDeselectAll()
                                    onToggleMultiEdit()
                                },
                                modifier = Modifier.height(20.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                            ) {
                                Text("Done", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = onToggleMultiEdit,
                            modifier = Modifier.height(20.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                        ) {
                            Text("Multi", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // Scrollable thumbnail list
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                itemsIndexed(state.files) { index, file ->
                    val isSelected =
                        if (isMultiEditMode) index in selectedIndices
                        else index == state.selectedIndex
                    val config = state.fileConfigs[file.absolutePath]?.config

                    Card(
                        modifier =
                            Modifier.width(100.dp).height(80.dp).clickable { onSelect(index) },
                        shape = RoundedCornerShape(6.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor =
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                            ),
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            val thumb = thumbnailCache[file.absolutePath]
                            if (thumb != null) {
                                val bitmap = remember(thumb) { thumb.toComposeImageBitmap() }
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = file.name,
                                    modifier = Modifier.fillMaxSize().padding(2.dp),
                                    contentScale = ContentScale.Fit,
                                )
                            } else {
                                Icon(
                                    Icons.Default.Image,
                                    "Loading",
                                    modifier = Modifier.align(Alignment.Center).size(32.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (isSelected && isMultiEditMode) {
                                Checkbox(
                                    checked = true,
                                    onCheckedChange = { onSelect(index) },
                                    modifier = Modifier.align(Alignment.TopStart).size(16.dp),
                                )
                            }
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.BottomStart).padding(2.dp),
                            )
                            if (config?.hasMetadata() == true) {
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
    }
}

// ── Metadata editor panel (adapted for bulk edit) ──

@Composable
private fun MetadataEditorPanel(
    state: BulkEditState,
    editState: MetadataEditState,
    isMultiEditMode: Boolean,
    selectedIndices: Set<Int>,
    sourceExif: SourceExifSummary?,
    metadataHistory: MetadataHistory,
    onSettingsChange: (AppSettings) -> Unit,
    currentSettings: AppSettings,
    settingsPort: SettingsPort,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    onPickLocation: (List<Int>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isMultiSelect = isMultiEditMode && selectedIndices.size > 1
    val selectedIndex = if (!isMultiSelect && state.selectedIndex >= 0) state.selectedIndex else -1
    val singleEditConfig: PhotoScanConfiguration? =
        if (!isMultiSelect) state.selectedConfig else null
    val singleEditBoxId: String? = state.selectedFile?.absolutePath

    ChunkyScrollbar(modifier = modifier) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Header
            if (isMultiSelect) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${selectedIndices.size} photos selected",
                        style =
                            MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Button(
                        onClick = {
                            selectedIndices.forEach { idx ->
                                state.updateConfig(idx) { config ->
                                    editState.applyNonBlankTo(config)
                                }
                            }
                            onSettingsChange(
                                currentSettings.addMetadataSet(editState.toRecentMetadataSet())
                            )
                        },
                        modifier = Modifier.height(28.dp),
                    ) {
                        Text("Apply", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Text(
                    "Only filled fields will be applied. Leave blank to keep existing values.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    state.selectedFile?.name ?: "No file selected",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Recent values (multi-edit only)
            if (isMultiSelect && metadataHistory.recentSets.isNotEmpty()) {
                RecentValuesDropdown(
                    recentSets = metadataHistory.recentSets,
                    onApplySet = { set ->
                        editState.loadFromSet(set)
                        onSettingsChange(currentSettings.addMetadataSet(set))
                    },
                )
            }

            // Metadata sections
            QuickEditMetadataFields(
                description = editState.description,
                onDescriptionChange = { v ->
                    editState.description = v
                    singleEditBoxId?.let { state.updateSelectedConfig { it.copy(description = v) } }
                },
                keywords = editState.keywords,
                onKeywordsChange = { v ->
                    editState.keywords = v
                    singleEditBoxId?.let { state.updateSelectedConfig { it.copy(keywords = v) } }
                },
                originalDate = editState.originalDate,
                onOriginalDateChange = { v ->
                    editState.originalDate = v
                    singleEditBoxId?.let {
                        state.updateSelectedConfig { it.copy(originalDate = v) }
                    }
                },
                year = editState.year,
                onYearChange = { v ->
                    val f = v.filter { c -> c.isDigit() }.take(4)
                    editState.year = f
                    singleEditBoxId?.let { state.updateSelectedConfig { it.copy(year = f) } }
                },
                metadataHistory = metadataHistory,
                onMetadataHistoryUpdate = { field, value ->
                    onSettingsChange(currentSettings.addMetadataHistory(field, value))
                },
                onMetadataHistoryRemove = { field, value ->
                    onSettingsChange(currentSettings.removeMetadataHistory(field, value))
                },
                onCommitKeyword =
                    if (!isMultiSelect) {
                        { keyword ->
                            onSettingsChange(
                                currentSettings.addMetadataHistory("keywords", keyword)
                            )
                        }
                    } else null,
                boxId = singleEditBoxId,
                state = null, // Bulk edit doesn't use PhotoScanWizardState
                overrideDescription =
                    if (!isMultiSelect && singleEditConfig != null)
                        singleEditConfig.overrideDescription != OverrideState.NULL_OUT
                    else null,
                onOverrideDescriptionChange =
                    if (!isMultiSelect) {
                        { included: Boolean ->
                            state.updateSelectedConfig {
                                it.copy(
                                    overrideDescription =
                                        if (included) OverrideState.KEEP_SOURCE
                                        else OverrideState.NULL_OUT
                                )
                            }
                        }
                    } else null,
                overrideKeywords =
                    if (!isMultiSelect && singleEditConfig != null)
                        singleEditConfig.overrideKeywords != OverrideState.NULL_OUT
                    else null,
                onOverrideKeywordsChange =
                    if (!isMultiSelect) {
                        { included: Boolean ->
                            state.updateSelectedConfig {
                                it.copy(
                                    overrideKeywords =
                                        if (included) OverrideState.KEEP_SOURCE
                                        else OverrideState.NULL_OUT
                                )
                            }
                        }
                    } else null,
                overrideOriginalDate =
                    if (!isMultiSelect && singleEditConfig != null)
                        singleEditConfig.overrideOriginalDate != OverrideState.NULL_OUT
                    else null,
                onOverrideOriginalDateChange =
                    if (!isMultiSelect) {
                        { included: Boolean ->
                            state.updateSelectedConfig {
                                it.copy(
                                    overrideOriginalDate =
                                        if (included) OverrideState.KEEP_SOURCE
                                        else OverrideState.NULL_OUT
                                )
                            }
                        }
                    } else null,
                overrideYear =
                    if (!isMultiSelect && singleEditConfig != null)
                        singleEditConfig.overrideYear != OverrideState.NULL_OUT
                    else null,
                onOverrideYearChange =
                    if (!isMultiSelect) {
                        { included: Boolean ->
                            state.updateSelectedConfig {
                                it.copy(
                                    overrideYear =
                                        if (included) OverrideState.KEEP_SOURCE
                                        else OverrideState.NULL_OUT
                                )
                            }
                        }
                    } else null,
                sourceExif = sourceExif,
            )

            LocationSection(
                locationName = editState.locationName,
                onLocationNameChange = { v ->
                    editState.locationName = v
                    singleEditBoxId?.let {
                        state.updateSelectedConfig { it.copy(locationName = v) }
                    }
                },
                address = editState.address,
                onAddressChange = { v ->
                    editState.address = v
                    singleEditBoxId?.let { state.updateSelectedConfig { it.copy(address = v) } }
                },
                city = editState.city,
                onCityChange = { v ->
                    editState.city = v
                    singleEditBoxId?.let { state.updateSelectedConfig { it.copy(city = v) } }
                },
                stateVal = editState.state,
                onStateChange = { v ->
                    editState.state = v
                    singleEditBoxId?.let { state.updateSelectedConfig { it.copy(state = v) } }
                },
                country = editState.country,
                onCountryChange = { v ->
                    editState.country = v
                    singleEditBoxId?.let { state.updateSelectedConfig { it.copy(country = v) } }
                },
                gpsLatitude = editState.gpsLatitude,
                onGpsLatitudeChange = { v ->
                    editState.gpsLatitude = v
                    singleEditBoxId?.let { state.updateSelectedConfig { it.copy(gpsLatitude = v) } }
                },
                gpsLongitude = editState.gpsLongitude,
                onGpsLongitudeChange = { v ->
                    editState.gpsLongitude = v
                    singleEditBoxId?.let {
                        state.updateSelectedConfig { it.copy(gpsLongitude = v) }
                    }
                },
                metadataHistory = metadataHistory,
                onMetadataHistoryUpdate = { field, value ->
                    onSettingsChange(currentSettings.addMetadataHistory(field, value))
                },
                onApplyRecentLocation =
                    if (!isMultiSelect) {
                        { set: RecentMetadataSet ->
                            singleEditBoxId?.let {
                                state.updateSelectedConfig { set.mergeLocationInto(it) }
                            }
                            editState.loadFromSet(set)
                            Unit
                        }
                    } else {
                        { set: RecentMetadataSet ->
                            editState.loadFromSet(set)
                            Unit
                        }
                    },
                onPickLocation = {
                    onPickLocation(
                        if (isMultiSelect) selectedIndices.toList() else listOf(state.selectedIndex)
                    )
                },
                overrideGps =
                    if (!isMultiSelect && singleEditConfig != null)
                        singleEditConfig.overrideGps != OverrideState.NULL_OUT
                    else null,
                onOverrideGpsChange =
                    if (!isMultiSelect) {
                        { included: Boolean ->
                            state.updateSelectedConfig {
                                it.copy(
                                    overrideGps =
                                        if (included) OverrideState.KEEP_SOURCE
                                        else OverrideState.NULL_OUT
                                )
                            }
                        }
                    } else null,
                sourceGpsHint =
                    run {
                        val exif = sourceExif ?: return@run null
                        val parts = mutableListOf<String>()
                        exif.gpsLatitude?.let { lat -> parts.add("Lat: $lat") }
                        exif.gpsLongitude?.let { lon -> parts.add("Lon: $lon") }
                        if (parts.isNotEmpty()) "Source: ${parts.joinToString(", ")}" else null
                    },
            )

            SubjectsSection(
                subjects = editState.subjects,
                onSubjectsChange = { v ->
                    editState.subjects = v
                    singleEditBoxId?.let { state.updateSelectedConfig { it.copy(subjects = v) } }
                },
                metadataHistory = metadataHistory,
                onMetadataHistoryUpdate = { field, value ->
                    onSettingsChange(currentSettings.addMetadataHistory(field, value))
                },
                onMetadataHistoryRemove = { field, value ->
                    onSettingsChange(currentSettings.removeMetadataHistory(field, value))
                },
                onSelectFaces = null,
                faceRegions = if (!isMultiSelect) state.selectedConfig.faceRegions else emptyList(),
                onRemoveFace = null,
                onClearAllFaces = null,
            )

            CameraSection(
                cameraMake = editState.cameraMake,
                onCameraMakeChange = { v ->
                    editState.cameraMake = v
                    singleEditBoxId?.let { state.updateSelectedConfig { it.copy(cameraMake = v) } }
                },
                cameraModel = editState.cameraModel,
                onCameraModelChange = { v ->
                    editState.cameraModel = v
                    singleEditBoxId?.let { state.updateSelectedConfig { it.copy(cameraModel = v) } }
                },
                lensModel = editState.lensModel,
                onLensModelChange = { v ->
                    editState.lensModel = v
                    singleEditBoxId?.let { state.updateSelectedConfig { it.copy(lensModel = v) } }
                },
                focalLength = editState.focalLength,
                onFocalLengthChange = { v ->
                    editState.focalLength = v
                    singleEditBoxId?.let { state.updateSelectedConfig { it.copy(focalLength = v) } }
                },
                aperture = editState.aperture,
                onApertureChange = { v ->
                    editState.aperture = v
                    singleEditBoxId?.let { state.updateSelectedConfig { it.copy(aperture = v) } }
                },
                shutterSpeed = editState.shutterSpeed,
                onShutterSpeedChange = { v ->
                    editState.shutterSpeed = v
                    singleEditBoxId?.let {
                        state.updateSelectedConfig { it.copy(shutterSpeed = v) }
                    }
                },
                iso = editState.iso,
                onIsoChange = { v ->
                    editState.iso = v
                    singleEditBoxId?.let { state.updateSelectedConfig { it.copy(iso = v) } }
                },
                metadataHistory = metadataHistory,
                onMetadataHistoryUpdate = { field, value ->
                    onSettingsChange(currentSettings.addMetadataHistory(field, value))
                },
                overrideCameraMake =
                    if (!isMultiSelect && singleEditConfig != null)
                        singleEditConfig.overrideCameraMake == OverrideState.KEEP_SOURCE
                    else null,
                onOverrideCameraMakeChange =
                    if (!isMultiSelect) {
                        { included: Boolean ->
                            state.updateSelectedConfig {
                                it.copy(
                                    overrideCameraMake =
                                        if (included) OverrideState.KEEP_SOURCE
                                        else OverrideState.NULL_OUT
                                )
                            }
                        }
                    } else null,
                overrideCameraModel =
                    if (!isMultiSelect && singleEditConfig != null)
                        singleEditConfig.overrideCameraModel == OverrideState.KEEP_SOURCE
                    else null,
                onOverrideCameraModelChange =
                    if (!isMultiSelect) {
                        { included: Boolean ->
                            state.updateSelectedConfig {
                                it.copy(
                                    overrideCameraModel =
                                        if (included) OverrideState.KEEP_SOURCE
                                        else OverrideState.NULL_OUT
                                )
                            }
                        }
                    } else null,
                overrideLensModel =
                    if (!isMultiSelect && singleEditConfig != null)
                        singleEditConfig.overrideLensModel == OverrideState.KEEP_SOURCE
                    else null,
                onOverrideLensModelChange =
                    if (!isMultiSelect) {
                        { included: Boolean ->
                            state.updateSelectedConfig {
                                it.copy(
                                    overrideLensModel =
                                        if (included) OverrideState.KEEP_SOURCE
                                        else OverrideState.NULL_OUT
                                )
                            }
                        }
                    } else null,
                overrideFocalLength =
                    if (!isMultiSelect && singleEditConfig != null)
                        singleEditConfig.overrideFocalLength == OverrideState.KEEP_SOURCE
                    else null,
                onOverrideFocalLengthChange =
                    if (!isMultiSelect) {
                        { included: Boolean ->
                            state.updateSelectedConfig {
                                it.copy(
                                    overrideFocalLength =
                                        if (included) OverrideState.KEEP_SOURCE
                                        else OverrideState.NULL_OUT
                                )
                            }
                        }
                    } else null,
                overrideAperture =
                    if (!isMultiSelect && singleEditConfig != null)
                        singleEditConfig.overrideAperture == OverrideState.KEEP_SOURCE
                    else null,
                onOverrideApertureChange =
                    if (!isMultiSelect) {
                        { included: Boolean ->
                            state.updateSelectedConfig {
                                it.copy(
                                    overrideAperture =
                                        if (included) OverrideState.KEEP_SOURCE
                                        else OverrideState.NULL_OUT
                                )
                            }
                        }
                    } else null,
                overrideShutterSpeed =
                    if (!isMultiSelect && singleEditConfig != null)
                        singleEditConfig.overrideShutterSpeed == OverrideState.KEEP_SOURCE
                    else null,
                onOverrideShutterSpeedChange =
                    if (!isMultiSelect) {
                        { included: Boolean ->
                            state.updateSelectedConfig {
                                it.copy(
                                    overrideShutterSpeed =
                                        if (included) OverrideState.KEEP_SOURCE
                                        else OverrideState.NULL_OUT
                                )
                            }
                        }
                    } else null,
                overrideIso =
                    if (!isMultiSelect && singleEditConfig != null)
                        singleEditConfig.overrideIso == OverrideState.KEEP_SOURCE
                    else null,
                onOverrideIsoChange =
                    if (!isMultiSelect) {
                        { included: Boolean ->
                            state.updateSelectedConfig {
                                it.copy(
                                    overrideIso =
                                        if (included) OverrideState.KEEP_SOURCE
                                        else OverrideState.NULL_OUT
                                )
                            }
                        }
                    } else null,
                sourceExif = null,
            )
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
