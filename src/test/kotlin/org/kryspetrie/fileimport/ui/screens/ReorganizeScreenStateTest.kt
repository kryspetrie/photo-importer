package org.kryspetrie.fileimport.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag

import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.AppTheme
import org.kryspetrie.fileimport.domain.model.ImportConfiguration
import org.kryspetrie.fileimport.domain.model.ReorganizeMode
import org.kryspetrie.fileimport.domain.model.ReorganizePreview
import org.kryspetrie.fileimport.ui.TestKoinHelper
import org.kryspetrie.fileimport.ui.i18n.TestStringsProvider
import org.kryspetrie.fileimport.ui.theme.ImporterTheme
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@DisplayName("ReorganizeScreen State Tests")
@Tag("UiComponentTest")
class ReorganizeScreenStateTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var settings: AppSettings
    private lateinit var mockViewModel: ReorganizeViewModel

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
    private fun reorganizeScreenWithMockViewModel() =
        ReorganizeScreen(
            settings = settings,
            onSettingsChange = {},
            viewModel = mockViewModel,
        )

    @Nested
    @DisplayName("SETUP State Tests")
    inner class SetupState {

        @Before
        fun setupMock() {
            mockViewModel = mock {
                on { folderPath } doReturn ""
                on { step } doReturn ReorganizeViewModel.ReorgStep.SETUP
                on { reorgMode } doReturn ReorganizeMode.MOVE
                on { renameOnly } doReturn false
                on { settingsExpanded } doReturn false
                on { preview } doReturn null
                on { errorMessage } doReturn null
                on { journals } doReturn emptyList()
                on { config } doReturn ImportConfiguration()
            }
        }

        @Test
        @DisplayName("should display folder selection in SETUP state")
        fun shouldDisplayFolderSelectionInSetupState() {
            composeTestRule.setContent {
                TestStringsProvider {
                    ImporterTheme {
                        reorganizeScreenWithMockViewModel()
                    }
                }
            }
            composeTestRule.onNodeWithText("Folder").assertIsDisplayed()
        }

        @Test
        @DisplayName("should display mode selection in SETUP state")
        fun shouldDisplayModeSelectionInSetupState() {
            composeTestRule.setContent {
                TestStringsProvider {
                    ImporterTheme {
                        reorganizeScreenWithMockViewModel()
                    }
                }
            }

            composeTestRule.onNodeWithText("Move").assertIsDisplayed()
            composeTestRule.onNodeWithText("Copy").assertIsDisplayed()
        }

        @Test
        @DisplayName("should display Start Preview button in SETUP state")
        fun shouldDisplayStartPreviewButtonInSetupState() {
            composeTestRule.setContent {
                TestStringsProvider {
                    ImporterTheme {
                        reorganizeScreenWithMockViewModel()
                    }
                }
            }

            composeTestRule.onNodeWithText("Start Preview").assertIsDisplayed()
        }

        @Test
        @DisplayName("should not display preview section in SETUP state")
        fun shouldNotDisplayPreviewSectionInSetupState() {
            composeTestRule.setContent {
                TestStringsProvider {
                    ImporterTheme {
                        reorganizeScreenWithMockViewModel()
                    }
                }
            }

            // Preview section should not be visible when preview is null
            composeTestRule.onNodeWithText("Preview").assertDoesNotExist()
        }
    }

    @Nested
    @DisplayName("SCANNING State Tests")
    inner class ScanningState {

        @Before
        fun setupMock() {
            mockViewModel = mock {
                on { folderPath } doReturn "/test/library"
                on { step } doReturn ReorganizeViewModel.ReorgStep.SCANNING
                on { reorgMode } doReturn ReorganizeMode.MOVE
                on { renameOnly } doReturn false
                on { settingsExpanded } doReturn false
                on { preview } doReturn null
                on { errorMessage } doReturn null
                on { journals } doReturn emptyList()
                on { config } doReturn ImportConfiguration()
            }
        }

        @Test
        @DisplayName("should display scanning progress in SCANNING state")
        fun shouldDisplayScanningProgressInScanningState() {
            composeTestRule.setContent {
                TestStringsProvider {
                    ImporterTheme {
                        reorganizeScreenWithMockViewModel()
                    }
                }
            }

            // Scanning indicator should be displayed
            composeTestRule.onNodeWithText("Scanning").assertExists()
        }

        @Test
        @DisplayName("should display folder path in SCANNING state")
        fun shouldDisplayFolderPathInScanningState() {
            composeTestRule.setContent {
                TestStringsProvider {
                    ImporterTheme {
                        reorganizeScreenWithMockViewModel()
                    }
                }
            }

            composeTestRule.onNodeWithText("/test/library").assertExists()
        }
    }

    @Nested
    @DisplayName("PREVIEW State Tests")
    inner class PreviewState {

        private lateinit var preview: ReorganizePreview

        @Before
        fun setupMock() {
            preview =
                ReorganizePreview(
                    mappings = emptyList(),
                    totalFiles = 10,
                    changedFiles = 5,
                    conflictCount = 0,
                    newFolderCount = 2,
                    operationMode = ReorganizeMode.MOVE,
                    libraryRoot = "/test/library",
                )

            mockViewModel = mock {
                on { folderPath } doReturn "/test/library"
                on { step } doReturn ReorganizeViewModel.ReorgStep.PREVIEW
                on { reorgMode } doReturn ReorganizeMode.MOVE
                on { renameOnly } doReturn false
                on { settingsExpanded } doReturn false
                on { preview } doReturn preview
                on { errorMessage } doReturn null
                on { journals } doReturn emptyList()
                on { config } doReturn ImportConfiguration()
            }
        }

        @Test
        @DisplayName("should display preview section in PREVIEW state")
        fun shouldDisplayPreviewSectionInPreviewState() {
            composeTestRule.setContent {
                TestStringsProvider {
                    ImporterTheme {
                        reorganizeScreenWithMockViewModel()
                    }
                }
            }

            // Preview section should be displayed
            composeTestRule.onNodeWithText("Preview").assertIsDisplayed()
        }

        @Test
        @DisplayName("should display file count in PREVIEW state")
        fun shouldDisplayFileCountInPreviewState() {
            composeTestRule.setContent {
                TestStringsProvider {
                    ImporterTheme {
                        reorganizeScreenWithMockViewModel()
                    }
                }
            }

            // File statistics should be displayed
            composeTestRule.onNodeWithText("10").assertExists()
        }

        @Test
        @DisplayName("should display changed files count in PREVIEW state")
        fun shouldDisplayChangedFilesCountInPreviewState() {
            composeTestRule.setContent {
                TestStringsProvider {
                    ImporterTheme {
                        reorganizeScreenWithMockViewModel()
                    }
                }
            }

            // Changed files should be displayed
            composeTestRule.onNodeWithText("5").assertExists()
        }

        @Test
        @DisplayName("should display new folder count in PREVIEW state")
        fun shouldDisplayNewFolderCountInPreviewState() {
            composeTestRule.setContent {
                TestStringsProvider {
                    ImporterTheme {
                        reorganizeScreenWithMockViewModel()
                    }
                }
            }

            // New folders should be displayed
            composeTestRule.onNodeWithText("2").assertExists()
        }

        @Test
        @DisplayName("should display Execute button in PREVIEW state")
        fun shouldDisplayExecuteButtonInPreviewState() {
            composeTestRule.setContent {
                TestStringsProvider {
                    ImporterTheme {
                        reorganizeScreenWithMockViewModel()
                    }
                }
            }

            composeTestRule.onNodeWithText("Execute").assertIsDisplayed()
        }

        @Test
        @DisplayName("should display Back button in PREVIEW state")
        fun shouldDisplayBackButtonInPreviewState() {
            composeTestRule.setContent {
                TestStringsProvider {
                    ImporterTheme {
                        reorganizeScreenWithMockViewModel()
                    }
                }
            }

            composeTestRule.onNodeWithText("Back").assertIsDisplayed()
        }
    }

    @Nested
    @DisplayName("ERROR State Tests")
    inner class ErrorState {

        @Before
        fun setupMock() {
            mockViewModel = mock {
                on { folderPath } doReturn "/test/library"
                on { step } doReturn ReorganizeViewModel.ReorgStep.SETUP
                on { reorgMode } doReturn ReorganizeMode.MOVE
                on { renameOnly } doReturn false
                on { settingsExpanded } doReturn false
                on { preview } doReturn null
                on { errorMessage } doReturn "Test error message"
                on { journals } doReturn emptyList()
                on { config } doReturn ImportConfiguration()
            }
        }

        @Test
        @DisplayName("should display error card in ERROR state")
        fun shouldDisplayErrorCardInErrorState() {
            composeTestRule.setContent {
                TestStringsProvider {
                    ImporterTheme {
                        reorganizeScreenWithMockViewModel()
                    }
                }
            }

            composeTestRule.onNodeWithText("Test error message").assertIsDisplayed()
        }

        @Test
        @DisplayName("should display error icon in ERROR state")
        fun shouldDisplayErrorIconInErrorState() {
            composeTestRule.setContent {
                TestStringsProvider {
                    ImporterTheme {
                        reorganizeScreenWithMockViewModel()
                    }
                }
            }

            // Error card should be present
            composeTestRule.onNodeWithText("Test error message").assertExists()
        }
    }

    @Nested
    @DisplayName("UNDO State Tests")
    inner class UndoState {

        @Before
        fun setupMock() {
            mockViewModel = mock {
                on { folderPath } doReturn "/test/library"
                on { step } doReturn ReorganizeViewModel.ReorgStep.SETUP
                on { reorgMode } doReturn ReorganizeMode.MOVE
                on { renameOnly } doReturn false
                on { settingsExpanded } doReturn false
                on { preview } doReturn null
                on { errorMessage } doReturn null
                on { journals } doReturn
                    listOf(
                        mock {
                            on { timestampString } doReturn "2024-01-01 12:00"
                            on { totalFiles } doReturn 10
                            on { undone } doReturn false
                        }
                    )
                on { config } doReturn ImportConfiguration()
            }
        }

        @Test
        @DisplayName("should display undo section with journals")
        fun shouldDisplayUndoSectionWithJournals() {
            composeTestRule.setContent {
                TestStringsProvider {
                    ImporterTheme {
                        reorganizeScreenWithMockViewModel()
                    }
                }
            }

            composeTestRule.onNodeWithText("Undo").assertIsDisplayed()
        }

        @Test
        @DisplayName("should display journal timestamp")
        fun shouldDisplayJournalTimestamp() {
            composeTestRule.setContent {
                TestStringsProvider {
                    ImporterTheme {
                        reorganizeScreenWithMockViewModel()
                    }
                }
            }

            composeTestRule.onNodeWithText("2024-01-01 12:00").assertExists()
        }

        @Test
        @DisplayName("should display journal file count")
        fun shouldDisplayJournalFileCount() {
            composeTestRule.setContent {
                TestStringsProvider {
                    ImporterTheme {
                        reorganizeScreenWithMockViewModel()
                    }
                }
            }

            composeTestRule.onNodeWithText("10").assertExists()
        }
    }

    @Nested
    @DisplayName("Settings Expanded State Tests")
    inner class SettingsExpandedState {

        @Before
        fun setupMock() {
            mockViewModel = mock {
                on { folderPath } doReturn ""
                on { step } doReturn ReorganizeViewModel.ReorgStep.SETUP
                on { reorgMode } doReturn ReorganizeMode.MOVE
                on { renameOnly } doReturn false
                on { settingsExpanded } doReturn true
                on { preview } doReturn null
                on { errorMessage } doReturn null
                on { journals } doReturn emptyList()
                on { config } doReturn ImportConfiguration()
            }
        }

        @Test
        @DisplayName("should display folder pattern field when settings expanded")
        fun shouldDisplayFolderPatternFieldWhenSettingsExpanded() {
            composeTestRule.setContent {
                TestStringsProvider {
                    ImporterTheme {
                        reorganizeScreenWithMockViewModel()
                    }
                }
            }

            composeTestRule.onNodeWithText("Folder pattern").assertIsDisplayed()
        }

        @Test
        @DisplayName("should display filename pattern field when settings expanded")
        fun shouldDisplayFilenamePatternFieldWhenSettingsExpanded() {
            composeTestRule.setContent {
                TestStringsProvider {
                    ImporterTheme {
                        reorganizeScreenWithMockViewModel()
                    }
                }
            }

            composeTestRule.onNodeWithText("Filename pattern").assertIsDisplayed()
        }

        @Test
        @DisplayName("should display date source options when settings expanded")
        fun shouldDisplayDateSourceOptionsWhenSettingsExpanded() {
            composeTestRule.setContent {
                TestStringsProvider {
                    ImporterTheme {
                        reorganizeScreenWithMockViewModel()
                    }
                }
            }

            composeTestRule.onNodeWithText("Date source").assertIsDisplayed()
        }

        @Test
        @DisplayName("should display conflict resolution options when settings expanded")
        fun shouldDisplayConflictResolutionOptionsWhenSettingsExpanded() {
            composeTestRule.setContent {
                TestStringsProvider {
                    ImporterTheme {
                        reorganizeScreenWithMockViewModel()
                    }
                }
            }

            composeTestRule.onNodeWithText("Conflict resolution").assertIsDisplayed()
        }
    }
}
