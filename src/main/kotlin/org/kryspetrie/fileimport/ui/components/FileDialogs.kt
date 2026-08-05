package org.kryspetrie.fileimport.ui.components

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FilenameFilter
import javax.swing.JFileChooser
import javax.swing.UIManager
import javax.swing.filechooser.FileNameExtensionFilter
import org.kryspetrie.fileimport.infrastructure.adapter.Platform

/**
 * Opens a native folder picker dialog, cross-platform.
 *
 * Platform behavior:
 * - **macOS**: Uses AWT [FileDialog] with `apple.awt.fileDialogForDirectories=true`. This gives the
 *   native macOS folder chooser, which is the familiar blue "Open" dialog.
 * - **Windows/Linux**: Uses [JFileChooser] in `DIRECTORIES_ONLY` mode with the system
 *   look-and-feel. This gives a native-looking dialog that works on all Linux window managers
 *   including Wayland, where AWT [FileDialog] can be invisible.
 *
 * @param title Dialog window title
 * @return Selected folder absolute path, or null if cancelled
 */
fun pickFolder(title: String): String? {
    // macOS: use AWT FileDialog with the directories-only system property.
    // This is the only way to get the native macOS folder picker.
    if (Platform.isMac) {
        System.setProperty("apple.awt.fileDialogForDirectories", "true")
        try {
            val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
            dialog.isVisible = true
            return if (dialog.directory != null && dialog.file != null) {
                File(dialog.directory, dialog.file).absolutePath
            } else null
        } finally {
            System.setProperty("apple.awt.fileDialogForDirectories", "false")
        }
    }

    // Windows and Linux: use JFileChooser with system look-and-feel.
    // JFileChooser renders natively on Windows (Windows LaF) and works correctly
    // on Linux/Wayland where AWT FileDialog can be invisible.
    applySystemLookAndFeel()
    val chooser =
        JFileChooser().apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            dialogTitle = title
            isAcceptAllFileFilterUsed = false
        }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile.absolutePath
    } else null
}

/**
 * Opens a native file picker dialog, cross-platform.
 *
 * Platform behavior:
 * - **macOS**: Uses AWT [FileDialog] which gives the native macOS file chooser (the familiar blue
 *   dialog, same as used by [pickFolder]). This matches the macOS folder picker experience exactly.
 * - **Windows/Linux**: Uses [JFileChooser] in `FILES_ONLY` mode with the system look-and-feel. This
 *   gives a native-looking dialog that works on all Linux window managers including Wayland, where
 *   AWT [FileDialog] can be invisible.
 *
 * @param title Dialog window title
 * @param extensionFilter Optional list of extensions to filter (e.g. listOf("jpg", "png"))
 * @return Selected file absolute path, or null if cancelled
 */
@Suppress("SpreadOperator")
fun pickFile(title: String, extensionFilter: List<String>? = null): String? {
    // macOS: use AWT FileDialog for the native macOS file chooser (same dialog as pickFolder).
    if (Platform.isMac) {
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
        // FileDialog.LOAD without apple.awt.fileDialogForDirectories selects files by default.
        // Filename filtering on AWT FileDialog uses FilenameFilter on the visible filename,
        // which works for the native macOS dialog.
        if (extensionFilter != null) {
            dialog.filenameFilter = FilenameFilter { _, name ->
                extensionFilter.any { ext -> name.lowercase().endsWith(".$ext") }
            }
        }
        dialog.isVisible = true
        return if (dialog.directory != null && dialog.file != null) {
            File(dialog.directory, dialog.file).absolutePath
        } else null
    }

    // Windows and Linux: use JFileChooser with system look-and-feel.
    // JFileChooser renders natively on Windows (Windows LaF) and works correctly
    // on Linux/Wayland where AWT FileDialog can be invisible.
    applySystemLookAndFeel()
    val chooser =
        JFileChooser().apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.FILES_ONLY
            if (extensionFilter != null) {
                isAcceptAllFileFilterUsed = false
                addChoosableFileFilter(
                    FileNameExtensionFilter(
                        "${extensionFilter.joinToString(", ") { it.uppercase() }} files",
                        *extensionFilter.toTypedArray(),
                    )
                )
            }
        }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile.absolutePath
    } else null
}

/** Supported image file extensions for photo/media import. */
val IMAGE_EXTENSIONS = listOf("jpg", "jpeg", "png", "gif", "bmp", "tiff", "tif", "webp")

/** Extensions that support in-place metadata editing (includes RAW). */
val METADATA_EDITABLE_EXTENSIONS: List<String> =
    org.kryspetrie.fileimport.domain.model.ImageFileType.imageExtensions().sorted()

/** Check if a file is a supported image format. */
fun isImageFile(file: File): Boolean {
    val ext = file.extension.lowercase()
    return ext in IMAGE_EXTENSIONS
}

/** Check if a file supports metadata editing via ExifTool. */
fun isMetadataEditableFile(file: File): Boolean {
    val fileType =
        org.kryspetrie.fileimport.domain.model.ImageFileType.fromExtension(file.extension)
    return org.kryspetrie.fileimport.application.export.FileFormatSupport.canWriteMetadataInPlace(
        fileType
    )
}

/**
 * Opens a native file picker dialog filtered to image files.
 *
 * Convenience wrapper around [pickFile] with a filter for common image formats.
 *
 * @param title Dialog window title
 * @return Selected file absolute path, or null if cancelled
 */
fun pickImageFile(title: String): String? = pickFile(title, IMAGE_EXTENSIONS)

/**
 * Opens a native file picker allowing multi-selection of metadata-editable image files.
 *
 * On macOS this uses [JFileChooser] with the system look-and-feel, which provides the familiar
 * column view and Cmd-click multi-selection. Single-selection is also supported.
 *
 * @param title Dialog window title
 * @return Selected file absolute paths, or empty list if cancelled
 */
@Suppress("SpreadOperator")
fun pickImageFiles(title: String, extensionDescription: String): List<String> {
    applySystemLookAndFeel()
    val chooser =
        JFileChooser().apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.FILES_ONLY
            isMultiSelectionEnabled = true
            isAcceptAllFileFilterUsed = false
            addChoosableFileFilter(
                FileNameExtensionFilter(
                    extensionDescription,
                    *METADATA_EDITABLE_EXTENSIONS.toTypedArray(),
                )
            )
        }
    if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
        return emptyList()
    }
    val selected = chooser.selectedFiles?.toList().orEmpty()
    if (selected.isNotEmpty()) return selected.map { it.absolutePath }
    return chooser.selectedFile?.absolutePath?.let { listOf(it) }.orEmpty()
}

/**
 * Applies the system look-and-feel for native dialog appearance.
 *
 * Sets Swing to use the platform-native theme (Aqua on macOS, Windows LaF on Windows, GTK on
 * Linux). This makes JFileChooser dialogs look native on each platform. Exceptions are silently
 * caught — default LaF is acceptable as fallback.
 */
private fun applySystemLookAndFeel() {
    try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
    } catch (_: Exception) {
        // Default look-and-feel is acceptable as fallback
    }
}
