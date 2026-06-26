package org.kryspetrie.fileimport.application.export

import java.io.File
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.infrastructure.adapter.FileSystemAdapter

@DisplayName("FilenameResolver")
class FilenameResolverTest {

    private val fileSystem = FileSystemAdapter()

    @TempDir
    lateinit var tempDir: File

    private var destPath = FilePath("")

    @BeforeEach
    fun setup() {
        destPath = FilePath(tempDir.absolutePath)
    }

    @Test
    @DisplayName("resolveFilenameConflict returns original name when no conflict")
    fun noConflict() = runTest {
        val result = FilenameResolver.resolveFilenameConflict(fileSystem, destPath, "photo.jpg")
        assertThat(result).endsWith("photo.jpg")
    }

    @Test
    @DisplayName("resolveFilenameConflict appends _1 when file exists")
    fun singleConflict() = runTest {
        // Create existing file
        File(tempDir, "photo.jpg").writeText("existing")
        
        val result = FilenameResolver.resolveFilenameConflict(fileSystem, destPath, "photo.jpg")
        assertThat(result).endsWith("photo_1.jpg")
    }

    @Test
    @DisplayName("resolveFilenameConflict increments until finding unused name")
    fun multipleConflicts() = runTest {
        // Create existing files
        File(tempDir, "photo.jpg").writeText("existing1")
        File(tempDir, "photo_1.jpg").writeText("existing2")
        
        val result = FilenameResolver.resolveFilenameConflict(fileSystem, destPath, "photo.jpg")
        assertThat(result).endsWith("photo_2.jpg")
    }

    @Test
    @DisplayName("resolveFilenameConflict handles files without extension")
    fun noExtension() = runTest {
        File(tempDir, "README").writeText("existing")
        
        val result = FilenameResolver.resolveFilenameConflict(fileSystem, destPath, "README")
        // When no extension, substringAfterLast(".", "jpg") defaults to "jpg"
        assertThat(result).endsWith("README_1.jpg")
    }

    @Test
    @DisplayName("resolveFilenameConflict returns absolute path")
    fun returnsAbsolutePath() = runTest {
        val result = FilenameResolver.resolveFilenameConflict(fileSystem, destPath, "photo.jpg")
        assertThat(result).startsWith("/")
    }

    @Test
    @DisplayName("generateUniqueFileName returns original name when no conflict")
    fun generateUniqueNoConflict() = runTest {
        val result = FilenameResolver.generateUniqueFileName(
            fileSystem, destPath, "photo", "jpg", emptySet()
        )
        assertThat(result).isEqualTo("photo.jpg")
    }

    @Test
    @DisplayName("generateUniqueFileName appends _1 when file exists on disk")
    fun generateUniqueDiskConflict() = runTest {
        File(tempDir, "photo.jpg").writeText("existing")
        
        val result = FilenameResolver.generateUniqueFileName(
            fileSystem, destPath, "photo", "jpg", emptySet()
        )
        assertThat(result).isEqualTo("photo_1.jpg")
    }

    @Test
    @DisplayName("generateUniqueFileName checks against existingExports set")
    fun generateUniqueExportConflict() = runTest {
        val result = FilenameResolver.generateUniqueFileName(
            fileSystem, destPath, "photo", "jpg", setOf("photo.jpg")
        )
        assertThat(result).isEqualTo("photo_1.jpg")
    }

    @Test
    @DisplayName("generateUniqueFileName handles both disk and set conflicts")
    fun generateUniqueBothConflicts() = runTest {
        File(tempDir, "photo.jpg").writeText("existing1")
        File(tempDir, "photo_1.jpg").writeText("existing2")
        
        val result = FilenameResolver.generateUniqueFileName(
            fileSystem, destPath, "photo", "jpg", setOf("photo_2.jpg")
        )
        assertThat(result).isEqualTo("photo_3.jpg")
    }

    @Test
    @DisplayName("generateUniqueFileName returns filename without path")
    fun generateUniqueReturnsFilenameOnly() = runTest {
        val result = FilenameResolver.generateUniqueFileName(
            fileSystem, destPath, "photo", "jpg", emptySet()
        )
        assertThat(result).doesNotContain("/")
    }
}