package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.awt.image.BufferedImage
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.ui.wizard.state.PhotoScanWizardState
import org.kryspetrie.fileimport.ui.wizard.state.WizardMode

/**
 * Component tests for OverviewScreen. Tests UI rendering and user interactions with state changes.
 *
 * Uses JUnit 4 style with @get:Rule for ComposeTestRule. Tagged with @Tag(UiComponentTest::class)
 * for test filtering.
 */
@DisplayName("OverviewScreen Component Tests")
@Tag("UiComponentTest")
class OverviewScreenTest {

    @get:Rule val composeTestRule = createComposeRule()

    private lateinit var wizardState: PhotoScanWizardState
    private lateinit var testImage: BufferedImage

    @BeforeEach
    fun setup() {
        wizardState = PhotoScanWizardState()
        testImage = BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB)
        wizardState.initializeWithImage(testImage, java.io.File("test-scan.jpg"))
    }

    @Test
    @DisplayName("should display title")
    fun shouldDisplayTitle() {
        composeTestRule.setContent {
            OverviewScreen(state = wizardState, onBack = {}, onToSummary = {})
        }

        composeTestRule.onNodeWithText("Bounding Box Overview").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display 4-Point button")
    fun shouldDisplay4PointButton() {
        composeTestRule.setContent {
            OverviewScreen(state = wizardState, onBack = {}, onToSummary = {})
        }

        composeTestRule.onNodeWithText("4-Point").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display Add Box button")
    fun shouldDisplayAddBoxButton() {
        composeTestRule.setContent {
            OverviewScreen(state = wizardState, onBack = {}, onToSummary = {})
        }

        composeTestRule.onNodeWithText("Add Box").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display To Summary button")
    fun shouldDisplayToSummaryButton() {
        composeTestRule.setContent {
            OverviewScreen(state = wizardState, onBack = {}, onToSummary = {})
        }

        composeTestRule.onNodeWithText("To Summary").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display help button")
    fun shouldDisplayHelpButton() {
        composeTestRule.setContent {
            OverviewScreen(state = wizardState, onBack = {}, onToSummary = {})
        }

        composeTestRule.onNodeWithContentDescription("Help").assertIsDisplayed()
    }

    @Test
    @DisplayName("should open help dialog when help clicked")
    fun shouldOpenHelpDialogWhenHelpClicked() {
        composeTestRule.setContent {
            OverviewScreen(state = wizardState, onBack = {}, onToSummary = {})
        }

        composeTestRule.onNodeWithContentDescription("Help").performClick()

        composeTestRule.onNodeWithText("Keyboard Shortcuts").assertIsDisplayed()
    }

    @Test
    @DisplayName("should show zero boxes initially")
    fun shouldShowZeroBoxesInitially() {
        composeTestRule.setContent {
            OverviewScreen(state = wizardState, onBack = {}, onToSummary = {})
        }

        composeTestRule.onNodeWithText("0 box(es)").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display zoom controls")
    fun shouldDisplayZoomControls() {
        composeTestRule.setContent {
            OverviewScreen(state = wizardState, onBack = {}, onToSummary = {})
        }

        composeTestRule.onNodeWithContentDescription("Zoom in").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Zoom out").assertIsDisplayed()
    }

    @Test
    @DisplayName("should zoom in when zoom in clicked")
    fun shouldZoomInWhenZoomInClicked() {
        val initialZoom = wizardState.zoom.zoomController.value.zoom

        composeTestRule.setContent {
            OverviewScreen(state = wizardState, onBack = {}, onToSummary = {})
        }

        composeTestRule.onNodeWithContentDescription("Zoom in").performClick()
        composeTestRule.waitForIdle()

        assertThat(wizardState.zoom.zoomController.value.zoom).isGreaterThan(initialZoom)
    }

    @Test
    @DisplayName("should enter four point mode when clicked")
    fun shouldEnterFourPointModeWhenClicked() {
        composeTestRule.setContent {
            OverviewScreen(state = wizardState, onBack = {}, onToSummary = {})
        }

        composeTestRule.onNodeWithText("4-Point").performClick()

        assertThat(wizardState.wizardMode.value).isEqualTo(WizardMode.FOUR_POINT)
    }

    @Test
    @DisplayName("should show add box mode indicator when clicked")
    fun shouldShowAddBoxModeIndicatorWhenClicked() {
        composeTestRule.setContent {
            OverviewScreen(state = wizardState, onBack = {}, onToSummary = {})
        }

        composeTestRule.onNodeWithText("Add Box").performClick()

        composeTestRule.onNodeWithText("Add Box Mode").assertIsDisplayed()
    }
}
