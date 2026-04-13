package org.kryspetrie.fileimport.domain.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Unit tests for CorrectionSettings, PhotoOutputFormat, and AspectRatioPreset. */
class PhotoScanSettingsTest {

  // ==================== CorrectionSettings Tests ====================

  @Test
  fun `default correction settings enables perspective only`() {
    val settings = CorrectionSettings()

    assertTrue(settings.enablePerspectiveCorrection)
    assertFalse(settings.enableRotationCorrection)
    assertEquals(PerspectiveMode.AUTO, settings.perspectiveMode)
  }

  @Test
  fun `correction settings can disable both`() {
    val settings =
        CorrectionSettings(enablePerspectiveCorrection = false, enableRotationCorrection = false)

    assertFalse(settings.enablePerspectiveCorrection)
    assertFalse(settings.enableRotationCorrection)
  }

  @Test
  fun `correction settings can enable both`() {
    val settings =
        CorrectionSettings(enablePerspectiveCorrection = true, enableRotationCorrection = true)

    assertTrue(settings.enablePerspectiveCorrection)
    assertTrue(settings.enableRotationCorrection)
  }

  @Test
  fun `perspective mode can be set explicitly`() {
    val autoMode = CorrectionSettings(perspectiveMode = PerspectiveMode.AUTO)
    val manualMode = CorrectionSettings(perspectiveMode = PerspectiveMode.MANUAL)
    val disabledMode = CorrectionSettings(perspectiveMode = PerspectiveMode.DISABLED)

    assertEquals(PerspectiveMode.AUTO, autoMode.perspectiveMode)
    assertEquals(PerspectiveMode.MANUAL, manualMode.perspectiveMode)
    assertEquals(PerspectiveMode.DISABLED, disabledMode.perspectiveMode)
  }

  // ==================== PhotoOutputFormat Tests ====================

  @Test
  fun `all output formats have valid extension`() {
    PhotoOutputFormat.entries.forEach { format ->
      assertTrue(format.extension.isNotBlank())
      assertFalse(format.extension.startsWith("."))
    }
  }

  @Test
  fun `JPEG formats have quality below 100`() {
    PhotoOutputFormat.entries
        .filter { it.name.contains("JPEG") }
        .forEach { format -> assertTrue(format.quality < 100) }
  }

  @Test
  fun `PNG has maximum quality`() {
    val png = PhotoOutputFormat.PNG
    assertEquals(100, png.quality)
  }

  @Test
  fun `TIFF has maximum quality`() {
    val tiff = PhotoOutputFormat.TIFF
    assertEquals(100, tiff.quality)
  }

  @Test
  fun `JPEG_QUALITY_90 is the default`() {
    val default = PhotoOutputFormat.JPEG_QUALITY_90
    assertEquals(90, default.quality)
    assertEquals("jpg", default.extension)
  }

  @Test
  fun `all formats have descriptions`() {
    PhotoOutputFormat.entries.forEach { format -> assertTrue(format.description.isNotBlank()) }
  }

  @Test
  fun `format quality values are valid`() {
    PhotoOutputFormat.entries.forEach { format -> assertTrue(format.quality in 0..100) }
  }

  // ==================== AspectRatioPreset Tests ====================

  @Test
  fun `ORIGINAL has zero value`() {
    val original = AspectRatioPreset.ORIGINAL
    assertEquals(0.0, original.value)
  }

  @Test
  fun `SQUARE has 1_1 ratio`() {
    val square = AspectRatioPreset.SQUARE
    assertEquals(1.0, square.value, 0.001)
  }

  @Test
  fun `LANDSCAPE_3_2 is wider than tall`() {
    val preset = AspectRatioPreset.LANDSCAPE_3_2
    assertTrue(preset.value > 1.0)
    assertEquals(3.0 / 2.0, preset.value, 0.001)
  }

  @Test
  fun `PORTRAIT_4_3 is taller than wide`() {
    val preset = AspectRatioPreset.PORTRAIT_4_3
    assertTrue(preset.value < 1.0)
    assertEquals(3.0 / 4.0, preset.value, 0.001)
  }

  @Test
  fun `WIDE_16_9 is cinematic ratio`() {
    val preset = AspectRatioPreset.WIDE_16_9
    assertTrue(preset.value > 1.0)
    assertEquals(16.0 / 9.0, preset.value, 0.001)
  }

