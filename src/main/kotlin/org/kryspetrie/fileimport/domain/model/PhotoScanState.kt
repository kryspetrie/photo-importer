package org.kryspetrie.fileimport.domain.model

import java.awt.image.BufferedImage
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow

/** Corner position for editing. */
enum class CornerType {
  TOP_LEFT,
  TOP_RIGHT,
  BOTTOM_LEFT,
  BOTTOM_RIGHT,
  CENTER  // For dragging entire bounding box
}

/**
 * State for the photo scan workflow.
 *
 * Manages:
 * - Current position in the scan queue
 * - Detected photos for the current image
 * - UI state (corner selection, metadata editing)
 * - Export progress
 *
 * Performance optimization: Uses mutable internal state for efficient updates during
 * dragging operations, and only creates immutable snapshots for observers.
 */
class PhotoScanState {

  /** Current step in the photo scan workflow. */
  enum class Step {
    DETECTING,
    CORNER_EDITING,
    METADATA_EDITING,
    EXPORTING,
    COMPLETE,
    SKIPPED
  }

  // ========== Internal Mutable State (efficient for frequent updates) ==========

  /** Internal mutable list of scanned images - updated efficiently during editing */
  private val _images = MutableStateFlow<List<ScannedImage>>(emptyList())

  /** Internal mutable current index */
  private val _currentIndex = MutableStateFlow(0)

  /** Internal mutable selected photo ID */
  private val _selectedPhotoId = MutableStateFlow<String?>(null)

  /** Internal mutable selected corner for dragging */
  private val _selectedCorner = MutableStateFlow<CornerType?>(null)

  /** Internal mutable workflow step */
  private val _step = MutableStateFlow(Step.DETECTING)

  /** Internal mutable export progress */
  private val _exportProgress = MutableStateFlow(ExportProgress())

  /** Internal mutable processed count */
  private val _processedCount = MutableStateFlow(0)

  /** Internal mutable extracted count */
  private val _extractedCount = MutableStateFlow(0)

  /** Internal mutable errors */
  private val _errors = MutableStateFlow<List<String>>(emptyList())

  /** Target number of photos to detect (1-4) */
  private val _targetPhotoCount = MutableStateFlow<Int?>(null)

  // ========== Derived State (for Compose observation) ==========

  /** Derived photos list that updates when images or index changes */
  private val _currentPhotos = MutableStateFlow<List<DetectedPhoto>>(emptyList())

  // ========== Public Immutable Accessors ==========

  /** List of scanned images to process */
  val images: MutableStateFlow<List<ScannedImage>> get() = _images

  /** Current image index */
  val currentIndex: MutableStateFlow<Int> get() = _currentIndex

  /** Current workflow step */
  val step: MutableStateFlow<Step> get() = _step

  /** Currently selected photo ID for editing */
  val selectedPhotoId: MutableStateFlow<String?> get() = _selectedPhotoId

  /** Currently selected corner for dragging */
  val selectedCorner: MutableStateFlow<CornerType?> get() = _selectedCorner

  /** Export progress */
  val exportProgress: MutableStateFlow<ExportProgress> get() = _exportProgress

  /** Total images processed count */
  val processedCount: MutableStateFlow<Int> get() = _processedCount

  /** Total photos extracted count */
  val extractedCount: MutableStateFlow<Int> get() = _extractedCount

  /** Errors encountered during processing */
  val errors: MutableStateFlow<List<String>> get() = _errors

  /** Target number of photos to detect (1-4, null for auto) */
  val targetPhotoCount: MutableStateFlow<Int?> get() = _targetPhotoCount

  /** Current scanned image being processed. */
  val currentImage: ScannedImage?
    get() = _images.value.getOrNull(_currentIndex.value)

  /** Detected photos for the current image - as StateFlow for Compose. */
  val currentPhotos: MutableStateFlow<List<DetectedPhoto>> get() = _currentPhotos

  /** Number of images remaining. */
  val remainingCount: Int
    get() = _images.value.size - _currentIndex.value - 1

  /** Progress percentage through the image queue. */
  val progressPercent: Float
    get() = if (_images.value.isEmpty()) 0f else (_currentIndex.value.toFloat() / _images.value.size)

