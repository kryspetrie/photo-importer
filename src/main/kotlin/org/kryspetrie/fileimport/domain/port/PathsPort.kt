package org.kryspetrie.fileimport.domain.port

/**
 * Domain port for accessing application paths.
 *
 * Provides access to default directories and path resolution without coupling the UI or application
 * layers to the infrastructure's [AppPaths] utility.
 *
 * Implementations should provide OS-appropriate default locations for:
 * - Destination: where imported files are saved by default
 * - Application support: config, cache, models directories
 * - Temp: temporary file storage
 */
interface PathsPort {
    /** Default destination directory for file imports. */
    val defaultDestination: String

    /** Application support directory (config, settings, etc.). */
    val appSupportDir: String

    /** Cache directory for temporary files. */
    val cacheDir: String

    /** Directory for downloaded models (e.g., orientation detection ONNX). */
    val modelsDir: String

    /** User home directory. */
    val userHome: String
}
