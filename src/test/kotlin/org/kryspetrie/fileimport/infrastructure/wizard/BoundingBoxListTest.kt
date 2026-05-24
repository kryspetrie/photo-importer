package org.kryspetrie.fileimport.infrastructure.wizard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for BoundingBoxList. Tests intersection detection, overlap prevention, and list
 * operations.
 */
class BoundingBoxListTest {

    // BL-01: Create empty list
    @Test
    fun `create empty list`() {
        val list = BoundingBoxList.empty()
        assertTrue(list.isEmpty())
        assertEquals(0, list.size())
    }

    // BL-02: Add box to empty list
    @Test
    fun `add box to empty list`() {
        var list = BoundingBoxList.empty()
        val box = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)

        list = list.add(box)

        assertFalse(list.isEmpty())
        assertEquals(1, list.size())
        assertEquals(box.id, list.boxes[0].id)
    }

    // BL-03: Remove box by ID
    @Test
    fun `remove box by ID`() {
        val box = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
        var list = BoundingBoxList(listOf(box))

        list = list.remove(box.id)

        assertTrue(list.isEmpty())
    }

    // BL-04: Update box
    @Test
    fun `update box`() {
        val box = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
        var list = BoundingBoxList(listOf(box))
        val originalX = box.corners.topLeft.x

        val movedBox = box.move(50.0, 0.0)
        list = list.update(movedBox)

        // Verify the top-left x moved by 50
        assertEquals(originalX + 50.0, list.boxes[0].corners.topLeft.x, 0.01)
    }

    // BL-05: Update at index
    @Test
    fun `update at index`() {
        val box = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
        var list = BoundingBoxList(listOf(box))
        val originalX = list.boxes[0].corners.topLeft.x

        list = list.updateAt(0) { it.move(50.0, 0.0) }

        // Verify the top-left x moved by 50
        assertEquals(originalX + 50.0, list.boxes[0].corners.topLeft.x, 0.01)
    }

    // BL-06: Prevent overlapping boxes - overlapping boxes rejected
    @Test
    fun `overlapping boxes are rejected`() {
        val box1 = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
        var list = BoundingBoxList(listOf(box1))

        // Create overlapping box (centers very close)
        val box2 = BoundingBox.createRectangular(Point(120.0, 110.0), 80.0, 60.0)

        val result = list.canAdd(box2)

        assertFalse(result)
    }

    // BL-07: Non-overlapping boxes are accepted
    @Test
    fun `non-overlapping boxes are accepted`() {
        val box1 = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
        var list = BoundingBoxList(listOf(box1))

        // Create non-overlapping box (far away)
        val box2 = BoundingBox.createRectangular(Point(500.0, 500.0), 100.0, 80.0)

        val result = list.canAdd(box2)

        assertTrue(result)
    }

    // BL-08: Add non-overlapping box
    @Test
    fun `add non-overlapping box`() {
        val box1 = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
        var list = BoundingBoxList(listOf(box1))

        val box2 = BoundingBox.createRectangular(Point(500.0, 500.0), 100.0, 80.0)

        list = list.add(box2)

        assertEquals(2, list.size())
    }

    // BL-09: Add overlapping box is no-op
    @Test
    fun `add overlapping box is no-op`() {
        val box1 = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
        var list = BoundingBoxList(listOf(box1))

        val box2 = BoundingBox.createRectangular(Point(120.0, 110.0), 80.0, 60.0)

        list = list.add(box2)

        assertEquals(1, list.size()) // Still only one box
    }

    // BL-10: Hit detection - box found at center
    @Test
    fun `hit detection finds box at center`() {
        val box = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
        val list = BoundingBoxList(listOf(box))

        val found = list.findAtPoint(Point(100.0, 100.0))

        assertNotNull(found)
        assertEquals(box.id, found!!.id)
    }

    // BL-11: Hit detection - no box at empty point
    @Test
    fun `hit detection finds no box at empty point`() {
        val box = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
        val list = BoundingBoxList(listOf(box))

        val found = list.findAtPoint(Point(500.0, 500.0))

        assertNull(found)
    }

    // BL-12: Hit detection - corner within buffer
    @Test
    fun `hit detection finds corner within buffer`() {
        val box = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
        val list = BoundingBoxList(listOf(box))

        // TL corner is at (50, 60) - click within 20px buffer
        val found = list.findCornerAtPoint(Point(60.0, 70.0), 20.0)

        assertNotNull(found)
        assertEquals(Corner.TOP_LEFT, found!!.second)
    }

    // BL-13: Hit detection - corner outside buffer
    @Test
    fun `hit detection finds no corner outside buffer`() {
        val box = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
        val list = BoundingBoxList(listOf(box))

        // Click far from any corner
        val found = list.findCornerAtPoint(Point(200.0, 200.0), 10.0)

        assertNull(found)
    }

    // BL-14: Index at point
    @Test
    fun `index at point returns correct index`() {
        val box1 = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
        val box2 = BoundingBox.createRectangular(Point(300.0, 100.0), 100.0, 80.0)
        val list = BoundingBoxList(listOf(box1, box2))

        val index = list.indexOfAtPoint(Point(100.0, 100.0))

        assertEquals(0, index)
    }

    // BL-15: Selection - select at index
    @Test
    fun `select at index selects box`() {
        val box1 = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
        val box2 = BoundingBox.createRectangular(Point(300.0, 100.0), 100.0, 80.0)
        var list = BoundingBoxList(listOf(box1, box2))

        list = list.selectAt(0)

        assertTrue(list.boxes[0].isSelected)
        assertFalse(list.boxes[1].isSelected)
    }

    // BL-16: Selection - deselect all
    @Test
    fun `deselect all deselects all boxes`() {
        val box1 = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0).select()
        val box2 = BoundingBox.createRectangular(Point(300.0, 100.0), 100.0, 80.0).select()
        var list = BoundingBoxList(listOf(box1, box2))

        list = list.deselectAll()

        assertFalse(list.boxes[0].isSelected)
        assertFalse(list.boxes[1].isSelected)
    }

    // BL-17: Selection - selected accessor
    @Test
    fun `selected accessor returns selected box`() {
        val box1 = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
        val box2 = BoundingBox.createRectangular(Point(300.0, 100.0), 100.0, 80.0).select()
        val list = BoundingBoxList(listOf(box1, box2))

        val selected = list.selected()

        assertNotNull(selected)
        assertEquals(box2.id, selected!!.id)
    }

    // BL-18: Selection - selected index
    @Test
    fun `selected index returns correct index`() {
        val box1 = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
        val box2 = BoundingBox.createRectangular(Point(300.0, 100.0), 100.0, 80.0).select()
        val list = BoundingBoxList(listOf(box1, box2))

        val index = list.selectedIndex()

        assertEquals(1, index)
    }

    // BL-19: Navigation - next box
    @Test
    fun `next box returns next box`() {
        val box1 = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
        val box2 = BoundingBox.createRectangular(Point(300.0, 100.0), 100.0, 80.0)
        val list = BoundingBoxList(listOf(box1, box2))

        val next = list.nextFrom(0)

        assertEquals(box2.id, next!!.id)
    }

    // BL-20: Navigation - next box wraps
    @Test
    fun `next box wraps around`() {
        val box1 = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
        val box2 = BoundingBox.createRectangular(Point(300.0, 100.0), 100.0, 80.0)
        val list = BoundingBoxList(listOf(box1, box2))

        val next = list.nextFrom(1)

        assertEquals(box1.id, next!!.id) // Wrapped back to first
    }

    // BL-21: Navigation - previous box
    @Test
    fun `previous box returns previous box`() {
        val box1 = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
        val box2 = BoundingBox.createRectangular(Point(300.0, 100.0), 100.0, 80.0)
        val list = BoundingBoxList(listOf(box1, box2))

        val prev = list.previousFrom(1)

        assertEquals(box1.id, prev!!.id)
    }

    // BL-22: Navigation - previous box wraps
    @Test
    fun `previous box wraps around`() {
        val box1 = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
        val box2 = BoundingBox.createRectangular(Point(300.0, 100.0), 100.0, 80.0)
        val list = BoundingBoxList(listOf(box1, box2))

        val prev = list.previousFrom(0)

        assertEquals(box2.id, prev!!.id) // Wrapped to last
    }

    // BL-23: Size check - isEmpty
    @Test
    fun `isEmpty returns true for empty list`() {
        val list = BoundingBoxList.empty()
        assertTrue(list.isEmpty())
        assertFalse(list.isNotEmpty())
    }

    // BL-24: Size check - isNotEmpty
    @Test
    fun `isNotEmpty returns true for non-empty list`() {
        val box = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
        val list = BoundingBoxList(listOf(box))
        assertFalse(list.isEmpty())
        assertTrue(list.isNotEmpty())
    }

    // BL-25: Point in quadrilateral - inside
    @Test
    fun `point inside quadrilateral returns true`() {
        val box = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
        val list = BoundingBoxList(listOf(box))

        // Point at center
        val found = list.findAtPoint(Point(100.0, 100.0))

        assertNotNull(found)
    }

    // BL-26: Point in quadrilateral - on edge
    @Test
    fun `point on edge of quadrilateral returns true`() {
        val box = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
        val list = BoundingBoxList(listOf(box))

        // Point on top edge (between TL and TR)
        val found = list.findAtPoint(Point(100.0, 60.0)) // y = 100 - 40 = 60 (top edge)

        assertNotNull(found)
    }

    // BL-27: Quadrilateral intersection - corner inside
    @Test
    fun `quadrilaterals intersect when corner is inside`() {
        val box1 = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
        var list = BoundingBoxList(listOf(box1))

        // box2's top-left corner is inside box1
        val box2 = BoundingBox.createRectangular(Point(80.0, 80.0), 50.0, 40.0)

        assertFalse(list.canAdd(box2))
    }

    // BL-28: Quadrilateral intersection - no overlap
    @Test
    fun `quadrilaterals don't intersect when separate`() {
        val box1 = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
        var list = BoundingBoxList(listOf(box1))

        // box2 is completely to the right
        val box2 = BoundingBox.createRectangular(Point(300.0, 100.0), 100.0, 80.0)

        assertTrue(list.canAdd(box2))
    }

    // BL-29: Can add at index (for validation before update)
    @Test
    fun `can add at index validates correctly`() {
        val box1 = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
        var list = BoundingBoxList(listOf(box1))

        // Position that would overlap with existing box
        val box2 = BoundingBox.createRectangular(Point(120.0, 110.0), 80.0, 60.0)

        // Can't add at index 0 (it's the existing box)
        assertTrue(list.canAddAt(0, box2)) // No other boxes to check
    }

    // BL-30: Select by ID
    @Test
    fun `select by ID selects correct box`() {
        val box1 = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
        val box2 = BoundingBox.createRectangular(Point(300.0, 100.0), 100.0, 80.0)
        var list = BoundingBoxList(listOf(box1, box2))

        list = list.selectById(box2.id)

        assertFalse(list.boxes[0].isSelected)
        assertTrue(list.boxes[1].isSelected)
    }

    // BL-31: Update with non-existent ID is no-op
    @Test
    fun `update with non-existent ID is no-op`() {
        val box = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
        var list = BoundingBoxList(listOf(box))

        list = list.update(box.copy(id = "non-existent-id"))

        assertEquals(1, list.size())
        assertEquals(box.id, list.boxes[0].id)
    }

    // BL-32: Update at invalid index is no-op
    @Test
    fun `update at invalid index is no-op`() {
        val box = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
        var list = BoundingBoxList(listOf(box))
        val originalX = list.boxes[0].corners.topLeft.x

        list = list.updateAt(5) { it.move(50.0, 0.0) }

        assertEquals(originalX, list.boxes[0].corners.topLeft.x, 0.01) // Unchanged
    }

    // BL-33: Remove from empty list is no-op
    @Test
    fun `remove from empty list is no-op`() {
        var list = BoundingBoxList.empty()

        list = list.remove("any-id")

        assertTrue(list.isEmpty())
    }

    // BL-34: Multiple boxes hit detection - first found
    @Test
    fun `hit detection returns first matching box`() {
        val box1 = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
        val box2 = BoundingBox.createRectangular(Point(150.0, 150.0), 100.0, 80.0) // Overlapping
        val list = BoundingBoxList(listOf(box1, box2))

        // Point that could be in either box
        val found = list.findAtPoint(Point(120.0, 120.0))

        assertNotNull(found)
    }

    // BL-35: canAdd with minimum size check
    @Test
    fun `canAdd respects minimum size`() {
        var list = BoundingBoxList.empty()

        // Box too small relative to image dimensions
        val tinyBox = BoundingBox.createRectangular(Point(100.0, 100.0), 10.0, 10.0)

        assertFalse(list.canAdd(tinyBox, 1000.0, 800.0, 0.1)) // 10% minimum
    }
}
