package org.kryspetrie.fileimport.infrastructure.adapter

import java.io.File
import org.kryspetrie.fileimport.domain.port.PlatformPort

/**
 * Infrastructure adapter that provides platform info via [Platform] utilities.
 *
 * Implements [PlatformPort] so the UI and application layers can query the OS and open files
 * without importing infrastructure directly.
 */
class PlatformInfoAdapter : PlatformPort {

    override val isMac: Boolean
        get() = Platform.isMac

    override val isWindows: Boolean
        get() = Platform.isWindows

    override val isLinux: Boolean
        get() = Platform.isLinux

    override val osName: String
        get() = Platform.osName

    override val lineSeparator: String
        get() = System.lineSeparator()

    override fun openWithSystemViewer(file: File): Boolean {
        return Platform.openWithSystemViewer(file)
    }
}
