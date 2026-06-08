package org.kryspetrie.fileimport.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/** Unit tests for PhotoScanConfiguration and related models. */
@DisplayName("PhotoScanConfiguration")
class PhotoScanConfigurationTest {

    @Nested
    @DisplayName("defaults")
    inner class Defaults {
        @Test
        fun `should have empty date fields by default`() {
            val config = PhotoScanConfiguration()

            assertThat(config.originalDate).isEmpty()
            assertThat(config.year).isEmpty()
        }

        @Test
        fun `should have empty metadata fields by default`() {
            val config = PhotoScanConfiguration()

            assertThat(config.keywords).isEmpty()
            assertThat(config.description).isEmpty()
        }
    }

    @Nested
    @DisplayName("date overrides")
    inner class DateOverrides {
        @Test
        fun `should allow setting date override`() {
            val config = PhotoScanConfiguration(originalDate = "2024-01-15")

            assertThat(config.originalDate).isEqualTo("2024-01-15")
        }

        @Test
        fun `should allow setting year override`() {
            val config = PhotoScanConfiguration(year = "2023")

            assertThat(config.year).isEqualTo("2023")
        }

        @Test
        fun `should allow setting all date fields together`() {
            val config = PhotoScanConfiguration(originalDate = "2024-06-15", year = "2023")

            assertThat(config.originalDate).isEqualTo("2024-06-15")
            assertThat(config.year).isEqualTo("2023")
        }
    }

    @Nested
    @DisplayName("metadata fields")
    inner class MetadataFields {
        @Test
        fun `should allow setting keywords`() {
            val config = PhotoScanConfiguration(keywords = "vacation, family, summer")

            assertThat(config.keywords).isEqualTo("vacation, family, summer")
        }

        @Test
        fun `should allow setting description`() {
            val config = PhotoScanConfiguration(description = "Summer vacation 2024")

            assertThat(config.description).isEqualTo("Summer vacation 2024")
        }

        @Test
        fun `should allow setting both keywords and description`() {
            val config =
                PhotoScanConfiguration(
                    keywords = "document, receipt",
                    description = "Important tax document",
                )

            assertThat(config.keywords).isEqualTo("document, receipt")
            assertThat(config.description).isEqualTo("Important tax document")
        }
    }

    @Nested
    @DisplayName("EXIF metadata fields")
    inner class ExifMetadataFields {
        @Test
        fun `should have empty EXIF overrides by default`() {
            val config = PhotoScanConfiguration()

            assertThat(config.description).isEmpty()
            assertThat(config.keywords).isEmpty()
            assertThat(config.originalDate).isEmpty()
            assertThat(config.year).isEmpty()
            assertThat(config.cameraMake).isEmpty()
            assertThat(config.cameraModel).isEmpty()
            assertThat(config.lensModel).isEmpty()
            assertThat(config.focalLength).isEmpty()
            assertThat(config.aperture).isEmpty()
            assertThat(config.shutterSpeed).isEmpty()
            assertThat(config.iso).isEmpty()
        }

        @Test
        fun `should allow setting EXIF metadata overrides`() {
            val config =
                PhotoScanConfiguration(
                    description = "A family portrait",
                    keywords = "vacation, family",
                    originalDate = "2024:06:15 12:30:00",
                    year = "2024",
                    cameraMake = "Nikon",
                    cameraModel = "D850",
                    lensModel = "AF-S Nikkor 50mm f/1.8",
                    focalLength = "50",
                    aperture = "2.8",
                    shutterSpeed = "1/125",
                    iso = "400",
                )

            assertThat(config.description).isEqualTo("A family portrait")
            assertThat(config.keywords).isEqualTo("vacation, family")
            assertThat(config.originalDate).isEqualTo("2024:06:15 12:30:00")
            assertThat(config.year).isEqualTo("2024")
            assertThat(config.cameraMake).isEqualTo("Nikon")
            assertThat(config.cameraModel).isEqualTo("D850")
            assertThat(config.lensModel).isEqualTo("AF-S Nikkor 50mm f/1.8")
            assertThat(config.focalLength).isEqualTo("50")
            assertThat(config.aperture).isEqualTo("2.8")
            assertThat(config.shutterSpeed).isEqualTo("1/125")
            assertThat(config.iso).isEqualTo("400")
        }
    }

