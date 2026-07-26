package org.kryspetrie.fileimport.ui.screens.metadataeditor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration

/** Severity level for UI messages in the metadata editor. */
enum class MessageSeverity {
    INFO,
    ERROR,
}

/**
 * A transient UI message for status/error feedback.
 *
 * @property text The message text.
 * @property severity Whether this is an info or error message.
 * @property timestampMs When the message was created (for auto-clear).
 */
data class UiMessage(
    val text: String,
    val severity: MessageSeverity = MessageSeverity.INFO,
    val timestampMs: Long = System.currentTimeMillis(),
)

/**
 * State holder for a single file entry in the bulk metadata editor.
 *
 * Each file tracks its own [PhotoScanConfiguration] for metadata overrides, its loaded
 * [BufferedImage] (lazily loaded, not stored here — loaded on demand), and whether metadata has
 * been modified since loading.
 */
data class BulkEditFileEntry(
    val file: File,
    val config: PhotoScanConfiguration = PhotoScanConfiguration(),
    val isModified: Boolean = false,
    val sourceExifLoaded: Boolean = false,
)

/**
 * Output mode for the bulk metadata editor.
 * - OVERWRITE: Modify the original file in place (backup first, enables undo).
 * - SAVE_NEW: Save to a new file in the specified output directory.
 */
enum class OutputMode {
    OVERWRITE,
    SAVE_NEW,
}

/**
 * Central state for the bulk metadata editor.
 *
 * Manages:
 * - The list of image files loaded from a folder
 * - The currently selected file index
 * - Per-file metadata configurations
 * - The output mode (overwrite vs save new)
 * - The output directory for save-new mode
 * - The source path (file or folder)
 * - Whether to include subfolders when loading a folder
 * - Whether the editor is active (folder selected, files loaded)
 * - Undo journal tracking
 * - UI messages (unified status and error feedback)
 */
class BulkEditState {
    /** Current source path being edited (can be a file or folder). */
    var sourcePath by mutableStateOf("")

    /** Whether to include image files from subdirectories when loading a folder. */
    var includeSubfolders by mutableStateOf(false)

    /** Whether the editor is active — files have been loaded and the user is editing. */
    var editingActive by mutableStateOf(false)

    /** All image files in the current folder. */
    var files by mutableStateOf<List<File>>(emptyList())
        private set

    /** Per-file metadata configuration state. */
    var fileConfigs by mutableStateOf<Map<String, BulkEditFileEntry>>(emptyMap())
        private set

    /** Index of the currently selected file (-1 = no selection). */
    var selectedIndex by mutableStateOf(-1)

    /** Output mode: overwrite original or save new. */
    var outputMode by mutableStateOf(OutputMode.OVERWRITE)

    /** Output directory for save-new mode. */
    var outputDirectory by mutableStateOf("")

    /** Whether currently loading files. */
    var isLoading by mutableStateOf(false)

    /** Unified UI message (status or error), auto-clears after timeout. */
    var message by mutableStateOf<UiMessage?>(null)

    /** Path to the most recent undo journal file (for undo/redo in OVERWRITE mode). */
    var lastJournalPath by mutableStateOf<String?>(null)

    /** Whether an undo operation is available. */
    var canUndo by mutableStateOf(false)

    /** Whether a redo operation is available. */
    var canRedo by mutableStateOf(false)

    /** The currently selected file, or null. */
    val selectedFile: File?
        get() = if (selectedIndex >= 0 && selectedIndex < files.size) files[selectedIndex] else null

    /** The currently selected file's config, or a default. */
    val selectedConfig: PhotoScanConfiguration
        get() {
            val file = selectedFile ?: return PhotoScanConfiguration()
            return fileConfigs[file.absolutePath]?.config ?: PhotoScanConfiguration()
        }

    /** Number of files loaded. */
    val fileCount: Int
        get() = files.size

    /** Whether the source is a single file (vs a folder). */
    val isSingleFile: Boolean
        get() = files.size == 1 && sourcePath.isNotEmpty() && File(sourcePath).isFile

    /** Number of files with unsaved metadata changes. */
    val modifiedCount: Int
        get() = fileConfigs.values.count { it.isModified }

    /** Shows an info message (auto-clears after timeout). */
    fun showInfo(text: String) {
        message = UiMessage(text, MessageSeverity.INFO)
    }

    /**
     * Shows an error message (auto-clears after timeout). Also clears isLoading to prevent stuck
     * spinners.
     */
    fun showError(text: String) {
        message = UiMessage(text, MessageSeverity.ERROR)
        isLoading = false
    }

    /** Clears the current message. */
    fun clearMessage() {
        message = null
    }

    /** Loads a list of files into the state. */
    fun loadFiles(imageFiles: List<File>) {
        files = imageFiles
        fileConfigs = imageFiles.associate { it.absolutePath to BulkEditFileEntry(it) }
        selectedIndex = if (imageFiles.isNotEmpty()) 0 else -1
        isLoading = false
        message = null
    }

    /** Loads a single file into the state. */
    fun loadSingleFile(file: File) {
        sourcePath = file.absolutePath
        files = listOf(file)
        fileConfigs = mapOf(file.absolutePath to BulkEditFileEntry(file))
        selectedIndex = 0
        isLoading = false
        message = null
    }

    /** Updates the config for the currently selected file. */
    fun updateSelectedConfig(transform: (PhotoScanConfiguration) -> PhotoScanConfiguration) {
        val file = selectedFile ?: return
        val key = file.absolutePath
        val entry = fileConfigs[key] ?: return
        val newConfig = transform(entry.config)
        fileConfigs =
            fileConfigs.toMutableMap().apply {
                this[key] = entry.copy(config = newConfig, isModified = true)
            }
    }

    /** Updates the config for a specific file index. */
    fun updateConfig(index: Int, transform: (PhotoScanConfiguration) -> PhotoScanConfiguration) {
        if (index < 0 || index >= files.size) return
        val file = files[index]
        val key = file.absolutePath
        val entry = fileConfigs[key] ?: return
        val newConfig = transform(entry.config)
        fileConfigs =
            fileConfigs.toMutableMap().apply {
                this[key] = entry.copy(config = newConfig, isModified = true)
            }
    }

    /** Marks a file as saved (no longer modified). Called after a successful save operation. */
    fun markSaved(file: File) {
        val key = file.absolutePath
        val entry = fileConfigs[key] ?: return
        fileConfigs =
            fileConfigs.toMutableMap().apply { this[key] = entry.copy(isModified = false) }
    }

    /** Marks source EXIF as loaded for the specified file. */
    fun markSourceExifLoaded(file: File) {
        val key = file.absolutePath
        val entry = fileConfigs[key] ?: return
        fileConfigs =
            fileConfigs.toMutableMap().apply { this[key] = entry.copy(sourceExifLoaded = true) }
    }

    /** Selects the file at the given index. */
    fun selectFile(index: Int) {
        selectedIndex = index.coerceIn(-1, files.size - 1)
    }

    /** Moves selection to the next file. Returns false if at end. */
    fun nextFile(): Boolean {
        if (selectedIndex < files.size - 1) {
            selectedIndex++
            return true
        }
        return false
    }

    /** Moves selection to the previous file. Returns false if at start. */
    fun prevFile(): Boolean {
        if (selectedIndex > 0) {
            selectedIndex--
            return true
        }
        return false
    }

    /** Clears all state. */
    fun clear() {
        sourcePath = ""
        files = emptyList()
        fileConfigs = emptyMap()
        selectedIndex = -1
        message = null
        lastJournalPath = null
        canUndo = false
        canRedo = false
    }
}
