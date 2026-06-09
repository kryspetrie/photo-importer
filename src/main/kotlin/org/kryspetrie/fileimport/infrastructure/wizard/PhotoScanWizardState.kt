package org.kryspetrie.fileimport.infrastructure.wizard

import java.awt.image.BufferedImage
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.kryspetrie.fileimport.domain.model.CorrectionStrategy
import org.kryspetrie.fileimport.domain.model.FaceRegion
import org.kryspetrie.fileimport.domain.model.ImportConfiguration
import org.kryspetrie.fileimport.domain.model.RegionType
import org.kryspetrie.fileimport.infrastructure.logging.AppLogger
import org.kryspetrie.fileimport.infrastructure.logging.OperationType

// Debug flag for performance timing - set to true to log timing data
internal const val DEBUG_TIMING = false

/**
 * Central state container for the Photo Import Wizard. Manages all state including mode, bounding
 * boxes, zoom, and selection.
 */
class PhotoScanWizardState(val imageWidth: Int = 0, val imageHeight: Int = 0) {

    /** Logger for operation tracking. Set externally via setLogger(). */
    var appLogger: AppLogger? = null

    fun setLogger(logger: AppLogger) {
        this.appLogger = logger
    }

    // ========== Import Configuration ==========

    private val _cvAutoDetectEnabled = MutableStateFlow(true)
    val cvAutoDetectEnabled: StateFlow<Boolean> = _cvAutoDetectEnabled.asStateFlow()

    fun setCvAutoDetectEnabled(enabled: Boolean) {
        _cvAutoDetectEnabled.value = enabled
    }

    /**
     * Whether single photo mode is active (skip multi-box detection, import one photo directly).
     */
    private val _singlePhotoMode = MutableStateFlow(false)
    val singlePhotoMode: StateFlow<Boolean> = _singlePhotoMode.asStateFlow()

    fun setSinglePhotoMode(enabled: Boolean) {
        _singlePhotoMode.value = enabled
    }

    private val _configuration = MutableStateFlow(ImportConfiguration())
    val configuration: StateFlow<ImportConfiguration> = _configuration.asStateFlow()

    fun setConfiguration(config: ImportConfiguration) {
        _configuration.value = config
    }

    // ========== Export Settings ==========

    /** Whether to apply perspective correction (warp-stretch) when exporting. Default: true. */
    private val _perspectiveCorrectionEnabled = MutableStateFlow(true)
    val perspectiveCorrectionEnabled: StateFlow<Boolean> =
        _perspectiveCorrectionEnabled.asStateFlow()

    fun setPerspectiveCorrectionEnabled(enabled: Boolean) {
        _perspectiveCorrectionEnabled.value = enabled
    }

    /**
     * Margin to add around each photo during export, expressed as a fraction of the photo's
     * diagonal length. Default: 0.02 (2%). For perspective correction: corners are pushed outward
     * from the quad center. For simple crop: the bounding box is expanded.
     */
    private val _exportMarginPercent = MutableStateFlow(0.02)
    val exportMarginPercent: StateFlow<Double> = _exportMarginPercent.asStateFlow()

    fun setExportMarginPercent(percent: Double) {
        _exportMarginPercent.value = percent.coerceIn(0.0, 0.2)
    }

    /** Default correction strategy for photos that don't have an explicit per-photo strategy. */
    private val _defaultCorrectionStrategy = MutableStateFlow<CorrectionStrategy?>(null)
    val defaultCorrectionStrategy: StateFlow<CorrectionStrategy?> =
        _defaultCorrectionStrategy.asStateFlow()

    fun setDefaultCorrectionStrategy(strategy: CorrectionStrategy?) {
        _defaultCorrectionStrategy.value = strategy
    }

    // ========== Image ==========

    private val _image = MutableStateFlow<BufferedImage?>(null)
    val image: StateFlow<BufferedImage?> = _image.asStateFlow()

    private val _imageFile = MutableStateFlow<File?>(null)
    val imageFile: StateFlow<File?> = _imageFile.asStateFlow()

    /** Read-only EXIF summary from the source file, for display in the metadata editor. */
    private val _sourceExif = MutableStateFlow<SourceExifSummary?>(null)
    val sourceExif: StateFlow<SourceExifSummary?> = _sourceExif.asStateFlow()

    /** Sets the source EXIF summary (called after reading EXIF from the source file). */
    fun setSourceExif(summary: SourceExifSummary?) {
        _sourceExif.value = summary
    }

    // ========== Batch Processing ==========

    /** List of all source image files for batch processing. Empty for single-image mode. */
    private val _sourceFiles = MutableStateFlow<List<File>>(emptyList())
    val sourceFiles: StateFlow<List<File>> = _sourceFiles.asStateFlow()

    /** Index of the currently displayed image within [sourceFiles]. */
    private val _currentImageIndex = MutableStateFlow(0)
    val currentImageIndex: StateFlow<Int> = _currentImageIndex.asStateFlow()

    /** Cache of pre-processed images (loaded + detected). Keyed by index in sourceFiles. */
    private val _preProcessedCache = MutableStateFlow<Map<Int, PreProcessedImage>>(emptyMap())
    val preProcessedCache: StateFlow<Map<Int, PreProcessedImage>> = _preProcessedCache.asStateFlow()

    /** Progress of batch pre-processing: count of images that have been processed. */
    private val _preProcessCount = MutableStateFlow(0)
    val preProcessCount: StateFlow<Int> = _preProcessCount.asStateFlow()

    /** Whether batch pre-processing is currently running. */
    private val _preProcessing = MutableStateFlow(false)
    val preProcessing: StateFlow<Boolean> = _preProcessing.asStateFlow()

    /** True when in batch mode (multiple source files). */
    val isBatchMode: Boolean
        get() = _sourceFiles.value.size > 1

    /** Total number of source images in the batch. */
    val batchTotal: Int
        get() = _sourceFiles.value.size

    /** Initializes batch mode with a list of source files. */
    fun initializeBatch(files: List<File>) {
        _sourceFiles.value = files
        _currentImageIndex.value = 0
        _preProcessedCache.value = emptyMap()
        _preProcessCount.value = 0
    }

    /** Stores a pre-processed result in the cache. */
    fun putPreProcessed(index: Int, result: PreProcessedImage) {
        _preProcessedCache.value = _preProcessedCache.value + (index to result)
        _preProcessCount.value = _preProcessedCache.value.size
    }

    /** Marks batch pre-processing as started or finished. */
    fun setPreProcessing(active: Boolean) {
        _preProcessing.value = active
    }

    /** Switches to a pre-processed image at the given index. Returns false if not cached yet. */
    fun switchToImage(index: Int): Boolean {
        if (index < 0 || index >= _sourceFiles.value.size) return false
        val cached = _preProcessedCache.value[index] ?: return false

        _currentImageIndex.value = index
        _image.value = cached.image
        _imageFile.value = cached.file
        _boundingBoxList.value =
            if (cached.boxes.isNotEmpty()) BoundingBoxList(cached.boxes)
            else BoundingBoxList.empty()
        _selectedBoxIndex.value = -1
        _refinementBoxIndex.value = -1
        _selectedCorner.value = null
        _fourPointState.value = FourPointState.inactive()
        _wizardMode.value = WizardMode.NORMAL
        _photoConfigurations.value = emptyMap()
        _undoRedoManager.clearAll()
        _undoRedoVersion.value++
        updateZoomController()
        return true
    }

