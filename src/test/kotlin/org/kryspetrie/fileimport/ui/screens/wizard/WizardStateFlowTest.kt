package org.kryspetrie.fileimport.ui.screens.wizard

import java.awt.image.BufferedImage
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.model.CorrectionStrategy
import org.kryspetrie.fileimport.infrastructure.wizard.BoundingBox
import org.kryspetrie.fileimport.infrastructure.wizard.BoundingBoxCorners
import org.kryspetrie.fileimport.infrastructure.wizard.PhotoConfiguration
import org.kryspetrie.fileimport.infrastructure.wizard.PhotoScanWizardState
import org.kryspetrie.fileimport.infrastructure.wizard.WizardStep
import org.kryspetrie.fileimport.infrastructure.wizard.Point
import org.kryspetrie.fileimport.infrastructure.wizard.WizardMode

/**
 * Pure state-based flow tests for the wizard workflow.
 *
 * These tests verify state transitions, configuration changes, and data flow through
 * the PhotoScanWizardState without requiring Compose UI rendering. This makes them
 * fast, reliable, and runnable in any environment (including headless CI).
 *
 * Tested workflows:
 * - Wizard step transitions (IMPORT → OVERVIEW → SUMMARY → EDIT → PROCESSING → COMPLETE)
 * - Photo configuration per-photo overrides (rotation, correction strategy)
 * - Correction strategy inheritance (default vs. per-photo override)
 * - Perspective correction toggling
 * - Export margin changes
 * - Batch initialization and navigation state
 * - Box manipulation state (add, select, enter/exit refinement)
 * - Zoom controller state changes
 * - Reset/full-cycle workflows
 */
@DisplayName("Wizard State Transition Flow Tests")
class WizardStateFlowTest {

    private lateinit var wizardState: PhotoScanWizardState

    private fun createTestBox(
        x: Double = 100.0,
        y: Double = 100.0,
        width: Double = 200.0,
        height: Double = 100.0,
    ): BoundingBox =
        BoundingBox(
            corners =
                BoundingBoxCorners(
                    topLeft = Point(x, y),
                    topRight = Point(x + width, y),
                    bottomRight = Point(x + width, y + height),
                    bottomLeft = Point(x, y + height),
                )
        )

    private fun createWizardWithBoxes(boxCount: Int = 1): PhotoScanWizardState {
        val state = PhotoScanWizardState()
        val image = BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB)
        state.initializeWithImage(image, File("test-scan.jpg"))

