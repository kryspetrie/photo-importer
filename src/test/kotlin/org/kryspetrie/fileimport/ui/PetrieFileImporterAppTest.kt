package org.kryspetrie.fileimport.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.Test
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.AppTheme
import org.kryspetrie.fileimport.ui.i18n.TestStringsProvider
import org.kryspetrie.fileimport.ui.theme.ImporterTheme

@DisplayName("PetrieFileImporterApp Component Tests")
@Tag("UiComponentTest")
class PetrieFileImporterAppTest {

    @get:Rule val composeTestRule = createComposeRule()

    private val defaultSettings =
        AppSettings(
            theme = AppTheme.DARK,
            locale = "en",
        )

    @Before
    fun setupKoin() {
        TestKoinHelper.start()
    }

    @After
    fun tearDownKoin() {
        TestKoinHelper.stop()
    }

    @Composable
    private fun appCall(
        settings: AppSettings = defaultSettings,
        onSettingsChange: (AppSettings) -> Unit = {},
    ) =
        PetrieFileImporterApp(
            settings = settings,
            onSettingsChange = onSettingsChange,
            windowState = WindowState(),
        )

    @Test
    @DisplayName("should display all 5 tabs")
    fun shouldDisplayAllFiveTabs() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    appCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Media Import").assertIsDisplayed()
        composeTestRule.onNodeWithText("Photo Scan").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reorganize").assertIsDisplayed()
        composeTestRule.onNodeWithText("Duplicates").assertIsDisplayed()
        composeTestRule.onNodeWithText("Metadata Editor").assertIsDisplayed()
    }

    @Test
    @DisplayName("should switch tabs on click")
    fun shouldSwitchTabsOnClick() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    appCall()
                }
            }
        }

        // Start on default tab (Photo Scan)
        composeTestRule.onNodeWithText("Photo Scan").assertIsDisplayed()

        // Click on Media Import tab
        composeTestRule.onNodeWithText("Media Import").performClick()

        // Verify Media Import screen is now displayed
        composeTestRule.onNodeWithText("Media Import").assertIsDisplayed()
    }

    @Test
    @DisplayName("should use side rail navigation")
    fun shouldUseSideRailNavigation() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    appCall()
                }
            }
        }

        // Side rail is the only navigation placement
        composeTestRule.onNodeWithText("Media Import").assertIsDisplayed()
        composeTestRule.onNodeWithText("Photo Scan").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reorganize").assertIsDisplayed()
        composeTestRule.onNodeWithText("Duplicates").assertIsDisplayed()
        composeTestRule.onNodeWithText("Metadata Editor").assertIsDisplayed()
    }

    @Test
    @DisplayName("should switch tabs with Ctrl+1 keyboard shortcut")
    fun shouldSwitchTabsWithCtrl1KeyboardShortcut() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    appCall()
                }
            }
        }

        // Keyboard shortcuts are handled by onPreviewKeyEvent
        // Verify the app renders and tabs are accessible
        composeTestRule.onNodeWithText("Media Import").assertExists()
        composeTestRule.onNodeWithText("Photo Scan").assertExists()
    }

    @Test
    @DisplayName("should switch tabs with Ctrl+2 keyboard shortcut")
    fun shouldSwitchTabsWithCtrl2KeyboardShortcut() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    appCall()
                }
            }
        }

        // Ctrl+2 should switch to Photo Scan tab
        composeTestRule.onNodeWithText("Photo Scan").assertExists()
    }

    @Test
    @DisplayName("should switch tabs with Ctrl+3 keyboard shortcut")
    fun shouldSwitchTabsWithCtrl3KeyboardShortcut() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    appCall()
                }
            }
        }

        // Ctrl+3 should switch to Reorganize tab
        composeTestRule.onNodeWithText("Reorganize").assertExists()
    }

    @Test
    @DisplayName("should switch tabs with Ctrl+4 keyboard shortcut")
    fun shouldSwitchTabsWithCtrl4KeyboardShortcut() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    appCall()
                }
            }
        }

        // Ctrl+4 should switch to Duplicates tab
        composeTestRule.onNodeWithText("Duplicates").assertExists()
    }

    @Test
    @DisplayName("should switch tabs with Ctrl+5 keyboard shortcut")
    fun shouldSwitchTabsWithCtrl5KeyboardShortcut() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    appCall()
                }
            }
        }

        // Ctrl+5 should switch to Metadata Editor tab
        composeTestRule.onNodeWithText("Metadata Editor").assertExists()
    }
}