    /** Navigates to the next image in the batch. Returns false if can't (not cached or at end). */
    fun nextImage(): Boolean {
        return switchToImage(_currentImageIndex.value + 1)
    }

    /**
     * Navigates to the previous image in the batch. Returns false if can't (not cached or at
     * start).
     */
    fun prevImage(): Boolean {
        return switchToImage(_currentImageIndex.value - 1)
    }

    /** Returns true when in batch mode and there are more images after the current one. */
    val hasMoreBatchImages: Boolean
        get() = isBatchMode && _currentImageIndex.value < _sourceFiles.value.size - 1

    /**
     * Advances the batch index to the next image and returns that file, or null if there are no
     * more. Does not load or detect — caller is responsible for calling initializeWithImage or
     * loadImageAndDetect afterwards.
     */
    fun advanceToNextBatchFile(): File? {
        val nextIndex = _currentImageIndex.value + 1
        if (nextIndex >= _sourceFiles.value.size) return null
        _currentImageIndex.value = nextIndex
        return _sourceFiles.value[nextIndex]
    }

    /**
     * Peeks at the next file in the batch without advancing the index. Returns null if there is no
     * next file. Use this to preview the next photo before the user decides to continue or skip.
     */
    fun peekNextBatchFile(): File? {
        val nextIndex = _currentImageIndex.value + 1
        return if (nextIndex < _sourceFiles.value.size) _sourceFiles.value[nextIndex] else null
    }

    /**
     * Skips the next file in the batch by advancing the index without loading or detecting. The
     * caller remains on the COMPLETE screen — the UI recomposes with updated
     * [hasMoreBatchImages]/[peekNextBatchFile] values. Returns the new "next file" after skipping,
     * or null if there are no more files to process.
     */
    fun skipNextBatchFile(): File? {
        val skippedIndex = _currentImageIndex.value + 1
        if (skippedIndex >= _sourceFiles.value.size) return null
        _currentImageIndex.value = skippedIndex
        _skippedBatchIndices.value = _skippedBatchIndices.value + skippedIndex
        // Return the file after the skipped one (the new "next" preview)
        return peekNextBatchFile()
    }

    /** Returns true if the next image in the batch is pre-processed and ready. */
    val isNextImageReady: Boolean
        get() =
            _currentImageIndex.value < _sourceFiles.value.size - 1 &&
                _preProcessedCache.value.containsKey(_currentImageIndex.value + 1)

    /** Returns true if the previous image in the batch is pre-processed and ready. */
    val isPrevImageReady: Boolean
        get() =
            _currentImageIndex.value > 0 &&
                _preProcessedCache.value.containsKey(_currentImageIndex.value - 1)

    // ========== Back-of-Photo / Auto-Skip ==========

    /**
     * Set of batch file indices that should be automatically skipped during folder processing.
     * These are files identified as "backs" of photographs — they've been selected as back images
     * for other photos and should not be processed as standalone photos.
     */
    private val _skippedBatchIndices = MutableStateFlow<Set<Int>>(emptySet())
    val skippedBatchIndices: StateFlow<Set<Int>> = _skippedBatchIndices.asStateFlow()

    /**
     * Marks a batch file index as skipped (it's a "back" of an already-processed photo). The file
     * will be skipped during automatic batch progression.
     */
    fun markBatchIndexSkipped(index: Int) {
        _skippedBatchIndices.value = _skippedBatchIndices.value + index
    }

    /** Removes a batch file index from the skipped set. */
    fun unmarkBatchIndexSkipped(index: Int) {
        _skippedBatchIndices.value = _skippedBatchIndices.value - index
    }

    /** Returns the set of source files that are marked as skipped (backs of photos). */
    val skippedBatchFiles: Set<File>
        get() = _skippedBatchIndices.value.mapNotNull { _sourceFiles.value.getOrNull(it) }.toSet()

    // ========== Mode and State ==========

    private val _wizardMode = MutableStateFlow(WizardMode.NORMAL)
    val wizardMode: StateFlow<WizardMode> = _wizardMode.asStateFlow()

    private val _fourPointState = MutableStateFlow(FourPointState.inactive())
    val fourPointState: StateFlow<FourPointState> = _fourPointState.asStateFlow()

    // ========== Bounding Boxes ==========

    private val _boundingBoxList = MutableStateFlow(BoundingBoxList.empty())
    val boundingBoxList: StateFlow<BoundingBoxList> = _boundingBoxList.asStateFlow()

    private val _selectedBoxIndex = MutableStateFlow(-1)
    val selectedBoxIndex: StateFlow<Int> = _selectedBoxIndex.asStateFlow()

    private val _refinementBoxIndex = MutableStateFlow(-1)
    val refinementBoxIndex: StateFlow<Int> = _refinementBoxIndex.asStateFlow()

    private val _selectedCorner = MutableStateFlow<Corner?>(null)
    val selectedCorner: StateFlow<Corner?> = _selectedCorner.asStateFlow()

    // ========== Throttled Drag State (4Hz for performance) ==========

    private val _displayRefinementBox = MutableStateFlow<BoundingBox?>(null)
    val displayRefinementBox: StateFlow<BoundingBox?> = _displayRefinementBox.asStateFlow()

    private val _pendingDragX = MutableStateFlow(0.0)
    val pendingDragX: Double
        get() = _pendingDragX.value

    private val _pendingDragY = MutableStateFlow(0.0)
    val pendingDragY: Double
        get() = _pendingDragY.value

    /** True when user is actively dragging a corner (used for 4Hz throttle loop) */
    val isDragging: Boolean
        get() = _selectedCorner.value != null && _refinementBoxIndex.value >= 0

    /** True when there's a pending drag position to sync */
    val hasPendingDrag: Boolean
        get() = _selectedCorner.value != null

    /** Updates the pending drag position (called on every Move event) */
    fun updatePendingDrag(newX: Double, newY: Double) {
        _pendingDragX.value = newX
        _pendingDragY.value = newY
    }

    /** Syncs the display state to show the actual box (called after drag ends). */
    fun syncDisplayBox() {
        val index = _refinementBoxIndex.value
        if (index >= 0 && index < _boundingBoxList.value.size()) {
            _displayRefinementBox.value = _boundingBoxList.value.boxes[index]
        } else {
            _displayRefinementBox.value = null
        }
    }

    /**
     * Syncs the pending drag position to the display state at 4Hz. This is a visual preview only -
     * actual box state updated on release. Called periodically from the LaunchedEffect throttle
     * loop.
     */
    fun syncPendingDrag(boxIndex: Int): Long {
        val startNanos = if (DEBUG_TIMING) System.nanoTime() else 0L

        val corner = _selectedCorner.value ?: return 0
        val imgX = _pendingDragX.value
        val imgY = _pendingDragY.value

        val list = _boundingBoxList.value
        if (boxIndex < 0 || boxIndex >= list.size()) return 0

        val box = list.boxes[boxIndex]
        val moved = box.moveCorner(corner, Point(imgX, imgY))

        // Validate to prevent rendering invalid shapes
        if (moved.corners.wouldCreateInvalidShape()) {
            return 0
        }

        _displayRefinementBox.value = moved

        val elapsed = if (DEBUG_TIMING) (System.nanoTime() - startNanos) / 1000 else 0L
        if (DEBUG_TIMING && elapsed > 500) { // Only log if > 0.5ms
            println("⚠️ syncPendingDrag: ${elapsed}μs (boxIndex=$boxIndex)")
        }
        return elapsed
    }

