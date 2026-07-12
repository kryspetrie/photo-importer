package org.kryspetrie.fileimport.application

import kotlinx.coroutines.Dispatchers
import org.kryspetrie.fileimport.domain.port.DispatcherProvider
import org.kryspetrie.fileimport.domain.port.IdGenerator
import org.kryspetrie.fileimport.domain.port.TimeProvider

/** Test [TimeProvider] that returns a fixed or incrementing timestamp. */
class TestTimeProvider(private var time: Long = 1000L) : TimeProvider {
    override fun currentTimeMillis(): Long = time

    override fun formattedTimestamp(): String = "2026-01-01T00:00:00"

    override fun formatTimestamp(timestamp: Long): String = "2026-01-01 00:00:00"

    fun advanceMs(ms: Long) {
        time += ms
    }
}

/** Test [IdGenerator] that returns sequential IDs. */
class TestIdGenerator(private var counter: Int = 0) : IdGenerator {
    override fun generateId(): String = "test-id-${counter++}"
}

/** Test [DispatcherProvider] that delegates to standard Kotlin dispatchers. */
class TestDispatcherProvider : DispatcherProvider {
    override val io: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
    override val default: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default
}

/** Test [FileSystemPort] that uses real file system operations via [java.io.File]. */
class TestFileSystemAdapter : org.kryspetrie.fileimport.domain.port.FileSystemPort {
    override suspend fun lastModified(path: org.kryspetrie.fileimport.domain.model.FilePath): Long =
        path.toFile().lastModified()

    override suspend fun length(path: org.kryspetrie.fileimport.domain.model.FilePath): Long =
        path.toFile().length()

    override suspend fun exists(path: org.kryspetrie.fileimport.domain.model.FilePath): Boolean =
        path.toFile().exists()

    override suspend fun delete(path: org.kryspetrie.fileimport.domain.model.FilePath): Boolean =
        path.toFile().delete()

    override suspend fun renameTo(
        source: org.kryspetrie.fileimport.domain.model.FilePath,
        destination: org.kryspetrie.fileimport.domain.model.FilePath,
    ): Boolean = source.toFile().renameTo(destination.toFile())

    override suspend fun mkdirs(path: org.kryspetrie.fileimport.domain.model.FilePath): Boolean =
        path.toFile().mkdirs()

    override suspend fun isDirectory(
        path: org.kryspetrie.fileimport.domain.model.FilePath
    ): Boolean = path.toFile().isDirectory

    override suspend fun listFiles(
        path: org.kryspetrie.fileimport.domain.model.FilePath
    ): List<org.kryspetrie.fileimport.domain.model.FilePath> =
        path.toFile().listFiles()?.map {
            org.kryspetrie.fileimport.domain.model.FilePath(it.absolutePath)
        } ?: emptyList()

    override suspend fun copy(
        source: org.kryspetrie.fileimport.domain.model.FilePath,
        destination: org.kryspetrie.fileimport.domain.model.FilePath,
    ): Boolean =
        try {
            destination.toFile().parentFile?.mkdirs()
            source.toFile().copyTo(destination.toFile(), overwrite = true)
            true
        } catch (_: Exception) {
            false
        }

    override fun canWrite(path: org.kryspetrie.fileimport.domain.model.FilePath): Boolean =
        path.toFile().canWrite()

    override fun absolutePath(path: org.kryspetrie.fileimport.domain.model.FilePath): String =
        path.toFile().absolutePath

    override fun walkBottomUp(
        path: org.kryspetrie.fileimport.domain.model.FilePath
    ): Sequence<org.kryspetrie.fileimport.domain.model.FilePath> {
        val dir = path.toFile()
        if (!dir.isDirectory) return emptySequence()
        return dir.walkBottomUp().asSequence().map {
            org.kryspetrie.fileimport.domain.model.FilePath(it.absolutePath)
        }
    }

    override fun readText(path: org.kryspetrie.fileimport.domain.model.FilePath): String =
        path.toFile().readText()

    override fun writeText(path: org.kryspetrie.fileimport.domain.model.FilePath, content: String) {
        path.toFile().parentFile?.mkdirs()
        path.toFile().writeText(content)
    }

    override fun readBytes(path: org.kryspetrie.fileimport.domain.model.FilePath): ByteArray =
        path.toFile().readBytes()

    override fun writeBytes(
        path: org.kryspetrie.fileimport.domain.model.FilePath,
        bytes: ByteArray,
    ) {
        path.toFile().parentFile?.mkdirs()
        path.toFile().writeBytes(bytes)
    }
}
