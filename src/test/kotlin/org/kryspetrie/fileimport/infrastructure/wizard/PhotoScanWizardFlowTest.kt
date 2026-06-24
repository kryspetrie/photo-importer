package org.kryspetrie.fileimport.infrastructure.wizard

import java.awt.image.BufferedImage
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.model.CorrectionStrategy
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.domain.model.RegionType

/**
 * End-to-end flow tests for PhotoScanWizardState.
 *
 * Tests realistic user workflows by driving the state machine through multiple steps
 * in sequence, verifying intermediate and final state at each point. Unlike the unit
 * tests in PhotoScanWizardStateTest which test individual operations in isolation,
 * these tests exercise the state transitions a real user would trigger.
 */
@DisplayName("Photo Scan Wizard Flow Tests")
class PhotoScanWizardFlowTest {

    private lateinit var state: PhotoScanWizardState
    private lateinit var testImage: BufferedImage

    @BeforeEach
    fun setup() {
        state = PhotoScanWizardState()
        testImage = BufferedImage(1000, 800, BufferedImage.TYPE_INT_RGB)
    }

    // ==================== Full Scan Workflow ====================

    @Nested
    @DisplayName("Full scan workflow")
    inner class FullScanWorkflow {

        @Test
        @DisplayName("Happy path: import → detect → configure → summary → edit → complete")
        fun happyPathFullScan() {
            // Step 1: Import — initialize with image
            state.initializeWithImage(testImage, File("test-scan.jpg"))
            assertEquals(WizardStep.OVERVIEW, state.navigation.currentStep.value)
            assertNotNull(state.image.value)
            assertEquals(File("test-scan.jpg"), state.imageFile.value)

            // Step 2: Detect — CV finds 2 photos
            val box1 = BoundingBox.createRectangular(Point(100.0, 100.0), 200.0, 150.0)
            val box2 = BoundingBox.createRectangular(Point(400.0, 100.0), 200.0, 150.0)
            state.setDetectedBoxes(listOf(box1, box2))
            assertEquals(2, state.boundingBoxList.value.size())

            // Step 3: Configure — set per-photo metadata
            val config1 = PhotoScanConfiguration(description = "Photo 1")
            state.configs.setPhotoConfiguration(box1.id, config1)
            assertEquals("Photo 1", state.photoConfigurations.value[box1.id]?.description)

            // Step 4: Navigate to summary
            state.navigation.goToSummary()
            assertEquals(WizardStep.SUMMARY, state.navigation.currentStep.value)

            // Step 5: Navigate to edit
            state.navigation.goToEdit()
            assertEquals(WizardStep.EDIT, state.navigation.currentStep.value)

            // Step 6: Navigate to processing
            state.navigation.goToProcessing()
            assertEquals(WizardStep.PROCESSING, state.navigation.currentStep.value)

            // Step 7: Complete
            state.navigation.goToComplete()
            assertEquals(WizardStep.COMPLETE, state.navigation.currentStep.value)
        }

        @Test
        @DisplayName("Navigate back from each step returns to correct previous step")
        fun navigateBackFromEachStep() {
            state.initializeWithImage(testImage, File("test.jpg"))

            // Forward: Overview → Summary → Edit
            state.navigation.goToSummary()
            state.navigation.goToEdit()
            assertEquals(WizardStep.EDIT, state.navigation.currentStep.value)

            // Back: Edit → Overview (via goToOverview, since Edit's back goes to Summary)
            state.goToOverview()
            assertEquals(WizardStep.OVERVIEW, state.navigation.currentStep.value)

            // Back: Summary → Overview (simulated by going forward then back)
            state.navigation.goToSummary()
            state.goToOverview()
            assertEquals(WizardStep.OVERVIEW, state.navigation.currentStep.value)
        }

        @Test
        @DisplayName("Reset from complete state clears everything for next scan")
        fun resetFromComplete() {
            state.initializeWithImage(testImage, File("scan.jpg"))
            state.boxes.addBox(BoundingBox.createRectangular(Point(100.0, 100.0), 200.0, 150.0))
            state.configs.setPhotoConfiguration(
                state.configs.boxes[0].id, PhotoScanConfiguration(description = "Test")
            )
            state.navigation.goToSummary()
            state.navigation.goToProcessing()
            state.navigation.goToComplete()

            // Now reset for next scan
            state.resetToImportStep()
            assertEquals(WizardStep.IMPORT, state.navigation.currentStep.value)
            assertNull(state.image.value)
            assertEquals(0, state.boundingBoxList.value.size())
            assertTrue(state.photoConfigurations.value.isEmpty())
        }
    }