    // ========== Zoom ==========

    private val _zoomController = MutableStateFlow(ZoomController())
    val zoomController: StateFlow<ZoomController> = _zoomController.asStateFlow()

    // ========== Undo/Redo ==========

    private val _undoRedoVersion = MutableStateFlow(0)
    val undoRedoVersion: StateFlow<Int> = _undoRedoVersion.asStateFlow()

    private val _undoRedoManager = UndoRedoManager.forBoundingBox()

    // ========== Summary Screen Settings ==========

    private val _photoConfigurations = MutableStateFlow<Map<String, PhotoConfiguration>>(emptyMap())
    val photoConfigurations: StateFlow<Map<String, PhotoConfiguration>> =
        _photoConfigurations.asStateFlow()

    // ========== Metadata Screen Selection ==========

    /** Indices of photos selected on the metadata screen. Used for multi-edit. */
    private val _selectedMetadataIndices = MutableStateFlow<Set<Int>>(emptySet())
    val selectedMetadataIndices: StateFlow<Set<Int>> = _selectedMetadataIndices.asStateFlow()

    /** Toggles metadata selection for a photo index (for shift-click multi-select). */
    fun toggleMetadataSelection(index: Int) {
        val current = _selectedMetadataIndices.value
        _selectedMetadataIndices.value = if (index in current) current - index else current + index
    }

    /** Selects a single photo for metadata editing, replacing any previous selection. */
    fun selectSingleMetadata(index: Int) {
        _selectedMetadataIndices.value = setOf(index)
    }

    /** Selects all photos for metadata editing. */
    fun selectAllMetadata() {
        _selectedMetadataIndices.value = (0 until _boundingBoxList.value.size()).toSet()
    }

    /** Deselects all photos on the metadata screen. */
    fun deselectAllMetadata() {
        _selectedMetadataIndices.value = emptySet()
    }

    /**
     * Applies metadata fields to all selected photos on the metadata screen. Only non-empty fields
     * are applied — empty fields are left unchanged on each photo.
     */
    fun applyMetadataToSelected(
        description: String = "",
        keywords: String = "",
        originalDate: String = "",
        year: String = "",
        cameraModel: String = "",
        cameraMake: String = "",
        lensModel: String = "",
        focalLength: String = "",
        aperture: String = "",
        shutterSpeed: String = "",
        iso: String = "",
        locationName: String = "",
        city: String = "",
        state: String = "",
        country: String = "",
        gpsLatitude: String = "",
        gpsLongitude: String = "",
        subjects: String = "",
    ) {
        val indices = _selectedMetadataIndices.value
        val list = _boundingBoxList.value
        for (index in indices) {
            if (index >= 0 && index < list.size()) {
                val boxId = list.boxes[index].id
                updatePhotoConfiguration(boxId) { existing ->
                    existing.copy(
                        description =
                            if (description.isNotBlank()) description else existing.description,
                        keywords = if (keywords.isNotBlank()) keywords else existing.keywords,
                        originalDate =
                            if (originalDate.isNotBlank()) originalDate else existing.originalDate,
                        year = if (year.isNotBlank()) year else existing.year,
                        cameraModel =
                            if (cameraModel.isNotBlank()) cameraModel else existing.cameraModel,
                        cameraMake =
                            if (cameraMake.isNotBlank()) cameraMake else existing.cameraMake,
                        lensModel = if (lensModel.isNotBlank()) lensModel else existing.lensModel,
                        focalLength =
                            if (focalLength.isNotBlank()) focalLength else existing.focalLength,
                        aperture = if (aperture.isNotBlank()) aperture else existing.aperture,
                        shutterSpeed =
                            if (shutterSpeed.isNotBlank()) shutterSpeed else existing.shutterSpeed,
                        iso = if (iso.isNotBlank()) iso else existing.iso,
                        locationName =
                            if (locationName.isNotBlank()) locationName else existing.locationName,
                        city = if (city.isNotBlank()) city else existing.city,
                        state = if (state.isNotBlank()) state else existing.state,
                        country = if (country.isNotBlank()) country else existing.country,
                        gpsLatitude =
                            if (gpsLatitude.isNotBlank()) gpsLatitude else existing.gpsLatitude,
                        gpsLongitude =
                            if (gpsLongitude.isNotBlank()) gpsLongitude else existing.gpsLongitude,
                        subjects = if (subjects.isNotBlank()) subjects else existing.subjects,
                    )
                }
            }
        }
    }

    /**
     * Convenience overload that applies buffered metadata from a [MetadataEditState]. Only
     * non-blank fields are applied — blank fields are left unchanged.
     */
    fun applyMetadataToSelected(
        editState: org.kryspetrie.fileimport.ui.screens.wizard.metadata.MetadataEditState
    ) {
        applyMetadataToSelected(
            description = editState.description,
            keywords = editState.keywords,
            originalDate = editState.originalDate,
            year = editState.year,
            cameraModel = editState.cameraModel,
            cameraMake = editState.cameraMake,
            lensModel = editState.lensModel,
            focalLength = editState.focalLength,
            aperture = editState.aperture,
            shutterSpeed = editState.shutterSpeed,
            iso = editState.iso,
        )
    }

    // ========== Face Selection ==========

    /** Whether face selection mode is active (fullscreen overlay for clicking faces). */
    private val _faceSelectMode = MutableStateFlow(false)
    val faceSelectMode: StateFlow<Boolean> = _faceSelectMode.asStateFlow()

    /** Index of the photo currently in face-select mode, or null if not active. */
    private val _faceSelectPhotoIndex = MutableStateFlow<Int?>(null)
    val faceSelectPhotoIndex: StateFlow<Int?> = _faceSelectPhotoIndex.asStateFlow()

    /** Enters face selection mode for a given photo index. */
    fun enterFaceSelectMode(photoIndex: Int) {
        _faceSelectMode.value = true
        _faceSelectPhotoIndex.value = photoIndex
    }

    /** Exits face selection mode. */
    fun exitFaceSelectMode() {
        _faceSelectMode.value = false
        _faceSelectPhotoIndex.value = null
    }

    /**
     * Adds a face region to the specified photo's configuration. Creates a default-sized bounding
     * box centered at the given normalized coordinates.
     *
     * @param photoIndex Index of the photo in the bounding box list
     * @param name Person's name for the face region
     * @param x Center X as fraction of image width (0.0-1.0)
     * @param y Center Y as fraction of image height (0.0-1.0)
     */
    fun addFaceRegion(
        photoIndex: Int,
        name: String,
        x: Double,
        y: Double,
        type: RegionType = RegionType.FACE,
        size: FaceSize = FaceSize.DEFAULT,
    ) {
        val list = _boundingBoxList.value
        if (photoIndex < 0 || photoIndex >= list.size()) return
        val boxId = list.boxes[photoIndex].id

        val faceRegion =
            FaceRegion(
                name = name,
                type = type.mwgRsValue,
                x = x.coerceIn(0.0, 1.0),
                y = y.coerceIn(0.0, 1.0),
                w = size.diameter,
                h = size.diameter,
            )

        updatePhotoConfiguration(boxId) { existing ->
            val newRegions = existing.faceRegions + faceRegion
            // Auto-populate subjects string with face region names
            val names = newRegions.map { it.name }.filter { it.isNotBlank() }
            val newSubjects = names.joinToString(", ")
            existing.copy(faceRegions = newRegions, subjects = newSubjects)
        }
    }

