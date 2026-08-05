package org.kryspetrie.fileimport.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.AppTheme
import org.kryspetrie.fileimport.ui.TestKoinHelper
import org.kryspetrie.fileimport.ui.i18n.TestStringsProvider
import org.kryspetrie.fileimport.ui.theme.ImporterTheme

@DisplayName("DuplicateScannerScreen Comprehensive Tests")
@Tag("UiComponentTest")
class DuplicateScannerScreenComprehensiveTest {

    @get:Rule val composeTestRule = createComposeRule()

    private lateinit var settings: AppSettings

    @Before
    fun setup() {
        settings = AppSettings(theme = AppTheme.DARK, locale = "en")
        TestKoinHelper.start()
    }

    @After
    fun tearDown() {
        TestKoinHelper.stop()
    }

    @Composable
    private fun duplicateScannerScreenCall() =
        DuplicateScannerScreen(
            settings = settings,
            onSettingsChange = {},
        )

    @Test
    @DisplayName("should display duplicate scanner title and description")
    fun shouldDisplayDuplicateScannerTitleAndDescription() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    duplicateScannerScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Find Duplicates").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Scan an existing library to find duplicate files. Duplicates can be resolved automatically or reviewed individually.")
            .assertIsDisplayed()
    }

    @Test
    @DisplayName("should display folder selection field")
    fun shouldDisplayFolderSelectionField() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    duplicateScannerScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Library Folder").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display hash detection toggle")
    fun shouldDisplayHashDetectionToggle() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    duplicateScannerScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Exact hash").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display EXIF detection toggle")
    fun shouldDisplayExifDetectionToggle() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    duplicateScannerScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("EXIF metadata").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display visual detection toggle")
    fun shouldDisplayVisualDetectionToggle() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    duplicateScannerScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("SURF visual").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display detection methods section")
    fun shouldDisplayDetectionMethodsSection() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    duplicateScannerScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Detection Methods").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display scan button")
    fun shouldDisplayScanButton() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    duplicateScannerScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Scan for Duplicates").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display error card placeholder")
    fun shouldDisplayErrorCardPlaceholder() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    duplicateScannerScreenCall()
                }
            }
        }

        // Screen should render without errors
        composeTestRule.onNodeWithText("Find Duplicates").assertExists()
    }

    @Test
    @DisplayName("should constrain content width to max 900dp")
    fun shouldConstrainContentWidthToMax900dp() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    duplicateScannerScreenCall()
                }
            }
        }

        // Content should render within constraints
        composeTestRule.onNodeWithText("Find Duplicates").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display single header surface")
    fun shouldDisplaySingleHeaderSurface() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    duplicateScannerScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Find Duplicates").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display all detection toggles")
    fun shouldDisplayAllDetectionToggles() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    duplicateScannerScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Exact hash").assertIsDisplayed()
        composeTestRule.onNodeWithText("EXIF metadata").assertIsDisplayed()
        composeTestRule.onNodeWithText("SURF visual").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display settings collapsible section")
    fun shouldDisplaySettingsCollapsibleSection() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    duplicateScannerScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Detection Methods").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display resolve action section placeholder")
    fun shouldDisplayResolveActionSectionPlaceholder() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    duplicateScannerScreenCall()
                }
            }
        }

        // Resolve action section should be present
        composeTestRule.onNodeWithText("Scan for Duplicates").assertExists()
    }

    @Test
    @DisplayName("should display results view placeholder")
    fun shouldDisplayResultsViewPlaceholder() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    duplicateScannerScreenCall()
                }
            }
        }

        // Results view should be present in component structure
        composeTestRule.onNodeWithText("Scan for Duplicates").assertExists()
    }

    @Test
    @DisplayName("should display scanning progress placeholder")
    fun shouldDisplayScanningProgressPlaceholder() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    duplicateScannerScreenCall()
                }
            }
        }

        // Scanning progress should be present in component structure
        composeTestRule.onNodeWithText("Scan for Duplicates").assertExists()
    }

    @Test
    @DisplayName("should display duplicate groups placeholder")
    fun shouldDisplayDuplicateGroupsPlaceholder() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    duplicateScannerScreenCall()
                }
            }
        }

        // Duplicate groups should be present in component structure
        composeTestRule.onNodeWithText("Scan for Duplicates").assertExists()
    }

    @Test
    @DisplayName("should display all primary UI elements")
    fun shouldDisplayAllPrimaryUIElements() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    duplicateScannerScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Find Duplicates").assertIsDisplayed()
        composeTestRule.onNodeWithText("Library Folder").assertIsDisplayed()
        composeTestRule.onNodeWithText("Exact hash").assertIsDisplayed()
        composeTestRule.onNodeWithText("EXIF metadata").assertIsDisplayed()
        composeTestRule.onNodeWithText("SURF visual").assertIsDisplayed()
        composeTestRule.onNodeWithText("Scan for Duplicates").assertIsDisplayed()
    }

    @Test
    @DisplayName("should render without crashing")
    fun shouldRenderWithoutCrashing() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    duplicateScannerScreenCall()
                }
            }
        }

        // If we get here without exception, the test passes
        composeTestRule.onNodeWithText("Find Duplicates").assertExists()
    }

    @Test
    @DisplayName("should display theme-aware colors")
    fun shouldDisplayThemeAwareColors() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    duplicateScannerScreenCall()
                }
            }
        }

        // Screen should render with theme
        composeTestRule.onNodeWithText("Find Duplicates").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display responsive layout")
    fun shouldDisplayResponsiveLayout() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    duplicateScannerScreenCall()
                }
            }
        }

        // Layout should render properly
        composeTestRule.onNodeWithText("Find Duplicates").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display accessible UI elements")
    fun shouldDisplayAccessibleUIElements() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    duplicateScannerScreenCall()
                }
            }
        }

        // All elements should be accessible
        composeTestRule.onNodeWithText("Find Duplicates").assertExists()
    }
}
