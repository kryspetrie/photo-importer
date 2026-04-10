package org.kryspetrie.fileimport.infrastructure.wizard

/**
 * State machine for 4-point bounding box creation mode. Manages the workflow of clicking 4 points
 * to define a quadrilateral.
 */
data class FourPointState(val points: List<Point> = emptyList(), val mode: Mode = Mode.INACTIVE) {
  enum class Mode {
    /** 4-point selection is not active */
    INACTIVE,
    /** 4-point selection is in progress (1-3 points placed) */
    PLACING,
    /** 4 points placed, awaiting confirmation */
    COMPLETE,
    /** 4 points placed and confirmed */
    CONFIRMED
  }

  /** Returns the number of points placed. */
  fun pointCount(): Int = points.size

  /** Returns true if 4 points have been placed. */
  fun isComplete(): Boolean = points.size == 4

  /** Returns true if 4 points are placed and mode is COMPLETE. */
  fun canConfirm(): Boolean = mode == Mode.COMPLETE && isComplete()

  /** Activates 4-point mode. */
  fun activate(): FourPointState {
    return copy(mode = Mode.PLACING, points = emptyList())
  }

  /** Deactivates 4-point mode. */
  fun deactivate(): FourPointState {
    return copy(mode = Mode.INACTIVE, points = emptyList())
  }

  /** Adds a point and returns new state. Does nothing if mode is INACTIVE or COMPLETE. */
  fun addPoint(point: Point): FourPointState {
    if (mode != Mode.PLACING) return this
    if (points.size >= 4) return this

    val newPoints = points + point
    val newMode = if (newPoints.size == 4) Mode.COMPLETE else Mode.PLACING

    return copy(points = newPoints, mode = newMode)
  }

  /** Removes the last placed point. Returns to PLACING mode if was COMPLETE. */
  fun removeLastPoint(): FourPointState {
    if (points.isEmpty()) return this

    val newPoints = points.dropLast(1)
    val newMode = if (newPoints.isEmpty()) Mode.INACTIVE else Mode.PLACING

    return copy(points = newPoints, mode = newMode)
  }

  /**
   * Confirms the selection and returns a BoundingBox. Returns null if not all 4 points are placed.
   */
  fun confirm(): BoundingBox? {
    if (!isComplete()) return null
    return BoundingBox.fromQuadrilateral(points)
  }

  /** Resets the state to initial (same as deactivate). */
  fun reset(): FourPointState {
    return copy(mode = Mode.INACTIVE, points = emptyList())
  }

  /** Returns the next point number to place (1-4). */
  fun nextPointNumber(): Int = points.size + 1

  /** Returns status message for the current state. */
  fun statusMessage(): String {
    return when (mode) {
      Mode.INACTIVE -> ""
      Mode.PLACING -> "Click to set point ${points.size + 1} of 4"
      Mode.COMPLETE -> "Press Enter to confirm or Escape to cancel"
      Mode.CONFIRMED -> "Box created"
    }
  }

  companion object {
    /** Creates an inactive state. */
    fun inactive(): FourPointState = FourPointState()

    /** Creates an active state ready for point placement. */
    fun active(): FourPointState = FourPointState(mode = Mode.PLACING)
  }
}