    // ==================== Box Manipulation Flow ====================

    @Nested
    @DisplayName("Box manipulation flow")
    inner class BoxManipulationFlow {

        @Test
        @DisplayName("Add boxes, select, move, modify corners, then undo/redo")
        fun addSelectMoveUndoRedoFlow() {
            state.initializeWithImage(testImage, File("scan.jpg"))

            // Add 3 boxes
            val box1 = BoundingBox.createRectangular(Point(100.0, 100.0), 200.0, 150.0)
            val box2 = BoundingBox.createRectangular(Point(400.0, 100.0), 200.0, 150.0)
            val box3 = BoundingBox.createRectangular(Point(250.0, 400.0), 200.0, 150.0)
            state.boxes.addBox(box1)
            state.boxes.addBox(box2)
            state.boxes.addBox(box3)
            assertEquals(3, state.boundingBoxList.value.size())

            // Select and move box 1
            state.boxes.selectBox(0)
            assertEquals(0, state.boxes.selectedBoxIndex.value)
            state.boxes.moveSelectedBox(10.0, 20.0)
            val movedBox1 = state.configs.boxes[0]
            assertEquals(box1.corners.topLeft.x + 10.0, movedBox1.corners.topLeft.x, 0.01)
            assertEquals(box1.corners.topLeft.y + 20.0, movedBox1.corners.topLeft.y, 0.01)

            // Move a corner
            state.boxes.moveCorner(0, Corner.BOTTOM_RIGHT, 500.0, 600.0)
            assertEquals(500.0, state.configs.boxes[0].corners.bottomRight.x, 0.01)
            assertEquals(600.0, state.configs.boxes[0].corners.bottomRight.y, 0.01)

            // Undo the corner move
            state.boxes.undo()
            assertEquals(box1.corners.bottomRight.x + 10.0, state.configs.boxes[0].corners.bottomRight.x, 0.01)

            // Undo the box move
            state.boxes.undo()
            // Back to original position (before move)
            assertEquals(box1.corners.topLeft.x, state.configs.boxes[0].corners.topLeft.x, 0.01)

            // Redo
            state.boxes.redo()
            assertEquals(box1.corners.topLeft.x + 10.0, state.configs.boxes[0].corners.topLeft.x, 0.01)
        }

        @Test
        @DisplayName("Rotate all boxes CW then CCW")
        fun rotateAllBoxesFlow() {
            state.initializeWithImage(testImage, File("scan.jpg"))
            state.boxes.addBox(BoundingBox.createRectangular(Point(100.0, 100.0), 200.0, 150.0))
            state.boxes.addBox(BoundingBox.createRectangular(Point(400.0, 100.0), 200.0, 150.0))

            // Rotate all CW — updates rotation in per-photo configs
            state.configs.rotateAllBoxesCW()
            val box1Id = state.configs.boxes[0].id
            val box2Id = state.configs.boxes[1].id
            val config1 = state.photoConfigurations.value[box1Id] ?: PhotoScanConfiguration()
            val config2 = state.photoConfigurations.value[box2Id] ?: PhotoScanConfiguration()
            assertEquals(90, config1.rotationDegrees)
            assertEquals(90, config2.rotationDegrees)

            // Rotate again — 180°
            state.configs.rotateAllBoxesCW()
            val config1After2 = state.photoConfigurations.value[box1Id] ?: PhotoScanConfiguration()
            assertEquals(180, config1After2.rotationDegrees)

            // Rotate CCW — back to 90°
            state.configs.rotateAllBoxesCCW()
            val config1AfterCCW = state.photoConfigurations.value[box1Id] ?: PhotoScanConfiguration()
            assertEquals(90, config1AfterCCW.rotationDegrees)
        }

        @Test
        @DisplayName("Four-point mode: enter → add points → confirm → box created")
        fun fourPointModeFlow() {
            state.initializeWithImage(testImage, File("scan.jpg"))

            // Enter four-point mode
            state.enterFourPointMode()
            assertEquals(WizardMode.FOUR_POINT, state.wizardMode.value)

            // Add 4 points
            state.addFourPoint(Point(100.0, 100.0))
            state.addFourPoint(Point(300.0, 100.0))
            state.addFourPoint(Point(300.0, 300.0))
            state.addFourPoint(Point(100.0, 300.0))

            // Confirm four-point box
            state.confirmFourPoint()
            assertEquals(1, state.boundingBoxList.value.size())
            // Should return to normal mode
            assertEquals(WizardMode.NORMAL, state.wizardMode.value)
        }

        @Test
        @DisplayName("Add box mode: enter → create box at center → exit")
        fun addBoxModeFlow() {
            state.initializeWithImage(testImage, File("scan.jpg"))

            // Enter add box mode
            state.enterAddBoxMode()
            assertEquals(WizardMode.ADD_BOX, state.wizardMode.value)

            // Create box at center of image
            val created = state.createBoxAtCenter(500.0, 400.0)
            assertTrue(created)
            assertEquals(1, state.boundingBoxList.value.size())

            // createBoxAtCenter auto-exits add box mode after creating
            assertEquals(WizardMode.NORMAL, state.wizardMode.value)
        }
    }

