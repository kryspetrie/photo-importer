package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import java.awt.image.BufferedImage
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.jupiter.api.*
import org.kryspetrie.fileimport.infrastructure.wizard.*

/**
 * Component tests for RefinementScreen. Tests UI rendering and user interactions for box
 * refinement.
 *
 * Uses JUnit 4 style with @get:Rule for ComposeTestRule. Tagged with @Tag(UiComponentTest::class)
 * for test filtering.
 */
@DisplayName("RefinementScreen Component Tests")
@Tag("UiComponentTest")
class RefinementScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var wizardState: PhotoScanWizardState

  @BeforeEach
  fun setup() {
    wizardState = PhotoScanWizardState()
    val testImage = BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB)
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
    wizardState.selectBox(0)
    wizardState.enterRefinement(0)
  }

  @Test
  @DisplayName("should display title")
  fun shouldDisplayTitle() {
    composeTestRule.setContent { RefinementScreen(state = wizardState, onBack = {}) }

    composeTestRule.onNodeWithText("Refine Bounding Box").assertIsDisplayed()
  }

  @Test
  @DisplayName("should display back button")
  fun shouldDisplayBackButton() {
    composeTestRule.setContent { RefinementScreen(state = wizardState, onBack = {}) }

    composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed()
  }

  @Test
  @DisplayName("should display perspective toggle")
  fun shouldDisplayPerspectiveToggle() {
    composeTestRule.setContent { RefinementScreen(state = wizardState, onBack = {}) }

    composeTestRule.onNodeWithText("Perspective Correction").assertIsDisplayed()
  }

  @Test
  @DisplayName("should display rotation controls")
  fun shouldDisplayRotationControls() {
    composeTestRule.setContent { RefinementScreen(state = wizardState, onBack = {}) }

    composeTestRule.onNodeWithText("Rotation").assertIsDisplayed()
  }

  @Test
  @DisplayName("should display apply button")
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

  @Test
  @DisplayName("should zoom in when zoom in clicked")
  fun shouldZoomInWhenZoomInClicked() {
    val initialZoom = wizardState.zoomController.value.zoom

    composeTestRule.setContent { RefinementScreen(state = wizardState, onBack = {}) }

    composeTestRule.onNodeWithContentDescription("Zoom in").performClick()
    composeTestRule.waitForIdle()

    assertThat(wizardState.zoomController.value.zoom).isGreaterThan(initialZoom)
  }

  @Test
  @DisplayName("should go to overview when back clicked")
  fun shouldGoToOverviewWhenBackClicked() {
    composeTestRule.setContent { RefinementScreen(state = wizardState, onBack = {}) }

    composeTestRule.onNodeWithContentDescription("Back").performClick()

    assertThat(wizardState.currentStep.value).isEqualTo(PhotoScanWizardState.WizardStep.OVERVIEW)
  }

  @Test
  @DisplayName("should display help button")
  fun shouldDisplayHelpButton() {
    composeTestRule.setContent { RefinementScreen(state = wizardState, onBack = {}) }

    composeTestRule.onNodeWithContentDescription("Help").assertIsDisplayed()
  }

  @Test
  @DisplayName("should open help dialog when help clicked")
  fun shouldOpenHelpDialogWhenHelpClicked() {
    composeTestRule.setContent { RefinementScreen(state = wizardState, onBack = {}) }

    composeTestRule.onNodeWithContentDescription("Help").performClick()

    composeTestRule.onNodeWithText("Keyboard Shortcuts").assertIsDisplayed()
  }

  @Test
  @DisplayName("should have perspective enabled by default")
  fun shouldHavePerspectiveEnabledByDefault() {
    val config = PhotoConfiguration()
    assertThat(config.perspectiveCorrectionEnabled).isTrue()
  }

  @Test
  @DisplayName("should have zero rotation by default")
  fun shouldHaveZeroRotationByDefault() {
    val config = PhotoConfiguration()
    assertThat(config.rotationDegrees).isEqualTo(0)
  }
}
