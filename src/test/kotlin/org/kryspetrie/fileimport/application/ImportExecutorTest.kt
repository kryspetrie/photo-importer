package org.kryspetrie.fileimport.application

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.kryspetrie.fileimport.domain.model.ErrorType
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.ImageFile
import org.kryspetrie.fileimport.domain.model.ImageMetadata
import org.kryspetrie.fileimport.domain.model.ImportConfiguration
import org.kryspetrie.fileimport.domain.model.ImportProgress
import org.kryspetrie.fileimport.domain.port.ImageRepositoryPort
import org.kryspetrie.fileimport.domain.port.NamingPort
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@DisplayName("ImportExecutor")
class ImportExecutorTest {
    private lateinit var namingPort: NamingPort
    private lateinit var fileSystem: TestFileSystemAdapter

    @TempDir lateinit var tempDir: File

    @BeforeEach
    fun setup() {
        namingPort = mock()
        fileSystem = TestFileSystemAdapter()
        whenever(namingPort.generateFolderPath(any(), any(), any())).thenAnswer {
            val destRoot = it.arguments[1] as String
            File(destRoot, "2024-01-01").absolutePath
        }
        whenever(namingPort.generateFileName(any(), any(), any())).thenReturn("out.jpg")
    }

    @Test
    fun hashMismatchDeletesDestAndDoesNotDeleteSource() = runTest {
        val source = File(tempDir, "src.jpg").apply { writeText("source-bytes") }
        val destRoot = File(tempDir, "dest").apply { mkdirs() }
        val image =
            ImageFile(
                path = FilePath(source.absolutePath),
                fileSize = source.length(),
                hash = "abc",
            )
        val imageRepository =
            RecordingImageRepository(verifyCopyResult = false, deleteSourceResult = true)
        val executor = ImportExecutor(imageRepository, namingPort, TestTimeProvider(), fileSystem)

        val result =
            executor.executeImport(
                images = listOf(image),
                destinationPath = destRoot.absolutePath,
                configuration =
                    ImportConfiguration(
                        verifyAfterCopy = true,
                        deleteAfterImport = true,
                        importSidecars = false,
                    ),
                importProgress = MutableStateFlow(ImportProgress()),
            )

        assertThat(result.errorCount).isEqualTo(1)
        assertThat(result.successCount).isEqualTo(0)
        assertThat(result.errors.single().errorType).isEqualTo(ErrorType.HASH_MISMATCH)
        assertThat(source.exists()).isTrue()
        assertThat(imageRepository.deleteCalls).isEmpty()
        assertThat(File(destRoot, "2024-01-01/out.jpg").exists()).isFalse()
    }

    @Test
    fun importsSidecarAlongsidePrimary() = runTest {
        val source = File(tempDir, "src.jpg").apply { writeText("image") }
        File(tempDir, "src.xmp").apply { writeText("<xmp/>") }
        val destRoot = File(tempDir, "dest").apply { mkdirs() }
        val image =
            ImageFile(
                path = FilePath(source.absolutePath),
                fileSize = source.length(),
                sidecars = listOf(FilePath(File(tempDir, "src.xmp").absolutePath)),
            )
        val executor =
            ImportExecutor(
                RecordingImageRepository(verifyCopyResult = true),
                namingPort,
                TestTimeProvider(),
                fileSystem,
            )

        val result =
            executor.executeImport(
                images = listOf(image),
                destinationPath = destRoot.absolutePath,
                configuration =
                    ImportConfiguration(
                        verifyAfterCopy = true,
                        importSidecars = true,
                        deleteAfterImport = false,
                    ),
                importProgress = MutableStateFlow(ImportProgress()),
            )

        assertThat(result.successCount).isEqualTo(1)
        assertThat(File(destRoot, "2024-01-01/out.jpg").exists()).isTrue()
        assertThat(File(destRoot, "2024-01-01/out.xmp").exists()).isTrue()
        assertThat(result.historyEntry!!.fileDetails.single().sidecarsImported).isTrue()
    }

    @Test
    fun deleteAfterImportRemovesSourceWhenCopySucceeds() = runTest {
        val source = File(tempDir, "src.jpg").apply { writeText("image") }
        val destRoot = File(tempDir, "dest").apply { mkdirs() }
        val image = ImageFile(path = FilePath(source.absolutePath), fileSize = source.length())
        val imageRepository =
            RecordingImageRepository(verifyCopyResult = true, deleteSourceResult = true)
        val executor = ImportExecutor(imageRepository, namingPort, TestTimeProvider(), fileSystem)

        val result =
            executor.executeImport(
                images = listOf(image),
                destinationPath = destRoot.absolutePath,
                configuration =
                    ImportConfiguration(
                        verifyAfterCopy = true,
                        deleteAfterImport = true,
                        importSidecars = false,
                    ),
                importProgress = MutableStateFlow(ImportProgress()),
            )

        assertThat(result.successCount).isEqualTo(1)
        assertThat(result.deletedSourceCount).isEqualTo(1)
        assertThat(result.historyEntry!!.fileDetails.single().sourceDeleted).isTrue()
        assertThat(source.exists()).isFalse()
        assertThat(imageRepository.deleteCalls).containsExactly(image.path.path)
    }
}

/** Test double that copies bytes on disk and records delete calls. */
private class RecordingImageRepository(
    private val verifyCopyResult: Boolean,
    private val deleteSourceResult: Boolean = true,
) : ImageRepositoryPort {
    val deleteCalls = mutableListOf<String>()

    override suspend fun scanDirectory(directory: FilePath, recursive: Boolean): List<ImageFile> =
        emptyList()

    override suspend fun getMetadata(imageFile: ImageFile): ImageMetadata? = null

    override suspend fun calculateFileHash(imageFile: ImageFile, algorithm: String): String = "hash"

    override suspend fun calculatePerceptualHash(imageFile: ImageFile): Float? = null

    override suspend fun copyFile(
        source: ImageFile,
        destination: FilePath,
        onProgress: (Long, Long) -> Unit,
    ): Boolean {
        val src = source.path.toFile()
        val dest = destination.toFile()
        dest.parentFile.mkdirs()
        src.copyTo(dest, overwrite = true)
        onProgress(source.fileSize, source.fileSize)
        return true
    }

    override suspend fun verifyCopy(source: ImageFile, destination: FilePath): Boolean =
        verifyCopyResult

    override suspend fun deleteFile(imageFile: ImageFile): Boolean {
        deleteCalls.add(imageFile.path.path)
        if (!deleteSourceResult) return false
        return imageFile.path.toFile().delete()
    }

    override suspend fun fileExists(file: FilePath): Boolean = file.toFile().exists()

    override fun getSupportedExtensions(): Set<String> = setOf("jpg", "jpeg", "png", "xmp")
}
