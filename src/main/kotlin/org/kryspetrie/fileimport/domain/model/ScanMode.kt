package org.kryspetrie.fileimport.domain.model

/**
 * Scan mode for import operations.
 *
 * Determines how files are scanned and processed during import:
 * - [STANDARD]: Traditional import with duplicate detection, normal metadata extraction
 * - [PHOTO_SCAN]: Specialized mode for scanning photos on background - detects corners and splits
 *   multiple photos from single image files
 */
enum class ScanMode {
    /** Standard import mode with duplicate detection */
    STANDARD,

    /** Photo scan mode - detects corners of photos on background, splits multiple photos */
    PHOTO_SCAN,
}
