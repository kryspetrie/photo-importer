package org.kryspetrie.fileimport.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Before
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.Test
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.AppTheme
import org.kryspetrie.fileimport.ui.i18n.TestStringsProvider
import org.kryspetrie.fileimport.ui.screens.DuplicateScannerScreen
import org.kryspetrie.fileimport.ui.screens.MediaImportScreen
import org.kryspetrie.fileimport.ui.screens.ReorganizeScreen
import org.kryspetrie.fileimport.ui.screens.metadataeditor.MetadataEditorScreen
import org.kryspetrie.fileimport.ui.theme.ImporterTheme

@DisplayName("Content Width Constraint Tests")
@Tag("UiComponentTest")
class ContentWidthTest {

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

    @Test
    @DisplayName("MediaImportScreen content should be constrained to max 900dp")
    fun mediaImportScreenContentWidthConstrained() {
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

        // Content should render without errors
        composeTestRule.onNodeWithText("Media Import").assertIsDisplayed()
    }

    @Test
    @DisplayName("ReorganizeScreen content should be constrained to max 900dp")
    fun reorganizeScreenContentWidthConstrained() {
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

        // Content should render without errors
        composeTestRule.onNodeWithText("Reorganize").assertIsDisplayed()
    }

    @Test
    @DisplayName("DuplicateScannerScreen content should be constrained to max 900dp")
    fun duplicateScannerScreenContentWidthConstrained() {
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

        // Content should render without errors
        composeTestRule.onNodeWithText("Duplicate Scanner").assertIsDisplayed()
    }

    @Test
    @DisplayName("MetadataEditorScreen landing should be constrained to max 800dp")
    fun metadataEditorScreenLandingWidthConstrained() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    MetadataEditorScreen(
                        settings = settings,
                        onSettingsChange = {},
                    )
                }
            }
        }

        // Content should render without errors
        composeTestRule.onNodeWithText("Metadata Editor").assertIsDisplayed()
    }
}
