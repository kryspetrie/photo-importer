package org.kryspetrie.fileimport.application.export

import org.kryspetrie.fileimport.domain.model.ImageFileType

/**
 * Describes which metadata operations a file format supports via ExifTool (photo-metadata-editor).
 *
 * ExifTool performs selective in-place metadata writes for JPEG, TIFF, and all major RAW formats
 * without re-encoding image payloads. Pixel rotation remains JPEG-only because decoding and
 * re-encoding RAW would destroy the original capture data.
 */
enum class MetadataSupport {
    /** Full in-place metadata writing (EXIF, IPTC, XMP, MWG face regions). */
    FULL,

    /** No metadata writing supported. */
    NONE,
}

object FileFormatSupport {

    /** Returns the metadata support level for the given file type. */
    fun metadataSupport(fileType: ImageFileType): MetadataSupport =
        when (fileType) {
            ImageFileType.JPEG,
            ImageFileType.TIFF,
            ImageFileType.PNG,
            ImageFileType.WEBP,
            ImageFileType.HEIF,
            ImageFileType.BMP,
            ImageFileType.GIF,
            ImageFileType.RAW_CR2,
            ImageFileType.RAW_CR3,
            ImageFileType.RAW_NEF,
            ImageFileType.RAW_ARW,
            ImageFileType.RAW_DNG,
            ImageFileType.RAW_RAF,
            ImageFileType.RAW_ORF,
            ImageFileType.RAW_RW2,
            ImageFileType.RAW_PEF,
            ImageFileType.RAW_SRW,
            ImageFileType.RAW_ERF,
            ImageFileType.RAW_3FR,
            ImageFileType.RAW_IIQ,
            ImageFileType.RAW_RWL,
            ImageFileType.RAW_X3F,
            -> MetadataSupport.FULL

            ImageFileType.VIDEO_MP4,
            ImageFileType.VIDEO_MOV,
            ImageFileType.VIDEO_AVI,
            ImageFileType.VIDEO_MKV,
            ImageFileType.VIDEO_WEBM,
            ImageFileType.VIDEO_MTS,
            ImageFileType.VIDEO_WMV,
            ImageFileType.VIDEO_FLV,
            ImageFileType.VIDEO_3GP,
            ImageFileType.VIDEO_MPG,
            ImageFileType.UNKNOWN,
            -> MetadataSupport.NONE
        }

    fun metadataSupportForFile(filePath: String): MetadataSupport {
        val ext = filePath.substringAfterLast('.', "").lowercase()
        return metadataSupport(ImageFileType.fromExtension(ext))
    }

    fun canWriteMetadataInPlace(fileType: ImageFileType): Boolean =
        metadataSupport(fileType) == MetadataSupport.FULL

    /** @deprecated Use [canWriteMetadataInPlace]; ExifTool writes EXIF in-place for RAW too. */
    fun canWriteExifInPlace(fileType: ImageFileType): Boolean = canWriteMetadataInPlace(fileType)

    fun canRotatePixels(fileType: ImageFileType): Boolean =
        when (fileType) {
            ImageFileType.JPEG -> true
            ImageFileType.TIFF -> true
            else -> false
        }

    fun canSetOrientationLossless(fileType: ImageFileType): Boolean = canWriteMetadataInPlace(fileType)

    /** Sidecar XMP is not required when ExifTool writes in-place; kept for callers that prefer it. */
    fun canWriteSidecarXmp(fileType: ImageFileType): Boolean = false

    fun supportDescription(fileType: ImageFileType): String =
        when (metadataSupport(fileType)) {
            MetadataSupport.FULL ->
                when {
                    fileType.isRawFormat ->
                        "RAW format — metadata is written in-place via ExifTool without modifying image data."
                    fileType == ImageFileType.JPEG ->
                        "Full metadata support — EXIF, IPTC, and XMP are written directly into the file."
                    else ->
                        "Metadata is written in-place via ExifTool without re-encoding image data."
                }
            MetadataSupport.NONE -> "This format does not support metadata editing."
        }

    fun isRawFormat(fileType: ImageFileType): Boolean = fileType.isRawFormat

    const val SIDECAR_XMP_EXTENSION = ".xmp"

    fun sidecarXmpPath(imagePath: String): String = "$imagePath$SIDECAR_XMP_EXTENSION"
}
