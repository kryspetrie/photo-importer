package org.kryspetrie.fileimport.ui.screens.metadataeditor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.kryspetrie.fileimport.application.OrientationCorrectionService
import org.kryspetrie.fileimport.application.export.MetadataWritingService
import org.kryspetrie.fileimport.application.metadata.MetadataEditService
import org.kryspetrie.fileimport.application.metadata.MetadataEditUndoService
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.FaceRegion
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.ImageFile
import org.kryspetrie.fileimport.domain.model.MetadataEditEntry
import org.kryspetrie.fileimport.domain.model.RotationAngle
import org.kryspetrie.fileimport.domain.port.DispatcherProvider
import org.kryspetrie.fileimport.domain.port.FaceRegionTransformerPort
import org.kryspetrie.fileimport.domain.port.FileSystemPort
import org.kryspetrie.fileimport.domain.port.GeocodingPort
import org.kryspetrie.fileimport.domain.port.ImageProcessingPort
import org.kryspetrie.fileimport.domain.port.ImageRepositoryPort
import org.kryspetrie.fileimport.domain.port.LocationSearchPort
import org.kryspetrie.fileimport.domain.port.ModelDownloadPort
import org.kryspetrie.fileimport.domain.port.ModelDownloadState
import org.kryspetrie.fileimport.domain.port.SettingsPort
import org.kryspetrie.fileimport.ui.components.isImageFile
import org.kryspetrie.fileimport.ui.screens.wizard.metadata.MetadataEditState
import org.kryspetrie.fileimport.ui.wizard.state.SourceExifSummary

private val THUMBNAIL_SIZE = 80

/**
 * ViewModel for the metadata editor screen.
 *
 * Hoists all business logic, service orchestration, and UI state from the MetadataEditorScreen
 * composable. The composable becomes a thin rendering shell that observes this ViewModel's state
 * and delegates all actions to it.
 *
 * ## Responsibilities
 * - Loading source files/folders and caching thumbnails
 * - Saving metadata (single file, all modified, undo, redo)
 * - Orientation detection and correction
 * - Location picking integration
 * - Multi-edit state (selection, applying batch edits)
 * - Auto-clearing status messages
 */
