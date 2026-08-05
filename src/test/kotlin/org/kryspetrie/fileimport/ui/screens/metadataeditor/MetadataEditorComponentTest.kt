package org.kryspetrie.fileimport.ui.screens.metadataeditor
import org.junit.Ignore
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import java.awt.image.BufferedImage
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Before
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.Test
import org.kryspetrie.fileimport.domain.model.MetadataEditorFileViewMode
import org.kryspetrie.fileimport.ui.i18n.TestStringsProvider

@DisplayName("Metadata editor UI components")
@Tag("UiComponentTest")
@Ignore("Temporarily disabled - requires more setup")
class MetadataEditorComponentTest {

    @get:Rule val composeTestRule = createComposeRule()

    private lateinit var state: BulkEditState
    private val thumbnailCache = java.util.concurrent.ConcurrentHashMap<String, BufferedImage>()

    @Before
    fun setUp() {
        state = BulkEditState()
        state.loadFiles(listOf(File("/tmp/folder/alpha.jpg"), File("/tmp/folder/beta.CR2")))
        thumbnailCache.clear()
    }

    @Nested
    @DisplayName("MetadataEditorFileBrowserPanel")
    inner class FileBrowserPanelTests {
        @Test
        fun listViewDisplaysSelectionActionsAndFileNames() {
            composeTestRule.setContent {
                MaterialTheme {
                    TestStringsProvider {
                        MetadataEditorFileBrowserPanel(
                            state = state,
                            viewMode = MetadataEditorFileViewMode.LIST,
                            onViewModeChange = {},
                            thumbnailCache = thumbnailCache,
                            thumbnailCacheRevision = 0,
                            onEnsureThumbnail = {},
                            diskThumbnailCacheEnabled = true,
                            onDiskThumbnailCacheChange = {},
                            onClearThumbnailCache = {},
                            isMultiEditMode = false,
                            selectedIndices = emptySet(),
                            onSelectFiles = {},
                            onSelectFolder = {},
                            onSelectIndex = {},
                            onToggleMultiEdit = {},
                            onDeselectAll = {},
                            onOpenFolder = {},
                            folderPathStack = emptyList(),
                            focusedFolderPath = null,
                            onNavigateUp = {},
                            onEnterFolderPath = {},
                            onBrowserKey = { false },
                            browserPaneWidthDp = 280,
                        )
                    }
                }
            }

            composeTestRule.onNodeWithText("Select Images…").assertIsDisplayed()
            composeTestRule.onNodeWithText("Select Folder…").assertIsDisplayed()
            composeTestRule.onNodeWithText("2 images").assertIsDisplayed()
            composeTestRule.onNodeWithText("alpha.jpg").assertIsDisplayed()
            composeTestRule.onNodeWithText("beta.CR2").assertIsDisplayed()
        }

        @Test
        fun clickingFileNameInvokesSelectionCallback() {
            var selectedIndex: Int? = null

            composeTestRule.setContent {
                MaterialTheme {
                    TestStringsProvider {
                        MetadataEditorFileBrowserPanel(
                            state = state,
                            viewMode = MetadataEditorFileViewMode.LIST,
                            onViewModeChange = {},
                            thumbnailCache = thumbnailCache,
                            thumbnailCacheRevision = 0,
                            onEnsureThumbnail = {},
                            diskThumbnailCacheEnabled = true,
                            onDiskThumbnailCacheChange = {},
                            onClearThumbnailCache = {},
                            isMultiEditMode = false,
                            selectedIndices = emptySet(),
                            onSelectFiles = {},
                            onSelectFolder = {},
                            onSelectIndex = { selectedIndex = it },
                            onToggleMultiEdit = {},
                            onDeselectAll = {},
                            onOpenFolder = {},
                            folderPathStack = emptyList(),
                            focusedFolderPath = null,
                            onNavigateUp = {},
                            onEnterFolderPath = {},
                            onBrowserKey = { false },
                            browserPaneWidthDp = 280,
                        )
                    }
                }
            }

            composeTestRule.onNodeWithText("beta.CR2").performClick()
            assertThat(selectedIndex).isEqualTo(1)
        }

        @Test
        fun multiModeShowsCheckboxesAndClearButton() {
            composeTestRule.setContent {
                MaterialTheme {
                    TestStringsProvider {
                        MetadataEditorFileBrowserPanel(
                            state = state,
                            viewMode = MetadataEditorFileViewMode.LIST,
                            onViewModeChange = {},
                            thumbnailCache = thumbnailCache,
                            thumbnailCacheRevision = 0,
                            onEnsureThumbnail = {},
                            diskThumbnailCacheEnabled = true,
                            onDiskThumbnailCacheChange = {},
                            onClearThumbnailCache = {},
                            isMultiEditMode = true,
                            selectedIndices = setOf(0),
                            onSelectFiles = {},
                            onSelectFolder = {},
                            onSelectIndex = {},
                            onToggleMultiEdit = {},
                            onDeselectAll = {},
                            onOpenFolder = {},
                            folderPathStack = emptyList(),
                            focusedFolderPath = null,
                            onNavigateUp = {},
                            onEnterFolderPath = {},
                            onBrowserKey = { false },
                            browserPaneWidthDp = 280,
                        )
                    }
                }
            }

            composeTestRule.onNodeWithText("Done").assertIsDisplayed()
            composeTestRule.onNodeWithText("Clear").assertIsDisplayed()
        }

        @Test
        fun modifiedFileShowsModifiedBadge() {
            state.updateConfig(0) { it.copy(description = "changed") }

            composeTestRule.setContent {
                MaterialTheme {
                    TestStringsProvider {
                        MetadataEditorFileBrowserPanel(
                            state = state,
                            viewMode = MetadataEditorFileViewMode.LIST,
                            onViewModeChange = {},
                            thumbnailCache = thumbnailCache,
                            thumbnailCacheRevision = 0,
                            onEnsureThumbnail = {},
                            diskThumbnailCacheEnabled = true,
                            onDiskThumbnailCacheChange = {},
                            onClearThumbnailCache = {},
                            isMultiEditMode = false,
                            selectedIndices = emptySet(),
                            onSelectFiles = {},
                            onSelectFolder = {},
                            onSelectIndex = {},
                            onToggleMultiEdit = {},
                            onDeselectAll = {},
                            onOpenFolder = {},
                            folderPathStack = emptyList(),
                            focusedFolderPath = null,
                            onNavigateUp = {},
                            onEnterFolderPath = {},
                            onBrowserKey = { false },
                            browserPaneWidthDp = 280,
                        )
                    }
                }
            }

            composeTestRule.onNodeWithText("Modified").assertIsDisplayed()
        }

        @Test
        fun selectImagesButtonInvokesCallback() {
            var selectFilesCalled = false

            composeTestRule.setContent {
                MaterialTheme {
                    TestStringsProvider {
                        MetadataEditorFileBrowserPanel(
                            state = state,
                            viewMode = MetadataEditorFileViewMode.LIST,
                            onViewModeChange = {},
                            thumbnailCache = thumbnailCache,
                            thumbnailCacheRevision = 0,
                            onEnsureThumbnail = {},
                            diskThumbnailCacheEnabled = true,
                            onDiskThumbnailCacheChange = {},
                            onClearThumbnailCache = {},
                            isMultiEditMode = false,
                            selectedIndices = emptySet(),
                            onSelectFiles = { selectFilesCalled = true },
                            onSelectFolder = {},
                            onSelectIndex = {},
                            onToggleMultiEdit = {},
                            onDeselectAll = {},
                            onOpenFolder = {},
                            folderPathStack = emptyList(),
                            focusedFolderPath = null,
                            onNavigateUp = {},
                            onEnterFolderPath = {},
                            onBrowserKey = { false },
                            browserPaneWidthDp = 280,
                        )
                    }
                }
            }

            composeTestRule.onNodeWithText("Select Images…").performClick()
            assertThat(selectFilesCalled).isTrue()
        }

        @Test
        fun iconsViewShowsOpenFolderIconInsteadOfSelectButtons() {
            composeTestRule.setContent {
                MaterialTheme {
                    TestStringsProvider {
                        MetadataEditorFileBrowserPanel(
                            state = state,
                            viewMode = MetadataEditorFileViewMode.ICONS,
                            onViewModeChange = {},
                            thumbnailCache = thumbnailCache,
                            thumbnailCacheRevision = 0,
                            onEnsureThumbnail = {},
                            diskThumbnailCacheEnabled = true,
                            onDiskThumbnailCacheChange = {},
                            onClearThumbnailCache = {},
                            isMultiEditMode = false,
                            selectedIndices = emptySet(),
                            onSelectFiles = {},
                            onSelectFolder = {},
                            onSelectIndex = {},
                            onToggleMultiEdit = {},
                            onDeselectAll = {},
                            onOpenFolder = {},
                            folderPathStack = emptyList(),
                            focusedFolderPath = null,
                            onNavigateUp = {},
                            onEnterFolderPath = {},
                            onBrowserKey = { false },
                            browserPaneWidthDp = 280,
                        )
                    }
                }
            }

            composeTestRule.onNodeWithContentDescription("Open folder").assertIsDisplayed()
            composeTestRule.onNodeWithText("Select Images…").assertIsNotDisplayed()
        }

        @Test
        fun openFolderInvokesCallbackInIconsView() {
            var openFolderCalled = false

            composeTestRule.setContent {
                MaterialTheme {
                    TestStringsProvider {
                        MetadataEditorFileBrowserPanel(
                            state = state,
                            viewMode = MetadataEditorFileViewMode.ICONS,
                            onViewModeChange = {},
                            thumbnailCache = thumbnailCache,
                            thumbnailCacheRevision = 0,
                            onEnsureThumbnail = {},
                            diskThumbnailCacheEnabled = true,
                            onDiskThumbnailCacheChange = {},
                            onClearThumbnailCache = {},
                            isMultiEditMode = false,
                            selectedIndices = emptySet(),
                            onSelectFiles = {},
                            onSelectFolder = {},
                            onSelectIndex = {},
                            onToggleMultiEdit = {},
                            onDeselectAll = {},
                            onOpenFolder = { openFolderCalled = true },
                            folderPathStack = emptyList(),
                            focusedFolderPath = null,
                            onNavigateUp = {},
                            onEnterFolderPath = {},
                            onBrowserKey = { false },
                            browserPaneWidthDp = 280,
                        )
                    }
                }
            }

            composeTestRule.onNodeWithContentDescription("Open folder").performClick()
            assertThat(openFolderCalled).isTrue()
        }

        @Test
        fun viewModeButtonsInvokeCallback() {
            var selectedMode: MetadataEditorFileViewMode? = null

            composeTestRule.setContent {
                MaterialTheme {
                    TestStringsProvider {
                        MetadataEditorFileBrowserPanel(
                            state = state,
                            viewMode = MetadataEditorFileViewMode.LIST,
                            onViewModeChange = { selectedMode = it },
                            thumbnailCache = thumbnailCache,
                            thumbnailCacheRevision = 0,
                            onEnsureThumbnail = {},
                            diskThumbnailCacheEnabled = true,
                            onDiskThumbnailCacheChange = {},
                            onClearThumbnailCache = {},
                            isMultiEditMode = false,
                            selectedIndices = emptySet(),
                            onSelectFiles = {},
                            onSelectFolder = {},
                            onSelectIndex = {},
                            onToggleMultiEdit = {},
                            onDeselectAll = {},
                            onOpenFolder = {},
                            folderPathStack = emptyList(),
                            focusedFolderPath = null,
                            onNavigateUp = {},
                            onEnterFolderPath = {},
                            onBrowserKey = { false },
                            browserPaneWidthDp = 280,
                        )
                    }
                }
            }

            composeTestRule.onNodeWithContentDescription("Hierarchy").performClick()
            assertThat(selectedMode).isEqualTo(MetadataEditorFileViewMode.HIERARCHY)
        }

        @Test
        fun listViewShowsSubfoldersAtRootAndNavigatesOnDoubleClick() {
            // GIVEN
            state.sourcePath = "/tmp/album"
            state.loadFiles(
                listOf(File("/tmp/album/alpha.jpg"), File("/tmp/album/nested/beta.CR2"))
            )
            val folderStack = mutableStateOf(emptyList<String>())

            composeTestRule.setContent {
                MaterialTheme {
                    TestStringsProvider {
                        MetadataEditorFileBrowserPanel(
                            state = state,
                            viewMode = MetadataEditorFileViewMode.LIST,
                            onViewModeChange = {},
                            thumbnailCache = thumbnailCache,
                            thumbnailCacheRevision = 0,
                            onEnsureThumbnail = {},
                            diskThumbnailCacheEnabled = true,
                            onDiskThumbnailCacheChange = {},
                            onClearThumbnailCache = {},
                            isMultiEditMode = false,
                            selectedIndices = emptySet(),
                            onSelectFiles = {},
                            onSelectFolder = {},
                            onSelectIndex = {},
                            onToggleMultiEdit = {},
                            onDeselectAll = {},
                            onOpenFolder = {},
                            folderPathStack = folderStack.value,
                            focusedFolderPath = null,
                            onNavigateUp = { folderStack.value = folderStack.value.dropLast(1) },
                            onEnterFolderPath = { path ->
                                folderStack.value = folderStack.value + path
                            },
                            onBrowserKey = { false },
                            browserPaneWidthDp = 280,
                        )
                    }
                }
            }

            // THEN — root shows folder and top-level file only
            composeTestRule.onNodeWithText("alpha.jpg").assertIsDisplayed()
            composeTestRule.onNodeWithText("nested").assertIsDisplayed()
            composeTestRule.onNodeWithText("beta.CR2").assertIsNotDisplayed()

            // WHEN — double-click the subfolder
            composeTestRule.onNodeWithText("nested").performTouchInput { doubleClick() }

            // THEN — nested file is visible and Up bar appears
            composeTestRule.onNodeWithText("beta.CR2").assertIsDisplayed()
            composeTestRule.onNodeWithText("alpha.jpg").assertIsNotDisplayed()
            composeTestRule.onNodeWithContentDescription("Up").assertIsDisplayed()
        }

        @Test
        fun upButtonReturnsToParentFolderInListView() {
            // GIVEN
            state.sourcePath = "/tmp/album"
            state.loadFiles(
                listOf(File("/tmp/album/alpha.jpg"), File("/tmp/album/nested/beta.CR2"))
            )
            val folderStack = mutableStateOf(emptyList<String>())

            composeTestRule.setContent {
                MaterialTheme {
                    TestStringsProvider {
                        MetadataEditorFileBrowserPanel(
                            state = state,
                            viewMode = MetadataEditorFileViewMode.LIST,
                            onViewModeChange = {},
                            thumbnailCache = thumbnailCache,
                            thumbnailCacheRevision = 0,
                            onEnsureThumbnail = {},
                            diskThumbnailCacheEnabled = true,
                            onDiskThumbnailCacheChange = {},
                            onClearThumbnailCache = {},
                            isMultiEditMode = false,
                            selectedIndices = emptySet(),
                            onSelectFiles = {},
                            onSelectFolder = {},
                            onSelectIndex = {},
                            onToggleMultiEdit = {},
                            onDeselectAll = {},
                            onOpenFolder = {},
                            folderPathStack = folderStack.value,
                            focusedFolderPath = null,
                            onNavigateUp = { folderStack.value = folderStack.value.dropLast(1) },
                            onEnterFolderPath = { path ->
                                folderStack.value = folderStack.value + path
                            },
                            onBrowserKey = { false },
                            browserPaneWidthDp = 280,
                        )
                    }
                }
            }
            composeTestRule.onNodeWithText("nested").performTouchInput { doubleClick() }

            // WHEN
            composeTestRule.onNodeWithContentDescription("Up").performClick()

            // THEN
            composeTestRule.onNodeWithText("alpha.jpg").assertIsDisplayed()
            composeTestRule.onNodeWithContentDescription("Up").assertIsNotDisplayed()
        }

        @Test
        fun browserPanelForwardsArrowKeysToHandlerWhenFocused() {
            // GIVEN
            var handledKey: Key? = null

            composeTestRule.setContent {
                MaterialTheme {
                    TestStringsProvider {
                        MetadataEditorFileBrowserPanel(
                            state = state,
                            viewMode = MetadataEditorFileViewMode.LIST,
                            onViewModeChange = {},
                            thumbnailCache = thumbnailCache,
                            thumbnailCacheRevision = 0,
                            onEnsureThumbnail = {},
                            diskThumbnailCacheEnabled = true,
                            onDiskThumbnailCacheChange = {},
                            onClearThumbnailCache = {},
                            isMultiEditMode = false,
                            selectedIndices = emptySet(),
                            onSelectFiles = {},
                            onSelectFolder = {},
                            onSelectIndex = {},
                            onToggleMultiEdit = {},
                            onDeselectAll = {},
                            onOpenFolder = {},
                            folderPathStack = emptyList(),
                            focusedFolderPath = null,
                            onNavigateUp = {},
                            onEnterFolderPath = {},
                            onBrowserKey = { key ->
                                handledKey = key
                                true
                            },
                            browserPaneWidthDp = 280,
                        )
                    }
                }
            }

            // WHEN
            composeTestRule.waitForIdle()
            composeTestRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }

            // THEN
            assertThat(handledKey).isEqualTo(Key.DirectionDown)
        }

