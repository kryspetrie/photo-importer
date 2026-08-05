package org.kryspetrie.fileimport.domain.model

import kotlinx.serialization.Serializable

/** Persisted UI state for the Media Import tab (survives app restart). */
@Serializable
data class MediaImportSessionPreferences(
    val settingsExpanded: Boolean = false,
    val historyExpanded: Boolean = false,
)
