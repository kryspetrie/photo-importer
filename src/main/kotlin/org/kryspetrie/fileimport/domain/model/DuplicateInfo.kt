package org.kryspetrie.fileimport.domain.model

data class DuplicateInfo(
    val primaryImage: ImageFile,
    val duplicateImages: List<ImageFile>,
    val duplicateType: DuplicateType,
    val similarityScore: Float? = null,
    val hashMatch: Boolean = false,
    val exifMatch: Boolean = false,
    val fileTypeMatch: Boolean = false,
)

enum class DuplicateType {
    EXACT_HASH,
    PERCEPTUAL_HASH,
    EXIF_MATCH,
    FILENAME_SIMILAR,
    CAMERA_PAIR,
    SURF_MATCH,
}

enum class DuplicateResolution {
    KEEP_BOTH,
    SKIP_DUPLICATE,
    REPLACE_PRIMARY,
}
