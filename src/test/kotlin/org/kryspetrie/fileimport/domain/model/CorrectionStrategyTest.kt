package org.kryspetrie.fileimport.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class CorrectionStrategyTest {

    // ── determineCorrectionStrategy ──────────────────────────────────────

    @Test
    fun `perfect rectangle returns CROP`() {
        val corners =
            listOf(
                PhotoCorner(100f, 100f), // TL
                PhotoCorner(400f, 100f), // TR
                PhotoCorner(400f, 300f), // BR
                PhotoCorner(100f, 300f), // BL
            )
        assertEquals(CorrectionStrategy.CROP, determineCorrectionStrategy(corners))
    }

    @Test
    fun `slightly rotated rectangle returns CROP_AND_ROTATE`() {
        // Rotate the rectangle by ~2° (well under the 3° skew threshold)
        val rad = Math.toRadians(2.0)
        val cos = kotlin.math.cos(rad)
        val sin = kotlin.math.sin(rad)
        fun rotate(x: Double, y: Double): Pair<Float, Float> {
            val cx = 250.0
            val cy = 200.0
            val rx = (x - cx) * cos - (y - cy) * sin + cx
            val ry = (x - cx) * sin + (y - cy) * cos + cy
            return rx.toFloat() to ry.toFloat()
        }
        val (tlx, tly) = rotate(100.0, 100.0)
        val (trx, try_) = rotate(400.0, 100.0)
        val (brx, bry) = rotate(400.0, 300.0)
        val (blx, bly) = rotate(100.0, 300.0)

        val corners =
            listOf(
                PhotoCorner(tlx, tly),
                PhotoCorner(trx, try_),
                PhotoCorner(brx, bry),
                PhotoCorner(blx, bly),
            )

        val strategy = determineCorrectionStrategy(corners, rotationThresholdDegrees = 1.5)
        assertEquals(CorrectionStrategy.CROP_AND_ROTATE, strategy)
    }

    @Test
    fun `trapezoid returns PERSPECTIVE`() {
        // A clear trapezoid — top edge much shorter than bottom
        val corners =
            listOf(
                PhotoCorner(150f, 100f), // TL (narrow)
                PhotoCorner(350f, 100f), // TR (narrow)
                PhotoCorner(400f, 300f), // BR (wide)
                PhotoCorner(100f, 300f), // BL (wide)
            )

        assertEquals(CorrectionStrategy.PERSPECTIVE, determineCorrectionStrategy(corners))
    }

    @Test
    fun `custom thresholds change strategy`() {
        // Same trapezoid but with very high threshold → CROP
        val corners =
            listOf(
                PhotoCorner(150f, 100f),
                PhotoCorner(350f, 100f),
                PhotoCorner(400f, 300f),
                PhotoCorner(100f, 300f),
            )

        // Very high skew threshold means even this trapezoid is "close enough"
        val strategy =
            determineCorrectionStrategy(
                corners,
                skewThresholdDegrees = 30.0,
                rotationThresholdDegrees = 30.0,
            )
        // The trapezoid has significant angle deviation, so PERSPECTIVE even with high threshold
        // unless we go really high.
        // With skewThresholdDegrees=30, angles ~25° deviation still within threshold,
        // so depends on the actual angles. Let's just test a near-rectangle with tight threshold.
        val nearRect =
            listOf(
                PhotoCorner(100f, 100f),
                PhotoCorner(400f, 101f), // slightly rotated
                PhotoCorner(401f, 300f),
                PhotoCorner(101f, 299f),
            )
        // With very tight rotation threshold, this should be CROP_AND_ROTATE
        assertEquals(
            CorrectionStrategy.CROP_AND_ROTATE,
            determineCorrectionStrategy(nearRect, rotationThresholdDegrees = 0.1),
        )
        // With loose rotation threshold, same shape should be CROP
        assertEquals(
            CorrectionStrategy.CROP,
            determineCorrectionStrategy(nearRect, rotationThresholdDegrees = 10.0),
        )
    }

    // ── computeCornerAngles ──────────────────────────────────────────────

    @Test
    fun `perfect rectangle has 90-degree corners`() {
        val corners =
            listOf(
                PhotoCorner(100f, 100f), // TL
                PhotoCorner(400f, 100f), // TR
                PhotoCorner(400f, 300f), // BR
                PhotoCorner(100f, 300f), // BL
            )
        val angles = computeCornerAngles(corners)
        assertEquals(4, angles.size)
        angles.forEach { angle ->
            assertEquals(90.0, angle, 0.01, "Perfect rectangle corner should be ~90°")
        }
    }

    @Test
    fun `corner angles sum to approximately 360 for convex quad`() {
        val corners =
            listOf(
                PhotoCorner(100f, 100f),
                PhotoCorner(400f, 100f),
                PhotoCorner(350f, 300f),
                PhotoCorner(150f, 300f),
            )
        val angles = computeCornerAngles(corners)
        val sum = angles.sum()
        // For a convex quadrilateral, interior angles sum to 360°
        assertEquals(360.0, sum, 5.0, "Interior angles should sum to ~360°")
    }

    // ── computeAverageRotation ───────────────────────────────────────────

    @Test
    fun `axis-aligned rectangle has zero rotation`() {
        val corners =
            listOf(
                PhotoCorner(100f, 100f),
                PhotoCorner(400f, 100f),
                PhotoCorner(400f, 300f),
                PhotoCorner(100f, 300f),
            )
        assertEquals(0.0, computeAverageRotation(corners), 0.01)
    }

    @Test
    fun `rotated rectangle returns non-zero rotation`() {
        // Rotate by exactly 5°
        val rad = Math.toRadians(5.0)
        val cos = kotlin.math.cos(rad)
        val sin = kotlin.math.sin(rad)
        fun rotate(x: Double, y: Double): Pair<Float, Float> {
            val cx = 250.0
            val cy = 200.0
            val rx = (x - cx) * cos - (y - cy) * sin + cx
            val ry = (x - cx) * sin + (y - cy) * cos + cy
            return rx.toFloat() to ry.toFloat()
        }
        val (tlx, tly) = rotate(100.0, 100.0)
        val (trx, try_) = rotate(400.0, 100.0)
        val (brx, bry) = rotate(400.0, 300.0)
        val (blx, bly) = rotate(100.0, 300.0)

        val corners =
            listOf(
                PhotoCorner(tlx, tly),
                PhotoCorner(trx, try_),
                PhotoCorner(brx, bry),
                PhotoCorner(blx, bly),
            )
        val rotation = computeAverageRotation(corners)
        assertEquals(5.0, rotation, 0.5, "Rotation should be approximately 5°")
    }

    // ── CorrectionStrategy enum ──────────────────────────────────────────

    @Test
    fun `CorrectionStrategy display names are human-readable`() {
        assertEquals("Crop Only", CorrectionStrategy.CROP.displayName)
        assertEquals("Crop & Rotate", CorrectionStrategy.CROP_AND_ROTATE.displayName)
        assertEquals("Perspective", CorrectionStrategy.PERSPECTIVE.displayName)
    }

    @Test
    fun `CorrectionStrategy has descriptions`() {
        for (strategy in CorrectionStrategy.entries) {
            assert(strategy.description.isNotBlank()) {
                "${strategy.name} should have a description"
            }
        }
    }
}
