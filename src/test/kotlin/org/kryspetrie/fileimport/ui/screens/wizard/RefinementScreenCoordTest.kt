package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.ui.geometry.Offset
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.kryspetrie.fileimport.infrastructure.wizard.BoundingBox
import org.kryspetrie.fileimport.infrastructure.wizard.BoundingBoxCorners
import org.kryspetrie.fileimport.infrastructure.wizard.Corner
import org.kryspetrie.fileimport.infrastructure.wizard.Point

/**
 * Unit tests for RefinementScreen coordinate transformations. Tests coordinate math that's critical
 * for correct box alignment with images.
 */
@DisplayName("RefinementScreen Coordinate Tests")
class RefinementScreenCoordTest {

  // ========== imageToScreen transformation tests ==========

  @Test
  @DisplayName("should transform bottom-right corner correctly")
  fun shouldTransformBottomRightCorner() {
    val point = Point(100.0, 200.0)
    val zoom = 2.0f
    val panX = 50f
    val panY = 50f

    val screen = imageToScreen(point, zoom, panX, panY)

    // 50 + 100 * 2 = 250, 50 + 200 * 2 = 450
    assertThat(screen.x).isEqualTo(250f)
    assertThat(screen.y).isEqualTo(450f)
  }

  @Test
  @DisplayName("should transform top-left corner correctly")
  fun shouldTransformTopLeftCorner() {
    val point = Point(0.0, 0.0)
    val zoom = 2.0f
    val panX = 50f
    val panY = 50f

    val screen = imageToScreen(point, zoom, panX, panY)

    assertThat(screen.x).isEqualTo(50f)
    assertThat(screen.y).isEqualTo(50f)
  }

  @Test
  @DisplayName("should handle zoom of 1.0")
  fun shouldHandleZoomOfOne() {
    val point = Point(100.0, 100.0)
    val zoom = 1.0f
    val panX = 0f
    val panY = 0f

    val screen = imageToScreen(point, zoom, panX, panY)

    assertThat(screen.x).isEqualTo(100f)
    assertThat(screen.y).isEqualTo(100f)
  }

  // ========== screenToImage transformation tests ==========

  @Test
  @DisplayName("should invert imageToScreen transformation")
  fun shouldInvertImageToScreen() {
    val original = Point(100.0, 200.0)
    val zoom = 2.0f
    val panX = 50f
    val panY = 50f

    // Transform to screen and back
    val screen = imageToScreen(original, zoom, panX, panY)
    val back = screenToImage(screen, zoom, panX, panY)

    assertThat(back.x).isCloseTo(original.x, org.assertj.core.data.Offset.offset(0.001))
    assertThat(back.y).isCloseTo(original.y, org.assertj.core.data.Offset.offset(0.001))
  }

  @Test
  @DisplayName("should handle zoom of 1.0 for screenToImage")
  fun shouldHandleZoomOfOneScreenToImage() {
    val screen = Offset(100f, 100f)
    val zoom = 1.0f
    val panX = 0f
    val panY = 0f

    val image = screenToImage(screen, zoom, panX, panY)

    assertThat(image.x).isEqualTo(100.0)
    assertThat(image.y).isEqualTo(100.0)
  }

  // ========== corner hit detection tests ==========

  @Test
  @DisplayName("should detect TOP_LEFT corner hit")
  fun shouldDetectTopLeftCornerHit() {
    val testBox =
        BoundingBox(
            corners =
                BoundingBoxCorners(
                    topLeft = Point(100.0, 100.0),
                    topRight = Point(300.0, 100.0),
                    bottomLeft = Point(100.0, 200.0),
                    bottomRight = Point(300.0, 200.0)))

    val zoom = 1.0f
    val panX = 0f
    val panY = 0f

    val hit = findCornerHit(Offset(100f, 100f), testBox, zoom, panX, panY)

    assertThat(hit).isEqualTo(Corner.TOP_LEFT)
  }

  @Test
  @DisplayName("should detect TOP_RIGHT corner hit")
  fun shouldDetectTopRightCornerHit() {
    val testBox =
        BoundingBox(
            corners =
                BoundingBoxCorners(
                    topLeft = Point(100.0, 100.0),
                    topRight = Point(300.0, 100.0),
                    bottomLeft = Point(100.0, 200.0),
                    bottomRight = Point(300.0, 200.0)))

    val hit = findCornerHit(Offset(300f, 100f), testBox, 1.0f, 0f, 0f)

    assertThat(hit).isEqualTo(Corner.TOP_RIGHT)
  }

