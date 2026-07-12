package org.kryspetrie.fileimport.domain.port

import org.kryspetrie.fileimport.domain.model.FilePath

/**
 * Port interface for file system I/O operations.
 *
 * This port abstracts all direct `java.io.File` operations behind a clean interface, keeping the
 * domain and application layers free of JVM I/O infrastructure. Infrastructure adapters provide the
 * actual file system access.
 *
 * ## Why?
 *
 * Previously, domain models like [ImageFile] held a `java.io.File` reference and application
 * services called `file.lastModified()`, `file.delete()`, etc. directly. This violated hexagonal
 * architecture because the domain layer depended on JVM-specific I/O classes.
 *
 * ## Usage
 *
 * ```kotlin
 * // In application services:
 * val fs: FileSystemPort = koinInject()
 * val lastModified = fs.lastModified(filePath)
 * val deleted = fs.delete(filePath)
 *
 * // In tests:
 * val fs = InMemoryFileSystem()  // test double
 * ```
 *
 * @see FilePath Domain value class representing file paths
 * @see org.kryspetrie.fileimport.infrastructure.adapter.FileSystemAdapter Default implementation
 */
interface FileSystemPort {

    /** Returns the last modification time in epoch milliseconds, or 0 if the file doesn't exist. */
    suspend fun lastModified(path: FilePath): Long

    /** Returns the file size in bytes, or 0 if the file doesn't exist. */
    suspend fun length(path: FilePath): Long

    /** Returns `true` if a file or directory exists at the given path. */
    suspend fun exists(path: FilePath): Boolean

    /** Deletes the file at the given path. Returns `true` if successful. */
    suspend fun delete(path: FilePath): Boolean

    /** Renames the file. Returns `true` if successful. */
    suspend fun renameTo(source: FilePath, destination: FilePath): Boolean

    /** Creates the directory at the given path, including any necessary parent directories. */
    suspend fun mkdirs(path: FilePath): Boolean

    /** Returns `true` if the path is a directory. */
    suspend fun isDirectory(path: FilePath): Boolean

    /** Lists files in a directory. Returns empty list if not a directory or doesn't exist. */
    suspend fun listFiles(path: FilePath): List<FilePath>

    /** Copies a file from source to destination. Returns `true` if successful. */
    suspend fun copy(source: FilePath, destination: FilePath): Boolean

    /** Returns the filename of the given path (e.g., "IMG_001.jpg" from "/photos/IMG_001.jpg"). */
    fun name(path: FilePath): String = path.name

    /** Returns the filename without extension (e.g., "IMG_001" from "IMG_001.jpg"). */
    fun nameWithoutExtension(path: FilePath): String = path.nameWithoutExtension

    /** Returns the file extension without dot (e.g., "jpg" from "IMG_001.jpg"). */
    fun extension(path: FilePath): String = path.extension

    /** Checks if a file can be written to. */
    fun canWrite(path: FilePath): Boolean

    /** Returns the absolute path string for [path]. */
    fun absolutePath(path: FilePath): String = path.toFile().absolutePath

    /**
     * Walks the directory tree at [path] bottom-up, yielding each file/directory found. Returns an
     * empty sequence if [path] is not a directory.
     */
    fun walkBottomUp(path: FilePath): Sequence<FilePath> {
        val dir = path.toFile()
        if (!dir.isDirectory) return emptySequence()
        return dir.walkBottomUp().asSequence().map { FilePath(it.absolutePath) }
    }

    /**
     * Walks the directory tree at [path] top-down, yielding each file/directory found. Returns an
     * empty sequence if [path] is not a directory.
     */
    fun walkTopDown(path: FilePath): Sequence<FilePath> {
        val dir = path.toFile()
        if (!dir.isDirectory) return emptySequence()
        return dir.walkTopDown().asSequence().map { FilePath(it.absolutePath) }
    }

    /**
     * Lists all subdirectories within [path], including [path] itself, walking recursively
     * top-down. Returns empty list if [path] is not a directory.
     */
    fun listDirectoriesRecursive(path: FilePath): List<FilePath> {
        val dir = path.toFile()
        if (!dir.isDirectory) return emptyList()
        return dir.walkTopDown()
            .filter { it.isDirectory }
            .map { FilePath(it.absolutePath) }
            .toList()
    }

    /** Reads the entire content of a file as a UTF-8 string. */
    fun readText(path: FilePath): String = path.toFile().readText()

    /**
     * Writes a UTF-8 string to a file, replacing its content. Creates parent directories if needed.
     */
    fun writeText(path: FilePath, content: String) {
        path.toFile().parentFile?.mkdirs()
        path.toFile().writeText(content)
    }

    /** Reads the entire content of a file as a byte array. */
    fun readBytes(path: FilePath): ByteArray = path.toFile().readBytes()

    /**
     * Writes a byte array to a file, replacing its content. Creates parent directories if needed.
     */
    fun writeBytes(path: FilePath, bytes: ByteArray) {
        path.toFile().parentFile?.mkdirs()
        path.toFile().writeBytes(bytes)
    }
}