  /** Initializes the state with a list of image files to scan. */
  fun initialize(imageFiles: List<File>) {
    _images.value = imageFiles.map { ScannedImage(file = it) }
    _currentIndex.value = 0
    _step.value = Step.DETECTING
    _processedCount.value = 0
    _extractedCount.value = 0
    _errors.value = emptyList()
    _selectedPhotoId.value = null
    _selectedCorner.value = null
    _currentPhotos.value = emptyList()
  }

  /** Updates the current image with detected photos and loaded image. */
  fun setCurrentImageDetected(image: BufferedImage, photos: List<DetectedPhoto>) {
    val current = currentImage ?: return
    val newImages = _images.value.toMutableList().apply {
      this[_currentIndex.value] = current.copy(image = image, detectedPhotos = photos)
    }
    _images.value = newImages
    _currentPhotos.value = photos
    _step.value = Step.CORNER_EDITING
  }

  /** Updates a detected photo's configuration. */
  fun updatePhotoConfiguration(photoId: String, config: PhotoScanConfiguration) {
    val currentIdx = _currentIndex.value
    val currentPhotos = _images.value.getOrNull(currentIdx)?.detectedPhotos ?: return

    val updatedPhotos =
        currentPhotos.map { photo ->
          if (photo.id == photoId) photo.copy(configuration = config) else photo
        }

    updateCurrentPhotos(updatedPhotos)
  }

  /** Updates a detected photo's corner position. */
  fun updatePhotoCorner(photoId: String, corner: CornerType, x: Float, y: Float) {
    val currentIdx = _currentIndex.value
    val currentPhotos = _images.value.getOrNull(currentIdx)?.detectedPhotos ?: return

    val updatedPhotos =
        currentPhotos.map { photo ->
          if (photo.id == photoId) {
            when (corner) {
              CornerType.TOP_LEFT -> photo.copy(topLeft = PhotoCorner(x, y))
              CornerType.TOP_RIGHT -> photo.copy(topRight = PhotoCorner(x, y))
              CornerType.BOTTOM_LEFT -> photo.copy(bottomLeft = PhotoCorner(x, y))
              CornerType.BOTTOM_RIGHT -> photo.copy(bottomRight = PhotoCorner(x, y))
              CornerType.CENTER -> photo  // Handled separately
            }
          } else photo
        }

    updateCurrentPhotos(updatedPhotos)
  }

  /** Moves an entire detected photo by delta (for center-drag). */
  fun movePhoto(photoId: String, deltaX: Float, deltaY: Float) {
    val currentPhotos = _currentPhotos.value
    val photo = currentPhotos.find { it.id == photoId } ?: return

    val newTopLeft = PhotoCorner(photo.topLeft.x + deltaX, photo.topLeft.y + deltaY)
    val newTopRight = PhotoCorner(photo.topRight.x + deltaX, photo.topRight.y + deltaY)
    val newBottomLeft = PhotoCorner(photo.bottomLeft.x + deltaX, photo.bottomLeft.y + deltaY)
    val newBottomRight = PhotoCorner(photo.bottomRight.x + deltaX, photo.bottomRight.y + deltaY)

    val movedPhoto = photo.copy(
        topLeft = newTopLeft,
        topRight = newTopRight,
        bottomLeft = newBottomLeft,
        bottomRight = newBottomRight
    )

    val updatedPhotos = currentPhotos.map { if (it.id == photoId) movedPhoto else it }
    updateCurrentPhotos(updatedPhotos)
  }

  /** Toggles perspective correction for a detected photo. */
  fun togglePerspectiveCorrection(photoId: String, enabled: Boolean) {
    val currentPhotos = _currentPhotos.value.map { photo ->
      if (photo.id == photoId) photo.withPerspectiveCorrection(enabled) else photo
    }
    updateCurrentPhotos(currentPhotos)
  }

  /** Rotates a detected photo clockwise. */
  fun rotatePhotoCW(photoId: String) {
    val currentPhotos = _currentPhotos.value.map { photo ->
      if (photo.id == photoId) photo.rotateCW() else photo
    }
    updateCurrentPhotos(currentPhotos)
  }

  /** Rotates a detected photo counter-clockwise. */
  fun rotatePhotoCCW(photoId: String) {
    val currentPhotos = _currentPhotos.value.map { photo ->
      if (photo.id == photoId) photo.rotateCCW() else photo
    }
    updateCurrentPhotos(currentPhotos)
  }

