package org.kryspetrie.fileimport.ui.screens.metadataeditor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.MetadataEditEntry
import org.kryspetrie.fileimport.domain.model.i18n.StringKey

/** Save the currently selected file's metadata. */
internal fun MetadataEditorViewModel.saveCurrentFile(scope: CoroutineScope) {
    val file = state.selectedFile ?: return
    val config = state.selectedConfig
    scope.launch {
        try {
            val result =
                editService.saveFile(
                    file = file,
                    config = config,
                    outputMode = state.outputMode.name,
                    outputDirectory = state.outputDirectory,
                )
            if (result != null) {
                val journalPath =
                    editService.saveJournal(
                        sourceFolderPath = state.sourcePath,
                        outputMode = state.outputMode.name,
                        entries = listOf(result.entry),
                    )
                if (journalPath != null) {
                    state.lastJournalPath = journalPath
                    state.canUndo = true
                    state.canRedo = false
                }
                state.markSaved(file)
                invalidateThumbnail(file, scope)
                state.showInfo(t(StringKey.META_SAVED_FILE, "name" to file.name))
            } else {
                state.showError(t(StringKey.ERROR_IMAGE_READ_FAILED) + ": ${file.name}")
            }
        } catch (_: CancellationException) {
            // Cancellation must propagate
        } catch (e: Exception) {
            state.showError(t(StringKey.META_ERROR_SAVE, "message" to (e.message ?: "")))
        }
    }
}

/** Save all modified files. */
internal fun MetadataEditorViewModel.saveAllModified(scope: CoroutineScope) {
    val modifiedEntries = state.fileConfigs.values.filter { it.isModified }
    if (modifiedEntries.isEmpty()) {
        state.showInfo(t(StringKey.META_NO_UNSAVED))
        return
    }
    scope.launch {
        try {
            val entries = mutableListOf<MetadataEditEntry>()
            var savedCount = 0
            for (entry in modifiedEntries) {
                val file = entry.file
                val config = entry.config
                val result =
                    editService.saveFile(
                        file = file,
                        config = config,
                        outputMode = state.outputMode.name,
                        outputDirectory = state.outputDirectory,
                    )
                if (result != null) {
                    entries.add(result.entry)
                    state.markSaved(file)
                    invalidateThumbnail(file, scope)
                    savedCount++
                }
            }
            if (entries.isNotEmpty()) {
                val journalPath =
                    editService.saveJournal(
                        sourceFolderPath = state.sourcePath,
                        outputMode = state.outputMode.name,
                        entries = entries,
                    )
                if (journalPath != null) {
                    state.lastJournalPath = journalPath
                    state.canUndo = true
                    state.canRedo = false
                }
            }
            state.showInfo(t(StringKey.META_SAVED_N_FILES, "count" to savedCount.toString()))
        } catch (_: CancellationException) {
            // Cancellation must propagate
        } catch (e: Exception) {
            state.showError(t(StringKey.META_ERROR_SAVE, "message" to (e.message ?: "")))
        }
    }
}

/** Undo the last save operation. */
internal fun MetadataEditorViewModel.undoLast(scope: CoroutineScope) {
    val journalId = state.lastJournalPath ?: return
    scope.launch {
        try {
            val undoResult = undoService.undo(journalId)
            if (undoResult > 0) {
                state.showInfo(t(StringKey.META_UNDONE_N, "count" to undoResult.toString()))
                state.canUndo = false
                state.canRedo = true
                // Reload current image
                state.selectedIndex = state.selectedIndex
            } else {
                state.showError(t(StringKey.META_UNDO_FAILED))
            }
        } catch (e: Exception) {
            state.showError(t(StringKey.META_ERROR_UNDO, "message" to (e.message ?: "")))
        }
    }
}

/** Redo the last undo operation. */
internal fun MetadataEditorViewModel.redoLast(scope: CoroutineScope) {
    val journalId = state.lastJournalPath ?: return
    if (!state.canRedo) return
    scope.launch {
        try {
            val redoResult =
                undoService.redo(journalId) { outputPath, config, sourcePath ->
                    editService.reapplyMetadata(
                        filePath = outputPath,
                        config = config,
                        exifSourcePath = sourcePath,
                    )
                }
            if (redoResult > 0) {
                state.showInfo(t(StringKey.META_REDONE_N, "count" to redoResult.toString()))
                state.canUndo = true
                state.canRedo = false
                state.selectedIndex = state.selectedIndex
            } else {
                state.showError(t(StringKey.META_REDO_FAILED))
            }
        } catch (e: Exception) {
            state.showError(t(StringKey.META_ERROR_REDO, "message" to (e.message ?: "")))
        }
    }
}

/** Clear edit fields, reloading from current config in single-edit mode. */
internal fun MetadataEditorViewModel.clearEditFields() {
    editState.clear()
    if (!isMultiEditMode && state.selectedFile != null) {
        editState.loadFrom(state.selectedConfig)
    }
}

/** Apply multi-edit fields to all selected indices. */
internal fun MetadataEditorViewModel.applyMultiEdit(onSettingsChange: (AppSettings) -> Unit) {
    if (selectedIndices.isEmpty()) {
        state.showError(t(StringKey.META_NO_IMAGES_SELECTED))
        return
    }
    selectedIndices.forEach { idx ->
        state.updateConfig(idx) { config -> editState.applyNonBlankTo(config) }
    }
    onSettingsChange(currentSettings.addMetadataSet(editState.toRecentMetadataSet()))
    editState.clear()
}
