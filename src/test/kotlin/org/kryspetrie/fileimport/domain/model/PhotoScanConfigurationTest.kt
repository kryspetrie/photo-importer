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
    fun `should have null date overrides by default`() {
      val config = PhotoScanConfiguration()

      assertThat(config.originalDateOverride).isNull()
      assertThat(config.originalYearOverride).isNull()
      assertThat(config.originalMonthOverride).isNull()
    }

    @Test
    fun `should have empty tags and notes by default`() {
      val config = PhotoScanConfiguration()

      assertThat(config.tags).isEmpty()
      assertThat(config.notes).isEmpty()
    }
  }

  @Nested
  @DisplayName("date overrides")
  inner class DateOverrides {
    @Test
    fun `should allow setting date override`() {
      val config = PhotoScanConfiguration(originalDateOverride = "2024-01-15")

      assertThat(config.originalDateOverride).isEqualTo("2024-01-15")
    }

    @Test
    fun `should allow setting year override`() {
      val config = PhotoScanConfiguration(originalYearOverride = "2023")

      assertThat(config.originalYearOverride).isEqualTo("2023")
    }

    @Test
    fun `should allow setting month override`() {
      val config = PhotoScanConfiguration(originalMonthOverride = "06")

      assertThat(config.originalMonthOverride).isEqualTo("06")
    }

    @Test
    fun `should allow setting all date overrides together`() {
      val config =
          PhotoScanConfiguration(
              originalDateOverride = "2024-06-15",
              originalYearOverride = "2023",
              originalMonthOverride = "03")

      assertThat(config.originalDateOverride).isEqualTo("2024-06-15")
      assertThat(config.originalYearOverride).isEqualTo("2023")
      assertThat(config.originalMonthOverride).isEqualTo("03")
    }
  }

  @Nested
  @DisplayName("metadata fields")
  inner class MetadataFields {
    @Test
    fun `should allow setting tags`() {
      val config = PhotoScanConfiguration(tags = "vacation, family, summer")

      assertThat(config.tags).isEqualTo("vacation, family, summer")
    }

    @Test
    fun `should allow setting notes`() {
      val config = PhotoScanConfiguration(notes = "Summer vacation 2024")

      assertThat(config.notes).isEqualTo("Summer vacation 2024")
    }

    @Test
    fun `should allow setting both tags and notes`() {
      val config =
          PhotoScanConfiguration(tags = "document, receipt", notes = "Important tax document")

      assertThat(config.tags).isEqualTo("document, receipt")
      assertThat(config.notes).isEqualTo("Important tax document")
    }
  }

  @Nested
  @DisplayName("copy")
  inner class Copy {
    @Test
    fun `copy should preserve all fields`() {
      val original =
          PhotoScanConfiguration(
              originalDateOverride = "2024-01-15",
              originalYearOverride = "2024",
              originalMonthOverride = "01",
              tags = "test, sample",
              notes = "Test note")

      val copy = original.copy()

      assertThat(copy.originalDateOverride).isEqualTo(original.originalDateOverride)
      assertThat(copy.originalYearOverride).isEqualTo(original.originalYearOverride)
      assertThat(copy.originalMonthOverride).isEqualTo(original.originalMonthOverride)
      assertThat(copy.tags).isEqualTo(original.tags)
      assertThat(copy.notes).isEqualTo(original.notes)
    }

    @Test
    fun `copy with overrides should apply changes`() {
      val original = PhotoScanConfiguration(tags = "original")

      val modified = original.copy(tags = "modified")

      assertThat(original.tags).isEqualTo("original")
      assertThat(modified.tags).isEqualTo("modified")
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
          PhotoOutputFormat.entries.filter { it.name.contains("JPEG") }.sortedBy { it.quality }

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
      AspectRatioPreset.entries.forEach { preset -> assertThat(preset.printSize).isNotEmpty() }
    }

    @Test
    fun `all presets have display names`() {
      AspectRatioPreset.entries.forEach { preset -> assertThat(preset.displayName).isNotEmpty() }
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
      assertThat(settings.perspectiveMode).isEqualTo(PerspectiveMode.AUTO)
    }

    @Test
    fun `all perspective modes are valid`() {
      assertThat(PerspectiveMode.entries).hasSize(3)
      assertThat(PerspectiveMode.AUTO).isNotNull()
      assertThat(PerspectiveMode.MANUAL).isNotNull()
      assertThat(PerspectiveMode.DISABLED).isNotNull()
    }

    @Test
    fun `correction settings can be fully configured`() {
      val settings =
          CorrectionSettings(
              enablePerspectiveCorrection = true,
              enableRotationCorrection = true,
              perspectiveMode = PerspectiveMode.MANUAL)

      assertThat(settings.enablePerspectiveCorrection).isTrue()
      assertThat(settings.enableRotationCorrection).isTrue()
      assertThat(settings.perspectiveMode).isEqualTo(PerspectiveMode.MANUAL)
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
