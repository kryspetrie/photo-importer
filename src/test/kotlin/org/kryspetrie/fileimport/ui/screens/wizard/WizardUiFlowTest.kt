package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.awt.image.BufferedImage
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.model.geometry.BoundingBox
import org.kryspetrie.fileimport.domain.model.geometry.BoundingBoxCorners
import org.kryspetrie.fileimport.domain.model.geometry.Point
import org.kryspetrie.fileimport.domain.port.SettingsPort
import org.kryspetrie.fileimport.infrastructure.adapter.SettingsAdapter
import org.kryspetrie.fileimport.infrastructure.photoscan.PerspectiveCorrectionService
import org.kryspetrie.fileimport.ui.components.PreviewCache
import org.kryspetrie.fileimport.ui.wizard.state.PhotoScanWizardState
import org.kryspetrie.fileimport.ui.wizard.state.WizardMode

/**
 * Compose UI flow tests that render actual Compose components and simulate user interactions.
 *
 * These tests verify that the UI renders correctly and that user interactions (button clicks, etc.)
 * drive state changes and invoke callbacks as expected.
 *
 * NOTE: These tests require a display/graphical environment to run. They are tagged with
 * `UiComponentTest` and excluded from the default `test` task. Run with `./gradlew uiTest`.
 *
 * For pure state-transition tests that don't need a display, see WizardStateFlowTest.
 */
@DisplayName("Wizard UI Flow Tests")
@Tag("UiComponentTest")
class WizardUiFlowTest {

    @get:Rule val composeTestRule = createComposeRule()

    private lateinit var wizardState: PhotoScanWizardState
    private lateinit var testImage: BufferedImage

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

    private fun setUpWizardWithBoxes(boxCount: Int = 1): PhotoScanWizardState {
        val state = PhotoScanWizardState()
        val image = BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB)
        state.initializeWithImage(image, File("test-scan.jpg"))