  /** Helper to update current photos in both the images list and the derived state. */
  private fun updateCurrentPhotos(photos: List<DetectedPhoto>) {
    val currentIdx = _currentIndex.value
    val current = _images.value.getOrNull(currentIdx) ?: return

    val newImages = _images.value.toMutableList().apply {
      this[currentIdx] = current.copy(detectedPhotos = photos)
    }
    _images.value = newImages
    _currentPhotos.value = photos
  }

  /** Adds a new detected photo. */
  fun addPhoto(photo: DetectedPhoto) {
    val updatedPhotos = _currentPhotos.value + photo
    updateCurrentPhotos(updatedPhotos)
    _selectedPhotoId.value = photo.id
  }

  /** Removes a detected photo. */
  fun removePhoto(photoId: String) {
    val updatedPhotos = _currentPhotos.value.filter { it.id != photoId }
    updateCurrentPhotos(updatedPhotos)
    if (_selectedPhotoId.value == photoId) {
      _selectedPhotoId.value = null
    }
  }

  /** Selects a photo for editing. */
  fun selectPhoto(photoId: String?) {
    _selectedPhotoId.value = photoId
  }

  /** Selects a corner for dragging. */
  fun selectCorner(corner: CornerType?) {
    _selectedCorner.value = corner
  }

  /** Moves to the next image in the queue. */
  fun nextImage() {
    _processedCount.value += _currentPhotos.value.size
    _extractedCount.value += _currentPhotos.value.size

    if (_currentIndex.value < _images.value.size - 1) {
      _currentIndex.value++
      _step.value = Step.DETECTING
      _selectedPhotoId.value = null
      _selectedCorner.value = null
      _currentPhotos.value = emptyList()
    } else {
      _step.value = Step.COMPLETE
    }
  }

  /** Skips the current image. */
  fun skipImage() {
    _processedCount.value++

    if (_currentIndex.value < _images.value.size - 1) {
      _currentIndex.value++
      _step.value = Step.DETECTING
      _selectedPhotoId.value = null
      _selectedCorner.value = null
      _currentPhotos.value = emptyList()
    } else {
      _step.value = Step.COMPLETE
    }
  }

  /** Moves to metadata editing step. */
  fun proceedToMetadata() {
    _step.value = Step.METADATA_EDITING
    if (_selectedPhotoId.value == null) {
      _selectedPhotoId.value = _currentPhotos.value.firstOrNull()?.id
    }
  }

  /** Moves to export step. */
  fun proceedToExport() {
    _step.value = Step.EXPORTING
  }

  /** Updates export progress. */
  fun updateExportProgress(progress: ExportProgress) {
    _exportProgress.value = progress
  }

  /** Adds an error message. */
  fun addError(message: String) {
    _errors.value = _errors.value + message
  }

  /** Sets the target number of photos to detect. */
  fun setTargetPhotoCount(count: Int?) {
    _targetPhotoCount.value = count?.coerceIn(1, 20)
  }

  /** Increments the target photo count (max 20). */
  fun incrementTargetPhotoCount() {
    val current = _targetPhotoCount.value ?: 1
    _targetPhotoCount.value = (current + 1).coerceAtMost(20)
  }

  /** Decrements the target photo count (min 1). */
  fun decrementTargetPhotoCount() {
    val current = _targetPhotoCount.value ?: 2
    _targetPhotoCount.value = (current - 1).coerceAtLeast(1)
  }

  /** Resets the state for a new scan session. */
  fun reset() {
    _images.value = emptyList()
    _currentIndex.value = 0
    _step.value = Step.DETECTING
    _selectedPhotoId.value = null
    _selectedCorner.value = null
    _exportProgress.value = ExportProgress()
    _processedCount.value = 0
    _extractedCount.value = 0
    _errors.value = emptyList()
    _currentPhotos.value = emptyList()
    _targetPhotoCount.value = null
  }
}

/** Represents a scanned image with its detected photos. */
data class ScannedImage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val file: File,
    val image: BufferedImage? = null,
    val detectedPhotos: List<DetectedPhoto> = emptyList()
)

/** Export progress information. */
data class ExportProgress(
    val totalPhotos: Int = 0,
    val processedPhotos: Int = 0,
    val currentFile: String = "",
    val status: String = "Ready"
) {
  val percent: Float
    get() = if (totalPhotos == 0) 0f else processedPhotos.toFloat() / totalPhotos
}
