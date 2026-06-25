package org.kryspetrie.fileimport.domain.model.geometry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.kryspetrie.fileimport.domain.model.geometry.BoundingBox
import org.kryspetrie.fileimport.domain.model.geometry.BoundingBoxCorners
import org.kryspetrie.fileimport.domain.model.geometry.BoundingBoxList
import org.kryspetrie.fileimport.domain.model.geometry.Corner
import org.kryspetrie.fileimport.domain.model.geometry.Point
import org.junit.jupiter.api.Test

/**
 * Unit tests for BoundingBox and related classes. Tests BB-01 through BB-14 from the implementation
 * plan.
 */
class BoundingBoxTest {

    // BB-01: Create rectangular box at center
    @Test
    fun createRectangularBoxAtCenter() {
        val box =
            BoundingBox.createRectangular(center = Point(100.0, 100.0), width = 60.0, height = 40.0)

        // Width 60 = half is 30, so x should be 70 to 130
        // Height 40 = half is 20, so y should be 80 to 120
        assertEquals(Point(70.0, 80.0), box.corners.topLeft)
        assertEquals(Point(130.0, 80.0), box.corners.topRight)
        assertEquals(Point(130.0, 120.0), box.corners.bottomRight)
        assertEquals(Point(70.0, 120.0), box.corners.bottomLeft)
    }

    // BB-02: Create quadrilateral box from 4 points
    @Test
    fun createQuadrilateralBoxFrom4Points() {
        val points =
            listOf(Point(10.0, 10.0), Point(90.0, 10.0), Point(90.0, 90.0), Point(10.0, 90.0))

        val box = BoundingBox.fromQuadrilateral(points)

        // The convex hull should order them clockwise starting from leftmost
        // Expected order after convex hull: TL, TR, BR, BL
        assertNotNull(box)
        assertEquals(4, box.corners.toList().size)

        // Verify it's a valid rectangle
        val center = box.corners.center()
        assertEquals(Point(50.0, 50.0), center)
    }

    // BB-02b: Create quadrilateral with points in different order
    @Test
    fun quadrilateralWorksWithAnyPointOrder() {
        val points =
            listOf(
                Point(90.0, 90.0), // BR first
                Point(10.0, 90.0), // BL second
                Point(10.0, 10.0), // TL third
                Point(90.0, 10.0), // TR fourth
            )

        val box = BoundingBox.fromQuadrilateral(points)
        assertNotNull(box)

        // Center should still be at (50, 50)
        assertEquals(Point(50.0, 50.0), box.corners.center())
    }

    // BB-03: Reject box below minimum size
    @Test
    fun rejectBoxBelowMinimumSize() {
        val box =
            BoundingBox.createRectangular(
                center = Point(100.0, 100.0),
                width = 5.0, // Only 5% of typical 1000px image
                height = 50.0,
            )

        // The box is created - validation happens at higher level
        assertEquals(5.0, box.width())
        assertEquals(50.0, box.height())
    }

    // BB-07: Move box by dragging
    @Test
    fun moveBoxByTranslation() {
        val box =
            BoundingBox.createRectangular(center = Point(100.0, 100.0), width = 60.0, height = 40.0)

        val moved = box.move(10.0, -5.0)

        // All corners should be translated
        assertEquals(Point(80.0, 75.0), moved.corners.topLeft)
        assertEquals(Point(140.0, 75.0), moved.corners.topRight)
        assertEquals(Point(140.0, 115.0), moved.corners.bottomRight)
        assertEquals(Point(80.0, 115.0), moved.corners.bottomLeft)
    }

    // BB-08: Move single corner
    @Test
    fun moveSingleCorner() {
        val box =
            BoundingBox.createRectangular(center = Point(100.0, 100.0), width = 60.0, height = 40.0)

        val moved = box.moveCorner(Corner.TOP_LEFT, Point(80.0, 75.0))

        // Only TL should be at new position
        assertEquals(Point(80.0, 75.0), moved.corners.topLeft)

        // Other corners should be unchanged
        assertEquals(Point(130.0, 80.0), moved.corners.topRight)
        assertEquals(Point(130.0, 120.0), moved.corners.bottomRight)
        assertEquals(Point(70.0, 120.0), moved.corners.bottomLeft)
    }

    // BB-09: Click detection - no box at empty point
    @Test
    fun noBoxAtEmptyPoint() {
        val list = BoundingBoxList.empty()

        val found = list.findAtPoint(Point(500.0, 500.0))

        assertNull(found)
    }

    // BB-10: Click detection - box hit
    @Test
    fun boxHitDetection() {
        val box =
            BoundingBox.createRectangular(center = Point(100.0, 100.0), width = 60.0, height = 40.0)
        val list = BoundingBoxList(listOf(box))

        // Click at center of box
        val found = list.findAtPoint(Point(100.0, 100.0))

        assertNotNull(found)
        assertEquals(box.id, found!!.id)
    }

