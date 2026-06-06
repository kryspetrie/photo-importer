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
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter
import org.apache.commons.imaging.formats.jpeg.iptc.IptcRecord
import org.apache.commons.imaging.formats.jpeg.iptc.IptcTypes
import org.apache.commons.imaging.formats.jpeg.iptc.JpegIptcRewriter
import org.apache.commons.imaging.formats.jpeg.iptc.PhotoshopApp13Data
import org.apache.commons.imaging.formats.jpeg.xmp.JpegXmpRewriter
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants
import org.apache.commons.imaging.formats.tiff.constants.GpsTagConstants
import org.apache.commons.imaging.formats.tiff.constants.MicrosoftTagConstants
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet
import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.OverrideState
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
constructor(
    private val perspectiveService: PerspectiveCorrectionService,
    private val faceRegionTransformer: FaceRegionTransformer,
) {

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
                writeImageWithMetadata(
                    finalImage,
                    outputFile,
                    photo.configuration,
                    sourceFile,
                    detectedPhoto = marginedPhoto,
                    marginFraction = marginFraction,
                    sourceImage = image,
                    preRotationWidth = correctedImage.width,
                    preRotationHeight = correctedImage.height,
                )

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
     * @param sourceFile The original source file (for EXIF metadata reading). May be null if
     *   unavailable.
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
            // and source face region transformation
            writeImageWithMetadata(
                finalImage,
                outputFile,
                detectedPhoto.configuration,
                sourceFile,
                detectedPhoto = marginedPhoto,
                marginFraction = marginFraction,
                sourceImage = sourceImage,
                preRotationWidth = correctedImage.width,
                preRotationHeight = correctedImage.height,
            )

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
     * 2. IPTC (via [JpegIptcRewriter]): Adds IPTC keywords for cross-platform/metadata
     *    compatibility.
     * 3. XMP face regions (MWG-RS): Merges source image face regions (transformed to output
     *    coordinates) with user-specified face regions.
     *
     * @param image The corrected image (post-warp, post-rotation)
     * @param outputFile Destination file
     * @param config Photo configuration with metadata overrides and copyOriginalExif setting
     * @param sourceFile The original scanned image file (for reading EXIF and XMP face regions)
     * @param detectedPhoto The detected photo with corner positions and rotation info
     * @param marginFraction Margin fraction used during export (must match the margin applied to
     *   corners)
     * @param sourceImage The original source image (for dimensions, may be null if unavailable)
     */
    private fun writeImageWithMetadata(
        image: BufferedImage,
        outputFile: File,
        config: PhotoScanConfiguration,
        sourceFile: File? = null,
        detectedPhoto: DetectedPhoto? = null,
        marginFraction: Double = 0.02,
        sourceImage: BufferedImage? = null,
        preRotationWidth: Int = image.width,
        preRotationHeight: Int = image.height,
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

        // Write IPTC keywords and location data (cross-platform, visible on macOS)
        val keywordsValue = resolveKeywords(config)
        val hasLocationData =
            !config.locationName.isNullOrBlank() ||
                !config.city.isNullOrBlank() ||
                !config.state.isNullOrBlank() ||
                !config.country.isNullOrBlank() ||
                !config.subjects.isNullOrBlank()
        if (keywordsValue != null || hasLocationData) {
            writeIptcData(outputFile, keywordsValue, config)
        }

        // Write XMP face region data (MWG-RS Regions) for Lightroom, digiKam, etc.
        // Merge user-specified face regions with source image face regions (transformed to output
        // coords)
        val allFaceRegions = mutableListOf(config.faceRegions)

        // If we have a source file and detected photo, read and transform source face regions
        if (sourceFile != null && detectedPhoto != null && sourceImage != null) {
            try {
                val transformedRegions =
                    faceRegionTransformer.transformFaceRegionsFromSource(
                        sourceFile = sourceFile,
                        detectedPhoto = detectedPhoto,
                        outputWidth = preRotationWidth,
                        outputHeight = preRotationHeight,
                        sourceWidth = sourceImage.width,
                        sourceHeight = sourceImage.height,
                        marginFraction = marginFraction,
                    )
                if (transformedRegions.isNotEmpty()) {
                    allFaceRegions.add(transformedRegions)
                }
            } catch (_: Exception) {
                // Source face region transformation is best-effort — don't fail the export
            }
        }

        val mergedConfig = config.copy(faceRegions = allFaceRegions.flatten())
        if (mergedConfig.faceRegions.isNotEmpty()) {
            writeXmpFaceRegions(outputFile, mergedConfig)
        }
    }

    /**
     * Writes EXIF metadata into an existing JPEG file.
     *
     * Baseline strategy depends on [PhotoScanConfiguration.copyOriginalExif]:
     * - **true** (default): Reads existing EXIF from [sourceFile] (the original scan) as a
     *   baseline, then applies user overrides on top. This preserves scanner EXIF (make, model,
     *   scan date, etc.) and only replaces the fields the user explicitly sets.
     * - **false**: Starts with a fresh (empty) [TiffOutputSet] — only user-specified overrides are
     *   written, and no scanner/source EXIF is carried forward.
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
            val outputSet =
                if (config.copyOriginalExif) {
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
            FileOutputStream(jpegFile).use { fos -> fos.write(baos.toByteArray()) }
        } catch (e: Exception) {
            // EXIF writing is best-effort — if it fails, the JPEG is still valid without EXIF
            // overrides
            System.err.println(
                "[PhotoScanExportService] Warning: Failed to write EXIF metadata: ${e.message}"
            )
        }
    }

    /**
     * Reads EXIF metadata from a file and returns a mutable [TiffOutputSet]. If the file has no
     * EXIF data, returns a fresh (empty) output set.
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
            when (config.overrideDescription) {
                OverrideState.NULL_OUT -> {
                    rootDir.removeField(TiffTagConstants.TIFF_TAG_IMAGE_DESCRIPTION)
                }
                OverrideState.OVERRIDE -> {
                    val desc = config.description ?: config.notes.ifBlank { null }
                    if (!desc.isNullOrBlank()) {
                        rootDir.removeField(TiffTagConstants.TIFF_TAG_IMAGE_DESCRIPTION)
                        rootDir.add(TiffTagConstants.TIFF_TAG_IMAGE_DESCRIPTION, desc)
                    }
                }
                OverrideState.KEEP_SOURCE,
                null -> {
                    // Legacy behavior: override if value is set
                    val description = config.description ?: config.notes.ifBlank { null }
                    if (!description.isNullOrBlank()) {
                        rootDir.removeField(TiffTagConstants.TIFF_TAG_IMAGE_DESCRIPTION)
                        rootDir.add(TiffTagConstants.TIFF_TAG_IMAGE_DESCRIPTION, description)
                    }
                }
            }

            // Make (0x010F)
            applyTriStateField(
                overrideState = config.overrideCameraMake,
                value = config.cameraMake,
                onNullOut = { rootDir.removeField(TiffTagConstants.TIFF_TAG_MAKE) },
                onOverride = { rootDir.add(TiffTagConstants.TIFF_TAG_MAKE, it) },
                legacyPredicate = !config.cameraMake.isNullOrBlank(),
                legacyAction = {
                    rootDir.removeField(TiffTagConstants.TIFF_TAG_MAKE)
                    rootDir.add(TiffTagConstants.TIFF_TAG_MAKE, config.cameraMake!!)
                },
            )

            // Model (0x0110)
            applyTriStateField(
                overrideState = config.overrideCameraModel,
                value = config.cameraModel,
                onNullOut = { rootDir.removeField(TiffTagConstants.TIFF_TAG_MODEL) },
                onOverride = { rootDir.add(TiffTagConstants.TIFF_TAG_MODEL, it) },
                legacyPredicate = !config.cameraModel.isNullOrBlank(),
                legacyAction = {
                    rootDir.removeField(TiffTagConstants.TIFF_TAG_MODEL)
                    rootDir.add(TiffTagConstants.TIFF_TAG_MODEL, config.cameraModel!!)
                },
            )

            // --- Exif SubIFD tags ---

            // DateTimeOriginal (0x9003) — format: "YYYY:MM:DD HH:MM:SS"
            when (config.overrideOriginalDate) {
                OverrideState.NULL_OUT -> {
                    exifDir.removeField(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL)
                    exifDir.removeField(ExifTagConstants.EXIF_TAG_DATE_TIME_DIGITIZED)
                }
                OverrideState.OVERRIDE -> {
                    val dateOriginal = resolveDateOriginal(config)
                    if (dateOriginal != null) {
                        exifDir.removeField(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL)
                        exifDir.add(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL, dateOriginal)
                        exifDir.removeField(ExifTagConstants.EXIF_TAG_DATE_TIME_DIGITIZED)
                        exifDir.add(ExifTagConstants.EXIF_TAG_DATE_TIME_DIGITIZED, dateOriginal)
                    }
                }
                OverrideState.KEEP_SOURCE,
                null -> {
                    // Legacy behavior
                    val dateOriginal = resolveDateOriginal(config)
                    if (dateOriginal != null) {
                        exifDir.removeField(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL)
                        exifDir.add(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL, dateOriginal)
                        exifDir.removeField(ExifTagConstants.EXIF_TAG_DATE_TIME_DIGITIZED)
                        exifDir.add(ExifTagConstants.EXIF_TAG_DATE_TIME_DIGITIZED, dateOriginal)
                    }
                }
            }

            // LensModel (0xA434)
            applyTriStateField(
                overrideState = config.overrideLensModel,
                value = config.lensModel,
                onNullOut = { exifDir.removeField(ExifTagConstants.EXIF_TAG_LENS_MODEL) },
                onOverride = { exifDir.add(ExifTagConstants.EXIF_TAG_LENS_MODEL, it) },
                legacyPredicate = !config.lensModel.isNullOrBlank(),
                legacyAction = {
                    exifDir.removeField(ExifTagConstants.EXIF_TAG_LENS_MODEL)
                    exifDir.add(ExifTagConstants.EXIF_TAG_LENS_MODEL, config.lensModel!!)
                },
            )

            // FocalLength (0x920A) — stored as RationalNumber in mm
            when (config.overrideFocalLength) {
                OverrideState.NULL_OUT -> {
                    exifDir.removeField(ExifTagConstants.EXIF_TAG_FOCAL_LENGTH)
                }
                OverrideState.OVERRIDE -> {
                    val fl = config.focalLength
                    if (!fl.isNullOrBlank()) {
                        val focalLengthMm = parseFocalLength(fl)
                        if (focalLengthMm != null) {
                            exifDir.removeField(ExifTagConstants.EXIF_TAG_FOCAL_LENGTH)
                            exifDir.add(
                                ExifTagConstants.EXIF_TAG_FOCAL_LENGTH,
                                RationalNumber.valueOf(focalLengthMm),
                            )
                        }
                    }
                }
                OverrideState.KEEP_SOURCE,
                null -> {
                    // Legacy behavior
                    if (!config.focalLength.isNullOrBlank()) {
                        val focalLengthMm = parseFocalLength(config.focalLength)
                        if (focalLengthMm != null) {
                            exifDir.removeField(ExifTagConstants.EXIF_TAG_FOCAL_LENGTH)
                            val rational = RationalNumber.valueOf(focalLengthMm)
                            exifDir.add(ExifTagConstants.EXIF_TAG_FOCAL_LENGTH, rational)
                        }
                    }
                }
            }

            // FNumber (0x829D) — stored as RationalNumber (aperture value)
            when (config.overrideAperture) {
                OverrideState.NULL_OUT -> {
                    exifDir.removeField(ExifTagConstants.EXIF_TAG_FNUMBER)
                }
                OverrideState.OVERRIDE -> {
                    val ap = config.aperture
                    if (!ap.isNullOrBlank()) {
                        val fNumber = parseAperture(ap)
                        if (fNumber != null) {
                            exifDir.removeField(ExifTagConstants.EXIF_TAG_FNUMBER)
                            exifDir.add(
                                ExifTagConstants.EXIF_TAG_FNUMBER,
                                RationalNumber.valueOf(fNumber),
                            )
                        }
                    }
                }
                OverrideState.KEEP_SOURCE,
                null -> {
                    // Legacy behavior
                    if (!config.aperture.isNullOrBlank()) {
                        val fNumber = parseAperture(config.aperture)
                        if (fNumber != null) {
                            exifDir.removeField(ExifTagConstants.EXIF_TAG_FNUMBER)
                            val rational = RationalNumber.valueOf(fNumber)
                            exifDir.add(ExifTagConstants.EXIF_TAG_FNUMBER, rational)
                        }
                    }
                }
            }

            // ExposureTime (0x829A) — stored as RationalNumber (e.g. 1/125)
            when (config.overrideShutterSpeed) {
                OverrideState.NULL_OUT -> {
                    exifDir.removeField(ExifTagConstants.EXIF_TAG_EXPOSURE_TIME)
                }
                OverrideState.OVERRIDE -> {
                    val ss = config.shutterSpeed
                    if (!ss.isNullOrBlank()) {
                        val rational = parseShutterSpeed(ss)
                        if (rational != null) {
                            exifDir.removeField(ExifTagConstants.EXIF_TAG_EXPOSURE_TIME)
                            exifDir.add(ExifTagConstants.EXIF_TAG_EXPOSURE_TIME, rational)
                        }
                    }
                }
                OverrideState.KEEP_SOURCE,
                null -> {
                    // Legacy behavior
                    if (!config.shutterSpeed.isNullOrBlank()) {
                        val rational = parseShutterSpeed(config.shutterSpeed)
                        if (rational != null) {
                            exifDir.removeField(ExifTagConstants.EXIF_TAG_EXPOSURE_TIME)
                            exifDir.add(ExifTagConstants.EXIF_TAG_EXPOSURE_TIME, rational)
                        }
                    }
                }
            }

            // ISOSpeedRatings (0x8827) — stored as Short
            when (config.overrideIso) {
                OverrideState.NULL_OUT -> {
                    exifDir.removeField(ExifTagConstants.EXIF_TAG_ISO)
                }
                OverrideState.OVERRIDE -> {
                    val isoValue = config.iso?.trim()?.toIntOrNull()
                    if (isoValue != null) {
                        exifDir.removeField(ExifTagConstants.EXIF_TAG_ISO)
                        exifDir.add(ExifTagConstants.EXIF_TAG_ISO, isoValue.toShort())
                    }
                }
                OverrideState.KEEP_SOURCE,
                null -> {
                    // Legacy behavior
                    if (!config.iso.isNullOrBlank()) {
                        val isoValue = config.iso.trim().toIntOrNull()
                        if (isoValue != null) {
                            exifDir.removeField(ExifTagConstants.EXIF_TAG_ISO)
                            exifDir.add(ExifTagConstants.EXIF_TAG_ISO, isoValue.toShort())
                        }
                    }
                }
            }

            // XPKeywords (0x9C9D) — Windows keyword tag, stored as XP String (UTF-16LE)
            when (config.overrideKeywords) {
                OverrideState.NULL_OUT -> {
                    rootDir.removeField(MicrosoftTagConstants.EXIF_TAG_XPKEYWORDS)
                }
                OverrideState.OVERRIDE -> {
                    val exifKeywordsValue = resolveKeywords(config)
                    if (exifKeywordsValue != null) {
                        rootDir.removeField(MicrosoftTagConstants.EXIF_TAG_XPKEYWORDS)
                        rootDir.add(MicrosoftTagConstants.EXIF_TAG_XPKEYWORDS, exifKeywordsValue)
                    }
                }
                OverrideState.KEEP_SOURCE,
                null -> {
                    // Legacy behavior
                    val exifKeywordsValue = resolveKeywords(config)
                    if (exifKeywordsValue != null) {
                        rootDir.removeField(MicrosoftTagConstants.EXIF_TAG_XPKEYWORDS)
                        rootDir.add(MicrosoftTagConstants.EXIF_TAG_XPKEYWORDS, exifKeywordsValue)
                    }
                }
            }

            // --- GPS IFD tags ---
            when (config.overrideGps) {
                OverrideState.NULL_OUT -> {
                    // Remove all GPS tags
                    try {
                        val gpsDir = outputSet.getGPSDirectory()
                        if (gpsDir != null) {
                            gpsDir.removeField(GpsTagConstants.GPS_TAG_GPS_LATITUDE_REF)
                            gpsDir.removeField(GpsTagConstants.GPS_TAG_GPS_LATITUDE)
                            gpsDir.removeField(GpsTagConstants.GPS_TAG_GPS_LONGITUDE_REF)
                            gpsDir.removeField(GpsTagConstants.GPS_TAG_GPS_LONGITUDE)
                            gpsDir.removeField(GpsTagConstants.GPS_TAG_GPS_VERSION_ID)
                        }
                    } catch (_: Exception) {}
                }
                OverrideState.OVERRIDE -> {
                    writeGpsData(outputSet, config)
                }
                OverrideState.KEEP_SOURCE,
                null -> {
                    // Legacy behavior: write GPS if both coordinates are present
                    if (
                        !config.gpsLatitude.isNullOrBlank() && !config.gpsLongitude.isNullOrBlank()
                    ) {
                        writeGpsData(outputSet, config)
                    }
                }
            }
        } catch (e: Exception) {
            // Best effort — don't fail the export if EXIF writing has issues
            System.err.println(
                "[PhotoScanExportService] Warning: Error applying EXIF overrides: ${e.message}"
            )
        }
    }

    /** Writes GPS latitude/longitude data to the GPS IFD directory. */
    private fun writeGpsData(outputSet: TiffOutputSet, config: PhotoScanConfiguration) {
        if (config.gpsLatitude.isNullOrBlank() || config.gpsLongitude.isNullOrBlank()) return
        val lat = config.gpsLatitude.trim().toDoubleOrNull()
        val lon = config.gpsLongitude.trim().toDoubleOrNull()
        if (lat == null || lon == null) return
        try {
            val gpsDir = outputSet.getOrCreateGPSDirectory()

            // Latitude: N/S reference + degrees/minutes/seconds
            gpsDir.removeField(GpsTagConstants.GPS_TAG_GPS_LATITUDE_REF)
            gpsDir.add(
                GpsTagConstants.GPS_TAG_GPS_LATITUDE_REF,
                if (lat >= 0) GpsTagConstants.GPS_TAG_GPS_LATITUDE_REF_VALUE_NORTH
                else GpsTagConstants.GPS_TAG_GPS_LATITUDE_REF_VALUE_SOUTH,
            )
            gpsDir.removeField(GpsTagConstants.GPS_TAG_GPS_LATITUDE)
            gpsDir.add(
                GpsTagConstants.GPS_TAG_GPS_LATITUDE,
                *decimalToGpsRationals(kotlin.math.abs(lat)),
            )

            // Longitude: E/W reference + degrees/minutes/seconds
            gpsDir.removeField(GpsTagConstants.GPS_TAG_GPS_LONGITUDE_REF)
            gpsDir.add(
                GpsTagConstants.GPS_TAG_GPS_LONGITUDE_REF,
                if (lon >= 0) GpsTagConstants.GPS_TAG_GPS_LONGITUDE_REF_VALUE_EAST
                else GpsTagConstants.GPS_TAG_GPS_LONGITUDE_REF_VALUE_WEST,
            )
            gpsDir.removeField(GpsTagConstants.GPS_TAG_GPS_LONGITUDE)
            gpsDir.add(
                GpsTagConstants.GPS_TAG_GPS_LONGITUDE,
                *decimalToGpsRationals(kotlin.math.abs(lon)),
            )

            // GPS version ID
            gpsDir.removeField(GpsTagConstants.GPS_TAG_GPS_VERSION_ID)
            gpsDir.add(
                GpsTagConstants.GPS_TAG_GPS_VERSION_ID,
                2.toByte(),
                3.toByte(),
                0.toByte(),
                0.toByte(),
            )
        } catch (e: Exception) {
            System.err.println(
                "[PhotoScanExportService] Warning: Failed to write GPS data: ${e.message}"
            )
        }
    }

    /**
     * Applies tri-state logic for a string-based EXIF field.
     * - [OverrideState.NULL_OUT]: Remove the field from output EXIF
     * - [OverrideState.OVERRIDE]: Replace with the value (via [onOverride])
     * - [OverrideState.KEEP_SOURCE] or null: Legacy behavior — apply [legacyAction] if
     *   [legacyPredicate]
     *
     * @param overrideState The tri-state override for this field (null = backward compat)
     * @param value The current field value (used for OVERRIDE validation)
     * @param onNullOut Action to remove the field from EXIF
     * @param onOverride Action to set the field value in EXIF (receives the string value)
     * @param legacyPredicate Whether legacy behavior should apply
     * @param legacyAction Action to take under legacy behavior
     */
    private fun applyTriStateField(
        overrideState: OverrideState?,
        value: String?,
        onNullOut: () -> Unit,
        onOverride: (String) -> Unit,
        legacyPredicate: Boolean,
        legacyAction: () -> Unit,
    ) {
        when (overrideState) {
            OverrideState.NULL_OUT -> onNullOut()
            OverrideState.OVERRIDE -> {
                val v = value
                if (!v.isNullOrBlank()) onOverride(v)
            }
            OverrideState.KEEP_SOURCE,
            null -> {
                if (legacyPredicate) legacyAction()
            }
        }
    }

    /**
     * Writes IPTC keywords, location, and subject data into an existing JPEG file using the
     * Photoshop APP13 segment.
     *
     * IPTC:Keywords (record 2, dataset 25) is the cross-platform standard for keywords that macOS
     * Preview, Photos.app, and Finder all read.
     *
     * IPTC location fields (SubLocation, City, Province/State, Country) map to photoshop:* XMP
     * fields and are read by Lightroom, Bridge, digiKam, Apple Photos, etc.
     *
     * Subject/person names are added as additional IPTC:Keywords for broad compatibility.
     *
     * @param jpegFile The JPEG file to rewrite with IPTC data (modified in-place)
     * @param keywordsValue Comma-separated keyword string (may be null if no keywords)
     * @param config Configuration with location and subject fields
     */
    private fun writeIptcData(
        jpegFile: File,
        keywordsValue: String?,
        config: PhotoScanConfiguration,
    ) {
        try {
            // Read existing IPTC data from the JPEG if present
            val metadata = Imaging.getMetadata(jpegFile)
            val existingRecords =
                if (metadata is JpegImageMetadata) {
                    val photoshop = metadata.photoshop
                    photoshop?.photoshopApp13Data?.records?.filterNotNull()?.toMutableList()
                        ?: mutableListOf()
                } else {
                    mutableListOf()
                }

            // Remove existing KEYWORDS records (we'll replace them)
            existingRecords.removeAll { it.iptcType == IptcTypes.KEYWORDS }

            // Add keyword records from the keywords field
            if (keywordsValue != null) {
                val keywordList =
                    keywordsValue.split(",").map { it.trim() }.filter { it.isNotBlank() }
                for (keyword in keywordList) {
                    existingRecords.add(IptcRecord(IptcTypes.KEYWORDS, keyword))
                }
            }

            // Add subject/person names as additional keyword records for compatibility
            if (!config.subjects.isNullOrBlank()) {
                val subjectList =
                    config.subjects.split(",").map { it.trim() }.filter { it.isNotBlank() }
                for (subject in subjectList) {
                    // Only add if not already in keywords (avoid duplicates)
                    val keywordMatches =
                        existingRecords.filter {
                            it.iptcType == IptcTypes.KEYWORDS && it.value == subject
                        }
                    if (keywordMatches.isEmpty()) {
                        existingRecords.add(IptcRecord(IptcTypes.KEYWORDS, subject))
                    }
                }
            }

            // --- IPTC Location fields ---
            // Remove existing location records (we'll replace them)
            existingRecords.removeAll { it.iptcType == IptcTypes.SUBLOCATION }
            existingRecords.removeAll { it.iptcType == IptcTypes.CITY }
            existingRecords.removeAll { it.iptcType == IptcTypes.PROVINCE_STATE }
            existingRecords.removeAll { it.iptcType == IptcTypes.COUNTRY_PRIMARY_LOCATION_NAME }
            existingRecords.removeAll { it.iptcType == IptcTypes.COUNTRY_PRIMARY_LOCATION_CODE }

            if (!config.locationName.isNullOrBlank()) {
                existingRecords.add(IptcRecord(IptcTypes.SUBLOCATION, config.locationName.trim()))
            }
            if (!config.city.isNullOrBlank()) {
                existingRecords.add(IptcRecord(IptcTypes.CITY, config.city.trim()))
            }
            if (!config.state.isNullOrBlank()) {
                existingRecords.add(IptcRecord(IptcTypes.PROVINCE_STATE, config.state.trim()))
            }
            if (!config.country.isNullOrBlank()) {
                existingRecords.add(
                    IptcRecord(IptcTypes.COUNTRY_PRIMARY_LOCATION_NAME, config.country.trim())
                )
            }

            // Build the Photoshop APP13 data block
            val existingBlocks =
                if (metadata is JpegImageMetadata) {
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

            FileOutputStream(jpegFile).use { fos -> fos.write(baos.toByteArray()) }
        } catch (e: Exception) {
            // IPTC writing is best-effort — don't fail the export
            System.err.println(
                "[PhotoScanExportService] Warning: Failed to write IPTC data: ${e.message}"
            )
        }
    }

    /**
     * Writes XMP face region data (MWG-RS Regions) into an existing JPEG file.
     *
     * MWG-RS (Metadata Working Group - Region Schema) uses normalized center-based coordinates (x/y
     * = center, w/h = fractions) stored in XMP.
     *
     * This method reads any existing XMP, merges in the face regions, and rewrites. If no existing
     * XMP is found, a fresh XMP packet is created.
     *
     * @param jpegFile The JPEG file to rewrite with XMP data (modified in-place)
     * @param config Configuration with face region data
     */
    private fun writeXmpFaceRegions(jpegFile: File, config: PhotoScanConfiguration) {
        try {
            // Build the MWG-RS region XMP fragment
            val regions = config.faceRegions
            if (regions.isEmpty()) return

            val regionEntries =
                regions.joinToString("\n") { region ->
                    """        <rdf:Description rdf:about=""
                   mwg-rs:Name="${escapeXml(region.name)}"
                   mwg-rs:Type="${escapeXml(region.type)}"
                   mwg-rs:Area="
                    x='${formatDecimal(region.x)}'
                    y='${formatDecimal(region.y)}'
                    w='${formatDecimal(region.w)}'
                    h='${formatDecimal(region.h)}'
                    unit='normalized'"/>"""
                }

            val mwgRsXmp =
                """
        |<rdf:Description rdf:about=''
        |   xmlns:mwg-rs='http://www.metadataworkinggroup.com/schemas/regions/'>
        |  <mwg-rs:Regions>
        |    <rdf:Alt>
        |$regionEntries
        |    </rdf:Alt>
        |  </mwg-rs:Regions>
        |</rdf:Description>
            """
                    .trimMargin()

            // Read existing XMP from the JPEG if present
            val jpegBytes = jpegFile.readBytes()
            // Read existing XMP from the JPEG if present
            val existingXmp: String? =
                try {
                    Imaging.getXmpXml(jpegBytes)
                } catch (_: Exception) {
                    null
                }

            val newXmp: String =
                if (!existingXmp.isNullOrBlank()) {
                    // Merge: insert MWG-RS into existing XMP before the closing </x:xmpmeta>
                    val existing = existingXmp
                    val closingTag = "</x:xmpmeta>"
                    if (existing.contains(closingTag)) {
                        existing.replace(closingTag, "$mwgRsXmp\n$closingTag")
                    } else {
                        "$existing\n$mwgRsXmp"
                    }
                } else {
                    // Create a fresh XMP packet
                    """<?xpacket begin='${'\uFEFF'}' id='W5M0MpCehiHzreSzNTczkc9d'?>
<x:xmpmeta xmlns:x='adobe:ns:meta/'>
<rdf:RDF xmlns:rdf='http://www.w3.org/1999/02/22-rdf-syntax-ns#'>
$mwgRsXmp
</rdf:RDF>
</x:xmpmeta>
<?xpacket end='w'?>"""
                        .trimIndent()
                }

            // Write the XMP back into the JPEG
            val baos = ByteArrayOutputStream()
            JpegXmpRewriter().updateXmpXml(jpegBytes, baos, newXmp)
            baos.close()

            FileOutputStream(jpegFile).use { fos -> fos.write(baos.toByteArray()) }
        } catch (e: Exception) {
            // XMP writing is best-effort — don't fail the export
            System.err.println(
                "[PhotoScanExportService] Warning: Failed to write XMP face regions: ${e.message}"
            )
        }
    }

    /** Escapes special XML characters in attribute values. */
    private fun escapeXml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    /** Formats a double to 6 decimal places for MWG-RS coordinates. */
    private fun formatDecimal(value: Double): String = String.format("%.6f", value)

    /**
     * Resolves the keywords value from configuration, checking both the new [keywords] field and
     * the legacy [tags] field.
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
     * Priority: [originalDate] > [originalDateOverride] > derived from [year] only. Format
     * returned: "YYYY:MM:DD HH:MM:SS" (EXIF format, colons in date part).
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

    /** Parses a focal length string (e.g. "50mm", "50", "24mm") to a floating-point value in mm. */
    private fun parseFocalLength(value: String): Double? {
        return value.trim().removeSuffix("mm").removeSuffix("MM").trim().toDoubleOrNull()
    }

    /** Parses an aperture string (e.g. "f/2.8", "2.8", "F2.8") to a floating-point f-number. */
    private fun parseAperture(value: String): Double? {
        val cleaned =
            value.trim().removePrefix("f/").removePrefix("F/").removePrefix("f").removePrefix("F")
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
     * Converts a decimal degree value to GPS rationals (degrees, minutes, seconds). EXIF GPS
     * latitude/longitude fields require 3 RationalNumber values: degrees, minutes, seconds.
     *
     * @param decimalDegrees Absolute value of latitude or longitude in decimal degrees
     * @return Array of 3 RationalNumbers representing [degrees, minutes, seconds]
     */
    private fun decimalToGpsRationals(decimalDegrees: Double): Array<RationalNumber> {
        val degrees = decimalDegrees.toInt()
        val minutesDecimal = (decimalDegrees - degrees) * 60.0
        val minutes = minutesDecimal.toInt()
        val seconds = (kotlin.math.round((minutesDecimal - minutes) * 60.0 * 10000.0)).toInt()
        return arrayOf(
            RationalNumber(degrees, 1),
            RationalNumber(minutes, 1),
            RationalNumber(seconds, 10000),
        )
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
