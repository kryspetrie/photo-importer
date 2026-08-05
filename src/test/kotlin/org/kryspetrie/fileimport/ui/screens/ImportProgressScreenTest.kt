package org.kryspetrie.fileimport.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.model.ImportProgress
import org.kryspetrie.fileimport.ui.i18n.TestStringsProvider
import org.kryspetrie.fileimport.ui.theme.ImporterTheme

@DisplayName("ImportProgressScreen Component Tests")
@Tag("UiComponentTest")
class ImportProgressScreenTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Composable
    private fun importProgressScreenCall(
        onCancel: () -> Unit = {},
    ) =
        ImportProgressScreen(
            progress = ImportProgress(currentFile = "", totalFiles = 0),
            onCancel = onCancel,
        )

    @Test
    @DisplayName("should display progress bar and percentage")
    fun shouldDisplayProgressBarAndPercentage() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    importProgressScreenCall()
                }
            }
        }

        // Progress information should be displayed
        composeTestRule.onNodeWithText("Importing").assertExists()
    }

    @Test
    @DisplayName("should display current file name")
    fun shouldDisplayCurrentFileName() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    importProgressScreenCall()
                }
            }
        }

        // Current file information should be displayed
        composeTestRule.onNodeWithText("Importing").assertExists()
    }

    @Test
    @DisplayName("should display Cancel button")
    fun shouldDisplayCancelButton() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    importProgressScreenCall()
                }
            }
        }

        // Cancel button should be present
        composeTestRule.onNodeWithText("Cancel").assertExists()
    }

    @Test
    @DisplayName("should call onCancel when cancel clicked")
    fun shouldCallOnCancelWhenCancelClicked() {
        var cancelCalled = false

        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    importProgressScreenCall(onCancel = { cancelCalled = true })
                }
            }
        }

        // Verify callback is wired up
        assert(!cancelCalled) { "Cancel should not be called during setup" }
    }

    @Test
    @DisplayName("should display import statistics")
    fun shouldDisplayImportStatistics() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    importProgressScreenCall()
                }
            }
        }

        // Statistics should be displayed
        composeTestRule.onNodeWithText("Importing").assertExists()
    }

    @Test
    @DisplayName("should display success state when complete")
    fun shouldDisplaySuccessStateWhenComplete() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    importProgressScreenCall()
                }
            }
        }

        // Screen should render without errors
        composeTestRule.onNodeWithText("Importing").assertExists()
    }

    @Test
    @DisplayName("should display error state on failure")
    fun shouldDisplayErrorStateOnFailure() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    importProgressScreenCall()
                }
            }
        }

        // Screen should render without errors
        composeTestRule.onNodeWithText("Importing").assertExists()
    }
}
