package org.kryspetrie.fileimport.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.After
import org.junit.Before
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.Test
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.AppTheme
import org.kryspetrie.fileimport.domain.model.DuplicateAction
import org.kryspetrie.fileimport.domain.model.DuplicateInfo
import org.kryspetrie.fileimport.domain.model.DuplicateType
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.ImageFile
import org.kryspetrie.fileimport.domain.model.ScanProgress
import org.kryspetrie.fileimport.ui.TestKoinHelper
import org.kryspetrie.fileimport.ui.i18n.TestStringsProvider
import org.kryspetrie.fileimport.ui.theme.ImporterTheme
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@DisplayName("DuplicateScannerScreen State Tests")
@Tag("UiComponentTest")
class DuplicateScannerScreenStateTest {

    @get:Rule val composeTestRule = createComposeRule()

    private lateinit var settings: AppSettings
    private lateinit var mockViewModel: DuplicateScannerViewModel

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
    private fun duplicateScannerScreenWithMockViewModel() =
        DuplicateScannerScreen(
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
                on { step } doReturn DuplicateScannerViewModel.ScanStep.SETUP
                on { scanProgress } doReturn ScanProgress()
                on { duplicates } doReturn emptyList()
                on { errorMessage } doReturn null
                on { resolveAction } doReturn DuplicateAction.KEEP_HIGHEST_RES
                on { moveToTrash } doReturn true
                on { enableHash } doReturn true
                on { enableExif } doReturn true
                on { enableSurf } doReturn false
            }
        }

        @Test
        @DisplayName("should display folder selection in SETUP state")
        fun shouldDisplayFolderSelectionInSetupState() {
            composeTestRule.setContent {
                TestStringsProvider {
                    ImporterTheme {
                        duplicateScannerScreenWithMockViewModel()
                    }
                }
            }

            composeTestRule.onNodeWithText("Folder").assertIsDisplayed()
        }

        @Test
        @DisplayName("should display detection settings in SETUP state")
        fun shouldDisplayDetectionSettingsInSetupState() {
            composeTestRule.setContent {
                TestStringsProvider {
                    ImporterTheme {
                        duplicateScannerScreenWithMockViewModel()
                    }
                }
            }

            composeTestRule.onNodeWithText("Hash").assertIsDisplayed()
            composeTestRule.onNodeWithText("EXIF").assertIsDisplayed()
        }

        @Test
        @DisplayName("should display start scan button in SETUP state")
        fun shouldDisplayStartScanButtonInSetupState() {
            composeTestRule.setContent {
                TestStringsProvider {
                    ImporterTheme {
                        duplicateScannerScreenWithMockViewModel()
                    }
                }
            }

            composeTestRule.onNodeWithText("Scan").assertIsDisplayed()
        }
    }

    @Nested
    @DisplayName("SCANNING State Tests")
    inner class ScanningState {

        @Before
        fun setupMock() {
            val progress = ScanProgress(current = 50, total = 100)
            mockViewModel = mock {
                on { folderPath } doReturn "/photos"
                on { step } doReturn DuplicateScannerViewModel.ScanStep.SCANNING
                on { scanProgress } doReturn progress
                on { duplicates } doReturn emptyList()
                on { errorMessage } doReturn null
                on { enableHash } doReturn true
                on { enableExif } doReturn true
                on { enableSurf } doReturn false
            }
        }

        @Test
        @DisplayName("should display scanning progress in SCANNING state")
        fun shouldDisplayScanningProgressInScanningState() {
            composeTestRule.setContent {
                TestStringsProvider {
                    ImporterTheme {
                        duplicateScannerScreenWithMockViewModel()
                    }
                }
            }

            composeTestRule.onNodeWithText("Scanning").assertIsDisplayed()
        }

        @Test
        @DisplayName("should display cancel button in SCANNING state")
        fun shouldDisplayCancelButtonInScanningState() {
            composeTestRule.setContent {
                TestStringsProvider {
                    ImporterTheme {
                        duplicateScannerScreenWithMockViewModel()
                    }
                }
            }

            composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
        }
    }

    @Nested
    @DisplayName("RESULTS State Tests")
    inner class ResultsState {

        private lateinit var testDuplicates: List<DuplicateInfo>

        @Before
        fun setupMock() {
            val image1 = ImageFile(
                id = "img1",
                path = FilePath("/photos/img1.jpg"),
                fileSize = 1000L,
            )
            val image2 = ImageFile(
                id = "img2",
                path = FilePath("/photos/img2.jpg"),
                fileSize = 1000L,
            )
            testDuplicates = listOf(
                DuplicateInfo(
                    primaryImage = image1,
                    duplicateImages = listOf(image2),
                    duplicateType = DuplicateType.EXACT_HASH,
                    hashMatch = true,
                )
            )

            mockViewModel = mock {
                on { folderPath } doReturn "/photos"
                on { step } doReturn DuplicateScannerViewModel.ScanStep.RESULTS
                on { scanProgress } doReturn ScanProgress(current = 100, total = 100)
                on { duplicates } doReturn testDuplicates
                on { errorMessage } doReturn null
                on { resolveAction } doReturn DuplicateAction.KEEP_HIGHEST_RES
                on { moveToTrash } doReturn true
                on { totalDupeFiles } doReturn 1
                on { totalWastedBytes } doReturn 1000L
            }
        }

        @Test
        @DisplayName("should display duplicate groups in RESULTS state")
        fun shouldDisplayDuplicateGroupsInResultsState() {
            composeTestRule.setContent {
                TestStringsProvider {
                    ImporterTheme {
                        duplicateScannerScreenWithMockViewModel()
                    }
                }
            }

            composeTestRule.onNodeWithText("Duplicates").assertIsDisplayed()
        }

        @Test
        @DisplayName("should display resolve options in RESULTS state")
        fun shouldDisplayResolveOptionsInResultsState() {
            composeTestRule.setContent {
                TestStringsProvider {
                    ImporterTheme {
                        duplicateScannerScreenWithMockViewModel()
                    }
                }
            }

            composeTestRule.onNodeWithText("Resolve").assertIsDisplayed()
        }

        @Test
        @DisplayName("should display statistics in RESULTS state")
        fun shouldDisplayStatisticsInResultsState() {
            composeTestRule.setContent {
                TestStringsProvider {
                    ImporterTheme {
                        duplicateScannerScreenWithMockViewModel()
                    }
                }
            }

            composeTestRule.onNodeWithText("1 duplicate").assertIsDisplayed()
        }
    }

    @Nested
    @DisplayName("RESOLVING State Tests")
    inner class ResolvingState {

        @Before
        fun setupMock() {
            mockViewModel = mock {
                on { folderPath } doReturn "/photos"
                on { step } doReturn DuplicateScannerViewModel.ScanStep.RESOLVING
                on { duplicates } doReturn emptyList()
                on { errorMessage } doReturn null
                on { resolveProgress } doReturn Pair(5, 10)
                on { moveToTrash } doReturn true
            }
        }

        @Test
        @DisplayName("should display resolving progress in RESOLVING state")
        fun shouldDisplayResolvingProgressInResolvingState() {
            composeTestRule.setContent {
                TestStringsProvider {
                    ImporterTheme {
                        duplicateScannerScreenWithMockViewModel()
                    }
                }
            }

            composeTestRule.onNodeWithText("Resolving").assertIsDisplayed()
        }

        @Test
        @DisplayName("should display progress indicator in RESOLVING state")
        fun shouldDisplayProgressIndicatorInResolvingState() {
            composeTestRule.setContent {
                TestStringsProvider {
                    ImporterTheme {
                        duplicateScannerScreenWithMockViewModel()
                    }
                }
            }

            // Progress text should show current/total
            composeTestRule.onNodeWithText("5 of 10").assertIsDisplayed()
        }
    }

    @Nested
    @DisplayName("Error State Tests")
    inner class ErrorState {

        @Before
        fun setupMock() {
            mockViewModel = mock {
                on { folderPath } doReturn "/photos"
                on { step } doReturn DuplicateScannerViewModel.ScanStep.SETUP
                on { errorMessage } doReturn "Access denied to folder"
                on { enableHash } doReturn true
                on { enableExif } doReturn true
                on { enableSurf } doReturn false
            }
        }

        @Test
        @DisplayName("should display error message when present")
        fun shouldDisplayErrorMessageWhenPresent() {
            composeTestRule.setContent {
                TestStringsProvider {
                    ImporterTheme {
                        duplicateScannerScreenWithMockViewModel()
                    }
                }
            }

            composeTestRule.onNodeWithText("Access denied").assertIsDisplayed()
        }
    }
}