    @Nested
    @DisplayName("hasExifOverrides")
    inner class HasExifOverrides {
        @Test
        fun `should return false for default configuration`() {
            val config = PhotoScanConfiguration()
            assertThat(config.hasExifOverrides()).isFalse()
        }

        @Test
        fun `should return true when description is set`() {
            val config = PhotoScanConfiguration(description = "Photo")
            assertThat(config.hasExifOverrides()).isTrue()
        }

        @Test
        fun `should return true when keywords is set`() {
            val config = PhotoScanConfiguration(keywords = "vacation")
            assertThat(config.hasExifOverrides()).isTrue()
        }

        @Test
        fun `should return true when originalDate is set`() {
            val config = PhotoScanConfiguration(originalDate = "2024:06:15 12:30:00")
            assertThat(config.hasExifOverrides()).isTrue()
        }

        @Test
        fun `should return true when cameraMake is set`() {
            val config = PhotoScanConfiguration(cameraMake = "Canon")
            assertThat(config.hasExifOverrides()).isTrue()
        }

        @Test
        fun `should return true when cameraModel is set`() {
            val config = PhotoScanConfiguration(cameraModel = "EOS R5")
            assertThat(config.hasExifOverrides()).isTrue()
        }

        @Test
        fun `should return true when focalLength is set`() {
            val config = PhotoScanConfiguration(focalLength = "50mm")
            assertThat(config.hasExifOverrides()).isTrue()
        }

        @Test
        fun `should return true when iso is set`() {
            val config = PhotoScanConfiguration(iso = "200")
            assertThat(config.hasExifOverrides()).isTrue()
        }

        @Test
        fun `should return true when copyOriginalExif is false`() {
            val config = PhotoScanConfiguration(copyOriginalExif = false)
            assertThat(config.hasExifOverrides()).isTrue()
        }
    }

    @Nested
    @DisplayName("convenience methods")
    inner class ConvenienceMethods {
        @Test
        fun `keywordList should parse comma-separated keywords`() {
            val config = PhotoScanConfiguration(keywords = "vacation, family, holiday")
            assertThat(config.keywordList()).containsExactly("vacation", "family", "holiday")
        }

        @Test
        fun `keywordList should trim whitespace`() {
            val config = PhotoScanConfiguration(keywords = " vacation , family , holiday ")
            assertThat(config.keywordList()).containsExactly("vacation", "family", "holiday")
        }

        @Test
        fun `subjectList should parse comma-separated subjects`() {
            val config = PhotoScanConfiguration(subjects = "Alice, Bob")
            assertThat(config.subjectList()).containsExactly("Alice", "Bob")
        }

        @Test
        fun `locationDisplay should join location fields`() {
            val config =
                PhotoScanConfiguration(
                    locationName = "Grandma's house",
                    city = "Worcester",
                    state = "MA",
                )
            assertThat(config.locationDisplay()).isEqualTo("Grandma's house, Worcester, MA")
        }

        @Test
        fun `hasGpsCoordinates should return true when both lat and lon are set`() {
            val config = PhotoScanConfiguration(gpsLatitude = "42.2626", gpsLongitude = "-71.8023")
            assertThat(config.hasGpsCoordinates()).isTrue()
        }

        @Test
        fun `hasGpsCoordinates should return false when only lat is set`() {
            val config = PhotoScanConfiguration(gpsLatitude = "42.2626")
            assertThat(config.hasGpsCoordinates()).isFalse()
        }

        @Test
        fun `cycleRotationCW should cycle 0 to 90`() {
            val config = PhotoScanConfiguration(rotationDegrees = 0)
            assertThat(config.cycleRotationCW().rotationDegrees).isEqualTo(90)
        }

        @Test
        fun `cycleRotationCCW should cycle 0 to 270`() {
            val config = PhotoScanConfiguration(rotationDegrees = 0)
            assertThat(config.cycleRotationCCW().rotationDegrees).isEqualTo(270)
        }
    }

    @Nested
    @DisplayName("copy")
    inner class Copy {
        @Test
        fun `copy should preserve all fields`() {
            val original =
                PhotoScanConfiguration(
                    originalDate = "2024-01-15",
                    year = "2024",
                    keywords = "test, sample",
                    description = "Test note",
                )

            val copy = original.copy()

            assertThat(copy.originalDate).isEqualTo(original.originalDate)
            assertThat(copy.year).isEqualTo(original.year)
            assertThat(copy.keywords).isEqualTo(original.keywords)
            assertThat(copy.description).isEqualTo(original.description)
        }

        @Test
        fun `copy with overrides should apply changes`() {
            val original = PhotoScanConfiguration(keywords = "original")

            val modified = original.copy(keywords = "modified")

            assertThat(original.keywords).isEqualTo("original")
            assertThat(modified.keywords).isEqualTo("modified")
        }
    }
}

/** Unit tests for PhotoScanProfile configuration integration. */
@DisplayName("PhotoScanProfile Configuration")
class PhotoScanProfileConfigurationTest {

