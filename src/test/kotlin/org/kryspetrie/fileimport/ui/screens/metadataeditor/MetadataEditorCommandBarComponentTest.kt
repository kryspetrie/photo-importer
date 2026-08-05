package org.kryspetrie.fileimport.ui.screens.metadataeditor

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import java.io.File
import org.junit.Rule
import org.junit.Before
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.Test
import org.kryspetrie.fileimport.application.TestDispatcherProvider
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.UiDensity
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.TestStringsProvider
import org.kryspetrie.fileimport.ui.theme.DensityThemeProvider
import org.kryspetrie.fileimport.ui.theme.ImporterTheme
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@DisplayName("MetadataEditorCommandBar")
@Tag("UiComponentTest")
class MetadataEditorCommandBarComponentTest {

    @get:Rule val composeTestRule = createComposeRule()

    private lateinit var state: BulkEditState
    private lateinit var vm: MetadataEditorViewModel

    @Before
    fun setUp() {
        state = BulkEditState()
        state.loadFiles(listOf(File("/tmp/photo.jpg")))
        val localePort = mock<org.kryspetrie.fileimport.domain.port.LocalePort>()
        whenever(localePort.t(any<StringKey>())).thenAnswer { inv ->
            inv.getArgument<StringKey>(0).name
        }
        whenever(localePort.t(any<StringKey>(), any())).thenAnswer { inv ->
            inv.getArgument<StringKey>(0).name
        }
        vm =
            MetadataEditorViewModel(
                dispatcherProvider = TestDispatcherProvider(),
                imageRepository = mock(),
                imageProcessing = mock(),
                locationSearchService = mock(),
                geocodingPort = mock(),
                settingsPort = mock(),
                editService = mock(),
                undoService = mock(),
                faceRegionTransformer = mock(),
                fileSystemAdapter = mock(),
                orientationCorrection = mock(),
                modelDownloadPort = mock(),
                faceDetectionPort = mock(),
                folderThumbnailCache = mock(),
                localePort = localePort,
            )
        vm.currentSettings = AppSettings()
    }

    @Test
    fun showsSaveAndBackActions() {
        // GIVEN / WHEN
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    DensityThemeProvider(UiDensity.COMFORTABLE) {
                        MaterialTheme {
                            MetadataEditorCommandBar(
                                state = state,
                                vm = vm,
                                autoOrientEnabled = true,
                                onBack = {},
                                onPrev = {},
                                onNext = {},
                                onSave = {},
                                onSaveAll = {},
                                onUndo = {},
                                onRedo = {},
                                onToggleBrowserDrawer = {},
                                onAutoRotate = {},
                                onBulkSelect = {},
                            )
                        }
                    }
                }
            }
        }

        // THEN
        composeTestRule.onNodeWithText("Back").assertIsDisplayed()
        composeTestRule.onNodeWithText("Save").assertIsDisplayed()
    }

    @Test
    fun hidesBackOfPhotoControls() {
        // GIVEN / WHEN
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    DensityThemeProvider(UiDensity.COMFORTABLE) {
                        MaterialTheme {
                            MetadataEditorCommandBar(
                                state = state,
                                vm = vm,
                                autoOrientEnabled = true,
                                onBack = {},
                                onPrev = {},
                                onNext = {},
                                onSave = {},
                                onSaveAll = {},
                                onUndo = {},
                                onRedo = {},
                                onToggleBrowserDrawer = {},
                                onAutoRotate = {},
                                onBulkSelect = {},
                            )
                        }
                    }
                }
            }
        }

        // THEN — back-of-photo picker is out of scope; controls must not appear
        composeTestRule.onNodeWithText(StringKey.META_ADD_BACK.name).assertDoesNotExist()
        composeTestRule
            .onNodeWithText(StringKey.META_SELECT_BACK_OF_PHOTO.name)
            .assertDoesNotExist()
    }
}
