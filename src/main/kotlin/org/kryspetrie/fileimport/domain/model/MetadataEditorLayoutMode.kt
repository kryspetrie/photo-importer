package org.kryspetrie.fileimport.domain.model

import kotlinx.serialization.Serializable

/** Alternate layouts for the bulk metadata editor. */
@Serializable
enum class MetadataEditorLayoutMode {
    /** Scrollable thumbnail strip on the left (default). */
    SIDEBAR,

    /** Compact preview with a native file picker and filename list. */
    FILE_PICKER,
}
