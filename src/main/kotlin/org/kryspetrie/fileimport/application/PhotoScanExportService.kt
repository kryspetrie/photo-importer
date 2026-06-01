package org.kryspetrie.fileimport.application

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.plugins.jpeg.JPEGImageWriteParam
import javax.inject.Inject
import javax.inject.Singleton
import org.apache.commons.imaging.Imaging
import org.apache.commons.imaging.common.RationalNumber
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata
import org.apache.commons.imaging.formats.jpeg.JpegPhotoshopMetadata
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter
import org.apache.commons.imaging.formats.jpeg.iptc.IptcConstants
import org.apache.commons.imaging.formats.jpeg.iptc.IptcRecord
import org.apache.commons.imaging.formats.jpeg.iptc.IptcTypes
import org.apache.commons.imaging.formats.jpeg.iptc.JpegIptcRewriter
import org.apache.commons.imaging.formats.jpeg.iptc.PhotoshopApp13Data
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants
import org.apache.commons.imaging.formats.tiff.constants.MicrosoftTagConstants
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants
import org.apache.commons.imaging.formats.tiff.write.TiffOutputDirectory
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet
import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.PhotoCorner
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.domain.model.PhotoScanExportResult
import org.kryspetrie.fileimport.domain.model.PhotoScanExportedFile
import org.kryspetrie.fileimport.domain.model.PhotoScanSingleExportResult
import org.kryspetrie.fileimport.domain.model.RotationAngle

/**
 * Service for exporting extracted photos with EXIF metadata preservation and modification.
 *
 * Handles the complete export pipeline:
 * 1. Perspective correction of the extracted photo
 * 2. EXIF metadata extraction and preservation
 * 3. EXIF metadata modification (date, tags, notes override)
 * 4. Incremental filename generation to avoid conflicts
 * 5. Writing the final image with metadata
 *
 * ## Filename Conflict Resolution
 *
 * When a filename already exists in the destination:
 * ```
 * photo.jpg          → photo_1.jpg
 * photo_1.jpg       → photo_2.jpg
 * photo_2.jpg       → photo_3.jpg
 * ```
 *
 * @see DetectedPhoto
 * @see PhotoScanConfiguration
 * @see PhotoCorner
 */
