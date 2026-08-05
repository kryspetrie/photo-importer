package org.kryspetrie.fileimport.domain.model

import kotlinx.serialization.Serializable

/** Persisted session preferences for the bulk metadata editor (survives app restart). */
@Serializable
data class MetadataEditorSessionPreferences(
    /** Output mode name: OVERWRITE or SAVE_NEW. */
    val outputMode: String = "OVERWRITE",
    /** Output directory when [outputMode] is SAVE_NEW. */
    val outputDirectory: String = "",
    /** Whether folder loads include subdirectories. */
    val includeSubfolders: Boolean = false,
) {
    /** Returns [outputMode] when valid, otherwise OVERWRITE. */
    fun normalizedOutputMode(): String =
        outputMode.takeIf { it == "OVERWRITE" || it == "SAVE_NEW" } ?: "OVERWRITE"
}
