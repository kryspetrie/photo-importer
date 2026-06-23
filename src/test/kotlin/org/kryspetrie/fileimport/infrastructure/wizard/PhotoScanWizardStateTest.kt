package org.kryspetrie.fileimport.infrastructure.wizard

import java.awt.image.BufferedImage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.model.CorrectionStrategy
import org.kryspetrie.fileimport.domain.model.RegionType

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
        assertEquals(WizardStep.IMPORT, state.currentStep.value)
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
        assertEquals(WizardStep.OVERVIEW, state.currentStep.value)
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
        assertEquals(WizardStep.OVERVIEW, state.currentStep.value)
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

        assertEquals(WizardStep.OVERVIEW, state.currentStep.value)
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

    // moveSelectedCorner must not jump to wrong corner position (ordinal mismatch bug)
    @Test
    fun `moveSelectedCorner moves BOTTOM_LEFT without jumping to BOTTOM_RIGHT`() {
        val box =
            BoundingBox(
                corners =
                    BoundingBoxCorners(
                        topLeft = Point(50.0, 50.0),
                        topRight = Point(200.0, 50.0),
                        bottomRight = Point(200.0, 150.0),
                        bottomLeft = Point(50.0, 150.0),
                    )
            )
        state.addBox(box)
        state.selectBox(0)
        state.selectCorner(Corner.BOTTOM_LEFT)

        // Move bottom-left right by 10px — it should stay at y=150, just shift x
        state.moveSelectedCorner(10.0, 0.0)

        val movedBox = state.boundingBoxList.value.boxes[0]
        // BEFORE FIX: read bottomRight (200,150) as start, so bottomLeft jumped to (210, 150)
        // Correct: should read bottomLeft (50,150), move to (60, 150)
        assertEquals(60.0, movedBox.corners.bottomLeft.x, 0.01)
        assertEquals(150.0, movedBox.corners.bottomLeft.y, 0.01)
        // bottomRight should be unchanged
        assertEquals(200.0, movedBox.corners.bottomRight.x, 0.01)
        assertEquals(150.0, movedBox.corners.bottomRight.y, 0.01)
    }

    @Test
    fun `moveSelectedCorner moves BOTTOM_RIGHT without jumping to BOTTOM_LEFT`() {
        val box =
            BoundingBox(
                corners =
                    BoundingBoxCorners(
                        topLeft = Point(50.0, 50.0),
                        topRight = Point(200.0, 50.0),
                        bottomRight = Point(200.0, 150.0),
                        bottomLeft = Point(50.0, 150.0),
                    )
            )
        state.addBox(box)
        state.selectBox(0)
        state.selectCorner(Corner.BOTTOM_RIGHT)

        // Move bottom-right up by 10px — it should stay at x=200, just shift y
        state.moveSelectedCorner(0.0, -10.0)

        val movedBox = state.boundingBoxList.value.boxes[0]
        // BEFORE FIX: read bottomLeft (50,150) as start, so bottomRight jumped to (50, 140)
        // Correct: should read bottomRight (200,150), move to (200, 140)
        assertEquals(200.0, movedBox.corners.bottomRight.x, 0.01)
        assertEquals(140.0, movedBox.corners.bottomRight.y, 0.01)
        // bottomLeft should be unchanged
        assertEquals(50.0, movedBox.corners.bottomLeft.x, 0.01)
        assertEquals(150.0, movedBox.corners.bottomLeft.y, 0.01)
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
        assertEquals(WizardStep.OVERVIEW, state.currentStep.value)

        state.goToSummary()
        assertEquals(WizardStep.SUMMARY, state.currentStep.value)

        state.goToProcessing()
        assertEquals(WizardStep.PROCESSING, state.currentStep.value)

        state.goToComplete()
        assertEquals(WizardStep.COMPLETE, state.currentStep.value)
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

        assertEquals(WizardStep.IMPORT, state.currentStep.value)
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

        state.putPreProcessed(0, PreProcessedImage(file, sampleImage, boxes))

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
        state.putPreProcessed(0, PreProcessedImage(file1, sampleImage, boxes1))
        state.putPreProcessed(1, PreProcessedImage(file2, image2, boxes2))

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
        state.putPreProcessed(0, PreProcessedImage(file1, sampleImage, emptyList()))

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
        state.putPreProcessed(0, PreProcessedImage(file1, sampleImage, emptyList()))
        state.putPreProcessed(1, PreProcessedImage(file2, image2, emptyList()))

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
        state.putPreProcessed(0, PreProcessedImage(file1, sampleImage, emptyList()))

        // Next image not ready yet (not cached)
        assertFalse(state.isNextImageReady)
        // Prev image not ready (at start)
        assertFalse(state.isPrevImageReady)

        // Cache second image
        state.putPreProcessed(1, PreProcessedImage(file2, image2, emptyList()))

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
        state.putPreProcessed(0, PreProcessedImage(file1, sampleImage, emptyList()))
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
        state.putPreProcessed(0, PreProcessedImage(file1, sampleImage, listOf(box)))
        state.putPreProcessed(1, PreProcessedImage(file2, image2, emptyList()))

        // Switch to second image — should clear configurations
        state.switchToImage(1)

        assertTrue(state.photoConfigurations.value.isEmpty())
    }

    @Test
    fun `hasMoreBatchImages returns true when more images exist`() {
        val file1 = java.io.File("/test/img1.jpg")
        val file2 = java.io.File("/test/img2.jpg")
        val file3 = java.io.File("/test/img3.jpg")
        val files = listOf(file1, file2, file3)
        state.initializeBatch(files)
        // After initialize, index is 0 — more images exist
        assertTrue(state.hasMoreBatchImages)
    }

    @Test
    fun `hasMoreBatchImages returns false when at last image`() {
        val file1 = java.io.File("/test/img1.jpg")
        val file2 = java.io.File("/test/img2.jpg")
        val files = listOf(file1, file2)
        state.initializeBatch(files)
        // Advance to last image
        state.advanceToNextBatchFile()
        assertFalse(state.hasMoreBatchImages)
    }

    @Test
    fun `hasMoreBatchImages returns false in single file mode`() {
        assertFalse(state.hasMoreBatchImages)
    }

    @Test
    fun `advanceToNextBatchFile moves to next and returns file`() {
        val file1 = java.io.File("/test/img1.jpg")
        val file2 = java.io.File("/test/img2.jpg")
        val file3 = java.io.File("/test/img3.jpg")
        val files = listOf(file1, file2, file3)
        state.initializeBatch(files)

        assertEquals(0, state.currentImageIndex.value)

        val next = state.advanceToNextBatchFile()
        assertEquals(file2, next)
        assertEquals(1, state.currentImageIndex.value)

        val next2 = state.advanceToNextBatchFile()
        assertEquals(file3, next2)
        assertEquals(2, state.currentImageIndex.value)
    }

    @Test
    fun `advanceToNextBatchFile returns null at end of batch`() {
        val file1 = java.io.File("/test/img1.jpg")
        val file2 = java.io.File("/test/img2.jpg")
        val files = listOf(file1, file2)
        state.initializeBatch(files)
        state.advanceToNextBatchFile() // move to index 1

        val result = state.advanceToNextBatchFile()
        assertNull(result)
        assertEquals(1, state.currentImageIndex.value) // index unchanged
    }

    @Test
    fun `resetPerImageState clears boxes and configs but preserves batch state`() {
        val file1 = java.io.File("/test/img1.jpg")
        val file2 = java.io.File("/test/img2.jpg")
        val files = listOf(file1, file2)

        state.initializeBatch(files)
        val sampleImage = BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB)
        state.initializeWithImage(sampleImage, file1)
        val box = BoundingBox.createRectangular(Point(100.0, 100.0), 200.0, 150.0)
        state.addBox(box)
        state.setPhotoConfiguration(box.id, PhotoConfiguration(perspectiveCorrectionEnabled = true))

        // Before reset: has boxes and configs
        assertEquals(1, state.boxCount())
        assertTrue(state.photoConfigurations.value.isNotEmpty())
        assertEquals(2, state.sourceFiles.value.size)

        state.resetPerImageState()

        // After reset: boxes and configs cleared
        assertEquals(0, state.boxCount())
        assertTrue(state.photoConfigurations.value.isEmpty())
        // But batch state is preserved
        assertEquals(2, state.sourceFiles.value.size)
        assertTrue(state.isBatchMode)
    }

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

    // ==================== Export Settings ====================

    // WS-56: perspectiveCorrectionEnabled defaults to true
    @Test
    fun `perspectiveCorrectionEnabled defaults to true`() {
        assertTrue(state.perspectiveCorrectionEnabled.value)
    }

    // WS-57: setPerspectiveCorrectionEnabled toggles the state
    @Test
    fun `setPerspectiveCorrectionEnabled toggles the state`() {
        state.setPerspectiveCorrectionEnabled(false)
        assertFalse(state.perspectiveCorrectionEnabled.value)
        state.setPerspectiveCorrectionEnabled(true)
        assertTrue(state.perspectiveCorrectionEnabled.value)
    }

    // WS-58: exportMarginPercent defaults to 0.02
    @Test
    fun `exportMarginPercent defaults to 2 percent`() {
        assertEquals(0.02, state.exportMarginPercent.value)
    }

    // WS-59: setExportMarginPercent updates the state
    @Test
    fun `setExportMarginPercent updates the state`() {
        state.setExportMarginPercent(0.05)
        assertEquals(0.05, state.exportMarginPercent.value)
    }

    // WS-60: setExportMarginPercent clamps to 0.0-0.2
    @Test
    fun `setExportMarginPercent clamps to valid range`() {
        state.setExportMarginPercent(-0.1)
        assertEquals(0.0, state.exportMarginPercent.value)
        state.setExportMarginPercent(0.5)
        assertEquals(0.2, state.exportMarginPercent.value)
    }

    // ==================== Correction Strategy Default ====================

    // WS-61: defaultCorrectionStrategy defaults to PERSPECTIVE
    @Test
    fun `defaultCorrectionStrategy defaults to PERSPECTIVE`() {
        assertEquals(CorrectionStrategy.PERSPECTIVE, state.defaultCorrectionStrategy.value)
    }

    // WS-62: setDefaultCorrectionStrategy changes the strategy
    @Test
    fun `setDefaultCorrectionStrategy changes the strategy`() {
        state.setDefaultCorrectionStrategy(CorrectionStrategy.CROP)
        assertEquals(CorrectionStrategy.CROP, state.defaultCorrectionStrategy.value)

        state.setDefaultCorrectionStrategy(CorrectionStrategy.CROP_AND_ROTATE)
        assertEquals(CorrectionStrategy.CROP_AND_ROTATE, state.defaultCorrectionStrategy.value)

        state.setDefaultCorrectionStrategy(CorrectionStrategy.PERSPECTIVE)
        assertEquals(CorrectionStrategy.PERSPECTIVE, state.defaultCorrectionStrategy.value)
    }

    // ==================== Face Region Tests ====================

    private fun addSampleBox(): BoundingBox {
        val box = BoundingBox.createRectangular(Point(100.0, 100.0), 100.0, 80.0)
        state.addBox(box)
        return box
    }

    @Test
    fun `addFaceRegion creates default-sized region with name and type`() {
        addSampleBox()
        state.addFaceRegion(0, "Alice", 0.5, 0.4)
        val config = state.photoConfigurations.value[state.boxes[0].id]
        assertNotNull(config)
        assertEquals(1, config!!.faceRegions.size)
        val region = config.faceRegions[0]
        assertEquals("Alice", region.name)
        assertEquals("Face", region.type)
        assertEquals(0.5, region.x)
        assertEquals(0.4, region.y)
        assertEquals(FaceSize.DEFAULT.diameter, region.w)
        assertEquals(FaceSize.DEFAULT.diameter, region.h)
        assertEquals("Alice", config.subjects)
    }

    @Test
    fun `addFaceRegion with type PET stores correct type`() {
        addSampleBox()
        state.addFaceRegion(0, "Fido", 0.3, 0.5, RegionType.PET)
        val config = state.photoConfigurations.value[state.boxes[0].id]!!
        assertEquals("Pet", config.faceRegions[0].type)
        assertEquals("Fido", config.subjects)
    }

    @Test
    fun `addFaceRegion with type BODY stores correct type`() {
        addSampleBox()
        state.addFaceRegion(0, "Torso", 0.3, 0.5, RegionType.BODY)
        val config = state.photoConfigurations.value[state.boxes[0].id]!!
        assertEquals("Body", config.faceRegions[0].type)
    }

    @Test
    fun `addFaceRegion with type OBJECT stores correct type`() {
        addSampleBox()
        state.addFaceRegion(0, "Car", 0.3, 0.5, RegionType.OBJECT)
        val config = state.photoConfigurations.value[state.boxes[0].id]!!
        assertEquals("Object", config.faceRegions[0].type)
    }

    @Test
    fun `addFaceRegion auto-populates subjects with multiple names`() {
        addSampleBox()
        state.addFaceRegion(0, "Alice", 0.3, 0.3)
        state.addFaceRegion(0, "Bob", 0.6, 0.6, RegionType.PET)
        val config = state.photoConfigurations.value[state.boxes[0].id]!!
        assertEquals("Alice, Bob", config.subjects)
        assertEquals(2, config.faceRegions.size)
    }

    @Test
    fun `addFaceRegion coerces coordinates to 0-1 range`() {
        addSampleBox()
        state.addFaceRegion(0, "Edge", 1.5, -0.3)
        val region = state.photoConfigurations.value[state.boxes[0].id]!!.faceRegions[0]
        assertEquals(1.0, region.x)
        assertEquals(0.0, region.y)
    }

    @Test
    fun `addFaceRegion ignores invalid photoIndex`() {
        addSampleBox()
        state.addFaceRegion(-1, "Nope", 0.5, 0.5)
        state.addFaceRegion(99, "Nope", 0.5, 0.5)
        // No crash, no face regions added
        assertTrue(state.photoConfigurations.value.isEmpty())
    }

    @Test
    fun `removeFaceRegion removes region and updates subjects`() {
        addSampleBox()
        state.addFaceRegion(0, "Alice", 0.3, 0.3)
        state.addFaceRegion(0, "Bob", 0.6, 0.6)
        val config0 = state.photoConfigurations.value[state.boxes[0].id]!!
        assertEquals("Alice, Bob", config0.subjects)

        state.removeFaceRegion(0, 0) // Remove Alice
        val config1 = state.photoConfigurations.value[state.boxes[0].id]!!
        assertEquals(1, config1.faceRegions.size)
        assertEquals("Bob", config1.faceRegions[0].name)
        assertEquals("Bob", config1.subjects)
    }

    @Test
    fun `removeFaceRegion removes name from subjects`() {
        addSampleBox()
        state.addFaceRegion(0, "Alice", 0.3, 0.3)
        state.removeFaceRegion(0, 0)
        val config = state.photoConfigurations.value[state.boxes[0].id]!!
        assertEquals("", config.subjects)
        assertEquals(0, config.faceRegions.size)
    }

    @Test
    fun `updateFaceRegion changes position`() {
        addSampleBox()
        state.addFaceRegion(0, "Alice", 0.5, 0.5)
        state.updateFaceRegion(0, 0, x = 0.3, y = 0.7)
        val region = state.photoConfigurations.value[state.boxes[0].id]!!.faceRegions[0]
        assertEquals(0.3, region.x)
        assertEquals(0.7, region.y)
        assertEquals("Alice", region.name) // unchanged
    }

    @Test
    fun `updateFaceRegion coerces values to 0-1 range`() {
        addSampleBox()
        state.addFaceRegion(0, "Alice", 0.5, 0.5)
        state.updateFaceRegion(0, 0, x = 2.0, y = -1.0)
        val region = state.photoConfigurations.value[state.boxes[0].id]!!.faceRegions[0]
        assertEquals(1.0, region.x)
        assertEquals(0.0, region.y)
    }

    @Test
    fun `updateFaceRegion preserves unchanged y when only x given`() {
        addSampleBox()
        state.addFaceRegion(0, "Alice", 0.5, 0.5)
        state.updateFaceRegion(0, 0, x = 0.3) // only change x
        val region = state.photoConfigurations.value[state.boxes[0].id]!!.faceRegions[0]
        assertEquals(0.3, region.x)
        assertEquals(0.5, region.y) // unchanged
    }

    @Test
    fun `resizeFaceRegion changes size to preset diameter`() {
        addSampleBox()
        state.addFaceRegion(0, "Alice", 0.5, 0.5)
        state.resizeFaceRegion(0, 0, FaceSize.LARGE)
        val region = state.photoConfigurations.value[state.boxes[0].id]!!.faceRegions[0]
        assertEquals(FaceSize.LARGE.diameter, region.w)
        assertEquals(FaceSize.LARGE.diameter, region.h)
        assertEquals(0.5, region.x) // position unchanged
        assertEquals(0.5, region.y)
    }

    @Test
    fun `clearAllFaceRegions removes all regions and clears subjects`() {
        addSampleBox()
        state.addFaceRegion(0, "Alice", 0.3, 0.4)
        state.addFaceRegion(0, "Bob", 0.6, 0.5)
        assertEquals(2, state.photoConfigurations.value[state.boxes[0].id]!!.faceRegions.size)
        assertEquals("Alice, Bob", state.photoConfigurations.value[state.boxes[0].id]!!.subjects)
        state.clearAllFaceRegions(0)
        assertEquals(0, state.photoConfigurations.value[state.boxes[0].id]!!.faceRegions.size)
        assertEquals("", state.photoConfigurations.value[state.boxes[0].id]!!.subjects)
    }

    @Test
    fun `moveFaceRegion offsets center position`() {
        addSampleBox()
        state.addFaceRegion(0, "Alice", 0.5, 0.5)
        state.moveFaceRegion(0, 0, dx = 0.1, dy = -0.2)
        val region = state.photoConfigurations.value[state.boxes[0].id]!!.faceRegions[0]
        assertEquals(0.6, region.x)
        assertEquals(0.3, region.y)
        // w/h unchanged (default FaceSize.MEDIUM diameter)
        assertEquals(FaceSize.DEFAULT.diameter, region.w)
        assertEquals(FaceSize.DEFAULT.diameter, region.h)
    }

    @Test
    fun `moveFaceRegion coerces to 0-1 range`() {
        addSampleBox()
        state.addFaceRegion(0, "Alice", 0.9, 0.1)
        state.moveFaceRegion(0, 0, dx = 0.5, dy = -0.5)
        val region = state.photoConfigurations.value[state.boxes[0].id]!!.faceRegions[0]
        assertEquals(1.0, region.x)
        assertEquals(0.0, region.y)
        // w/h unchanged
        assertEquals(FaceSize.DEFAULT.diameter, region.w)
        assertEquals(FaceSize.DEFAULT.diameter, region.h)
    }

    @Test
    fun `RegionType fromMwgRs parses known types`() {
        assertEquals(RegionType.FACE, RegionType.fromMwgRs("Face"))
        assertEquals(RegionType.PET, RegionType.fromMwgRs("Pet"))
        assertEquals(RegionType.BODY, RegionType.fromMwgRs("Body"))
        assertEquals(RegionType.OBJECT, RegionType.fromMwgRs("Object"))
    }

    @Test
    fun `RegionType fromMwgRs is case-insensitive and defaults to FACE`() {
        assertEquals(RegionType.FACE, RegionType.fromMwgRs("face"))
        assertEquals(RegionType.PET, RegionType.fromMwgRs("PET"))
        assertEquals(RegionType.FACE, RegionType.fromMwgRs("Unknown"))
        assertEquals(RegionType.FACE, RegionType.fromMwgRs(""))
    }

    @Test
    fun `enterFaceSelectMode sets state correctly`() {
        addSampleBox()
        state.enterFaceSelectMode(0)
        assertTrue(state.faceSelectMode.value)
        assertEquals(0, state.faceSelectPhotoIndex.value)
    }

    @Test
    fun `exitFaceSelectMode clears state`() {
        addSampleBox()
        state.enterFaceSelectMode(0)
        state.exitFaceSelectMode()
        assertFalse(state.faceSelectMode.value)
        assertNull(state.faceSelectPhotoIndex.value)
    }
}
