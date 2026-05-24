package org.kryspetrie.fileimport.ui.components

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
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
 * Uses [JFileChooser] on all platforms with system look-and-feel for native appearance. This avoids
 * the AWT [FileDialog] issue on Linux/Wayland where dialogs can be invisible.
 *
 * On macOS, JFileChooser with Aqua LaF renders as a native-looking dialog. On Windows, JFileChooser
 * with Windows LaF renders as a native-looking dialog. On Linux, JFileChooser works under all
 * window managers including Wayland.
 *
 * @param title Dialog window title
 * @param extensionFilter Optional list of extensions to filter (e.g. listOf("jpg", "png"))
 * @return Selected file absolute path, or null if cancelled
 */
@Suppress("SpreadOperator")
fun pickFile(title: String, extensionFilter: List<String>? = null): String? {
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

/** Check if a file is a supported image format. */
fun isImageFile(file: File): Boolean {
    val ext = file.extension.lowercase()
    return ext in IMAGE_EXTENSIONS
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
