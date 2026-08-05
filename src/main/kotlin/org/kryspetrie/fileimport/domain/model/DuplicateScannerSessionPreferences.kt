package org.kryspetrie.fileimport.domain.model

import kotlinx.serialization.Serializable

/** Persisted session state for the Duplicate Scanner tab (survives app restart). */
@Serializable
data class DuplicateScannerSessionPreferences(
    val folderPath: String = "",
    val enableHash: Boolean = true,
    val enableExif: Boolean = true,
    val enableSurf: Boolean = false,
    /** [DuplicateAction] name. */
    val resolveAction: String = DuplicateAction.KEEP_HIGHEST_RES.name,
    val moveToTrash: Boolean = true,
) {
    fun resolvedResolveAction(): DuplicateAction =
        runCatching { DuplicateAction.valueOf(resolveAction) }
            .getOrDefault(DuplicateAction.KEEP_HIGHEST_RES)
}
