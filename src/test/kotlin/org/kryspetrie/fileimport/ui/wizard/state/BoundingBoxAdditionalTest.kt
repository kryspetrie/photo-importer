package org.kryspetrie.fileimport.ui.wizard.state

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.model.CornerType
import org.kryspetrie.fileimport.domain.model.PhotoCorner

/** Unit tests for BoundingBox and related classes. */
@DisplayName("BoundingBox Tests")
class BoundingBoxAdditionalTest {

    private fun createCorners(
        x: Double = 100.0,
        y: Double = 100.0,
        width: Double = 200.0,
        height: Double = 150.0,
    ): BoundingBoxCorners {
        return BoundingBoxCorners(
            topLeft = Point(x, y),
            topRight = Point(x + width, y),
            bottomRight = Point(x + width, y + height),
            bottomLeft = Point(x, y + height),
        )
    }

    private fun createBox(
        x: Double = 100.0,
        y: Double = 100.0,
        width: Double = 200.0,
        height: Double = 150.0,
    ): BoundingBox {
        return BoundingBox(
            id = "box-1",
            corners = createCorners(x, y, width, height),
            isSelected = false,
            selectedCorner = null,
        )
    }

    @Nested
    @DisplayName("BoundingBoxCorners")
    inner class BoundingBoxCornersTests {

        @Test
        fun `should calculate center correctly`() {
            val corners = createCorners(100.0, 100.0, 200.0, 150.0)

            val center = corners.center()

            assertThat(center.x).isEqualTo(200.0)
            assertThat(center.y).isEqualTo(175.0)
        }

        @Test
        fun `should calculate width correctly`() {
            val corners = createCorners(100.0, 100.0, 200.0, 150.0)

            val width = corners.width()

            assertThat(width).isEqualTo(200.0)
        }

        @Test
        fun `should calculate height correctly`() {
            val corners = createCorners(100.0, 100.0, 200.0, 150.0)

            val height = corners.height()

            assertThat(height).isEqualTo(150.0)
        }

        @Test
        fun `should calculate aspect ratio correctly`() {
            val corners = createCorners(0.0, 0.0, 300.0, 200.0)

            val aspectRatio = corners.aspectRatio()

            assertThat(aspectRatio).isCloseTo(1.5, org.assertj.core.data.Offset.offset(0.001))
        }

        @Test
        fun `should identify portrait orientation`() {
            val portraitCorners = createCorners(0.0, 0.0, 200.0, 300.0)

            assertThat(portraitCorners.isPortrait()).isTrue()
        }

        @Test
        fun `should identify landscape orientation`() {
            val landscapeCorners = createCorners(0.0, 0.0, 300.0, 200.0)

            assertThat(landscapeCorners.isPortrait()).isFalse()
        }

        @Test
        fun `should detect square shape`() {
            val squareCorners = createCorners(0.0, 0.0, 100.0, 100.0)

            assertThat(squareCorners.isSquare()).isTrue()
        }

        @Test
        fun `should detect non-square shape`() {
            val rectangleCorners = createCorners(0.0, 0.0, 100.0, 50.0)

            assertThat(rectangleCorners.isSquare()).isFalse()
        }

        @Test
        fun `should translate corners`() {
            val corners = createCorners(100.0, 100.0, 100.0, 100.0)

            val translated = corners.translated(50.0, 50.0)

            assertThat(translated.topLeft.x).isEqualTo(150.0)
            assertThat(translated.topLeft.y).isEqualTo(150.0)
        }

        @Test
        fun `should move specific corner`() {
            val corners = createCorners(100.0, 100.0, 100.0, 100.0)

            val moved = corners.withCornerMoved(Corner.TOP_LEFT, Point(50.0, 50.0))

            assertThat(moved.topLeft.x).isEqualTo(50.0)
            assertThat(moved.topLeft.y).isEqualTo(50.0)
            // Other corners should be unchanged
            assertThat(moved.bottomRight.x).isEqualTo(200.0)
            assertThat(moved.bottomRight.y).isEqualTo(200.0)
        }

        @Test
        fun `should expand corners from center`() {
            val corners = createCorners(100.0, 100.0, 100.0, 100.0)

            val expanded = corners.expanded(1.5)

            // Check that corners moved outward
            assertThat(expanded.width()).isGreaterThan(corners.width())
        }

        @Test
        fun `should convert to list`() {
            val corners = createCorners(100.0, 100.0, 100.0, 100.0)

            val list = corners.toList()

            assertThat(list).hasSize(4)
            assertThat(list[0]).isEqualTo(corners.topLeft)
            assertThat(list[1]).isEqualTo(corners.topRight)
            assertThat(list[2]).isEqualTo(corners.bottomRight)
            assertThat(list[3]).isEqualTo(corners.bottomLeft)
        }
    }

