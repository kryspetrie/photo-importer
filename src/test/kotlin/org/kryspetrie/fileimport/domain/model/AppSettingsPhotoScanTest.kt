package org.kryspetrie.fileimport.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for AppSettings Photo Scan-related functionality.
 *
 * Tests profile management, recent destinations, and default profile handling.
 */
class AppSettingsPhotoScanTest {

    // ==================== Default Profile Tests ====================

    @Test
    fun `default settings includes default photo scan profile`() {
        val settings = AppSettings()

        assertEquals(1, settings.photoScanProfiles.size)
        assertEquals("Default", settings.photoScanProfiles.first().name)
    }

    @Test
    fun `activePhotoScanProfile returns selected profile by ID`() {
        val profile1 = PhotoScanProfile(id = "id1", name = "Profile 1")
        val profile2 = PhotoScanProfile(id = "id2", name = "Profile 2")
        val settings =
            AppSettings(
                photoScanProfiles = listOf(profile1, profile2),
                activePhotoScanProfileId = "id2",
            )

        assertEquals("Profile 2", settings.activePhotoScanProfile.name)
    }

    @Test
    fun `activePhotoScanProfile returns first profile when no ID set`() {
        val profile1 = PhotoScanProfile(id = "id1", name = "First")
        val profile2 = PhotoScanProfile(id = "id2", name = "Second")
        val settings =
            AppSettings(
                photoScanProfiles = listOf(profile1, profile2),
                activePhotoScanProfileId = null,
            )

        assertEquals("First", settings.activePhotoScanProfile.name)
    }

    @Test
    fun `activePhotoScanProfile returns default when no profiles`() {
        val settings = AppSettings(photoScanProfiles = emptyList())

        val profile = settings.activePhotoScanProfile

        assertNotNull(profile)
        assertEquals("Default", profile.name)
    }

    // ==================== Recent Destinations Tests ====================

    @Test
    fun `lastPhotoScanDestination returns recent destination when available`() {
        val settings =
            AppSettings(recentPhotoScanDestinations = listOf("/recent/path", "/older/path"))

        assertEquals("/recent/path", settings.lastPhotoScanDestination)
    }

    @Test
    fun `lastPhotoScanDestination returns profile destination when no recent`() {
        val profile = PhotoScanProfile(name = "Test", defaultDestination = "~/Pictures/Scans")
        val settings =
            AppSettings(
                photoScanProfiles = listOf(profile),
                recentPhotoScanDestinations = emptyList(),
            )

        val lastDest = settings.lastPhotoScanDestination

        assertTrue(lastDest.contains("Pictures"))
        assertTrue(lastDest.contains("Scans"))
    }

    @Test
    fun `recent destinations are ordered by recency`() {
        val settings =
            AppSettings(
                recentPhotoScanDestinations =
                    listOf("/path/1", "/path/2", "/path/3", "/path/4", "/path/5")
            )

        // Most recent should be first
        assertEquals("/path/1", settings.lastPhotoScanDestination)
    }

    // ==================== Profile Management Tests ====================

    @Test
    fun `can add new photo scan profile`() {
        val settings = AppSettings()
        val newProfile = PhotoScanProfile(name = "New Profile")

        val updated = settings.copy(photoScanProfiles = settings.photoScanProfiles + newProfile)

        assertEquals(2, updated.photoScanProfiles.size)
        assertTrue(updated.photoScanProfiles.any { it.name == "New Profile" })
    }

    @Test
    fun `can remove photo scan profile`() {
        val profile1 = PhotoScanProfile(id = "id1", name = "Keep")
        val profile2 = PhotoScanProfile(id = "id2", name = "Remove")
        val settings = AppSettings(photoScanProfiles = listOf(profile1, profile2))

        val updated =
            settings.copy(photoScanProfiles = settings.photoScanProfiles.filter { it.id != "id2" })

        assertEquals(1, updated.photoScanProfiles.size)
        assertTrue(updated.photoScanProfiles.all { it.name == "Keep" })
    }

    @Test
    fun `can update photo scan profile`() {
        val profile1 = PhotoScanProfile(id = "id1", name = "Original")
        val settings = AppSettings(photoScanProfiles = listOf(profile1))

        val updatedProfile = profile1.copy(name = "Updated")
        val updated =
            settings.copy(
                photoScanProfiles =
                    settings.photoScanProfiles.map { if (it.id == "id1") updatedProfile else it }
            )

        assertEquals("Updated", updated.photoScanProfiles.first().name)
    }

    @Test
    fun `active profile ID can be changed`() {
        val profile1 = PhotoScanProfile(id = "id1", name = "Profile 1")
        val profile2 = PhotoScanProfile(id = "id2", name = "Profile 2")
        val settings =
            AppSettings(
                photoScanProfiles = listOf(profile1, profile2),
                activePhotoScanProfileId = "id1",
            )

        val updated = settings.copy(activePhotoScanProfileId = "id2")

        assertEquals("id2", updated.activePhotoScanProfileId)
        assertEquals("Profile 2", updated.activePhotoScanProfile.name)
    }

    // ==================== Serialization Tests ====================

    @Test
    fun `settings can be serialized and deserialized`() {
        val original =
            AppSettings(
                photoScanProfiles =
                    listOf(
                        PhotoScanProfile(id = "id1", name = "Profile 1"),
                        PhotoScanProfile.createDocumentProfile(),
                    ),
                activePhotoScanProfileId = "id1",
                recentPhotoScanDestinations = listOf("/recent/path"),
            )

        // Serialize
        val json =
            kotlinx.serialization.json
                .Json {
                    prettyPrint = true
                    ignoreUnknownKeys = true
                }
                .encodeToString(AppSettings.serializer(), original)

        // Deserialize
        val deserialized =
            kotlinx.serialization.json
                .Json { ignoreUnknownKeys = true }
                .decodeFromString(AppSettings.serializer(), json)

        assertEquals(2, deserialized.photoScanProfiles.size)
        assertEquals("id1", deserialized.activePhotoScanProfileId)
        assertEquals(listOf("/recent/path"), deserialized.recentPhotoScanDestinations)
    }