    /**
     * Removes a face region by index from the specified photo's configuration.
     *
     * @param photoIndex Index of the photo in the bounding box list
     * @param faceIndex Index of the face region within the photo's faceRegions list
     */
    fun removeFaceRegion(photoIndex: Int, faceIndex: Int) {
        val list = _boundingBoxList.value
        if (photoIndex < 0 || photoIndex >= list.size()) return
        val boxId = list.boxes[photoIndex].id

        updatePhotoConfiguration(boxId) { existing ->
            if (faceIndex < 0 || faceIndex >= existing.faceRegions.size)
                return@updatePhotoConfiguration existing
            val removed = existing.faceRegions[faceIndex]
            val newRegions = existing.faceRegions.filterIndexed { i, _ -> i != faceIndex }
            // Remove the name from subjects string
            val currentSubjects =
                existing.subjects.split(",").map { it.trim() }.filter { it.isNotBlank() }
            val updatedSubjects = currentSubjects.filter { it != removed.name }
            existing.copy(faceRegions = newRegions, subjects = updatedSubjects.joinToString(", "))
        }
    }

    /**
     * Removes all face regions from the specified photo's configuration and clears the derived
     * subjects string.
     *
     * @param photoIndex Index of the photo in the bounding box list
     */
    fun clearAllFaceRegions(photoIndex: Int) {
        val list = _boundingBoxList.value
        if (photoIndex < 0 || photoIndex >= list.size()) return
        val boxId = list.boxes[photoIndex].id

        updatePhotoConfiguration(boxId) { existing ->
            existing.copy(faceRegions = emptyList(), subjects = "")
        }
    }

    /**
     * Updates a face region's position at the given index (used for drag-to-move).
     *
     * @param photoIndex Index of the photo in the bounding box list
     * @param faceIndex Index of the face region within the photo's faceRegions list
     * @param x New center X (0.0-1.0), or null to keep current
     * @param y New center Y (0.0-1.0), or null to keep current
     */
    fun updateFaceRegion(photoIndex: Int, faceIndex: Int, x: Double? = null, y: Double? = null) {
        val list = _boundingBoxList.value
        if (photoIndex < 0 || photoIndex >= list.size()) return
        val boxId = list.boxes[photoIndex].id

        updatePhotoConfiguration(boxId) { existing ->
            if (faceIndex < 0 || faceIndex >= existing.faceRegions.size)
                return@updatePhotoConfiguration existing
            val old = existing.faceRegions[faceIndex]
            val updated =
                old.copy(x = x?.coerceIn(0.0, 1.0) ?: old.x, y = y?.coerceIn(0.0, 1.0) ?: old.y)
            existing.copy(
                faceRegions =
                    existing.faceRegions.mapIndexed { i, r -> if (i == faceIndex) updated else r }
            )
        }
    }

    /**
     * Changes a face region's size to one of the preset sizes.
     *
     * @param photoIndex Index of the photo in the bounding box list
     * @param faceIndex Index of the face region within the photo's faceRegions list
     * @param size The new preset size
     */
    fun resizeFaceRegion(photoIndex: Int, faceIndex: Int, size: FaceSize) {
        val list = _boundingBoxList.value
        if (photoIndex < 0 || photoIndex >= list.size()) return
        val boxId = list.boxes[photoIndex].id

        updatePhotoConfiguration(boxId) { existing ->
            if (faceIndex < 0 || faceIndex >= existing.faceRegions.size)
                return@updatePhotoConfiguration existing
            val old = existing.faceRegions[faceIndex]
            val updated = old.copy(w = size.diameter, h = size.diameter)
            existing.copy(
                faceRegions =
                    existing.faceRegions.mapIndexed { i, r -> if (i == faceIndex) updated else r }
            )
        }
    }

    /**
     * Moves a face region by offsetting its center position.
     *
     * @param photoIndex Index of the photo in the bounding box list
     * @param faceIndex Index of the face region within the photo's faceRegions list
     * @param dx X offset to add (in normalized coordinates)
     * @param dy Y offset to add (in normalized coordinates)
     */
    fun moveFaceRegion(photoIndex: Int, faceIndex: Int, dx: Double, dy: Double) {
        val list = _boundingBoxList.value
        if (photoIndex < 0 || photoIndex >= list.size()) return
        val boxId = list.boxes[photoIndex].id

        updatePhotoConfiguration(boxId) { existing ->
            if (faceIndex < 0 || faceIndex >= existing.faceRegions.size)
                return@updatePhotoConfiguration existing
            val old = existing.faceRegions[faceIndex]
            val updated =
                old.copy(x = (old.x + dx).coerceIn(0.0, 1.0), y = (old.y + dy).coerceIn(0.0, 1.0))
            existing.copy(
                faceRegions =
                    existing.faceRegions.mapIndexed { i, r -> if (i == faceIndex) updated else r }
            )
        }
    }

    // ========== Workflow ==========

    private val _currentStep = MutableStateFlow(WizardStep.IMPORT)
    val currentStep: StateFlow<WizardStep> = _currentStep.asStateFlow()

    enum class WizardStep {
        IMPORT, // Mode selection
        OVERVIEW, // All boxes visible
        REFINEMENT, // Zoomed single box (redirects to OVERVIEW - inline refinement)
        SUMMARY, // Crop & rotate grid view
        QUICK_EDIT, // Combined rotation + metadata + location + subjects
        METADATA, // EXIF metadata editing (legacy - superseded by QUICK_EDIT)
        PROCESSING, // Export in progress
        COMPLETE, // Done — post-export completion page
    }

    /** Initializes the wizard with an image file. */
    fun initializeWithImage(image: BufferedImage, file: File) {
        _image.value = image
        _imageFile.value = file

        // Zoom/pan will be set by the UI via fitToView when the container size is known

        // Clear previous state
        _boundingBoxList.value = BoundingBoxList.empty()
        _selectedBoxIndex.value = -1
        _refinementBoxIndex.value = -1
        _selectedCorner.value = null
        _fourPointState.value = FourPointState.inactive()
        _wizardMode.value = WizardMode.NORMAL
        _currentStep.value = WizardStep.OVERVIEW
        _undoRedoManager.clearAll()
        _undoRedoVersion.value++
    }