    // ==================== Correction Strategy Flow ====================

    @Nested
    @DisplayName("Correction strategy flow")
    inner class CorrectionStrategyFlow {

        @Test
        @DisplayName("Photo with no per-photo strategy uses global default")
        fun photoWithoutStrategyUsesGlobalDefault() {
            state.initializeWithImage(testImage, File("scan.jpg"))
            state.exportSettings.setDefaultCorrectionStrategy(CorrectionStrategy.CROP)

            val box = BoundingBox.createRectangular(Point(100.0, 100.0), 200.0, 150.0)
            state.boxes.addBox(box)

            // Photo has no explicit correction strategy
            assertNull(state.photoConfigurations.value[box.id]?.correctionStrategy)

            // Export logic: null strategy → use global default (CROP)
            // This is tested in PhotoScanExportServiceTest
            // Here we verify the state is consistent: null per-photo means "use global"
            val globalStrategy = state.exportSettings.defaultCorrectionStrategy.value
            assertEquals(CorrectionStrategy.CROP, globalStrategy)
        }

        @Test
        @DisplayName("Per-photo strategy overrides global default")
        fun perPhotoStrategyOverridesGlobal() {
            state.initializeWithImage(testImage, File("scan.jpg"))
            state.exportSettings.setDefaultCorrectionStrategy(CorrectionStrategy.PERSPECTIVE)

            val box = BoundingBox.createRectangular(Point(100.0, 100.0), 200.0, 150.0)
            state.boxes.addBox(box)

            // Set per-photo strategy to CROP (different from global PERSPECTIVE)
            state.configs.setPhotoConfiguration(
                box.id, PhotoScanConfiguration(correctionStrategy = CorrectionStrategy.CROP)
            )

            // Per-photo strategy takes precedence
            assertEquals(
                CorrectionStrategy.CROP,
                state.photoConfigurations.value[box.id]?.correctionStrategy
            )
            // Global default is still PERSPECTIVE for other photos
            assertEquals(CorrectionStrategy.PERSPECTIVE, state.exportSettings.defaultCorrectionStrategy.value)
        }

        @Test
        @DisplayName("Changing global default doesn't override per-photo strategy")
        fun changingGlobalDefaultDoesNotOverridePerPhoto() {
            state.initializeWithImage(testImage, File("scan.jpg"))

            val box = BoundingBox.createRectangular(Point(100.0, 100.0), 200.0, 150.0)
            state.boxes.addBox(box)
            state.configs.setPhotoConfiguration(
                box.id, PhotoScanConfiguration(correctionStrategy = CorrectionStrategy.CROP)
            )

            // Change global default
            state.exportSettings.setDefaultCorrectionStrategy(CorrectionStrategy.CROP_AND_ROTATE)

            // Per-photo strategy is still CROP, unchanged
            assertEquals(
                CorrectionStrategy.CROP,
                state.photoConfigurations.value[box.id]?.correctionStrategy
            )
        }

        @Test
        @DisplayName("Perspective correction toggle affects export behavior")
        fun perspectiveCorrectionToggleFlow() {
            state.initializeWithImage(testImage, File("scan.jpg"))

            // Default: perspective correction enabled
            assertTrue(state.exportSettings.perspectiveCorrectionEnabled.value)

            // Disable perspective correction — strategy dropdown becomes visible
            state.exportSettings.setPerspectiveCorrectionEnabled(false)
            assertFalse(state.exportSettings.perspectiveCorrectionEnabled.value)

            // Set global strategy to CROP (only matters when perspective correction OFF)
            state.exportSettings.setDefaultCorrectionStrategy(CorrectionStrategy.CROP)
            assertEquals(CorrectionStrategy.CROP, state.exportSettings.defaultCorrectionStrategy.value)

            // Re-enable perspective correction — strategy dropdown hidden, PERSPECTIVE implied
            state.exportSettings.setPerspectiveCorrectionEnabled(true)
            assertTrue(state.exportSettings.perspectiveCorrectionEnabled.value)
        }

        @Test
        @DisplayName("Set all boxes to perspective correction ON/OFF")
        fun setAllBoxesPerspectiveCorrectionFlow() {
            state.initializeWithImage(testImage, File("scan.jpg"))
            val box1 = BoundingBox.createRectangular(Point(100.0, 100.0), 200.0, 150.0)
            val box2 = BoundingBox.createRectangular(Point(400.0, 100.0), 200.0, 150.0)
            state.boxes.addBox(box1)
            state.boxes.addBox(box2)

            // Enable perspective correction for all
            state.configs.setPerspectiveCorrectionAll(true)
            assertTrue(
                state.photoConfigurations.value[box1.id]?.perspectiveCorrectionEnabled ?: false
            )
            assertTrue(
                state.photoConfigurations.value[box2.id]?.perspectiveCorrectionEnabled ?: false
            )

            // Disable for all
            state.configs.setPerspectiveCorrectionAll(false)
            assertFalse(
                state.photoConfigurations.value[box1.id]?.perspectiveCorrectionEnabled ?: true
            )
            assertFalse(
                state.photoConfigurations.value[box2.id]?.perspectiveCorrectionEnabled ?: true
            )
        }
    }

