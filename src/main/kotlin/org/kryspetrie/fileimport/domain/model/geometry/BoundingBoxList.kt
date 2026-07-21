package org.kryspetrie.fileimport.domain.model.geometry

/** Manages a collection of bounding boxes with intersection detection and hit testing. */
data class BoundingBoxList(val boxes: List<BoundingBox> = emptyList()) {
    fun add(box: BoundingBox): BoundingBoxList =
        if (canAdd(box)) copy(boxes = boxes + box) else this

    fun remove(boxId: String): BoundingBoxList = copy(boxes = boxes.filter { it.id != boxId })

    fun update(box: BoundingBox): BoundingBoxList =
        copy(boxes = boxes.map { if (it.id == box.id) box else it })

    fun updateAt(index: Int, transform: (BoundingBox) -> BoundingBox): BoundingBoxList {
        if (index < 0 || index >= boxes.size) return this
        return copy(boxes = boxes.mapIndexed { i, box -> if (i == index) transform(box) else box })
    }

    fun findAtPoint(point: Point, bufferRadius: Double = 20.0): BoundingBox? =
        boxes.find { isPointInOrNearBox(point, it, bufferRadius) }

    fun findCornerAtPoint(point: Point, bufferRadius: Double = 20.0): Pair<BoundingBox, Corner>? {
        for (box in boxes) {
            for (corner in Corner.entries) {
                if (point.distanceTo(box.corners.forCorner(corner)) <= bufferRadius)
                    return Pair(box, corner)
            }
        }
        return null
    }

    fun indexOfAtPoint(point: Point, bufferRadius: Double = 20.0): Int =
        boxes.indexOfFirst { isPointInOrNearBox(point, it, bufferRadius) }

    fun canAdd(box: BoundingBox): Boolean = boxes.none { boxesIntersect(box, it) }

    fun canAdd(
        box: BoundingBox,
        imageWidth: Double,
        imageHeight: Double,
        minSizePercent: Double = 0.1,
    ): Boolean {
        if (box.width() < imageWidth * minSizePercent) return false
        if (box.height() < imageHeight * minSizePercent) return false
        return canAdd(box)
    }

    fun canAddAt(index: Int, box: BoundingBox): Boolean {
        val otherBoxes = boxes.filterIndexed { i, _ -> i != index }
        return otherBoxes.none { boxesIntersect(box, it) }
    }

    fun selected(): BoundingBox? = boxes.find { it.isSelected }

    fun selectedIndex(): Int = boxes.indexOfFirst { it.isSelected }

    fun deselectAll(): BoundingBoxList = copy(boxes = boxes.map { it.deselect() })

    fun selectAt(index: Int): BoundingBoxList =
        copy(
            boxes = boxes.mapIndexed { i, box -> if (i == index) box.select() else box.deselect() }
        )

    fun selectById(id: String): BoundingBoxList =
        copy(boxes = boxes.map { if (it.id == id) it.select() else it.deselect() })

    fun nextFrom(index: Int): BoundingBox? {
        if (boxes.isEmpty()) return null
        val safeIndex = index.coerceIn(0, boxes.size - 1)
        return boxes[(safeIndex + 1) % boxes.size]
    }

    fun previousFrom(index: Int): BoundingBox? {
        if (boxes.isEmpty()) return null
        val safeIndex = index.coerceIn(0, boxes.size - 1)
        return boxes[if (safeIndex <= 0) boxes.size - 1 else safeIndex - 1]
    }

    fun size(): Int = boxes.size

    fun isEmpty(): Boolean = boxes.isEmpty()

    fun isNotEmpty(): Boolean = boxes.isNotEmpty()

    private fun isPointInOrNearBox(point: Point, box: BoundingBox, bufferRadius: Double): Boolean {
        for (cornerPoint in box.corners.toList()) {
            if (point.distanceTo(cornerPoint) <= bufferRadius) return true
        }
        return isPointInQuadrilateral(point, box.corners)
    }

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

    private fun boxesIntersect(a: BoundingBox, b: BoundingBox): Boolean {
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