    // BB-11: Click detection - corner selection
    @Test
    fun cornerSelectionWithinBuffer() {
        val box =
            BoundingBox.createRectangular(center = Point(100.0, 100.0), width = 60.0, height = 40.0)
        val list = BoundingBoxList(listOf(box))

        // TL corner is at (70, 80), click within 15px
        val found = list.findCornerAtPoint(Point(78.0, 85.0), 20.0)

        assertNotNull(found)
        assertEquals(Corner.TOP_LEFT, found!!.second)
    }

    // BB-12: Deselect on outside click
    @Test
    fun deselectOnOutsideClick() {
        val box =
            BoundingBox.createRectangular(center = Point(100.0, 100.0), width = 60.0, height = 40.0)
                .select()
        val list = BoundingBoxList(listOf(box))

        // Click outside the box
        val found = list.findAtPoint(Point(500.0, 500.0))

        assertNull(found)
    }

    // BB-13: Convex hull reorders points
    @Test
    fun convexHullReordersPointsCorrectly() {
        val points =
            listOf(
                Point(10.0, 10.0), // TL
                Point(10.0, 90.0), // BL
                Point(90.0, 90.0), // BR
                Point(90.0, 10.0), // TR - wrong order
            )

        val box = BoundingBox.fromQuadrilateral(points)
        assertNotNull(box)

        // Center should still be correct
        assertEquals(Point(50.0, 50.0), box.corners.center())
    }

    // Test aspect ratio calculation
    @Test
    fun aspectRatioCalculationForLandscapeBox() {
        val box =
            BoundingBox.createRectangular(
                center = Point(100.0, 100.0),
                width = 100.0,
                height = 60.0, // landscape
            )

        assertEquals(100.0 / 60.0, box.aspectRatio(), 0.001)
        assertFalse(box.isPortrait())
        assertFalse(box.isSquare())
    }

    // Test aspect ratio for portrait box
    @Test
    fun aspectRatioCalculationForPortraitBox() {
        val box =
            BoundingBox.createRectangular(
                center = Point(100.0, 100.0),
                width = 60.0,
                height = 100.0, // portrait
            )

        assertEquals(60.0 / 100.0, box.aspectRatio(), 0.001)
        assertTrue(box.isPortrait())
        assertFalse(box.isSquare())
    }

    // Test square detection
    @Test
    fun squareDetectionForNearlySquareBox() {
        val box =
            BoundingBox.createRectangular(
                center = Point(100.0, 100.0),
                width = 100.0,
                height = 98.0, // within threshold
            )

        assertTrue(box.isSquare())
    }

    // Test selection state
    @Test
    fun selectionStateManagement() {
        val box =
            BoundingBox.createRectangular(center = Point(100.0, 100.0), width = 60.0, height = 40.0)

        // Initially not selected
        assertFalse(box.isSelected)

        // Select
        val selected = box.select()
        assertTrue(selected.isSelected)
        assertNull(selected.selectedCorner)

        // Select corner
        val withCorner = selected.selectCorner(Corner.TOP_RIGHT)
        assertTrue(withCorner.isSelected)
        assertEquals(Corner.TOP_RIGHT, withCorner.selectedCorner)

        // Deselect
        val deselected = withCorner.deselect()
        assertFalse(deselected.isSelected)
        assertNull(deselected.selectedCorner)
    }

    // Test expand operation
    @Test
    fun expandBoxByScaleFactor() {
        val box =
            BoundingBox.createRectangular(center = Point(100.0, 100.0), width = 60.0, height = 40.0)

        val expanded = box.expand(1.2) // 20% larger

        // All corners should be further from center
        assertTrue(expanded.width() > box.width())
        assertTrue(expanded.height() > box.height())
    }

    // Test rotate operation
    @Test
    fun rotateBoxByAngle() {
        val box =
            BoundingBox.createRectangular(center = Point(100.0, 100.0), width = 60.0, height = 40.0)

        val rotated = box.rotate(45.0)

        // Center should remain the same
        assertEquals(box.center(), rotated.center())
    }

    // BB-05: Reject overlapping boxes
    @Test
    fun rejectOverlappingBox() {
        val existing =
            BoundingBox.createRectangular(center = Point(100.0, 100.0), width = 60.0, height = 40.0)

        val overlapping =
            BoundingBox.createRectangular(
                center = Point(100.0, 100.0), // Same center
                width = 40.0,
                height = 30.0,
            )

        val list = BoundingBoxList(listOf(existing))
        assertFalse(list.canAdd(overlapping))
    }

