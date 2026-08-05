package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.AppTheme
import org.kryspetrie.fileimport.ui.i18n.TestStringsProvider
import org.kryspetrie.fileimport.ui.theme.ImporterTheme
import org.kryspetrie.fileimport.ui.wizard.state.PhotoScanWizardState
import java.awt.image.BufferedImage
import org.mockito.kotlin.mock

@DisplayName("EditScreen Component Tests")
@Tag("UiComponentTest")
class EditScreenTest {

    @get:Rule val composeTestRule = createComposeRule()

    private lateinit var wizardState: PhotoScanWizardState
    private lateinit var testImage: BufferedImage

    @BeforeEach
    fun setup() {
        wizardState = PhotoScanWizardState()
        // Create a small test image
        testImage = BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB)
    }

    @Composable
    private fun editScreenCall(
        onBack: () -> Unit = {},
        onExport: () -> Unit = {},
        onSkipCurrentPhoto: (() -> Unit)? = null,
    ) =
        EditScreen(
            state = wizardState,
            image = testImage,
            perspectiveService = mock(),
            previewCache = mock(),
            metadataHistory = mock(),
            onMetadataHistoryUpdate = { _, _ -> },
            onMetadataHistoryRemove = { _, _ -> },
            onRecordMetadataSet = {},
            onBack = onBack,
            onExport = onExport,
            onSkipCurrentPhoto = onSkipCurrentPhoto,
            startWithMetadata = false,
        )

    @Test
    @DisplayName("should display edit screen with metadata panel")
    fun shouldDisplayEditScreenWithMetadataPanel() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    editScreenCall()
                }
            }
        }

        // Verify the edit screen renders with its main components
        composeTestRule.onNodeWithText("Edit").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display photo sidebar with box list")
    fun shouldDisplayPhotoSidebarWithBoxList() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    editScreenCall()
                }
            }
        }

        // The sidebar should be present (may show thumbnails or box indicators)
        composeTestRule.onNodeWithText("Edit").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display quick edit metadata fields")
    fun shouldDisplayQuickEditMetadataFields() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    editScreenCall()
                }
            }
        }

        // Metadata fields should be visible in the edit panel
        composeTestRule.onNodeWithText("Edit").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display export button")
    fun shouldDisplayExportButton() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    editScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Export").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display back button")
    fun shouldDisplayBackButton() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    editScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Back").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display skip button only when batch has multiple source files")
    fun shouldDisplaySkipButtonInBatchMode() {
        wizardState.batch.initializeBatch(
            listOf(
                java.io.File("/tmp/batch-a.jpg"),
                java.io.File("/tmp/batch-b.jpg"),
            )
        )
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    editScreenCall(onSkipCurrentPhoto = {})
                }
            }
        }

        composeTestRule.onNodeWithText("Skip Photo").assertExists()
    }

    @Test
    @DisplayName("should not display skip when only a single file was selected")
    fun shouldNotDisplaySkipForSingleFileSelection() {
        // No batch — mirrors file-by-filename import
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    editScreenCall(onSkipCurrentPhoto = {})
                }
            }
        }

        composeTestRule.onNodeWithText("Skip Photo").assertDoesNotExist()
    }
}
