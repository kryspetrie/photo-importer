package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.port.SettingsPort
import org.kryspetrie.fileimport.infrastructure.adapter.SettingsAdapter
import org.kryspetrie.fileimport.infrastructure.wizard.*

/**
 * Component tests for PhotoScanImportScreen. Tests UI rendering and user interactions with mode
 * selection.
 *
 * Uses JUnit 4 style with @get:Rule for ComposeTestRule. Tagged with @Tag(UiComponentTest::class)
 * for test filtering.
 */
@DisplayName("PhotoScanImportScreen Component Tests")
@Tag("UiComponentTest")
class PhotoScanImportScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var wizardState: PhotoScanWizardState
  private lateinit var settingsPort: SettingsPort

  @BeforeEach
  fun setup() {
    wizardState = PhotoScanWizardState()
    settingsPort = SettingsAdapter(File(System.getProperty("java.io.tmpdir"), "test-settings"))
  }

  @Composable
  private fun importScreenCall(onImageSelected: (File) -> Unit, onCancel: () -> Unit) =
      PhotoScanImportScreen(
          state = wizardState,
          settingsPort = settingsPort,
          onSettingsChange = {},
          onImageSelected = onImageSelected,
          onCancel = onCancel)

  @Test
  @DisplayName("should display Photo Scan Import title")
  fun shouldDisplayTitle() {
    composeTestRule.setContent { importScreenCall(onImageSelected = {}, onCancel = {}) }

    composeTestRule.onNodeWithText("Photo Scan Import").assertIsDisplayed()
  }

  @Test
  @DisplayName("should display Import Mode header")
  fun shouldDisplayImportModeHeader() {
    composeTestRule.setContent { importScreenCall(onImageSelected = {}, onCancel = {}) }

    composeTestRule.onNodeWithText("Import Mode").assertIsDisplayed()
  }

  @Test
  @DisplayName("should display Photo Scan mode card")
  fun shouldDisplayPhotoScanCard() {
    composeTestRule.setContent { importScreenCall(onImageSelected = {}, onCancel = {}) }

    composeTestRule.onNodeWithText("Photo Scan").assertIsDisplayed()
  }

  @Test
  @DisplayName("should display Single Photo mode card")
  fun shouldDisplaySinglePhotoCard() {
    composeTestRule.setContent { importScreenCall(onImageSelected = {}, onCancel = {}) }

    composeTestRule.onNodeWithText("Single Photo").assertIsDisplayed()
  }

  @Test
  @DisplayName("should display auto-detect toggle")
  fun shouldDisplayAutoDetectToggle() {
    composeTestRule.setContent { importScreenCall(onImageSelected = {}, onCancel = {}) }

    composeTestRule.onNodeWithText("Auto-detect bounding boxes").assertIsDisplayed()
  }

  @Test
  @DisplayName("should display cancel button")
  fun shouldDisplayCancelButton() {
    composeTestRule.setContent { importScreenCall(onImageSelected = {}, onCancel = {}) }

    composeTestRule.onNodeWithContentDescription("Cancel").assertIsDisplayed()
  }

  @Test
  @DisplayName("should call onCancel when clicked")
  fun shouldCallOnCancelWhenClicked() {
    var cancelCalled = false

    composeTestRule.setContent {
      importScreenCall(onImageSelected = {}, onCancel = { cancelCalled = true })
    }

    composeTestRule.onNodeWithContentDescription("Cancel").performClick()

    assertThat(cancelCalled).isTrue()
  }

  @Test
  @DisplayName("should display Select Image button")
  fun shouldDisplaySelectImageButton() {
    composeTestRule.setContent { importScreenCall(onImageSelected = {}, onCancel = {}) }

    composeTestRule.onNodeWithText("Select Image").assertIsDisplayed()
  }
}
