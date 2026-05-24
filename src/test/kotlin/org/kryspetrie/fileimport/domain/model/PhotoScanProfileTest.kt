package org.kryspetrie.fileimport.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for PhotoScanProfile.
 *
 * Tests profile creation, validation, destination resolution, and usage tracking.
 */
class PhotoScanProfileTest {

    // ==================== Creation Tests ====================

    @Test
    fun `createDefault returns valid profile`() {
        val profile = PhotoScanProfile.createDefault()

        assertEquals("Default", profile.name)
        assertEquals("Pictures/PhotoScan", profile.defaultDestination)
        assertEquals(PhotoOutputFormat.JPEG_QUALITY_90, profile.outputFormat)
        assertEquals(AspectRatioPreset.ORIGINAL, profile.aspectRatioPreset)
        assertTrue(profile.correctionSettings.enablePerspectiveCorrection)
        assertFalse(profile.correctionSettings.enableRotationCorrection)
        assertTrue(profile.autoDetectEnabled)
        assertEquals("{original}", profile.namingPattern)
    }

    @Test
    fun `createDocumentProfile returns document-optimized profile`() {
        val profile = PhotoScanProfile.createDocumentProfile()

        assertEquals("Document Scan", profile.name)
        assertEquals("Documents/Scans", profile.defaultDestination)
        assertEquals(PhotoOutputFormat.JPEG_QUALITY_85, profile.outputFormat)
        assertEquals(AspectRatioPreset.PORTRAIT_4_3, profile.aspectRatioPreset)
        assertTrue(profile.correctionSettings.enablePerspectiveCorrection)
        assertTrue(profile.correctionSettings.enableRotationCorrection)
    }

    @Test
    fun `createPhotoAlbumProfile returns photo-album-optimized profile`() {
        val profile = PhotoScanProfile.createPhotoAlbumProfile()

        assertEquals("Photo Album", profile.name)
        assertEquals("Pictures/PhotoScan/Albums", profile.defaultDestination)
        assertEquals(PhotoOutputFormat.JPEG_QUALITY_95, profile.outputFormat)
        assertEquals(AspectRatioPreset.LANDSCAPE_3_2, profile.aspectRatioPreset)
    }

    @Test
    fun `custom profile can be created with all fields`() {
        val profile =
            PhotoScanProfile(
                id = "test-id",
                name = "Custom Profile",
                description = "Test description",
                defaultDestination = "/custom/path",
                outputFormat = PhotoOutputFormat.PNG,
                aspectRatioPreset = AspectRatioPreset.SQUARE,
                correctionSettings =
                    CorrectionSettings(
                        enablePerspectiveCorrection = false,
                        enableRotationCorrection = true,
                    ),
                namingPattern = "{date}_{original}",
                autoDetectEnabled = false,
            )

        assertEquals("test-id", profile.id)
        assertEquals("Custom Profile", profile.name)
        assertEquals("Test description", profile.description)
        assertEquals("/custom/path", profile.defaultDestination)
        assertEquals(PhotoOutputFormat.PNG, profile.outputFormat)
        assertEquals(AspectRatioPreset.SQUARE, profile.aspectRatioPreset)
        assertFalse(profile.correctionSettings.enablePerspectiveCorrection)
        assertTrue(profile.correctionSettings.enableRotationCorrection)
        assertEquals("{date}_{original}", profile.namingPattern)
        assertFalse(profile.autoDetectEnabled)
    }

    // ==================== Destination Resolution Tests ====================

    @Test
    fun `resolveDestination returns absolute paths unchanged`() {
        val profile =
            PhotoScanProfile(name = "Test", defaultDestination = "/absolute/path/to/folder")

        assertEquals("/absolute/path/to/folder", profile.resolveDestination())
    }

    @Test
    fun `resolveDestination resolves home-relative paths`() {
        val profile = PhotoScanProfile(name = "Test", defaultDestination = "~/Pictures/Scans")

        val resolved = profile.resolveDestination()
        assertTrue(resolved.startsWith(System.getProperty("user.home")))
        assertTrue(resolved.endsWith("Pictures/Scans"))
    }

    @Test
    fun `resolveDestination handles simple relative paths`() {
        val profile = PhotoScanProfile(name = "Test", defaultDestination = "Pictures/PhotoScan")

        val resolved = profile.resolveDestination()
        assertTrue(resolved.startsWith(System.getProperty("user.home")))
        assertTrue(resolved.endsWith("Pictures/PhotoScan"))
    }

    // ==================== Usage Tracking Tests ====================

    @Test
    fun `markAsUsed increments use count`() {
        val profile = PhotoScanProfile(name = "Test", useCount = 5)

        val updated = profile.markAsUsed()

        assertEquals(6, updated.useCount)
    }