    /**
     * Initializes single photo mode: loads the image, creates a single bounding box covering the
     * entire image, and jumps directly to the Quick Edit step (skipping overview/detection).
     */
    fun initializeSinglePhoto(image: BufferedImage, file: File) {
        _image.value = image
        _imageFile.value = file
        _singlePhotoMode.value = true

        // Create a single bounding box covering the entire image
        val w = image.width.toDouble()
        val h = image.height.toDouble()
        val fullBox =
            BoundingBox.createRectangular(center = Point(w / 2, h / 2), width = w, height = h)
        _boundingBoxList.value = BoundingBoxList(listOf(fullBox.select()))
        _selectedBoxIndex.value = 0
        _refinementBoxIndex.value = -1
        _selectedCorner.value = null
        _fourPointState.value = FourPointState.inactive()
        _wizardMode.value = WizardMode.NORMAL
        _currentStep.value = WizardStep.QUICK_EDIT
        _undoRedoManager.clearAll()
        _undoRedoVersion.value++
    }

    /** Sets the detected bounding boxes (from YOLO pipeline). */
    fun setDetectedBoxes(boxes: List<BoundingBox>) {
        // Sort in reading order: left-to-right within rows, top-to-bottom across rows.
        // Group by vertical position (within 20% of image height = same row), then sort by x.
        val imageHeight = _image.value?.height?.toDouble() ?: 1.0
        val sorted =
            boxes.sortedWith(
                compareBy<BoundingBox> { box ->
                        // Round y to nearest 20% of image height to group same-row items
                        ((box.corners.center().y / imageHeight) * 5).toInt()
                    }
                    .thenBy { box ->
                        // Within same row, sort by x
                        box.corners.center().x
                    }
            )
        _boundingBoxList.value = BoundingBoxList(sorted)
        _selectedBoxIndex.value = -1
    }

    /**
     * Sets detected bounding boxes with per-box configurations (detection mode, correction
     * strategy).
     *
     * Boxes are sorted in reading order, and configs are re-associated by index after sorting.
     */
    fun setDetectedBoxes(
        boxes: List<BoundingBox>,
        configs: List<PhotoConfiguration> = emptyList(),
    ) {
        // Sort in reading order, tracking original indices to re-associate configs
        val imageHeight = _image.value?.height?.toDouble() ?: 1.0
        val indexed = boxes.mapIndexed { index, box -> index to box }
        val sorted =
            indexed.sortedWith(
                compareBy<Pair<Int, BoundingBox>> { (_, box) ->
                        ((box.corners.center().y / imageHeight) * 5).toInt()
                    }
                    .thenBy { (_, box) -> box.corners.center().x }
            )
        _boundingBoxList.value = BoundingBoxList(sorted.map { (_, box) -> box })
        _selectedBoxIndex.value = -1

        // Apply per-box configurations after sort (re-associated by tracked index)
        if (configs.isNotEmpty()) {
            val configMap = mutableMapOf<String, PhotoConfiguration>()
            sorted.forEach { (originalIndex, box) ->
                if (originalIndex < configs.size) {
                    configMap[box.id] = configs[originalIndex]
                }
            }
            _photoConfigurations.value = _photoConfigurations.value + configMap
        }
    }

    /**
     * Sets the photo configuration for a specific box.
     *
     * @param boxId The ID of the box to configure
     * @param config The configuration to apply
     */
    /**
     * Sets the photo configuration for a specific box.
     *
     * This method stores correction preferences (perspective, rotation, aspect ratio) that will be
     * applied during export. Each box can have its own unique configuration.
     *
     * ## Example
     *
     * ```kotlin
     * state.setPhotoConfiguration(box.id, PhotoConfiguration(
     *     perspectiveCorrectionEnabled = true,
     *     rotationDegrees = 90
     * ))
     * ```
     *
     * @param boxId The unique identifier of the bounding box to configure
     * @param config The [PhotoConfiguration] containing correction settings
     * @see PhotoConfiguration
     * @see updatePhotoConfiguration
     * @see clearPhotoConfiguration
     */
    fun setPhotoConfiguration(boxId: String, config: PhotoConfiguration) {
        _photoConfigurations.value = _photoConfigurations.value + (boxId to config)
    }

    /**
     * Updates the photo configuration for a specific box, preserving existing values.
     *
     * @param boxId The ID of the box to configure
     * @param update A function that takes the existing config and returns a new one
     */
    /**
     * Updates the photo configuration for a specific box, preserving existing values.
     *
     * Unlike [setPhotoConfiguration] which replaces the entire config, this method applies a
     * transformation function to the existing config. Useful for updating a single field while
     * preserving others.
     *
     * ## Example
     *
     * ```kotlin
     * // Enable perspective without changing rotation
     * state.updatePhotoConfiguration(box.id) { config ->
     *     config.copy(perspectiveCorrectionEnabled = true)
     * }
     * ```
     *
     * @param boxId The ID of the box to update
     * @param update A function that takes existing config and returns new config
     * @see setPhotoConfiguration
     * @see clearPhotoConfiguration
     */
    fun updatePhotoConfiguration(
        boxId: String,
        update: (PhotoConfiguration) -> PhotoConfiguration,
    ) {
        val existing = _photoConfigurations.value[boxId] ?: PhotoConfiguration()
        _photoConfigurations.value = _photoConfigurations.value + (boxId to update(existing))
    }

    /**
     * Clears the photo configuration for a specific box.
     *
     * @param boxId The ID of the box to clear configuration for
     */
    /**
     * Clears the photo configuration for a specific box.
     *
     * Removes any correction settings for the specified box, reverting it to default configuration
     * (no corrections enabled).
     *
     * @param boxId The ID of the box to clear configuration for
     * @see setPhotoConfiguration
     * @see updatePhotoConfiguration
     */
    fun clearPhotoConfiguration(boxId: String) {
        _photoConfigurations.value = _photoConfigurations.value - boxId
    }

    /**
     * Applies a rotation to all boxes.
     *
     * @param degrees Rotation in degrees (-90, 90, 180, or 0 to clear)
     */
    /**
     * Applies a rotation to all bounding boxes in the current state.
     *
     * This bulk operation sets the rotation degrees for all existing boxes. Useful when you want to
     * rotate all detected photos uniformly.
     *
     * ## Common Use Cases
     * - Rotate all photos 90° clockwise for scanning orientation correction
     * - Rotate all photos 180° for upside-down scans
     *
     * ## Example
     *
     * ```kotlin
     * // Cycle all boxes 90° clockwise
     * state.rotateAllBoxesCW()
     * // Cycle all boxes 90° counter-clockwise
     * state.rotateAllBoxesCCW()
     * ```
     */
    fun rotateAllBoxesCW() {
        boxes.forEach { box -> updatePhotoConfiguration(box.id) { it.cycleRotationCW() } }
    }

    /**
     * Cycles rotation 90° counter-clockwise for all bounding boxes.
     *
     * Each box's rotation cycles: 0°→270°→180°→90°→0°
     *
     * @see rotateAllBoxesCW
     */
    fun rotateAllBoxesCCW() {
        boxes.forEach { box -> updatePhotoConfiguration(box.id) { it.cycleRotationCCW() } }
    }

