package org.kryspetrie.fileimport.domain.model

import kotlinx.serialization.Serializable

/** How loaded images are displayed in the metadata editor file browser. */
@Serializable
enum class MetadataEditorFileViewMode {
    /** Multi-column Finder-style browser. */
    COLUMN,

    /** Flat filename list. */
    LIST,

    /** Tree grouped by folder hierarchy. */
    HIERARCHY,

    /** Thumbnail grid (compact strip or wider grid). */
    ICONS,
}