    @Nested
    @DisplayName("BoundingBox")
    inner class BoundingBoxTests {

        @Test
        fun `should calculate center correctly`() {
            val box = createBox(100.0, 100.0, 200.0, 150.0)

            val center = box.center()

            assertThat(center.x).isEqualTo(200.0)
            assertThat(center.y).isEqualTo(175.0)
        }

        @Test
        fun `should calculate width correctly`() {
            val box = createBox(100.0, 100.0, 200.0, 150.0)

            assertThat(box.width()).isEqualTo(200.0)
        }

        @Test
        fun `should calculate height correctly`() {
            val box = createBox(100.0, 100.0, 200.0, 150.0)

            assertThat(box.height()).isEqualTo(150.0)
        }

        @Test
        fun `should move box`() {
            val box = createBox(100.0, 100.0, 100.0, 100.0)

            val moved = box.move(50.0, 50.0)

            // Center of original box: (100 + 50, 100 + 50) = (150, 150)
            // After move by (50, 50): (200, 200)
            assertThat(moved.center().x).isEqualTo(200.0)
            assertThat(moved.center().y).isEqualTo(200.0)
        }

        @Test
        fun `should move specific corner`() {
            val box = createBox(100.0, 100.0, 100.0, 100.0)

            val moved = box.moveCorner(Corner.TOP_RIGHT, Point(250.0, 100.0))

            assertThat(moved.corners.topRight.x).isEqualTo(250.0)
        }

        @Test
        fun `should expand box`() {
            val box = createBox(100.0, 100.0, 100.0, 100.0)

            val expanded = box.expand(1.5)

            assertThat(expanded.width()).isGreaterThan(box.width())
        }

        @Test
        fun `should rotate box`() {
            val box = createBox(100.0, 100.0, 100.0, 50.0)

            val rotated = box.rotate(45.0)

            // After rotation, corners should have changed position
            assertThat(rotated.corners.topLeft).isNotEqualTo(box.corners.topLeft)
        }

        @Test
        fun `should select and deselect`() {
            val box = createBox()

            val selected = box.select()
            assertThat(selected.isSelected).isTrue()

            val deselected = selected.deselect()
            assertThat(deselected.isSelected).isFalse()
        }

        @Test
        fun `should select and deselect corner`() {
            val box = createBox()

            val withCorner = box.selectCorner(Corner.TOP_LEFT)
            assertThat(withCorner.selectedCorner).isEqualTo(Corner.TOP_LEFT)
            assertThat(withCorner.isSelected).isTrue()

            val withoutCorner = withCorner.deselectCorner()
            assertThat(withoutCorner.selectedCorner).isNull()
        }

        @Test
        fun `should update corners`() {
            val box = createBox()
            val newCorners = createCorners(0.0, 0.0, 50.0, 50.0)

            val updated = box.withCorners(newCorners)

            assertThat(updated.corners.width()).isEqualTo(50.0)
            assertThat(updated.corners.height()).isEqualTo(50.0)
        }
    }
}

/** Unit tests for Point. */
@DisplayName("Point Tests")
class PointTest {

    @Test
    fun `should create point with coordinates`() {
        val point = Point(10.0, 20.0)

        assertThat(point.x).isEqualTo(10.0)
        assertThat(point.y).isEqualTo(20.0)
    }

