package org.kryspetrie.fileimport.application.export

import com.petrielabs.metadataeditor.domain.WriteMetadataCommand
import org.kryspetrie.fileimport.domain.model.ExifValueResolver
import org.kryspetrie.fileimport.domain.model.FaceRegion
import org.kryspetrie.fileimport.domain.model.OverrideState
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import java.nio.file.Path

/**
 * Maps [PhotoScanConfiguration] fields to ExifTool group-qualified tag names for selective writes.
 *
 * Uses the same tri-state / upsert semantics as the legacy Commons-Imaging writers: blank fields are
 * skipped (preserve source), non-blank fields are written, and [OverrideState.NULL_OUT] clears tags.
 */
object PhotoScanMetadataMapper {

    data class MappedMetadata(
        val command: WriteMetadataCommand,
    )

    fun map(
        filePath: Path,
        config: PhotoScanConfiguration,
        preRotationWidth: Int = 0,
        preRotationHeight: Int = 0,
        faceRegions: List<FaceRegion> = config.faceRegions,
        physicalPixelRotationApplied: Boolean = false,
    ): MappedMetadata {
        val changes = linkedMapOf<String, String>()
        var allowProtectedWrites = false

        fun markProtected() {
            allowProtectedWrites = true
        }

        fun mapTextField(
            override: OverrideState?,
            value: String,
            tags: List<String>,
            protected: Boolean = false,
        ) {
            when (override) {
                OverrideState.NULL_OUT -> {
                    tags.forEach { changes[it] = "" }
                    if (protected) markProtected()
                }
                OverrideState.OVERRIDE -> {
                    if (value.isNotBlank()) {
                        tags.forEach { changes[it] = value.trim() }
                        if (protected) markProtected()
                    }
                }
                OverrideState.KEEP_SOURCE,
                null -> {
                    if (value.isNotBlank()) {
                        tags.forEach { changes[it] = value.trim() }
                        if (protected) markProtected()
                    }
                }
            }
        }

        mapTextField(
            override = config.overrideDescription,
            value = config.description,
            tags =
                listOf(
                    "EXIF:ImageDescription",
                    "IPTC:Caption-Abstract",
                    "XMP-dc:Description",
                ),
        )

        when (config.overrideKeywords) {
            OverrideState.NULL_OUT -> {
                listOf("IPTC:Keywords", "XMP-dc:Subject").forEach { changes[it] = "" }
            }
            OverrideState.OVERRIDE,
            OverrideState.KEEP_SOURCE,
            null -> {
                val keywordParts = buildList {
                    addAll(config.keywordList())
                    addAll(config.subjectList())
                }.distinct()
                if (keywordParts.isNotEmpty()) {
                    val joined = keywordParts.joinToString(", ")
                    changes["IPTC:Keywords"] = joined
                    changes["XMP-dc:Subject"] = joined
                }
            }
        }

        when (config.overrideOriginalDate) {
            OverrideState.NULL_OUT -> {
                listOf(
                        "EXIF:DateTimeOriginal",
                        "EXIF:CreateDate",
                        "EXIF:DateTimeDigitized",
                        "XMP-xmp:CreateDate",
                    )
                    .forEach { changes[it] = "" }
            }
            OverrideState.OVERRIDE,
            OverrideState.KEEP_SOURCE,
            null -> {
                val dateOriginal = ExifValueResolver.resolveDateOriginal(config)
                if (dateOriginal != null) {
                    changes["EXIF:DateTimeOriginal"] = dateOriginal
                    changes["EXIF:CreateDate"] = dateOriginal
                    changes["EXIF:DateTimeDigitized"] = dateOriginal
                    changes["XMP-xmp:CreateDate"] = dateOriginal.replace(' ', 'T')
                }
            }
        }

        mapTextField(
            override = config.overrideCameraMake,
            value = config.cameraMake,
            tags = listOf("EXIF:Make"),
            protected = true,
        )
        mapTextField(
            override = config.overrideCameraModel,
            value = config.cameraModel,
            tags = listOf("EXIF:Model"),
            protected = true,
        )
        mapTextField(
            override = config.overrideLensModel,
            value = config.lensModel,
            tags = listOf("EXIF:LensModel"),
            protected = true,
        )

        when (config.overrideFocalLength) {
            OverrideState.NULL_OUT -> {
                changes["EXIF:FocalLength"] = ""
                markProtected()
            }
            OverrideState.OVERRIDE,
            OverrideState.KEEP_SOURCE,
            null -> {
                if (config.focalLength.isNotBlank()) {
                    ExifValueResolver.parseFocalLength(config.focalLength)?.let { mm ->
                        changes["EXIF:FocalLength"] = "${formatDecimal(mm)} mm"
                        markProtected()
                    }
                }
            }
        }

        when (config.overrideAperture) {
            OverrideState.NULL_OUT -> {
                changes["EXIF:FNumber"] = ""
                markProtected()
            }
            OverrideState.OVERRIDE,
            OverrideState.KEEP_SOURCE,
            null -> {
                if (config.aperture.isNotBlank()) {
                    ExifValueResolver.parseAperture(config.aperture)?.let { fNumber ->
                        changes["EXIF:FNumber"] = formatDecimal(fNumber)
                        markProtected()
                    }
                }
            }
        }

        when (config.overrideShutterSpeed) {
            OverrideState.NULL_OUT -> {
                changes["EXIF:ExposureTime"] = ""
                markProtected()
            }
            OverrideState.OVERRIDE,
            OverrideState.KEEP_SOURCE,
            null -> {
                if (config.shutterSpeed.isNotBlank()) {
                    formatExposureTime(config.shutterSpeed)?.let { formatted ->
                        changes["EXIF:ExposureTime"] = formatted
                        markProtected()
                    }
                }
            }
        }

        when (config.overrideIso) {
            OverrideState.NULL_OUT -> {
                changes["EXIF:ISO"] = ""
                markProtected()
            }
            OverrideState.OVERRIDE,
            OverrideState.KEEP_SOURCE,
            null -> {
                config.iso.trim().toIntOrNull()?.let { iso ->
                    changes["EXIF:ISO"] = iso.coerceIn(0, 65535).toString()
                    markProtected()
                }
            }
        }

        if (config.rotationDegrees != 0) {
            changes["EXIF:Orientation"] =
                if (physicalPixelRotationApplied) {
                    "Horizontal (normal)"
                } else {
                    MetadataRotationHelper.exifOrientationTag(config.rotationDegrees)
                }
            markProtected()
        }

        when (config.overrideGps) {
            OverrideState.NULL_OUT -> {
                listOf(
                        "EXIF:GPSLatitude",
                        "EXIF:GPSLongitude",
                        "EXIF:GPSAltitude",
                        "EXIF:GPSLatitudeRef",
                        "EXIF:GPSLongitudeRef",
                    )
                    .forEach { changes[it] = "" }
            }
            OverrideState.OVERRIDE,
            OverrideState.KEEP_SOURCE,
            null -> {
                if (config.gpsLatitude.isNotBlank() && config.gpsLongitude.isNotBlank()) {
                    config.gpsLatitude.trim().toDoubleOrNull()?.let { lat ->
                        config.gpsLongitude.trim().toDoubleOrNull()?.let { lon ->
                            changes["EXIF:GPSLatitude"] = lat.toString()
                            changes["EXIF:GPSLongitude"] = lon.toString()
                        }
                    }
                }
            }
        }

        if (config.address.isNotBlank()) {
            changes["IPTC:Sub-location"] = config.address.trim()
            changes["XMP-iptcExt:LocationShown"] = config.address.trim()
        } else if (config.locationName.isNotBlank()) {
            changes["IPTC:Sub-location"] = config.locationName.trim()
            changes["XMP-iptcExt:LocationShown"] = config.locationName.trim()
        }
        if (config.city.isNotBlank()) {
            changes["IPTC:City"] = config.city.trim()
            changes["XMP-photoshop:City"] = config.city.trim()
        }
        if (config.state.isNotBlank()) {
            changes["IPTC:Province-State"] = config.state.trim()
            changes["XMP-photoshop:State"] = config.state.trim()
        }
        if (config.country.isNotBlank()) {
            changes["IPTC:Country-PrimaryLocationName"] = config.country.trim()
            changes["XMP-photoshop:Country"] = config.country.trim()
        }

        if (faceRegions.isNotEmpty()) {
            changes["XMP-mwg-rs:RegionInfo"] =
                buildMwgRegionInfo(faceRegions, preRotationWidth, preRotationHeight)
        }

        return MappedMetadata(
            command =
                WriteMetadataCommand(
                    path = filePath,
                    changes = changes,
                    allowProtectedWrites = allowProtectedWrites,
                )
        )
    }