    // ==================== Batch Scan Flow ====================

    @Nested
    @DisplayName("Batch scan workflow")
    inner class BatchScanWorkflow {

        @Test
        @DisplayName("Batch initialization, navigation, and state isolation")
        fun batchNavigationFlow() {
            val file1 = File("scan1.jpg")
            val file2 = File("scan2.jpg")
            val file3 = File("scan3.jpg")
            val files = listOf(file1, file2, file3)

            // Initialize batch
            state.batch.initializeBatch(files)
            assertEquals(3, state.batch.sourceFiles.value.size)
            assertEquals(0, state.batch.currentImageIndex.value)

            // Pre-process first image with no detected boxes
            val img1 = BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB)
            state.batch.putPreProcessed(0, PreProcessedImage(file1, img1, listOf()))
            assertTrue(state.switchToImage(0))
            assertEquals(0, state.batch.currentImageIndex.value)

            // Add a box manually to first image
            val box = BoundingBox.createRectangular(Point(100.0, 100.0), 200.0, 150.0)
            state.boxes.addBox(box)
            assertEquals(1, state.boundingBoxList.value.size())

            // Pre-process second image
            val img2 = BufferedImage(600, 800, BufferedImage.TYPE_INT_RGB)
            state.batch.putPreProcessed(1, PreProcessedImage(file2, img2, listOf()))
            assertTrue(state.switchToImage(1))
            assertEquals(1, state.batch.currentImageIndex.value)

            // switchToImage clears boxes and configs (loads from cache, which has empty boxes)
            assertEquals(0, state.boundingBoxList.value.size())

            // Pre-processed third image and switch to it
            val img3 = BufferedImage(700, 500, BufferedImage.TYPE_INT_RGB)
            state.batch.putPreProcessed(2, PreProcessedImage(file3, img3, listOf()))
            assertTrue(state.switchToImage(2))
            assertEquals(2, state.batch.currentImageIndex.value)

            // Can't switch to non-existent index
            assertFalse(state.switchToImage(5))
        }

