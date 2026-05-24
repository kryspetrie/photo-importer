package org.kryspetrie.fileimport.infrastructure.wizard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for PhotoConfiguration.
 *
 * Tests configuration creation, defaults, rotation cycling, and aspect ratio.
 */
class PhotoConfigurationTest {

    // ==================== Default Values ====================

    @Test
    fun `default configuration has correct defaults`() {
        val config = PhotoConfiguration()

        assertFalse(config.perspectiveCorrectionEnabled)
        assertEquals(0, config.rotationDegrees)
        assertEquals(0.0, config.aspectRatio)
    }

    @Test
    fun `custom configuration overrides defaults`() {
        val config =
            PhotoConfiguration(
                perspectiveCorrectionEnabled = true,
                rotationDegrees = 90,
                aspectRatio = 1.5,
            )

        assertTrue(config.perspectiveCorrectionEnabled)
        assertEquals(90, config.rotationDegrees)
        assertEquals(1.5, config.aspectRatio)
    }

    // ==================== Copy Tests ====================

    @Test
    fun `copy creates new instance with same values`() {
        val original =
            PhotoConfiguration(
                perspectiveCorrectionEnabled = true,
                rotationDegrees = 180,
                aspectRatio = 3.0 / 4.0,
            )

        val copy = original.copy()

        assertEquals(original.perspectiveCorrectionEnabled, copy.perspectiveCorrectionEnabled)
        assertEquals(original.rotationDegrees, copy.rotationDegrees)
        assertEquals(original.aspectRatio, copy.aspectRatio)
    }

    @Test
    fun `copy with modification changes only specified field`() {
        val original = PhotoConfiguration(perspectiveCorrectionEnabled = false, rotationDegrees = 0)

        val modified = original.copy(perspectiveCorrectionEnabled = true)

        assertTrue(modified.perspectiveCorrectionEnabled)
        assertEquals(0, modified.rotationDegrees) // Unchanged
    }

    // ==================== Perspective and Rotation Independence ====================

    @Test
    fun `perspective and rotation can both be false-and-zero`() {
        val config = PhotoConfiguration(perspectiveCorrectionEnabled = false, rotationDegrees = 0)

        assertFalse(config.perspectiveCorrectionEnabled)
        assertEquals(0, config.rotationDegrees)
    }

    @Test
    fun `perspective can be true with rotation`() {
        val config = PhotoConfiguration(perspectiveCorrectionEnabled = true, rotationDegrees = 90)

        assertTrue(config.perspectiveCorrectionEnabled)
        assertEquals(90, config.rotationDegrees)
    }

    @Test
    fun `rotation can be non-zero when perspective is false`() {
        val config = PhotoConfiguration(perspectiveCorrectionEnabled = false, rotationDegrees = 270)

        assertFalse(config.perspectiveCorrectionEnabled)
        assertEquals(270, config.rotationDegrees)
    }

    // ==================== Rotation Degrees (0/90/180/270 cycle) ====================

    @Test
    fun `rotation degrees can be 0`() {
        val config = PhotoConfiguration(rotationDegrees = 0)
        assertEquals(0, config.rotationDegrees)
    }

    @Test
    fun `rotation degrees can be 90`() {
        val config = PhotoConfiguration(rotationDegrees = 90)
        assertEquals(90, config.rotationDegrees)
    }

    @Test
    fun `rotation degrees can be 180`() {
        val config = PhotoConfiguration(rotationDegrees = 180)
        assertEquals(180, config.rotationDegrees)
    }

    @Test
    fun `rotation degrees can be 270`() {
        val config = PhotoConfiguration(rotationDegrees = 270)
        assertEquals(270, config.rotationDegrees)
    }

    // ==================== Cycle Rotation CW ====================

    @Test
    fun `cycleRotationCW goes from 0 to 90`() {
        val config = PhotoConfiguration(rotationDegrees = 0)
        assertEquals(90, config.cycleRotationCW().rotationDegrees)
    }

    @Test
    fun `cycleRotationCW goes from 90 to 180`() {
        val config = PhotoConfiguration(rotationDegrees = 90)
        assertEquals(180, config.cycleRotationCW().rotationDegrees)
    }

    @Test
    fun `cycleRotationCW goes from 180 to 270`() {
        val config = PhotoConfiguration(rotationDegrees = 180)
        assertEquals(270, config.cycleRotationCW().rotationDegrees)
    }

    @Test
    fun `cycleRotationCW wraps from 270 to 0`() {
        val config = PhotoConfiguration(rotationDegrees = 270)
        assertEquals(0, config.cycleRotationCW().rotationDegrees)
    }

    @Test
    fun `cycleRotationCW preserves other fields`() {
        val config = PhotoConfiguration(perspectiveCorrectionEnabled = true, aspectRatio = 1.5)
        val cycled = config.cycleRotationCW()
        assertTrue(cycled.perspectiveCorrectionEnabled)
        assertEquals(1.5, cycled.aspectRatio)
        assertEquals(90, cycled.rotationDegrees)
    }

    // ==================== Cycle Rotation CCW ====================

    @Test
    fun `cycleRotationCCW goes from 0 to 270`() {
        val config = PhotoConfiguration(rotationDegrees = 0)
        assertEquals(270, config.cycleRotationCCW().rotationDegrees)
    }

    @Test
    fun `cycleRotationCCW goes from 270 to 180`() {
        val config = PhotoConfiguration(rotationDegrees = 270)
        assertEquals(180, config.cycleRotationCCW().rotationDegrees)
    }

    @Test
    fun `cycleRotationCCW goes from 180 to 90`() {
        val config = PhotoConfiguration(rotationDegrees = 180)
        assertEquals(90, config.cycleRotationCCW().rotationDegrees)
    }

    @Test
    fun `cycleRotationCCW goes from 90 to 0`() {
        val config = PhotoConfiguration(rotationDegrees = 90)
        assertEquals(0, config.cycleRotationCCW().rotationDegrees)
    }

    @Test
    fun `cycleRotationCCW preserves other fields`() {
        val config = PhotoConfiguration(perspectiveCorrectionEnabled = true, aspectRatio = 2.0)
        val cycled = config.cycleRotationCCW()
        assertTrue(cycled.perspectiveCorrectionEnabled)
        assertEquals(2.0, cycled.aspectRatio)
        assertEquals(270, cycled.rotationDegrees)
    }

    // ==================== Aspect Ratio ====================

    @Test
    fun `aspect ratio of 0 means original`() {
        val config = PhotoConfiguration(aspectRatio = 0.0)
        assertEquals(0.0, config.aspectRatio)
    }

    @Test
    fun `aspect ratio can be 1_0 for square`() {
        val config = PhotoConfiguration(aspectRatio = 1.0)
        assertEquals(1.0, config.aspectRatio)
    }

    @Test
    fun `aspect ratio can be 3_2 for landscape photos`() {
        val config = PhotoConfiguration(aspectRatio = 3.0 / 2.0)
        assertEquals(3.0 / 2.0, config.aspectRatio)
    }

    @Test
    fun `aspect ratio can be 4_3 for standard photos`() {
        val config = PhotoConfiguration(aspectRatio = 4.0 / 3.0)
        assertEquals(4.0 / 3.0, config.aspectRatio)
    }
}
