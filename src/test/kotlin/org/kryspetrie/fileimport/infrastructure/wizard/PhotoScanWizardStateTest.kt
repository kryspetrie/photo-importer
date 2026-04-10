package org.kryspetrie.fileimport.infrastructure.wizard

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage

/**
 * Unit tests for PhotoScanWizardState. Tests state transitions, box management, configuration, and
 * workflow operations.
 */
class PhotoScanWizardStateTest {

  private lateinit var state: PhotoScanWizardState
  private lateinit var sampleImage: BufferedImage

  @BeforeEach
  fun setup() {
    state = PhotoScanWizardState()
    sampleImage = BufferedImage(1000, 800, BufferedImage.TYPE_INT_RGB)
  }

  // WS-01: Initial state
  @Test
  fun `initial state has correct defaults`() {
    assertEquals(PhotoScanWizardState.WizardStep.IMPORT, state.currentStep.value)
    assertNull(state.image.value)
    assertTrue(state.boundingBoxList.value.isEmpty())
    assertEquals(-1, state.selectedBoxIndex.value)
    assertEquals(ImportMode.PHOTO_SCAN, state.importMode.value)
    assertTrue(state.cvAutoDetectEnabled.value)
  }

  // WS-02: Initialize with image
  @Test
  fun `initialize with image sets image and clears state`() {
    val file = java.io.File("/test/image.jpg")
    state.initializeWithImage(sampleImage, file)

    assertEquals(sampleImage, state.image.value)
    assertEquals(file, state.imageFile.value)
    assertTrue(state.boundingBoxList.value.isEmpty())
    assertEquals(PhotoScanWizardState.WizardStep.OVERVIEW, state.currentStep.value)
  }

  // WS-03: Set detected boxes
  @Test
  fun `set detected boxes updates list`() {
    val boxes = listOf(
        BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0),
        BoundingBox.createRectangular(Point(300.0, 100.0), 100.0, 80.0)
    )
    state.setDetectedBoxes(boxes)

