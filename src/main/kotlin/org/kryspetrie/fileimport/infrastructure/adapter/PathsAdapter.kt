package org.kryspetrie.fileimport.infrastructure.adapter

import java.io.File
import org.kryspetrie.fileimport.domain.port.PathsPort

/**
 * Infrastructure adapter that provides application paths via [Platform] utilities.
 *
 * Implements [PathsPort] so the UI and application layers can access default paths
 * without importing infrastructure directly.
 */
class PathsAdapter : PathsPort {

    override val defaultDestination: String
        get() = AppPaths.defaultDestination.absolutePath

    override val appSupportDir: String
        get() = Platform.appDataDir.absolutePath

    override val cacheDir: String
        get() = Platform.cacheDir.absolutePath

    override val modelsDir: String
        get() = File(Platform.appDataDir, "models").absolutePath

    override val userHome: String
        get() = System.getProperty("user.home")
}