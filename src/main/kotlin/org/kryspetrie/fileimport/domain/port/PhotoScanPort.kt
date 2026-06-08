package org.kryspetrie.fileimport.domain.port

import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.PhotoScanExportResult
import org.kryspetrie.fileimport.domain.model.PhotoScanSingleExportResult
import org.kryspetrie.fileimport.domain.model.ProcessedImage

/**
 * Port interface for photo detection in scanned images.
 *
 * This port defines the contract for detecting individual photo boundaries within an image
 * containing multiple photos (e.g., scanned from a flatbed scanner or photographed from a photo
 * album page).
 *
 * ## Implementation
 *
 * Implementations may use various techniques:
 * - Classical computer vision (edge detection, contour analysis)
 * - Machine learning (YOLO, CNN-based detection)
 * - Hybrid approaches combining classical CV with ML refinement
 *
 * ## Usage
 *
 * ```kotlin
 * val detector: PhotoScanDetectorPort = koinInject()
 * val image: ProcessedImage = bufferedImage.toProcessedImage()
 * val detectedPhotos = detector.detectPhotos(image)
 * ```
 *
 * @see DetectedPhoto The detected photo result containing corner coordinates
 */
interface PhotoScanDetectorPort {

    /**
     * Detects rectangular photo regions within a scanned image.
     *
     * @param image The scanned image to analyze as a [ProcessedImage].
     * @return List of [DetectedPhoto] objects representing each photo found. Returns empty list if
     *   no photos detected.
     * @throws Exception If detection fails due to processing errors
     */
    fun detectPhotos(image: ProcessedImage): List<DetectedPhoto>
}

/**
 * Port interface for photo export with correction and metadata handling.
 *
 * @see PhotoScanExportService Default implementation with EXIF support
 * @see PhotoScanExportResult Result of batch export
 * @see PhotoScanSingleExportResult Result of single export
 */
interface PhotoScanExportPort {

    /**
     * Exports a single photo with optional corrections and EXIF metadata preservation.
     *
     * @param sourceImage The source scanned image
     * @param detectedPhoto The photo to export with corner positions and correction settings
     * @param destinationPath Folder where the exported photo will be saved
     * @param baseFileName Base filename (without extension) for the output
     * @param sourceFile The original source file path for EXIF baseline reading. May be null.
     * @return [PhotoScanSingleExportResult] with success status, path, and dimensions
     */
    fun exportSinglePhoto(
        sourceImage: ProcessedImage,
        detectedPhoto: DetectedPhoto,
        destinationPath: String,
        baseFileName: String,
        sourceFile: FilePath? = null,
    ): PhotoScanSingleExportResult

    /**
     * Exports multiple photos from a single source image.
     *
     * @param sourceFile Path to the original scanned image file (used for EXIF metadata extraction)
     * @param image The source scanned image
     * @param detectedPhotos List of photos to export with their configurations
     * @param destinationPath Destination folder for exported images
     * @param baseFileName Base filename for exported images (will have _1, _2 suffixes)
     * @return [PhotoScanExportResult] with success status, files, and errors
     */
    fun exportPhotos(
        sourceFile: FilePath,
        image: ProcessedImage,
        detectedPhotos: List<DetectedPhoto>,
        destinationPath: String,
        baseFileName: String,
    ): PhotoScanExportResult
}
