package org.kryspetrie.fileimport.domain.model

/**
 * Value class representing a file path in the domain layer.
 *
 * This abstraction removes direct `java.io.File` dependencies from domain models and ports, keeping
 * the domain layer free of JVM I/O infrastructure. Infrastructure adapters convert `FilePath` to
 * `java.io.File` when performing actual I/O operations.
 *
 * ## Usage
 *
 * ```kotlin
 * // In domain models:
 * data class ImageFile(val path: FilePath, ...)
 *
 * // Creating from a path string:
 * val path = FilePath("/photos/IMG_001.jpg")
 *
 * // Accessing path components:
 * path.name          // "IMG_001.jpg"
 * path.extension     // "jpg"
 * path.nameWithoutExtension  // "IMG_001"
 * path.parent        // "/photos"
 *
 * // In infrastructure adapters:
 * val javaFile = path.toFile()  // java.io.File
 * ```
 *
 * @property path The absolute or relative file path as a string
 */
@JvmInline
value class FilePath(val path: String) : Comparable<FilePath> {

    /** The filename component (e.g., "IMG_001.jpg" from "/photos/IMG_001.jpg"). */
    val name: String
        get() = path.substringAfterLast('/').substringAfterLast('\\')

    /**
     * The file extension without dot (e.g., "jpg" from "IMG_001.jpg"). Empty string if no
     * extension.
     */
    val extension: String
        get() = name.substringAfterLast('.', "")

    /** The filename without its extension (e.g., "IMG_001" from "IMG_001.jpg"). */
    val nameWithoutExtension: String
        get() = name.substringBeforeLast('.')

    /**
     * The parent directory path (e.g., "/photos" from "/photos/IMG_001.jpg"). Null if no parent.
     */
    val parent: String?
        get() {
            val lastSep = path.lastIndexOfAny(charArrayOf('/', '\\'))
            return if (lastSep > 0) path.substring(0, lastSep) else null
        }

    val isAbsolute: Boolean
        get() = path.startsWith('/') || path.contains(':')

    /** Whether this path points to a video file based on extension. */
    val isVideo: Boolean
        get() = ImageFileType.fromExtension(extension).isVideo

    /** Whether this path points to an image file (not video, not unknown). */
    val isImage: Boolean
        get() {
            val type = ImageFileType.fromExtension(extension)
            return !type.isVideo && type != ImageFileType.UNKNOWN
        }

    /** Resolve a relative path against this path as a parent directory. */
    fun resolve(child: String): FilePath = FilePath("${path.trimEnd('/', '\\')}/$child")

    /** Resolve a sibling file with a different extension. */
    fun siblingWithExtension(newExtension: String): FilePath {
        val parentPath = parent ?: return FilePath("${nameWithoutExtension}.$newExtension")
        return FilePath("$parentPath/${nameWithoutExtension}.$newExtension")
    }

    /** Convert to [java.io.File] for JVM I/O operations. Use sparingly in infrastructure layer. */
    fun toFile(): java.io.File = java.io.File(path)

    override fun compareTo(other: FilePath): Int = path.compareTo(other.path)

    override fun toString(): String = path

    companion object {
        /** Create a FilePath from individual parent directory and filename. */
        fun from(parent: String, name: String): FilePath {
            val separator =
                if (parent.isEmpty() || parent.endsWith('/') || parent.endsWith('\\')) "" else "/"
            return FilePath("$parent$separator$name")
        }
    }
}