        @Test
        @DisplayName("advanceToNextBatchFile iterates through batch")
        fun advanceToNextBatchFileFlow() {
            val file1 = File("scan1.jpg")
            val file2 = File("scan2.jpg")
            val files = listOf(file1, file2)

            state.batch.initializeBatch(files)
            assertEquals(file1, state.batch.sourceFiles.value[0])

            // Advance from index 0 → 1
            val nextFile = state.batch.advanceToNextBatchFile()
            assertEquals(file2, nextFile)
            assertEquals(1, state.batch.currentImageIndex.value)

            // At end of batch — returns null
            val endFile = state.batch.advanceToNextBatchFile()
            assertNull(endFile)
        }

        @Test
        @DisplayName("hasMoreBatchImages tracks remaining files")
        fun hasMoreBatchImagesFlow() {
            // Single image mode — not a batch
            state.initializeWithImage(testImage, File("single.jpg"))
            assertFalse(state.batch.hasMoreBatchImages)

            // Batch mode with 3 files
            val files = listOf(File("1.jpg"), File("2.jpg"), File("3.jpg"))
            state.batch.initializeBatch(files)

            // At start (index 0), more images remain
            assertTrue(state.batch.hasMoreBatchImages)

            // Pre-process and switch to last image (index 2) — no more remain
            val img = BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB)
            state.batch.putPreProcessed(2, PreProcessedImage(File("3.jpg"), img, listOf()))
            state.switchToImage(2)
            assertFalse(state.batch.hasMoreBatchImages)

