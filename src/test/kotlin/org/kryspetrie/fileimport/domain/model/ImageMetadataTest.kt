package org.kryspetrie.fileimport.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*

@DisplayName("ImageMetadata")
class ImageMetadataTest {

  @Nested
  @DisplayName("derived properties")
  inner class DerivedProperties {
    @Test
    @DisplayName("should combine camera model from make and model")
    fun shouldCombineCameraModel() {
      val metadata = ImageMetadata(make = "Canon", model = "EOS R5")
      assertThat(metadata.cameraModel).isEqualTo("Canon EOS R5")
    }

    @Test
    @DisplayName("should handle missing make/model gracefully")
    fun shouldHandleMissingMakeModel() {
      assertThat(ImageMetadata().cameraModel).isEmpty()
      assertThat(ImageMetadata(make = "Canon").cameraModel).isEqualTo("Canon")
      assertThat(ImageMetadata(model = "R5").cameraModel).isEqualTo("R5")
    }

    @Test
    @DisplayName("should detect GPS data presence")
    fun shouldDetectGpsData() {
      assertThat(ImageMetadata(latitude = 40.0, longitude = -74.0).hasGpsData).isTrue()
      assertThat(ImageMetadata(latitude = 40.0).hasGpsData).isFalse()
      assertThat(ImageMetadata().hasGpsData).isFalse()
    }

    @Test
    @DisplayName("should format resolution string")
    fun shouldFormatResolution() {
      assertThat(ImageMetadata(imageWidth = 4000, imageHeight = 3000).resolution)
          .isEqualTo("4000\u00D73000")
      assertThat(ImageMetadata().resolution).isNull()
      assertThat(ImageMetadata(imageWidth = 4000).resolution).isNull()
    }

    @Test
    @DisplayName("should return lens info or Unknown")
    fun shouldReturnLensInfo() {
      assertThat(ImageMetadata(lensModel = "RF 50mm F1.2L").lensInfo).isEqualTo("RF 50mm F1.2L")
      assertThat(ImageMetadata().lensInfo).isEqualTo("Unknown")
    }

    @Test
    @DisplayName("should return cameraMake or Unknown")
    fun shouldReturnCameraMake() {
      assertThat(ImageMetadata(make = "Nikon").cameraMake).isEqualTo("Nikon")
      assertThat(ImageMetadata().cameraMake).isEqualTo("Unknown")
    }
  }

  @Nested
  @DisplayName("video duration formatting")
  inner class DurationFormatting {
    @Test
    @DisplayName("should format short duration")
    fun shouldFormatShortDuration() {
      val metadata = ImageMetadata(durationSeconds = 45.0)
      assertThat(metadata.durationFormatted).isEqualTo("0:45")
    }

    @Test
    @DisplayName("should format minutes and seconds")
    fun shouldFormatMinutesAndSeconds() {
      val metadata = ImageMetadata(durationSeconds = 125.0)
      assertThat(metadata.durationFormatted).isEqualTo("2:05")
    }

    @Test
    @DisplayName("should format hours")
    fun shouldFormatHours() {
      val metadata = ImageMetadata(durationSeconds = 3665.0)
      assertThat(metadata.durationFormatted).isEqualTo("1:01:05")
    }

    @Test
    @DisplayName("should return null for no duration")
    fun shouldReturnNullForNoDuration() {
      assertThat(ImageMetadata().durationFormatted).isNull()
    }
  }
}