    @Test
    fun `markAsUsed updates timestamps`() {
        val beforeTime = System.currentTimeMillis()
        val profile = PhotoScanProfile(name = "Test", lastUsedAt = 0L, updatedAt = 0L)

        val updated = profile.markAsUsed()
        val afterTime = System.currentTimeMillis()

        assertTrue(updated.lastUsedAt >= beforeTime)
        assertTrue(updated.lastUsedAt <= afterTime)
        assertTrue(updated.updatedAt >= beforeTime)
        assertTrue(updated.updatedAt <= afterTime)
    }

    @Test
    fun `markAsUsed preserves other fields`() {
        val profile =
            PhotoScanProfile(
                id = "test-id",
                name = "Test Profile",
                description = "Test description",
                defaultDestination = "/custom/path",
                useCount = 10,
            )

        val updated = profile.markAsUsed()

        assertEquals("test-id", updated.id)
        assertEquals("Test Profile", updated.name)
        assertEquals("Test description", updated.description)
        assertEquals("/custom/path", updated.defaultDestination)
        assertEquals(11, updated.useCount) // Incremented from 10
    }

    // ==================== Validation Tests ====================

    @Test
    fun `validate returns empty list for valid profile`() {
        val profile = PhotoScanProfile(name = "Valid Profile")

        val errors = profile.validate()

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `validate returns error for blank name`() {
        val profile = PhotoScanProfile(name = "   ")

        val errors = profile.validate()

        assertTrue(errors.any { it.contains("name") && it.contains("empty") })
    }

    @Test
    fun `validate returns error for name too long`() {
        val profile = PhotoScanProfile(name = "A".repeat(101))

        val errors = profile.validate()

        assertTrue(errors.any { it.contains("name") && it.contains("100") })
    }

    @Test
    fun `validate returns error for description too long`() {
        val profile = PhotoScanProfile(name = "Valid", description = "D".repeat(501))

        val errors = profile.validate()

        assertTrue(errors.any { it.contains("description") && it.contains("500") })
    }

    @Test
    fun `validate returns error for blank destination`() {
        val profile = PhotoScanProfile(name = "Valid", defaultDestination = "")

        val errors = profile.validate()

        assertTrue(errors.any { it.contains("destination") && it.contains("empty") })
    }

    @Test
    fun `validate returns error for blank naming pattern`() {
        val profile = PhotoScanProfile(name = "Valid", namingPattern = "")

        val errors = profile.validate()

        assertTrue(errors.any { it.contains("pattern") && it.contains("empty") })
    }

    @Test
    fun `validate returns error for invalid naming pattern`() {
        val profile =
            PhotoScanProfile(name = "Valid", namingPattern = "fixed_name_without_placeholders")

        val errors = profile.validate()

        assertTrue(
            errors.any {
                it.contains("pattern") &&
                    (it.contains("placeholder") ||
                        it.contains("original") ||
                        it.contains("counter") ||
                        it.contains("date"))
            }
        )
    }

    @Test
    fun `validate accepts pattern with original placeholder`() {
        val profile = PhotoScanProfile(name = "Valid", namingPattern = "{original}_scan")

        val errors = profile.validate()

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `validate accepts pattern with counter placeholder`() {
        val profile = PhotoScanProfile(name = "Valid", namingPattern = "photo_{counter}")

        val errors = profile.validate()

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `validate accepts pattern with date placeholder`() {
        val profile = PhotoScanProfile(name = "Valid", namingPattern = "{date}_photo")

        val errors = profile.validate()

        assertTrue(errors.isEmpty())
    }

    // ==================== Serialization Tests ====================

    @Test
    fun `profile can be serialized and deserialized`() {
        val original =
            PhotoScanProfile(
                id = "test-id",
                name = "Serializable Profile",
                description = "Test",
                defaultDestination = "~/Scans",
                outputFormat = PhotoOutputFormat.PNG,
                aspectRatioPreset = AspectRatioPreset.SQUARE,
                useCount = 42,
            )

        // Serialize to JSON
        val json =
            kotlinx.serialization.json.Json.encodeToString(PhotoScanProfile.serializer(), original)

        // Deserialize from JSON
        val deserialized =
            kotlinx.serialization.json.Json.decodeFromString(PhotoScanProfile.serializer(), json)

        assertEquals(original.id, deserialized.id)
        assertEquals(original.name, deserialized.name)
        assertEquals(original.description, deserialized.description)
        assertEquals(original.defaultDestination, deserialized.defaultDestination)
        assertEquals(original.outputFormat, deserialized.outputFormat)
        assertEquals(original.aspectRatioPreset, deserialized.aspectRatioPreset)
        assertEquals(original.useCount, deserialized.useCount)
    }
}