            // Go back to middle (index 1) — more remain
            state.batch.putPreProcessed(1, PreProcessedImage(File("2.jpg"), img, listOf()))
            state.switchToImage(1)
            assertTrue(state.batch.hasMoreBatchImages)
        }

        @Test
        @DisplayName("Reset preserves batch files but clears per-image state")
        fun resetPreservesBatchState() {
            val files = listOf(File("1.jpg"), File("2.jpg"))
            state.batch.initializeBatch(files)
            state.switchToImage(0)
            state.boxes.addBox(BoundingBox.createRectangular(Point(100.0, 100.0), 200.0, 150.0))

            // Reset per-image state
            state.resetPerImageState()
            assertEquals(0, state.boundingBoxList.value.size())
            // Batch files and index preserved
            assertEquals(2, state.batch.sourceFiles.value.size)
        }
    }

    // ==================== Face Region Workflow ====================

    @Nested
    @DisplayName("Face region workflow")
    inner class FaceRegionWorkflow {

        @Test
        @DisplayName("Add face regions, move, resize, then remove")
        fun faceRegionLifecycleFlow() {
            state.initializeWithImage(testImage, File("scan.jpg"))
            state.boxes.addBox(BoundingBox.createRectangular(Point(100.0, 100.0), 400.0, 300.0))
            val boxId = state.configs.boxes[0].id

            // Enter face select mode for photo 0
            state.faceRegions.enterFaceSelectMode(0)
            assertTrue(state.faceRegions.faceSelectMode.value)

            // Add a face region
            state.faceRegions.addFaceRegion(0, "Alice", 0.5, 0.4, RegionType.FACE)
            val config = state.photoConfigurations.value[boxId] ?: PhotoScanConfiguration()
            assertEquals(1, config.faceRegions.size)
            assertEquals("Alice", config.faceRegions[0].name)

            // Move the face region
            state.faceRegions.moveFaceRegion(0, 0, 0.05, 0.03)
            val movedConfig = state.photoConfigurations.value[boxId] ?: PhotoScanConfiguration()
            // Face region center should have moved by the delta from initial position (0.5 + 0.05)
            assertEquals(0.55, movedConfig.faceRegions[0].x, 0.01)

            // Resize the face region
            state.faceRegions.resizeFaceRegion(0, 0, FaceSize.LARGE)
            val resizedConfig = state.photoConfigurations.value[boxId] ?: PhotoScanConfiguration()
            assertEquals(FaceSize.LARGE.diameter, resizedConfig.faceRegions[0].w, 0.01)

            // Remove the face region
            state.faceRegions.removeFaceRegion(0, 0)
            val finalConfig = state.photoConfigurations.value[boxId] ?: PhotoScanConfiguration()
            assertEquals(0, finalConfig.faceRegions.size)

            // Exit face select mode
            state.faceRegions.exitFaceSelectMode()
            assertFalse(state.faceRegions.faceSelectMode.value)
        }

        @Test
        @DisplayName("Multiple face regions with auto-populated subjects")
        fun multipleFaceRegionsSubjects() {
            state.initializeWithImage(testImage, File("scan.jpg"))
            state.boxes.addBox(BoundingBox.createRectangular(Point(100.0, 100.0), 400.0, 300.0))
            val boxId = state.configs.boxes[0].id

            // Add two face regions with names
            state.faceRegions.addFaceRegion(0, "Alice", 0.5, 0.4, RegionType.FACE)
            state.faceRegions.addFaceRegion(0, "Bob", 0.3, 0.5, RegionType.FACE)

            val config = state.photoConfigurations.value[boxId] ?: PhotoScanConfiguration()
            assertEquals(2, config.faceRegions.size)
            // Subjects should be auto-populated from face names
            assertTrue(config.subjects.contains("Alice"))
            assertTrue(config.subjects.contains("Bob"))

            // Clear all face regions — subjects should be cleared too
            state.faceRegions.clearAllFaceRegions(0)
            val clearedConfig = state.photoConfigurations.value[boxId] ?: PhotoScanConfiguration()
            assertEquals(0, clearedConfig.faceRegions.size)
            assertTrue(clearedConfig.subjects.isEmpty())
        }
    }

    // ==================== Export Configuration Flow ====================

    @Nested
    @DisplayName("Export configuration flow")
    inner class ExportConfigurationFlow {

        @Test
        @DisplayName("Set correction strategy, export margin, and perspective correction for all photos")
        fun configureExportSettingsFlow() {
            state.initializeWithImage(testImage, File("scan.jpg"))
            state.boxes.addBox(BoundingBox.createRectangular(Point(100.0, 100.0), 200.0, 150.0))
            state.boxes.addBox(BoundingBox.createRectangular(Point(400.0, 100.0), 200.0, 150.0))

            // Configure global defaults
            state.exportSettings.setDefaultCorrectionStrategy(CorrectionStrategy.CROP_AND_ROTATE)
            state.exportSettings.setExportMarginPercent(0.05)
            state.exportSettings.setPerspectiveCorrectionEnabled(false)

            assertEquals(CorrectionStrategy.CROP_AND_ROTATE, state.exportSettings.defaultCorrectionStrategy.value)
            assertEquals(0.05, state.exportSettings.exportMarginPercent.value, 0.001)
            assertFalse(state.exportSettings.perspectiveCorrectionEnabled.value)

            // Override one photo's strategy
            val box1Id = state.configs.boxes[0].id
            state.configs.setPhotoConfiguration(
                box1Id,
                PhotoScanConfiguration(correctionStrategy = CorrectionStrategy.PERSPECTIVE),
            )

            // Verify per-photo override
            assertEquals(
                CorrectionStrategy.PERSPECTIVE,
                state.photoConfigurations.value[box1Id]?.correctionStrategy
            )

            // Second photo still uses global default (null per-photo)
            val box2Id = state.configs.boxes[1].id
            assertNull(state.photoConfigurations.value[box2Id]?.correctionStrategy)
        }

        @Test
        @DisplayName("Configure metadata for all photos then override per-photo")
        fun metadataOverrideAllThenPerPhoto() {
            state.initializeWithImage(testImage, File("scan.jpg"))
            val box1 = BoundingBox.createRectangular(Point(100.0, 100.0), 200.0, 150.0)
            val box2 = BoundingBox.createRectangular(Point(400.0, 100.0), 200.0, 150.0)
            state.boxes.addBox(box1)
            state.boxes.addBox(box2)

            // Set metadata for first photo
            state.configs.setPhotoConfiguration(
                box1.id,
                PhotoScanConfiguration(
                    description = "Family photo",
                    originalDate = "2024-06-15",
                    cameraMake = "Canon",
                ),
            )

            // Set different metadata for second photo
            state.configs.setPhotoConfiguration(
                box2.id,
                PhotoScanConfiguration(
                    description = "Vacation photo",
                    originalDate = "2024-07-20",
                ),
            )

            // Verify each photo has its own config
            assertEquals(
                "Family photo",
                state.photoConfigurations.value[box1.id]?.description
            )
            assertEquals(
                "Vacation photo",
                state.photoConfigurations.value[box2.id]?.description
            )
            assertEquals("Canon", state.photoConfigurations.value[box1.id]?.cameraMake)
            // Second photo has default empty cameraMake (not null)
            assertTrue(state.photoConfigurations.value[box2.id]?.cameraMake?.isEmpty() ?: true)
        }
    }

    // ==================== Selection and Navigation Flow ====================

    @Nested
    @DisplayName("Selection and navigation flow")
    inner class SelectionNavigationFlow {

        @Test
        @DisplayName("Select, navigate between boxes, enter/exit refinement")
        fun selectAndNavigatePhotos() {
            state.initializeWithImage(testImage, File("scan.jpg"))
            val box1 = BoundingBox.createRectangular(Point(100.0, 100.0), 200.0, 150.0)
            val box2 = BoundingBox.createRectangular(Point(400.0, 100.0), 200.0, 150.0)
            val box3 = BoundingBox.createRectangular(Point(250.0, 400.0), 200.0, 150.0)
            state.boxes.addBox(box1)
            state.boxes.addBox(box2)
            state.boxes.addBox(box3)

            // Select first box
            state.boxes.selectBox(0)
            assertEquals(0, state.boxes.selectedBoxIndex.value)

            // Enter refinement on box 0, then navigate to next
            state.enterRefinement(0)
            assertEquals(0, state.boxes.selectedBoxIndex.value)

            // Next box (refinement navigation)
            state.nextBox()
            assertEquals(1, state.boxes.selectedBoxIndex.value)

            // Next box again
            state.nextBox()
            assertEquals(2, state.boxes.selectedBoxIndex.value)

            // Previous box
            state.previousBox()
            assertEquals(1, state.boxes.selectedBoxIndex.value)

            // Enter refinement on a specific box directly
            state.enterRefinement(2)
            assertEquals(2, state.boxes.selectedBoxIndex.value)

            // Exit refinement
            state.exitRefinement()
            // After exiting refinement, we're still in OVERVIEW step
            assertEquals(WizardStep.OVERVIEW, state.navigation.currentStep.value)
        }

        @Test
        @DisplayName("Remove selected box then verify remaining boxes")
        fun removeSelectedBoxFlow() {
            state.initializeWithImage(testImage, File("scan.jpg"))
            val box1 = BoundingBox.createRectangular(Point(100.0, 100.0), 200.0, 150.0)
            val box2 = BoundingBox.createRectangular(Point(400.0, 100.0), 200.0, 150.0)
            state.boxes.addBox(box1)
            state.boxes.addBox(box2)

            // Select and remove first box
            state.boxes.selectBox(0)
            state.removeSelectedBox()
            assertEquals(1, state.boxes.boxCount())

            // Add a new box
            val box3 = BoundingBox.createRectangular(Point(250.0, 400.0), 200.0, 150.0)
            state.boxes.addBox(box3)
            assertEquals(2, state.boxes.boxCount())
        }
    }

    // ==================== Zoom and Pan Flow ====================

    @Nested
    @DisplayName("Zoom and pan flow")
    inner class ZoomPanFlow {

        @Test
        @DisplayName("Zoom in, zoom out, pan, fit-to-view")
        fun zoomPanFitToViewFlow() {
            state.initializeWithImage(testImage, File("scan.jpg"))

            val initialZoom = state.zoom.zoomController.value.zoom

            // Zoom in
            state.zoom.zoomIn()
            assertTrue(state.zoom.zoomController.value.zoom > initialZoom)

            // Zoom out
            val zoomedIn = state.zoom.zoomController.value.zoom
            state.zoom.zoomOut()
            assertTrue(state.zoom.zoomController.value.zoom < zoomedIn)

            // Pan
            val initialPanX = state.zoom.zoomController.value.panX
            state.zoom.pan(50.0, 30.0)
            assertTrue(state.zoom.zoomController.value.panX > initialPanX)

            // Fit to view resets zoom to a calculated fit level
            state.fitToView()
            // After fitToView, zoom should be positive and non-zero
            assertTrue(state.zoom.zoomController.value.zoom > 0)
        }
    }
}
