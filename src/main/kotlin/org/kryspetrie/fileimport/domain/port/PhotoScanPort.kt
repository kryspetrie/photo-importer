package org.kryspetrie.fileimport.domain.port

import java.awt.image.BufferedImage
import org.kryspetrie.fileimport.application.PhotoScanExportService
import org.kryspetrie.fileimport.domain.model.DetectedPhoto

/**
 * Port interface for photo detection in scanned images.
 *
 * This port defines the contract for detecting individual photo boundaries within an image
 * containing multiple photos (e.g., scanned from a flatbed scanner or photographed from
 * a photo album page).
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
 * val image = ImageIO.read(File("scan.jpg"))
 * val detectedPhotos = detector.detectPhotos(image)
 *
 * for (photo in detectedPhotos) {
 *     println("Photo at: ${photo.topLeft}, ${photo.bottomRight}")
 * }
 * ```
 *
 * @see PhotoScanDetectorService Default implementation using classical CV
 * @see DetectedPhoto The detected photo result containing corner coordinates
 */
interface PhotoScanDetectorPort {

  /**
   * Detects rectangular photo regions within a scanned image.
   *
   * The detection process should:
   * 1. Estimate the background color (desk/surface)
   * 2. Find content regions differing from the background
   * 3. Filter to rectangular regions matching expected photo dimensions
   * 4. Return corners ordered: top-left → top-right → bottom-right → bottom-left
   *
   * @param image The scanned image to analyze. Must be a valid, non-null BufferedImage.
   * @return List of [DetectedPhoto] objects representing each photo found.
   *         Returns empty list if no photos detected.
   * @throws IllegalArgumentException If image is null or has invalid dimensions
   * @throws Exception If detection fails due to processing errors
   */
  fun detectPhotos(image: BufferedImage): List<DetectedPhoto>
}

/**
 * Port interface for photo export with correction and metadata handling.
 *
 * This port defines the contract for:
 * - Cropping photos from source images
 * - Applying perspective correction
 * - Applying rotation transforms
 * - Writing output with EXIF metadata
 * - Handling filename conflicts
 *
 * ## Implementation
 *
 * Implementations handle:
 * - Perspective transform via 4-point mapping
 * - Rotation with proper bounding box expansion
 * - EXIF metadata reading and writing
 * - Incremental filename generation to avoid conflicts
 *
 * ## Usage
 *
 * ```kotlin
 * val exporter: PhotoScanExportPort = koinInject()
 * val result = exporter.exportSinglePhoto(
 *     sourceImage = scannedImage,
 *     detectedPhoto = detectedPhoto,
 *     destinationPath = "/photos/scanned",
 *     baseFileName = "vacation_001"
 * )
 * println("Exported to: ${result.destinationPath}")
 * ```
 *
 * @see PhotoScanExportService Default implementation with EXIF support
 * @see DetectedPhoto The photo configuration with corners and corrections
 * @see PhotoScanExportService.ExportResult Result of batch export
 * @see PhotoScanExportService.SingleExportResult Result of single export
 */
interface PhotoScanExportPort {

  /**
   * Exports a single photo with optional corrections.
   *
   * This is the primary method for exporting individual photos from a scanned image.
   * It handles:
   * - Axis-aligned cropping or perspective correction
   * - Rotation (CW_90, CCW_90, CW_180)
   * - Filename conflict resolution
   * - Writing to destination folder
   *
   * @param sourceImage The source scanned image containing the photo
   * @param detectedPhoto The photo to export with corner positions and correction settings
   * @param destinationPath Folder where the exported photo will be saved
   * @param baseFileName Base filename (without extension) for the output
   * @return [PhotoScanExportService.SingleExportResult] with success status, path, and dimensions
   * @throws Exception If export fails due to processing errors or IO issues
   */
  fun exportSinglePhoto(
      sourceImage: BufferedImage,
      detectedPhoto: DetectedPhoto,
      destinationPath: String,
      baseFileName: String
  ): PhotoScanExportService.SingleExportResult

  /**
   * Exports multiple photos from a single source image.
   *
   * This method is optimized for batch export of multiple photos with a single
   * source image. It processes each photo sequentially and handles filename conflicts
   * across the entire batch.
   *
   * @param sourceFile Original scanned image file (used for EXIF metadata extraction)
   * @param image The source scanned image
   * @param detectedPhotos List of photos to export with their configurations
   * @param destinationPath Destination folder for exported images
   * @param baseFileName Base filename for exported images (will have _1, _2 suffixes)
   * @return [PhotoScanExportService.ExportResult] with success status, files, and errors
   * @throws Exception If batch export fails catastrophically
   */
  fun exportPhotos(
      sourceFile: java.io.File,
      image: BufferedImage,
      detectedPhotos: List<DetectedPhoto>,
      destinationPath: String,
      baseFileName: String
  ): PhotoScanExportService.ExportResult
}