  @Test
  @DisplayName("should detect BOTTOM_LEFT corner hit")
  fun shouldDetectBottomLeftCornerHit() {
    val testBox =
        BoundingBox(
            corners =
                BoundingBoxCorners(
                    topLeft = Point(100.0, 100.0),
                    topRight = Point(300.0, 100.0),
                    bottomLeft = Point(100.0, 200.0),
                    bottomRight = Point(300.0, 200.0)))

    val hit = findCornerHit(Offset(100f, 200f), testBox, 1.0f, 0f, 0f)

    assertThat(hit).isEqualTo(Corner.BOTTOM_LEFT)
  }

  @Test
  @DisplayName("should detect BOTTOM_RIGHT corner hit")
  fun shouldDetectBottomRightCornerHit() {
    val testBox =
        BoundingBox(
            corners =
                BoundingBoxCorners(
                    topLeft = Point(100.0, 100.0),
                    topRight = Point(300.0, 100.0),
                    bottomLeft = Point(100.0, 200.0),
                    bottomRight = Point(300.0, 200.0)))

    val hit = findCornerHit(Offset(300f, 200f), testBox, 1.0f, 0f, 0f)

    assertThat(hit).isEqualTo(Corner.BOTTOM_RIGHT)
  }

  @Test
  @DisplayName("should return null when click outside hit radius")
  fun shouldReturnNullOutsideHitRadius() {
    val testBox =
        BoundingBox(
            corners =
                BoundingBoxCorners(
                    topLeft = Point(100.0, 100.0),
                    topRight = Point(300.0, 100.0),
                    bottomLeft = Point(100.0, 200.0),
                    bottomRight = Point(300.0, 200.0)))

    val hit = findCornerHit(Offset(200f, 150f), testBox, 1.0f, 0f, 0f)

    assertThat(hit).isNull()
  }

  @Test
  @DisplayName("should account for pan offset in hit detection")
  fun shouldAccountForPanOffset() {
    val testBox =
        BoundingBox(
            corners =
                BoundingBoxCorners(
                    topLeft = Point(100.0, 100.0),
                    topRight = Point(300.0, 100.0),
                    bottomLeft = Point(100.0, 200.0),
                    bottomRight = Point(300.0, 200.0)))

    // Corner at (100, 100), appears at (150, 150) with pan (50, 50)
    val hit = findCornerHit(Offset(150f, 150f), testBox, 1.0f, 50f, 50f)

    assertThat(hit).isEqualTo(Corner.TOP_LEFT)
  }

  @Test
  @DisplayName("should scale hit detection with zoom")
  fun shouldScaleHitDetectionWithZoom() {
    val testBox =
        BoundingBox(
            corners =
                BoundingBoxCorners(
                    topLeft = Point(100.0, 100.0),
                    topRight = Point(300.0, 100.0),
                    bottomLeft = Point(100.0, 200.0),
                    bottomRight = Point(300.0, 200.0)))

    // With 2x zoom, corner at (100, 100) appears at (200, 200)
    val hit = findCornerHit(Offset(200f, 200f), testBox, 2.0f, 0f, 0f)

    assertThat(hit).isEqualTo(Corner.TOP_LEFT)
  }

  // ========== sampled image tests ==========

  @Test
  @DisplayName("should create sampled image at correct size")
  fun shouldCreateSampledImageAtCorrectSize() {
    val image = java.awt.image.BufferedImage(800, 600, java.awt.image.BufferedImage.TYPE_INT_RGB)
    val scale = 0.5

    val sampled = createSampledImageForRefinement(image, scale)

    assertThat(sampled).isNotNull()
    assertThat(sampled!!.width).isEqualTo(400)
    assertThat(sampled.height).isEqualTo(300)
  }

  @Test
  @DisplayName("should clamp minimum size to 100")
  fun shouldClampMinimumSize() {
    val image = java.awt.image.BufferedImage(100, 100, java.awt.image.BufferedImage.TYPE_INT_RGB)
    val scale = 0.1

    val sampled = createSampledImageForRefinement(image, scale)

    assertThat(sampled!!.width).isEqualTo(100)
    assertThat(sampled!!.height).isEqualTo(100)
  }
}