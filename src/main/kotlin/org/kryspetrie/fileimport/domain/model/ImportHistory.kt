package org.kryspetrie.fileimport.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ImportHistoryEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val sourcePath: String,
    val destinationPath: String,
    val profileName: String = "",
    val totalFiles: Int,
    val successCount: Int,
    val errorCount: Int,
    val skippedCount: Int,
    val duplicateCount: Int,
    val totalBytes: Long = 0,
    val durationMs: Long = 0
)
