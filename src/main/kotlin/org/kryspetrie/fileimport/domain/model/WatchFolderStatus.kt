package org.kryspetrie.fileimport.domain.model

/** Status of a watched folder for automatic imports. */
data class WatchFolderStatus(
    val isWatching: Boolean = false,
    val watchPath: String = "",
    val lastEventTime: Long = 0,
    val filesDetected: Int = 0,
    val autoImportsPending: Int = 0,
    val lastError: String? = null,
)
