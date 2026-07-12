package org.kryspetrie.fileimport.application.export

import java.io.ByteArrayOutputStream
import org.apache.commons.imaging.Imaging
import org.apache.commons.imaging.common.RationalNumber
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants
import org.apache.commons.imaging.formats.tiff.constants.GpsTagConstants
import org.apache.commons.imaging.formats.tiff.constants.MicrosoftTagConstants
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet
import org.kryspetrie.fileimport.domain.model.ExifValueResolver
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.OverrideState
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.domain.port.FileSystemPort

/**
 * Writes EXIF metadata into existing JPEG files.
 *
 * Handles:
 * - Baseline EXIF reading from source files
 * - Override application with TriState (OVERRIDE / NULL_OUT / KEEP_SOURCE)
 * - GPS coordinate writing
 *
 * All operations are best-effort — failures log warnings but don't fail the export.
 *
 * @param fileSystem Port for file I/O operations (replaces direct `java.io.File` usage)
 */
class ExifMetadataWriter(private val fileSystem: FileSystemPort) {

    /**
     * Writes EXIF metadata into an existing JPEG file.
     *
     * Baseline strategy depends on [PhotoScanConfiguration.copyOriginalExif]:
     * - **true** (default): Reads existing EXIF from [sourceFile] as baseline, then applies user
     *   overrides on top.
     * - **false**: Starts with a fresh (empty) [TiffOutputSet] — only user-specified overrides are
     *   written.
     *
     * @param jpegPath The JPEG file path to rewrite with EXIF data (modified in-place)
     * @param config Configuration with EXIF override values and the copyOriginalExif flag
     * @param sourcePath The original source file path to read baseline EXIF from (may be null)
     */
    fun writeExifMetadata(
        jpegPath: FilePath,
        config: PhotoScanConfiguration,
        sourcePath: FilePath?,
    ) {
        try {
            // Determine the baseline EXIF output set
            val outputSet =
                if (config.copyOriginalExif) {
                    // Copy original EXIF from source file (or fall back to the just-written JPEG)
                    readExifOutputSet(sourcePath ?: jpegPath)
                } else {
                    // Start fresh — no original EXIF is carried forward
                    TiffOutputSet()
                }

            // Apply user overrides on top of the baseline
            applyExifOverrides(outputSet, config)

            // Read the JPEG bytes we just wrote, then rewrite with the EXIF output set
            val jpegBytes = fileSystem.readBytes(jpegPath)
            val baos = ByteArrayOutputStream()
            ExifRewriter().updateExifMetadataLossless(jpegBytes, baos, outputSet)
            baos.close()

            // Write the updated JPEG back to the file
            fileSystem.writeBytes(jpegPath, baos.toByteArray())
        } catch (e: Exception) {
            // EXIF writing is best-effort — if it fails, the JPEG is still valid without EXIF
            System.err.println(
                "[ExifMetadataWriter] Warning: Failed to write EXIF metadata: ${e.message}"
            )
        }
    }