    // ==================== lastCorrectionStrategy Tests ====================

    @Test
    fun `lastCorrectionStrategy defaults to PERSPECTIVE`() {
        val settings = AppSettings()
        assertEquals(CorrectionStrategy.PERSPECTIVE, settings.lastCorrectionStrategy)
    }

    @Test
    fun `lastCorrectionStrategy can be changed`() {
        val settings = AppSettings(lastCorrectionStrategy = CorrectionStrategy.CROP)
        assertEquals(CorrectionStrategy.CROP, settings.lastCorrectionStrategy)

        val updated = settings.copy(lastCorrectionStrategy = CorrectionStrategy.CROP_AND_ROTATE)
        assertEquals(CorrectionStrategy.CROP_AND_ROTATE, updated.lastCorrectionStrategy)
    }

    @Test
    fun `lastCorrectionStrategy persists through serialization`() {
        val original = AppSettings(lastCorrectionStrategy = CorrectionStrategy.CROP_AND_ROTATE)
        val json =
            kotlinx.serialization.json
                .Json { prettyPrint = true }
                .encodeToString(AppSettings.serializer(), original)
        val deserialized =
            kotlinx.serialization.json
                .Json { ignoreUnknownKeys = true }
                .decodeFromString(AppSettings.serializer(), json)

        assertEquals(CorrectionStrategy.CROP_AND_ROTATE, deserialized.lastCorrectionStrategy)
    }

    @Test
    fun `all three strategies can be stored and retrieved`() {
        for (strategy in CorrectionStrategy.entries) {
            val settings = AppSettings(lastCorrectionStrategy = strategy)
            val json =
                kotlinx.serialization.json
                    .Json { prettyPrint = true }
                    .encodeToString(AppSettings.serializer(), settings)
            val deserialized =
                kotlinx.serialization.json
                    .Json { ignoreUnknownKeys = true }
                    .decodeFromString(AppSettings.serializer(), json)

            assertEquals(strategy, deserialized.lastCorrectionStrategy)
        }
    }

    // ==================== Metadata Set (Recent Values) Tests ====================

    @Test
    fun `addMetadataSet adds a set to history`() {
        val settings = AppSettings()
        val set = RecentMetadataSet(city = "Worcester", state = "MA")

        val updated = settings.addMetadataSet(set)

        assertEquals(1, updated.metadataHistory.recentSets.size)
        assertEquals("Worcester", updated.metadataHistory.recentSets[0].city)
    }

    @Test
    fun `addMetadataSet preserves existing history`() {
        val settings = AppSettings()
            .addMetadataHistory("city", "Boston")
            .addMetadataSet(RecentMetadataSet(city = "Worcester", timestamp = 1000))

        val updated = settings.addMetadataSet(RecentMetadataSet(city = "Paris", timestamp = 2000))

        // History should be preserved
        assertEquals(listOf("Boston"), updated.metadataHistory.city)
        assertEquals(2, updated.metadataHistory.recentSets.size)
    }

    @Test
    fun `removeMetadataSet removes by timestamp`() {
        val set1 = RecentMetadataSet(city = "Boston", timestamp = 1000)
        val set2 = RecentMetadataSet(city = "Worcester", timestamp = 2000)

        val settings = AppSettings()
            .addMetadataSet(set1)
            .addMetadataSet(set2)
            .removeMetadataSet(1000)

        assertEquals(1, settings.metadataHistory.recentSets.size)
        assertEquals("Worcester", settings.metadataHistory.recentSets[0].city)
    }

    @Test
    fun `metadata set serialization round trip`() {
        val original = AppSettings(
            metadataHistory = MetadataHistory(
                description = listOf("Sunset", "Beach"),
                city = listOf("Worcester", "Boston"),
                recentSets = listOf(
                    RecentMetadataSet(
                        city = "Worcester",
                        state = "MA",
                        gpsLatitude = "42.2626",
                        gpsLongitude = "-71.8023",
                        timestamp = 1700000000000L,
                    )
                )
            )
        )

        val json = kotlinx.serialization.json.Json { prettyPrint = true; ignoreUnknownKeys = true }
            .encodeToString(AppSettings.serializer(), original)

        val deserialized = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString(AppSettings.serializer(), json)

        assertEquals(original.metadataHistory.description, deserialized.metadataHistory.description)
        assertEquals(original.metadataHistory.city, deserialized.metadataHistory.city)
        assertEquals(1, deserialized.metadataHistory.recentSets.size)
        assertEquals("Worcester", deserialized.metadataHistory.recentSets[0].city)
        assertEquals("42.2626", deserialized.metadataHistory.recentSets[0].gpsLatitude)
        assertEquals("-71.8023", deserialized.metadataHistory.recentSets[0].gpsLongitude)
    }

    @Test
    fun `addMetadataHistory works for gpsLatitude and gpsLongitude`() {
        val settings = AppSettings()
            .addMetadataHistory("gpsLatitude", "42.2626")
            .addMetadataHistory("gpsLatitude", "51.5074")
            .addMetadataHistory("gpsLongitude", "-71.8023")

        assertEquals(listOf("51.5074", "42.2626"), settings.metadataHistory.gpsLatitude)
        assertEquals(listOf("-71.8023"), settings.metadataHistory.gpsLongitude)
    }
}
