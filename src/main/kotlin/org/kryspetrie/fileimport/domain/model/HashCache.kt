package org.kryspetrie.fileimport.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class FolderIndex(
    val folderPath: String,
    val entries: Map<String, HashCacheEntry> = emptyMap(),
    val lastScanTime: Long = 0,
)

@Serializable
data class HashCacheEntry(val hash: String, val fileSize: Long, val lastModified: Long)

data class IndexProgress(
    val indexed: Int = 0,
    val total: Int = 0,
    val currentFile: String = "",
    val isComplete: Boolean = false,
) {
    val percent: Float
        get() = if (total > 0) indexed.toFloat() / total else 0f
}