        @Test
        fun hierarchyViewShowsNestedFolderNames() {
            // GIVEN
            state.sourcePath = "/tmp/album"
            state.loadFiles(
                listOf(File("/tmp/album/alpha.jpg"), File("/tmp/album/nested/beta.CR2"))
            )

            // WHEN
            composeTestRule.setContent {
                MaterialTheme {
                    TestStringsProvider {
                        MetadataEditorFileBrowserPanel(
                            state = state,
                            viewMode = MetadataEditorFileViewMode.HIERARCHY,
                            onViewModeChange = {},
                            thumbnailCache = thumbnailCache,
                            thumbnailCacheRevision = 0,
                            onEnsureThumbnail = {},
                            diskThumbnailCacheEnabled = true,
                            onDiskThumbnailCacheChange = {},
                            onClearThumbnailCache = {},
                            isMultiEditMode = false,
                            selectedIndices = emptySet(),
                            onSelectFiles = {},
                            onSelectFolder = {},
                            onSelectIndex = {},
                            onToggleMultiEdit = {},
                            onDeselectAll = {},
                            onOpenFolder = {},
                            folderPathStack = emptyList(),
                            focusedFolderPath = null,
                            onNavigateUp = {},
                            onEnterFolderPath = {},
                            onBrowserKey = { false },
                            browserPaneWidthDp = 280,
                        )
                    }
                }
            }

            // THEN
            composeTestRule.onNodeWithText("nested").assertIsDisplayed()
            composeTestRule.onNodeWithText("alpha.jpg").assertIsDisplayed()
            composeTestRule.onNodeWithText("beta.CR2").assertIsDisplayed()
        }
    }
}
