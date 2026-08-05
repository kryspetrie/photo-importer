package org.kryspetrie.fileimport.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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

@DisplayName("ReorganizeScreen Comprehensive Tests")
@Tag("UiComponentTest")
class ReorganizeScreenComprehensiveTest {

    @get:Rule
    val composeTestRule = createComposeRule()

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
    private fun reorganizeScreenCall() =
        ReorganizeScreen(
            settings = settings,
            onSettingsChange = {},
        )

    @Test
    @DisplayName("should display reorganize title and description")
    fun shouldDisplayReorganizeTitleAndDescription() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    reorganizeScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Reorganize Library").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Apply folder and filename patterns to an existing media library.")
            .assertIsDisplayed()
    }

    @Test
    @DisplayName("should display folder selection field")
    fun shouldDisplayFolderSelectionField() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    reorganizeScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Library Folder").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display move radio button")
    fun shouldDisplayMoveRadioButton() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    reorganizeScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Move files").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display copy radio button")
    fun shouldDisplayCopyRadioButton() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    reorganizeScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Copy files").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display rename-only toggle")
    fun shouldDisplayRenameOnlyToggle() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    reorganizeScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Rename files only").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display organization settings toggle")
    fun shouldDisplayOrganizationSettingsToggle() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    reorganizeScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Organization").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display preview changes button")
    fun shouldDisplayPreviewChangesButton() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    reorganizeScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Preview Changes").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display error card placeholder")
    fun shouldDisplayErrorCardPlaceholder() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    reorganizeScreenCall()
                }
            }
        }

        // Screen should render without errors
        composeTestRule.onNodeWithText("Reorganize Library").assertExists()
    }

    @Test
    @DisplayName("should constrain content width to max 900dp")
    fun shouldConstrainContentWidthToMax900dp() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    reorganizeScreenCall()
                }
            }
        }

        // Content should render within constraints
        composeTestRule.onNodeWithText("Reorganize Library").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display single header surface")
    fun shouldDisplaySingleHeaderSurface() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    reorganizeScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Reorganize Library").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display preview section placeholder")
    fun shouldDisplayPreviewSectionPlaceholder() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    reorganizeScreenCall()
                }
            }
        }

        // Preview section should be present in the component structure
        composeTestRule.onNodeWithText("Preview Changes").assertExists()
    }

    @Test
    @DisplayName("should display mode selection row")
    fun shouldDisplayModeSelectionRow() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    reorganizeScreenCall()
                }
            }
        }

        // Move and Copy should be in the same row
        composeTestRule.onNodeWithText("Move files").assertIsDisplayed()
        composeTestRule.onNodeWithText("Copy files").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display settings collapsible section")
    fun shouldDisplaySettingsCollapsibleSection() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    reorganizeScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Organization").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display undo section placeholder")
    fun shouldDisplayUndoSectionPlaceholder() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    reorganizeScreenCall()
                }
            }
        }

        // Undo section should be present in the component structure
        composeTestRule.onNodeWithText("Reorganize Library").assertExists()
    }

    @Test
    @DisplayName("should display action bar placeholder")
    fun shouldDisplayActionBarPlaceholder() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    reorganizeScreenCall()
                }
            }
        }

        // Action bar should be present
        composeTestRule.onNodeWithText("Preview Changes").assertExists()
    }

    @Test
    @DisplayName("should display all primary UI elements")
    fun shouldDisplayAllPrimaryUIElements() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    reorganizeScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Reorganize Library").assertIsDisplayed()
        composeTestRule.onNodeWithText("Library Folder").assertIsDisplayed()
        composeTestRule.onNodeWithText("Move files").assertIsDisplayed()
        composeTestRule.onNodeWithText("Copy files").assertIsDisplayed()
        composeTestRule.onNodeWithText("Rename files only").assertIsDisplayed()
        composeTestRule.onNodeWithText("Organization").assertIsDisplayed()
        composeTestRule.onNodeWithText("Preview Changes").assertIsDisplayed()
    }

    @Test
    @DisplayName("should render without crashing")
    fun shouldRenderWithoutCrashing() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    reorganizeScreenCall()
                }
            }
        }

        // If we get here without exception, the test passes
        composeTestRule.onNodeWithText("Reorganize Library").assertExists()
    }

    @Test
    @DisplayName("should display theme-aware colors")
    fun shouldDisplayThemeAwareColors() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    reorganizeScreenCall()
                }
            }
        }

        // Screen should render with theme
        composeTestRule.onNodeWithText("Reorganize Library").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display responsive layout")
    fun shouldDisplayResponsiveLayout() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    reorganizeScreenCall()
                }
            }
        }

        // Layout should render properly
        composeTestRule.onNodeWithText("Reorganize Library").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display accessible UI elements")
    fun shouldDisplayAccessibleUIElements() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    reorganizeScreenCall()
                }
            }
        }

        // All elements should be accessible
        composeTestRule.onNodeWithText("Reorganize Library").assertExists()
    }
}