    /**
     * Enables or disables perspective correction for all boxes.
     *
     * @param enabled True to enable, false to disable
     */
    /**
     * Enables or disables perspective correction for all bounding boxes.
     *
     * This bulk operation sets the perspective correction flag for all existing boxes. When
     * enabled, photos will be transformed to correct trapezoidal distortion.
     *
     * ## Common Use Cases
     * - Enable perspective on all photos after reviewing scan results
     * - Batch-apply perspective correction before export
     *
     * ## Example
     *
     * ```kotlin
     * // Enable perspective correction for all boxes
     * state.setPerspectiveCorrectionAll(true)
     * ```
     *
     * @param enabled True to enable perspective correction, false to disable
     * @see rotateAllBoxesCW
     * @see clearAllConfigurations
     */
    fun setPerspectiveCorrectionAll(enabled: Boolean) {
        boxes.forEach { box ->
            updatePhotoConfiguration(box.id) { it.copy(perspectiveCorrectionEnabled = enabled) }
        }
    }

    /** Clears all photo configurations. */
    /**
     * Clears all photo configurations, resetting every box to default settings.
     *
     * This removes all correction settings (perspective, rotation, aspect ratio) for all bounding
     * boxes. Each box will use default configuration with no corrections applied.
     *
     * ## Common Use Cases
     * - Reset all photos before re-configuring from scratch
     * - Clear user mistakes and start over
     *
     * ## Example
     *
     * ```kotlin
     * // Clear all configurations and start fresh
     * state.clearAllConfigurations()
     * ```
     *
     * @see setPhotoConfiguration
     * @see rotateAllBoxesCW
     * @see setPerspectiveCorrectionAll
     */
    fun clearAllConfigurations() {
        _photoConfigurations.value = emptyMap()
    }

    /** Returns all bounding boxes as a list. */
    val boxes: List<BoundingBox>
        get() = _boundingBoxList.value.boxes

    /** Adds a bounding box. Respects overlap detection from [BoundingBoxList]. */
    fun addBox(box: BoundingBox): Boolean {
        val currentList = _boundingBoxList.value
        if (!currentList.canAdd(box)) {
            appLogger?.warn("Box not added: overlaps with existing box")
            return false
        }
        _boundingBoxList.value = currentList.copy(boxes = currentList.boxes + box)
        appLogger?.logOperationComplete(
            OperationType.BOX_CREATION,
            "Box ${currentList.size() + 1} at (${box.center().x.toInt()}, " +
                "${box.center().y.toInt()}), size: ${box.width().toInt()}x${box.height().toInt()}",
        )
        return true
    }

    /** Removes a bounding box by index. */
    fun removeBox(index: Int) {
        val list = _boundingBoxList.value
        if (index >= 0 && index < list.size()) {
            val box = list.boxes[index]
            val boxId = box.id
            // Save for undo
            _undoRedoManager.push(box.id, box)
            // Remove
            _boundingBoxList.value = list.remove(box.id)

            // Clear selection if this was selected
            if (_selectedBoxIndex.value == index) {
                _selectedBoxIndex.value = -1
            }
            if (_refinementBoxIndex.value == index) {
                _refinementBoxIndex.value = -1
            }
            appLogger?.logOperationComplete(OperationType.BOX_DELETION, "Removed box $boxId")
        }
    }

    /** Removes the currently selected bounding box. */
    fun removeSelectedBox() {
        val index = _selectedBoxIndex.value
        if (index >= 0) {
            removeBox(index)
        }
    }

    /** Updates a bounding box at the given index. */
    fun updateBox(index: Int, box: BoundingBox) {
        val list = _boundingBoxList.value
        _boundingBoxList.value = list.updateAt(index) { box }
    }

    /** Selects a box at the given index. */
    fun selectBox(index: Int) {
        _boundingBoxList.value = _boundingBoxList.value.selectAt(index)
        _selectedBoxIndex.value = index
    }

    /** Deselects all boxes. */
    fun deselectAll() {
        _boundingBoxList.value = _boundingBoxList.value.deselectAll()
        _selectedBoxIndex.value = -1
        _selectedCorner.value = null
    }

    /**
     * Selects a corner for arrow key movement. Does NOT change selectedBoxIndex — callers must set
     * that explicitly via selectBox() or enterRefinement(). Previously this set selectedBoxIndex =
     * refinementBoxIndex, which broke overview-page corner dragging because refinementBoxIndex is
     * -1 when not in refinement mode.
     */
    fun selectCorner(corner: Corner) {
        _selectedCorner.value = corner
    }

    /** Deselects the current corner. */
    fun deselectCorner() {
        _selectedCorner.value = null
    }

    // ========== Mode Management ==========

    /** Enters 4-point mode for creating a quadrilateral bounding box. */
    fun enterFourPointMode() {
        _fourPointState.value = FourPointState.activeQuad()
        _wizardMode.value = WizardMode.FOUR_POINT
        deselectAll()
    }

    /** Enters 2-click rectangle mode for creating a rectangular bounding box. */
    fun enterRectangleMode() {
        _fourPointState.value = FourPointState.activeRectangle()
        _wizardMode.value = WizardMode.FOUR_POINT
        deselectAll()
    }

    /** Exits 4-point mode. */
    fun exitFourPointMode() {
        _fourPointState.value = FourPointState.inactive()
        if (_wizardMode.value == WizardMode.FOUR_POINT) {
            _wizardMode.value = WizardMode.NORMAL
        }
    }

    /** Updates the mouse position for drawing line preview in creation mode. */
    fun updateCreationMousePosition(point: Point?) {
        if (_wizardMode.value == WizardMode.FOUR_POINT) {
            _fourPointState.value = _fourPointState.value.updatePendingPoint(point)
        }
    }

    /** Adds a point in 4-point mode. */
    fun addFourPoint(point: Point) {
        val newState = _fourPointState.value.addPoint(point)
        _fourPointState.value = newState

        // If complete, create the bounding box
        if (newState.isComplete()) {
            val box = newState.confirm()
            if (box != null) {
                addBox(box.select())
                _selectedBoxIndex.value = _boundingBoxList.value.size() - 1
            }
            exitFourPointMode()
        }
    }

    /** Removes the last point in 4-point mode. */
    fun removeLastFourPoint() {
        _fourPointState.value = _fourPointState.value.removeLastPoint()
        if (_fourPointState.value.mode == FourPointState.Mode.INACTIVE) {
            exitFourPointMode()
        }
    }

    /** Confirms 4-point selection. */
    fun confirmFourPoint() {
        if (_fourPointState.value.canConfirm()) {
            val box = _fourPointState.value.confirm()
            if (box != null) {
                addBox(box.select())
                _selectedBoxIndex.value = _boundingBoxList.value.size() - 1
            }
            exitFourPointMode()
        }
    }

    /** Enters add box mode. */
    fun enterAddBoxMode() {
        _wizardMode.value = WizardMode.ADD_BOX
        deselectAll()
    }

    /** Exits add box mode. */
    fun exitAddBoxMode() {
        if (_wizardMode.value == WizardMode.ADD_BOX) {
            _wizardMode.value = WizardMode.NORMAL
        }
    }

