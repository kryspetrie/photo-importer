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

    // ========== Sub-states (independent, no constructor dependencies) ==========

    /** Navigation state (wizard step transitions). Delegated sub-state. */
    val navigation = WizardNavigationState()

    /** Batch/folder image processing state. Delegated sub-state. */
    val batch = ImageBatchState()

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
    private val _defaultCorrectionStrategy = MutableStateFlow(CorrectionStrategy.PERSPECTIVE)
    val defaultCorrectionStrategy: StateFlow<CorrectionStrategy> =
        _defaultCorrectionStrategy.asStateFlow()

    fun setDefaultCorrectionStrategy(strategy: CorrectionStrategy) {
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

    // ========== Batch Processing (delegated to ImageBatchState) ==========

    /** Source image files for batch processing. Delegates to [ImageBatchState]. */
    val sourceFiles: StateFlow<List<File>> = batch.sourceFiles

    /** Index of the currently displayed batch image. Delegates to [ImageBatchState]. */
    val currentImageIndex: StateFlow<Int> = batch.currentImageIndex

    /** Cache of pre-processed images. Delegates to [ImageBatchState]. */
    val preProcessedCache: StateFlow<Map<Int, PreProcessedImage>> = batch.preProcessedCache

    /** Count of pre-processed images. Delegates to [ImageBatchState]. */
    val preProcessCount: StateFlow<Int> = batch.preProcessCount

    /** Whether batch pre-processing is running. Delegates to [ImageBatchState]. */
    val preProcessing: StateFlow<Boolean> = batch.preProcessing

    /** Indices of skipped batch files. Delegates to [ImageBatchState]. */
    val skippedBatchIndices: StateFlow<Set<Int>> = batch.skippedBatchIndices

    /** True when in batch mode. Delegates to [ImageBatchState]. */
    val isBatchMode: Boolean get() = batch.isBatchMode

    /** Total number of batch images. Delegates to [ImageBatchState]. */
    val batchTotal: Int get() = batch.batchTotal

    /** Initializes batch mode. Delegates to [ImageBatchState]. */
    fun initializeBatch(files: List<File>) = batch.initializeBatch(files)

    /** Stores a pre-processed result. Delegates to [ImageBatchState]. */
    fun putPreProcessed(index: Int, result: PreProcessedImage) = batch.putPreProcessed(index, result)

    /** Marks batch pre-processing as started/finished. Delegates to [ImageBatchState]. */
    fun setPreProcessing(active: Boolean) = batch.setPreProcessing(active)

    /** Switches to a pre-processed image at the given index. Returns false if not cached yet. */
    fun switchToImage(index: Int): Boolean {
        val cached = batch.getCachedImage(index) ?: return false

        batch.setCurrentImageIndex(index)
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

    /** Navigates to the next image in the batch. Returns false if can't. */
    fun nextImage(): Boolean = switchToImage(batch.currentImageIndex.value + 1)

    /** Navigates to the previous image in the batch. Returns false if can't. */
    fun prevImage(): Boolean = switchToImage(batch.currentImageIndex.value - 1)

    /** True when more batch images remain. Delegates to [ImageBatchState]. */
    val hasMoreBatchImages: Boolean get() = batch.hasMoreBatchImages

    /** Advances batch index, returns next file or null. Delegates to [ImageBatchState]. */
    fun advanceToNextBatchFile(): File? = batch.advanceToNextBatchFile()

    /** Peeks at next batch file. Delegates to [ImageBatchState]. */
    fun peekNextBatchFile(): File? = batch.peekNextBatchFile()

    /** Skips next batch file. Delegates to [ImageBatchState]. */
    fun skipNextBatchFile(): File? = batch.skipNextBatchFile()

    /** True if next batch image is ready. Delegates to [ImageBatchState]. */
    val isNextImageReady: Boolean get() = batch.isNextImageReady

    /** True if previous batch image is ready. Delegates to [ImageBatchState]. */
    val isPrevImageReady: Boolean get() = batch.isPrevImageReady

    /** Marks a batch file index as skipped. Delegates to [ImageBatchState]. */
    fun markBatchIndexSkipped(index: Int) = batch.markBatchIndexSkipped(index)

    /** Removes a batch file index from skipped set. Delegates to [ImageBatchState]. */
    fun unmarkBatchIndexSkipped(index: Int) = batch.unmarkBatchIndexSkipped(index)

    /** Source files marked as skipped (backs of photos). Delegates to [ImageBatchState]. */
    val skippedBatchFiles: Set<File> get() = batch.skippedBatchFiles

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

    /** Face region state (selection mode, face region CRUD). Delegated sub-state. */
    val faceRegions = FaceRegionState(_photoConfigurations, _boundingBoxList)

    /** Photo configuration state (per-photo configs, metadata selection, bulk ops). Delegated sub-state. */
    val configs = PhotoConfigurationState(_photoConfigurations, _boundingBoxList)

    // ========== Metadata Selection (delegated to PhotoConfigurationState) ==========

    /** Indices of selected metadata photos. Delegates to [PhotoConfigurationState]. */
    val selectedMetadataIndices: StateFlow<Set<Int>> get() = configs.selectedMetadataIndices

    /** Toggles metadata selection. Delegates to [PhotoConfigurationState]. */
    fun toggleMetadataSelection(index: Int) = configs.toggleMetadataSelection(index)

    /** Selects a single metadata photo. Delegates to [PhotoConfigurationState]. */
    fun selectSingleMetadata(index: Int) = configs.selectSingleMetadata(index)

    /** Selects all metadata photos. Delegates to [PhotoConfigurationState]. */
    fun selectAllMetadata() = configs.selectAllMetadata()

    /** Deselects all metadata photos. Delegates to [PhotoConfigurationState]. */
    fun deselectAllMetadata() = configs.deselectAllMetadata()

    /** Applies metadata to selected photos. Delegates to [PhotoConfigurationState]. */
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
    ) = configs.applyMetadataToSelected(
        description = description,
        keywords = keywords,
        originalDate = originalDate,
        year = year,
        cameraModel = cameraModel,
        cameraMake = cameraMake,
        lensModel = lensModel,
        focalLength = focalLength,
        aperture = aperture,
        shutterSpeed = shutterSpeed,
        iso = iso,
        locationName = locationName,
        city = city,
        state = state,
        country = country,
        gpsLatitude = gpsLatitude,
        gpsLongitude = gpsLongitude,
        subjects = subjects,
    )

    /** Applies metadata from a MetadataEditState. Adapts UI type to primitive parameters. */
    fun applyMetadataToSelected(
        editState: org.kryspetrie.fileimport.ui.screens.wizard.metadata.MetadataEditState
    ) = configs.applyMetadataToSelected(
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
    )    // ========== Face Selection (delegated to FaceRegionState) ==========

    /** Whether face selection mode is active. Delegates to [FaceRegionState]. */
    val faceSelectMode: StateFlow<Boolean> get() = faceRegions.faceSelectMode

    /** Index of the photo currently in face-select mode. Delegates to [FaceRegionState]. */
    val faceSelectPhotoIndex: StateFlow<Int?> get() = faceRegions.faceSelectPhotoIndex

    /** Enters face selection mode for a given photo index. Delegates to [FaceRegionState]. */
    fun enterFaceSelectMode(photoIndex: Int) = faceRegions.enterFaceSelectMode(photoIndex)

    /** Exits face selection mode. Delegates to [FaceRegionState]. */
    fun exitFaceSelectMode() = faceRegions.exitFaceSelectMode()

    /** Adds a face region. Delegates to [FaceRegionState.addFaceRegion]. */
    fun addFaceRegion(
        photoIndex: Int,
        name: String,
        x: Double,
        y: Double,
        type: RegionType = RegionType.FACE,
        size: FaceSize = FaceSize.DEFAULT,
    ) = faceRegions.addFaceRegion(photoIndex, name, x, y, type, size)

    /** Removes a face region. Delegates to [FaceRegionState.removeFaceRegion]. */
    fun removeFaceRegion(photoIndex: Int, faceIndex: Int) =
        faceRegions.removeFaceRegion(photoIndex, faceIndex)

    /** Clears all face regions for a photo. Delegates to [FaceRegionState.clearAllFaceRegions]. */
    fun clearAllFaceRegions(photoIndex: Int) = faceRegions.clearAllFaceRegions(photoIndex)

    /** Adds detected face regions. Delegates to [FaceRegionState.addDetectedFaceRegions]. */
    fun addDetectedFaceRegions(photoIndex: Int, regions: List<FaceRegion>) =
        faceRegions.addDetectedFaceRegions(photoIndex, regions)

    /** Updates a face region's name. Delegates to [FaceRegionState.updateFaceRegionName]. */
    fun updateFaceRegionName(photoIndex: Int, faceIndex: Int, name: String) =
        faceRegions.updateFaceRegionName(photoIndex, faceIndex, name)

    /** Updates a face region's position. Delegates to [FaceRegionState.updateFaceRegion]. */
    fun updateFaceRegion(photoIndex: Int, faceIndex: Int, x: Double? = null, y: Double? = null) =
        faceRegions.updateFaceRegion(photoIndex, faceIndex, x, y)

    /** Resizes a face region. Delegates to [FaceRegionState.resizeFaceRegion]. */
    fun resizeFaceRegion(photoIndex: Int, faceIndex: Int, size: FaceSize) =
        faceRegions.resizeFaceRegion(photoIndex, faceIndex, size)

    /** Moves a face region by offset. Delegates to [FaceRegionState.moveFaceRegion]. */
    fun moveFaceRegion(photoIndex: Int, faceIndex: Int, dx: Double, dy: Double) =
        faceRegions.moveFaceRegion(photoIndex, faceIndex, dx, dy)

    // ========== Workflow ==========

    /** Current wizard step. Delegates to [WizardNavigationState]. */
    val currentStep: StateFlow<WizardStep> = navigation.currentStep

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
        navigation.step.value = WizardStep.OVERVIEW
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
        navigation.step.value = WizardStep.EDIT
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

    // ========== Photo Configuration (delegated to PhotoConfigurationState) ==========

    /** Sets photo configuration for a box. Delegates to [PhotoConfigurationState]. */
    fun setPhotoConfiguration(boxId: String, config: PhotoConfiguration) =
        configs.setPhotoConfiguration(boxId, config)

    /** Updates photo configuration for a box. Delegates to [PhotoConfigurationState]. */
    fun updatePhotoConfiguration(
        boxId: String,
        update: (PhotoConfiguration) -> PhotoConfiguration,
    ) = configs.updatePhotoConfiguration(boxId, update)

    /** Clears photo configuration for a box. Delegates to [PhotoConfigurationState]. */
    fun clearPhotoConfiguration(boxId: String) = configs.clearPhotoConfiguration(boxId)

    /** Rotates all boxes CW. Delegates to [PhotoConfigurationState]. */
    fun rotateAllBoxesCW() = configs.rotateAllBoxesCW()

    /** Rotates all boxes CCW. Delegates to [PhotoConfigurationState]. */
    fun rotateAllBoxesCCW() = configs.rotateAllBoxesCCW()

    /** Sets perspective correction for all boxes. Delegates to [PhotoConfigurationState]. */
    fun setPerspectiveCorrectionAll(enabled: Boolean) =
        configs.setPerspectiveCorrectionAll(enabled)

    /** Clears all configurations. Delegates to [PhotoConfigurationState]. */
    fun clearAllConfigurations() = configs.clearAllConfigurations()

    /** Returns all bounding boxes as a list. */
    val boxes: List<BoundingBox>
        get() = configs.boxes

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
        navigation.step.value = WizardStep.OVERVIEW
    }

    /** Exits refinement mode and returns to overview. */
    fun exitRefinement() {
        _refinementBoxIndex.value = -1
        _selectedCorner.value = null
        navigation.step.value = WizardStep.OVERVIEW
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

    /** Goes to the overview step. Delegates to [WizardNavigationState]. */
    fun goToOverview() {
        navigation.goToOverview()
        exitRefinement()
    }

    /** Goes to the summary step. Delegates to [WizardNavigationState]. */
    fun goToSummary() = navigation.goToSummary()

    /** Goes to the edit step. Delegates to [WizardNavigationState]. */
    fun goToEdit() = navigation.goToEdit()

    /** Goes to processing step. Delegates to [WizardNavigationState]. */
    fun goToProcessing() = navigation.goToProcessing()

    /** Goes to complete step. Delegates to [WizardNavigationState]. */
    fun goToComplete() = navigation.goToComplete()

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
        navigation.step.value = WizardStep.IMPORT
        _undoRedoManager.clearAll()
        _undoRedoVersion.value++
        _photoConfigurations.value = emptyMap()
        _zoomController.value = ZoomController()
        // Reset metadata selection
        configs.deselectAllMetadata()
        // Reset batch processing state
        batch.reset()
        _sourceExif.value = null
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
        configs.deselectAllMetadata()
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
