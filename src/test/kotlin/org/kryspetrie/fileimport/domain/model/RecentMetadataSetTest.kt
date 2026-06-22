package org.kryspetrie.fileimport.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecentMetadataSetTest {

    // ── label tests ──

    @Test
    fun `label prefers location over description`() {
        val set = RecentMetadataSet(
            locationName = "Grandma's house",
            city = "Worcester",
            country = "United States",
            description = "Beach photo",
        )

        assertEquals("Grandma's house, Worcester, United States", set.label)
    }

    @Test
    fun `label uses description when no location`() {
        val set = RecentMetadataSet(description = "Beach photo")

        assertEquals("Beach photo", set.label)
    }

    @Test
    fun `label uses originalDate when no location or description`() {
        val set = RecentMetadataSet(originalDate = "2024-06-15")

        assertEquals("2024-06-15", set.label)
    }

    @Test
    fun `label falls back to unlabeled set when all blank`() {
        val set = RecentMetadataSet()

        assertEquals("unlabeled set", set.label)
    }

    @Test
    fun `label combines location fields correctly`() {
        val set = RecentMetadataSet(
            city = "Worcester",
            state = "MA",
        )

        assertEquals("Worcester, MA", set.label)
    }

    // ── hasAnyValue tests ──

    @Test
    fun `hasAnyValue returns false for empty set`() {
        val set = RecentMetadataSet()
        assertFalse(set.hasAnyValue())
    }

    @Test
    fun `hasAnyValue returns true for set with city`() {
        val set = RecentMetadataSet(city = "Boston")
        assertTrue(set.hasAnyValue())
    }

    @Test
    fun `hasAnyValue returns true for set with gpsLatitude`() {
        val set = RecentMetadataSet(gpsLatitude = "42.2626")
        assertTrue(set.hasAnyValue())
    }

    @Test
    fun `hasAnyValue returns true for set with subjects`() {
        val set = RecentMetadataSet(subjects = "Alice, Bob")
        assertTrue(set.hasAnyValue())
    }

    @Test
    fun `hasAnyValue returns false when all fields are blank strings`() {
        val set = RecentMetadataSet(
            description = "",
            city = "",
            gpsLatitude = "",
        )
        assertFalse(set.hasAnyValue())
    }

    // ── summary tests ──

    @Test
    fun `summary shows location, date, and camera`() {
        val set = RecentMetadataSet(
            city = "Worcester",
            state = "MA",
            originalDate = "2024-06-15",
            cameraMake = "Canon",
            cameraModel = "EOS R5",
        )

        assertTrue(set.summary.contains("Worcester, MA"))
        assertTrue(set.summary.contains("2024-06-15"))
        assertTrue(set.summary.contains("Canon EOS R5"))
    }

    @Test
    fun `summary uses year when originalDate is blank`() {
        val set = RecentMetadataSet(year = "2024")

        assertTrue(set.summary.contains("2024"))
    }

    @Test
    fun `summary shows only camera when no location or date`() {
        val set = RecentMetadataSet(cameraMake = "Nikon")

        assertEquals("Nikon", set.summary)
    }

    @Test
    fun `summary is empty when all fields blank`() {
        val set = RecentMetadataSet()
        assertEquals("", set.summary)
    }

    @Test
    fun `summary shows location name over city for short labels`() {
        val set = RecentMetadataSet(
            locationName = "Grandma's house",
            city = "Worcester",
            state = "MA",
        )

        assertTrue(set.summary.startsWith("Grandma's house, Worcester, MA"))
    }

    // ── fromConfig tests ──

    @Test
    fun `fromConfig captures all metadata fields`() {
        val config = PhotoScanConfiguration(
            description = "Sunset over mountains",
            keywords = "vacation, landscape",
            originalDate = "2024-06-15",
            year = "2024",
            cameraMake = "Canon",
            cameraModel = "EOS R5",
            lensModel = "RF 24-70mm f/2.8L",
            focalLength = "35mm",
            aperture = "f/2.8",
            shutterSpeed = "1/500",
            iso = "400",
            locationName = "Grandma's house",
            city = "Worcester",
            state = "MA",
            country = "United States",
            gpsLatitude = "42.2626",
            gpsLongitude = "-71.8023",
            subjects = "Alice, Bob",
        )

        val set = RecentMetadataSet.fromConfig(config)

        assertEquals("Sunset over mountains", set.description)
        assertEquals("vacation, landscape", set.keywords)
        assertEquals("2024-06-15", set.originalDate)
        assertEquals("2024", set.year)
        assertEquals("Canon", set.cameraMake)
        assertEquals("EOS R5", set.cameraModel)
        assertEquals("RF 24-70mm f/2.8L", set.lensModel)
        assertEquals("35mm", set.focalLength)
        assertEquals("f/2.8", set.aperture)
        assertEquals("1/500", set.shutterSpeed)
        assertEquals("400", set.iso)
        assertEquals("Grandma's house", set.locationName)
        assertEquals("Worcester", set.city)
        assertEquals("MA", set.state)
        assertEquals("United States", set.country)
        assertEquals("42.2626", set.gpsLatitude)
        assertEquals("-71.8023", set.gpsLongitude)
        assertEquals("Alice, Bob", set.subjects)
    }

    @Test
    fun `fromConfig with empty config produces blank fields`() {
        val config = PhotoScanConfiguration()
        val set = RecentMetadataSet.fromConfig(config)

        assertEquals("", set.description)
        assertEquals("", set.city)
        assertEquals("", set.gpsLatitude)
    }

    @Test
    fun `fromConfig does not include face regions`() {
        // Face regions are photo-specific coordinate data, not reusable metadata
        val config = PhotoScanConfiguration(description = "Test")
        val set = RecentMetadataSet.fromConfig(config)

        // There's no faceRegions field in RecentMetadataSet
        assertTrue(set.hasAnyValue())
        assertEquals("Test", set.description)
    }

    // ── Serialization round-trip test ──

    @Test
    fun `RecentMetadataSet fields are accessible after construction`() {
        val set = RecentMetadataSet(
            description = "Test",
            city = "Boston",
            gpsLatitude = "42.3601",
            gpsLongitude = "-71.0589",
            timestamp = 1700000000000L,
        )

        assertEquals("Test", set.description)
        assertEquals("Boston", set.city)
        assertEquals("42.3601", set.gpsLatitude)
        assertEquals("-71.0589", set.gpsLongitude)
        assertEquals(1700000000000L, set.timestamp)
    }
}