        repeat(boxCount) { i ->
            state.addBox(createTestBox(x = 100.0 + i * 250.0, y = 100.0))
        }
        return state
    }

    // ================================================================
    // Wizard Step Transition Flow Tests
    // ================================================================

    @Nested
    @DisplayName("Wizard Step Transition Flow")
    inner class StepTransitionFlow {

        @Test
        @DisplayName("should start at IMPORT step")
        fun shouldStartAtImportStep() {
            val state = PhotoScanWizardState()
            assertThat(state.currentStep.value)
                .isEqualTo(WizardStep.IMPORT)
        }

        @Test
        @DisplayName("should transition IMPORT → OVERVIEW")
        fun shouldTransitionToOverview() {
            val state = PhotoScanWizardState()
            val image = BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB)
            state.initializeWithImage(image, File("test.jpg"))
            state.goToOverview()

            assertThat(state.currentStep.value)
                .isEqualTo(WizardStep.OVERVIEW)
        }

        @Test
        @DisplayName("should transition OVERVIEW → SUMMARY")
        fun shouldTransitionToSummary() {
            val state = createWizardWithBoxes()
            state.goToOverview()
            state.goToSummary()

            assertThat(state.currentStep.value)
                .isEqualTo(WizardStep.SUMMARY)
        }

        @Test
        @DisplayName("should transition SUMMARY → EDIT")
        fun shouldTransitionToEdit() {
            val state = createWizardWithBoxes()
            state.goToOverview()
            state.goToSummary()
            state.goToEdit()

            assertThat(state.currentStep.value)
                .isEqualTo(WizardStep.EDIT)
        }

        @Test
        @DisplayName("should transition EDIT → PROCESSING")
        fun shouldTransitionToProcessing() {
            val state = createWizardWithBoxes()
            state.goToOverview()
            state.goToSummary()
            state.goToEdit()
            state.goToProcessing()

            assertThat(state.currentStep.value)
                .isEqualTo(WizardStep.PROCESSING)
        }

        @Test
        @DisplayName("should transition PROCESSING → COMPLETE")
        fun shouldTransitionToComplete() {
            val state = createWizardWithBoxes()
            state.goToOverview()
            state.goToSummary()
            state.goToEdit()
            state.goToProcessing()
            state.goToComplete()

            assertThat(state.currentStep.value)
                .isEqualTo(WizardStep.COMPLETE)
        }

        @Test
        @DisplayName("should navigate full IMPORT → COMPLETE cycle")
        fun shouldNavigateFullCycle() {
            val state = createWizardWithBoxes()

            state.goToOverview()
            assertThat(state.currentStep.value).isEqualTo(WizardStep.OVERVIEW)

            state.goToSummary()
            assertThat(state.currentStep.value).isEqualTo(WizardStep.SUMMARY)

            state.goToEdit()
            assertThat(state.currentStep.value).isEqualTo(WizardStep.EDIT)

            state.goToProcessing()
            assertThat(state.currentStep.value).isEqualTo(WizardStep.PROCESSING)

            state.goToComplete()
            assertThat(state.currentStep.value).isEqualTo(WizardStep.COMPLETE)
        }

        @Test
        @DisplayName("should reset from COMPLETE back to IMPORT")
        fun shouldResetFromCompleteToImport() {
            val state = createWizardWithBoxes()

            state.goToOverview()
            state.goToSummary()
            state.goToEdit()
            state.goToProcessing()
            state.goToComplete()

            state.resetToImportStep()
            assertThat(state.currentStep.value).isEqualTo(WizardStep.IMPORT)
            assertThat(state.image.value).isNull()
        }

        @Test
        @DisplayName("should go back from SUMMARY → OVERVIEW")
        fun shouldGoBackFromSummaryToOverview() {
            val state = createWizardWithBoxes()
            state.goToOverview()
            state.goToSummary()
            assertThat(state.currentStep.value).isEqualTo(WizardStep.SUMMARY)

            state.goToOverview()
            assertThat(state.currentStep.value).isEqualTo(WizardStep.OVERVIEW)
        }

        @Test
        @DisplayName("should go back from EDIT → SUMMARY")
        fun shouldGoBackFromEditToSummary() {
            val state = createWizardWithBoxes()
            state.goToOverview()
            state.goToSummary()
            state.goToEdit()
            assertThat(state.currentStep.value).isEqualTo(WizardStep.EDIT)

            state.goToSummary()
            assertThat(state.currentStep.value).isEqualTo(WizardStep.SUMMARY)
        }

        @Test
        @DisplayName("should clear image on reset")
        fun shouldClearImageOnReset() {
            val state = createWizardWithBoxes()
            assertThat(state.image.value).isNotNull()

            state.resetToImportStep()
            assertThat(state.image.value).isNull()
        }
    }

    // ================================================================
    // Photo Configuration Flow Tests
    // ================================================================

    @Nested
    @DisplayName("Photo Configuration Flow")
    inner class PhotoConfigurationFlow {

        @BeforeEach
        fun setup() {
            wizardState = createWizardWithBoxes(boxCount = 3)
        }

        @Test
        @DisplayName("should start with empty configurations")
        fun shouldStartWithEmptyConfigurations() {
            assertThat(wizardState.photoConfigurations.value).isEmpty()
        }

        @Test
        @DisplayName("should set and retrieve per-photo configuration")
        fun shouldSetAndRetrievePerPhotoConfiguration() {
            val boxId = wizardState.boundingBoxList.value.boxes[0].id

            wizardState.setPhotoConfiguration(boxId, PhotoConfiguration(rotationDegrees = 90))

            assertThat(wizardState.photoConfigurations.value[boxId]?.rotationDegrees).isEqualTo(90)
        }

        @Test
        @DisplayName("should update existing configuration")
        fun shouldUpdateExistingConfiguration() {
            val boxId = wizardState.boundingBoxList.value.boxes[0].id
            wizardState.setPhotoConfiguration(boxId, PhotoConfiguration())

            wizardState.updatePhotoConfiguration(boxId) { it.copy(rotationDegrees = 45) }

            assertThat(wizardState.photoConfigurations.value[boxId]?.rotationDegrees).isEqualTo(45)
        }

        @Test
        @DisplayName("should rotate box clockwise via configuration")
        fun shouldRotateBoxClockwiseViaConfiguration() {
            val boxId = wizardState.boundingBoxList.value.boxes[0].id
            wizardState.setPhotoConfiguration(boxId, PhotoConfiguration())

            assertThat(wizardState.photoConfigurations.value[boxId]?.rotationDegrees).isEqualTo(0)

            wizardState.updatePhotoConfiguration(boxId) { it.copy(rotationDegrees = 90) }

            assertThat(wizardState.photoConfigurations.value[boxId]?.rotationDegrees).isEqualTo(90)
        }

        @Test
        @DisplayName("should maintain separate configs per photo")
        fun shouldMaintainSeparateConfigsPerPhoto() {
            val box1Id = wizardState.boundingBoxList.value.boxes[0].id
            val box2Id = wizardState.boundingBoxList.value.boxes[1].id

            wizardState.setPhotoConfiguration(
                box1Id,
                PhotoConfiguration(rotationDegrees = 90, correctionStrategy = CorrectionStrategy.CROP),
            )
            wizardState.setPhotoConfiguration(
                box2Id,
                PhotoConfiguration(rotationDegrees = 180, correctionStrategy = CorrectionStrategy.PERSPECTIVE),
            )

            val configs = wizardState.photoConfigurations.value
            assertThat(configs[box1Id]?.rotationDegrees).isEqualTo(90)
            assertThat(configs[box1Id]?.correctionStrategy).isEqualTo(CorrectionStrategy.CROP)
            assertThat(configs[box2Id]?.rotationDegrees).isEqualTo(180)
            assertThat(configs[box2Id]?.correctionStrategy).isEqualTo(CorrectionStrategy.PERSPECTIVE)
        }

        @Test
        @DisplayName("should clear all configurations at once")
        fun shouldClearAllConfigurationsAtOnce() {
            val box1Id = wizardState.boundingBoxList.value.boxes[0].id
            val box2Id = wizardState.boundingBoxList.value.boxes[1].id

            wizardState.setPhotoConfiguration(box1Id, PhotoConfiguration(rotationDegrees = 90))
            wizardState.setPhotoConfiguration(box2Id, PhotoConfiguration(rotationDegrees = 180))
            assertThat(wizardState.photoConfigurations.value).hasSize(2)

            wizardState.clearAllConfigurations()
            assertThat(wizardState.photoConfigurations.value).isEmpty()
        }

        @Test
        @DisplayName("should rotate all boxes CW via state method")
        fun shouldRotateAllBoxesCW() {
            val box1Id = wizardState.boundingBoxList.value.boxes[0].id
            val box2Id = wizardState.boundingBoxList.value.boxes[1].id

            wizardState.setPhotoConfiguration(box1Id, PhotoConfiguration(rotationDegrees = 0))
            wizardState.setPhotoConfiguration(box2Id, PhotoConfiguration(rotationDegrees = 90))

            wizardState.rotateAllBoxesCW()

            val configs = wizardState.photoConfigurations.value
            assertThat(configs[box1Id]?.rotationDegrees).isEqualTo(90)
            assertThat(configs[box2Id]?.rotationDegrees).isEqualTo(180)
        }

        @Test
        @DisplayName("should toggle perspective correction")
        fun shouldTogglePerspectiveCorrection() {
            assertThat(wizardState.perspectiveCorrectionEnabled.value).isTrue()

            wizardState.setPerspectiveCorrectionEnabled(false)
            assertThat(wizardState.perspectiveCorrectionEnabled.value).isFalse()

            wizardState.setPerspectiveCorrectionEnabled(true)
            assertThat(wizardState.perspectiveCorrectionEnabled.value).isTrue()
        }

        @Test
        @DisplayName("should change export margin")
        fun shouldChangeExportMargin() {
            wizardState.setExportMarginPercent(0.05)
            assertThat(wizardState.exportMarginPercent.value).isCloseTo(
                0.05,
                org.assertj.core.data.Offset.offset(0.001),
            )
        }
    }

    // ================================================================
    // Correction Strategy Flow Tests
    // ================================================================

    @Nested
    @DisplayName("Correction Strategy Flow")
    inner class CorrectionStrategyFlow {

        @BeforeEach
        fun setup() {
            wizardState = createWizardWithBoxes(boxCount = 2)
        }

        @Test
        @DisplayName("should default to PERSPECTIVE correction strategy")
        fun shouldDefaultToPerspectiveStrategy() {
            assertThat(wizardState.defaultCorrectionStrategy.value)
                .isEqualTo(CorrectionStrategy.PERSPECTIVE)
        }

        @Test
        @DisplayName("should change default correction strategy")
        fun shouldChangeDefaultCorrectionStrategy() {
            wizardState.setDefaultCorrectionStrategy(CorrectionStrategy.CROP)
            assertThat(wizardState.defaultCorrectionStrategy.value).isEqualTo(CorrectionStrategy.CROP)

            wizardState.setDefaultCorrectionStrategy(CorrectionStrategy.CROP_AND_ROTATE)
            assertThat(wizardState.defaultCorrectionStrategy.value)
                .isEqualTo(CorrectionStrategy.CROP_AND_ROTATE)
        }

        @Test
        @DisplayName("should set per-photo correction strategy override")
        fun shouldSetPerPhotoCorrectionStrategyOverride() {
            val boxId = wizardState.boundingBoxList.value.boxes[0].id

            wizardState.setPhotoConfiguration(
                boxId,
                PhotoConfiguration(correctionStrategy = CorrectionStrategy.CROP_AND_ROTATE),
            )

            assertThat(wizardState.photoConfigurations.value[boxId]?.correctionStrategy)
                .isEqualTo(CorrectionStrategy.CROP_AND_ROTATE)
        }

        @Test
        @DisplayName("should clear per-photo correction strategy by setting to null")
        fun shouldClearPerPhotoCorrectionStrategy() {
            val boxId = wizardState.boundingBoxList.value.boxes[0].id

            wizardState.setPhotoConfiguration(
                boxId,
                PhotoConfiguration(correctionStrategy = CorrectionStrategy.CROP),
            )
            assertThat(wizardState.photoConfigurations.value[boxId]?.correctionStrategy)
                .isEqualTo(CorrectionStrategy.CROP)

            wizardState.setPhotoConfiguration(boxId, PhotoConfiguration(correctionStrategy = null))
            assertThat(wizardState.photoConfigurations.value[boxId]?.correctionStrategy).isNull()
        }

        @Test
        @DisplayName("should set different strategies for different photos")
        fun shouldSetDifferentStrategiesForDifferentPhotos() {
            val box1Id = wizardState.boundingBoxList.value.boxes[0].id
            val box2Id = wizardState.boundingBoxList.value.boxes[1].id

            wizardState.setPhotoConfiguration(
                box1Id,
                PhotoConfiguration(correctionStrategy = CorrectionStrategy.CROP),
            )
            wizardState.setPhotoConfiguration(
                box2Id,
                PhotoConfiguration(correctionStrategy = CorrectionStrategy.PERSPECTIVE),
            )

            val configs = wizardState.photoConfigurations.value
            assertThat(configs[box1Id]?.correctionStrategy).isEqualTo(CorrectionStrategy.CROP)
            assertThat(configs[box2Id]?.correctionStrategy).isEqualTo(CorrectionStrategy.PERSPECTIVE)
        }

        @Test
        @DisplayName("null correction strategy means 'use global default'")
        fun nullCorrectionStrategyMeansUseGlobalDefault() {
            val boxId = wizardState.boundingBoxList.value.boxes[0].id

            wizardState.setDefaultCorrectionStrategy(CorrectionStrategy.CROP)
            wizardState.setPhotoConfiguration(boxId, PhotoConfiguration(correctionStrategy = null))

            assertThat(wizardState.photoConfigurations.value[boxId]?.correctionStrategy).isNull()
            assertThat(wizardState.defaultCorrectionStrategy.value).isEqualTo(CorrectionStrategy.CROP)
        }

        @Test
        @DisplayName("photo-level override should take precedence over global default")
        fun photoLevelOverrideShouldTakePrecedenceOverGlobalDefault() {
            val boxId = wizardState.boundingBoxList.value.boxes[0].id

            wizardState.setDefaultCorrectionStrategy(CorrectionStrategy.PERSPECTIVE)
            wizardState.setPhotoConfiguration(
                boxId,
                PhotoConfiguration(correctionStrategy = CorrectionStrategy.CROP),
            )

            assertThat(wizardState.photoConfigurations.value[boxId]?.correctionStrategy)
                .isEqualTo(CorrectionStrategy.CROP)
        }

        @Test
        @DisplayName("should persist strategy change across all three strategies")
        fun shouldPersistStrategyChangeAcrossAllThreeStrategies() {
            for (strategy in CorrectionStrategy.entries) {
                wizardState.setDefaultCorrectionStrategy(strategy)
                assertThat(wizardState.defaultCorrectionStrategy.value).isEqualTo(strategy)
            }
        }
    }

    // ================================================================
    // Box Manipulation Flow Tests
    // ================================================================

    @Nested
    @DisplayName("Box Manipulation Flow")
    inner class BoxManipulationFlow {

        @BeforeEach
        fun setup() {
            wizardState = PhotoScanWizardState()
            val testImage = BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB)
            wizardState.initializeWithImage(testImage, File("test-boxes.jpg"))
        }

        @Test
        @DisplayName("should add boxes and track count")
        fun shouldAddBoxesAndTrackCount() {
            assertThat(wizardState.boundingBoxList.value.size()).isEqualTo(0)

            wizardState.addBox(createTestBox())
            assertThat(wizardState.boundingBoxList.value.size()).isEqualTo(1)

            wizardState.addBox(createTestBox(x = 400.0))
            assertThat(wizardState.boundingBoxList.value.size()).isEqualTo(2)
        }

        @Test
        @DisplayName("should select and deselect boxes")
        fun shouldSelectAndDeselectBoxes() {
            wizardState.addBox(createTestBox())
            wizardState.addBox(createTestBox(x = 400.0))

            assertThat(wizardState.selectedBoxIndex.value).isEqualTo(-1)

            wizardState.selectBox(0)
            assertThat(wizardState.selectedBoxIndex.value).isEqualTo(0)

            wizardState.selectBox(1)
            assertThat(wizardState.selectedBoxIndex.value).isEqualTo(1)

            wizardState.deselectAll()
            assertThat(wizardState.selectedBoxIndex.value).isEqualTo(-1)
        }

        @Test
        @DisplayName("should enter and exit refinement mode")
        fun shouldEnterAndExitRefinementMode() {
            wizardState.addBox(createTestBox())

            wizardState.enterRefinement(0)
            assertThat(wizardState.refinementBoxIndex.value).isEqualTo(0)

            wizardState.exitRefinement()
            assertThat(wizardState.refinementBoxIndex.value).isEqualTo(-1)
        }

        @Test
        @DisplayName("should navigate boxes in refinement mode")
        fun shouldNavigateBoxesInRefinementMode() {
            wizardState.addBox(createTestBox(x = 10.0, y = 10.0, width = 100.0, height = 80.0))
            wizardState.addBox(createTestBox(x = 250.0, y = 10.0, width = 100.0, height = 80.0))
            wizardState.addBox(createTestBox(x = 500.0, y = 10.0, width = 100.0, height = 80.0))

            wizardState.enterRefinement(0)
            assertThat(wizardState.refinementBoxIndex.value).isEqualTo(0)
            assertThat(wizardState.boundingBoxList.value.size()).isEqualTo(3)

            // nextBox() cycles through boxes using modular arithmetic
            wizardState.nextBox()
            assertThat(wizardState.refinementBoxIndex.value).isEqualTo(1)

            // previousBox() goes back
            wizardState.previousBox()
            assertThat(wizardState.refinementBoxIndex.value).isEqualTo(0)

            // Wrapping behavior: previousBox from 0 goes to last box
            wizardState.previousBox()
            assertThat(wizardState.refinementBoxIndex.value).isEqualTo(2)
        }

        @Test
        @DisplayName("should remove box by index")
        fun shouldRemoveBoxByIndex() {
            wizardState.addBox(createTestBox())
            wizardState.addBox(createTestBox(x = 400.0))
            assertThat(wizardState.boundingBoxList.value.size()).isEqualTo(2)

            wizardState.removeBox(0)
            assertThat(wizardState.boundingBoxList.value.size()).isEqualTo(1)
        }

        @Test
        @DisplayName("should enter four-point mode and add points")
        fun shouldEnterFourPointModeAndAddPoints() {
            wizardState.enterFourPointMode()
            assertThat(wizardState.wizardMode.value).isEqualTo(WizardMode.FOUR_POINT)

            wizardState.addFourPoint(Point(100.0, 100.0))
            assertThat(wizardState.fourPointState.value.points.size).isGreaterThanOrEqualTo(1)

            wizardState.exitFourPointMode()
            assertThat(wizardState.wizardMode.value).isEqualTo(WizardMode.NORMAL)
        }

        @Test
        @DisplayName("should create box at center and auto-exit add-box mode")
        fun shouldCreateBoxAtCenterAndAutoExitAddBoxMode() {
            assertThat(wizardState.boundingBoxList.value.size()).isEqualTo(0)

            wizardState.createBoxAtCenter(400.0, 300.0)
            assertThat(wizardState.wizardMode.value).isEqualTo(WizardMode.NORMAL)
            assertThat(wizardState.boundingBoxList.value.size()).isGreaterThan(0)
        }
    }

    // ================================================================
    // Zoom Controller Flow Tests
    // ================================================================

    @Nested
    @DisplayName("Zoom Controller Flow")
    inner class ZoomControllerFlow {

        @BeforeEach
        fun setup() {
            wizardState = PhotoScanWizardState()
            val testImage = BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB)
            wizardState.initializeWithImage(testImage, File("test-zoom.jpg"))
        }

        @Test
        @DisplayName("should start with default zoom")
        fun shouldStartWithDefaultZoom() {
            assertThat(wizardState.zoomController.value.zoom).isEqualTo(1.0)
        }

        @Test
        @DisplayName("should zoom in and increase zoom level")
        fun shouldZoomIn() {
            val initialZoom = wizardState.zoomController.value.zoom
            wizardState.zoomIn()
            assertThat(wizardState.zoomController.value.zoom).isGreaterThan(initialZoom)
        }

        @Test
        @DisplayName("should zoom out and decrease zoom level")
        fun shouldZoomOut() {
            wizardState.zoomIn()
            val zoomedIn = wizardState.zoomController.value.zoom
            wizardState.zoomOut()
            assertThat(wizardState.zoomController.value.zoom).isLessThan(zoomedIn)
        }

        @Test
        @DisplayName("should not zoom below minimum")
        fun shouldNotZoomBelowMinimum() {
            repeat(20) { wizardState.zoomOut() }
            val minZoom = wizardState.zoomController.value.minZoom
            assertThat(wizardState.zoomController.value.zoom).isGreaterThanOrEqualTo(minZoom)
        }

        @Test
        @DisplayName("should fit to view resetting zoom to calculated level")
        fun shouldFitToView() {
            wizardState.zoomIn()
            wizardState.zoomIn()
            assertThat(wizardState.zoomController.value.zoom).isGreaterThan(1.0)

            wizardState.fitToView(800.0, 600.0)
            assertThat(wizardState.zoomController.value.zoom).isGreaterThan(0.0)
        }
    }

    // ================================================================
    // Batch Mode Flow Tests
    // ================================================================

    @Nested
    @DisplayName("Batch Mode Flow")
    inner class BatchModeFlow {

        @Test
        @DisplayName("should initialize batch mode with files")
        fun shouldInitializeBatchModeWithFiles() {
            val state = PhotoScanWizardState()
            val files = listOf(File("photo1.jpg"), File("photo2.jpg"), File("photo3.jpg"))

            state.initializeBatch(files)

            assertThat(state.isBatchMode).isTrue()
            assertThat(state.batchTotal).isEqualTo(3)
            assertThat(state.currentImageIndex.value).isEqualTo(0)
        }

        @Test
        @DisplayName("should check hasMoreBatchImages")
        fun shouldCheckHasMoreBatchImages() {
            val state = PhotoScanWizardState()
            val files = listOf(File("photo1.jpg"), File("photo2.jpg"), File("photo3.jpg"))

            state.initializeBatch(files)
            assertThat(state.hasMoreBatchImages).isTrue()
        }

        @Test
        @DisplayName("should advance to next batch image")
        fun shouldAdvanceToNextBatchImage() {
            val state = PhotoScanWizardState()
            val files = listOf(File("photo1.jpg"), File("photo2.jpg"), File("photo3.jpg"))

            state.initializeBatch(files)
            assertThat(state.currentImageIndex.value).isEqualTo(0)

            state.advanceToNextBatchFile()
            assertThat(state.currentImageIndex.value).isEqualTo(1)
        }

        @Test
        @DisplayName("should skip next batch file")
        fun shouldSkipNextBatchFile() {
            val state = PhotoScanWizardState()
            val files = listOf(File("photo1.jpg"), File("photo2.jpg"), File("photo3.jpg"))

            state.initializeBatch(files)
            state.skipNextBatchFile()

            assertThat(state.skippedBatchIndices.value).contains(1)
        }
    }

    // ================================================================
    // Full End-to-End Simulation Flow
    // ================================================================

    @Nested
    @DisplayName("Full End-to-End Simulation Flow")
    inner class FullEndToEndSimulationFlow {

        @Test
        @DisplayName("should simulate full import → detect → configure → export-ready workflow")
        fun shouldSimulateFullWorkflow() {
            val state = PhotoScanWizardState()

            // 1. IMPORT: Initialize with image
            val image = BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB)
            state.initializeWithImage(image, File("family-photo.jpg"))
            state.goToOverview()
            assertThat(state.currentStep.value).isEqualTo(WizardStep.OVERVIEW)

            // 2. DETECT: Simulate CV detection — add boxes
            state.addBox(createTestBox(x = 50.0, y = 50.0, width = 300.0, height = 200.0))
            state.addBox(createTestBox(x = 400.0, y = 50.0, width = 300.0, height = 200.0))
            assertThat(state.boundingBoxList.value.size()).isEqualTo(2)

            // 3. CONFIGURE: Set per-photo strategies and rotations
            val box1Id = state.boundingBoxList.value.boxes[0].id
            val box2Id = state.boundingBoxList.value.boxes[1].id

            state.setDefaultCorrectionStrategy(CorrectionStrategy.PERSPECTIVE)
            state.setPhotoConfiguration(box1Id, PhotoConfiguration(correctionStrategy = CorrectionStrategy.CROP))
            state.updatePhotoConfiguration(box2Id) { it.copy(rotationDegrees = 90) }

            assertThat(state.defaultCorrectionStrategy.value).isEqualTo(CorrectionStrategy.PERSPECTIVE)
            assertThat(state.photoConfigurations.value[box1Id]?.correctionStrategy).isEqualTo(CorrectionStrategy.CROP)
            assertThat(state.photoConfigurations.value[box2Id]?.rotationDegrees).isEqualTo(90)

            // 4. NAVIGATE: Overview → Summary
            state.goToSummary()
            assertThat(state.currentStep.value).isEqualTo(WizardStep.SUMMARY)

            // 5. CONFIGURE MORE: Rotate all, change strategy
            state.rotateAllBoxesCW()
            state.setDefaultCorrectionStrategy(CorrectionStrategy.CROP_AND_ROTATE)

            assertThat(state.photoConfigurations.value[box1Id]?.rotationDegrees).isEqualTo(90)
            assertThat(state.defaultCorrectionStrategy.value).isEqualTo(CorrectionStrategy.CROP_AND_ROTATE)

            // 6. NAVIGATE: Summary → Edit
            state.goToEdit()
            assertThat(state.currentStep.value).isEqualTo(WizardStep.EDIT)

            // 7. NAVIGATE: Edit → Processing → Complete
            state.goToProcessing()
            state.goToComplete()
            assertThat(state.currentStep.value).isEqualTo(WizardStep.COMPLETE)

            // 8. Verify batch info
            assertThat(state.boxCount()).isEqualTo(2)
            assertThat(state.imageFile.value).isNotNull()
            assertThat(state.imageFile.value?.name).isEqualTo("family-photo.jpg")

            // 9. RESET: Back to start
            state.resetToImportStep()
            assertThat(state.currentStep.value).isEqualTo(WizardStep.IMPORT)
            assertThat(state.image.value).isNull()
            assertThat(state.boundingBoxList.value.size()).isEqualTo(0)
            assertThat(state.photoConfigurations.value).isEmpty()
        }

        @Test
        @DisplayName("should simulate batch workflow with navigation")
        fun shouldSimulateBatchWorkflowWithNavigation() {
            val state = PhotoScanWizardState()
            val files = listOf(File("photo1.jpg"), File("photo2.jpg"), File("photo3.jpg"))

            state.initializeBatch(files)

            assertThat(state.isBatchMode).isTrue()
            assertThat(state.batchTotal).isEqualTo(3)
            assertThat(state.currentImageIndex.value).isEqualTo(0)
            assertThat(state.hasMoreBatchImages).isTrue()

            // Advance to next batch image (0 -> 1)
            state.advanceToNextBatchFile()
            assertThat(state.currentImageIndex.value).isEqualTo(1)
            assertThat(state.hasMoreBatchImages).isTrue() // index 1 < size-1=2

            // Skip next image: skips index 2, and also advances to it
            state.skipNextBatchFile()
            assertThat(state.skippedBatchIndices.value).contains(2)
            // After skip, currentImageIndex has advanced past the skipped image
            assertThat(state.currentImageIndex.value).isEqualTo(2)
            // At last image, no more batch images
            assertThat(state.hasMoreBatchImages).isFalse()
        }

        @Test
        @DisplayName("should simulate single-photo mode workflow")
        fun shouldSimulateSinglePhotoModeWorkflow() {
            val state = PhotoScanWizardState()

            val image = BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB)
            state.initializeSinglePhoto(image, File("portrait.jpg"))
            assertThat(state.singlePhotoMode.value).isTrue()
            assertThat(state.image.value).isNotNull()
            assertThat(state.boundingBoxList.value.size()).isEqualTo(1)
            assertThat(state.currentStep.value).isEqualTo(WizardStep.EDIT)

            state.resetToImportStep()
            assertThat(state.image.value).isNull()
            assertThat(state.singlePhotoMode.value).isFalse()
        }
    }
}
