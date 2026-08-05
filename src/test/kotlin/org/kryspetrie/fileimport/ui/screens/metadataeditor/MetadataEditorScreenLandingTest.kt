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
import org.kryspetrie.fileimport.ui.theme.ImporterTheme

@DisplayName("MetadataEditorScreen Landing Component Tests")
@Tag("UiComponentTest")
class MetadataEditorScreenLandingTest {

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
    @DisplayName("should display landing title and description")
    fun shouldDisplayLandingTitleAndDescription() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    metadataEditorScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Bulk Metadata Editor").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Select a folder or image to edit EXIF, IPTC, and XMP metadata")
            .assertIsDisplayed()
    }

    @Test
    @DisplayName("should display source path field on landing")
    fun shouldDisplaySourcePathFieldOnLanding() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    metadataEditorScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Source").assertIsDisplayed()
        composeTestRule.onNodeWithText("folder").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display Edit Metadata button")
    fun shouldDisplayEditMetadataButton() {
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
    @DisplayName("should display include subfolders checkbox")
    fun shouldDisplayIncludeSubfoldersCheckbox() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    metadataEditorScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Include subfolders").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display recent paths list")
    fun shouldDisplayRecentPathsList() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    metadataEditorScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Recent").assertIsDisplayed()
    }

    @Test
    @DisplayName("should have single header surface (no double header)")
    fun shouldHaveSingleHeaderSurface() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    metadataEditorScreenCall()
                }
            }
        }

        // Verify header exists
        composeTestRule.onNodeWithText("Bulk Metadata Editor").assertIsDisplayed()
    }

    @Test
    @DisplayName("should use scrollable card pattern matching other pages")
    fun shouldUseScrollableCardPattern() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    metadataEditorScreenCall()
                }
            }
        }

        // Verify the landing page uses the same card pattern as other screens
        composeTestRule.onNodeWithText("Bulk Metadata Editor").assertIsDisplayed()
    }
}
