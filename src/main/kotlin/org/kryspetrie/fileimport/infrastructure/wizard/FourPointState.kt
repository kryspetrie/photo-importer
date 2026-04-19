package org.kryspetrie.fileimport.infrastructure.wizard

/**
 * State machine for bounding box creation modes. Supports both:
 * - 2-click rectangle mode (diagonal corners)
 * - 4-point quadrilateral mode
 */
data class FourPointState(
    val points: List<Point> = emptyList(),
    val mode: Mode = Mode.INACTIVE,
    val creationType: CreationType = CreationType.RECTANGLE,
    /** Current mouse position for drawing line preview */
    val pendingPoint: Point? = null
) {
  enum class Mode {
    /** Creation is not active */
    INACTIVE,
    /** Clicking points (1-3 for 4-point, 1 for rectangle) */
    PLACING,
    /** All points placed, awaiting confirmation */
    COMPLETE,
    /** Confirmed */
    CONFIRMED
  }

  enum class CreationType {
    RECTANGLE,  // 2-click diagonal corners
    QUAD        // 4-click quadrilateral
  }

  /** Returns the number of points placed. */
  fun pointCount(): Int = points.size

  /** Returns true if all required points are placed for current creation type. */
  fun isComplete(): Boolean {
    return when (creationType) {
      CreationType.RECTANGLE -> points.size == 2
      CreationType.QUAD -> points.size == 4
    }
  }

  /** Returns true if ready to confirm. */
  fun canConfirm(): Boolean = mode == Mode.COMPLETE && isComplete()

  /** Returns true if creating a rectangle. */
  fun isRectangle(): Boolean = creationType == CreationType.RECTANGLE

  /** Returns the number of points needed for the current creation type. */
  fun pointsNeeded(): Int = if (creationType == CreationType.RECTANGLE) 2 else 4

  /** Activates creation mode. */
  fun activate(type: CreationType = CreationType.RECTANGLE): FourPointState {
    return copy(
        mode = Mode.PLACING, points = emptyList(), creationType = type, pendingPoint = null)
  }

  /** Deactivates creation mode. */
  fun deactivate(): FourPointState {
    return copy(mode = Mode.INACTIVE, points = emptyList(), pendingPoint = null)
  }

  /** Adds a point and returns new state. Does nothing if mode is INACTIVE or COMPLETE. */
  fun addPoint(point: Point): FourPointState {
    if (mode != Mode.PLACING) return this
    if (isComplete()) return this

    val newPoints = points + point
    val newMode = if (isComplete()) Mode.COMPLETE else Mode.PLACING

    return copy(points = newPoints, mode = newMode, pendingPoint = null)
  }

  /** Updates the pending point (mouse position) for line preview. */
  fun updatePendingPoint(point: Point?): FourPointState {
    if (mode != Mode.PLACING || isComplete()) return this
    return copy(pendingPoint = point)
  }

  /** Removes the last placed point. */
  fun removeLastPoint(): FourPointState {
    if (points.isEmpty()) return this

    val newPoints = points.dropLast(1)
    val newMode = if (newPoints.isEmpty()) Mode.INACTIVE else Mode.PLACING

    return copy(points = newPoints, mode = newMode, pendingPoint = null)
  }

  /**
   * Confirms the selection and returns a BoundingBox. Returns null if not all points are placed.
   */
  fun confirm(): BoundingBox? {
    if (!isComplete()) return null

    return when (creationType) {
      CreationType.RECTANGLE -> BoundingBox.fromRectangle(points[0], points[1])
      CreationType.QUAD -> BoundingBox.fromQuadrilateral(points)
    }
  }

  /** Resets the state to initial. */
  fun reset(): FourPointState = copy(mode = Mode.INACTIVE, points = emptyList(), pendingPoint = null)

  /** Returns status message for the current state. */
  fun statusMessage(): String {
    if (mode == Mode.INACTIVE) return ""

    val needed = pointsNeeded()
    val placed = points.size

    return when (mode) {
      Mode.PLACING -> {
        if (isRectangle()) {
          when (placed) {
            0 -> "Click to set first corner"
            1 -> "Click to set opposite corner"
            else -> ""
          }
        } else {
          "Click to set point $placed of $needed"
        }
      }
      Mode.COMPLETE -> "Press Enter to confirm or Escape to cancel"
      Mode.CONFIRMED -> "Box created"
      else -> ""
    }
  }

  companion object {
    /** Creates an inactive state. */
    fun inactive(): FourPointState = FourPointState()

    /** Creates an active rectangle creation state (2-click). */
    fun activeRectangle(): FourPointState = FourPointState(mode = Mode.PLACING, creationType = CreationType.RECTANGLE)

    /** Creates an active quadrilateral creation state (4-click). */
    fun activeQuad(): FourPointState = FourPointState(mode = Mode.PLACING, creationType = CreationType.QUAD)
  }
}
