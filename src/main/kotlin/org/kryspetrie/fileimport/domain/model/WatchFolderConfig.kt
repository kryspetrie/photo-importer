package org.kryspetrie.fileimport.domain.model

import kotlinx.serialization.Serializable

/**
 * Configuration for watching a folder for automatic imports.
 *
 * Each config represents a single watch folder with its own source path, destination, import
 * settings, and scheduling options. Configs are persisted in [AppSettings] and survive app
 * restarts.
 *
 * @property id Unique identifier for this watch config. Auto-generated if not provided.
 * @property watchPath Directory to watch for new image files.
 * @property destinationPath Directory where imported files will be placed.
 * @property profileName Human-readable name for this watch config.
 * @property configuration Import settings (dedup, verification, sidecars, etc.).
 * @property cooldownMs Milliseconds to wait after the last file event before triggering import.
 * @property recursive Whether to watch subdirectories recursively.
 * @property enabled Whether this watch config is active. Disabled configs are paused but not
 *   deleted.
 * @property autoStart Whether to automatically start watching when the app launches.
 */
@Serializable
data class WatchFolderConfig(
    val id: String = java.util.UUID.randomUUID().toString(),
    val watchPath: String,
    val destinationPath: String,
    val profileName: String = "",
    val configuration: ImportConfiguration = ImportConfiguration(),
    val cooldownMs: Long = 5000,
    val recursive: Boolean = true,
    val enabled: Boolean = true,
    val autoStart: Boolean = false,
)
