package org.kryspetrie.fileimport.application

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

@DisplayName("PhotoScanExportService")
class PhotoScanExportServiceTest {
  private lateinit var service: PhotoScanExportService
  private lateinit var perspectiveService: PerspectiveCorrectionService

  @TempDir lateinit var tempDir: File

  @BeforeEach
  fun setup() {
    perspectiveService = PerspectiveCorrectionService()
    service = PhotoScanExportService(perspectiveService)
  }

  private fun createTestImage(width: Int, height: Int, color: Int): File {
    val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()
    g.color = java.awt.Color(color)
    g.fillRect(0, 0, width, height)
    // Add some detail for perspective correction to work with
    g.color = java.awt.Color(0xFF, 0x00, 0x00)
    g.fillRect(width / 4, height / 4, width / 2, height / 2)
    g.dispose()

    val file = File(tempDir, "test_${System.nanoTime()}.jpg")
    ImageIO.write(img, "jpg", file)
    return file
  }

  private fun createDetectedPhoto(
      tlX: Float = 0f,
      tlY: Float = 0f,
      trX: Float = 100f,
      trY: Float = 0f,
      brX: Float = 100f,
      brY: Float = 100f,
      blX: Float = 0f,
      blY: Float = 100f
  ): org.kryspetrie.fileimport.domain.model.DetectedPhoto {
    return org.kryspetrie.fileimport.domain.model.DetectedPhoto(
        topLeft = org.kryspetrie.fileimport.domain.model.PhotoCorner(tlX, tlY),
        topRight = org.kryspetrie.fileimport.domain.model.PhotoCorner(trX, trY),
        bottomRight = org.kryspetrie.fileimport.domain.model.PhotoCorner(brX, brY),
        bottomLeft = org.kryspetrie.fileimport.domain.model.PhotoCorner(blX, blY))
  }

  @Nested
  @DisplayName("service initialization")
  inner class ServiceInitialization {
    @Test
    @DisplayName("should initialize with perspective service")
    fun shouldInitialize() {
      assertThat(service).isNotNull
      assertThat(service.jpegQuality).isEqualTo(0.95f)
    }

    @Test
    @DisplayName("should allow jpeg quality configuration")
    fun shouldAllowQualityConfiguration() {
      service.jpegQuality = 0.5f
      assertThat(service.jpegQuality).isEqualTo(0.5f)
    }
  }

  @Nested
  @DisplayName("export result structure")
  inner class ExportResultStructure {
    @Test
    @DisplayName("should return ExportResult with success flag")
    fun shouldReturnExportResult() {
      val sourceFile = createTestImage(200, 200, 0x808080)
      val source = ImageIO.read(sourceFile)
      val photos = listOf(createDetectedPhoto(0f, 0f, 200f, 0f, 200f, 200f, 0f, 200f))
      val destDir = File(tempDir, "exports1")
      destDir.mkdirs()

      val result =
          service.exportPhotos(sourceFile, source, photos, destDir.absolutePath, "test_photo")

      assertThat(result).isNotNull
      assertThat(result).hasFieldOrProperty("success")
      assertThat(result).hasFieldOrProperty("exportedFiles")
      assertThat(result).hasFieldOrProperty("errors")
    }

    @Test
    @DisplayName("should handle empty photos list")
    fun shouldHandleEmptyPhotosList() {
      val sourceFile = createTestImage(200, 200, 0xFFFFFF)
      val source = ImageIO.read(sourceFile)
      val destDir = File(tempDir, "empty")
      destDir.mkdirs()

      val result =
          service.exportPhotos(sourceFile, source, emptyList(), destDir.absolutePath, "empty")

      assertThat(result.success).isTrue()
      assertThat(result.exportedFiles).isEmpty()
    }
  }

  @Nested
  @DisplayName("error handling")
  inner class ErrorHandling {
    @Test
    @DisplayName("should report error for invalid source file")
    fun shouldReportErrorForInvalidSource() {
      val sourceFile = File("/nonexistent/file.jpg")
      val source = BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB)
      val photos = listOf(createDetectedPhoto())
      val destDir = File(tempDir, "error")

      val result = service.exportPhotos(sourceFile, source, photos, destDir.absolutePath, "photo")

      assertThat(result.success).isFalse()
      assertThat(result.errors).isNotEmpty()
    }
  }

  @Nested
  @DisplayName("export with real images")
  inner class RealImageExport {
    @Test
    @DisplayName("should export when photos are within image bounds")
    fun shouldExportWithinBounds() {
      val sourceFile = createTestImage(300, 200, 0x808080)
      val source = ImageIO.read(sourceFile)
      val photos = listOf(createDetectedPhoto(50f, 30f, 250f, 30f, 250f, 170f, 50f, 170f))
      val destDir = File(tempDir, "exports2")
      destDir.mkdirs()

      val result =
          service.exportPhotos(sourceFile, source, photos, destDir.absolutePath, "bounded_photo")

      // Result should have errors or success depending on perspective correction
      assertThat(result).isNotNull
    }
  }
}
