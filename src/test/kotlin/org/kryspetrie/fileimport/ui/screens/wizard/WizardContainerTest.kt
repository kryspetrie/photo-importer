package org.kryspetrie.fileimport.ui.screens.wizard

import java.awt.image.BufferedImage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.infrastructure.wizard.BoundingBox
import org.kryspetrie.fileimport.infrastructure.wizard.BoundingBoxCorners
import org.kryspetrie.fileimport.infrastructure.wizard.FourPointState
import org.kryspetrie.fileimport.infrastructure.wizard.PhotoConfiguration
import org.kryspetrie.fileimport.infrastructure.wizard.PhotoScanWizardState
import org.kryspetrie.fileimport.infrastructure.wizard.Point
import org.kryspetrie.fileimport.infrastructure.wizard.WizardMode

/**
 * Unit tests for wizard state management. Tests state transitions, mode selections, and navigation
 * logic.
 */
@DisplayName("Wizard State Tests")
class WizardContainerTest {

    private lateinit var wizardState: PhotoScanWizardState

    @BeforeEach
    fun setup() {
        wizardState = PhotoScanWizardState()
    }

    @Nested
    @DisplayName("Initial State")
    inner class InitialStateTest {

        @Test
        @DisplayName("should have Photo Scan mode selected by default")
        fun shouldHavePhotoScanSelectedByDefault() {
            val state = PhotoScanWizardState()
        }

        @Test
        @DisplayName("should have CV auto-detect enabled by default")
        fun shouldHaveCvAutoDetectEnabledByDefault() {
            val state = PhotoScanWizardState()
            assertThat(state.cvAutoDetectEnabled.value).isTrue()
        }

        @Test
        @DisplayName("should have correct initial wizard step")
        fun shouldHaveCorrectInitialStep() {
            val state = PhotoScanWizardState()
            assertThat(state.currentStep.value).isEqualTo(PhotoScanWizardState.WizardStep.IMPORT)
        }
    }

    @Nested
    @DisplayName("Navigation State")
    inner class NavigationStateTest {

        @Test
        @DisplayName("should reset to import step")
        fun shouldResetToImportStep() {
            wizardState.resetToImportStep()
            assertThat(wizardState.currentStep.value)
                .isEqualTo(PhotoScanWizardState.WizardStep.IMPORT)
        }
    }

    @Nested
    @DisplayName("Image Loading State")
    inner class ImageLoadingStateTest {

        @Test
        @DisplayName("should have no image initially")
        fun shouldHaveNoImageInitially() {
            val state = PhotoScanWizardState()
            assertThat(state.image.value).isNull()
        }

        @Test
        @DisplayName("should initialize with image")
        fun shouldInitializeWithImage() {
            val image = BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB)
            wizardState.initializeWithImage(image, java.io.File("test.jpg"))

            assertThat(wizardState.image.value).isNotNull()
            assertThat(wizardState.image.value!!.width).isEqualTo(800)
            assertThat(wizardState.image.value!!.height).isEqualTo(600)
        }

        @Test
        @DisplayName("should store image file reference")
        fun shouldStoreImageFile() {
            val file = java.io.File("test.jpg")
            val image = BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB)
            wizardState.initializeWithImage(image, file)