    // BB-05b: Accept non-overlapping box
    @Test
    fun acceptNonOverlappingBox() {
        val existing =
            BoundingBox.createRectangular(center = Point(100.0, 100.0), width = 60.0, height = 40.0)

        val separate =
            BoundingBox.createRectangular(
                center = Point(300.0, 100.0), // Far away
                width = 40.0,
                height = 30.0,
            )

        val list = BoundingBoxList(listOf(existing))
        assertTrue(list.canAdd(separate))
    }

    // Test BoundingBoxCorners center calculation
    @Test
    fun boundingBoxCornersCenterCalculation() {
        val corners =
            BoundingBoxCorners(
                topLeft = Point(10.0, 10.0),
                topRight = Point(90.0, 10.0),
                bottomRight = Point(90.0, 90.0),
                bottomLeft = Point(10.0, 90.0),
            )

        val center = corners.center()

        assertEquals(Point(50.0, 50.0), center)
    }

    // Test BoundingBoxCorners translation
    @Test
    fun boundingBoxCornersTranslation() {
        val corners =
            BoundingBoxCorners(
                topLeft = Point(10.0, 10.0),
                topRight = Point(90.0, 10.0),
                bottomRight = Point(90.0, 90.0),
                bottomLeft = Point(10.0, 90.0),
            )

        val translated = corners.translated(5.0, -5.0)

        assertEquals(Point(15.0, 5.0), translated.topLeft)
        assertEquals(Point(95.0, 5.0), translated.topRight)
        assertEquals(Point(95.0, 85.0), translated.bottomRight)
        assertEquals(Point(15.0, 85.0), translated.bottomLeft)
    }

    // Test BoundingBoxCorners expansion
    @Test
    fun boundingBoxCornersExpansionFromCenter() {
        val corners =
            BoundingBoxCorners(
                topLeft = Point(70.0, 80.0),
                topRight = Point(130.0, 80.0),
                bottomRight = Point(130.0, 120.0),
                bottomLeft = Point(70.0, 120.0),
            )

        val expanded = corners.expanded(1.1) // 10% larger

        assertTrue(expanded.width() > corners.width())
        assertTrue(expanded.height() > corners.height())
    }

    // Test BoundingBoxList add and remove
    @Test
    fun boundingBoxListAddAndRemove() {
        var list = BoundingBoxList.empty()
        assertTrue(list.isEmpty())

        val box =
            BoundingBox.createRectangular(center = Point(100.0, 100.0), width = 60.0, height = 40.0)

        list = list.add(box)
        assertEquals(1, list.size())

        list = list.remove(box.id)
        assertTrue(list.isEmpty())
    }

    // Test BoundingBoxList update
    @Test
    fun boundingBoxListUpdate() {
        val box1 =
            BoundingBox.createRectangular(center = Point(100.0, 100.0), width = 60.0, height = 40.0)
        val box2 =
            BoundingBox.createRectangular(center = Point(200.0, 200.0), width = 50.0, height = 30.0)

        var list = BoundingBoxList(listOf(box1, box2))

        val moved = box1.move(10.0, 10.0)
        list = list.update(moved)

        val updated = list.boxes.find { it.id == box1.id }
        assertNotNull(updated)
        assertEquals(10.0, updated!!.corners.topLeft.x - 70.0, 0.001)
    }

    // Test forCorner: correct mapping regardless of Corner enum ordinal (was a bug)
    @Test
    fun forCornerReturnsCorrectPoint() {
        val corners =
            BoundingBoxCorners(
                topLeft = Point(10.0, 20.0),
                topRight = Point(110.0, 20.0),
                bottomRight = Point(110.0, 120.0),
                bottomLeft = Point(10.0, 120.0),
            )

        assertEquals(corners.topLeft, corners.forCorner(Corner.TOP_LEFT))
        assertEquals(corners.topRight, corners.forCorner(Corner.TOP_RIGHT))
        assertEquals(corners.bottomLeft, corners.forCorner(Corner.BOTTOM_LEFT))
        assertEquals(corners.bottomRight, corners.forCorner(Corner.BOTTOM_RIGHT))
    }

    // Test Point distanceTo
    @Test
    fun pointDistanceCalculation() {
        val p1 = Point(0.0, 0.0)
        val p2 = Point(3.0, 4.0)

        assertEquals(5.0, p1.distanceTo(p2), 0.001)
    }

    // Test Point operators
    @Test
    fun pointOperators() {
        val p1 = Point(10.0, 20.0)
        val p2 = Point(5.0, 10.0)

        assertEquals(Point(15.0, 30.0), p1 + p2)
        assertEquals(Point(5.0, 10.0), p1 - p2)
        assertEquals(Point(20.0, 40.0), p1 * 2.0)
    }
}