    fun hasWritableChanges(config: PhotoScanConfiguration): Boolean {
        val mapped =
            map(
                filePath = Path.of("placeholder"),
                config = config,
            )
        return mapped.command.changes.isNotEmpty()
    }

    private fun buildMwgRegionInfo(
        regions: List<FaceRegion>,
        imageWidth: Int,
        imageHeight: Int,
    ): String {
        val width = imageWidth.takeIf { it > 0 } ?: 1
        val height = imageHeight.takeIf { it > 0 } ?: 1
        val regionEntries =
            regions.joinToString(",") { region ->
                val areaX = formatDecimal(region.x)
                val areaY = formatDecimal(region.y)
                val areaW = formatDecimal(region.w)
                val areaH = formatDecimal(region.h)
                val name = escapeStructValue(region.name)
                val type = escapeStructValue(region.type.ifBlank { "Face" })
                "{Area={W=$areaW,H=$areaH,X=$areaX,Y=$areaY,Unit=normalized},Name=$name,Type=$type}"
            }
        return "{AppliedToDimensions={W=$width,H=$height,Unit=pixel},RegionList=[$regionEntries]}"
    }

    private fun formatExposureTime(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.contains("/")) return trimmed
        val rational = ExifValueResolver.parseShutterSpeed(trimmed) ?: return null
        return if (rational.numerator == 1L && rational.divisor > 0) {
            "1/${rational.divisor}"
        } else {
            formatDecimal(rational.numerator.toDouble() / rational.divisor.toDouble())
        }
    }

    private fun formatDecimal(value: Double): String =
        if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            String.format(java.util.Locale.US, "%.6f", value).trimEnd('0').trimEnd('.')
        }

    private fun escapeStructValue(value: String): String =
        value.replace("\\", "\\\\").replace(",", "\\,")
}