    @Nested
    @DisplayName("naming patterns")
    inner class NamingPatterns {
        @Test
        fun `default profile should have default naming pattern`() {
            val profile = PhotoScanProfile.createDefault()

            assertThat(profile.namingPattern).isEqualTo("{original}")
        }

        @Test
        fun `custom profile can override naming pattern`() {
            val profile = PhotoScanProfile(name = "Custom", namingPattern = "{date}_{original}")

            assertThat(profile.namingPattern).isEqualTo("{date}_{original}")
        }

        @Test
        fun `naming pattern can include counter`() {
            val profile =
                PhotoScanProfile(name = "Counter Test", namingPattern = "{original}_{counter:3}")

            assertThat(profile.namingPattern).isEqualTo("{original}_{counter:3}")
        }
    }

    @Nested
    @DisplayName("output formats")
    inner class OutputFormats {
        @Test
        fun `all formats have valid extensions`() {
            PhotoOutputFormat.entries.forEach { format ->
                assertThat(format.extension).isNotEmpty()
                assertThat(format.extension).doesNotContain(".")
            }
        }

        @Test
        fun `JPEG formats have increasing quality`() {
            val jpegFormats =
                PhotoOutputFormat.entries
                    .filter { it.name.contains("JPEG") }
                    .sortedBy { it.quality }

            assertThat(jpegFormats).hasSizeGreaterThan(1)
            for (i in 0 until jpegFormats.size - 1) {
                assertThat(jpegFormats[i].quality).isLessThan(jpegFormats[i + 1].quality)
            }
        }

        @Test
        fun `lossless formats have quality 100`() {
            val losslessFormats = listOf(PhotoOutputFormat.PNG, PhotoOutputFormat.TIFF)

            losslessFormats.forEach { format -> assertThat(format.quality).isEqualTo(100) }
        }
    }

    @Nested
    @DisplayName("aspect ratio presets")
    inner class AspectRatioPresets {
        @Test
        fun `ORIGINAL has zero value`() {
            val preset = AspectRatioPreset.ORIGINAL

            assertThat(preset.value).isEqualTo(0.0)
        }

        @Test
        fun `SQUARE has 1_1 ratio`() {
            val preset = AspectRatioPreset.SQUARE

            assertThat(preset.value).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.001))
        }

        @Test
        fun `LANDSCAPE_3_2 has 3_2 ratio`() {
            val preset = AspectRatioPreset.LANDSCAPE_3_2

            assertThat(preset.value).isCloseTo(1.5, org.assertj.core.data.Offset.offset(0.001))
        }

        @Test
        fun `all presets have print sizes`() {
            AspectRatioPreset.entries.forEach { preset ->
                assertThat(preset.printSize).isNotEmpty()
            }
        }

        @Test
        fun `all presets have display names`() {
            AspectRatioPreset.entries.forEach { preset ->
                assertThat(preset.displayName).isNotEmpty()
            }
        }
    }

    @Nested
    @DisplayName("correction settings")
    inner class CorrectionSettingsTests {
        @Test
        fun `default correction settings are sensible`() {
            val settings = CorrectionSettings()

            assertThat(settings.enablePerspectiveCorrection).isTrue()
            assertThat(settings.enableRotationCorrection).isFalse()
            assertThat(settings.correctionStrategy).isNull()
        }

        @Test
        fun `all correction strategies are valid`() {
            assertThat(CorrectionStrategy.entries).hasSize(3)
            assertThat(CorrectionStrategy.CROP).isNotNull()
            assertThat(CorrectionStrategy.CROP_AND_ROTATE).isNotNull()
            assertThat(CorrectionStrategy.PERSPECTIVE).isNotNull()
        }

        @Test
        fun `correction settings can be fully configured`() {
            val settings =
                CorrectionSettings(
                    enablePerspectiveCorrection = true,
                    enableRotationCorrection = true,
                    correctionStrategy = CorrectionStrategy.PERSPECTIVE,
                )

            assertThat(settings.enablePerspectiveCorrection).isTrue()
            assertThat(settings.enableRotationCorrection).isTrue()
            assertThat(settings.correctionStrategy).isEqualTo(CorrectionStrategy.PERSPECTIVE)
        }
    }
}

/** Unit tests for ImportConfiguration Photo Scan integration. */
@DisplayName("ImportConfiguration Photo Scan Integration")
class ImportConfigurationPhotoScanTest {

    @Test
    fun `default import configuration has sensible defaults`() {
        val config = ImportConfiguration()

        assertThat(config.folderPattern).isNotEmpty()
        assertThat(config.preserveOriginalName).isTrue()
    }

    @Test
    fun `import configuration has conflict resolution defaults`() {
        val config = ImportConfiguration()

        assertThat(config.conflictResolution).isNotNull()
        assertThat(config.rawJpegPairMode).isNotNull()
    }
}
