package org.kryspetrie.fileimport.domain.model

import java.io.File
import java.time.LocalDateTime

/**
 * Represents a media file (image or video) in the domain layer.
 *
 * Uses [FilePath] as the primary path representation, keeping the domain layer free of JVM I/O
 * infrastructure. A computed [file] property provides backward compatibility during migration.
 *
 * New code should prefer [path] and [FilePath] operations. The [file] property and [toJavaFile]
 * method are retained for gradual migration and infrastructure-layer convenience only.
 *
 * @property path The file path as a domain [FilePath]
 * @property fileName Display name of the file
 * @property filePath Absolute path string of the file
 * @property fileSize Size of the file in bytes
 * @property fileType The type of image/video file
 * @property hash File hash (MD5, etc.)
 * @property perceptualHash Perceptual hash for similarity detection
 * @property metadata EXIF/metadata extracted from the file
 * @property isSelected Whether this file is selected for import
 * @property importStatus Current import status
 * @property errorMessage Error message if import failed
 * @property sidecars Sidecar file paths (XMP, etc.)
 */
data class ImageFile(
    val id: String = DomainDefaults.generateId(),
    val path: FilePath,
    val fileName: String = path.name,
    val filePath: String = path.path,
    val fileSize: Long = 0L,
    val fileType: ImageFileType = ImageFileType.fromExtension(path.extension),
    val hash: String? = null,
    val perceptualHash: Float? = null,
    val metadata: ImageMetadata? = null,
    val isSelected: Boolean = false,
    val importStatus: ImportStatus = ImportStatus.PENDING,
    val errorMessage: String? = null,
    val sidecars: List<FilePath> = emptyList(),
) {
    /**
     * Backward-compatible access to the underlying [java.io.File].
     *
     * Prefer using [path] and
     * [FileSystemPort][org.kryspetrie.fileimport.domain.port.FileSystemPort] for I/O operations.
     * This property is provided for gradual migration from `java.io.File`.
     */
    val file: File
        get() = path.toFile()

    val dateTaken: LocalDateTime?
        get() = metadata?.dateTimeOriginal

    val dateTakenFormatted: String
        get() = metadata?.dateTimeOriginal?.toLocalDate()?.toString() ?: "Unknown"

    val isVideo: Boolean
        get() = fileType.isVideo

    val isImage: Boolean
        get() = !fileType.isVideo && fileType != ImageFileType.UNKNOWN

    val durationFormatted: String
        get() {
            val secs = metadata?.durationSeconds ?: return ""
            val m = (secs / 60).toInt()
            val s = (secs % 60).toInt()
            return if (m > 0) "${m}m ${s}s" else "${s}s"
        }
}

enum class ImageFileType(
    val extensions: Set<String>,
    val displayName: String,
    val isRaw: Boolean,
    val isVideo: Boolean = false,
    val mimePrefix: String = "image",
) {
    // Photos
    JPEG(setOf("jpg", "jpeg"), "JPEG", false),
    TIFF(setOf("tif", "tiff"), "TIFF", false),
    PNG(setOf("png"), "PNG", false),
    BMP(setOf("bmp"), "BMP", false),
    GIF(setOf("gif"), "GIF", false),
    WEBP(setOf("webp"), "WebP", false),
    HEIF(setOf("heic", "heif"), "HEIF", false),

    // RAW
    RAW_DNG(setOf("dng"), "Adobe DNG", true),
    RAW_ARW(setOf("arw"), "Sony RAW", true),
    RAW_RAF(setOf("raf"), "Fuji RAW", true),
    RAW_NEF(setOf("nef"), "Nikon RAW", true),
    RAW_CR2(setOf("cr2"), "Canon RAW", true),
    RAW_CR3(setOf("cr3"), "Canon RAW", true),
    RAW_ORF(setOf("orf"), "Olympus RAW", true),
    RAW_RW2(setOf("rw2"), "Panasonic RAW", true),
    RAW_PEF(setOf("pef"), "Pentax RAW", true),
    RAW_SRW(setOf("srw"), "Samsung RAW", true),
    RAW_ERF(setOf("erf"), "Epson RAW", true),
    RAW_3FR(setOf("3fr"), "Hasselblad RAW", true),
    RAW_IIQ(setOf("iiq"), "Phase One RAW", true),
    RAW_RWL(setOf("rwl"), "Leica RAW", true),
    RAW_X3F(setOf("x3f"), "Sigma RAW", true),

    // Video
    VIDEO_MP4(setOf("mp4", "m4v"), "MP4", false, isVideo = true, mimePrefix = "video"),
    VIDEO_MOV(setOf("mov"), "QuickTime", false, isVideo = true, mimePrefix = "video"),
    VIDEO_AVI(setOf("avi"), "AVI", false, isVideo = true, mimePrefix = "video"),
    VIDEO_MKV(setOf("mkv"), "Matroska", false, isVideo = true, mimePrefix = "video"),
    VIDEO_WEBM(setOf("webm"), "WebM", false, isVideo = true, mimePrefix = "video"),
    VIDEO_MTS(setOf("mts", "m2ts"), "AVCHD", false, isVideo = true, mimePrefix = "video"),
    VIDEO_WMV(setOf("wmv"), "WMV", false, isVideo = true, mimePrefix = "video"),
    VIDEO_FLV(setOf("flv"), "FLV", false, isVideo = true, mimePrefix = "video"),
    VIDEO_3GP(setOf("3gp", "3g2"), "3GP", false, isVideo = true, mimePrefix = "video"),
    VIDEO_MPG(setOf("mpg", "mpeg", "mpe"), "MPEG", false, isVideo = true, mimePrefix = "video"),
    UNKNOWN(setOf(), "Unknown", false);

    companion object {
        private val extensionMap =
            entries.flatMap { type -> type.extensions.map { it.lowercase() to type } }.toMap()

        fun fromExtension(extension: String): ImageFileType =
            extensionMap[extension.lowercase()] ?: UNKNOWN

        fun isSupported(extension: String): Boolean =
            extensionMap.containsKey(extension.lowercase())

        fun supportedExtensions(): Set<String> = extensionMap.keys

        fun imageExtensions(): Set<String> =
            entries.filter { !it.isVideo && it != UNKNOWN }.flatMap { it.extensions }.toSet()

        fun videoExtensions(): Set<String> =
            entries.filter { it.isVideo }.flatMap { it.extensions }.toSet()
    }

    val isJpeg: Boolean
        get() = this == JPEG

    val isTiff: Boolean
        get() = this == TIFF

    val isRawFormat: Boolean
        get() = isRaw
}

enum class ImportStatus {
    PENDING,
    PROCESSING,
    DUPLICATE,
    COPIED,
    VERIFIED,
    DELETED,
    ERROR,
    SKIPPED,
}
