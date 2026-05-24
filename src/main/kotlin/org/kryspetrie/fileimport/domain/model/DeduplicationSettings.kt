package org.kryspetrie.fileimport.domain.model

import kotlinx.serialization.Serializable

/**
 * Dedicated settings for duplicate detection and resolution.
 *
 * Separate from [ImportConfiguration] to allow fine-tuning duplicate detection independently from
 * import settings. Used primarily in the Duplicate Scanner screen.
 *
 * @property enableHashDeduplication Detect exact duplicates by file hash
 * @property enablePerceptualHash Detect visual duplicates by perceptual hash
 * @property enableExifDeduplication Detect duplicates by EXIF metadata
 * @property enableFilenameDeduplication Detect duplicates by filename only
 * @property ignoreDifferentFileTypes Don't match across different file types
 * @property perceptualHashThreshold Similarity threshold for pHash matching
 * @property autoResolveDuplicates Automatically select which duplicate to keep
 * @property enableSurfMatching Enable SURF feature matching
 * @property surfMatchThreshold SURF match threshold
 * @see ImportConfiguration Main import configuration
 */
@Serializable
data class DeduplicationSettings(
    val enableHashDeduplication: Boolean = true,
    val enablePerceptualHash: Boolean = true,
    val enableExifDeduplication: Boolean = true,
    val enableFilenameDeduplication: Boolean = false,
    val ignoreDifferentFileTypes: Boolean = true,
    val perceptualHashThreshold: Float = 0.95f,
    val autoResolveDuplicates: Boolean = false,
    val enableSurfMatching: Boolean = false,
    val surfMatchThreshold: Int = 30,
)
