package org.kryspetrie.fileimport.ui.screens.metadataeditor

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.MetadataEditorFileViewMode
import org.kryspetrie.fileimport.ui.components.ResizableSplitPane
import org.kryspetrie.fileimport.ui.components.ResizableWeightSplitPane

@Composable
fun MetadataEditorEditingLayout(
    vm: MetadataEditorViewModel,
    fileViewMode: MetadataEditorFileViewMode,
    currentSettings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    coroutineScope: CoroutineScope,
    onPickEditorImages: () -> Unit,
    onPickSourceFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layoutPrefs = currentSettings.metadataEditorLayoutPreferences
    val browserWidthDp = layoutPrefs.browserPaneWidthDp
    val previewWeight = layoutPrefs.previewPaneWeight

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val breakpoint = layoutBreakpointForWidth(maxWidth.value)
        val showBrowser =
            when (breakpoint) {
                MetadataEditorLayoutBreakpoint.NARROW -> vm.browserDrawerOpen
                else -> true
            }

        @Composable
        fun FileBrowser(modifier: Modifier = Modifier) {
            MetadataEditorFileBrowserPanel(
                state = vm.state,
                viewMode = fileViewMode,
                onViewModeChange = { mode -> vm.setFileViewMode(mode, onSettingsChange) },
                thumbnailCache = vm.thumbnailCache,
                thumbnailCacheRevision = vm.thumbnailCacheRevision,
                onEnsureThumbnail = { file -> vm.ensureThumbnail(file) },
                diskThumbnailCacheEnabled = currentSettings.metadataEditorDiskThumbnailCache,
                onDiskThumbnailCacheChange = { enabled ->
                    vm.setDiskThumbnailCacheEnabled(enabled, onSettingsChange)
                },
                onClearThumbnailCache = { vm.clearDiskThumbnailCache(coroutineScope) },
                isMultiEditMode = vm.isMultiEditMode,
                selectedIndices = vm.selectedIndices,
                folderPathStack = vm.browserFolderPathStack,
                focusedFolderPath = vm.browserFocusedFolderPath,
                onSelectFiles = onPickEditorImages,
                onSelectFolder = onPickSourceFolder,
                onSelectIndex = { index ->
                    if (vm.isMultiEditMode) vm.toggleSelection(index)
                    else vm.selectBrowserFile(index)
                },
                onToggleMultiEdit = { vm.toggleMultiEditMode() },
                onDeselectAll = { vm.deselectAll() },
                onOpenFolder = onPickSourceFolder,
                onNavigateUp = { vm.navigateBrowserUp() },
                onEnterFolderPath = { path -> vm.navigateBrowserInto(path) },
                onBrowserKey = { key -> vm.handleBrowserKey(key, fileViewMode) },
                browserPaneWidthDp =
                    if (breakpoint == MetadataEditorLayoutBreakpoint.NARROW) {
                        maxWidth.value.toInt().coerceIn(200, 400)
                    } else {
                        browserWidthDp
                    },
                modifier = modifier.fillMaxHeight(),
            )
        }

        @Composable
        fun MetadataPane(modifier: Modifier = Modifier) {
            MetadataEditorPanel(
                state = vm.state,
                editState = vm.editState,
                isMultiEditMode = vm.isMultiEditMode,
                selectedIndices = vm.selectedIndices,
                sourceExif = vm.sourceExif,
                metadataHistory = currentSettings.metadataHistory,
                onSettingsChange = onSettingsChange,
                currentSettings = currentSettings,
                settingsPort = vm.settingsPort,
                coroutineScope = coroutineScope,
                dispatcherProvider = vm.dispatcherProvider,
                onPickLocation = { indices -> vm.requestLocationPicker(indices) },
                onApply = { vm.applyMultiEdit(onSettingsChange) },
                onClear = { vm.clearEditFields() },
                onSelectFaces = { vm.toggleFaceTagging() },
                onRemoveFace = { index -> vm.removeFace(index) },
                onClearAllFaces = { vm.clearAllFaces() },
                keywordsFocusTrigger = vm.keywordsFocusTrigger,
                wideSourceMetadata = maxWidth > 400.dp,
                modifier = modifier.widthIn(max = 520.dp).fillMaxHeight(),
            )
        }

        @Composable
        fun PreviewAndMetadataRow(modifier: Modifier = Modifier) {
            Row(modifier = modifier.fillMaxSize()) {
                MetadataEditorPreviewPane(
                    vm = vm,
                    onPickEditorImages = onPickEditorImages,
                    modifier = Modifier.weight(previewWeight),
                )
                MetadataPane(modifier = Modifier.weight(1f - previewWeight))
            }
        }

        @Composable
        fun PreviewAndMetadataStack(modifier: Modifier = Modifier) {
            ResizableWeightSplitPane(
                firstWeight = previewWeight,
                onFirstWeightChange = { weight ->
                    vm.updatePreviewPaneWeight(weight, onSettingsChange)
                },
                modifier = modifier.fillMaxSize(),
                first = {
                    MetadataEditorPreviewPane(
                        vm = vm,
                        onPickEditorImages = onPickEditorImages,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
                second = { MetadataPane(modifier = Modifier.fillMaxSize()) },
            )
        }

        when {
            breakpoint == MetadataEditorLayoutBreakpoint.NARROW && showBrowser -> {
                FileBrowser(Modifier.fillMaxSize())
            }
            breakpoint == MetadataEditorLayoutBreakpoint.NARROW -> {
                PreviewAndMetadataRow(Modifier.fillMaxSize())
            }
            breakpoint == MetadataEditorLayoutBreakpoint.ULTRA_WIDE -> {
                ResizableSplitPane(
                    firstWidthDp = browserWidthDp.dp,
                    onFirstWidthChange = { width ->
                        vm.updateBrowserPaneWidth(width.value.toInt(), onSettingsChange)
                    },
                    modifier = Modifier.fillMaxSize(),
                    first = { FileBrowser() },
                    second = { PreviewAndMetadataRow(Modifier.fillMaxSize()) },
                )
            }
            else -> {
                ResizableSplitPane(
                    firstWidthDp = browserWidthDp.dp,
                    onFirstWidthChange = { width ->
                        vm.updateBrowserPaneWidth(width.value.toInt(), onSettingsChange)
                    },
                    modifier = Modifier.fillMaxSize(),
                    first = { FileBrowser() },
                    second = { PreviewAndMetadataStack(Modifier.fillMaxSize()) },
                )
            }
        }
    }
}
