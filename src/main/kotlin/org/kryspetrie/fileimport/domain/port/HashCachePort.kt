package org.kryspetrie.fileimport.domain.port

import org.kryspetrie.fileimport.domain.model.FolderIndex
import org.kryspetrie.fileimport.domain.model.IndexProgress

interface HashCachePort {
    suspend fun getIndex(folderPath: String): FolderIndex?

    suspend fun saveIndex(index: FolderIndex)

    suspend fun clearIndex(folderPath: String)

    suspend fun clearAllIndexes()

    suspend fun indexFolder(
        folderPath: String,
        recursive: Boolean = true,
        onProgress: (IndexProgress) -> Unit = {},
    ): FolderIndex

    fun getDestinationHashes(folderPath: String): Set<String>
}
