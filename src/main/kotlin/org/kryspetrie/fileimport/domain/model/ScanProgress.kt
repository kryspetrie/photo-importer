package org.kryspetrie.fileimport.domain.model

/** Progress state for duplicate scanning operations. */
data class ScanProgress(
    val phase: String = "",
    val current: Int = 0,
    val total: Int = 0,
    val currentFile: String = "",
)
