package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import java.awt.image.BufferedImage
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.jupiter.api.*
import org.kryspetrie.fileimport.infrastructure.wizard.*

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

  @BeforeEach
  fun setup() {
    wizardState = PhotoScanWizardState()
    testImage = BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB)
    wizardState.initializeWithImage(testImage, java.io.File("test-scan.jpg"))

    val box =
        BoundingBox(
            corners =
                BoundingBoxCorners(
                    Point(100.0, 100.0),
                    Point(300.0, 100.0),
                    Point(300.0, 200.0),
                    Point(100.0, 200.0)))
    wizardState.addBox(box)
  }

  @Test
  @DisplayName("should display title")
  fun shouldDisplayTitle() {
    composeTestRule.setContent {
      SummaryScreen(
          state = wizardState,
          image = testImage,
          exportDestination = "/test/output",
          onBack = {},
          onExport = {})
    }

    composeTestRule.onNodeWithText("Photo Summary").assertIsDisplayed()
  }

  @Test
  @DisplayName("should display back button")
  fun shouldDisplayBackButton() {
    composeTestRule.setContent {
      SummaryScreen(
          state = wizardState,
          image = testImage,
          exportDestination = "/test/output",
          onBack = {},
          onExport = {})
    }

    composeTestRule.onNodeWithText("Back").assertIsDisplayed()
  }

  @Test
  @DisplayName("should display export button")
  fun shouldDisplayExportButton() {
    composeTestRule.setContent {
      SummaryScreen(
          state = wizardState,
          image = testImage,
          exportDestination = "/test/output",
          onBack = {},
          onExport = {})
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
          exportDestination = "/test/output",
          onBack = {},
          onExport = {})
    }

    composeTestRule.onNodeWithText("1 photo(s)").assertIsDisplayed()
  }

  @Test
  @DisplayName("should display export destination")
  fun shouldDisplayExportDestination() {
    composeTestRule.setContent {
      SummaryScreen(
          state = wizardState,
          image = testImage,
          exportDestination = "/test/output",
          onBack = {},
          onExport = {})
    }

    composeTestRule.onNodeWithText("/test/output").assertIsDisplayed()
  }

  @Test
  @DisplayName("should call onBack when clicked")
  fun shouldCallOnBackWhenClicked() {
    var backCalled = false

    composeTestRule.setContent {
      SummaryScreen(
          state = wizardState,
          image = testImage,
          exportDestination = "/test/output",
          onBack = { backCalled = true },
          onExport = {})
    }

    composeTestRule.onNodeWithText("Back").performClick()

    assertThat(backCalled).isTrue()
  }

  @Test
  @DisplayName("should call onExport when clicked")
  fun shouldCallOnExportWhenClicked() {
    var exportCalled = false

    composeTestRule.setContent {
      SummaryScreen(
          state = wizardState,
          image = testImage,
          exportDestination = "/test/output",
          onBack = {},
          onExport = { exportCalled = true })
    }

    composeTestRule.onNodeWithText("Export").performClick()

    assertThat(exportCalled).isTrue()
  }

  @Test
  @DisplayName("should display perspective setting")
  fun shouldDisplayPerspectiveSetting() {
    composeTestRule.setContent {
      SummaryScreen(
          state = wizardState,
          image = testImage,
          exportDestination = "/test/output",
          onBack = {},
          onExport = {})
    }

    composeTestRule.onNodeWithText("Perspective Correction").assertIsDisplayed()
  }

  @Test
  @DisplayName("should display rotation setting")
  fun shouldDisplayRotationSetting() {
    composeTestRule.setContent {
      SummaryScreen(
          state = wizardState,
          image = testImage,
          exportDestination = "/test/output",
          onBack = {},
          onExport = {})
    }

    composeTestRule.onNodeWithText("Rotation").assertIsDisplayed()
  }
}
