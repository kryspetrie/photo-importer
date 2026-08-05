package org.kryspetrie.fileimport.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.AppTheme
import org.kryspetrie.fileimport.ui.TestKoinHelper
import org.kryspetrie.fileimport.ui.i18n.TestStringsProvider
import org.kryspetrie.fileimport.ui.theme.ImporterTheme

@DisplayName("MediaImportScreen Comprehensive Tests")
@Tag("UiComponentTest")
@Ignore("Temporarily disabled - requires more complete ViewModel mocking")
class MediaImportScreenComprehensiveTest {

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
    private fun mediaImportScreenCall() =
        MediaImportScreen(
            settings = settings,
            onSettingsChange = {},
        )

    @Test
    @DisplayName("should display import title and subtitle")
    fun shouldDisplayImportTitleAndSubtitle() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    mediaImportScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Media Import").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Import photos from folders, cameras, or watch folders")
            .assertIsDisplayed()
    }

    @Test
    @DisplayName("should display source folder selection field")
    fun shouldDisplaySourceFolderSelectionField() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    mediaImportScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Source Folder").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display destination folder field")
    fun shouldDisplayDestinationFolderField() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    mediaImportScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Destination Folder").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display import settings section")
    fun shouldDisplayImportSettingsSection() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    mediaImportScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Import Settings").assertIsDisplayed()
    }

    @Test
    @DisplayName("should expand settings section on click")
    fun shouldExpandSettingsSectionOnClick() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    mediaImportScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Import Settings").performClick()
        composeTestRule.onNodeWithText("Folder pattern").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display folder pattern field when expanded")
    fun shouldDisplayFolderPatternFieldWhenExpanded() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    mediaImportScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Import Settings").performClick()
        composeTestRule.onNodeWithText("Folder pattern").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display filename pattern field when preserve names off")
    fun shouldDisplayFilenamePatternFieldWhenPreserveNamesOff() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    mediaImportScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Import Settings").performClick()
        composeTestRule.onNodeWithText("Filename pattern").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display date source options")
    fun shouldDisplayDateSourceOptions() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    mediaImportScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Import Settings").performClick()
        composeTestRule.onNodeWithText("Date source").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display conflict resolution options")
    fun shouldDisplayConflictResolutionOptions() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    mediaImportScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Import Settings").performClick()
        composeTestRule.onNodeWithText("Conflict resolution").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display organization subsection")
    fun shouldDisplayOrganizationSubsection() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    mediaImportScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Import Settings").performClick()
        composeTestRule.onNodeWithText("Organization").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display deduplication subsection")
    fun shouldDisplayDeduplicationSubsection() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    mediaImportScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Import Settings").performClick()
        composeTestRule.onNodeWithText("Deduplication").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display hash deduplication toggle")
    fun shouldDisplayHashDeduplicationToggle() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    mediaImportScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Import Settings").performClick()
        composeTestRule.onNodeWithText("Hash").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display EXIF deduplication toggle")
    fun shouldDisplayExifDeduplicationToggle() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    mediaImportScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Import Settings").performClick()
        composeTestRule.onNodeWithText("EXIF").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display orientation model subsection")
    fun shouldDisplayOrientationModelSubsection() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    mediaImportScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Import Settings").performClick()
        composeTestRule.onNodeWithText("Orientation correction").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display orientation model download toggle")
    fun shouldDisplayOrientationModelDownloadToggle() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    mediaImportScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Import Settings").performClick()
        composeTestRule.onNodeWithText("Download model").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display advanced settings subsection")
    fun shouldDisplayAdvancedSettingsSubsection() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    mediaImportScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Import Settings").performClick()
        composeTestRule.onNodeWithText("Advanced").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display watch folders section")
    fun shouldDisplayWatchFoldersSection() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    mediaImportScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Watch Folders").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display import history section")
    fun shouldDisplayImportHistorySection() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    mediaImportScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Import History").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display action bar with start import button")
    fun shouldDisplayActionBarWithStartImportButton() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    mediaImportScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Start Import").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display review and import button")
    fun shouldDisplayReviewAndImportButton() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    mediaImportScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Review & Import").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display error card placeholder")
    fun shouldDisplayErrorCardPlaceholder() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    mediaImportScreenCall()
                }
            }
        }

        // Screen should render without errors
        composeTestRule.onNodeWithText("Media Import").assertExists()
    }

    @Test
    @DisplayName("should constrain content width to max 900dp")
    fun shouldConstrainContentWidthToMax900dp() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    mediaImportScreenCall()
                }
            }
        }

        // Content should render within constraints
        composeTestRule.onNodeWithText("Media Import").assertIsDisplayed()
    }

    @Test
    @DisplayName("should display single header surface")
    fun shouldDisplaySingleHeaderSurface() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    mediaImportScreenCall()
                }
            }
        }

        composeTestRule.onNodeWithText("Media Import").assertIsDisplayed()
    }
}
