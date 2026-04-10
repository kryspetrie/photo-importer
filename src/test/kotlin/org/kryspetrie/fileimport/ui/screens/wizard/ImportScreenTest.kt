package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.jupiter.api.*
import org.kryspetrie.fileimport.infrastructure.wizard.*

/**
 * Component tests for ImportScreen.
 * Tests UI rendering and user interactions with mode selection.
 * 
 * Uses JUnit 4 style with @get:Rule for ComposeTestRule.
 * Tagged with @Tag(UiComponentTest::class) for test filtering.
 */
@DisplayName("ImportScreen Component Tests")
@Tag("UiComponentTest")
class ImportScreenTest {

    @get:Rule val composeTestRule = createComposeRule()

    private lateinit var wizardState: PhotoScanWizardState

    @BeforeEach
    fun setup() {
        wizardState = PhotoScanWizardState()
    }

    @Test
    @DisplayName("should display Import Photos title")
    fun shouldDisplayTitle() {
        composeTestRule.setContent {
            ImportScreen(
                state = wizardState,
                onImageSelected = {},
                onCancel = {})
        }

        composeTestRule.onNodeWithText("Import Photos")
            .assertIsDisplayed()
    }

    @Test
    @DisplayName("should display Import Mode header")
    fun shouldDisplayImportModeHeader() {
        composeTestRule.setContent {
            ImportScreen(
                state = wizardState,
                onImageSelected = {},
                onCancel = {})
        }

        composeTestRule.onNodeWithText("Import Mode")
            .assertIsDisplayed()
    }

    @Test
    @DisplayName("should display Photo Scan mode card")
    fun shouldDisplayPhotoScanCard() {
        composeTestRule.setContent {
            ImportScreen(
                state = wizardState,
                onImageSelected = {},
                onCancel = {})
        }

        composeTestRule.onNodeWithText("Photo Scan")
            .assertIsDisplayed()
    }

    @Test
    @DisplayName("should display Single Photo mode card")
    fun shouldDisplaySinglePhotoCard() {
        composeTestRule.setContent {
            ImportScreen(
                state = wizardState,
                onImageSelected = {},
                onCancel = {})
        }

        composeTestRule.onNodeWithText("Single Photo")
            .assertIsDisplayed()
    }

    @Test
    @DisplayName("should display auto-detect toggle")
    fun shouldDisplayAutoDetectToggle() {
        composeTestRule.setContent {
            ImportScreen(
                state = wizardState,
                onImageSelected = {},
                onCancel = {})
        }

        composeTestRule.onNodeWithText("Auto-detect bounding boxes")
            .assertIsDisplayed()
    }

    @Test
    @DisplayName("should have Photo Scan selected by default")
    fun shouldHavePhotoScanSelectedByDefault() {
        assertThat(wizardState.importMode.value).isEqualTo(ImportMode.PHOTO_SCAN)
    }

    @Test
    @DisplayName("should update state when Single Photo clicked")
    fun shouldUpdateStateWhenSinglePhotoClicked() {
        composeTestRule.setContent {
            ImportScreen(
                state = wizardState,
                onImageSelected = {},
                onCancel = {})
        }

        composeTestRule.onNodeWithText("Single Photo")
            .performClick()

        assertThat(wizardState.importMode.value).isEqualTo(ImportMode.SINGLE_PHOTO)
    }

    @Test
    @DisplayName("should hide options when Single Photo selected")
    fun shouldHideOptionsWhenSinglePhotoSelected() {
        composeTestRule.setContent {
            ImportScreen(
                state = wizardState,
                onImageSelected = {},
                onCancel = {})
        }

        composeTestRule.onNodeWithText("Single Photo")
            .performClick()

        composeTestRule.onNodeWithText("Auto-detect bounding boxes")
            .assertDoesNotExist()
    }

    @Test
    @DisplayName("should display cancel button")
    fun shouldDisplayCancelButton() {
        composeTestRule.setContent {
            ImportScreen(
                state = wizardState,
                onImageSelected = {},
                onCancel = {})
        }

        composeTestRule.onNodeWithContentDescription("Cancel")
            .assertIsDisplayed()
    }

    @Test
    @DisplayName("should call onCancel when clicked")
    fun shouldCallOnCancelWhenClicked() {
        var cancelCalled = false

        composeTestRule.setContent {
            ImportScreen(
                state = wizardState,
                onImageSelected = {},
                onCancel = { cancelCalled = true })
        }

        composeTestRule.onNodeWithContentDescription("Cancel")
            .performClick()

        assertThat(cancelCalled).isTrue()
    }

    @Test
    @DisplayName("should display Select Image button")
    fun shouldDisplaySelectImageButton() {
        composeTestRule.setContent {
            ImportScreen(
                state = wizardState,
                onImageSelected = {},
                onCancel = {})
        }

        composeTestRule.onNodeWithText("Select Image")
            .assertIsDisplayed()
    }
}
