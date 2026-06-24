package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.awt.image.BufferedImage
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.application.PerspectiveCorrectionService
import org.kryspetrie.fileimport.infrastructure.wizard.BoundingBox
import org.kryspetrie.fileimport.infrastructure.wizard.BoundingBoxCorners
import org.kryspetrie.fileimport.infrastructure.wizard.PhotoScanWizardState
import org.kryspetrie.fileimport.infrastructure.wizard.Point
import org.kryspetrie.fileimport.ui.components.PreviewCache

/**
 * Component tests for SummaryScreen. Tests UI rendering and export configuration.
 *
 * Uses JUnit 4 style with @get:Rule for ComposeTestRule. Tagged with @Tag(UiComponentTest::class)
 * for test filtering.
 */
@DisplayName("SummaryScreen Component Tests")
@Tag("UiComponentTest")
class SummaryScreenTest {

    @get:Rule val composeTestRule = createComposeRule()

    private lateinit var wizardState: PhotoScanWizardState
    private lateinit var testImage: BufferedImage
    private lateinit var perspectiveService: PerspectiveCorrectionService
    private lateinit var previewCache: PreviewCache

    @BeforeEach
    fun setup() {
        wizardState = PhotoScanWizardState()
        testImage = BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB)
        wizardState.initializeWithImage(testImage, java.io.File("test-scan.jpg"))
        perspectiveService = PerspectiveCorrectionService()
        previewCache = PreviewCache(perspectiveService)

        val box =
            BoundingBox(
                corners =
                    BoundingBoxCorners(
                        Point(100.0, 100.0),
                        Point(300.0, 100.0),
                        Point(300.0, 200.0),
                        Point(100.0, 200.0),
                    )
            )
        wizardState.boxes.addBox(box)
    }

    @Test
    @DisplayName("should display title")
    fun shouldDisplayTitle() {
        composeTestRule.setContent {
            SummaryScreen(
                state = wizardState,
                image = testImage,
                perspectiveService = PerspectiveCorrectionService(),
                previewCache = previewCache,
                onBack = {},
                onExport = {},
            )
        }

        composeTestRule.onNodeWithText("Photo Summary").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display export button")
    fun shouldDisplayExportButton() {
        composeTestRule.setContent {
            SummaryScreen(
                state = wizardState,
                image = testImage,
                perspectiveService = PerspectiveCorrectionService(),
                previewCache = previewCache,
                onBack = {},
                onExport = {},
            )
        }

        composeTestRule.onNodeWithText("Export").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display photo count")
    fun shouldDisplayPhotoCount() {
        composeTestRule.setContent {
            SummaryScreen(
                state = wizardState,
                image = testImage,
                perspectiveService = PerspectiveCorrectionService(),
                previewCache = previewCache,
                onBack = {},
                onExport = {},
            )
        }

        composeTestRule.onNodeWithText("1 photo(s)").assertIsDisplayed()
    }

    @Test
    @DisplayName("should call onExport when clicked")
    fun shouldCallOnExportWhenClicked() {
        var exportCalled = false

        composeTestRule.setContent {
            SummaryScreen(
                state = wizardState,
                image = testImage,
                perspectiveService = PerspectiveCorrectionService(),
                previewCache = previewCache,
                onBack = {},
                onExport = { exportCalled = true },
            )
        }

        composeTestRule.onNodeWithText("Export").performClick()

        assertThat(exportCalled).isTrue()
    }
}
