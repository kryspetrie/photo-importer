package org.kryspetrie.fileimport.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("GeometryUtils")
class GeometryUtilsTest {

    @Nested
    @DisplayName("distance")
    inner class DistanceTests {

        @Test
        fun `calculates Euclidean distance between two points`() {
            val a = 0.0 to 0.0
            val b = 3.0 to 4.0
            assertEquals(5.0, GeometryUtils.distance(a, b), 0.001)
        }

        @Test
        fun `returns zero for identical points`() {
            val a = 5.0 to 5.0
            assertEquals(0.0, GeometryUtils.distance(a, a), 0.001)
        }

        @Test
        fun `calculates horizontal distance`() {
            val a = 0.0 to 0.0
            val b = 10.0 to 0.0
            assertEquals(10.0, GeometryUtils.distance(a, b), 0.001)
        }

        @Test
        fun `calculates vertical distance`() {
            val a = 0.0 to 0.0
            val b = 0.0 to 10.0
            assertEquals(10.0, GeometryUtils.distance(a, b), 0.001)
        }
    }

    @Nested
    @DisplayName("applyMargin")
    inner class ApplyMarginTests {

        private fun photo(
            tl: Pair<Float, Float>,
            tr: Pair<Float, Float>,
            br: Pair<Float, Float>,
            bl: Pair<Float, Float>,
        ): DetectedPhoto = DetectedPhoto(
            topLeft = PhotoCorner(tl.first, tl.second),
            topRight = PhotoCorner(tr.first, tr.second),
            bottomRight = PhotoCorner(br.first, br.second),
            bottomLeft = PhotoCorner(bl.first, bl.second),
        )

        @Test
        fun `returns same photo when margin is zero`() {
            val original = photo(
                10f to 10f, 90f to 10f, 90f to 90f, 10f to 90f
            )
            val result = GeometryUtils.applyMargin(original, 0.0)
            assertEquals(original, result)
        }

        @Test
        fun `returns same photo when margin is negative`() {
            val original = photo(
                10f to 10f, 90f to 10f, 90f to 90f, 10f to 90f
            )
            val result = GeometryUtils.applyMargin(original, -0.05)
            assertEquals(original, result)
        }

        @Test
        fun `expands corners outward from center`() {
            val original = photo(
                40f to 40f, 60f to 40f, 60f to 60f, 40f to 60f
            )
            val result = GeometryUtils.applyMargin(original, 0.1)

            // Each corner should be further from center after margin
            val origCx = (40f + 60f + 60f + 40f) / 4f
            val origCy = (40f + 40f + 60f + 60f) / 4f

            val origDist = GeometryUtils.distance(
                40.0 to 40.0, origCx.toDouble() to origCy.toDouble()
            )
            val resultDist = GeometryUtils.distance(
                result.topLeft.x.toDouble() to result.topLeft.y.toDouble(),
                origCx.toDouble() to origCy.toDouble()
            )

            assertTrue(resultDist > origDist, "Corners should expand outward from center")
        }

        @Test
        fun `preserves approximate aspect ratio after margin expansion`() {
            val original = photo(
                30f to 20f, 70f to 20f, 70f to 80f, 30f to 80f
            )
            val origWidth = 70f - 30f
            val origHeight = 80f - 20f
            val origRatio = origWidth.toDouble() / origHeight.toDouble()

            val result = GeometryUtils.applyMargin(original, 0.05)

            val resultWidth = result.topRight.x - result.topLeft.x
            val resultHeight = result.bottomLeft.y - result.topLeft.y
            val resultRatio = resultWidth.toDouble() / resultHeight.toDouble()

            // Ratio should be roughly preserved (within 10%)
            assertTrue(
                kotlin.math.abs(resultRatio - origRatio) / origRatio < 0.1,
                "Aspect ratio should be approximately preserved after margin expansion"
            )
        }

        @Test
        fun `positive margin expands all corners`() {
            val original = photo(
                100f to 100f, 200f to 100f, 200f to 200f, 100f to 200f
            )
            val result = GeometryUtils.applyMargin(original, 0.05)

            // Top-left should move up and left
            assertTrue(result.topLeft.x < original.topLeft.x, "TL.x should decrease")
            assertTrue(result.topLeft.y < original.topLeft.y, "TL.y should decrease")

            // Bottom-right should move down and right
            assertTrue(result.bottomRight.x > original.bottomRight.x, "BR.x should increase")
            assertTrue(result.bottomRight.y > original.bottomRight.y, "BR.y should increase")
        }
    }
}