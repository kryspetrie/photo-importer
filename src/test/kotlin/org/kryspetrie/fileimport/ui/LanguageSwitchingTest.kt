package org.kryspetrie.fileimport.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.window.WindowState
import org.junit.Rule
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.AppTheme
import org.kryspetrie.fileimport.ui.i18n.TestStringsProvider
import org.kryspetrie.fileimport.ui.theme.ImporterTheme

@DisplayName("Language Switching Component Tests")
@Tag("UiComponentTest")
class LanguageSwitchingTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Composable
    private fun appCall(
        settings: AppSettings,
        onSettingsChange: (AppSettings) -> Unit = {},
    ) =
        PetrieFileImporterApp(
            settings = settings,
            onSettingsChange = onSettingsChange,
            windowState = WindowState(),
        )

    @Test
    @DisplayName("should update MenuBar text immediately when locale changes")
    fun shouldUpdateMenuBarTextOnLocaleChange() {
        val englishSettings =
            AppSettings(
                theme = AppTheme.DARK,
                locale = "en",
            )

        val spanishSettings =
            AppSettings(
                theme = AppTheme.DARK,
                locale = "es",
            )

        // Start with English
        composeTestRule.setContent {
            TestStringsProvider("en") {
                ImporterTheme {
                    appCall(settings = englishSettings)
                }
            }
        }

        // Verify English text is displayed
        composeTestRule.onNodeWithText("Media Import").assertIsDisplayed()
        composeTestRule.onNodeWithText("Photo Scan").assertIsDisplayed()

        // Switch to Spanish
        composeTestRule.setContent {
            TestStringsProvider("es") {
                ImporterTheme {
                    appCall(settings = spanishSettings)
                }
            }
        }

        // Verify Spanish text is displayed immediately
        composeTestRule.onNodeWithText("Importar medios").assertIsDisplayed()
        composeTestRule.onNodeWithText("Escanear foto").assertIsDisplayed()
    }

    @Test
    @DisplayName("should update all tab labels when locale changes")
    fun shouldUpdateAllTabLabelsOnLocaleChange() {
        // Start with English
        composeTestRule.setContent {
            TestStringsProvider("en") {
                ImporterTheme {
                    appCall(
                        settings =
                            AppSettings(
                                theme = AppTheme.DARK,
                                locale = "en",
                            )
                    )
                }
            }
        }

        // Verify all English tab labels
        composeTestRule.onNodeWithText("Media Import").assertIsDisplayed()
        composeTestRule.onNodeWithText("Photo Scan").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reorganize").assertIsDisplayed()
        composeTestRule.onNodeWithText("Duplicates").assertIsDisplayed()
        composeTestRule.onNodeWithText("Metadata Editor").assertIsDisplayed()

        // Switch to German
        composeTestRule.setContent {
            TestStringsProvider("de") {
                ImporterTheme {
                    appCall(
                        settings =
                            AppSettings(
                                theme = AppTheme.DARK,
                                locale = "de",
                            )
                    )
                }
            }
        }

        // Verify German tab labels
        composeTestRule.onNodeWithText("Medienimport").assertIsDisplayed()
        composeTestRule.onNodeWithText("Foto scannen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reorganisieren").assertIsDisplayed()
        composeTestRule.onNodeWithText("Duplikate").assertIsDisplayed()
        composeTestRule.onNodeWithText("Metadaten-Editor").assertIsDisplayed()
    }
}
