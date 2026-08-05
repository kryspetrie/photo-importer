package org.kryspetrie.fileimport.domain.model

import kotlinx.serialization.Serializable

/** Persisted layout preferences for the bulk metadata editor workstation. */
@Serializable
data class MetadataEditorLayoutPreferences(
    /** Width of the file browser pane in dp (user-resizable). */
    val browserPaneWidthDp: Int = 280,
    /** Relative weight of preview pane vs metadata pane (0.0–1.0). */
    val previewPaneWeight: Float = 0.55f,
    /** Whether the file browser drawer is open on narrow layouts. */
    val browserDrawerOpen: Boolean = true,
) {
    fun withBrowserPaneWidthDp(width: Int): MetadataEditorLayoutPreferences =
        copy(browserPaneWidthDp = width.coerceIn(160, 600))

    fun withPreviewPaneWeight(weight: Float): MetadataEditorLayoutPreferences =
        copy(previewPaneWeight = weight.coerceIn(0.35f, 0.75f))

    fun withBrowserDrawerOpen(open: Boolean): MetadataEditorLayoutPreferences =
        copy(browserDrawerOpen = open)
}
