package org.kryspetrie.fileimport.infrastructure.wizard

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Unit tests for PhotoScanConstants.
 *
 * Tests that constants have expected values and documentation is accurate.
 */
class PhotoScanConstantsTest {

  // ==================== Box Creation Constants ====================

  @Test
  fun `MIN_BOX_SIZE_PIXELS is positive`() {
    assertTrue(PhotoScanConstants.MIN_BOX_SIZE_PIXELS > 0)
  }

  @Test
  fun `DEFAULT_BOX_WIDTH_RATIO is between 0 and 1`() {
    assertTrue(PhotoScanConstants.DEFAULT_BOX_WIDTH_RATIO > 0)
    assertTrue(PhotoScanConstants.DEFAULT_BOX_WIDTH_RATIO <= 1)
  }

  @Test
  fun `DEFAULT_BOX_ASPECT_RATIO is positive`() {
    assertTrue(PhotoScanConstants.DEFAULT_BOX_ASPECT_RATIO > 0)
  }

  @Test
  fun `DEFAULT_BOX_ASPECT_RATIO corresponds to 3_2 ratio`() {
    // 3:2 = 1.5
    assertEquals(1.5, PhotoScanConstants.DEFAULT_BOX_ASPECT_RATIO, 0.01)
  }

  // ==================== Export Constants ====================

  @Test
  fun `ESTIMATED_SPACE_PER_PHOTO_BYTES is positive`() {
    assertTrue(PhotoScanConstants.ESTIMATED_SPACE_PER_PHOTO_BYTES > 0)
  }

  @Test
  fun `ESTIMATED_SPACE_PER_PHOTO_BYTES is at least 10MB`() {
    val minBytes = 10L * 1024 * 1024
    assertTrue(PhotoScanConstants.ESTIMATED_SPACE_PER_PHOTO_BYTES >= minBytes)
  }

  @Test
  fun `ESTIMATED_SPACE_PER_PHOTO_BYTES corresponds to 50MB`() {
    val expected = 50L * 1024 * 1024
    assertEquals(expected, PhotoScanConstants.ESTIMATED_SPACE_PER_PHOTO_BYTES)
  }

  @Test
  fun `MIN_FREE_SPACE_WARNING_PERCENT is greater than 1`() {
    assertTrue(PhotoScanConstants.MIN_FREE_SPACE_WARNING_PERCENT > 1.0)
  }

  @Test
  fun `DEFAULT_JPEG_QUALITY is between 0 and 1`() {
    assertTrue(PhotoScanConstants.DEFAULT_JPEG_QUALITY >= 0f)
    assertTrue(PhotoScanConstants.DEFAULT_JPEG_QUALITY <= 1f)
  }

  // ==================== Zoom Constants ====================

  @Test
  fun `MIN_ZOOM_LEVEL is positive`() {
    assertTrue(PhotoScanConstants.MIN_ZOOM_LEVEL > 0)
  }

  @Test
  fun `MAX_ZOOM_LEVEL is greater than MIN`() {
    assertTrue(PhotoScanConstants.MAX_ZOOM_LEVEL > PhotoScanConstants.MIN_ZOOM_LEVEL)
  }

  @Test
  fun `ZOOM_STEP is positive`() {
    assertTrue(PhotoScanConstants.ZOOM_STEP > 1.0)
  }

  @Test
  fun `KEYBOARD_ZOOM_STEP is positive`() {
    assertTrue(PhotoScanConstants.KEYBOARD_ZOOM_STEP > 0)
  }

  // ==================== UI Constants ====================

  @Test
  fun `CORNER_HIT_RADIUS_PX is positive`() {
    assertTrue(PhotoScanConstants.CORNER_HIT_RADIUS_PX > 0)
  }

  @Test
  fun `DEFAULT_VIEWPORT_WIDTH is positive`() {
    assertTrue(PhotoScanConstants.DEFAULT_VIEWPORT_WIDTH > 0)
  }