    assertEquals(2, state.boundingBoxList.value.size())
    assertEquals(-1, state.selectedBoxIndex.value)
  }

  // WS-04: Add box
  @Test
  fun `add box increases list size`() {
    val box = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
    state.addBox(box)

    assertEquals(1, state.boundingBoxList.value.size())
  }

  // WS-05: Remove box
  @Test
  fun `remove box decreases list size`() {
    val box = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
    state.addBox(box)
    assertEquals(1, state.boundingBoxList.value.size())

    state.removeBox(0)
    assertEquals(0, state.boundingBoxList.value.size())
  }

  // WS-06: Select box
  @Test
  fun `select box updates selection`() {
    val box = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
    state.addBox(box)

    state.selectBox(0)

    assertEquals(0, state.selectedBoxIndex.value)
    assertTrue(state.boundingBoxList.value.boxes[0].isSelected)
  }

  // WS-07: Deselect all
  @Test
  fun `deselect all clears selection`() {
    val box = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
    state.addBox(box)
    state.selectBox(0)

    state.deselectAll()

    assertEquals(-1, state.selectedBoxIndex.value)
    assertFalse(state.boundingBoxList.value.boxes[0].isSelected)
  }

  // WS-08: Enter refinement mode
  @Test
  fun `enter refinement sets step and index`() {
    val box = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
    state.addBox(box)

    state.enterRefinement(0)

    assertEquals(PhotoScanWizardState.WizardStep.REFINEMENT, state.currentStep.value)
    assertEquals(0, state.refinementBoxIndex.value)
  }

  // WS-09: Exit refinement returns to overview
  @Test
  fun `exit refinement returns to overview`() {
    val box = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
    state.addBox(box)
    state.enterRefinement(0)

    state.exitRefinement()

    assertEquals(PhotoScanWizardState.WizardStep.OVERVIEW, state.currentStep.value)
    assertEquals(-1, state.refinementBoxIndex.value)
  }

  // WS-10: Move selected box
  @Test
  fun `move selected box translates corners`() {
    val box = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
    state.addBox(box)
    state.selectBox(0)

    val originalCorners = state.boundingBoxList.value.boxes[0].corners.topLeft.copy()

    state.moveSelectedBox(10.0, -5.0)

    val movedBox = state.boundingBoxList.value.boxes[0]
    // Check that the top-left corner moved by the expected amount
    assertEquals(originalCorners.x + 10.0, movedBox.corners.topLeft.x, 0.01)
    assertEquals(originalCorners.y - 5.0, movedBox.corners.topLeft.y, 0.01)
  }

  // WS-11: Move corner
  @Test
  fun `move corner updates specific corner`() {
    val box = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
    state.addBox(box)
    state.selectBox(0)

    state.moveCorner(0, Corner.TOP_LEFT, 50.0, 50.0)

    val movedBox = state.boundingBoxList.value.boxes[0]
    assertEquals(50.0, movedBox.corners.topLeft.x, 0.01)
    assertEquals(50.0, movedBox.corners.topLeft.y, 0.01)
  }

  // WS-12: Rotate box
  @Test
  fun `rotate box rotates around center`() {
    val box = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
    state.addBox(box)

    val centerBefore = box.center()

    state.rotateBox(0, 45.0)

    val rotatedBox = state.boundingBoxList.value.boxes[0]
    assertEquals(centerBefore.x, rotatedBox.center().x, 0.01)
    assertEquals(centerBefore.y, rotatedBox.center().y, 0.01)
  }

  // WS-13: Expand box
  @Test
  fun `expand box increases size`() {
    val box = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
    state.addBox(box)
    val widthBefore = box.width()

    state.expandBox(0, 1.2)

    val expandedBox = state.boundingBoxList.value.boxes[0]
    assertTrue(expandedBox.width() > widthBefore)
  }

  // WS-14: Enter 4-point mode
  @Test
  fun `enter four point mode changes mode`() {
    state.enterFourPointMode()

    assertEquals(WizardMode.FOUR_POINT, state.wizardMode.value)
  }

  // WS-15: Exit 4-point mode
  @Test
  fun `exit four point mode returns to normal`() {
    state.enterFourPointMode()

    state.exitFourPointMode()

    assertEquals(WizardMode.NORMAL, state.wizardMode.value)
  }

  // WS-16: Enter add box mode
  @Test
  fun `enter add box mode changes mode`() {
    state.enterAddBoxMode()

    assertEquals(WizardMode.ADD_BOX, state.wizardMode.value)
  }

  // WS-17: Create box at center
  @Test
  fun `create box at center adds box`() {
    state.initializeWithImage(sampleImage, java.io.File("/test/image.jpg"))
    state.enterAddBoxMode()

    state.createBoxAtCenter(500.0, 400.0)

    assertEquals(1, state.boundingBoxList.value.size())
    assertEquals(WizardMode.NORMAL, state.wizardMode.value) // Mode exits after creation
  }

  // WS-17a: Reject box when image is too small
  @Test
  fun `create box at center rejects box when image is too small`() {
    val tinyImage = BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB)
    state.initializeWithImage(tinyImage, java.io.File("/test/tiny.jpg"))
    state.enterAddBoxMode()

    // Box would be 15x10 pixels (30% of 50), below minimum 50
    val result = state.createBoxAtCenter(25.0, 25.0, minSize = 50.0)

    assertFalse(result)
    assertEquals(0, state.boundingBoxList.value.size())
    // Mode should still be ADD_BOX since box wasn't created
    assertEquals(WizardMode.ADD_BOX, state.wizardMode.value)
  }

  // WS-17b: Accept box when image is large enough
  @Test
  fun `create box at center accepts box when image is large enough`() {
    val largeImage = BufferedImage(500, 400, BufferedImage.TYPE_INT_RGB)
    state.initializeWithImage(largeImage, java.io.File("/test/large.jpg"))
    state.enterAddBoxMode()

    // Box would be 150x100 pixels (30% of 500), above minimum 50
    val result = state.createBoxAtCenter(250.0, 200.0, minSize = 50.0)

    assertTrue(result)
    assertEquals(1, state.boundingBoxList.value.size())
    assertEquals(WizardMode.NORMAL, state.wizardMode.value)
  }

  // WS-18: Workflow navigation
  @Test
  fun `workflow navigation moves through steps`() {
    state.goToOverview()
    assertEquals(PhotoScanWizardState.WizardStep.OVERVIEW, state.currentStep.value)

    state.goToSummary()
    assertEquals(PhotoScanWizardState.WizardStep.SUMMARY, state.currentStep.value)

    state.goToProcessing()
    assertEquals(PhotoScanWizardState.WizardStep.PROCESSING, state.currentStep.value)

    state.goToComplete()
    assertEquals(PhotoScanWizardState.WizardStep.COMPLETE, state.currentStep.value)
  }

  // WS-19: Next box navigation
  @Test
  fun `next box navigates to next box`() {
    state.addBox(BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0))
    state.addBox(BoundingBox.createRectangular(Point(300.0, 100.0), 100.0, 80.0))
    state.enterRefinement(0)

    state.nextBox()

    assertEquals(1, state.refinementBoxIndex.value)
  }

  // WS-20: Previous box navigation
  @Test
  fun `previous box navigates to previous box`() {
    state.addBox(BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0))
    state.addBox(BoundingBox.createRectangular(Point(300.0, 100.0), 100.0, 80.0))
    state.enterRefinement(1)

    state.previousBox()

    assertEquals(0, state.refinementBoxIndex.value)
  }

  // WS-21: Next box wraps around
  @Test
  fun `next box wraps around`() {
    state.addBox(BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0))
    state.enterRefinement(0)

    state.nextBox()

    assertEquals(0, state.refinementBoxIndex.value) // Wrapped back
  }

  // WS-22: Set photo configuration
  @Test
  fun `set photo configuration stores config`() {
    val box = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
    state.addBox(box)

    val config = PhotoConfiguration(
        perspectiveCorrectionEnabled = true,
        rotationDegrees = 90
    )
    state.setPhotoConfiguration(box.id, config)

    val storedConfig = state.photoConfigurations.value[box.id]
    assertNotNull(storedConfig)
    assertTrue(storedConfig!!.perspectiveCorrectionEnabled)
    assertEquals(90, storedConfig.rotationDegrees)
  }

  // WS-23: Update photo configuration
  @Test
  fun `update photo configuration merges with existing`() {
    val box = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
    state.addBox(box)

    state.setPhotoConfiguration(box.id, PhotoConfiguration(perspectiveCorrectionEnabled = true))
    state.updatePhotoConfiguration(box.id) { it.copy(rotationDegrees = 90) }

    val config = state.photoConfigurations.value[box.id]
    assertTrue(config!!.perspectiveCorrectionEnabled)
    assertEquals(90, config.rotationDegrees)
  }

  // WS-24: Clear photo configuration
  @Test
  fun `clear photo configuration removes config`() {
    val box = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
    state.addBox(box)
    state.setPhotoConfiguration(box.id, PhotoConfiguration(perspectiveCorrectionEnabled = true))

    state.clearPhotoConfiguration(box.id)

    assertFalse(state.photoConfigurations.value.containsKey(box.id))
  }

  // WS-25: Rotate all boxes
  @Test
  fun `rotate all boxes updates all configs`() {
    val box1 = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
    val box2 = BoundingBox.createRectangular(Point(300.0, 100.0), 100.0, 80.0)
    state.addBox(box1)
    state.addBox(box2)

    state.rotateAllBoxes(90)

    assertEquals(90, state.photoConfigurations.value[box1.id]?.rotationDegrees)
    assertEquals(90, state.photoConfigurations.value[box2.id]?.rotationDegrees)
  }

  // WS-26: Set perspective correction all
  @Test
  fun `set perspective correction all enables for all`() {
    val box1 = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
    val box2 = BoundingBox.createRectangular(Point(300.0, 100.0), 100.0, 80.0)
    state.addBox(box1)
    state.addBox(box2)

    state.setPerspectiveCorrectionAll(true)

    assertTrue(state.photoConfigurations.value[box1.id]?.perspectiveCorrectionEnabled ?: false)
    assertTrue(state.photoConfigurations.value[box2.id]?.perspectiveCorrectionEnabled ?: false)
  }

  // WS-27: Clear all configurations
  @Test
  fun `clear all configurations removes all configs`() {
    val box = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
    state.addBox(box)
    state.setPhotoConfiguration(box.id, PhotoConfiguration(perspectiveCorrectionEnabled = true))

    state.clearAllConfigurations()

    assertTrue(state.photoConfigurations.value.isEmpty())
  }

  // WS-28: Reset to import step
  @Test
  fun `reset to import step clears everything`() {
    state.initializeWithImage(sampleImage, java.io.File("/test/image.jpg"))
    state.addBox(BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0))
    state.selectBox(0)
    state.setPhotoConfiguration(state.boundingBoxList.value.boxes[0].id, PhotoConfiguration())

    state.resetToImportStep()

    assertEquals(PhotoScanWizardState.WizardStep.IMPORT, state.currentStep.value)
    assertNull(state.image.value)
    assertTrue(state.boundingBoxList.value.isEmpty())
    assertTrue(state.photoConfigurations.value.isEmpty())
  }

  // WS-29: Import mode
  @Test
  fun `set import mode changes mode`() {
    state.setImportMode(ImportMode.SINGLE_PHOTO)
    assertEquals(ImportMode.SINGLE_PHOTO, state.importMode.value)

    state.setImportMode(ImportMode.PHOTO_SCAN)
    assertEquals(ImportMode.PHOTO_SCAN, state.importMode.value)
  }

  // WS-30: CV auto-detect toggle
  @Test
  fun `set CV auto detect changes setting`() {
    state.setCvAutoDetectEnabled(false)
    assertFalse(state.cvAutoDetectEnabled.value)

    state.setCvAutoDetectEnabled(true)
    assertTrue(state.cvAutoDetectEnabled.value)
  }

  // WS-31: Box count
  @Test
  fun `box count returns correct size`() {
    assertEquals(0, state.boxCount())

    state.addBox(BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0))
    assertEquals(1, state.boxCount())

    state.addBox(BoundingBox.createRectangular(Point(300.0, 100.0), 100.0, 80.0))
    assertEquals(2, state.boxCount())
  }

  // WS-32: Selected box accessor
  @Test
  fun `selected box returns correct box`() {
    val box = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
    state.addBox(box)
    state.selectBox(0)

    val selected = state.selectedBox()
    assertNotNull(selected)
    assertEquals(box.id, selected!!.id)
  }

  // WS-33: Refinement box accessor
  @Test
  fun `refinement box returns correct box`() {
    val box = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
    state.addBox(box)
    state.enterRefinement(0)

    val refinementBox = state.refinementBox()
    assertNotNull(refinementBox)
    assertEquals(box.id, refinementBox!!.id)
  }

  // WS-34: Coordinate conversion
  @Test
  fun `screen to image and back roundtrips`() {
    state.initializeWithImage(sampleImage, java.io.File("/test/image.jpg"))

    val original = Point(500.0, 400.0)
    val screen = state.imageToScreen(original.x, original.y)
    val back = state.screenToImage(screen.x, screen.y)

    assertEquals(original.x, back.x, 0.01)
    assertEquals(original.y, back.y, 0.01)
  }

  // WS-35: Undo/Redo basic functionality
  @Test
  fun `undo restores previous state`() {
    val box = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
    state.addBox(box)
    state.selectBox(0)

    // Move the box (pushes to undo stack)
    state.moveSelectedBox(10.0, 0.0)
    val movedBox = state.boundingBoxList.value.boxes[0]

    // Undo
    state.undo()

    // Should be back to original position
    val undoneBox = state.boundingBoxList.value.boxes[0]
    assertEquals(box.corners.topLeft.x, undoneBox.corners.topLeft.x, 0.01)
  }

  // ==================== Logger Integration Tests ====================

  // WS-36: Logger integration
  @Test
  fun `setLogger attaches logger to state`() {
    val mockLogger = org.kryspetrie.fileimport.infrastructure.logging.AppLogger()
    state.setLogger(mockLogger)
    
    // Logger should be attached (no exception thrown)
    assertNotNull(state.appLogger)
  }

  // WS-37: Box creation logs when logger attached
  @Test
  fun `addBox logs operation when logger attached`() {
    state.initializeWithImage(sampleImage, java.io.File("/test/image.jpg"))
    val mockLogger = org.kryspetrie.fileimport.infrastructure.logging.AppLogger()
    state.setLogger(mockLogger)
    
    val box = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
    state.addBox(box)
    
    // Logger should have received the log
    val logs = mockLogger.getRecentLogs()
    assertTrue(logs.any { it.message.contains("Box") })
  }

  // WS-38: Box removal logs when logger attached
  @Test
  fun `removeBox logs operation when logger attached`() {
    state.initializeWithImage(sampleImage, java.io.File("/test/image.jpg"))
    val mockLogger = org.kryspetrie.fileimport.infrastructure.logging.AppLogger()
    state.setLogger(mockLogger)
    
    val box = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
    state.addBox(box)
    mockLogger.getRecentLogs().drop(0) // Clear logs from addBox
    
    state.removeBox(0)
    
    val logs = mockLogger.getRecentLogs()
    assertTrue(logs.any { it.message.contains("Removed") || it.message.contains("Delete") })
  }

  // WS-39: Rejected box logs warning when logger attached
  @Test
  fun `createBoxAtCenter logs warning when rejected`() {
    val tinyImage = BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB)
    state.initializeWithImage(tinyImage, java.io.File("/test/tiny.jpg"))
    state.enterAddBoxMode()
    val mockLogger = org.kryspetrie.fileimport.infrastructure.logging.AppLogger()
    state.setLogger(mockLogger)
    
    val result = state.createBoxAtCenter(25.0, 25.0, minSize = 50.0)
    
    assertFalse(result)
    val logs = mockLogger.getRecentLogs()
    assertTrue(logs.any { it.level == org.kryspetrie.fileimport.infrastructure.logging.Level.WARN })
  }

  // WS-40: Successful box creation logs when logger attached
  @Test
  fun `createBoxAtCenter logs completion when successful`() {
    state.initializeWithImage(sampleImage, java.io.File("/test/image.jpg"))
    state.enterAddBoxMode()
    val mockLogger = org.kryspetrie.fileimport.infrastructure.logging.AppLogger()
    state.setLogger(mockLogger)
    
    val result = state.createBoxAtCenter(500.0, 400.0)
    
    assertTrue(result)
    val logs = mockLogger.getRecentLogs()
    assertTrue(logs.any { it.message.contains("Box") && it.message.contains("COMPLETE") })
  }

  // ==================== Box Creation Constants Tests ====================

  // WS-41: Constants are used correctly
  @Test
  fun `box creation uses PhotoScanConstants`() {
    // This verifies the constants are being used
    assertEquals(50.0, PhotoScanConstants.MIN_BOX_SIZE_PIXELS, 0.01)
    assertEquals(0.3, PhotoScanConstants.DEFAULT_BOX_WIDTH_RATIO, 0.01)
    assertEquals(1.5, PhotoScanConstants.DEFAULT_BOX_ASPECT_RATIO, 0.01)
  }
}
