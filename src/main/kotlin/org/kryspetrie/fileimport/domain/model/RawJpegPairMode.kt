package org.kryspetrie.fileimport.domain.model

import kotlinx.serialization.Serializable

/**
 * RAW+JPEG pair handling mode.
 *
 * Many cameras can save both RAW and JPEG versions of each photo. This enum determines which files
 * to import:
 * - **IMPORT_BOTH**: Import both RAW and JPEG (maximum flexibility)
 * - **RAW_ONLY**: Import only RAW files, skip JPEGs
 * - **JPEG_ONLY**: Import only JPEG files, skip RAWs
 *
 * Pairs are detected by matching base filename and timestamp.
 *
 * @see ImportConfiguration.rawJpegPairMode
 */
@Serializable
enum class RawJpegPairMode {
    IMPORT_BOTH,
    RAW_ONLY,
    JPEG_ONLY,
}