    /** Creates a rectangular bounding box at the given center point. */
    /**
     * Creates a rectangular bounding box at the given center point.
     *
     * @param centerX Center X coordinate in image space
     * @param centerY Center Y coordinate in image space
     * @param minSize Minimum dimension for the box (default 50 pixels)
     * @return True if the box was created, false if it was too small
     */
    fun createBoxAtCenter(
        centerX: Double,
        centerY: Double,
        minSize: Double = PhotoScanConstants.MIN_BOX_SIZE_PIXELS,
    ): Boolean {
        val imageWidth = _image.value?.width?.toDouble()
        val imageHeight = _image.value?.height?.toDouble()

        // Calculate box dimensions based on image size using constants
        val width = imageWidth?.times(PhotoScanConstants.DEFAULT_BOX_WIDTH_RATIO) ?: 100.0
        val height = width / PhotoScanConstants.DEFAULT_BOX_ASPECT_RATIO

        // Validate minimum box size to prevent tiny boxes using constant
        if (width < minSize || height < minSize) {
            val imageSizeInfo =
                if (imageWidth != null && imageHeight != null) {
                    " (image is ${imageWidth.toInt()}x${imageHeight.toInt()}px)"
                } else {
                    ""
                }
            appLogger?.warn(
                "Cannot create box: size $width x $height below minimum $minSize px$imageSizeInfo"
            )
            return false
        }

        val box = BoundingBox.createRectangular(centerX, centerY, width, height)
        addBox(box.select())
        _selectedBoxIndex.value = _boundingBoxList.value.size() - 1

        exitAddBoxMode()
        return true
    }

    /** Enters refinement mode for the box at the given index. */
    fun enterRefinement(boxIndex: Int) {
        _refinementBoxIndex.value = boxIndex
        _selectedBoxIndex.value = boxIndex
        _selectedCorner.value = null // Clear any previous corner selection
        // Stay on OVERVIEW step — corner editing is now inline
        _currentStep.value = WizardStep.OVERVIEW
    }

    /** Exits refinement mode and returns to overview. */
    fun exitRefinement() {
        _refinementBoxIndex.value = -1
        _selectedCorner.value = null
        _currentStep.value = WizardStep.OVERVIEW
    }

    // ========== Box Manipulation ==========

    /** Moves the selected box by the given delta (pushes to undo stack). */
    fun moveSelectedBox(deltaX: Double, deltaY: Double) {
        val index = _selectedBoxIndex.value
        if (index >= 0) {
            val list = _boundingBoxList.value
            val box = list.boxes[index]

            // Save for undo
            _undoRedoManager.push(box.id, box)

            // Move
            val moved = box.move(deltaX, deltaY)
            updateBox(index, moved)
        }
    }

    /** Moves the selected box by the given delta without saving to undo (for drag intermediate). */
    fun moveSelectedBoxWithoutUndo(deltaX: Double, deltaY: Double) {
        val index = _selectedBoxIndex.value
        if (index >= 0) {
            val list = _boundingBoxList.value
            val box = list.boxes[index]
            val moved = box.move(deltaX, deltaY)
            updateBox(index, moved)
        }
    }

    /** Moves a specific corner of the box at the given index (pushes to undo stack). */
    fun moveCorner(boxIndex: Int, corner: Corner, newX: Double, newY: Double) {
        val list = _boundingBoxList.value
        if (boxIndex >= 0 && boxIndex < list.size()) {
            val box = list.boxes[boxIndex]

            // Save for undo
            _undoRedoManager.push(box.id, box)

            // Move corner
            val moved = box.moveCorner(corner, Point(newX, newY))
            updateBox(boxIndex, moved)
        }
    }

    /** Moves a corner without saving to undo (for drag intermediate frames). */
    fun moveCornerWithoutUndo(boxIndex: Int, corner: Corner, newX: Double, newY: Double) {
        val list = _boundingBoxList.value
        if (boxIndex >= 0 && boxIndex < list.size()) {
            val box = list.boxes[boxIndex]
            val moved = box.moveCorner(corner, Point(newX, newY))
            if (!moved.corners.wouldCreateInvalidShape()) {
                updateBox(boxIndex, moved)
            }
        }
    }

    /**
     * Moves a corner with validation to prevent invalid shapes (bowties, self-intersecting).
     * Returns true if the move was applied, false if it was rejected. Pushes to undo stack.
     */
    fun moveCornerWithValidation(
        boxIndex: Int,
        corner: Corner,
        newX: Double,
        newY: Double,
    ): Boolean {
        val list = _boundingBoxList.value
        if (boxIndex < 0 || boxIndex >= list.size()) return false

        val box = list.boxes[boxIndex]
        val moved = box.moveCorner(corner, Point(newX, newY))

        // Check if the resulting shape would be valid (no crossing edges)
        if (moved.corners.wouldCreateInvalidShape()) {
            return false
        }

        // Save for undo
        _undoRedoManager.push(box.id, box)

        // Apply the move
        updateBox(boxIndex, moved)
        return true
    }

    /**
     * Saves the current state of a box to the undo stack. Call this once at the start of a drag
     * operation, then use moveCornerWithoutUndo/moveSelectedBoxWithoutUndo for intermediate frames.
     */
    fun saveBoxUndoSnapshot(boxIndex: Int) {
        val list = _boundingBoxList.value
        if (boxIndex >= 0 && boxIndex < list.size()) {
            val box = list.boxes[boxIndex]
            _undoRedoManager.push(box.id, box)
        }
    }

    /** Moves the selected corner by the given delta (arrow key movement, pushes to undo). */
    fun moveSelectedCorner(deltaX: Double, deltaY: Double) {
        val index = _selectedBoxIndex.value
        val corner = _selectedCorner.value
        if (index >= 0 && corner != null) {
            val list = _boundingBoxList.value
            val box = list.boxes[index]
            val cornerPoint = box.corners.forCorner(corner)

            // Save for undo
            _undoRedoManager.push(box.id, box)

            // Move corner
            val moved =
                box.moveCorner(corner, Point(cornerPoint.x + deltaX, cornerPoint.y + deltaY))
            updateBox(index, moved)
        }
    }

    // ========== Box Scale/Rotate ==========

    /** Expands or contracts the selected box by the given scale factor. Pushes to undo stack. */
    fun expandSelectedBox(scaleFactor: Double) {
        val index = _selectedBoxIndex.value
        if (index >= 0) {
            val list = _boundingBoxList.value
            if (index < list.size()) {
                val box = list.boxes[index]
                _undoRedoManager.push(box.id, box)
                val expanded = box.expand(scaleFactor)
                if (!expanded.corners.wouldCreateInvalidShape()) {
                    updateBox(index, expanded)
                }
            }
        }
    }

    /**
     * Rotates the selected box by the given angle in degrees around its center. Pushes to undo
     * stack.
     */
    fun rotateSelectedBox(angleDegrees: Double) {
        val index = _selectedBoxIndex.value
        if (index >= 0) {
            val list = _boundingBoxList.value
            if (index < list.size()) {
                val box = list.boxes[index]
                _undoRedoManager.push(box.id, box)
                val rotated = box.rotate(angleDegrees)
                updateBox(index, rotated)
            }
        }
    }

    // ========== Navigation ==========

    /** Navigates to the next box. */
    fun nextBox() {
        val current = _refinementBoxIndex.value
        val list = _boundingBoxList.value
        if (list.isNotEmpty()) {
            val nextIndex = (current + 1) % list.size()
            enterRefinement(nextIndex)
        }
    }

    /** Navigates to the previous box. */
    fun previousBox() {
        val current = _refinementBoxIndex.value
        val list = _boundingBoxList.value
        if (list.isNotEmpty()) {
            val prevIndex = if (current <= 0) list.size() - 1 else current - 1
            enterRefinement(prevIndex)
        }
    }

