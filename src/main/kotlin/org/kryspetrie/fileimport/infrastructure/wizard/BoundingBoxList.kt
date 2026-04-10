package org.kryspetrie.fileimport.infrastructure.wizard

/** Manages a collection of bounding boxes with intersection detection and hit testing. */
data class BoundingBoxList(val boxes: List<BoundingBox> = emptyList()) {
  /**
   * Returns a new list with the given box added. Does not add if the box would overlap with
   * existing boxes.
   */
  fun add(box: BoundingBox): BoundingBoxList {
    return if (canAdd(box)) {
      copy(boxes = boxes + box)
    } else {
      this
    }
  }

  /** Returns a new list with the box having the given ID removed. */
  fun remove(boxId: String): BoundingBoxList {
    return copy(boxes = boxes.filter { it.id != boxId })
  }

  /** Returns a new list with the given box updated. */
  fun update(box: BoundingBox): BoundingBoxList {
    return copy(boxes = boxes.map { if (it.id == box.id) box else it })
  }

  /** Returns a new list with the box at the given index updated by the transform function. */
  fun updateAt(index: Int, transform: (BoundingBox) -> BoundingBox): BoundingBoxList {
    if (index < 0 || index >= boxes.size) return this
    return copy(boxes = boxes.mapIndexed { i, box -> if (i == index) transform(box) else box })
  }

  /**
   * Finds the box at the given point (within the hit buffer radius). Returns null if no box is hit.
   */
  fun findAtPoint(point: Point, bufferRadius: Double = 20.0): BoundingBox? {
    return boxes.find { box -> isPointInOrNearBox(point, box, bufferRadius) }
  }

  /**
   * Finds the corner of any box at the given point (within the hit buffer radius). Returns the box
   * and corner if found, null otherwise.
   */
  fun findCornerAtPoint(point: Point, bufferRadius: Double = 20.0): Pair<BoundingBox, Corner>? {
    for (box in boxes) {
      for (corner in Corner.entries) {
        val cornerPoint = box.corners.toList()[corner.ordinal]
        if (point.distanceTo(cornerPoint) <= bufferRadius) {
          return Pair(box, corner)
        }
      }
    }
    return null
  }

  /** Returns the index of the box at the given point. */
  fun indexOfAtPoint(point: Point, bufferRadius: Double = 20.0): Int {
    return boxes.indexOfFirst { box -> isPointInOrNearBox(point, box, bufferRadius) }
  }

  /** Returns true if the box can be added without intersecting existing boxes. */
  fun canAdd(box: BoundingBox): Boolean {
    return boxes.none { existing -> boxesIntersect(box, existing) }
  }

  /**
   * Returns true if the box can be added without being too small. minSizePercent is the minimum
   * size as a percentage of image dimensions.
   */
  fun canAdd(
      box: BoundingBox,
      imageWidth: Double,
      imageHeight: Double,
      minSizePercent: Double = 0.1
  ): Boolean {
    if (box.width() < imageWidth * minSizePercent) return false
    if (box.height() < imageHeight * minSizePercent) return false
    return canAdd(box)
  }

  /** Returns true if the box at index can be added without intersecting other boxes. */
  fun canAddAt(index: Int, box: BoundingBox): Boolean {
    val otherBoxes = boxes.filterIndexed { i, _ -> i != index }
    return otherBoxes.none { existing -> boxesIntersect(box, existing) }
  }

  /** Returns the selected box, if any. */
  fun selected(): BoundingBox? {
    return boxes.find { it.isSelected }
  }

  /** Returns the index of the selected box. */
  fun selectedIndex(): Int {
    return boxes.indexOfFirst { it.isSelected }
  }

  /** Returns a new list with all boxes deselected. */
  fun deselectAll(): BoundingBoxList {
    return copy(boxes = boxes.map { it.deselect() })
  }

  /** Returns a new list with the box at the given index selected. */
  fun selectAt(index: Int): BoundingBoxList {
    return copy(
        boxes = boxes.mapIndexed { i, box -> if (i == index) box.select() else box.deselect() })
  }

  /** Returns a new list with the box having the given ID selected. */
  fun selectById(id: String): BoundingBoxList {
    return copy(boxes = boxes.map { box -> if (box.id == id) box.select() else box.deselect() })
  }

  /** Returns the next box in the list (wrapping around). */
  fun nextFrom(index: Int): BoundingBox? {
    if (boxes.isEmpty()) return null
    val nextIndex = (index + 1) % boxes.size
    return boxes[nextIndex]
  }

  /** Returns the previous box in the list (wrapping around). */
  fun previousFrom(index: Int): BoundingBox? {
    if (boxes.isEmpty()) return null
    val prevIndex = if (index <= 0) boxes.size - 1 else index - 1
    return boxes[prevIndex]
  }

  /** Returns the number of boxes in the list. */
  fun size(): Int = boxes.size

  /** Returns true if the list is empty. */
  fun isEmpty(): Boolean = boxes.isEmpty()

  /** Returns true if the list is not empty. */
  fun isNotEmpty(): Boolean = boxes.isNotEmpty()

  /** Checks if a point is inside or near a box (within buffer radius). */
  private fun isPointInOrNearBox(point: Point, box: BoundingBox, bufferRadius: Double): Boolean {
    // First check if point is near any corner (for corner selection)
    for (cornerPoint in box.corners.toList()) {
      if (point.distanceTo(cornerPoint) <= bufferRadius) {
        return true
      }
    }
    // Then check if point is inside the box
    return isPointInQuadrilateral(point, box.corners)
  }

  /** Checks if a point is inside a quadrilateral using ray casting algorithm. */
  private fun isPointInQuadrilateral(point: Point, corners: BoundingBoxCorners): Boolean {
    val points = corners.toList()
    var inside = false
    var j = points.size - 1

    for (i in points.indices) {
      val xi = points[i].x
      val yi = points[i].y
      val xj = points[j].x
      val yj = points[j].y

      val intersect =
          ((yi > point.y) != (yj > point.y)) &&
              (point.x < (xj - xi) * (point.y - yi) / (yj - yi) + xi)

      if (intersect) inside = !inside
      j = i
    }

    return inside
  }

  /** Checks if two bounding boxes intersect. */
  private fun boxesIntersect(a: BoundingBox, b: BoundingBox): Boolean {
    // Simple check: if corners of one are inside the other
    for (corner in a.corners.toList()) {
      if (isPointInQuadrilateral(corner, b.corners)) return true
    }
    for (corner in b.corners.toList()) {
      if (isPointInQuadrilateral(corner, a.corners)) return true
    }
    return false
  }

  companion object {
    fun empty(): BoundingBoxList = BoundingBoxList(emptyList())
  }
}
