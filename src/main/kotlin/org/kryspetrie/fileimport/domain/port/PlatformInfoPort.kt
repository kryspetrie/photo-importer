package org.kryspetrie.fileimport.domain.port

import java.io.File

/**
 * Domain port for platform-specific queries and operations.
 *
 * Decouples the UI and application layers from the infrastructure's [Platform] utility, allowing
 * cross-platform logic to be tested without static method calls.
 */
interface PlatformPort {

    /** Whether the current OS is macOS. */
    val isMac: Boolean

    /** Whether the current OS is Windows. */
    val isWindows: Boolean

    /** Whether the current OS is Linux. */
    val isLinux: Boolean

    /** The OS name string (e.g. "Mac OS X", "Windows 11", "Linux"). */
    val osName: String

    /** Line separator for the current platform. */
    val lineSeparator: String

    /**
     * Open a file or directory with the system's default viewer (Finder, Explorer, etc.).
     *
     * @param file The file or directory to open.
     * @return true if the operation was initiated successfully, false otherwise.
     */
    fun openWithSystemViewer(file: File): Boolean
}
