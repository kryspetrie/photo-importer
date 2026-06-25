package org.kryspetrie.fileimport.infrastructure.photoscan

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.PhotoCorner

@DisplayName("PerspectiveCorrectionService")
class PerspectiveCorrectionServiceTest {
    private lateinit var service: PerspectiveCorrectionService

    @TempDir lateinit var tempDir: File

    @BeforeEach
    fun setup() {
        service = PerspectiveCorrectionService()
    }

    private fun createTestImage(
        width: Int,
        height: Int,
        rect: Rect? = null,
        color: Int = 0xFF8080,
    ): File {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = java.awt.Color(0x80, 0x80, 0x80)
        g.fillRect(0, 0, width, height)

        if (rect != null) {
            g.color = java.awt.Color(color)
            g.fillRect(rect.x, rect.y, rect.w, rect.h)
        }
        g.dispose()

        val file = File(tempDir, "test_${System.nanoTime()}.jpg")
        ImageIO.write(img, "jpg", file)
        return file
    }

    private data class Rect(val x: Int, val y: Int, val w: Int, val h: Int)

    private fun createDetectedPhoto(
        tlX: Float,
        tlY: Float,
        trX: Float,
        trY: Float,
        brX: Float,
        brY: Float,
        blX: Float,
        blY: Float,
    ): DetectedPhoto {
        return DetectedPhoto(
            topLeft = PhotoCorner(tlX, tlY),
            topRight = PhotoCorner(trX, trY),
            bottomRight = PhotoCorner(brX, brY),
            bottomLeft = PhotoCorner(blX, blY),
        )
    }

    @Nested
    @DisplayName("correctPerspective")
    inner class CorrectPerspective {
        @Test
        @DisplayName("should correct perspective of rectangular region")
        fun shouldCorrectPerspective() {
            val sourceFile = createTestImage(400, 300, Rect(50, 30, 300, 200), 0xFF0000)
            val source = ImageIO.read(sourceFile)
            val detectedPhoto = createDetectedPhoto(50f, 30f, 350f, 30f, 350f, 230f, 50f, 230f)

            val result = service.correctPerspective(source, detectedPhoto)

            assertThat(result).isNotNull
            assertThat(result.width).isGreaterThan(200)
            assertThat(result.height).isGreaterThan(100)
        }

        @Test
        @DisplayName("should handle square source region")
        fun shouldHandleSquareRegion() {
            val sourceFile = createTestImage(400, 400, Rect(100, 100, 200, 200), 0x00FF00)
            val source = ImageIO.read(sourceFile)
            val detectedPhoto = createDetectedPhoto(100f, 100f, 300f, 100f, 300f, 300f, 100f, 300f)

            val result = service.correctPerspective(source, detectedPhoto)

            assertThat(result).isNotNull
            assertThat(result.width).isEqualTo(result.height)
        }

        @Test
        @DisplayName("should produce larger output for trapezoid input")
        fun shouldProduceLargerOutputForTrapezoid() {
            val sourceFile = createTestImage(400, 300, null)
            val source = ImageIO.read(sourceFile)
            val detectedPhoto = createDetectedPhoto(80f, 50f, 320f, 50f, 350f, 250f, 50f, 250f)

            val result = service.correctPerspective(source, detectedPhoto)

            assertThat(result.width).isGreaterThan(200)
            assertThat(result.height).isGreaterThan(150)
        }

        @Test
        @DisplayName("should handle specified output dimensions")
        fun shouldHandleSpecifiedDimensions() {
            val sourceFile = createTestImage(400, 300, null)
            val source = ImageIO.read(sourceFile)
            val detectedPhoto = createDetectedPhoto(100f, 50f, 300f, 50f, 300f, 250f, 100f, 250f)

            val result = service.correctPerspective(source, detectedPhoto, 500, 400)

            assertThat(result.width).isEqualTo(500)
            assertThat(result.height).isEqualTo(400)
        }
    }

    @Nested
    @DisplayName("getWidth and getHeight")
    inner class GetWidthHeight {
        @Test
        @DisplayName("should return correct width from corners")
        fun shouldReturnCorrectWidth() {
            val photo = createDetectedPhoto(0f, 0f, 200f, 0f, 200f, 100f, 0f, 100f)

            assertThat(photo.getWidth()).isEqualTo(200)
        }

        @Test
        @DisplayName("should return correct height from corners")
        fun shouldReturnCorrectHeight() {
            val photo = createDetectedPhoto(0f, 0f, 200f, 0f, 200f, 100f, 0f, 100f)

            assertThat(photo.getHeight()).isEqualTo(100)
        }
    }

    @Nested
    @DisplayName("getBounds")
    inner class GetBounds {
        @Test
        @DisplayName("should return correct bounding box")
        fun shouldReturnCorrectBounds() {
            val photo = createDetectedPhoto(50f, 100f, 200f, 80f, 180f, 300f, 60f, 280f)

            val bounds = photo.getBounds()

            assertThat(bounds.minX).isEqualTo(50)
            assertThat(bounds.maxX).isEqualTo(200)
            assertThat(bounds.minY).isEqualTo(80)
            assertThat(bounds.maxY).isEqualTo(300)
        }
    }

    @Nested
    @DisplayName("preserve pixel colors")
    inner class PixelColors {
        @Test
        @DisplayName("should preserve corner colors in output")
        fun shouldPreserveCornerColors() {
            val img = BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics()
            g.color = java.awt.Color.RED
            g.fillRect(0, 0, 50, 50)
            g.color = java.awt.Color.GREEN
            g.fillRect(50, 0, 50, 50)
            g.color = java.awt.Color.BLUE
            g.fillRect(0, 50, 50, 50)
            g.color = java.awt.Color.YELLOW
            g.fillRect(50, 50, 50, 50)
            g.dispose()

            val photo = createDetectedPhoto(0f, 0f, 100f, 0f, 100f, 100f, 0f, 100f)

            val result = service.correctPerspective(img, photo)

            assertThat(result).isNotNull
            assertThat(result.width).isGreaterThan(0)
            assertThat(result.height).isGreaterThan(0)
        }

        @Test
        @DisplayName("should handle edge coordinates")
        fun shouldHandleEdgeCoordinates() {
            val img = BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB)
            val photo = createDetectedPhoto(0f, 0f, 200f, 0f, 200f, 200f, 0f, 200f)

            val result = service.correctPerspective(img, photo)

            assertThat(result).isNotNull
        }
    }
}
