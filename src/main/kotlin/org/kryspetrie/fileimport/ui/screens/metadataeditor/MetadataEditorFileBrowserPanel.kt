package org.kryspetrie.fileimport.ui.screens.metadataeditor

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage
import java.io.File
import org.kryspetrie.fileimport.domain.model.MetadataEditorFileViewMode
import org.kryspetrie.fileimport.ui.i18n.strings
import org.kryspetrie.fileimport.ui.theme.LocalUiDensityScale

/** Default minimum width for adaptive thumbnail grids in the icons view. */
internal val METADATA_BROWSER_MIN_GRID_CELL_DP = 96

/** Unified file browser for the metadata editor with Finder-style view modes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataEditorFileBrowserPanel(
    state: BulkEditState,
    viewMode: MetadataEditorFileViewMode,
    onViewModeChange: (MetadataEditorFileViewMode) -> Unit,
    thumbnailCache: java.util.concurrent.ConcurrentHashMap<String, BufferedImage>,
    thumbnailCacheRevision: Int,
    onEnsureThumbnail: suspend (File) -> Unit,
    diskThumbnailCacheEnabled: Boolean,
    onDiskThumbnailCacheChange: (Boolean) -> Unit,
    onClearThumbnailCache: () -> Unit,
    isMultiEditMode: Boolean,
    selectedIndices: Set<Int>,
    folderPathStack: List<String>,
    focusedFolderPath: String?,
    onSelectFiles: () -> Unit,
    onSelectFolder: () -> Unit,
    onSelectIndex: (Int) -> Unit,
    onToggleMultiEdit: () -> Unit,
    onDeselectAll: () -> Unit,
    onOpenFolder: () -> Unit,
    onNavigateUp: () -> Unit,
    onEnterFolderPath: (String) -> Unit,
    onBrowserKey: (Key) -> Boolean,
    browserPaneWidthDp: Int,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    val densityScale = LocalUiDensityScale.current
    val fileTree =
        remember(state.files, state.sourcePath) {
            buildMetadataFileTree(state.files, state.sourcePath)
        }
    val currentFolder =
        remember(fileTree, folderPathStack) {
            resolveMetadataBrowserFolder(fileTree, folderPathStack)
        }
    val supportsFolderNavigation =
        viewMode == MetadataEditorFileViewMode.LIST || viewMode == MetadataEditorFileViewMode.ICONS
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(state.files, state.editingActive) {
        if (state.editingActive && state.files.isNotEmpty()) {
            focusRequester.requestFocus()
        }
    }

    Surface(
        tonalElevation = 2.dp,
        modifier =
            modifier
                .width(browserPaneWidthDp.dp)
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { keyEvent ->
                    when (keyEvent.type) {
                        KeyEventType.KeyDown -> onBrowserKey(keyEvent.key)
                        else -> false
                    }
                },
        shape = RoundedCornerShape(0.dp),
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            MetadataEditorBrowserToolbar(
                viewMode = viewMode,
                onViewModeChange = onViewModeChange,
                onSelectFiles = onSelectFiles,
                onSelectFolder = onSelectFolder,
                onOpenFolder = onOpenFolder,
                showOpenFolderIcon = viewMode == MetadataEditorFileViewMode.ICONS,
            )

            MetadataEditorBrowserActionsRow(
                state = state,
                isMultiEditMode = isMultiEditMode,
                selectedIndices = selectedIndices,
                onToggleMultiEdit = onToggleMultiEdit,
                onDeselectAll = onDeselectAll,
                diskThumbnailCacheEnabled = diskThumbnailCacheEnabled,
                onDiskThumbnailCacheChange = onDiskThumbnailCacheChange,
                onClearThumbnailCache = onClearThumbnailCache,
                canClearThumbnailCache = state.sourcePath.isNotBlank(),
            )

            if (supportsFolderNavigation && folderPathStack.isNotEmpty()) {
                MetadataEditorFolderNavigationBar(
                    currentFolder = currentFolder,
                    sourcePath = state.sourcePath,
                    onNavigateUp = onNavigateUp,
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (viewMode) {
                    MetadataEditorFileViewMode.LIST ->
                        MetadataEditorListView(
                            currentFolder = currentFolder,
                            state = state,
                            isMultiEditMode = isMultiEditMode,
                            selectedIndices = selectedIndices,
                            focusedFolderPath = focusedFolderPath,
                            onSelectIndex = onSelectIndex,
                            onEnterFolderPath = onEnterFolderPath,
                        )
                    MetadataEditorFileViewMode.HIERARCHY ->
                        MetadataEditorHierarchyView(
                            node = fileTree,
                            state = state,
                            isMultiEditMode = isMultiEditMode,
                            selectedIndices = selectedIndices,
                            onSelectIndex = onSelectIndex,
                        )
                    MetadataEditorFileViewMode.COLUMN ->
                        MetadataEditorColumnView(
                            fileTree = fileTree,
                            state = state,
                            isMultiEditMode = isMultiEditMode,
                            selectedIndices = selectedIndices,
                            onSelectIndex = onSelectIndex,
                        )
                    MetadataEditorFileViewMode.ICONS ->
                        MetadataEditorIconsView(
                            currentFolder = currentFolder,
                            state = state,
                            thumbnailCache = thumbnailCache,
                            thumbnailCacheRevision = thumbnailCacheRevision,
                            onEnsureThumbnail = onEnsureThumbnail,
                            isMultiEditMode = isMultiEditMode,
                            selectedIndices = selectedIndices,
                            focusedFolderPath = focusedFolderPath,
                            onSelectIndex = onSelectIndex,
                            onEnterFolderPath = onEnterFolderPath,
                            compact = false,
                        )
                }
            }
        }
    }
}
