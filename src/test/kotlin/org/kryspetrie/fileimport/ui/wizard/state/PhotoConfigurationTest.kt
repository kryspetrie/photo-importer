package org.kryspetrie.fileimport.ui.wizard.state

import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.model.FaceRegion

/**
 * Unit tests for PhotoScanConfiguration.
 *
 * Tests configuration creation, defaults, rotation cycling, and aspect ratio.
 */
class PhotoScanConfigurationTest {

    // ==================== Default Values ====================

    @Test
    fun `default configuration has correct defaults`() {
        val config = PhotoScanConfiguration()

        assertFalse(config.perspectiveCorrectionEnabled)
        assertEquals(0, config.rotationDegrees)
        assertEquals(0.0, config.aspectRatio)
    }

    @Test
    fun `custom configuration overrides defaults`() {
        val config =
            PhotoScanConfiguration(
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
            PhotoScanConfiguration(
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
        val original = PhotoScanConfiguration(perspectiveCorrectionEnabled = false, rotationDegrees = 0)

        val modified = original.copy(perspectiveCorrectionEnabled = true)

        assertTrue(modified.perspectiveCorrectionEnabled)
        assertEquals(0, modified.rotationDegrees) // Unchanged
    }

    // ==================== Perspective and Rotation Independence ====================

    @Test
    fun `perspective and rotation can both be false-and-zero`() {
        val config = PhotoScanConfiguration(perspectiveCorrectionEnabled = false, rotationDegrees = 0)

        assertFalse(config.perspectiveCorrectionEnabled)
        assertEquals(0, config.rotationDegrees)
    }

    @Test
    fun `perspective can be true with rotation`() {
        val config = PhotoScanConfiguration(perspectiveCorrectionEnabled = true, rotationDegrees = 90)

        assertTrue(config.perspectiveCorrectionEnabled)
        assertEquals(90, config.rotationDegrees)
    }

    @Test
    fun `rotation can be non-zero when perspective is false`() {
        val config = PhotoScanConfiguration(perspectiveCorrectionEnabled = false, rotationDegrees = 270)

        assertFalse(config.perspectiveCorrectionEnabled)
        assertEquals(270, config.rotationDegrees)
    }

    // ==================== Rotation Degrees (0/90/180/270 cycle) ====================

    @Test
    fun `rotation degrees can be 0`() {
        val config = PhotoScanConfiguration(rotationDegrees = 0)
        assertEquals(0, config.rotationDegrees)
    }

    @Test
    fun `rotation degrees can be 90`() {
        val config = PhotoScanConfiguration(rotationDegrees = 90)
        assertEquals(90, config.rotationDegrees)
    }

    @Test
    fun `rotation degrees can be 180`() {
        val config = PhotoScanConfiguration(rotationDegrees = 180)
        assertEquals(180, config.rotationDegrees)
    }

    @Test
    fun `rotation degrees can be 270`() {
        val config = PhotoScanConfiguration(rotationDegrees = 270)
        assertEquals(270, config.rotationDegrees)
    }

    // ==================== Cycle Rotation CW ====================

    @Test
    fun `cycleRotationCW goes from 0 to 90`() {
        val config = PhotoScanConfiguration(rotationDegrees = 0)
        assertEquals(90, config.cycleRotationCW().rotationDegrees)
    }

    @Test
    fun `cycleRotationCW goes from 90 to 180`() {
        val config = PhotoScanConfiguration(rotationDegrees = 90)
        assertEquals(180, config.cycleRotationCW().rotationDegrees)
    }

    @Test
    fun `cycleRotationCW goes from 180 to 270`() {
        val config = PhotoScanConfiguration(rotationDegrees = 180)
        assertEquals(270, config.cycleRotationCW().rotationDegrees)
    }

    @Test
    fun `cycleRotationCW wraps from 270 to 0`() {
        val config = PhotoScanConfiguration(rotationDegrees = 270)
        assertEquals(0, config.cycleRotationCW().rotationDegrees)
    }

    @Test
    fun `cycleRotationCW preserves other fields`() {
        val config = PhotoScanConfiguration(perspectiveCorrectionEnabled = true, aspectRatio = 1.5)
        val cycled = config.cycleRotationCW()
        assertTrue(cycled.perspectiveCorrectionEnabled)
        assertEquals(1.5, cycled.aspectRatio)
        assertEquals(90, cycled.rotationDegrees)
    }

    // ==================== Cycle Rotation CCW ====================

    @Test
    fun `cycleRotationCCW goes from 0 to 270`() {
        val config = PhotoScanConfiguration(rotationDegrees = 0)
        assertEquals(270, config.cycleRotationCCW().rotationDegrees)
    }

    @Test
    fun `cycleRotationCCW goes from 270 to 180`() {
        val config = PhotoScanConfiguration(rotationDegrees = 270)
        assertEquals(180, config.cycleRotationCCW().rotationDegrees)
    }

    @Test
    fun `cycleRotationCCW goes from 180 to 90`() {
        val config = PhotoScanConfiguration(rotationDegrees = 180)
        assertEquals(90, config.cycleRotationCCW().rotationDegrees)
    }

    @Test
    fun `cycleRotationCCW goes from 90 to 0`() {
        val config = PhotoScanConfiguration(rotationDegrees = 90)
        assertEquals(0, config.cycleRotationCCW().rotationDegrees)
    }

    @Test
    fun `cycleRotationCCW preserves other fields`() {
        val config = PhotoScanConfiguration(perspectiveCorrectionEnabled = true, aspectRatio = 2.0)
        val cycled = config.cycleRotationCCW()
        assertTrue(cycled.perspectiveCorrectionEnabled)
        assertEquals(2.0, cycled.aspectRatio)
        assertEquals(270, cycled.rotationDegrees)
    }

    // ==================== Aspect Ratio ====================

    @Test
    fun `aspect ratio of 0 means original`() {
        val config = PhotoScanConfiguration(aspectRatio = 0.0)
        assertEquals(0.0, config.aspectRatio)
    }

    @Test
    fun `aspect ratio can be 1_0 for square`() {
        val config = PhotoScanConfiguration(aspectRatio = 1.0)
        assertEquals(1.0, config.aspectRatio)
    }

    @Test
    fun `aspect ratio can be 3_2 for landscape photos`() {
        val config = PhotoScanConfiguration(aspectRatio = 3.0 / 2.0)
        assertEquals(3.0 / 2.0, config.aspectRatio)
    }

    @Test
    fun `aspect ratio can be 4_3 for standard photos`() {
        val config = PhotoScanConfiguration(aspectRatio = 4.0 / 3.0)
        assertEquals(4.0 / 3.0, config.aspectRatio)
    }

    // ==================== Metadata Fields ====================

    @Test
    fun `default configuration has empty metadata fields`() {
        val config = PhotoScanConfiguration()
        assertEquals("", config.description)
        assertEquals("", config.keywords)
        assertEquals("", config.originalDate)
        assertEquals("", config.year)
        assertEquals("", config.cameraModel)
        assertEquals("", config.cameraMake)
        assertFalse(config.hasMetadata())
    }

    @Test
    fun `hasMetadata returns true when description is set`() {
        val config = PhotoScanConfiguration(description = "Christmas morning")
        assertTrue(config.hasMetadata())
    }

    @Test
    fun `hasMetadata returns true when keywords is set`() {
        val config = PhotoScanConfiguration(keywords = "vacation, family")
        assertTrue(config.hasMetadata())
    }

    @Test
    fun `hasMetadata returns true when originalDate is set`() {
        val config = PhotoScanConfiguration(originalDate = "1995-06-15")
        assertTrue(config.hasMetadata())
    }

    @Test
    fun `hasMetadata returns true when camera fields are set`() {
        val config = PhotoScanConfiguration(cameraModel = "EOS 5D")
        assertTrue(config.hasMetadata())
    }

    @Test
    fun `hasMetadata returns false when all fields are empty strings`() {
        val config =
            PhotoScanConfiguration(description = "", keywords = "", originalDate = "", year = "")
        assertFalse(config.hasMetadata())
    }

    // ==================== Location Fields ====================

    @Test
    fun `hasMetadata returns true when locationName is set`() {
        val config = PhotoScanConfiguration(locationName = "Grandma's house")
        assertTrue(config.hasMetadata())
    }

    @Test
    fun `hasMetadata returns true when city is set`() {
        val config = PhotoScanConfiguration(city = "Worcester")
        assertTrue(config.hasMetadata())
    }

    @Test
    fun `hasMetadata returns true when gpsLatitude and gpsLongitude are set`() {
        val config = PhotoScanConfiguration(gpsLatitude = "42.2626", gpsLongitude = "-71.8023")
        assertTrue(config.hasMetadata())
        assertTrue(config.hasGpsCoordinates())
    }

    @Test
    fun `hasGpsCoordinates returns false when only latitude is set`() {
        val config = PhotoScanConfiguration(gpsLatitude = "42.2626")
        assertFalse(config.hasGpsCoordinates())
    }

    @Test
    fun `locationDisplay combines location name, city, state, country`() {
        val config =
            PhotoScanConfiguration(
                locationName = "Grandma's house",
                city = "Worcester",
                state = "MA",
                country = "United States",
            )
        assertEquals("Grandma's house, Worcester, MA, United States", config.locationDisplay())
    }

    @Test
    fun `locationDisplay skips blank fields`() {
        val config = PhotoScanConfiguration(city = "Worcester", state = "MA")
        assertEquals("Worcester, MA", config.locationDisplay())
    }

    // ==================== Subject Fields ====================

    @Test
    fun `hasMetadata returns true when subjects is set`() {
        val config = PhotoScanConfiguration(subjects = "Alice, Bob")
        assertTrue(config.hasMetadata())
    }

    @Test
    fun `subjectList parses comma-separated names`() {
        val config = PhotoScanConfiguration(subjects = "Alice, Bob, Charlie")
        assertEquals(listOf("Alice", "Bob", "Charlie"), config.subjectList())
    }

    @Test
    fun `keywordList parses comma-separated keywords`() {
        val config = PhotoScanConfiguration(keywords = "vacation, family, holiday")
        assertEquals(listOf("vacation", "family", "holiday"), config.keywordList())
    }

    @Test
    fun `withKeywordList joins list into comma-separated string`() {
        val config = PhotoScanConfiguration().withKeywordList(listOf("vacation", "family", "holiday"))
        assertEquals("vacation, family, holiday", config.keywords)
    }

    @Test
    fun `hasMetadata returns true when faceRegions is non-empty`() {
        val config = PhotoScanConfiguration(faceRegions = listOf(FaceRegion(name = "Alice")))
        assertTrue(config.hasMetadata())
    }
}
