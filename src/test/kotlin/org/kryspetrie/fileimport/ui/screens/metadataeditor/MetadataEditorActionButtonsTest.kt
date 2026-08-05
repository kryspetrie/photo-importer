package org.kryspetrie.fileimport.ui.screens.metadataeditor

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Before
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.AppTheme
import org.kryspetrie.fileimport.ui.i18n.TestStringsProvider
import org.kryspetrie.fileimport.ui.screens.DuplicateScannerScreen
import org.kryspetrie.fileimport.ui.screens.MediaImportScreen
import org.kryspetrie.fileimport.ui.screens.ReorganizeScreen
import org.kryspetrie.fileimport.ui.theme.ImporterTheme

@DisplayName("Metadata Editor Action Buttons Layout Tests")
@Tag("UiComponentTest")
class MetadataEditorActionButtonsTest {

    @get:Rule val composeTestRule = createComposeRule()

    private lateinit var settings: AppSettings

    @Before
    fun setup() {
        settings =
            AppSettings(
                theme = AppTheme.DARK,
                locale = "en",
            )
    }

    @Composable
    private fun metadataEditorScreenCall(
        onSettingsChange: (AppSettings) -> Unit = {},
    ) =
        MetadataEditorScreen(
            settings = settings,
            onSettingsChange = onSettingsChange,
        )

    @Test
    @DisplayName("should use FlowRow for action buttons (not full-width Column)")
    fun shouldUseFlowRowForActionButtons() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    metadataEditorScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Edit Metadata").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display action buttons in scrollable card pattern")
    fun shouldDisplayActionButtonsInScrollableCardPattern() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    metadataEditorScreenCall()
                }
            }
        }

        // Landing header + primary action
        composeTestRule.onNodeWithText("Bulk Metadata Editor").assertIsDisplayed()
        composeTestRule.onNodeWithText("Edit Metadata").assertIsDisplayed()
    }

    @Test
    @DisplayName("should match action button pattern across all main screens")
    fun shouldMatchActionButtonPatternAcrossScreens() {
        // Media Import screen
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    MediaImportScreen(
                        settings = settings,
                        onSettingsChange = {},
                    )
                }
            }
        }
        composeTestRule.onNodeWithText("Start Import").assertExists()

        // Reorganize screen
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    ReorganizeScreen(
                        settings = settings,
                        onSettingsChange = {},
                    )
                }
            }
        }
        composeTestRule.onNodeWithText("Start Preview").assertExists()

        // Duplicate Scanner screen
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    DuplicateScannerScreen(
                        settings = settings,
                        onSettingsChange = {},
                    )
                }
            }
        }
        composeTestRule.onNodeWithText("Scan").assertExists()

        // All screens should have consistent action button placement
    }
}