  @Test
  fun `PANORAMA_3_1 is very wide`() {
    val preset = AspectRatioPreset.PANORAMA_3_1
    assertTrue(preset.value > 2.0)
    assertEquals(3.0, preset.value, 0.001)
  }

  @Test
  fun `all presets have display names`() {
    AspectRatioPreset.entries.forEach { preset -> assertTrue(preset.displayName.isNotBlank()) }
  }

  @Test
  fun `all presets have print size descriptions`() {
    AspectRatioPreset.entries.forEach { preset -> assertTrue(preset.printSize.isNotBlank()) }
  }

  @Test
  fun `portrait presets have value less than 1`() {
    val portraitPresets =
        listOf(
            AspectRatioPreset.PORTRAIT_4_3,
            AspectRatioPreset.PORTRAIT_2_3,
            AspectRatioPreset.PORTRAIT_5_7,
            AspectRatioPreset.PORTRAIT_4_5)

    portraitPresets.forEach { preset ->
      assertTrue(preset.value < 1.0, "${preset.name} should have value < 1")
    }
  }

  @Test
  fun `landscape presets have value greater than 1`() {
    val landscapePresets =
        listOf(
            AspectRatioPreset.LANDSCAPE_3_2,
            AspectRatioPreset.LANDSCAPE_3_4,
            AspectRatioPreset.WIDE_16_9,
            AspectRatioPreset.PANORAMA_3_1)

    landscapePresets.forEach { preset ->
      assertTrue(preset.value > 1.0, "${preset.name} should have value > 1")
    }
  }

  // ==================== PerspectiveMode Tests ====================

  @Test
  fun `all perspective modes have values`() {
    assertEquals(0, PerspectiveMode.AUTO.ordinal)
    assertEquals(1, PerspectiveMode.MANUAL.ordinal)
    assertEquals(2, PerspectiveMode.DISABLED.ordinal)
  }

  @Test
  fun `perspective modes can be iterated`() {
    val modes = PerspectiveMode.entries
    assertEquals(3, modes.size)
    assertTrue(modes.contains(PerspectiveMode.AUTO))
    assertTrue(modes.contains(PerspectiveMode.MANUAL))
    assertTrue(modes.contains(PerspectiveMode.DISABLED))
  }

  // ==================== Integration Tests ====================

  @Test
  fun `PhotoScanProfile uses all settings correctly`() {
    val profile =
        PhotoScanProfile(
            name = "Test Profile",
            outputFormat = PhotoOutputFormat.JPEG_QUALITY_95,
            aspectRatioPreset = AspectRatioPreset.LANDSCAPE_3_2,
            correctionSettings =
                CorrectionSettings(
                    enablePerspectiveCorrection = true, enableRotationCorrection = true))

    assertEquals(PhotoOutputFormat.JPEG_QUALITY_95, profile.outputFormat)
    assertEquals(AspectRatioPreset.LANDSCAPE_3_2, profile.aspectRatioPreset)
    assertTrue(profile.correctionSettings.enablePerspectiveCorrection)
    assertTrue(profile.correctionSettings.enableRotationCorrection)
  }

  @Test
  fun `default profile uses sensible defaults`() {
    val profile = PhotoScanProfile.createDefault()

    // Default format should be good quality JPEG
    assertTrue(profile.outputFormat.quality >= 85)

    // Default aspect ratio should preserve original
    assertEquals(AspectRatioPreset.ORIGINAL, profile.aspectRatioPreset)

    // Default corrections
    assertTrue(profile.correctionSettings.enablePerspectiveCorrection)
    assertFalse(profile.correctionSettings.enableRotationCorrection)
  }

  @Test
  fun `document profile uses document-appropriate settings`() {
    val profile = PhotoScanProfile.createDocumentProfile()

    // Document should use standard document aspect ratio
    assertEquals(AspectRatioPreset.PORTRAIT_4_3, profile.aspectRatioPreset)

    // Documents often need both corrections
    assertTrue(profile.correctionSettings.enablePerspectiveCorrection)
    assertTrue(profile.correctionSettings.enableRotationCorrection)
  }

  @Test
  fun `photo album profile uses photo-appropriate settings`() {
    val profile = PhotoScanProfile.createPhotoAlbumProfile()

    // Photo album should use standard photo aspect ratio
    assertEquals(AspectRatioPreset.LANDSCAPE_3_2, profile.aspectRatioPreset)

    // Photo album should use high quality
    assertTrue(profile.outputFormat.quality >= 90)
  }
}
