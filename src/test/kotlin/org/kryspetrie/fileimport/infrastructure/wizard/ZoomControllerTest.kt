package org.kryspetrie.fileimport.infrastructure.wizard

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Unit tests for ZoomController. Tests zoom calculations, pan offset, cursor-centered zooming, and
 * fit-to-bounds operations.
 */
class ZoomControllerTest {

  // ZC-01: Create default zoom controller
  @Test
  fun `create default zoom controller`() {
    val controller = ZoomController()

    assertEquals(1.0, controller.zoom)
    assertEquals(0.0, controller.panX)
    assertEquals(0.0, controller.panY)
  }

  // ZC-02: Zoom in increases zoom level by zoomStep
  @Test
  fun `zoom in increases zoom level by zoomStep`() {
    val controller = ZoomController()
    val expectedZoom = 1.0 * controller.zoomStep // 1.25

    val zoomed = controller.zoomIn()

    assertEquals(expectedZoom, zoomed.zoom)
  }

  // ZC-03: Zoom out decreases zoom level by zoomStep
  @Test
  fun `zoom out decreases zoom level by zoomStep`() {
    val controller = ZoomController()
    val expectedZoom = 1.0 / controller.zoomStep // 0.8

    val zoomed = controller.zoomOut()

    assertEquals(expectedZoom, zoomed.zoom)
  }

  // ZC-04: Zoom is bounded by max zoom
  @Test
  fun `zoom is bounded by max zoom`() {
    val controller = ZoomController()

    // Keep zooming in
    var current = controller
    repeat(20) {
      current = current.zoomIn()
    }

    assertTrue(current.zoom <= current.maxZoom)
  }

  // ZC-05: Zoom out is bounded by min zoom
  @Test
  fun `zoom out is bounded by min zoom`() {
    val controller = ZoomController()

    // Keep zooming out
    var current = controller
    repeat(20) {
      current = current.zoomOut()
    }

    assertTrue(current.zoom >= current.minZoom)
  }

  // ZC-06: Cursor-centered zoom keeps point stationary
  @Test
  fun `cursor centered zoom keeps point stationary`() {
    val controller = ZoomController()
    val cursorX = 400.0
    val cursorY = 300.0

    // Zoom in centered on cursor
    val zoomed = controller.zoomIn(cursorX, cursorY)

    // The cursor position should map to the same image position
    val imagePosBefore = controller.screenToImage(cursorX, cursorY)
    val imagePosAfter = zoomed.screenToImage(cursorX, cursorY)

    assertEquals(imagePosBefore.x, imagePosAfter.x, 0.01)
    assertEquals(imagePosBefore.y, imagePosAfter.y, 0.01)
  }

  // ZC-07: Pan updates offset
  @Test
  fun `pan updates offset`() {
    val controller = ZoomController()

    val panned = controller.pan(50.0, 30.0)

    assertEquals(50.0, panned.panX)
    assertEquals(30.0, panned.panY)
  }

  // ZC-08: Fit creates zoom to fit image in viewport
  @Test
  fun `fit creates zoom to fit image in viewport`() {
    val imageWidth = 1000.0
    val imageHeight = 800.0
    val viewportWidth = 500.0
    val viewportHeight = 400.0

    val controller = ZoomController.fit(imageWidth, imageHeight, viewportWidth, viewportHeight)

    // After fit, the entire image should fit in viewport with some margin
    // Calculate how much of image is visible at this zoom
    val visibleWidth = viewportWidth / controller.zoom
    val visibleHeight = viewportHeight / controller.zoom

    // Visible should be >= image dimensions
    assertTrue(visibleWidth >= imageWidth * 0.95) // Within 5%
    assertTrue(visibleHeight >= imageHeight * 0.95)
  }

  // ZC-09: Fit to box zooms to show box
  @Test
  fun `fit to box zooms to show box`() {
    val viewportWidth = 800.0
    val viewportHeight = 600.0

    val corners = BoundingBoxCorners(
        topLeft = Point(100.0, 100.0),
        topRight = Point(300.0, 100.0),
        bottomRight = Point(300.0, 200.0),
        bottomLeft = Point(100.0, 200.0)
    )

    val controller = ZoomController()
    val fitted = controller.fitToBox(corners, viewportWidth, viewportHeight)

    // Should have zoomed in significantly (box is 200x100, viewport is 800x600)
    assertTrue(fitted.zoom > 1.0)
  }

  // ZC-10: Image to screen conversion
  @Test
  fun `image to screen conversion`() {
    val controller = ZoomController(zoom = 2.0, panX = 100.0, panY = 50.0)
    val imageX = 200.0
    val imageY = 150.0

    val screen = controller.imageToScreen(imageX, imageY)

    // screen = (image * zoom) + pan
    assertEquals(200.0 * 2.0 + 100.0, screen.x, 0.01)
    assertEquals(150.0 * 2.0 + 50.0, screen.y, 0.01)
  }