  @Test
  fun `DEFAULT_VIEWPORT_HEIGHT is positive`() {
    assertTrue(PhotoScanConstants.DEFAULT_VIEWPORT_HEIGHT > 0)
  }

  // ==================== Keyboard Constants ====================

  @Test
  fun `ARROW_KEY_STEP is positive`() {
    assertTrue(PhotoScanConstants.ARROW_KEY_STEP > 0)
  }

  @Test
  fun `SHIFT_ARROW_KEY_STEP is greater than normal step`() {
    assertTrue(PhotoScanConstants.SHIFT_ARROW_KEY_STEP > PhotoScanConstants.ARROW_KEY_STEP)
  }

  @Test
  fun `ROTATION_STEP_DEGREES is positive`() {
    assertTrue(PhotoScanConstants.ROTATION_STEP_DEGREES > 0)
  }

  @Test
  fun `EXPANSION_FACTOR is positive`() {
    assertTrue(PhotoScanConstants.EXPANSION_FACTOR > 0)
  }

  @Test
  fun `EXPANSION_FACTOR is greater than 1 for expansion`() {
    assertTrue(PhotoScanConstants.EXPANSION_FACTOR > 1.0)
  }

  // ==================== Image Processing Constants ====================

  @Test
  fun `MAX_PREVIEW_DIMENSION is reasonable size`() {
    // Should be at least 500 but not more than 10000
    assertTrue(PhotoScanConstants.MAX_PREVIEW_DIMENSION >= 500)
    assertTrue(PhotoScanConstants.MAX_PREVIEW_DIMENSION <= 10000)
  }

  // ==================== Persistence Constants ====================

  @Test
  fun `MAX_RECENT_DESTINATIONS is reasonable`() {
    assertTrue(PhotoScanConstants.MAX_RECENT_DESTINATIONS > 0)
    assertTrue(PhotoScanConstants.MAX_RECENT_DESTINATIONS <= 20)
  }

  @Test
  fun `MAX_UNDO_OPERATIONS is positive`() {
    assertTrue(PhotoScanConstants.MAX_UNDO_OPERATIONS > 0)
  }

  // ==================== Consistency Tests ====================

  @Test
  fun `default box size calculation produces valid box`() {
    val imageWidth = 1000.0
    val width = imageWidth * PhotoScanConstants.DEFAULT_BOX_WIDTH_RATIO
    val height = width / PhotoScanConstants.DEFAULT_BOX_ASPECT_RATIO

    // Box should be at least MIN_BOX_SIZE_PIXELS
    assertTrue(width >= PhotoScanConstants.MIN_BOX_SIZE_PIXELS)
    assertTrue(height >= PhotoScanConstants.MIN_BOX_SIZE_PIXELS)

    // For a 1000px wide image, box should be 300px wide (30%) and 200px tall (3:2)
    assertEquals(300.0, width, 0.01)
    assertEquals(200.0, height, 0.01)
  }

  @Test
  fun `small image should reject box creation`() {
    val imageWidth = 100.0 // Very small image
    val width = imageWidth * PhotoScanConstants.DEFAULT_BOX_WIDTH_RATIO

    // Box would be 30px, which is below MIN_BOX_SIZE_PIXELS
    assertTrue(width < PhotoScanConstants.MIN_BOX_SIZE_PIXELS)
  }

  @Test
  fun `typical image should allow box creation`() {
    val imageWidth = 2000.0 // Typical photo size
    val width = imageWidth * PhotoScanConstants.DEFAULT_BOX_WIDTH_RATIO
    val height = width / PhotoScanConstants.DEFAULT_BOX_ASPECT_RATIO

    // Box should be 600x400, well above minimum
    assertTrue(width >= PhotoScanConstants.MIN_BOX_SIZE_PIXELS)
    assertTrue(height >= PhotoScanConstants.MIN_BOX_SIZE_PIXELS)
  }
}