    /**
     * Reads EXIF metadata from a file and returns a mutable [TiffOutputSet]. If the file has no
     * EXIF data, returns a fresh (empty) output set.
     */
    fun readExifOutputSet(path: FilePath): TiffOutputSet {
        return try {
            val bytes = fileSystem.readBytes(path)
            val metadata = Imaging.getMetadata(bytes)
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
     */
    fun applyExifOverrides(outputSet: TiffOutputSet, config: PhotoScanConfiguration) {
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
                    if (config.description.isNotBlank()) {
                        rootDir.removeField(TiffTagConstants.TIFF_TAG_IMAGE_DESCRIPTION)
                        rootDir.add(TiffTagConstants.TIFF_TAG_IMAGE_DESCRIPTION, config.description)
                    }
                }
                OverrideState.KEEP_SOURCE,
                null -> {
                    if (config.description.isNotBlank()) {
                        rootDir.removeField(TiffTagConstants.TIFF_TAG_IMAGE_DESCRIPTION)
                        rootDir.add(TiffTagConstants.TIFF_TAG_IMAGE_DESCRIPTION, config.description)
                    }
                }
            }

            // Make (0x010F)
            ExifValueResolver.applyTriStateField(
                overrideState = config.overrideCameraMake,
                value = config.cameraMake,
                onNullOut = { rootDir.removeField(TiffTagConstants.TIFF_TAG_MAKE) },
                onOverride = { rootDir.add(TiffTagConstants.TIFF_TAG_MAKE, it) },
                legacyPredicate = config.cameraMake.isNotBlank(),
                legacyAction = {
                    rootDir.removeField(TiffTagConstants.TIFF_TAG_MAKE)
                    rootDir.add(TiffTagConstants.TIFF_TAG_MAKE, config.cameraMake)
                },
            )

            // Model (0x0110)
            ExifValueResolver.applyTriStateField(
                overrideState = config.overrideCameraModel,
                value = config.cameraModel,
                onNullOut = { rootDir.removeField(TiffTagConstants.TIFF_TAG_MODEL) },
                onOverride = { rootDir.add(TiffTagConstants.TIFF_TAG_MODEL, it) },
                legacyPredicate = config.cameraModel.isNotBlank(),
                legacyAction = {
                    rootDir.removeField(TiffTagConstants.TIFF_TAG_MODEL)
                    rootDir.add(TiffTagConstants.TIFF_TAG_MODEL, config.cameraModel)
                },
            )

            // --- Exif SubIFD tags ---

            // DateTimeOriginal (0x9003)
            when (config.overrideOriginalDate) {
                OverrideState.NULL_OUT -> {
                    exifDir.removeField(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL)
                    exifDir.removeField(ExifTagConstants.EXIF_TAG_DATE_TIME_DIGITIZED)
                }
                OverrideState.OVERRIDE -> {
                    val dateOriginal = ExifValueResolver.resolveDateOriginal(config)
                    if (dateOriginal != null) {
                        exifDir.removeField(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL)
                        exifDir.add(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL, dateOriginal)
                        exifDir.removeField(ExifTagConstants.EXIF_TAG_DATE_TIME_DIGITIZED)
                        exifDir.add(ExifTagConstants.EXIF_TAG_DATE_TIME_DIGITIZED, dateOriginal)
                    }
                }
                OverrideState.KEEP_SOURCE,
                null -> {
                    val dateOriginal = ExifValueResolver.resolveDateOriginal(config)
                    if (dateOriginal != null) {
                        exifDir.removeField(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL)
                        exifDir.add(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL, dateOriginal)
                        exifDir.removeField(ExifTagConstants.EXIF_TAG_DATE_TIME_DIGITIZED)
                        exifDir.add(ExifTagConstants.EXIF_TAG_DATE_TIME_DIGITIZED, dateOriginal)
                    }
                }
            }

            // LensModel (0xA434)
            ExifValueResolver.applyTriStateField(
                overrideState = config.overrideLensModel,
                value = config.lensModel,
                onNullOut = { exifDir.removeField(ExifTagConstants.EXIF_TAG_LENS_MODEL) },
                onOverride = { exifDir.add(ExifTagConstants.EXIF_TAG_LENS_MODEL, it) },
                legacyPredicate = config.lensModel.isNotBlank(),
                legacyAction = {
                    exifDir.removeField(ExifTagConstants.EXIF_TAG_LENS_MODEL)
                    exifDir.add(ExifTagConstants.EXIF_TAG_LENS_MODEL, config.lensModel)
                },
            )

            // FocalLength (0x920A)
            when (config.overrideFocalLength) {
                OverrideState.NULL_OUT -> {
                    exifDir.removeField(ExifTagConstants.EXIF_TAG_FOCAL_LENGTH)
                }
                OverrideState.OVERRIDE -> {
                    if (config.focalLength.isNotBlank()) {
                        val focalLengthMm = ExifValueResolver.parseFocalLength(config.focalLength)
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
                    if (config.focalLength.isNotBlank()) {
                        val focalLengthMm = ExifValueResolver.parseFocalLength(config.focalLength)
                        if (focalLengthMm != null) {
                            exifDir.removeField(ExifTagConstants.EXIF_TAG_FOCAL_LENGTH)
                            val rational = RationalNumber.valueOf(focalLengthMm)
                            exifDir.add(ExifTagConstants.EXIF_TAG_FOCAL_LENGTH, rational)
                        }
                    }
                }
            }

            // FNumber (0x829D)
            when (config.overrideAperture) {
                OverrideState.NULL_OUT -> {
                    exifDir.removeField(ExifTagConstants.EXIF_TAG_FNUMBER)
                }
                OverrideState.OVERRIDE -> {
                    if (config.aperture.isNotBlank()) {
                        val fNumber = ExifValueResolver.parseAperture(config.aperture)
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
                    if (config.aperture.isNotBlank()) {
                        val fNumber = ExifValueResolver.parseAperture(config.aperture)
                        if (fNumber != null) {
                            exifDir.removeField(ExifTagConstants.EXIF_TAG_FNUMBER)
                            val rational = RationalNumber.valueOf(fNumber)
                            exifDir.add(ExifTagConstants.EXIF_TAG_FNUMBER, rational)
                        }
                    }
                }
            }

            // ExposureTime (0x829A)
            when (config.overrideShutterSpeed) {
                OverrideState.NULL_OUT -> {
                    exifDir.removeField(ExifTagConstants.EXIF_TAG_EXPOSURE_TIME)
                }
                OverrideState.OVERRIDE -> {
                    if (config.shutterSpeed.isNotBlank()) {
                        val rational = ExifValueResolver.parseShutterSpeed(config.shutterSpeed)
                        if (rational != null) {
                            exifDir.removeField(ExifTagConstants.EXIF_TAG_EXPOSURE_TIME)
                            exifDir.add(ExifTagConstants.EXIF_TAG_EXPOSURE_TIME, rational)
                        }
                    }
                }
                OverrideState.KEEP_SOURCE,
                null -> {
                    if (config.shutterSpeed.isNotBlank()) {
                        val rational = ExifValueResolver.parseShutterSpeed(config.shutterSpeed)
                        if (rational != null) {
                            exifDir.removeField(ExifTagConstants.EXIF_TAG_EXPOSURE_TIME)
                            exifDir.add(ExifTagConstants.EXIF_TAG_EXPOSURE_TIME, rational)
                        }
                    }
                }
            }

            // ISOSpeedRatings (0x8827)
            when (config.overrideIso) {
                OverrideState.NULL_OUT -> {
                    exifDir.removeField(ExifTagConstants.EXIF_TAG_ISO)
                }
                OverrideState.OVERRIDE -> {
                    val isoValue = config.iso.trim().toIntOrNull()
                    if (isoValue != null) {
                        exifDir.removeField(ExifTagConstants.EXIF_TAG_ISO)
                        exifDir.add(ExifTagConstants.EXIF_TAG_ISO, isoValue.toShort())
                    }
                }
                OverrideState.KEEP_SOURCE,
                null -> {
                    if (config.iso.isNotBlank()) {
                        val isoValue = config.iso.trim().toIntOrNull()
                        if (isoValue != null) {
                            exifDir.removeField(ExifTagConstants.EXIF_TAG_ISO)
                            exifDir.add(ExifTagConstants.EXIF_TAG_ISO, isoValue.toShort())
                        }
                    }
                }
            }

            // XPKeywords (0x9C9D)
            when (config.overrideKeywords) {
                OverrideState.NULL_OUT -> {
                    rootDir.removeField(MicrosoftTagConstants.EXIF_TAG_XPKEYWORDS)
                }
                OverrideState.OVERRIDE -> {
                    val exifKeywordsValue = ExifValueResolver.resolveKeywords(config)
                    if (exifKeywordsValue != null) {
                        rootDir.removeField(MicrosoftTagConstants.EXIF_TAG_XPKEYWORDS)
                        rootDir.add(MicrosoftTagConstants.EXIF_TAG_XPKEYWORDS, exifKeywordsValue)
                    }
                }
                OverrideState.KEEP_SOURCE,
                null -> {
                    val exifKeywordsValue = ExifValueResolver.resolveKeywords(config)
                    if (exifKeywordsValue != null) {
                        rootDir.removeField(MicrosoftTagConstants.EXIF_TAG_XPKEYWORDS)
                        rootDir.add(MicrosoftTagConstants.EXIF_TAG_XPKEYWORDS, exifKeywordsValue)
                    }
                }
            }

            // --- GPS IFD tags ---
            when (config.overrideGps) {
                OverrideState.NULL_OUT -> {
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
                    if (config.gpsLatitude.isNotBlank() && config.gpsLongitude.isNotBlank()) {
                        writeGpsData(outputSet, config)
                    }
                }
            }
        } catch (e: Exception) {
            System.err.println(
                "[ExifMetadataWriter] Warning: Error applying EXIF overrides: ${e.message}"
            )
        }
    }

    /** Writes GPS latitude/longitude data to the GPS IFD directory. */
    @Suppress("SpreadOperator") // Apache Commons Imaging vararg API requires spread
    private fun writeGpsData(outputSet: TiffOutputSet, config: PhotoScanConfiguration) {
        if (config.gpsLatitude.isBlank() || config.gpsLongitude.isBlank()) return
        val lat = config.gpsLatitude.trim().toDoubleOrNull()
        val lon = config.gpsLongitude.trim().toDoubleOrNull()
        if (lat == null || lon == null) return
        try {
            val gpsDir = outputSet.getOrCreateGPSDirectory()

            // Latitude
            gpsDir.removeField(GpsTagConstants.GPS_TAG_GPS_LATITUDE_REF)
            gpsDir.add(
                GpsTagConstants.GPS_TAG_GPS_LATITUDE_REF,
                if (lat >= 0) GpsTagConstants.GPS_TAG_GPS_LATITUDE_REF_VALUE_NORTH
                else GpsTagConstants.GPS_TAG_GPS_LATITUDE_REF_VALUE_SOUTH,
            )
            gpsDir.removeField(GpsTagConstants.GPS_TAG_GPS_LATITUDE)
            gpsDir.add(
                GpsTagConstants.GPS_TAG_GPS_LATITUDE,
                *ExifValueResolver.decimalToGpsRationals(kotlin.math.abs(lat)),
            )

            // Longitude
            gpsDir.removeField(GpsTagConstants.GPS_TAG_GPS_LONGITUDE_REF)
            gpsDir.add(
                GpsTagConstants.GPS_TAG_GPS_LONGITUDE_REF,
                if (lon >= 0) GpsTagConstants.GPS_TAG_GPS_LONGITUDE_REF_VALUE_EAST
                else GpsTagConstants.GPS_TAG_GPS_LONGITUDE_REF_VALUE_WEST,
            )
            gpsDir.removeField(GpsTagConstants.GPS_TAG_GPS_LONGITUDE)
            gpsDir.add(
                GpsTagConstants.GPS_TAG_GPS_LONGITUDE,
                *ExifValueResolver.decimalToGpsRationals(kotlin.math.abs(lon)),
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
                "[ExifMetadataWriter] Warning: Failed to write GPS data: ${e.message}"
            )
        }
    }
}
