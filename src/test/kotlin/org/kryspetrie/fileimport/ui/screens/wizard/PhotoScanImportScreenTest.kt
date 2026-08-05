package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import java.io.File
import org.junit.Rule
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.port.SettingsPort
import org.kryspetrie.fileimport.infrastructure.adapter.SettingsAdapter
import org.kryspetrie.fileimport.ui.wizard.state.PhotoScanWizardState

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
    private fun importScreenCall(
        onImageSelected: (File, List<File>?) -> Unit,
        onCancel: () -> Unit,
    ) =
        PhotoScanImportScreen(
            state = wizardState,
            settingsPort = settingsPort,
            onSettingsChange = {},
            onImageSelected = onImageSelected,
            onCancel = onCancel,
        )

    @Test
    @DisplayName("should display landing hero with workflow steps")
    fun shouldDisplayLandingHero() {
        composeTestRule.setContent {
            importScreenCall(onImageSelected = { _, _ -> }, onCancel = {})
        }

        composeTestRule
            .onNodeWithText(
                "Digitize photos on a solid background — detect each print, fix perspective, add metadata, and export."
            )
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Select source → Adjust boxes → Edit metadata → Export")
            .assertIsDisplayed()
    }

    @Test
    @DisplayName("should display scan mode cards")
    fun shouldDisplayScanModeCards() {
        composeTestRule.setContent {
            importScreenCall(onImageSelected = { _, _ -> }, onCancel = {})
        }

        composeTestRule.onNodeWithText("Photo Scan").assertIsDisplayed()
        composeTestRule.onNodeWithText("Single Photo").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display auto-detect toggle")
    fun shouldDisplayAutoDetectToggle() {
        composeTestRule.setContent {
            importScreenCall(onImageSelected = { _, _ -> }, onCancel = {})
        }

        composeTestRule.onNodeWithText("Auto-detect bounding boxes").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display import scans button")
    fun shouldDisplayImportScansButton() {
        composeTestRule.setContent {
            importScreenCall(onImageSelected = { _, _ -> }, onCancel = {})
        }

        composeTestRule.onNodeWithText("Import Scans").assertIsDisplayed()
    }
}
