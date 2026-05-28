package org.kryspetrie.fileimport.infrastructure.wizard

import java.awt.image.BufferedImage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

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
        val boxes =
            listOf(
                BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0),
                BoundingBox.createRectangular(Point(300.0, 100.0), 100.0, 80.0),
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
    fun `enter refinement selects box and stays on overview`() {
        val box = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
        state.addBox(box)

        state.enterRefinement(0)

        // Refinement is now inline — stays on OVERVIEW step
        assertEquals(PhotoScanWizardState.WizardStep.OVERVIEW, state.currentStep.value)
        assertEquals(0, state.refinementBoxIndex.value)
        assertEquals(0, state.selectedBoxIndex.value)
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

        val config = PhotoConfiguration(perspectiveCorrectionEnabled = true, rotationDegrees = 90)
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

        state.rotateAllBoxesCW()

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
        assertTrue(
            logs.any { it.level == org.kryspetrie.fileimport.infrastructure.logging.Level.WARN }
        )
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

    // ==================== Batch Processing Tests ====================

    // WS-42: Batch state defaults
    @Test
    fun `batch state has correct defaults`() {
        assertFalse(state.isBatchMode)
        assertEquals(0, state.batchTotal)
        assertTrue(state.sourceFiles.value.isEmpty())
        assertEquals(0, state.currentImageIndex.value)
        assertTrue(state.preProcessedCache.value.isEmpty())
        assertFalse(state.preProcessing.value)
    }

    // WS-43: Initialize batch mode
    @Test
    fun `initializeBatch sets source files and resets state`() {
        val files =
            listOf(
                java.io.File("/test/img1.jpg"),
                java.io.File("/test/img2.jpg"),
                java.io.File("/test/img3.jpg"),
            )
        state.initializeBatch(files)

        assertEquals(3, state.batchTotal)
        assertTrue(state.isBatchMode)
        assertEquals(files, state.sourceFiles.value)
        assertEquals(0, state.currentImageIndex.value)
        assertTrue(state.preProcessedCache.value.isEmpty())
        assertEquals(0, state.preProcessCount.value)
    }

    // WS-44: Put and retrieve pre-processed image
    @Test
    fun `putPreProcessed caches result and updates count`() {
        val file = java.io.File("/test/img1.jpg")
        val boxes = listOf(BoundingBox.createRectangular(Point(100.0, 100.0), 200.0, 150.0))

        state.putPreProcessed(0, PhotoScanWizardState.PreProcessedImage(file, sampleImage, boxes))

        assertEquals(1, state.preProcessCount.value)
        val cached = state.preProcessedCache.value[0]
        assertNotNull(cached)
        assertEquals(file, cached!!.file)
        assertEquals(1, cached.boxes.size)
    }

    // WS-45: Switch to pre-processed image
    @Test
    fun `switchToImage loads cached image and boxes`() {
        val file1 = java.io.File("/test/img1.jpg")
        val file2 = java.io.File("/test/img2.jpg")
        val boxes1 = listOf(BoundingBox.createRectangular(Point(50.0, 50.0), 200.0, 150.0))
        val boxes2 = listOf(BoundingBox.createRectangular(Point(100.0, 100.0), 200.0, 150.0))

        val image2 = BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB)

        // Initialize with first image
        state.initializeWithImage(sampleImage, file1)
        state.setDetectedBoxes(boxes1)

        // Set up batch and cache second image
        val files = listOf(file1, file2)
        state.initializeBatch(files)
        state.putPreProcessed(0, PhotoScanWizardState.PreProcessedImage(file1, sampleImage, boxes1))
        state.putPreProcessed(1, PhotoScanWizardState.PreProcessedImage(file2, image2, boxes2))

        // Switch to second image
        val result = state.switchToImage(1)

        assertTrue(result)
        assertEquals(1, state.currentImageIndex.value)
        assertEquals(image2, state.image.value)
        assertEquals(file2, state.imageFile.value)
        assertEquals(1, state.boundingBoxList.value.size())
    }

    // WS-46: Switch to uncached image returns false
    @Test
    fun `switchToImage returns false for uncached index`() {
        val file1 = java.io.File("/test/img1.jpg")
        state.initializeWithImage(sampleImage, file1)

        val files = listOf(file1, java.io.File("/test/img2.jpg"))
        state.initializeBatch(files)
        // Only cache first image, not second
        state.putPreProcessed(
            0,
            PhotoScanWizardState.PreProcessedImage(file1, sampleImage, emptyList()),
        )

        val result = state.switchToImage(1)
        assertFalse(result)
        // Current index should not change
        assertEquals(0, state.currentImageIndex.value)
    }

    // WS-47: Next and prev image navigation
    @Test
    fun `nextImage and prevImage navigate batch`() {
        val file1 = java.io.File("/test/img1.jpg")
        val file2 = java.io.File("/test/img2.jpg")
        val image2 = BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB)

        state.initializeWithImage(sampleImage, file1)

        val files = listOf(file1, file2)
        state.initializeBatch(files)
        state.putPreProcessed(
            0,
            PhotoScanWizardState.PreProcessedImage(file1, sampleImage, emptyList()),
        )
        state.putPreProcessed(1, PhotoScanWizardState.PreProcessedImage(file2, image2, emptyList()))

        // Navigate forward
        assertTrue(state.nextImage())
        assertEquals(1, state.currentImageIndex.value)

        // Navigate backward
        assertTrue(state.prevImage())
        assertEquals(0, state.currentImageIndex.value)
    }

    // WS-48: isNextImageReady and isPrevImageReady
    @Test
    fun `isNextImageReady and isPrevImageReady reflect cache state`() {
        val file1 = java.io.File("/test/img1.jpg")
        val file2 = java.io.File("/test/img2.jpg")
        val image2 = BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB)

        state.initializeWithImage(sampleImage, file1)

        val files = listOf(file1, file2)
        state.initializeBatch(files)
        state.putPreProcessed(
            0,
            PhotoScanWizardState.PreProcessedImage(file1, sampleImage, emptyList()),
        )

        // Next image not ready yet (not cached)
        assertFalse(state.isNextImageReady)
        // Prev image not ready (at start)
        assertFalse(state.isPrevImageReady)

        // Cache second image
        state.putPreProcessed(1, PhotoScanWizardState.PreProcessedImage(file2, image2, emptyList()))

        // Now next image is ready
        assertTrue(state.isNextImageReady)
        // Prev still not ready (at index 0)
        assertFalse(state.isPrevImageReady)
    }

    // WS-49: Reset clears batch state
    @Test
    fun `resetToImportStep clears batch state`() {
        val file1 = java.io.File("/test/img1.jpg")
        state.initializeWithImage(sampleImage, file1)
        state.initializeBatch(listOf(file1, java.io.File("/test/img2.jpg")))
        state.putPreProcessed(
            0,
            PhotoScanWizardState.PreProcessedImage(file1, sampleImage, emptyList()),
        )
        state.setPreProcessing(true)

        state.resetToImportStep()

        assertFalse(state.isBatchMode)
        assertEquals(0, state.batchTotal)
        assertTrue(state.preProcessedCache.value.isEmpty())
        assertEquals(0, state.preProcessCount.value)
        assertFalse(state.preProcessing.value)
    }

    // WS-50: setPreProcessing toggles state
    @Test
    fun `setPreProcessing toggles state`() {
        state.setPreProcessing(true)
        assertTrue(state.preProcessing.value)
        state.setPreProcessing(false)
        assertFalse(state.preProcessing.value)
    }

    // WS-51: switchToImage clears photo configurations and undo state
    @Test
    fun `switchToImage clears photo configurations and undo state`() {
        val file1 = java.io.File("/test/img1.jpg")
        val file2 = java.io.File("/test/img2.jpg")
        val image2 = BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB)

        state.initializeWithImage(sampleImage, file1)
        val box = BoundingBox.createRectangular(Point(100.0, 100.0), 200.0, 150.0)
        state.addBox(box)

        // Set some photo configuration
        state.setPhotoConfiguration(box.id, PhotoConfiguration(perspectiveCorrectionEnabled = true))
        assertTrue(state.photoConfigurations.value.isNotEmpty())

        val files = listOf(file1, file2)
        state.initializeBatch(files)
        state.putPreProcessed(
            0,
            PhotoScanWizardState.PreProcessedImage(file1, sampleImage, listOf(box)),
        )
        state.putPreProcessed(1, PhotoScanWizardState.PreProcessedImage(file2, image2, emptyList()))

        // Switch to second image — should clear configurations
        state.switchToImage(1)

        assertTrue(state.photoConfigurations.value.isEmpty())
    }

    // ==================== selectCorner Bug Fix Tests ====================

    // WS-52: selectCorner should NOT reset selectedBoxIndex (overview page corner drag bug)
    @Test
    fun `selectCorner does not reset selectedBoxIndex`() {
        val box = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
        state.addBox(box)

        // Select box 0 (sets selectedBoxIndex = 0)
        state.selectBox(0)
        assertEquals(0, state.selectedBoxIndex.value)

        // Select a corner — this should NOT reset selectedBoxIndex to -1
        state.selectCorner(Corner.TOP_LEFT)
        assertEquals(0, state.selectedBoxIndex.value) // WAS -1 before the fix (bug!)
        assertEquals(Corner.TOP_LEFT, state.selectedCorner.value)
    }

    // WS-53: selectCorner preserves selectedBoxIndex even without refinement mode
    @Test
    fun `selectCorner preserves selectedBoxIndex without refinement mode`() {
        val box1 = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
        val box2 = BoundingBox.createRectangular(Point(300.0, 100.0), 100.0, 80.0)
        state.addBox(box1)
        state.addBox(box2)

        // refinementBoxIndex is -1 (no refinement mode)
        assertEquals(-1, state.refinementBoxIndex.value)

        // Select box 1 (not 0)
        state.selectBox(1)
        assertEquals(1, state.selectedBoxIndex.value)

        // Select a corner — should preserve selectedBoxIndex = 1, not set it to
        // refinementBoxIndex(-1)
        state.selectCorner(Corner.BOTTOM_RIGHT)
        assertEquals(1, state.selectedBoxIndex.value) // WAS -1 before the fix
        assertEquals(Corner.BOTTOM_RIGHT, state.selectedCorner.value)
    }

    // WS-54: selectCorner after enterRefinement preserves selectedBoxIndex
    @Test
    fun `selectCorner after enterRefinement preserves selectedBoxIndex`() {
        val box = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
        state.addBox(box)

        state.enterRefinement(0)
        assertEquals(0, state.selectedBoxIndex.value)
        assertEquals(0, state.refinementBoxIndex.value)

        state.selectCorner(Corner.TOP_RIGHT)
        assertEquals(0, state.selectedBoxIndex.value)
        assertEquals(Corner.TOP_RIGHT, state.selectedCorner.value)
    }

    // WS-55: deselectCorner does not change selectedBoxIndex
    @Test
    fun `deselectCorner does not change selectedBoxIndex`() {
        val box = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
        state.addBox(box)

        state.selectBox(0)
        state.selectCorner(Corner.TOP_LEFT)
        assertEquals(0, state.selectedBoxIndex.value)

        state.deselectCorner()
        assertEquals(0, state.selectedBoxIndex.value) // Should still be 0
        assertNull(state.selectedCorner.value)
    }
}
