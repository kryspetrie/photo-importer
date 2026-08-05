package org.kryspetrie.fileimport.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.Test
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.AppTheme
import org.kryspetrie.fileimport.ui.TestKoinHelper
import org.kryspetrie.fileimport.ui.i18n.TestStringsProvider
import org.kryspetrie.fileimport.ui.theme.ImporterTheme

@DisplayName("MediaImportScreen Composable Coverage Tests")
@Tag("UiComponentTest")
class MediaImportScreenComposableTest {

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
    private fun mediaImportScreenCall() = MediaImportScreen(settings = settings, onSettingsChange = {})

    @Test
    @DisplayName("should render title and subtitle")
    fun shouldRenderTitleAndSubtitle() {
        composeTestRule.setContent {
            TestStringsProvider { ImporterTheme { mediaImportScreenCall() } }
        }
        composeTestRule.onNodeWithText("Media Import").assertExists()
    }

    @Test
    @DisplayName("should render source and destination fields")
    fun shouldRenderSourceAndDestinationFields() {
        composeTestRule.setContent {
            TestStringsProvider { ImporterTheme { mediaImportScreenCall() } }
        }
        composeTestRule.onNodeWithText("Source").assertExists()
        composeTestRule.onNodeWithText("Destination").assertExists()
    }

    @Test
    @DisplayName("should render import settings section")
    fun shouldRenderImportSettingsSection() {
        composeTestRule.setContent {
            TestStringsProvider { ImporterTheme { mediaImportScreenCall() } }
        }
        composeTestRule.onNodeWithText("Import Settings").assertExists()
    }

    @Test
    @DisplayName("should render organization settings")
    fun shouldRenderOrganizationSettings() {
        composeTestRule.setContent {
            TestStringsProvider { ImporterTheme { mediaImportScreenCall() } }
        }
        composeTestRule.onNodeWithText("Organization").assertExists()
    }

    @Test
    @DisplayName("should render deduplication settings")
    fun shouldRenderDeduplicationSettings() {
        composeTestRule.setContent {
            TestStringsProvider { ImporterTheme { mediaImportScreenCall() } }
        }
        composeTestRule.onNodeWithText("Deduplication").assertExists()
    }

    @Test
    @DisplayName("should render advanced settings")
    fun shouldRenderAdvancedSettings() {
        composeTestRule.setContent {
            TestStringsProvider { ImporterTheme { mediaImportScreenCall() } }
        }
        composeTestRule.onNodeWithText("Advanced").assertExists()
    }

    @Test
    @DisplayName("should render watch folders section")
    fun shouldRenderWatchFoldersSection() {
        composeTestRule.setContent {
            TestStringsProvider { ImporterTheme { mediaImportScreenCall() } }
        }
        composeTestRule.onNodeWithText("Watch Folders").assertExists()
    }

    @Test
    @DisplayName("should render import history section")
    fun shouldRenderImportHistorySection() {
        composeTestRule.setContent {
            TestStringsProvider { ImporterTheme { mediaImportScreenCall() } }
        }
        composeTestRule.onNodeWithText("Import History").assertExists()
    }

    @Test
    @DisplayName("should render action bar")
    fun shouldRenderActionBar() {
        composeTestRule.setContent {
            TestStringsProvider { ImporterTheme { mediaImportScreenCall() } }
        }
        composeTestRule.onNodeWithText("Start Import").assertExists()
    }
}