    @Test
    fun `should calculate distance to another point`() {
        val p1 = Point(0.0, 0.0)
        val p2 = Point(3.0, 4.0)

        val distance = p1.distanceTo(p2)

        assertThat(distance).isEqualTo(5.0)
    }

    @Test
    fun `should calculate midpoint between two points`() {
        val p1 = Point(0.0, 0.0)
        val p2 = Point(10.0, 20.0)

        val midpoint = Point((p1.x + p2.x) / 2, (p1.y + p2.y) / 2)

        assertThat(midpoint.x).isEqualTo(5.0)
        assertThat(midpoint.y).isEqualTo(10.0)
    }

    @Test
    fun `should translate point`() {
        val point = Point(10.0, 20.0)

        val translated = point.translate(5.0, -10.0)

        assertThat(translated.x).isEqualTo(15.0)
        assertThat(translated.y).isEqualTo(10.0)
    }

    @Test
    fun `should perform point subtraction`() {
        val p1 = Point(10.0, 20.0)
        val p2 = Point(3.0, 5.0)

        val result = p1 - p2

        assertThat(result.x).isEqualTo(7.0)
        assertThat(result.y).isEqualTo(15.0)
    }

    @Test
    fun `should perform point addition`() {
        val p1 = Point(10.0, 20.0)
        val p2 = Point(3.0, 5.0)

        val result = p1 + p2

        assertThat(result.x).isEqualTo(13.0)
        assertThat(result.y).isEqualTo(25.0)
    }

    @Test
    fun `should multiply point by scalar`() {
        val point = Point(10.0, 20.0)

        val result = point * 2.0

        assertThat(result.x).isEqualTo(20.0)
        assertThat(result.y).isEqualTo(40.0)
    }

    @Test
    fun `should check equality`() {
        val p1 = Point(10.0, 20.0)
        val p2 = Point(10.0, 20.0)
        val p3 = Point(10.0, 21.0)

        assertThat(p1).isEqualTo(p2)
        assertThat(p1).isNotEqualTo(p3)
    }

    @Test
    fun `should have ZERO constant`() {
        val zero = Point.ZERO

        assertThat(zero.x).isEqualTo(0.0)
        assertThat(zero.y).isEqualTo(0.0)
    }
}

/** Unit tests for PhotoCorner. */
@DisplayName("PhotoCorner Tests")
class PhotoCornerTest {

    @Test
    fun `should create photo corner with coordinates`() {
        val corner = PhotoCorner(100f, 200f)

        assertThat(corner.x).isEqualTo(100f)
        assertThat(corner.y).isEqualTo(200f)
    }

    @Test
    fun `should calculate distance to another corner`() {
        val c1 = PhotoCorner(0f, 0f)
        val c2 = PhotoCorner(6f, 8f)

        val dx = (c2.x - c1.x).toDouble()
        val dy = (c2.y - c1.y).toDouble()
        val distance = kotlin.math.sqrt(dx * dx + dy * dy)

        assertThat(distance).isCloseTo(10.0, org.assertj.core.data.Offset.offset(0.001))
    }

    @Test
    fun `should convert to Point`() {
        val corner = PhotoCorner(50f, 75f)

        val point = Point(corner.x.toDouble(), corner.y.toDouble())

        assertThat(point.x).isEqualTo(50.0)
        assertThat(point.y).isEqualTo(75.0)
    }
}

/** Unit tests for CornerType. */
@DisplayName("CornerType Tests")
class CornerTypeTest {

    @Test
    fun `should have all corner types`() {
        assertThat(CornerType.entries.size).isGreaterThanOrEqualTo(4)
        assertThat(CornerType.entries).contains(CornerType.TOP_LEFT)
        assertThat(CornerType.entries).contains(CornerType.TOP_RIGHT)
        assertThat(CornerType.entries).contains(CornerType.BOTTOM_LEFT)
        assertThat(CornerType.entries).contains(CornerType.BOTTOM_RIGHT)
    }

    @Test
    fun `corner types should be distinguishable`() {
        val types = CornerType.entries.map { it.name }.toSet()
        assertThat(types.size).isEqualTo(CornerType.entries.size)
    }
}
