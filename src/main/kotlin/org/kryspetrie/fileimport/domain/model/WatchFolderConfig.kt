package org.kryspetrie.fileimport.domain.model

/** Configuration for watching a folder for automatic imports. */
data class WatchFolderConfig(
    val watchPath: String,
    val destinationPath: String,
    val profileName: String = "",
    val configuration: ImportConfiguration = ImportConfiguration(),
    val cooldownMs: Long = 5000,
    val recursive: Boolean = true,
)
