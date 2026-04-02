package org.kryspetrie.fileimport.domain.model

/**
 * Axis-aligned bounding box for a detection.
 *
 * Used internally by [org.kryspetrie.fileimport.infrastructure.photoscan.YoloOutputParser] to
 * represent bounding boxes from the YOLO model output, for IOU computation during non-maximum
 * suppression.
 *
 * @property x1 Left coordinate
 * @property y1 Top coordinate
 * @property x2 Right coordinate
 * @property y2 Bottom coordinate
 */
data class Rectangle(val x1: Float, val y1: Float, val x2: Float, val y2: Float) {
  /** Area of this rectangle. */
  val area: Float
    get() = (x2 - x1) * (y2 - y1)
}
