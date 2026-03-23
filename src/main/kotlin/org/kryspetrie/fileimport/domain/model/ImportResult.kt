package org.kryspetrie.fileimport.domain.model

data class ImportResult(
    val totalFiles: Int,
    val successCount: Int,
    val errorCount: Int,
    val duplicateCount: Int,
    val skippedCount: Int,
    val deletedSourceCount: Int,
    val copiedFiles: List<CopiedFile> = emptyList(),
    val errors: List<ImportError> = emptyList(),
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long = 0,
    /** Optional history entry with detailed file information for persistence */
    val historyEntry: ImportHistoryEntry? = null
) {
  val duration: Long
    get() = endTime - startTime

  val isComplete: Boolean
    get() = endTime > 0

  val successRate: Float
    get() = if (totalFiles > 0) successCount.toFloat() / totalFiles else 0f
}

data class CopiedFile(
    val sourceFile: ImageFile,
    val destinationPath: String,
    val hashVerified: Boolean = false,
    val hashMatches: Boolean = false
)

data class ImportError(
    val file: ImageFile,
    val errorType: ErrorType,
    val message: String,
    val retryable: Boolean = false
)

enum class ErrorType {
  FILE_NOT_FOUND,
  PERMISSION_DENIED,
  HASH_MISMATCH,
  DISK_FULL,
  SOURCE_UNMOUNTED,
  DESTINATION_UNMOUNTED,
  CORRUPT_FILE,
  UNSUPPORTED_FORMAT,
  METADATA_ERROR,
  UNKNOWN
}

data class ImportProgress(
    val currentFile: String = "",
    val currentIndex: Int = 0,
    val totalFiles: Int = 0,
    val copiedBytes: Long = 0,
    val totalBytes: Long = 0,
    val status: ImportStatus = ImportStatus.PENDING,
    val currentFileHash: String? = null,
    val verifiedFileHash: String? = null,
    val errors: List<ImportError> = emptyList()
) {
  val progressPercent: Float
    get() = if (totalFiles > 0) currentIndex.toFloat() / totalFiles else 0f

  val bytesProgressPercent: Float
    get() = if (totalBytes > 0) copiedBytes.toFloat() / totalBytes else 0f
}
