package org.kryspetrie.fileimport.application.export

import com.petrielabs.metadataeditor.domain.MetadataTag

/**
 * Builds an ExifTool change map that starts from source-file metadata (when
 * [PhotoScanConfiguration.copyOriginalExif] is enabled) and then applies wizard overrides.
 *
 * Overrides always win. Source tags that are unsafe to copy onto a cropped JPEG (file system,
 * composite, thumbnails, geometry) are excluded.
 */
object SourceExifBaselineMerger {

    private val SKIP_GROUPS =
        setOf(
            "File",
            "System",
            "Composite",
            "ExifTool",
            "ICC_Profile",
            "Photoshop",
            "MakerNotes",
            "PrintIM",
            "APP14",
            "JFIF",
            "Adobe",
            "MPF",
            "Apple",
        )

    private val ALLOWED_GROUP_PREFIXES =
        listOf("IFD0", "IFD1", "ExifIFD", "GPS", "InteropIFD", "IPTC", "XMP", "EXIF")

    private val SKIP_TAG_NAMES =
        setOf(
            "ThumbnailImage",
            "ThumbnailOffset",
            "ThumbnailLength",
            "PreviewImage",
            "JpgFromRaw",
            "OtherImage",
            "ImageWidth",
            "ImageHeight",
            "ExifImageWidth",
            "ExifImageHeight",
            "Orientation",
            "ImageSize",
            "Megapixels",
            "FileName",
            "Directory",
            "FileSize",
            "FileModifyDate",
            "FileAccessDate",
            "FileInodeChangeDate",
            "FilePermissions",
            "FileType",
            "FileTypeExtension",
            "MIMEType",
            "ExifByteOrder",
            "EncodingProcess",
            "BitsPerSample",
            "ColorComponents",
            "YCbCrSubSampling",
        )

    /**
     * Returns [overrides] merged on top of transferable [sourceTags]. Keys already present in
     * [overrides] are left unchanged.
     */
    fun merge(sourceTags: List<MetadataTag>, overrides: Map<String, String>): Map<String, String> {
        if (sourceTags.isEmpty()) return overrides
        val merged = linkedMapOf<String, String>()
        for (tag in sourceTags) {
            if (!isTransferable(tag)) continue
            val key = tag.key
            if (key !in overrides) {
                merged[key] = tag.value
            }
        }
        merged.putAll(overrides)
        return merged
    }

    fun isTransferable(tag: MetadataTag): Boolean {
        if (tag.group in SKIP_GROUPS) return false
        if (tag.name in SKIP_TAG_NAMES) return false
        if (tag.value.isBlank()) return false
        // Binary / multi-line blobs are unreliable to round-trip as string assigns
        if (tag.value.startsWith("(Binary data")) return false
        val group = tag.group
        val allowed =
            ALLOWED_GROUP_PREFIXES.any { prefix ->
                group.equals(prefix, ignoreCase = true) ||
                    group.startsWith("$prefix-", ignoreCase = true) ||
                    group.startsWith(prefix, ignoreCase = true) && prefix == "XMP"
            }
        return allowed
    }
}
