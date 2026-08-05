package org.kryspetrie.fileimport.ui.screens.metadataeditor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import java.io.File
import kotlinx.coroutines.flow.first
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.MetadataEditorFileViewMode

// ── Multi-edit ───────────────────────────────────────────────

internal fun MetadataEditorViewModel.toggleMultiEditMode() {
    isMultiEditMode = !isMultiEditMode
    if (!isMultiEditMode) {
        if (selectedIndices.size == 1) {
            state.selectFile(selectedIndices.first())
        }
        selectedIndices = emptySet()
    } else {
        if (state.selectedIndex >= 0) {
            selectedIndices = setOf(state.selectedIndex)
        }
    }
}

internal fun MetadataEditorViewModel.syncPrimarySelectionInMultiMode() {
    val primary = selectedIndices.minOrNull() ?: return
    if (state.selectedIndex != primary) {
        state.selectFile(primary)
    }
}

internal fun MetadataEditorViewModel.toggleSelection(index: Int) {
    browserFocusedFolderPath = null
    if (isMultiEditMode) {
        selectedIndices =
            if (index in selectedIndices) selectedIndices - index else selectedIndices + index
        syncPrimarySelectionInMultiMode()
    } else {
        state.selectFile(index)
    }
}

internal fun MetadataEditorViewModel.selectAll() {
    selectedIndices = state.files.indices.toSet()
}

internal fun MetadataEditorViewModel.deselectAll() {
    selectedIndices = emptySet()
}

// ── Settings persistence ──────────────────────────────────────

internal fun MetadataEditorViewModel.saveSettings(
    update: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
) {
    onSettingsChange(update)
}

internal fun MetadataEditorViewModel.resetBrowserNavigation() {
    browserFolderPathStack = emptyList()
    browserFocusedFolderPath = null
}

internal fun MetadataEditorViewModel.fileTree(): MetadataFolderNode =
    buildMetadataFileTree(state.files, state.sourcePath)

internal fun MetadataEditorViewModel.currentBrowserFolder(): MetadataFolderNode =
    resolveMetadataBrowserFolder(fileTree(), browserFolderPathStack)

internal fun MetadataEditorViewModel.navigateBrowserUp() {
    if (browserFolderPathStack.isNotEmpty()) {
        browserFolderPathStack = browserFolderPathStack.dropLast(1)
        browserFocusedFolderPath = null
    }
}

internal fun MetadataEditorViewModel.navigateBrowserInto(folderPath: String) {
    browserFolderPathStack = browserFolderPathStack + folderPath
    browserFocusedFolderPath = null
}

internal fun MetadataEditorViewModel.selectBrowserFile(index: Int) {
    browserFocusedFolderPath = null
    if (isMultiEditMode) {
        selectedIndices = setOf(index)
        state.selectFile(index)
    } else {
        state.selectFile(index)
    }
}

/** Arrow-key navigation for the file browser. Returns true when the key was handled. */
internal fun MetadataEditorViewModel.handleBrowserKey(
    key: Key,
    viewMode: MetadataEditorFileViewMode,
): Boolean {
    if (!state.editingActive || state.fileCount == 0) return false
    return when (key) {
        Key.DirectionUp -> {
            if (
                viewMode == MetadataEditorFileViewMode.LIST ||
                    viewMode == MetadataEditorFileViewMode.ICONS
            ) {
                moveBrowserNav(-1)
            } else {
                state.prevFile()
            }
            true
        }
        Key.DirectionDown -> {
            if (
                viewMode == MetadataEditorFileViewMode.LIST ||
                    viewMode == MetadataEditorFileViewMode.ICONS
            ) {
                moveBrowserNav(1)
            } else {
                state.nextFile()
            }
            true
        }
        Key.DirectionLeft -> {
            if (
                viewMode == MetadataEditorFileViewMode.LIST ||
                    viewMode == MetadataEditorFileViewMode.ICONS
            ) {
                navigateBrowserUp()
                true
            } else {
                false
            }
        }
        Key.DirectionRight,
        Key.Enter -> {
            if (
                (viewMode == MetadataEditorFileViewMode.LIST ||
                    viewMode == MetadataEditorFileViewMode.ICONS) &&
                    browserFocusedFolderPath != null
            ) {
                navigateBrowserInto(browserFocusedFolderPath!!)
                true
            } else {
                false
            }
        }
        else -> false
    }
}

internal fun MetadataEditorViewModel.moveBrowserNav(delta: Int) {
    val items = metadataBrowserNavItems(currentBrowserFolder())
    if (items.isEmpty()) return
    val currentIndex = metadataBrowserNavIndex(items, state.selectedIndex, browserFocusedFolderPath)
    val newIndex = metadataBrowserNavIndexAfterDelta(items, currentIndex, delta)
    if (newIndex < 0) return
    when (val item = items[newIndex]) {
        is MetadataBrowserNavItem.File -> selectBrowserFile(item.index)
        is MetadataBrowserNavItem.Folder -> browserFocusedFolderPath = item.path
    }
}

// ── Navigation ──────────────────────────────────────────────────

/** Go back to the landing page, clearing all loaded files and edit state. */
internal fun MetadataEditorViewModel.goBackToLanding() {
    state.editingActive = false
    state.clear()
    currentImage = null
    sourceExif = null
    isMultiEditMode = false
    selectedIndices = emptySet()
    resetBrowserNavigation()
    thumbnailCache.clear()
    thumbnailCacheRevision++
}
