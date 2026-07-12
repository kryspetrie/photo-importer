package org.kryspetrie.fileimport.ui.screens.metadataeditor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration

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
 * - OVERWRITE: Modify the original file in place (backup first).
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
 * - The current folder path
 */
class BulkEditState {
    /** Current folder path being edited. */
    var folderPath by mutableStateOf("")

    /** All image files in the current folder. */
    var files by mutableStateOf<List<File>>(emptyList())
        private set

    /** Per-file metadata configuration state. */
    var fileConfigs by mutableStateOf<Map<String, BulkEditFileEntry>>(emptyMap())
        private set

    /** Index of the currently selected file (-1 = no selection). */
    var selectedIndex by mutableStateOf(-1)

    /** Output mode: overwrite original or save new. */
    var outputMode by mutableStateOf(OutputMode.SAVE_NEW)

    /** Output directory for save-new mode. */
    var outputDirectory by mutableStateOf("")

    /** Whether currently loading files. */
    var isLoading by mutableStateOf(false)

    /** Error message to display, if any. */
    var errorMessage by mutableStateOf<String?>(null)

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

    /** Loads a list of files into the state. */
    fun loadFiles(imageFiles: List<File>) {
        files = imageFiles
        fileConfigs = imageFiles.associate { it.absolutePath to BulkEditFileEntry(it) }
        selectedIndex = if (imageFiles.isNotEmpty()) 0 else -1
        isLoading = false
        errorMessage = null
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
        folderPath = ""
        files = emptyList()
        fileConfigs = emptyMap()
        selectedIndex = -1
        errorMessage = null
    }
}