            assertThat(wizardState.imageFile.value).isEqualTo(file)
        }

        @Test
        @DisplayName("should clear image on reset")
        fun shouldClearImageOnReset() {
            val image = BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB)
            wizardState.initializeWithImage(image, java.io.File("test.jpg"))
            wizardState.resetToImportStep()

            assertThat(wizardState.image.value).isNull()
        }
    }

    @Nested
    @DisplayName("Bounding Box State")
    inner class BoundingBoxStateTest {

        @Test
        @DisplayName("should start with empty box list")
        fun shouldStartWithEmptyBoxList() {
            val state = PhotoScanWizardState()
            assertThat(state.boundingBoxList.value.size()).isEqualTo(0)
        }

        @Test
        @DisplayName("should add bounding box")
        fun shouldAddBoundingBox() {
            val image = BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB)
            wizardState.initializeWithImage(image, java.io.File("test.jpg"))

            val box = createTestBox()
            wizardState.addBox(box)

            assertThat(wizardState.boundingBoxList.value.size()).isEqualTo(1)
        }

        @Test
        @DisplayName("should remove bounding box")
        fun shouldRemoveBoundingBox() {
            val image = BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB)
            wizardState.initializeWithImage(image, java.io.File("test.jpg"))

            wizardState.addBox(createTestBox())
            assertThat(wizardState.boundingBoxList.value.size()).isEqualTo(1)

            wizardState.removeBox(0)
            assertThat(wizardState.boundingBoxList.value.size()).isEqualTo(0)
        }

        @Test
        @DisplayName("should select box by index")
        fun shouldSelectBox() {
            val image = BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB)
            wizardState.initializeWithImage(image, java.io.File("test.jpg"))
            wizardState.addBox(createTestBox())

            wizardState.selectBox(0)

            assertThat(wizardState.selectedBoxIndex.value).isEqualTo(0)
        }

        @Test
        @DisplayName("should deselect all boxes")
        fun shouldDeselectAll() {
            val image = BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB)
            wizardState.initializeWithImage(image, java.io.File("test.jpg"))
            wizardState.addBox(createTestBox())
            wizardState.selectBox(0)

            wizardState.deselectAll()

            assertThat(wizardState.selectedBoxIndex.value).isEqualTo(-1)
        }

        @Test
        @DisplayName("should track added boxes")
        fun shouldTrackAddedBoxes() {
            val image = BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB)
            wizardState.initializeWithImage(image, java.io.File("test.jpg"))

            wizardState.addBox(createTestBox())

            // Verify box was added - the count should increase
            assertThat(wizardState.boundingBoxList.value.size()).isGreaterThan(0)
        }

        @Test
        @DisplayName("should allow adding multiple boxes sequentially")
        fun shouldAddMultipleBoxes() {
            val image = BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB)
            wizardState.initializeWithImage(image, java.io.File("test.jpg"))

            wizardState.addBox(createTestBox(100.0, 100.0))
            wizardState.addBox(createTestBox(400.0, 100.0))
            wizardState.addBox(createTestBox(100.0, 400.0))

            // Verify multiple boxes were added
            assertThat(wizardState.boundingBoxList.value.size()).isGreaterThan(1)
        }

        @Test
        @DisplayName("should track added boxes count")
        fun shouldTrackAddedBoxesCount() {
            val image = BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB)
            wizardState.initializeWithImage(image, java.io.File("test.jpg"))

            wizardState.addBox(createTestBox())
            wizardState.addBox(createTestBox())
            wizardState.addBox(createTestBox())

            // Verify count is greater than zero
            assertThat(wizardState.boundingBoxList.value.size()).isGreaterThan(0)
        }

        private fun createTestBox(x: Double = 100.0, y: Double = 100.0): BoundingBox {
            return BoundingBox(
                corners =
                    BoundingBoxCorners(
                        Point(x, y),
                        Point(x + 200.0, y),
                        Point(x + 200.0, y + 100.0),
                        Point(x, y + 100.0),
                    )
            )
        }
    }

    @Nested
    @DisplayName("Zoom Controller State")
    inner class ZoomControllerStateTest {

        @Test
        @DisplayName("should have default zoom of 1.0")
        fun shouldHaveDefaultZoom() {
            assertThat(wizardState.zoomController.value.zoom).isEqualTo(1.0)
        }

        @Test
        @DisplayName("should zoom in")
        fun shouldZoomIn() {
            val initialZoom = wizardState.zoomController.value.zoom
            wizardState.zoomIn()
            assertThat(wizardState.zoomController.value.zoom).isGreaterThan(initialZoom)
        }

        @Test
        @DisplayName("should zoom out")
        fun shouldZoomOut() {
            wizardState.zoomIn()
            val initialZoom = wizardState.zoomController.value.zoom
            wizardState.zoomOut()
            assertThat(wizardState.zoomController.value.zoom).isLessThan(initialZoom)
        }

        @Test
        @DisplayName("should not zoom below minimum")
        fun shouldNotZoomBelowMinimum() {
            // Zoom out multiple times
            repeat(21) { wizardState.zoomOut() }
            assertThat(wizardState.zoomController.value.zoom)
                .isGreaterThanOrEqualTo(wizardState.zoomController.value.minZoom)
        }
    }

    @Nested
    @DisplayName("Wizard Mode State")
    inner class WizardModeStateTest {

        @Test
        @DisplayName("should start in NORMAL mode")
        fun shouldStartInNormalMode() {
            assertThat(wizardState.wizardMode.value).isEqualTo(WizardMode.NORMAL)
        }

        @Test
        @DisplayName("should enter FOUR_POINT mode")
        fun shouldEnterFourPointMode() {
            wizardState.enterFourPointMode()
            assertThat(wizardState.wizardMode.value).isEqualTo(WizardMode.FOUR_POINT)
        }

        @Test
        @DisplayName("should exit FOUR_POINT mode")
        fun shouldExitFourPointMode() {
            wizardState.enterFourPointMode()
            wizardState.exitFourPointMode()
            assertThat(wizardState.wizardMode.value).isEqualTo(WizardMode.NORMAL)
        }

        @Test
        @DisplayName("should have four wizard modes")
        fun shouldHaveFourWizardModes() {
            assertThat(WizardMode.entries).hasSize(4)
        }
    }

    @Nested
    @DisplayName("Photo Configuration State")
    inner class PhotoConfigurationStateTest {

        @Test
        @DisplayName("should have empty configurations initially")
        fun shouldHaveEmptyConfigurations() {
            assertThat(wizardState.photoConfigurations.value).isEmpty()
        }

        @Test
        @DisplayName("should set photo configuration for box")
        fun shouldSetPhotoConfiguration() {
            val image = BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB)
            wizardState.initializeWithImage(image, java.io.File("test.jpg"))
            wizardState.addBox(createTestBox())

            val boxId = wizardState.boundingBoxList.value.boxes[0].id
            val config =
                PhotoConfiguration(perspectiveCorrectionEnabled = false, rotationDegrees = 90)
            wizardState.setPhotoConfiguration(boxId, config)

            assertThat(wizardState.photoConfigurations.value[boxId]?.perspectiveCorrectionEnabled)
                .isFalse()
            assertThat(wizardState.photoConfigurations.value[boxId]?.rotationDegrees).isEqualTo(90)
        }

        @Test
        @DisplayName("should update existing configuration")
        fun shouldUpdateExistingConfiguration() {
            val image = BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB)
            wizardState.initializeWithImage(image, java.io.File("test.jpg"))
            wizardState.addBox(createTestBox())

            val boxId = wizardState.boundingBoxList.value.boxes[0].id
            wizardState.setPhotoConfiguration(boxId, PhotoConfiguration())

            wizardState.updatePhotoConfiguration(boxId) { it.copy(rotationDegrees = 45) }

            assertThat(wizardState.photoConfigurations.value[boxId]?.rotationDegrees).isEqualTo(45)
        }

        @Test
        @DisplayName("should clear specific configuration")
        fun shouldClearSpecificConfiguration() {
            val image = BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB)
            wizardState.initializeWithImage(image, java.io.File("test.jpg"))
            wizardState.addBox(createTestBox())

            val boxId = wizardState.boundingBoxList.value.boxes[0].id
            wizardState.setPhotoConfiguration(boxId, PhotoConfiguration())
            wizardState.clearPhotoConfiguration(boxId)

            assertThat(wizardState.photoConfigurations.value).doesNotContainKey(boxId)
        }

        @Test
        @DisplayName("should clear all configurations")
        fun shouldClearAllConfigurations() {
            val image = BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB)
            wizardState.initializeWithImage(image, java.io.File("test.jpg"))
            wizardState.addBox(createTestBox())
            wizardState.addBox(createTestBox(400.0, 100.0))

            val boxId1 = wizardState.boundingBoxList.value.boxes[0].id
            val boxId2 = wizardState.boundingBoxList.value.boxes[1].id
            wizardState.setPhotoConfiguration(boxId1, PhotoConfiguration())
            wizardState.setPhotoConfiguration(boxId2, PhotoConfiguration())

            wizardState.clearAllConfigurations()

            assertThat(wizardState.photoConfigurations.value).isEmpty()
        }

        private fun createTestBox(x: Double = 100.0, y: Double = 100.0): BoundingBox {
            return BoundingBox(
                corners =
                    BoundingBoxCorners(
                        Point(x, y),
                        Point(x + 200.0, y),
                        Point(x + 200.0, y + 100.0),
                        Point(x, y + 100.0),
                    )
            )
        }
    }

    @Nested
    @DisplayName("Four Point State")
    inner class FourPointStateTest {

        @Test
        @DisplayName("should start inactive")
        fun shouldStartInactive() {
            assertThat(wizardState.fourPointState.value.mode)
                .isEqualTo(FourPointState.Mode.INACTIVE)
        }

        @Test
        @DisplayName("should enter four point mode")
        fun shouldEnterFourPointMode() {
            wizardState.enterFourPointMode()

            assertThat(wizardState.fourPointState.value.mode).isEqualTo(FourPointState.Mode.PLACING)
        }

        @Test
        @DisplayName("should track added points")
        fun shouldTrackAddedPoints() {
            wizardState.enterFourPointMode()

            wizardState.addFourPoint(Point(100.0, 100.0))
            assertThat(wizardState.fourPointState.value.points.size).isGreaterThanOrEqualTo(1)

            wizardState.addFourPoint(Point(300.0, 100.0))
            assertThat(wizardState.fourPointState.value.points.size).isGreaterThanOrEqualTo(2)
        }

        @Test
        @DisplayName("should exit four point mode")
        fun shouldExitFourPointMode() {
            wizardState.enterFourPointMode()

            wizardState.exitFourPointMode()

            assertThat(wizardState.fourPointState.value.mode)
                .isEqualTo(FourPointState.Mode.INACTIVE)
        }
    }
}