    // ========== Undo/Redo ==========

    /** Undoes the last operation. */
    fun undo() {
        val index = _selectedBoxIndex.value
        if (index >= 0) {
            val list = _boundingBoxList.value
            val box = list.boxes[index]

            val previousState = _undoRedoManager.undo(box, box.id)
            if (previousState != null) {
                updateBox(index, previousState)
            }
        }
        _undoRedoVersion.value++
    }

    /** Redoes the last undone operation. */
    fun redo() {
        val index = _selectedBoxIndex.value
        if (index >= 0) {
            val list = _boundingBoxList.value
            val box = list.boxes[index]

            val restoredState = _undoRedoManager.redo(box, box.id)
            if (restoredState != null) {
                updateBox(index, restoredState)
            }
        }
        _undoRedoVersion.value++
    }

    /** Returns true if undo is available. */
    fun canUndo(): Boolean {
        return _undoRedoManager.totalUndoOperations() > 0
    }

    /** Returns true if redo is available. */
    fun canRedo(): Boolean {
        val index = _selectedBoxIndex.value
        if (index >= 0) {
            val list = _boundingBoxList.value
            val box = list.boxes[index]
            return _undoRedoManager.canRedo(box.id)
        }
        return false
    }

    // ========== Zoom ==========

    /** Updates the zoom controller to fit the current image. */
    fun updateZoomController(viewportWidth: Double = 800.0, viewportHeight: Double = 600.0) {
        val image = _image.value
        if (image != null) {
            _zoomController.value =
                ZoomController.fit(
                    image.width.toDouble(),
                    image.height.toDouble(),
                    viewportWidth,
                    viewportHeight,
                )
        }
    }

    /** Zooms in the view. */
    fun zoomIn(cursorX: Double? = null, cursorY: Double? = null) {
        _zoomController.value = _zoomController.value.zoomIn(cursorX, cursorY)
    }

    /** Zooms out the view. */
    fun zoomOut(cursorX: Double? = null, cursorY: Double? = null) {
        _zoomController.value = _zoomController.value.zoomOut(cursorX, cursorY)
    }

    /** Pans the view by the given delta. */
    fun pan(deltaX: Double, deltaY: Double) {
        _zoomController.value = _zoomController.value.pan(deltaX, deltaY)
    }

    /** Fits the view to the current image. */
    fun fitToView(viewportWidth: Double = 800.0, viewportHeight: Double = 600.0) {
        updateZoomController(viewportWidth, viewportHeight)
    }

    /** Fits the view to the current refinement box. */
    fun fitToBox(viewportWidth: Double = 800.0, viewportHeight: Double = 600.0) {
        val index = _refinementBoxIndex.value
        if (index >= 0 && index < _boundingBoxList.value.size()) {
            val box = _boundingBoxList.value.boxes[index]
            _zoomController.value =
                _zoomController.value.fitToBox(box.corners, viewportWidth, viewportHeight)
        }
    }

    /** Fits the view to the currently selected box (used on overview page). */
    fun fitToSelectedBox(viewportWidth: Double = 800.0, viewportHeight: Double = 600.0) {
        val index = _selectedBoxIndex.value
        if (index >= 0 && index < _boundingBoxList.value.size()) {
            val box = _boundingBoxList.value.boxes[index]
            _zoomController.value =
                _zoomController.value.fitToBox(box.corners, viewportWidth, viewportHeight)
        }
    }

    // ========== Workflow Steps ==========

    /** Goes to the overview step. */
    fun goToOverview() {
        _currentStep.value = WizardStep.OVERVIEW
        exitRefinement()
    }

    /** Goes to the summary step. */
    fun goToSummary() {
        _currentStep.value = WizardStep.SUMMARY
    }

    /** Goes to the metadata editing step. */
    fun goToMetadata() {
        _currentStep.value = WizardStep.METADATA
    }

    /** Goes to the quick edit step (combined rotation + metadata + location). */
    fun goToQuickEdit() {
        _currentStep.value = WizardStep.QUICK_EDIT
    }

    /** Goes to processing step. */
    fun goToProcessing() {
        _currentStep.value = WizardStep.PROCESSING
    }

    /** Goes to complete step. */
    fun goToComplete() {
        _currentStep.value = WizardStep.COMPLETE
    }

    /**
     * Resets the wizard to the import step, clearing all state. Use this when user cancels or
     * completes a scan session.
     */
    fun resetToImportStep() {
        _image.value = null
        _imageFile.value = null
        _singlePhotoMode.value = false
        _boundingBoxList.value = BoundingBoxList.empty()
        _selectedBoxIndex.value = -1
        _refinementBoxIndex.value = -1
        _selectedCorner.value = null
        _fourPointState.value = FourPointState.inactive()
        _wizardMode.value = WizardMode.NORMAL
        _currentStep.value = WizardStep.IMPORT
        _undoRedoManager.clearAll()
        _undoRedoVersion.value++
        _photoConfigurations.value = emptyMap()
        _zoomController.value = ZoomController()
        // Reset metadata selection
        _selectedMetadataIndices.value = emptySet()
        // Reset batch processing state
        _sourceFiles.value = emptyList()
        _currentImageIndex.value = 0
        _preProcessedCache.value = emptyMap()
        _preProcessCount.value = 0
        _preProcessing.value = false
        _sourceExif.value = null
        // Reset back-of-photo skip state
        _skippedBatchIndices.value = emptySet()
    }

    /**
     * Resets per-image state (boxes, selections, configs) while preserving batch/folder state. Use
     * this when moving to the next image in a batch folder import so the user gets a clean canvas
     * while the source file list and current index are maintained.
     */
    fun resetPerImageState() {
        _boundingBoxList.value = BoundingBoxList.empty()
        _selectedBoxIndex.value = -1
        _refinementBoxIndex.value = -1
        _selectedCorner.value = null
        _fourPointState.value = FourPointState.inactive()
        _wizardMode.value = WizardMode.NORMAL
        _undoRedoManager.clearAll()
        _undoRedoVersion.value++
        _photoConfigurations.value = emptyMap()
        _zoomController.value = ZoomController()
        _selectedMetadataIndices.value = emptySet()
        _sourceExif.value = null
    }

    // ========== Utility ==========

    /** Converts screen coordinates to image coordinates. */
    fun screenToImage(screenX: Double, screenY: Double): Point {
        return _zoomController.value.screenToImage(screenX, screenY)
    }

    /** Converts image coordinates to screen coordinates. */
    fun imageToScreen(imageX: Double, imageY: Double): Point {
        return _zoomController.value.imageToScreen(imageX, imageY)
    }

    /** Returns the current box count. */
    fun boxCount(): Int = _boundingBoxList.value.size()

    /** Returns the currently selected box. */
    fun selectedBox(): BoundingBox? {
        val index = _selectedBoxIndex.value
        val list = _boundingBoxList.value
        return if (index >= 0 && index < list.size()) list.boxes[index] else null
    }

    /** Returns the box at refinement index. */
    fun refinementBox(): BoundingBox? {
        val index = _refinementBoxIndex.value
        val list = _boundingBoxList.value
        return if (index >= 0 && index < list.size()) list.boxes[index] else null
    }
}
