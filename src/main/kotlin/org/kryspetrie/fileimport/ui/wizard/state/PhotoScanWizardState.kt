package org.kryspetrie.fileimport.ui.wizard.state

import java.awt.image.BufferedImage
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.domain.model.geometry.BoundingBox
import org.kryspetrie.fileimport.domain.model.geometry.BoundingBoxList
import org.kryspetrie.fileimport.domain.model.geometry.Point
import org.kryspetrie.fileimport.infrastructure.logging.AppLogger

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
        boxes.appLogger = logger
    }

    // ========== Sub-states ==========

    /** Navigation state (wizard step transitions). */
    val navigation = WizardNavigationState()

    /** Batch/folder image processing state. */
    val batch = ImageBatchState()

    /** Import settings state (CV auto-detect, single photo mode, configuration). */
    val importSettings = ImportSettingsState()

    /** Export settings state (perspective correction, margin, default strategy). */
    val exportSettings = ExportSettingsState()

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

    /** Switches to a pre-processed image at the given index. Returns false if not cached yet. */
    fun switchToImage(index: Int): Boolean {
        val cached = batch.getCachedImage(index) ?: return false

        batch.setCurrentImageIndex(index)
        _image.value = cached.image
        _imageFile.value = cached.file
        boxes.clearBoxes()
        if (cached.boxes.isNotEmpty()) {
            boxes.setDetectedBoxes(cached.boxes)
        }
        boxes.clearUndoAndSelection()
        _fourPointState.value = FourPointState.inactive()
        _wizardMode.value = WizardMode.NORMAL
        _photoConfigurations.value = emptyMap()
        updateZoomController()
        return true
    }

    /** Navigates to the next image in the batch. Returns false if can't. */
    fun nextImage(): Boolean = switchToImage(batch.currentImageIndex.value + 1)

    /** Navigates to the previous image in the batch. Returns false if can't. */
    fun prevImage(): Boolean = switchToImage(batch.currentImageIndex.value - 1)

    // ========== Mode and State ==========

    private val _wizardMode = MutableStateFlow(WizardMode.NORMAL)
    val wizardMode: StateFlow<WizardMode> = _wizardMode.asStateFlow()

    private val _fourPointState = MutableStateFlow(FourPointState.inactive())
    val fourPointState: StateFlow<FourPointState> = _fourPointState.asStateFlow()

    // ========== Bounding Boxes & Selection ==========

    /**
     * Shared mutable box list — also used by [PhotoScanConfigurationState] and [FaceRegionState].
     */
    private val _boundingBoxList = MutableStateFlow(BoundingBoxList.empty())
    val boundingBoxList: StateFlow<BoundingBoxList> = _boundingBoxList.asStateFlow()

    // ========== Undo/Redo (shared with BoxInteractionState) ==========

    private val _undoRedoVersion = MutableStateFlow(0)
    val undoRedoVersion: StateFlow<Int> = _undoRedoVersion.asStateFlow()

    private val _undoRedoManager = UndoRedoManager.forBoundingBox()

    /** Box interaction state (selection, CRUD, manipulation, undo/redo, drag throttle). */
    val boxes =
        BoxInteractionState(_boundingBoxList, _undoRedoManager, _undoRedoVersion).also {
            it.appLogger = appLogger
        }

    // ========== Zoom ==========

    /** Zoom state (view transform, pan, coordinate conversion). */
    val zoom = ZoomState()

    // ========== Summary Screen Settings ==========

    private val _photoConfigurations =
        MutableStateFlow<Map<String, PhotoScanConfiguration>>(emptyMap())
    val photoConfigurations: StateFlow<Map<String, PhotoScanConfiguration>> =
        _photoConfigurations.asStateFlow()

    /** Face region state (selection mode, face region CRUD). */
    val faceRegions = FaceRegionState(_photoConfigurations, _boundingBoxList)

    /** Photo configuration state (per-photo configs, metadata selection, bulk ops). */
    val configs = PhotoScanConfigurationState(_photoConfigurations, _boundingBoxList)

    /**
     * Last-used back image source path. Persists across photo edits within a session so that
     * subsequent crops from the same scan default to the same back file.
     */
    private val _lastBackImageSourcePath = MutableStateFlow<String?>(null)
    val lastBackImageSourcePath: StateFlow<String?> = _lastBackImageSourcePath.asStateFlow()

    /** Records a back image source path for use as default in subsequent back selections. */
    fun setLastBackImageSourcePath(path: String?) {
        _lastBackImageSourcePath.value = path
    }

    // ========== Workflow ==========

    /** Initializes the wizard with an image file. */
    fun initializeWithImage(image: BufferedImage, file: File) {
        _image.value = image
        _imageFile.value = file

        // Zoom/pan will be set by the UI via fitToView when the container size is known

        // Clear previous state
        boxes.clearBoxes()
        boxes.clearUndoAndSelection()
        _fourPointState.value = FourPointState.inactive()
        _wizardMode.value = WizardMode.NORMAL
        navigation.step.value = WizardStep.OVERVIEW
    }

    /**
     * Initializes single photo mode: loads the image, creates a single bounding box covering the
     * entire image, and jumps directly to the Quick Edit step (skipping overview/detection).
     */
    fun initializeSinglePhoto(image: BufferedImage, file: File) {
        _image.value = image
        _imageFile.value = file
        importSettings.setSinglePhotoMode(true)

        // Create a single bounding box covering the entire image
        val w = image.width.toDouble()
        val h = image.height.toDouble()
        val fullBox =
            BoundingBox.createRectangular(center = Point(w / 2, h / 2), width = w, height = h)
        val boxList = listOf(fullBox.select())
        boxes.setDetectedBoxes(boxList)
        _fourPointState.value = FourPointState.inactive()
        _wizardMode.value = WizardMode.NORMAL
        navigation.step.value = WizardStep.EDIT
        boxes.setSelectedBoxIndex(0)
        boxes.setRefinementBoxIndex(-1)
        boxes.deselectCorner()
    }

    /** Sets the detected bounding boxes (from YOLO pipeline). */
    fun setDetectedBoxes(detectedBoxes: List<BoundingBox>) {
        // Sort in reading order: left-to-right within rows, top-to-bottom across rows.
        // Group by vertical position (within 20% of image height = same row), then sort by x.
        val imageHeight = _image.value?.height?.toDouble() ?: 1.0
        val sorted =
            detectedBoxes.sortedWith(
                compareBy<BoundingBox> { box ->
                        // Round y to nearest 20% of image height to group same-row items
                        ((box.corners.center().y / imageHeight) * 5).toInt()
                    }
                    .thenBy { box ->
                        // Within same row, sort by x
                        box.corners.center().x
                    }
            )
        boxes.setDetectedBoxes(sorted)
    }

    /**
     * Sets detected bounding boxes with per-box configurations (detection mode, correction
     * strategy).
     *
     * Boxes are sorted in reading order, and configs are re-associated by index after sorting.
     */
    fun setDetectedBoxes(
        detectedBoxes: List<BoundingBox>,
        configs: List<PhotoScanConfiguration> = emptyList(),
    ) {
        // Sort in reading order, tracking original indices to re-associate configs
        val imageHeight = _image.value?.height?.toDouble() ?: 1.0
        val indexed = detectedBoxes.mapIndexed { index, box -> index to box }
        val sorted =
            indexed.sortedWith(
                compareBy<Pair<Int, BoundingBox>> { (_, box) ->
                        ((box.corners.center().y / imageHeight) * 5).toInt()
                    }
                    .thenBy { (_, box) -> box.corners.center().x }
            )
        boxes.setDetectedBoxes(sorted.map { (_, box) -> box })

        // Apply per-box configurations after sort (re-associated by tracked index)
        if (configs.isNotEmpty()) {
            val configMap = mutableMapOf<String, PhotoScanConfiguration>()
            sorted.forEach { (originalIndex, box) ->
                if (originalIndex < configs.size) {
                    configMap[box.id] = configs[originalIndex]
                }
            }
            _photoConfigurations.value = _photoConfigurations.value + configMap
        }
    }

    /** Removes the currently selected bounding box. */
    fun removeSelectedBox() {
        val index = boxes.selectedBoxIndex.value
        if (index >= 0) {
            boxes.removeBox(index)
        }
    }

    // ========== Mode Management ==========

    /** Enters 4-point mode for creating a quadrilateral bounding box. */
    fun enterFourPointMode() {
        _fourPointState.value = FourPointState.activeQuad()
        _wizardMode.value = WizardMode.FOUR_POINT
        boxes.deselectAll()
    }

    /** Enters 2-click rectangle mode for creating a rectangular bounding box. */
    fun enterRectangleMode() {
        _fourPointState.value = FourPointState.activeRectangle()
        _wizardMode.value = WizardMode.FOUR_POINT
        boxes.deselectAll()
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
                boxes.addBox(box.select())
                boxes.setSelectedBoxIndex(boxes.lastBoxIndex)
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
                boxes.addBox(box.select())
                boxes.setSelectedBoxIndex(boxes.lastBoxIndex)
            }
            exitFourPointMode()
        }
    }

    /** Enters add box mode. */
    fun enterAddBoxMode() {
        _wizardMode.value = WizardMode.ADD_BOX
        boxes.deselectAll()
    }

    /** Exits add box mode. */
    fun exitAddBoxMode() {
        if (_wizardMode.value == WizardMode.ADD_BOX) {
            _wizardMode.value = WizardMode.NORMAL
        }
    }

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
        boxes.addBox(box.select())
        boxes.setSelectedBoxIndex(boxes.lastBoxIndex)

        exitAddBoxMode()
        return true
    }

    /** Enters refinement mode for the box at the given index. */
    fun enterRefinement(boxIndex: Int) {
        boxes.setRefinementBoxIndex(boxIndex)
        boxes.setSelectedBoxIndex(boxIndex)
        boxes.deselectCorner()
        // Stay on OVERVIEW step — corner editing is now inline
        navigation.step.value = WizardStep.OVERVIEW
    }

    /** Exits refinement mode and returns to overview. */
    fun exitRefinement() {
        boxes.setRefinementBoxIndex(-1)
        boxes.deselectCorner()
        navigation.step.value = WizardStep.OVERVIEW
    }

    // ========== Navigation ==========

    /** Navigates to the next box. */
    fun nextBox() {
        val current = boxes.refinementBoxIndex.value
        val count = boxes.boxCount()
        if (count > 0) {
            val nextIndex = (current + 1) % count
            enterRefinement(nextIndex)
        }
    }

    /** Navigates to the previous box. */
    fun previousBox() {
        val current = boxes.refinementBoxIndex.value
        val count = boxes.boxCount()
        if (count > 0) {
            val prevIndex = if (current <= 0) count - 1 else current - 1
            enterRefinement(prevIndex)
        }
    }

    // ========== Zoom ==========

    /** Updates the zoom controller to fit the current image. */
    fun updateZoomController(viewportWidth: Double = 800.0, viewportHeight: Double = 600.0) {
        val image = _image.value
        if (image != null) {
            zoom.fitToImage(
                image.width.toDouble(),
                image.height.toDouble(),
                viewportWidth,
                viewportHeight,
            )
        }
    }

    /** Fits the view to the current image. */
    fun fitToView(viewportWidth: Double = 800.0, viewportHeight: Double = 600.0) {
        updateZoomController(viewportWidth, viewportHeight)
    }

    /** Fits the view to the current refinement box. */
    fun fitToBox(viewportWidth: Double = 800.0, viewportHeight: Double = 600.0) {
        val box = boxes.refinementBox()
        if (box != null) {
            zoom.fitToBox(box.corners, viewportWidth, viewportHeight)
        }
    }

    /** Fits the view to the currently selected box (used on overview page). */
    fun fitToSelectedBox(viewportWidth: Double = 800.0, viewportHeight: Double = 600.0) {
        val box = boxes.selectedBox()
        if (box != null) {
            zoom.fitToBox(box.corners, viewportWidth, viewportHeight)
        }
    }

    // ========== Workflow Steps ==========

    /** Goes to the overview step, also exits refinement. */
    fun goToOverview() {
        navigation.goToOverview()
        exitRefinement()
    }

    /**
     * Resets the wizard to the import step, clearing all state. Use this when user cancels or
     * completes a scan session.
     */
    fun resetToImportStep() {
        _image.value = null
        _imageFile.value = null
        importSettings.setSinglePhotoMode(false)
        boxes.clearBoxes()
        boxes.clearUndoAndSelection()
        _fourPointState.value = FourPointState.inactive()
        _wizardMode.value = WizardMode.NORMAL
        navigation.step.value = WizardStep.IMPORT
        _photoConfigurations.value = emptyMap()
        zoom.reset()
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
        boxes.clearBoxes()
        boxes.clearUndoAndSelection()
        _fourPointState.value = FourPointState.inactive()
        _wizardMode.value = WizardMode.NORMAL
        _photoConfigurations.value = emptyMap()
        zoom.reset()
        configs.deselectAllMetadata()
        _sourceExif.value = null
    }

    // ========== Utility ==========

}