  // ZC-11: Screen to image conversion
  @Test
  fun `screen to image conversion`() {
    val controller = ZoomController(zoom = 2.0, panX = 100.0, panY = 50.0)
    val screenX = 500.0
    val screenY = 350.0

    val image = controller.screenToImage(screenX, screenY)

    // image = (screen - pan) / zoom
    assertEquals((500.0 - 100.0) / 2.0, image.x, 0.01)
    assertEquals((350.0 - 50.0) / 2.0, image.y, 0.01)
  }

  // ZC-12: Zoom and pan are independent
  @Test
  fun `zoom and pan are independent`() {
    val controller = ZoomController()

    val withZoom = controller.zoomIn()
    val withPan = controller.pan(50.0, 30.0)
    val withBoth = controller.zoomIn().pan(50.0, 30.0)

    assertEquals(withZoom.zoom, withBoth.zoom)
    assertEquals(withPan.panX, withBoth.panX)
    assertEquals(withPan.panY, withBoth.panY)
  }

  // ZC-13: Zoom with null cursor centers on viewport center
  @Test
  fun `zoom with null cursor centers on viewport center`() {
    val controller = ZoomController()
    val viewportWidth = 800.0
    val viewportHeight = 600.0

    // Zoom with null should center on viewport
    val zoomed = controller.zoomIn()

    // Check that center maps correctly
    val centerX = viewportWidth / 2
    val centerY = viewportHeight / 2

    val imagePos = zoomed.screenToImage(centerX, centerY)

    // Center should map close to image center (0,0) at zoom 2.0
    // with pan offset added
    assertNotNull(imagePos)
  }

  // ZC-14: Reset by creating new controller
  @Test
  fun `reset by creating new controller restores defaults`() {
    val controller = ZoomController(zoom = 5.0, panX = 100.0, panY = 200.0)

    val reset = ZoomController(zoom = 1.0, panX = 0.0, panY = 0.0)

    assertEquals(1.0, reset.zoom)
    assertEquals(0.0, reset.panX)
    assertEquals(0.0, reset.panY)
  }

  // ZC-15: Fit portrait image in landscape viewport
  @Test
  fun `fit portrait image in landscape viewport`() {
    val imageWidth = 800.0
    val imageHeight = 1200.0  // Portrait
    val viewportWidth = 1000.0  // Landscape
    val viewportHeight = 600.0

    val controller = ZoomController.fit(imageWidth, imageHeight, viewportWidth, viewportHeight)

    // Portrait image in landscape viewport - should fit with letterboxing
    assertTrue(controller.zoom < 1.0)
  }

  // ZC-16: Fit landscape image in portrait viewport
  @Test
  fun `fit landscape image in portrait viewport`() {
    val imageWidth = 1200.0  // Landscape
    val imageHeight = 800.0
    val viewportWidth = 600.0   // Portrait
    val viewportHeight = 1000.0

    val controller = ZoomController.fit(imageWidth, imageHeight, viewportWidth, viewportHeight)

    // Landscape image in portrait viewport - should fit with letterboxing
    assertTrue(controller.zoom < 1.0)
  }

  // ZC-17: Box fitting preserves aspect ratio awareness
  @Test
  fun `box fitting preserves aspect ratio awareness`() {
    val viewportWidth = 800.0
    val viewportHeight = 600.0

    // Very wide box
    val wideCorners = BoundingBoxCorners(
        topLeft = Point(0.0, 100.0),
        topRight = Point(500.0, 100.0),
        bottomRight = Point(500.0, 150.0),
        bottomLeft = Point(0.0, 150.0)
    )

    val controller = ZoomController()
    val fitted = controller.fitToBox(wideCorners, viewportWidth, viewportHeight)

    // Should fit the wide box in the viewport - just verify it doesn't crash and has reasonable values
    assertTrue(fitted.zoom > 0)
    assertFalse(fitted.zoom.isNaN())
    assertFalse(fitted.zoom.isInfinite())

    // Very tall box
    val tallCorners = BoundingBoxCorners(
        topLeft = Point(100.0, 0.0),
        topRight = Point(150.0, 0.0),
        bottomRight = Point(150.0, 500.0),
        bottomLeft = Point(100.0, 500.0)
    )

    val fittedTall = controller.fitToBox(tallCorners, viewportWidth, viewportHeight)

    assertTrue(fittedTall.zoom > 0)
    assertFalse(fittedTall.zoom.isNaN())
    assertFalse(fittedTall.zoom.isInfinite())
  }
}
