package org.kryspetrie.fileimport.domain.model

import kotlinx.serialization.Serializable

/**
 * Status of a watched folder for automatic imports.
 *
 * Updated in real-time by [WatchFolderService] as files are detected and imported. Each status is
 * tied to a [WatchFolderConfig] via [configId].
 *
 * @property configId The ID of the [WatchFolderConfig] this status belongs to.
 * @property isWatching Whether the folder is currently being watched.
 * @property watchPath The path being watched.
 * @property lastEventTime Timestamp of the last file system event (epoch millis).
 * @property filesDetected Total number of image files detected since watching started.
 * @property autoImportsPending Number of files waiting for the cooldown period before import.
 * @property lastError Error message if the watch or import failed, null otherwise.
 * @property importCount Total number of successful auto-imports since watching started.
 * @property lastImportTime Timestamp of the last successful import (epoch millis).
 * @property lastImportFileCount Number of files in the most recent successful import.
 */
@Serializable
data class WatchFolderStatus(
    val configId: String = "",
    val isWatching: Boolean = false,
    val watchPath: String = "",
    val lastEventTime: Long = 0,
    val filesDetected: Int = 0,
    val autoImportsPending: Int = 0,
    val lastError: String? = null,
    val importCount: Int = 0,
    val lastImportTime: Long = 0,
    val lastImportFileCount: Int = 0,
)
