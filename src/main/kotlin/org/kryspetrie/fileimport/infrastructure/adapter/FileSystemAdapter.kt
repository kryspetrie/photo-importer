package org.kryspetrie.fileimport.infrastructure.adapter

import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.port.FileSystemPort

/**
 * Default JVM implementation of [FileSystemPort] that delegates to `java.io.File`.
 *
 * All I/O operations are dispatched to the calling thread; callers should use a coroutine
 * dispatcher (e.g., `Dispatchers.IO`) for blocking calls.
 */
class FileSystemAdapter : FileSystemPort {

    override suspend fun lastModified(path: FilePath): Long = path.toFile().lastModified()

    override suspend fun length(path: FilePath): Long = path.toFile().length()

    override suspend fun exists(path: FilePath): Boolean = path.toFile().exists()

    override suspend fun delete(path: FilePath): Boolean = path.toFile().delete()

    override suspend fun renameTo(source: FilePath, destination: FilePath): Boolean =
        source.toFile().renameTo(destination.toFile())

    override suspend fun mkdirs(path: FilePath): Boolean = path.toFile().mkdirs()

    override suspend fun isDirectory(path: FilePath): Boolean = path.toFile().isDirectory

    override suspend fun listFiles(path: FilePath): List<FilePath> {
        val dir = path.toFile()
        return dir.listFiles()?.map { FilePath(it.absolutePath) } ?: emptyList()
    }

    override suspend fun copy(source: FilePath, destination: FilePath): Boolean {
        return try {
            destination.toFile().parentFile?.mkdirs()
            source.toFile().copyTo(destination.toFile(), overwrite = true)
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun canWrite(path: FilePath): Boolean = path.toFile().canWrite()
}