@Singleton
class PhotoScanExportService
@Inject
constructor(private val perspectiveService: PerspectiveCorrectionService) {

    /** JPEG quality for output images (0.0 - 1.0). */
    var jpegQuality = 0.95f

    // Type aliases for backward compatibility — actual types now live in domain/model
    // to fix hexagonal architecture violation (domain port must not reference application types)
    typealias ExportResult = PhotoScanExportResult

    typealias SingleExportResult = PhotoScanSingleExportResult

    typealias ExportedFile = PhotoScanExportedFile

    /**
     * Exports all detected photos from a scanned image.
     *
     * @param sourceFile Original scanned image file
     * @param image Original scanned image
     * @param detectedPhotos List of detected photos with their configurations
     * @param destinationPath Destination folder for exported images
     * @param baseFileName Base filename (without extension) for exported images
     * @param marginFraction Margin as fraction of photo diagonal (0.0–0.2, default 0.02). Pushes
     *   corners outward from quad center for warp, or expands bounding box for simple crop.
     * @return ExportResult with success status and exported file information
     */
    fun exportPhotos(
        sourceFile: File,
        image: BufferedImage,
        detectedPhotos: List<DetectedPhoto>,
        destinationPath: String,
        baseFileName: String,
        marginFraction: Double = 0.02,
    ): ExportResult {
        val errors = mutableListOf<String>()
        val exportedFiles = mutableListOf<ExportedFile>()

        // Validate source file exists before proceeding
        if (!sourceFile.exists()) {
            errors.add("Source file does not exist: ${sourceFile.absolutePath}")
            return ExportResult(success = false, errors = errors)
        }

        for ((index, photo) in detectedPhotos.withIndex()) {
            try {
                // Apply margin to corners before processing
                val marginedPhoto = applyMargin(photo, marginFraction)

                // Crop and correct the image based on photo settings
                val correctedImage =
                    if (marginedPhoto.applyPerspectiveCorrection) {
                        // Apply perspective correction
                        perspectiveService.correctPerspective(image, marginedPhoto)
                    } else {
                        // Simple axis-aligned crop
                        cropAxisAligned(image, marginedPhoto)
                    }

                // Apply rotation if needed
                val finalImage =
                    if (photo.rotation != RotationAngle.NONE) {
                        rotateImage(correctedImage, photo.rotation)
                    } else {
                        correctedImage
                    }

                // Generate filename with index if multiple photos
                val fileName =
                    if (detectedPhotos.size > 1) {
                        "${baseFileName}_${index + 1}.jpg"
                    } else {
                        "${baseFileName}.jpg"
                    }

                // Resolve filename conflicts
                val resolvedPath = resolveFilenameConflict(File(destinationPath), fileName)
                val outputFile = File(resolvedPath)

                // Write the image with metadata — pass source file for EXIF baseline reading
                writeImageWithMetadata(finalImage, outputFile, photo.configuration, sourceFile)

                exportedFiles.add(
                    ExportedFile(
                        sourceFile = sourceFile,
                        destinationPath = resolvedPath,
                        photoId = photo.id,
                        width = finalImage.width,
                        height = finalImage.height,
                        fileSize = outputFile.length(),
                    )
                )
            } catch (e: Exception) {
                errors.add("Failed to export photo ${index + 1}: ${e.message}")
            }
        }

        return ExportResult(
            success = errors.isEmpty(),
            exportedFiles = exportedFiles,
            errors = errors,
        )
    }

    /**
     * Exports a single photo with the given configuration.
     *
     * @param sourceImage The source scanned image
     * @param sourceFile The original source file (for EXIF metadata reading). May be null if unavailable.
     * @param detectedPhoto The photo to export with corner positions and configuration
     * @param destinationPath Destination folder for the exported image
     * @param baseFileName Base filename (without extension)
     * @param marginFraction Margin as fraction of photo diagonal (0.0–0.2, default 0.02)
     * @return SingleExportResult with the result of the export
     */
    fun exportSinglePhoto(
        sourceImage: BufferedImage,
        detectedPhoto: DetectedPhoto,
        destinationPath: String,
        baseFileName: String,
        sourceFile: File? = null,
        marginFraction: Double = 0.02,
    ): SingleExportResult {
        return try {
            // Apply margin to corners before processing
            val marginedPhoto = applyMargin(detectedPhoto, marginFraction)

            // Crop and correct the image based on photo settings
            val correctedImage =
                if (marginedPhoto.applyPerspectiveCorrection) {
                    perspectiveService.correctPerspective(sourceImage, marginedPhoto)
                } else {
                    cropAxisAligned(sourceImage, marginedPhoto)
                }

            // Apply rotation if needed
            val finalImage =
                if (detectedPhoto.rotation != RotationAngle.NONE) {
                    rotateImage(correctedImage, detectedPhoto.rotation)
                } else {
                    correctedImage
                }

            // Resolve filename conflicts
            val resolvedPath = resolveFilenameConflict(File(destinationPath), "$baseFileName.jpg")
            val outputFile = File(resolvedPath)

            // Write the image with metadata — pass source file for EXIF baseline reading
            writeImageWithMetadata(finalImage, outputFile, detectedPhoto.configuration, sourceFile)

            SingleExportResult(
                success = true,
                destinationPath = resolvedPath,
                width = finalImage.width,
                height = finalImage.height,
            )
        } catch (e: Exception) {
            SingleExportResult(
                success = false,
                destinationPath = "",
                width = 0,
                height = 0,
                error = e.message,
            )
        }
    }

    /**
     * Writes an image to file with EXIF and IPTC metadata.
     *
     * Writes the plain JPEG first, then layers on metadata:
     * 1. EXIF (via [ExifRewriter]): Either copies existing EXIF from [sourceFile] and overlays
     *    overrides, or starts fresh depending on [PhotoScanConfiguration.copyOriginalExif].
     * 2. IPTC (via [JpegIptcRewriter]): Adds IPTC keywords for cross-platform/metadata compatibility.
     *
     * @param image The corrected image
     * @param outputFile Destination file
     * @param config Photo configuration with metadata overrides and copyOriginalExif setting
     * @param sourceFile The original scanned image file (for reading existing EXIF when copyOriginalExif=true)
     */
    private fun writeImageWithMetadata(
        image: BufferedImage,
        outputFile: File,
        config: PhotoScanConfiguration,
        sourceFile: File? = null,
    ) {
        outputFile.parentFile?.mkdirs()

        // First, write the plain JPEG image
        val writer = ImageIO.getImageWritersByFormatName("jpg").next()
        val writeParam =
            JPEGImageWriteParam(Locale.US).apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = jpegQuality
            }

        val fileOs = ImageIO.createImageOutputStream(outputFile)
        fileOs.use {
            writer.output = it
            writer.write(null, IIOImage(image, null, null), writeParam)
        }
        writer.dispose()

        // If there are EXIF overrides or we need to force a fresh write, inject the EXIF
        if (config.hasExifOverrides()) {
            writeExifMetadata(outputFile, config, sourceFile)
        }

        // Write IPTC keywords if keywords are set (cross-platform, visible on macOS)
        val keywordsValue = resolveKeywords(config)
        if (keywordsValue != null) {
            writeIptcKeywords(outputFile, keywordsValue)
        }
    }

    /**
     * Writes EXIF metadata into an existing JPEG file.
     *
     * Baseline strategy depends on [PhotoScanConfiguration.copyOriginalExif]:
     * - **true** (default): Reads existing EXIF from [sourceFile] (the original scan) as a baseline,
     *   then applies user overrides on top. This preserves scanner EXIF (make, model, scan date, etc.)
     *   and only replaces the fields the user explicitly sets.
     * - **false**: Starts with a fresh (empty) [TiffOutputSet] — only user-specified overrides
     *   are written, and no scanner/source EXIF is carried forward.
     *
     * The output file is rewritten in-place with the updated EXIF.
     *
     * @param jpegFile The JPEG file to rewrite with EXIF data (modified in-place)
     * @param config Configuration with EXIF override values and the copyOriginalExif flag
     * @param sourceFile The original source file to read baseline EXIF from (may be null)
     */
    private fun writeExifMetadata(
        jpegFile: File,
        config: PhotoScanConfiguration,
        sourceFile: File?,
    ) {
        try {
            // Determine the baseline EXIF output set
            val outputSet = if (config.copyOriginalExif) {
                // Copy original EXIF from source file (or fall back to the just-written JPEG)
                readExifOutputSet(sourceFile ?: jpegFile)
            } else {
                // Start fresh — no original EXIF is carried forward
                TiffOutputSet()
            }

            // Apply user overrides on top of the baseline
            applyExifOverrides(outputSet, config)

            // Read the JPEG bytes we just wrote, then rewrite with the EXIF output set
            val jpegBytes = jpegFile.readBytes()
            val baos = ByteArrayOutputStream()
            ExifRewriter().updateExifMetadataLossless(jpegBytes, baos, outputSet)
            baos.close()

            // Write the updated JPEG back to the file
            FileOutputStream(jpegFile).use { fos ->
                fos.write(baos.toByteArray())
            }
        } catch (e: Exception) {
            // EXIF writing is best-effort — if it fails, the JPEG is still valid without EXIF overrides
            System.err.println("[PhotoScanExportService] Warning: Failed to write EXIF metadata: ${e.message}")
        }
    }

    /**
     * Reads EXIF metadata from a file and returns a mutable [TiffOutputSet].
     * If the file has no EXIF data, returns a fresh (empty) output set.
     *
     * @param file The source image file to read EXIF from
     * @return A TiffOutputSet suitable for modification
     */
    private fun readExifOutputSet(file: File): TiffOutputSet {
        return try {
            val metadata = Imaging.getMetadata(file)
            if (metadata is JpegImageMetadata) {
                val exif = metadata.exif
                exif?.outputSet ?: TiffOutputSet()
            } else {
                TiffOutputSet()
            }
        } catch (_: Exception) {
            TiffOutputSet()
        }
    }

    /**
     * Applies EXIF override values from [config] to the [outputSet].
     *
     * Only non-null/non-empty fields are applied; null fields preserve the original EXIF values.
     *
     * @param outputSet The TiffOutputSet to modify
     * @param config Configuration with EXIF override values
     */
    private fun applyExifOverrides(outputSet: TiffOutputSet, config: PhotoScanConfiguration) {
        try {
            // Ensure required directories exist
            val rootDir = outputSet.getOrCreateRootDirectory()
            val exifDir = outputSet.getOrCreateExifDirectory()

            // --- IFD0 (root) tags ---

            // ImageDescription (0x010E)
            val description = config.description ?: config.notes.ifBlank { null }
            if (!description.isNullOrBlank()) {
                rootDir.removeField(TiffTagConstants.TIFF_TAG_IMAGE_DESCRIPTION)
                rootDir.add(TiffTagConstants.TIFF_TAG_IMAGE_DESCRIPTION, description)
            }

            // Make (0x010F)
            if (!config.cameraMake.isNullOrBlank()) {
                rootDir.removeField(TiffTagConstants.TIFF_TAG_MAKE)
                rootDir.add(TiffTagConstants.TIFF_TAG_MAKE, config.cameraMake)
            }

            // Model (0x0110)
            if (!config.cameraModel.isNullOrBlank()) {
                rootDir.removeField(TiffTagConstants.TIFF_TAG_MODEL)
                rootDir.add(TiffTagConstants.TIFF_TAG_MODEL, config.cameraModel)
            }

            // --- Exif SubIFD tags ---

            // DateTimeOriginal (0x9003) — format: "YYYY:MM:DD HH:MM:SS"
            val dateOriginal = resolveDateOriginal(config)
            if (dateOriginal != null) {
                exifDir.removeField(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL)
                exifDir.add(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL, dateOriginal)
                // Also set DateTimeDigitized (0x9004) to match
                exifDir.removeField(ExifTagConstants.EXIF_TAG_DATE_TIME_DIGITIZED)
                exifDir.add(ExifTagConstants.EXIF_TAG_DATE_TIME_DIGITIZED, dateOriginal)
            }

            // LensModel (0xA434)
            if (!config.lensModel.isNullOrBlank()) {
                exifDir.removeField(ExifTagConstants.EXIF_TAG_LENS_MODEL)
                exifDir.add(ExifTagConstants.EXIF_TAG_LENS_MODEL, config.lensModel)
            }

            // FocalLength (0x920A) — stored as RationalNumber in mm
            if (!config.focalLength.isNullOrBlank()) {
                val focalLengthMm = parseFocalLength(config.focalLength)
                if (focalLengthMm != null) {
                    exifDir.removeField(ExifTagConstants.EXIF_TAG_FOCAL_LENGTH)
                    val rational = RationalNumber.valueOf(focalLengthMm)
                    exifDir.add(ExifTagConstants.EXIF_TAG_FOCAL_LENGTH, rational)
                }
            }

            // FNumber (0x829D) — stored as RationalNumber (aperture value)
            if (!config.aperture.isNullOrBlank()) {
                val fNumber = parseAperture(config.aperture)
                if (fNumber != null) {
                    exifDir.removeField(ExifTagConstants.EXIF_TAG_FNUMBER)
                    val rational = RationalNumber.valueOf(fNumber)
                    exifDir.add(ExifTagConstants.EXIF_TAG_FNUMBER, rational)
                }
            }

            // ExposureTime (0x829A) — stored as RationalNumber (e.g. 1/125)
            if (!config.shutterSpeed.isNullOrBlank()) {
                val rational = parseShutterSpeed(config.shutterSpeed)
                if (rational != null) {
                    exifDir.removeField(ExifTagConstants.EXIF_TAG_EXPOSURE_TIME)
                    exifDir.add(ExifTagConstants.EXIF_TAG_EXPOSURE_TIME, rational)
                }
            }

            // ISOSpeedRatings (0x8827) — stored as Short
            if (!config.iso.isNullOrBlank()) {
                val isoValue = config.iso.trim().toIntOrNull()
                if (isoValue != null) {
                    exifDir.removeField(ExifTagConstants.EXIF_TAG_ISO)
                    exifDir.add(ExifTagConstants.EXIF_TAG_ISO, isoValue.toShort())
                }
            }

            // XPKeywords (0x9C9D) — Windows keyword tag, stored as XP String (UTF-16LE)
            // NOTE: XPKeywords is only read by Windows. macOS reads IPTC:Keywords instead,
            // which we write separately in writeIptcKeywords(). We write both for cross-platform compat.
            val exifKeywordsValue = resolveKeywords(config)
            if (exifKeywordsValue != null) {
                rootDir.removeField(MicrosoftTagConstants.EXIF_TAG_XPKEYWORDS)
                rootDir.add(MicrosoftTagConstants.EXIF_TAG_XPKEYWORDS, exifKeywordsValue)
            }
        } catch (e: Exception) {
            // Best effort — don't fail the export if EXIF writing has issues
            System.err.println("[PhotoScanExportService] Warning: Error applying EXIF overrides: ${e.message}")
        }
    }

    /**
     * Writes IPTC keywords into an existing JPEG file using the Photoshop APP13 segment.
     *
     * IPTC:Keywords (record 2, dataset 25) is the cross-platform standard for keywords that
     * macOS Preview, Photos.app, and Finder all read. This supplements [MicrosoftTagConstants.EXIF_TAG_XPKEYWORDS]
     * which is Windows-only.
     *
     * If the JPEG already has IPTC data (e.g. from Photoshop), existing records are preserved
     * and only the KEYWORDS records are replaced. Duplicate KEYWORDS records are removed first.
     *
     * @param jpegFile The JPEG file to rewrite with IPTC data (modified in-place)
     * @param keywords Comma-separated keyword string (e.g. "vacation, beach, family")
     */
    private fun writeIptcKeywords(jpegFile: File, keywords: String) {
        try {
            val keywordList = keywords.split(",").map { it.trim() }.filter { it.isNotBlank() }

            // Read existing IPTC data from the JPEG if present
            val metadata = Imaging.getMetadata(jpegFile)
            val existingRecords = if (metadata is JpegImageMetadata) {
                val photoshop = metadata.photoshop
                photoshop?.photoshopApp13Data?.records?.filterNotNull()?.toMutableList()
                    ?: mutableListOf()
            } else {
                mutableListOf()
            }

            // Remove any existing KEYWORDS records (we'll replace them)
            existingRecords.removeAll { it.iptcType == IptcTypes.KEYWORDS }

            // Add new KEYWORDS records (one per keyword, per IPTC spec)
            for (keyword in keywordList) {
                existingRecords.add(IptcRecord(IptcTypes.KEYWORDS, keyword))
            }

            // Also add a DATE_CREATED record if not present and we can derive one
            // (IPTC DateCreated is tag 2:30, separate from EXIF DateTimeOriginal)

            // Build the Photoshop APP13 data block
            val existingBlocks = if (metadata is JpegImageMetadata) {
                metadata.photoshop?.photoshopApp13Data?.nonIptcBlocks?.filterNotNull()
                    ?: emptyList()
            } else {
                emptyList()
            }
            val app13Data = PhotoshopApp13Data(existingRecords, existingBlocks)

            // Rewrite the JPEG with the new IPTC data
            val jpegBytes = jpegFile.readBytes()
            val baos = ByteArrayOutputStream()
            JpegIptcRewriter().writeIPTC(jpegBytes, baos, app13Data)
            baos.close()

            FileOutputStream(jpegFile).use { fos ->
                fos.write(baos.toByteArray())
            }
        } catch (e: Exception) {
            // IPTC writing is best-effort — don't fail the export
            System.err.println("[PhotoScanExportService] Warning: Failed to write IPTC keywords: ${e.message}")
        }
    }

    /**
     * Resolves the keywords value from configuration, checking both the new [keywords] field
     * and the legacy [tags] field.
     *
     * @return The resolved comma-separated keyword string, or null if no keywords set
     */
    private fun resolveKeywords(config: PhotoScanConfiguration): String? {
        return if (!config.keywords.isNullOrBlank()) {
            config.keywords
        } else if (config.tags.isNotBlank()) {
            config.tags
        } else {
            null
        }
    }

    /**
     * Resolves the DateTimeOriginal string from configuration fields.
     *
     * Priority: [originalDate] > [originalDateOverride] > derived from [year] only.
     * Format returned: "YYYY:MM:DD HH:MM:SS" (EXIF format, colons in date part).
     *
     * @return EXIF-formatted date string, or null if no date override
     */
    private fun resolveDateOriginal(config: PhotoScanConfiguration): String? {
        // originalDate takes priority (full date string)
        if (!config.originalDate.isNullOrBlank()) {
            return formatDateToExif(config.originalDate)
        }
        // Legacy originalDateOverride field
        if (!config.originalDateOverride.isNullOrBlank()) {
            return formatDateToExif(config.originalDateOverride)
        }
        // Year-only override
        if (!config.year.isNullOrBlank()) {
            return "${config.year}:01:01 00:00:00"
        }
        if (!config.originalYearOverride.isNullOrBlank()) {
            return "${config.originalYearOverride}:01:01 00:00:00"
        }
        return null
    }

    /**
     * Converts a date string to EXIF format.
     *
     * Accepts "YYYY-MM-DD HH:MM:SS" or "YYYY-MM-DD" and converts to "YYYY:MM:DD HH:MM:SS".
     */
    private fun formatDateToExif(dateStr: String): String {
        // Normalize: replace dashes in the date portion with colons
        val parts = dateStr.trim().split(" ", limit = 2)
        val datePart = parts[0].replace("-", ":")
        val timePart = parts.getOrElse(1) { "00:00:00" }
        return "$datePart $timePart"
    }

    /**
     * Parses a focal length string (e.g. "50mm", "50", "24mm") to a floating-point value in mm.
     */
    private fun parseFocalLength(value: String): Double? {
        return value.trim().removeSuffix("mm").removeSuffix("MM").trim().toDoubleOrNull()
    }

    /**
     * Parses an aperture string (e.g. "f/2.8", "2.8", "F2.8") to a floating-point f-number.
     */
    private fun parseAperture(value: String): Double? {
        val cleaned = value.trim().removePrefix("f/").removePrefix("F/").removePrefix("f").removePrefix("F")
        return cleaned.toDoubleOrNull()
    }

    /**
     * Parses a shutter speed string to a RationalNumber for EXIF ExposureTime.
     *
     * Accepts formats: "1/125" (fraction), "0.008" (decimal seconds), "125" (1/N).
     */
    private fun parseShutterSpeed(value: String): RationalNumber? {
        val trimmed = value.trim()
        // Fraction format: "1/125"
        if (trimmed.contains("/")) {
            val parts = trimmed.split("/")
            if (parts.size == 2) {
                val num = parts[0].toIntOrNull() ?: return null
                val den = parts[1].toIntOrNull() ?: return null
                if (den != 0) return RationalNumber(num, den)
            }
        }
        // Decimal format: "0.008"
        val decimal = trimmed.toDoubleOrNull()
        if (decimal != null && decimal > 0) {
            return RationalNumber.valueOf(decimal)
        }
        // Integer "1/N" format: "125" means 1/125
        val intVal = trimmed.toIntOrNull()
        if (intVal != null && intVal > 0) {
            return RationalNumber(1, intVal)
        }
        return null
    }

    /**
     * Resolves filename conflicts by incrementing an index.
     *
     * @param directory Destination directory
     * @param fileName Proposed filename
     * @return Resolved filename that doesn't conflict
     */
    private fun resolveFilenameConflict(directory: File, fileName: String): String {
        var candidate = File(directory, fileName)
        var counter = 1

        val baseName = fileName.substringBeforeLast(".")
        val extension = fileName.substringAfterLast(".", "jpg")

        while (candidate.exists()) {
            candidate = File(directory, "${baseName}_$counter.$extension")
            counter++
        }

        return candidate.absolutePath
    }

    /**
     * Generates a unique filename for an export, avoiding conflicts with existing files and files
     * being exported in the current batch.
     *
     * @param destinationPath Destination folder
     * @param baseName Base filename without extension
     * @param extension File extension
     * @param existingExports Set of filenames already used in this export batch
     * @return Unique filename (without path)
     */
    fun generateUniqueFileName(
        destinationPath: String,
        baseName: String,
        extension: String,
        existingExports: Set<String>,
    ): String {
        var counter = 1
        var candidate = "$baseName.$extension"
        val destDir = File(destinationPath)

        while (true) {
            val exists = File(destDir, candidate).exists() || candidate in existingExports
            if (!exists) break
            candidate = "${baseName}_$counter.$extension"
            counter++
        }

        return candidate
    }

    /** Crops an image using axis-aligned bounding box (when perspective correction is disabled). */
    private fun cropAxisAligned(sourceImage: BufferedImage, photo: DetectedPhoto): BufferedImage {
        val bounds = photo.getBounds()
        val cropX = bounds.minX.coerceIn(0, (sourceImage.width - 1).coerceAtLeast(0))
        val cropY = bounds.minY.coerceIn(0, (sourceImage.height - 1).coerceAtLeast(0))
        val cropWidth = bounds.getWidth().coerceIn(1, (sourceImage.width - cropX).coerceAtLeast(1))
        val cropHeight =
            bounds.getHeight().coerceIn(1, (sourceImage.height - cropY).coerceAtLeast(1))

        return try {
            sourceImage.getSubimage(cropX, cropY, cropWidth, cropHeight)
        } catch (_: Exception) {
            // Fallback to manual copy if getSubimage fails
            val cropped = BufferedImage(cropWidth, cropHeight, BufferedImage.TYPE_INT_RGB)
            val g = cropped.createGraphics()
            g.drawImage(
                sourceImage.getSubimage(
                    cropX.coerceAtLeast(0),
                    cropY.coerceAtLeast(0),
                    cropWidth.coerceAtMost(sourceImage.width - cropX),
                    cropHeight.coerceAtMost(sourceImage.height - cropY),
                ),
                0,
                0,
                null,
            )
            g.dispose()
            cropped
        }
    }

    /**
     * Applies margin to a detected photo's corners, pushing them outward from the quad center.
     *
     * This mirrors the margin logic in photocrop.py: each corner is expanded outward along the
     * direction from the quad center to that corner. The margin is computed as a fraction of the
     * photo's diagonal length.
     *
     * @param photo The detected photo
     * @param marginFraction Margin as fraction of the photo's diagonal (e.g. 0.02 = 2%)
     * @return New DetectedPhoto with corners pushed outward, or the same photo if margin is 0
     */
    fun applyMargin(photo: DetectedPhoto, marginFraction: Double): DetectedPhoto {
        if (marginFraction <= 0.0) return photo

        val corners =
            listOf(
                photo.topLeft.x.toDouble() to photo.topLeft.y.toDouble(),
                photo.topRight.x.toDouble() to photo.topRight.y.toDouble(),
                photo.bottomRight.x.toDouble() to photo.bottomRight.y.toDouble(),
                photo.bottomLeft.x.toDouble() to photo.bottomLeft.y.toDouble(),
            )

        // Quad center (centroid of the 4 corners)
        val cx = corners.map { it.first }.average()
        val cy = corners.map { it.second }.average()

        // Diagonal length of the quad (max opposite-corner distance)
        val diag1 = distance(corners[0], corners[2]) // TL to BR
        val diag2 = distance(corners[1], corners[3]) // TR to BL
        val diagonal = maxOf(diag1, diag2)

        if (diagonal <= 0.0) return photo

        val marginPx = marginFraction * diagonal

        // Push each corner outward from center
        val expanded =
            corners.map { (x, y) ->
                val dx = x - cx
                val dy = y - cy
                val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                if (dist > 0) {
                    (x + (marginPx / dist) * dx) to (y + (marginPx / dist) * dy)
                } else {
                    x to y
                }
            }

        return photo.copy(
            topLeft = PhotoCorner(expanded[0].first.toFloat(), expanded[0].second.toFloat()),
            topRight = PhotoCorner(expanded[1].first.toFloat(), expanded[1].second.toFloat()),
            bottomRight = PhotoCorner(expanded[2].first.toFloat(), expanded[2].second.toFloat()),
            bottomLeft = PhotoCorner(expanded[3].first.toFloat(), expanded[3].second.toFloat()),
        )
    }

    /** Euclidean distance between two points. */
    private fun distance(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val dx = b.first - a.first
        val dy = b.second - a.second
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    /** Rotates an image by the specified rotation angle. */
    private fun rotateImage(image: BufferedImage, rotation: RotationAngle): BufferedImage {
        val radians = rotation.radians

        val cos = kotlin.math.abs(kotlin.math.cos(radians))
        val sin = kotlin.math.abs(kotlin.math.sin(radians))

        val newWidth: Int
        val newHeight: Int

        when (rotation) {
            RotationAngle.CW_90,
            RotationAngle.CCW_90 -> {
                newWidth = image.height
                newHeight = image.width
            }
            else -> {
                newWidth = (image.width * cos + image.height * sin).toInt()
                newHeight = (image.width * sin + image.height * cos).toInt()
            }
        }

        val rotated =
            BufferedImage(
                newWidth.coerceAtLeast(1),
                newHeight.coerceAtLeast(1),
                BufferedImage.TYPE_INT_RGB,
            )

        val graphics = rotated.createGraphics()
        graphics.background = java.awt.Color.BLACK

        when (rotation) {
            RotationAngle.CW_90 -> {
                graphics.translate(newWidth, 0)
                graphics.rotate(Math.PI / 2)
            }
            RotationAngle.CCW_90 -> {
                graphics.translate(0, newHeight)
                graphics.rotate(-Math.PI / 2)
            }
            RotationAngle.CW_180 -> {
                graphics.translate(newWidth / 2.0, newHeight / 2.0)
                graphics.rotate(Math.PI)
                graphics.translate(-image.width / 2.0, -image.height / 2.0)
            }
            RotationAngle.NONE -> {
                // No rotation
            }
        }

        graphics.drawImage(image, 0, 0, null)
        graphics.dispose()

        return rotated
    }
}
