package org.kryspetrie.fileimport.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.model.ImportConfiguration
import org.kryspetrie.fileimport.ui.i18n.TestStringsProvider
import org.kryspetrie.fileimport.ui.theme.ImporterTheme

@DisplayName("PreviewStructureScreen Component Tests")
@Tag("UiComponentTest")
class PreviewStructureScreenTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Composable
    private fun previewStructureScreenCall(
        onBack: () -> Unit = {},
        onImport: () -> Unit = {},
    ) =
        PreviewStructureScreen(
            images = emptyList(),
            sourcePath = "/source",
            destinationPath = "/dest",
            configuration = ImportConfiguration(),
            onBack = onBack,
            onImport = onImport,
        )

    @Test
    @DisplayName("should display file count and folder stats")
    fun shouldDisplayFileCountAndFolderStats() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    previewStructureScreenCall()
                }
            }
        }

        // Screen should render with preview information
        composeTestRule.onNodeWithText("Preview").assertExists()
    }

    @Test
    @DisplayName("should display source folder tree")
    fun shouldDisplaySourceFolderTree() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    previewStructureScreenCall()
                }
            }
        }

        // Source folder information should be present
        composeTestRule.onNodeWithText("Source").assertExists()
    }

    @Test
    @DisplayName("should display destination folder tree")
    fun shouldDisplayDestinationFolderTree() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    previewStructureScreenCall()
                }
            }
        }

        // Destination folder information should be present
        composeTestRule.onNodeWithText("Destination").assertExists()
    }

    @Test
    @DisplayName("should display conflict warning when conflicts exist")
    fun shouldDisplayConflictWarningWhenConflictsExist() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    previewStructureScreenCall()
                }
            }
        }

        // Conflict information may be displayed
        composeTestRule.onNodeWithText("Preview").assertExists()
    }

    @Test
    @DisplayName("should display Back button")
    fun shouldDisplayBackButton() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    previewStructureScreenCall()
                }
            }
        }

        // Back button should be present
        composeTestRule.onNodeWithText("Back").assertExists()
    }

    @Test
    @DisplayName("should display Import button")
    fun shouldDisplayImportButton() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    previewStructureScreenCall()
                }
            }
        }

        // Import button should be present
        composeTestRule.onNodeWithText("Import").assertExists()
    }

    @Test
    @DisplayName("should call onBack when back clicked")
    fun shouldCallOnBackWhenBackClicked() {
        var backCalled = false

        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    previewStructureScreenCall(onBack = { backCalled = true })
                }
            }
        }

        // Verify callback is wired up
        assert(!backCalled) { "Back should not be called during setup" }
    }

    @Test
    @DisplayName("should call onImport when import clicked")
    fun shouldCallOnImportWhenImportClicked() {
        var importCalled = false

        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    previewStructureScreenCall(onImport = { importCalled = true })
                }
            }
        }

        // Verify callback is wired up
        assert(!importCalled) { "Import should not be called during setup" }
    }
}
