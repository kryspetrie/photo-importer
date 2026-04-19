package org.kryspetrie.fileimport.infrastructure.wizard

import java.awt.image.BufferedImage
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.kryspetrie.fileimport.domain.model.ImportConfiguration
import org.kryspetrie.fileimport.infrastructure.logging.AppLogger
import org.kryspetrie.fileimport.infrastructure.logging.OperationType

// Debug flag for performance timing - set to true to log timing data
internal const val DEBUG_TIMING = true

/** Wizard mode for the overview/refinement screens. */
enum class WizardMode {
  /** Default mode - select, move, zoom */
  NORMAL,
  /** 4-point bounding box creation mode */
  FOUR_POINT,
  /** Click-to-add rectangular box mode */
  ADD_BOX,
  /** Zoomed refinement of a single box */
  REFINEMENT
}

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

  private val _configuration = MutableStateFlow(ImportConfiguration())
  val configuration: StateFlow<ImportConfiguration> = _configuration.asStateFlow()

  fun setConfiguration(config: ImportConfiguration) {
    _configuration.value = config
  }

  // ========== Image ==========

  private val _image = MutableStateFlow<BufferedImage?>(null)
  val image: StateFlow<BufferedImage?> = _image.asStateFlow()

  private val _imageFile = MutableStateFlow<File?>(null)
  val imageFile: StateFlow<File?> = _imageFile.asStateFlow()

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
   * actual box state updated on release. Called periodically from the LaunchedEffect throttle loop.
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

  private val _undoRedoManager = MutableStateFlow(UndoRedoManager.forBoundingBox())
  val undoRedoManager: StateFlow<UndoRedoManager<BoundingBox>> = _undoRedoManager.asStateFlow()

  // ========== Summary Screen Settings ==========

  private val _photoConfigurations = MutableStateFlow<Map<String, PhotoConfiguration>>(emptyMap())
  val photoConfigurations: StateFlow<Map<String, PhotoConfiguration>> =
      _photoConfigurations.asStateFlow()

  // ========== Workflow ==========

  private val _currentStep = MutableStateFlow(WizardStep.IMPORT)
  val currentStep: StateFlow<WizardStep> = _currentStep.asStateFlow()

  enum class WizardStep {
    IMPORT, // Mode selection
    OVERVIEW, // All boxes visible
    REFINEMENT, // Zoomed single box
    SUMMARY, // Correction options
    PROCESSING, // Export in progress
    COMPLETE // Done
  }

  /** Initializes the wizard with an image file. */
  fun initializeWithImage(image: BufferedImage, file: File) {
    _image.value = image
    _imageFile.value = file

    // Initialize zoom to fit view
    updateZoomController()

    // Clear previous state
    _boundingBoxList.value = BoundingBoxList.empty()
    _selectedBoxIndex.value = -1
    _refinementBoxIndex.value = -1
    _selectedCorner.value = null
    _fourPointState.value = FourPointState.inactive()
    _wizardMode.value = WizardMode.NORMAL
    _currentStep.value = WizardStep.OVERVIEW
    _undoRedoManager.value.clearAll()
  }

  /** Sets whether CV auto-detection is enabled. */
  fun setCvAutoDetectEnabled(enabled: Boolean) {
    _cvAutoDetectEnabled.value = enabled
  }

  /** Sets the detected bounding boxes (from CV). */
  fun setDetectedBoxes(boxes: List<BoundingBox>) {
    _boundingBoxList.value = BoundingBoxList(boxes)
    _selectedBoxIndex.value = -1
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
   *     rotationCorrectionEnabled = true,
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
  fun updatePhotoConfiguration(boxId: String, update: (PhotoConfiguration) -> PhotoConfiguration) {
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
   * // Rotate all boxes 90 degrees clockwise
   * state.rotateAllBoxes(90)
   * ```
   *
   * @param degrees Rotation in degrees. Common values:
   *     - 90: Clockwise 90°
   *     - -90 or -1: Counter-clockwise 90°
   *     - 180 or -180: 180° flip
   *     - 0: Clear rotation
   */
  fun rotateAllBoxes(degrees: Int) {
    boxes.forEach { box -> updatePhotoConfiguration(box.id) { it.copy(rotationDegrees = degrees) } }
  }

  /**
   * Enables or disables perspective correction for all boxes.
   *
   * @param enabled True to enable, false to disable
   */
  /**
   * Enables or disables perspective correction for all bounding boxes.
   *
   * This bulk operation sets the perspective correction flag for all existing boxes. When enabled,
   * photos will be transformed to correct trapezoidal distortion.
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
   * @see rotateAllBoxes
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
   * @see rotateAllBoxes
   * @see setPerspectiveCorrectionAll
   */
  fun clearAllConfigurations() {
    _photoConfigurations.value = emptyMap()
  }

  /** Returns all bounding boxes as a list. */
  val boxes: List<BoundingBox>
    get() = _boundingBoxList.value.boxes

  /** Adds a bounding box. */
  fun addBox(box: BoundingBox) {
    val newList = _boundingBoxList.value.add(box)
    _boundingBoxList.value = newList
    appLogger?.logOperationComplete(
        OperationType.BOX_CREATION,
        "Box ${newList.size()} at (${box.center().x.toInt()}, ${box.center().y.toInt()}), size: ${box.width().toInt()}x${box.height().toInt()}")
  }

  /** Removes a bounding box by index. */
  fun removeBox(index: Int) {
    val list = _boundingBoxList.value
    if (index >= 0 && index < list.size()) {
      val box = list.boxes[index]
      val boxId = box.id
      // Save for undo
      _undoRedoManager.value.push(box.id, box)
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

  /** Selects a corner for arrow key movement. */
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
      minSize: Double = PhotoScanConstants.MIN_BOX_SIZE_PIXELS
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
          "Cannot create box: size $width x $height below minimum $minSize px$imageSizeInfo")
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
    _currentStep.value = WizardStep.REFINEMENT
    // Zoom/pan will be set by the UI once container size is known
  }

  /** Exits refinement mode and returns to overview. */
  fun exitRefinement() {
    _refinementBoxIndex.value = -1
    _selectedCorner.value = null
    _currentStep.value = WizardStep.OVERVIEW

    // Reset zoom to fit image
    updateZoomController()
  }

  // ========== Box Manipulation ==========

  /** Moves the selected box by the given delta. */
  fun moveSelectedBox(deltaX: Double, deltaY: Double) {
    val index = _selectedBoxIndex.value
    if (index >= 0) {
      val list = _boundingBoxList.value
      val box = list.boxes[index]

      // Save for undo
      _undoRedoManager.value.push(box.id, box)

      // Move
      val moved = box.move(deltaX, deltaY)
      updateBox(index, moved)
    }
  }

  /** Moves a specific corner of the box at the given index. */
  fun moveCorner(boxIndex: Int, corner: Corner, newX: Double, newY: Double) {
    val list = _boundingBoxList.value
    if (boxIndex >= 0 && boxIndex < list.size()) {
      val box = list.boxes[boxIndex]

      // Save for undo
      _undoRedoManager.value.push(box.id, box)

      // Move corner
      val moved = box.moveCorner(corner, Point(newX, newY))
      updateBox(boxIndex, moved)
    }
  }

  /**
   * Moves a corner with validation to prevent invalid shapes (bowties, self-intersecting). Returns
   * true if the move was applied, false if it was rejected.
   */
  fun moveCornerWithValidation(boxIndex: Int, corner: Corner, newX: Double, newY: Double): Boolean {
    val list = _boundingBoxList.value
    if (boxIndex < 0 || boxIndex >= list.size()) return false

    val box = list.boxes[boxIndex]
    val moved = box.moveCorner(corner, Point(newX, newY))

    // Check if the resulting shape would be valid (no crossing edges)
    if (moved.corners.wouldCreateInvalidShape()) {
      return false
    }

    // Save for undo
    _undoRedoManager.value.push(box.id, box)

    // Apply the move
    updateBox(boxIndex, moved)
    return true
  }

  /** Moves the selected corner by the given delta (arrow key movement). */
  fun moveSelectedCorner(deltaX: Double, deltaY: Double) {
    val index = _selectedBoxIndex.value
    val corner = _selectedCorner.value
    if (index >= 0 && corner != null) {
      val list = _boundingBoxList.value
      val box = list.boxes[index]
      val cornerPoint = box.corners.toList()[corner.ordinal]

      // Save for undo
      _undoRedoManager.value.push(box.id, box)

      // Move corner
      val moved = box.moveCorner(corner, Point(cornerPoint.x + deltaX, cornerPoint.y + deltaY))
      updateBox(index, moved)
    }
  }

  /** Expands the box at the given index. */
  fun expandBox(boxIndex: Int, scaleFactor: Double) {
    val list = _boundingBoxList.value
    if (boxIndex >= 0 && boxIndex < list.size()) {
      val box = list.boxes[boxIndex]

      // Save for undo
      _undoRedoManager.value.push(box.id, box)

      // Expand
      val expanded = box.expand(scaleFactor)
      updateBox(boxIndex, expanded)
    }
  }

  /** Rotates the box at the given index. */
  fun rotateBox(boxIndex: Int, angleDegrees: Double) {
    val list = _boundingBoxList.value
    if (boxIndex >= 0 && boxIndex < list.size()) {
      val box = list.boxes[boxIndex]

      // Save for undo
      _undoRedoManager.value.push(box.id, box)

      // Rotate
      val rotated = box.rotate(angleDegrees)
      updateBox(boxIndex, rotated)
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

      val previousState = _undoRedoManager.value.undo(box, box.id)
      if (previousState != null) {
        updateBox(index, previousState)
      }
    }
  }

  /** Redoes the last undone operation. */
  fun redo() {
    val index = _selectedBoxIndex.value
    if (index >= 0) {
      val list = _boundingBoxList.value
      val box = list.boxes[index]

      val restoredState = _undoRedoManager.value.redo(box, box.id)
      if (restoredState != null) {
        updateBox(index, restoredState)
      }
    }
  }

  /** Returns true if undo is available. */
  fun canUndo(): Boolean {
    return _undoRedoManager.value.totalUndoOperations() > 0
  }

  /** Returns true if redo is available. */
  fun canRedo(): Boolean {
    val index = _selectedBoxIndex.value
    if (index >= 0) {
      val list = _boundingBoxList.value
      val box = list.boxes[index]
      return _undoRedoManager.value.canRedo(box.id)
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
              image.width.toDouble(), image.height.toDouble(), viewportWidth, viewportHeight)
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
    _boundingBoxList.value = BoundingBoxList.empty()
    _selectedBoxIndex.value = -1
    _refinementBoxIndex.value = -1
    _selectedCorner.value = null
    _fourPointState.value = FourPointState.inactive()
    _wizardMode.value = WizardMode.NORMAL
    _currentStep.value = WizardStep.IMPORT
    _undoRedoManager.value.clearAll()
    _photoConfigurations.value = emptyMap()
    _zoomController.value = ZoomController()
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

/** Configuration for a photo in the summary screen. */
data class PhotoConfiguration(
    val perspectiveCorrectionEnabled: Boolean = false,
    val rotationCorrectionEnabled: Boolean = false,
    val rotationDegrees: Int = 0, // -1 = CCW, 0 = none, 1 = CW
    val aspectRatio: Double = 0.0 // 0 = current, or specific ratio
)
