package org.kryspetrie.fileimport.domain.model

import kotlinx.serialization.Serializable

/** Persisted session state for the Reorganize tab (survives app restart). */
@Serializable
data class ReorganizeSessionPreferences(
    val folderPath: String = "",
    val configuration: ImportConfiguration = ImportConfiguration(),
    val renameOnly: Boolean = false,
    /** [ReorganizeMode] name (MOVE or COPY). */
    val reorgMode: String = ReorganizeMode.MOVE.name,
    val settingsExpanded: Boolean = false,
) {
    fun resolvedReorgMode(): ReorganizeMode =
        runCatching { ReorganizeMode.valueOf(reorgMode) }.getOrDefault(ReorganizeMode.MOVE)
}