class MetadataEditorViewModel(
    val dispatcherProvider: DispatcherProvider,
    val imageRepository: ImageRepositoryPort,
    val imageProcessing: ImageProcessingPort,
    val locationSearchService: LocationSearchPort,
    val geocodingPort: GeocodingPort,
    val settingsPort: SettingsPort,
    val editService: MetadataEditService,
    val undoService: MetadataEditUndoService,
    val faceRegionTransformer: FaceRegionTransformerPort,
    val fileSystemAdapter: FileSystemPort,
    val orientationCorrection: OrientationCorrectionService,
    val modelDownloadPort: ModelDownloadPort,
) {
    // ── Core state ──────────────────────────────────────────────

    /** Bulk edit state (file list, configs, selection, output mode, messages). */
    val state = BulkEditState()

    /** Metadata field edit state (single & multi-edit field values). */
    val editState = MetadataEditState()

    // ── Image loading ───────────────────────────────────────────

    /** Currently loaded full-resolution image for the selected file. */
    var currentImage by mutableStateOf<BufferedImage?>(null)
        private set

    /** Whether an image is currently being loaded. */
    var isLoadingImage by mutableStateOf(false)
        private set

    /** Source EXIF data for the current file. */
    var sourceExif by mutableStateOf<SourceExifSummary?>(null)
        private set

    /** Thumbnail cache keyed by file absolute path. */
    val thumbnailCache = java.util.concurrent.ConcurrentHashMap<String, BufferedImage>()

    // ── Dialog state ─────────────────────────────────────────────

    /** Whether the face name entry popup is visible. */
    var showFaceNamePopup by mutableStateOf(false)
        private set

    /** Pending face coordinates for the name entry popup: (index, normX, normY). */
    var pendingFaceCoords by mutableStateOf<Triple<Int, Double, Double>?>(null)

    /** Current face name input text. */
    var faceNameInput by mutableStateOf("")

    /** Selected face region type in the name entry popup. */
    var selectedRegionType by mutableStateOf(org.kryspetrie.fileimport.domain.model.RegionType.FACE)

    /** Selected face size in the name entry popup. */
    var selectedFaceSize by
        mutableStateOf(org.kryspetrie.fileimport.ui.wizard.state.FaceSize.DEFAULT)

    /** Whether the back-of-photo image picker dialog is visible. */
    var showBackImagePicker by mutableStateOf(false)
        private set

    /** Whether the bulk selection dialog is visible. */
    var showBulkSelectionDialog by mutableStateOf(false)

    /** Whether the rotation preview overlay is visible. */
    var showRotationPreview by mutableStateOf(false)
        private set

    /** Whether orientation detection is running (batch scan for all files). */
    var isDetectingOrientation by mutableStateOf(false)
        private set

    /** Per-file orientation detection results, keyed by file absolute path. */
    var orientationResults by mutableStateOf<Map<String, OrientationCorrectionService.CorrectionResult>>(emptyMap())
        private set

    /** Set of file paths that the user has unchecked (excluded from rotation). */
    var rotationExcludedPaths by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Index of the file currently shown in the large preview within the rotation overlay. */
    var rotationPreviewIndex by mutableStateOf(-1)
        private set

    /** The full-resolution image loaded for the rotation preview overlay. */
    var rotationPreviewImage by mutableStateOf<BufferedImage?>(null)
        private set

    /** Whether to show the model download prompt dialog. */
    var showModelDownloadDialog by mutableStateOf(false)

    /** Download progress state (null = not downloading). */
    var modelDownloadState by mutableStateOf<ModelDownloadState?>(null)

    /** Whether the orientation model is available (on disk). */
    val isOrientationModelAvailable: Boolean
        get() = modelDownloadPort.isModelDownloaded(ModelDownloadPort.ORIENTATION_MODEL_ID)

    /** Whether the location picker overlay is visible. */
    var showLocationPicker by mutableStateOf(false)
        private set

    /** Indices targeted by the location picker. */
    var locationPickerTargetIndices by mutableStateOf(emptyList<Int>())

    // ── Multi-edit state ─────────────────────────────────────────

    /** Whether multi-edit mode is active. */
    var isMultiEditMode by mutableStateOf(false)

    /** Set of selected indices in multi-edit mode. */
    var selectedIndices by mutableStateOf<Set<Int>>(emptySet())

    // ── Current settings ─────────────────────────────────────────

    /** Cached current settings, observed from settingsPort. */
    var currentSettings by mutableStateOf(AppSettings())

    // ── Actions ──────────────────────────────────────────────────

    /** Start observing settings from the settings port. Call once in a LaunchedEffect. */
    fun observeSettings(scope: CoroutineScope) {
        scope.launch { settingsPort.observeSettings().collect { currentSettings = it } }
    }

    /** Load a source path (file or folder). */
    fun loadSource(path: String, onSettingsChange: (AppSettings) -> Unit) {
        state.isLoading = true
        state.message = null
        val source = File(path)
        if (source.isFile) {
            if (!isImageFile(source)) {
                state.showError("Not an image file: $path")
                return
            }
            state.loadSingleFile(source)
            thumbnailCache.clear()
            onSettingsChange(currentSettings.withMetadataEditorRecentPath(path))
        } else if (source.isDirectory) {
            val imageFiles =
                runCatching {
                        source
                            .listFiles()
                            ?.filter { it.isFile && isImageFile(it) }
                            ?.sortedBy { it.name.lowercase() } ?: emptyList()
                    }
                    .getOrDefault(emptyList())
            if (imageFiles.isEmpty()) {
                state.showError("No image files found in: $path")
                return
            }
            state.sourcePath = path
            state.loadFiles(imageFiles)
            thumbnailCache.clear()
            onSettingsChange(currentSettings.withMetadataEditorRecentPath(path))
        } else {
            state.showError("Path does not exist: $path")
        }
        state.isLoading = false
    }

    /** Load a source path asynchronously (for use from composable callbacks). */
    fun loadSourceAsync(
        path: String,
        scope: CoroutineScope,
        onSettingsChange: (AppSettings) -> Unit,
    ) {
        state.isLoading = true
        state.message = null
        scope.launch {
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

    /** Load the image and EXIF for the currently selected file. Called in LaunchedEffect. */
    suspend fun loadSelectedImage() {
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
            } catch (_: CancellationException) {
                throw CancellationException()
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

    /** Load thumbnails for all files in the sidebar. Called in LaunchedEffect. */
    suspend fun loadThumbnails() {
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

    /** Save the currently selected file's metadata. */
    fun saveCurrentFile(scope: CoroutineScope) {
        val file = state.selectedFile ?: return
        val config = state.selectedConfig
        scope.launch {
            try {
                val result =
                    editService.saveFile(
                        file = file,
                        config = config,
                        outputMode = state.outputMode.name,
                        outputDirectory = state.outputDirectory,
                    )
                if (result != null) {
                    val journalPath =
                        editService.saveJournal(
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

    /** Save all modified files. */
    fun saveAllModified(scope: CoroutineScope) {
        val modifiedEntries = state.fileConfigs.values.filter { it.isModified }
        if (modifiedEntries.isEmpty()) {
            state.showInfo("No unsaved changes")
            return
        }
        scope.launch {
            try {
                val entries = mutableListOf<MetadataEditEntry>()
                var savedCount = 0
                for (entry in modifiedEntries) {
                    val file = entry.file
                    val config = entry.config
                    val result =
                        editService.saveFile(
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
                    val journalPath =
                        editService.saveJournal(
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

    /** Undo the last save operation. */
    fun undoLast(scope: CoroutineScope) {
        val journalId = state.lastJournalPath ?: return
        scope.launch {
            try {
                val undoResult = undoService.undo(journalId)
                if (undoResult > 0) {
                    state.showInfo(
                        "Undone: $undoResult file${if (undoResult != 1) "s" else ""} restored"
                    )
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

    /** Redo the last undo operation. */
    fun redoLast(scope: CoroutineScope) {
        val journalId = state.lastJournalPath ?: return
        if (!state.canRedo) return
        val writer =
            MetadataWritingService(
                faceRegionTransformer = faceRegionTransformer,
                imageProcessing = imageProcessing,
                fileSystem = fileSystemAdapter,
            )
        scope.launch {
            try {
                val redoResult =
                    undoService.redo(journalId) { outputPath, config, sourcePath ->
                        val processedImage =
                            withContext(dispatcherProvider.io) {
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

    /** Clear edit fields, reloading from current config in single-edit mode. */
    fun clearEditFields() {
        editState.clear()
        if (!isMultiEditMode && state.selectedFile != null) {
            editState.loadFrom(state.selectedConfig)
        }
    }

    /** Apply multi-edit fields to all selected indices. */
    fun applyMultiEdit(onSettingsChange: (AppSettings) -> Unit) {
        selectedIndices.forEach { idx ->
            state.updateConfig(idx) { config -> editState.applyNonBlankTo(config) }
        }
        onSettingsChange(currentSettings.addMetadataSet(editState.toRecentMetadataSet()))
        editState.clear()
    }

    /**
     * Detect orientation for all files in the current folder and show the rotation preview overlay.
     * If the orientation model is not available, requests a download instead.
     */
    fun startBatchOrientationDetection(scope: CoroutineScope) {
        if (state.files.isEmpty()) {
            state.showError("No files loaded")
            return
        }
        val modelAvailable = isOrientationModelAvailable || orientationCorrection.isAvailable()
        if (!modelAvailable) {
            requestModelDownload()
            return
        }
        isDetectingOrientation = true
        orientationResults = emptyMap()
        rotationExcludedPaths = emptySet()
        scope.launch {
            try {
                val results = mutableMapOf<String, OrientationCorrectionService.CorrectionResult>()
                for (file in state.files) {
                    try {
                        val img =
                            withContext(dispatcherProvider.io) {
                                imageProcessing.readImage(FilePath(file.absolutePath))
                            }
                        if (img != null) {
                            val result = orientationCorrection.detectOnly(img)
                            if (result != null) {
                                results[file.absolutePath] = result
                            }
                        }
                    } catch (_: CancellationException) {
                        throw CancellationException()
                    } catch (_: Exception) {
                        // Skip files that fail to load or detect
                    }
                }
                if (results.isEmpty()) {
                    state.showError("Could not detect orientation for any files")
                } else {
                    orientationResults = results
                    // Pre-check: exclude files where no rotation is needed (NONE)
                    rotationExcludedPaths =
                        results
                            .filter { it.value.nearestRotation == RotationAngle.NONE }
                            .keys
                            .toSet()
                    rotationPreviewIndex =
                        state.files.indexOfFirst { it.absolutePath in results }.coerceAtLeast(0)
                    showRotationPreview = true
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

    /** Toggle whether a file is excluded from rotation correction. */
    fun toggleRotationExclusion(filePath: String) {
        rotationExcludedPaths =
            if (filePath in rotationExcludedPaths) rotationExcludedPaths - filePath
            else rotationExcludedPaths + filePath
    }

    /** Select all files for rotation (clear exclusions). */
    fun selectAllForRotation() {
        rotationExcludedPaths = emptySet()
    }

    /** Deselect all files for rotation (exclude all). */
    fun deselectAllForRotation() {
        rotationExcludedPaths = orientationResults.keys
    }

    /** Set the preview index for the rotation overlay, loading the full-resolution image. */
    fun updateRotationPreviewIndex(index: Int, scope: kotlinx.coroutines.CoroutineScope) {
        rotationPreviewIndex = index.coerceIn(-1, state.files.size - 1)
        // Load the full-resolution image for preview
        val file = if (index in state.files.indices) state.files[index] else null
        if (file != null) {
            scope.launch {
                try {
                    val img = withContext(dispatcherProvider.io) { ImageIO.read(file) }
                    rotationPreviewImage = img
                } catch (_: CancellationException) {
                    throw CancellationException()
                } catch (_: Exception) {
                    rotationPreviewImage = null
                }
            }
        } else {
            rotationPreviewImage = null
        }
    }

    /**
     * Apply rotation correction to all checked (non-excluded) files.
     * Updates each file's rotationDegrees in the bulk edit state.
     */
    fun applyBatchRotationCorrection() {
        var appliedCount = 0
        for ((filePath, result) in orientationResults) {
            if (filePath in rotationExcludedPaths) continue
            if (result.nearestRotation == RotationAngle.NONE) continue
            val fileIndex = state.files.indexOfFirst { it.absolutePath == filePath }
            if (fileIndex < 0) continue
            val nearestCorrectionDeg =
                when (result.nearestRotation) {
                    RotationAngle.NONE -> 0
                    RotationAngle.CW_90 -> 90
                    RotationAngle.CW_180 -> 180
                    RotationAngle.CCW_90 -> 270
                }
            state.updateConfig(fileIndex) { config ->
                val correctedRotation = (config.rotationDegrees + nearestCorrectionDeg) % 360
                config.copy(
                    rotationDegrees = correctedRotation,
                    faceRegions =
                        config.faceRegions.map { region ->
                            when (result.nearestRotation) {
                                RotationAngle.CW_90 -> region.rotate90CW()
                                RotationAngle.CCW_90 -> region.rotate90CCW()
                                RotationAngle.CW_180 -> region.rotate180()
                                RotationAngle.NONE -> region
                            }
                        },
                )
            }
            appliedCount++
        }
        showRotationPreview = false
        orientationResults = emptyMap()
        rotationExcludedPaths = emptySet()
        rotationPreviewIndex = -1
        rotationPreviewImage = null
        state.showInfo("Applied rotation to $appliedCount file${if (appliedCount != 1) "s" else ""}")
    }

    /** Dismiss the rotation preview overlay without applying changes. */
    fun dismissRotationPreview() {
        showRotationPreview = false
        orientationResults = emptyMap()
        rotationExcludedPaths = emptySet()
        rotationPreviewIndex = -1
        rotationPreviewImage = null
    }

    // ── Model download ────────────────────────────────────────────

    /** Download the orientation detection model. */
    fun downloadOrientationModel(scope: CoroutineScope) {
        modelDownloadState = ModelDownloadState.Connecting
        scope.launch {
            modelDownloadPort.downloadModel(ModelDownloadPort.ORIENTATION_MODEL_ID).collect { state
                ->
                modelDownloadState = state
                if (state is ModelDownloadState.Completed) {
                    showModelDownloadDialog = false
                    modelDownloadState = null
                }
                if (state is ModelDownloadState.Failed || state is ModelDownloadState.Cancelled) {
                    // Keep dialog open so user can see the error / retry
                }
            }
        }
    }

    /** Cancel the orientation model download, or dismiss if no download is active. */
    fun cancelModelDownload() {
        val currentState = modelDownloadState
        if (
            currentState is ModelDownloadState.Downloading ||
                currentState is ModelDownloadState.Connecting
        ) {
            modelDownloadPort.cancelDownload(ModelDownloadPort.ORIENTATION_MODEL_ID)
            modelDownloadState = ModelDownloadState.Cancelled
        }
        showModelDownloadDialog = false
        modelDownloadState = null
    }

    /** Dismiss the model download dialog. */
    fun dismissModelDownloadDialog() {
        showModelDownloadDialog = false
        modelDownloadState = null
    }

    /** Show the model download dialog. */
    fun requestModelDownload() {
        showModelDownloadDialog = true
        modelDownloadState = null
    }

    // ── Face name popup ──────────────────────────────────────────

    fun showFaceNamePopupFor(coords: Triple<Int, Double, Double>) {
        pendingFaceCoords = coords
        showFaceNamePopup = true
    }

    fun confirmFaceName() {
        if (faceNameInput.isNotBlank() && pendingFaceCoords != null) {
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
        dismissFaceNamePopup()
    }

    fun dismissFaceNamePopup() {
        showFaceNamePopup = false
        pendingFaceCoords = null
        faceNameInput = ""
    }

    // ── Dialog toggles ──────────────────────────────────────────

    fun toggleBulkSelectionDialog() {
        showBulkSelectionDialog = !showBulkSelectionDialog
    }

    fun dismissBulkSelectionDialog() {
        showBulkSelectionDialog = false
    }

    fun showBackImagePicker() {
        showBackImagePicker = true
    }

    fun dismissBackImagePicker() {
        showBackImagePicker = false
    }

    fun onBackImageSelected(sourcePath: String?, cropResult: Any?, rotation: Int, mode: String?) {
        state.updateSelectedConfig { config ->
            config.copy(
                backImageMode = mode,
                backImageSourcePath = sourcePath,
                backCropNormalized = null, // Simplified — full crop handling is UI-side
                backCropRotation = rotation,
            )
        }
        showBackImagePicker = false
    }

    fun removeBackImage() {
        state.updateSelectedConfig {
            it.copy(
                backImageMode = null,
                backImageSourcePath = null,
                backCropNormalized = null,
                backCropRotation = 0,
            )
        }
    }

    // ── Location picker ──────────────────────────────────────────

    fun requestLocationPicker(indices: List<Int>) {
        locationPickerTargetIndices = indices
        showLocationPicker = true
    }

    fun onLocationSelected(result: org.kryspetrie.fileimport.domain.model.LocationResult) {
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
    }

    fun dismissLocationPicker() {
        showLocationPicker = false
        locationPickerTargetIndices = emptyList()
    }

    fun updateMapLocation(lat: Double, lon: Double, zoom: Double, scope: CoroutineScope) {
        scope.launch {
            val current = settingsPort.observeSettings().first()
            settingsPort.saveSettings(
                current.copy(lastMapLat = lat, lastMapLon = lon, lastMapZoom = zoom)
            )
        }
    }

    // ── Multi-edit ───────────────────────────────────────────────

    fun toggleMultiEditMode() {
        isMultiEditMode = !isMultiEditMode
        if (!isMultiEditMode) {
            if (selectedIndices.size == 1) {
                state.selectFile(selectedIndices.first())
            }
            selectedIndices = emptySet()
        } else {
            if (state.selectedIndex >= 0) {
                selectedIndices = setOf(state.selectedIndex)
            }
        }
    }

    fun toggleSelection(index: Int) {
        if (isMultiEditMode) {
            selectedIndices =
                if (index in selectedIndices) selectedIndices - index else selectedIndices + index
        } else {
            state.selectFile(index)
        }
    }

    fun selectAll() {
        selectedIndices = state.files.indices.toSet()
    }

    fun deselectAll() {
        selectedIndices = emptySet()
    }

    // ── Settings persistence ──────────────────────────────────────

    fun saveSettings(update: AppSettings, onSettingsChange: (AppSettings) -> Unit) {
        onSettingsChange(update)
    }


    // ── Helper ──────────────────────────────────────────────────

    /** Get pre-selected back image path (next file in sequence, or current back image). */
    fun getPreSelectedBackPath(): String? {
        val currentImageFile = state.selectedFile
        val preSelected =
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
        return preSelected
    }
}

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