        repeat(boxCount) { i ->
            state.boxes.addBox(createTestBox(x = 100.0 + i * 250.0, y = 100.0))
        }
        return state
    }

    // ================================================================
    // OverviewScreen Flow Tests
    // ================================================================

    @Nested
    @DisplayName("Overview Screen Flow Tests")
    inner class OverviewScreenFlowTests {

        @BeforeEach
        fun setup() {
            wizardState = setUpWizardWithBoxes()
            testImage = BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB)
            wizardState.initializeWithImage(testImage, File("test-overview.jpg"))
        }

        @Test
        @DisplayName("should display box count after detection")
        fun shouldDisplayBoxCount() {
            composeTestRule.setContent {
                OverviewScreen(state = wizardState, onBack = {}, onToSummary = {})
            }

            composeTestRule.onNodeWithText("1 box(es)").assertIsDisplayed()
        }

        @Test
        @DisplayName("should update box count when box added")
        fun shouldUpdateBoxCountWhenBoxAdded() {
            composeTestRule.setContent {
                OverviewScreen(state = wizardState, onBack = {}, onToSummary = {})
            }

            composeTestRule.onNodeWithText("1 box(es)").assertIsDisplayed()

            wizardState.boxes.addBox(createTestBox(x = 400.0, y = 100.0))

            composeTestRule.onNodeWithText("2 box(es)").assertIsDisplayed()
        }

        @Test
        @DisplayName("should enter four point mode on button click")
        fun shouldEnterFourPointModeOnButtonClick() {
            composeTestRule.setContent {
                OverviewScreen(state = wizardState, onBack = {}, onToSummary = {})
            }

            composeTestRule.onNodeWithText("4-Point").performClick()

            assertThat(wizardState.wizardMode.value).isEqualTo(WizardMode.FOUR_POINT)
        }

        @Test
        @DisplayName("should trigger onToSummary callback")
        fun shouldTriggerOnToSummaryCallback() {
            var summaryClicked = false

            composeTestRule.setContent {
                OverviewScreen(
                    state = wizardState,
                    onBack = {},
                    onToSummary = { summaryClicked = true },
                )
            }

            composeTestRule.onNodeWithText("To Summary").performClick()

            assertThat(summaryClicked).isTrue()
        }

        @Test
        @DisplayName("should zoom in via controls")
        fun shouldZoomInViaControls() {
            composeTestRule.setContent {
                OverviewScreen(state = wizardState, onBack = {}, onToSummary = {})
            }

            val initialZoom = wizardState.zoom.zoomController.value.zoom
            composeTestRule.onNodeWithContentDescription("Zoom in").performClick()
            composeTestRule.waitForIdle()

            assertThat(wizardState.zoom.zoomController.value.zoom).isGreaterThan(initialZoom)
        }
    }

    // ================================================================
    // SummaryScreen Flow Tests
    // ================================================================

    @Nested
    @DisplayName("Summary Screen Flow Tests")
    inner class SummaryScreenFlowTests {

        private lateinit var perspectiveService: PerspectiveCorrectionService
        private lateinit var previewCache: PreviewCache

        @BeforeEach
        fun setup() {
            wizardState = setUpWizardWithBoxes()
            testImage = BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB)
            wizardState.initializeWithImage(testImage, File("test-summary.jpg"))
            perspectiveService = PerspectiveCorrectionService()
            previewCache = PreviewCache(perspectiveService)
        }

        @Test
        @DisplayName("should display Photo Summary title")
        fun shouldDisplayPhotoSummaryTitle() {
            composeTestRule.setContent {
                SummaryScreen(
                    state = wizardState,
                    image = testImage,
                    perspectiveService = perspectiveService,
                    previewCache = previewCache,
                    onBack = {},
                    onExport = {},
                )
            }

            composeTestRule.onNodeWithText("Photo Summary").assertIsDisplayed()
        }

        @Test
        @DisplayName("should display photo count in bottom bar")
        fun shouldDisplayPhotoCountInBottomBar() {
            composeTestRule.setContent {
                SummaryScreen(
                    state = wizardState,
                    image = testImage,
                    perspectiveService = perspectiveService,
                    previewCache = previewCache,
                    onBack = {},
                    onExport = {},
                )
            }

            composeTestRule.onNodeWithText("1 photo(s) ready").assertIsDisplayed()
        }

        @Test
        @DisplayName("should call onExport when Edit button clicked")
        fun shouldCallOnExportWhenEditButtonClicked() {
            var exportCalled = false

            composeTestRule.setContent {
                SummaryScreen(
                    state = wizardState,
                    image = testImage,
                    perspectiveService = perspectiveService,
                    previewCache = previewCache,
                    onBack = {},
                    onExport = { exportCalled = true },
                )
            }

            composeTestRule.onNodeWithText("Edit").performClick()

            assertThat(exportCalled).isTrue()
        }

        @Test
        @DisplayName("should call onBack when Back button clicked")
        fun shouldCallOnBackWhenBackButtonClicked() {
            var backCalled = false

            composeTestRule.setContent {
                SummaryScreen(
                    state = wizardState,
                    image = testImage,
                    perspectiveService = perspectiveService,
                    previewCache = previewCache,
                    onBack = { backCalled = true },
                    onExport = {},
                )
            }

            composeTestRule.onNodeWithText("Back").performClick()

            assertThat(backCalled).isTrue()
        }

        // Note: The "Export Now" / "Skip Metadata" functionality was removed from
        // SummaryScreen. The workflow now uses "Skip Crop & Rotate" setting on the
        // import screen to skip the Crop & Rotate step entirely.
    }

    // ================================================================
    // ExportSettingsCard Flow Tests
    // ================================================================

    @Nested
    @DisplayName("Export Settings Card Flow Tests")
    inner class ExportSettingsCardFlowTests {

        @BeforeEach
        fun setup() {
            wizardState = PhotoScanWizardState()
            testImage = BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB)
            wizardState.initializeWithImage(testImage, File("test-settings.jpg"))
        }

        @Test
        @DisplayName("should display Export Settings title")
        fun shouldDisplayExportSettingsTitle() {
            composeTestRule.setContent { ExportSettingsCard(state = wizardState) }

            composeTestRule.onNodeWithText("Export Settings").assertIsDisplayed()
        }

        @Test
        @DisplayName("should display perspective correction toggle")
        fun shouldDisplayPerspectiveCorrectionToggle() {
            composeTestRule.setContent { ExportSettingsCard(state = wizardState) }

            composeTestRule.onNodeWithText("Perspective correction").assertIsDisplayed()
        }

        @Test
        @DisplayName("should display correction strategy when perspective is off")
        fun shouldDisplayCorrectionStrategyWhenPerspectiveOff() {
            wizardState.exportSettings.setPerspectiveCorrectionEnabled(false)

            composeTestRule.setContent { ExportSettingsCard(state = wizardState) }

            composeTestRule.onNodeWithText("Correction strategy").assertIsDisplayed()
        }

        @Test
        @DisplayName("should not display correction strategy when perspective is on")
        fun shouldNotDisplayCorrectionStrategyWhenPerspectiveOn() {
            wizardState.exportSettings.setPerspectiveCorrectionEnabled(true)

            composeTestRule.setContent { ExportSettingsCard(state = wizardState) }

            composeTestRule.onNodeWithText("Correction strategy").assertDoesNotExist()
        }

        @Test
        @DisplayName("should display Additional margin label")
        fun shouldDisplayAdditionalMarginLabel() {
            composeTestRule.setContent { ExportSettingsCard(state = wizardState) }

            composeTestRule.onNodeWithText("Additional margin").assertIsDisplayed()
        }
    }

    // ================================================================
    // CompletionScreen Flow Tests
    // ================================================================

    @Nested
    @DisplayName("Completion Screen Flow Tests")
    inner class CompletionScreenFlowTests {

        @Test
        @DisplayName("should display Complete title")
        fun shouldDisplayCompleteTitle() {
            composeTestRule.setContent {
                CompletionScreen(
                    photoCount = 3,
                    exportDestination = "/tmp/exports",
                    isBatchMode = false,
                    hasMoreBatchImages = false,
                    currentBatchIndex = 0,
                    batchTotal = 1,
                    onDone = {},
                    onImportFile = {},
                    onImportFolder = {},
                    onContinueToNextPhoto = {},
                    onCancelImport = {},
                    onOpenFolder = {},
                )
            }

            composeTestRule.onNodeWithText("Complete").assertIsDisplayed()
        }

        @Test
        @DisplayName("should display photo count for successful export")
        fun shouldDisplayPhotoCountForSuccessfulExport() {
            composeTestRule.setContent {
                CompletionScreen(
                    photoCount = 3,
                    exportDestination = "/tmp/exports",
                    isBatchMode = false,
                    hasMoreBatchImages = false,
                    currentBatchIndex = 0,
                    batchTotal = 1,
                    onDone = {},
                    onImportFile = {},
                    onImportFolder = {},
                    onContinueToNextPhoto = {},
                    onCancelImport = {},
                    onOpenFolder = {},
                )
            }

            composeTestRule
                .onNodeWithText("3 extracted photo(s) exported from this scan")
                .assertIsDisplayed()
        }

        @Test
        @DisplayName("should display failed count when exports fail")
        fun shouldDisplayFailedCountWhenExportsFail() {
            composeTestRule.setContent {
                CompletionScreen(
                    photoCount = 3,
                    exportDestination = "/tmp/exports",
                    isBatchMode = false,
                    hasMoreBatchImages = false,
                    currentBatchIndex = 0,
                    batchTotal = 1,
                    failedCount = 1,
                    onDone = {},
                    onImportFile = {},
                    onImportFolder = {},
                    onContinueToNextPhoto = {},
                    onCancelImport = {},
                    onOpenFolder = {},
                )
            }

            composeTestRule
                .onNodeWithText(
                    "3 extracted photo(s) exported from this scan (1 failed)"
                )
                .assertIsDisplayed()
        }

        @Test
        @DisplayName("should call onDone when Done button clicked")
        fun shouldCallOnDoneWhenDoneButtonClicked() {
            var doneCalled = false

            composeTestRule.setContent {
                CompletionScreen(
                    photoCount = 1,
                    exportDestination = "/tmp/exports",
                    isBatchMode = false,
                    hasMoreBatchImages = false,
                    currentBatchIndex = 0,
                    batchTotal = 1,
                    onDone = { doneCalled = true },
                    onImportFile = {},
                    onImportFolder = {},
                    onContinueToNextPhoto = {},
                    onCancelImport = {},
                    onOpenFolder = {},
                )
            }

            composeTestRule.onNodeWithText("Done").performClick()

            assertThat(doneCalled).isTrue()
        }

        @Test
        @DisplayName("should show batch progress when in batch mode")
        fun shouldShowBatchProgressInBatchMode() {
            composeTestRule.setContent {
                CompletionScreen(
                    photoCount = 1,
                    exportDestination = "/tmp/exports",
                    isBatchMode = true,
                    hasMoreBatchImages = true,
                    currentBatchIndex = 0,
                    batchTotal = 5,
                    onDone = {},
                    onImportFile = {},
                    onImportFolder = {},
                    onContinueToNextPhoto = {},
                    onSkipNextPhoto = {},
                    onCancelImport = {},
                    onOpenFolder = {},
                )
            }

            composeTestRule.onNodeWithText("Photo 1 of 5").assertIsDisplayed()
        }

        @Test
        @DisplayName("should show Continue button in batch mode with more images")
        fun shouldShowContinueInBatchModeWithMoreImages() {
            composeTestRule.setContent {
                CompletionScreen(
                    photoCount = 1,
                    exportDestination = "/tmp/exports",
                    isBatchMode = true,
                    hasMoreBatchImages = true,
                    currentBatchIndex = 0,
                    batchTotal = 5,
                    onDone = {},
                    onImportFile = {},
                    onImportFolder = {},
                    onContinueToNextPhoto = {},
                    onSkipNextPhoto = {},
                    onCancelImport = {},
                    onOpenFolder = {},
                )
            }

            composeTestRule.onNodeWithText("Continue").assertIsDisplayed()
            composeTestRule.onNodeWithText("Skip").assertIsDisplayed()
            composeTestRule.onNodeWithText("Cancel Import").assertIsDisplayed()
        }

        @Test
        @DisplayName("should show Done instead of Continue at batch end")
        fun shouldShowDoneAtBatchEnd() {
            composeTestRule.setContent {
                CompletionScreen(
                    photoCount = 1,
                    exportDestination = "/tmp/exports",
                    isBatchMode = true,
                    hasMoreBatchImages = false,
                    currentBatchIndex = 4,
                    batchTotal = 5,
                    onDone = {},
                    onImportFile = {},
                    onImportFolder = {},
                    onContinueToNextPhoto = {},
                    onCancelImport = {},
                    onOpenFolder = {},
                )
            }

            composeTestRule.onNodeWithText("Done").assertIsDisplayed()
            composeTestRule.onNodeWithText("Continue").assertDoesNotExist()
        }

        @Test
        @DisplayName("should show skipped count in batch mode")
        fun shouldShowSkippedCountInBatchMode() {
            composeTestRule.setContent {
                CompletionScreen(
                    photoCount = 1,
                    exportDestination = "/tmp/exports",
                    isBatchMode = true,
                    hasMoreBatchImages = true,
                    currentBatchIndex = 2,
                    batchTotal = 5,
                    skippedCount = 1,
                    onDone = {},
                    onImportFile = {},
                    onImportFolder = {},
                    onContinueToNextPhoto = {},
                    onSkipNextPhoto = {},
                    onCancelImport = {},
                    onOpenFolder = {},
                )
            }

            composeTestRule.onNodeWithText("(1 skipped as photo backs)").assertIsDisplayed()
        }
    }

    // ================================================================
    // RefinementScreen Flow Tests
    // ================================================================

    @Nested
    @DisplayName("Refinement Screen Flow Tests")
    inner class RefinementScreenFlowTests {

        @BeforeEach
        fun setup() {
            wizardState = setUpWizardWithBoxes()
            testImage = BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB)
            wizardState.initializeWithImage(testImage, File("test-refinement.jpg"))
            wizardState.boxes.selectBox(0)
            wizardState.enterRefinement(0)
        }

        @Test
        @DisplayName("should display Refine Bounding Box title")
        fun shouldDisplayRefineBoundingBoxTitle() {
            composeTestRule.setContent { RefinementScreen(state = wizardState, onBack = {}) }

            composeTestRule.onNodeWithText("Refine Bounding Box").assertIsDisplayed()
        }

        @Test
        @DisplayName("should display Perspective Correction toggle")
        fun shouldDisplayPerspectiveCorrectionToggle() {
            composeTestRule.setContent { RefinementScreen(state = wizardState, onBack = {}) }

            composeTestRule.onNodeWithText("Perspective Correction").assertIsDisplayed()
        }

        @Test
        @DisplayName("should display Rotation controls")
        fun shouldDisplayRotationControls() {
            composeTestRule.setContent { RefinementScreen(state = wizardState, onBack = {}) }

            composeTestRule.onNodeWithText("Rotation").assertIsDisplayed()
        }

        @Test
        @DisplayName("should display Apply button")
        fun shouldDisplayApplyButton() {
            composeTestRule.setContent { RefinementScreen(state = wizardState, onBack = {}) }

            composeTestRule.onNodeWithText("Apply").assertIsDisplayed()
        }

        @Test
        @DisplayName("should display zoom controls")
        fun shouldDisplayZoomControls() {
            composeTestRule.setContent { RefinementScreen(state = wizardState, onBack = {}) }

            composeTestRule.onNodeWithContentDescription("Zoom in").assertIsDisplayed()
            composeTestRule.onNodeWithContentDescription("Zoom out").assertIsDisplayed()
        }
    }

    // ================================================================
    // ImportScreen Flow Tests
    // ================================================================

    @Nested
    @DisplayName("Import Screen Flow Tests")
    inner class ImportScreenFlowTests {

        private lateinit var settingsPort: SettingsPort

        @BeforeEach
        fun setup() {
            wizardState = PhotoScanWizardState()
            settingsPort =
                SettingsAdapter(
                    File(
                        System.getProperty("java.io.tmpdir"),
                        "test-settings-ui-flow-${System.nanoTime()}",
                    )
                )
            testImage = BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB)
        }

        @Test
        @DisplayName("should display Photo Scan Import title")
        fun shouldDisplayTitle() {
            composeTestRule.setContent {
                PhotoScanImportScreen(
                    state = wizardState,
                    settingsPort = settingsPort,
                    onSettingsChange = {},
                    onImageSelected = { _, _ -> },
                    onCancel = {},
                )
            }

            composeTestRule.onNodeWithText("Photo Scan Import").assertIsDisplayed()
        }

        @Test
        @DisplayName("should display Import Mode header")
        fun shouldDisplayImportModeHeader() {
            composeTestRule.setContent {
                PhotoScanImportScreen(
                    state = wizardState,
                    settingsPort = settingsPort,
                    onSettingsChange = {},
                    onImageSelected = { _, _ -> },
                    onCancel = {},
                )
            }

            composeTestRule.onNodeWithText("Import Mode").assertIsDisplayed()
        }

        @Test
        @DisplayName("should call onCancel when Cancel button clicked")
        fun shouldCallOnCancelWhenCancelClicked() {
            var cancelCalled = false

            composeTestRule.setContent {
                PhotoScanImportScreen(
                    state = wizardState,
                    settingsPort = settingsPort,
                    onSettingsChange = {},
                    onImageSelected = { _, _ -> },
                    onCancel = { cancelCalled = true },
                )
            }

            composeTestRule.onNodeWithContentDescription("Cancel").performClick()

            assertThat(cancelCalled).isTrue()
        }
    }
}
