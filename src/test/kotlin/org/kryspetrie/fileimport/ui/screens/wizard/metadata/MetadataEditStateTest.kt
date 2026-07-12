package org.kryspetrie.fileimport.ui.screens.wizard.metadata

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.domain.model.RecentMetadataSet

class MetadataEditStateTest {

    private val fullConfig =
        PhotoScanConfiguration(
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

    private val emptyConfig = PhotoScanConfiguration()

    @Test
    fun `clear resets all fields to empty strings`() {
        val state = MetadataEditState()
        state.loadFrom(fullConfig)
        // Verify something was loaded
        assertEquals("Sunset over mountains", state.description)
        assertEquals("Canon", state.cameraMake)

        state.clear()

        assertEquals("", state.description)
        assertEquals("", state.keywords)
        assertEquals("", state.originalDate)
        assertEquals("", state.year)
        assertEquals("", state.cameraMake)
        assertEquals("", state.cameraModel)
        assertEquals("", state.lensModel)
        assertEquals("", state.focalLength)
        assertEquals("", state.aperture)
        assertEquals("", state.shutterSpeed)
        assertEquals("", state.iso)
        assertEquals("", state.locationName)
        assertEquals("", state.city)
        assertEquals("", state.state)
        assertEquals("", state.country)
        assertEquals("", state.gpsLatitude)
        assertEquals("", state.gpsLongitude)
        assertEquals("", state.subjects)
    }

    @Test
    fun `loadFrom populates all fields from config`() {
        val state = MetadataEditState()
        state.loadFrom(fullConfig)

        assertEquals("Sunset over mountains", state.description)
        assertEquals("vacation, landscape", state.keywords)
        assertEquals("2024-06-15", state.originalDate)
        assertEquals("2024", state.year)
        assertEquals("Canon", state.cameraMake)
        assertEquals("EOS R5", state.cameraModel)
        assertEquals("RF 24-70mm f/2.8L", state.lensModel)
        assertEquals("35mm", state.focalLength)
        assertEquals("f/2.8", state.aperture)
        assertEquals("1/500", state.shutterSpeed)
        assertEquals("400", state.iso)
        assertEquals("Grandma's house", state.locationName)
        assertEquals("Worcester", state.city)
        assertEquals("MA", state.state)
        assertEquals("United States", state.country)
        assertEquals("42.2626", state.gpsLatitude)
        assertEquals("-71.8023", state.gpsLongitude)
        assertEquals("Alice, Bob", state.subjects)
    }

    @Test
    fun `loadFrom with empty config sets all fields to empty`() {
        val state = MetadataEditState()
        state.loadFrom(emptyConfig)

        assertEquals("", state.description)
        assertEquals("", state.keywords)
        assertEquals("", state.originalDate)
        assertEquals("", state.year)
        assertEquals("", state.cameraMake)
    }

    @Test
    fun `loadFrom then modify reflects changes`() {
        val state = MetadataEditState()
        state.loadFrom(fullConfig)
        state.description = "New description"
        state.year = "2025"

        assertEquals("New description", state.description)
        assertEquals("2025", state.year)
        // Other fields unchanged
        assertEquals("Canon", state.cameraMake)
    }

    @Test
    fun `applyToConfig copies all fields from state to config`() {
        val state = MetadataEditState()
        state.loadFrom(fullConfig)
        state.description = "Modified description"
        state.year = "2025"

        val result = state.applyToConfig(emptyConfig)

        assertEquals("Modified description", result.description)
        assertEquals("vacation, landscape", result.keywords)
        assertEquals("2024-06-15", result.originalDate)
        assertEquals("2025", result.year)
        assertEquals("Canon", result.cameraMake)
        assertEquals("Worcester", result.city)
    }

    @Test
    fun `applyToConfig preserves non-metadata fields`() {
        val base =
            PhotoScanConfiguration(
                perspectiveCorrectionEnabled = true,
                rotationDegrees = 90,
                aspectRatio = 1.5,
            )
        val state = MetadataEditState()
        state.description = "Test"
        state.year = "2024"

        val result = state.applyToConfig(base)

        // Metadata fields applied
        assertEquals("Test", result.description)
        assertEquals("2024", result.year)
        // Non-metadata fields preserved
        assertTrue(result.perspectiveCorrectionEnabled)
        assertEquals(90, result.rotationDegrees)
        assertEquals(1.5, result.aspectRatio, 0.001)
    }

    @Test
    fun `applyNonBlankTo replaces only non-blank fields`() {
        val state = MetadataEditState()
        state.description = "New description"
        state.year = "2025"
        // All other fields left blank (default)

        val result = state.applyNonBlankTo(fullConfig)

        // Non-blank fields applied
        assertEquals("New description", result.description)
        assertEquals("2025", result.year)
        // Blank fields preserved from original
        assertEquals("vacation, landscape", result.keywords)
        assertEquals("2024-06-15", result.originalDate)
        assertEquals("Canon", result.cameraMake)
        assertEquals("Worcester", result.city)
    }

    @Test
    fun `applyNonBlankTo with all blanks preserves original entirely`() {
        val state = MetadataEditState() // all blank

        val result = state.applyNonBlankTo(fullConfig)

        assertEquals(fullConfig, result)
    }

    @Test
    fun `applyNonBlankTo with all fields set replaces everything`() {
        val state = MetadataEditState()
        state.description = "Override desc"
        state.keywords = "override keywords"
        state.originalDate = "2099-01-01"
        state.year = "2099"
        state.cameraMake = "Nikon"
        state.cameraModel = "Z9"
        state.lensModel = "Z 50mm f/1.8"
        state.focalLength = "50mm"
        state.aperture = "f/1.8"
        state.shutterSpeed = "1/1000"
        state.iso = "800"
        state.locationName = "Paris"
        state.city = "Paris"
        state.state = "Île-de-France"
        state.country = "France"
        state.gpsLatitude = "48.8566"
        state.gpsLongitude = "2.3522"
        state.subjects = "Charlie"

        val result = state.applyNonBlankTo(emptyConfig)

        assertEquals("Override desc", result.description)
        assertEquals("override keywords", result.keywords)
        assertEquals("2099-01-01", result.originalDate)
        assertEquals("2099", result.year)
        assertEquals("Nikon", result.cameraMake)
        assertEquals("Paris", result.city)
    }

    @Test
    fun `applyToConfig with empty state replaces all fields including blanks`() {
        val state = MetadataEditState() // all blank

        val result = state.applyToConfig(fullConfig)

        // All fields set to blank (empty strings), overwriting original values
        assertEquals("", result.description)
        assertEquals("", result.keywords)
        assertEquals("", result.originalDate)
        assertEquals("", result.year)
        assertEquals("", result.cameraMake)
    }

    @Test
    fun `loadFrom then applyNonBlankTo round-trip preserves values`() {
        val state = MetadataEditState()
        state.loadFrom(fullConfig)

        val result = state.applyNonBlankTo(emptyConfig)

        // Since all fields are non-blank in fullConfig, they should all be applied
        assertEquals("Sunset over mountains", result.description)
        assertEquals("vacation, landscape", result.keywords)
        assertEquals("Canon", result.cameraMake)
        assertEquals("Worcester", result.city)
    }

    // ── loadFromSet tests ──

    @Test
    fun `loadFromSet fills only non-blank fields`() {
        val state = MetadataEditState()
        state.description = "Existing description"
        state.city = "Existing city"

        val set =
            RecentMetadataSet(
                locationName = "Grandma's house",
                city = "Worcester",
                country = "United States",
            )
        state.loadFromSet(set)

        // Non-blank fields from the set overwrite existing values
        assertEquals("Grandma's house", state.locationName)
        assertEquals("Worcester", state.city)
        assertEquals("United States", state.country)
        // Pre-existing values in fields that are blank in the set are preserved
        assertEquals("Existing description", state.description)
        // Other fields left untouched since they were blank in state and set
        assertEquals("", state.keywords)
        assertEquals("", state.originalDate)
    }

    @Test
    fun `loadFromSet with empty set leaves state unchanged`() {
        val state = MetadataEditState()
        state.description = "Test"
        state.city = "Boston"

        val emptySet = RecentMetadataSet()
        state.loadFromSet(emptySet)

        assertEquals("Test", state.description)
        assertEquals("Boston", state.city)
    }

    @Test
    fun `loadFromSet with full set overwrites all existing values`() {
        val state = MetadataEditState()
        state.description = "Old"

        val set =
            RecentMetadataSet(
                description = "New",
                keywords = "new, keywords",
                originalDate = "2024-01-01",
                year = "2024",
                cameraMake = "Canon",
                cameraModel = "EOS R5",
                lensModel = "RF 24-70mm",
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
                subjects = "Alice",
            )
        state.loadFromSet(set)

        assertEquals("New", state.description)
        assertEquals("new, keywords", state.keywords)
        assertEquals("2024-01-01", state.originalDate)
        assertEquals("2024", state.year)
        assertEquals("Canon", state.cameraMake)
        assertEquals("Worcester", state.city)
        assertEquals("42.2626", state.gpsLatitude)
        assertEquals("-71.8023", state.gpsLongitude)
        assertEquals("Alice", state.subjects)
    }

    @Test
    fun `loadFromSet preserves blank fields in state when set is blank`() {
        val state = MetadataEditState()
        // Start with all empty
        assertEquals("", state.description)
        assertEquals("", state.city)

        val set = RecentMetadataSet(city = "Paris")
        state.loadFromSet(set)

        // City was set from the set
        assertEquals("Paris", state.city)
        // Description was blank in both state and set, stays blank
        assertEquals("", state.description)
    }
}
