package org.kryspetrie.fileimport.domain.port

import java.io.File
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.infrastructure.adapter.FileSystemAdapter

@DisplayName("FileSystemPort contract")
class FileSystemPortContractTest {

    private val fileSystem: FileSystemPort = FileSystemAdapter()

    @TempDir lateinit var tempDir: File

    @Test
    @DisplayName("exists returns true for existing file")
    fun existsReturnsTrueForExistingFile() = runTest {
        val file = File(tempDir, "test.txt")
        file.writeText("hello")
        assertThat(fileSystem.exists(FilePath(file.absolutePath))).isTrue()
    }

    @Test
    @DisplayName("exists returns false for non-existent file")
    fun existsReturnsFalseForNonExistentFile() = runTest {
        assertThat(fileSystem.exists(FilePath("/nonexistent/path/file.txt"))).isFalse()
    }

    @Test
    @DisplayName("isDirectory returns true for directory")
    fun isDirectoryReturnsTrueForDirectory() = runTest {
        assertThat(fileSystem.isDirectory(FilePath(tempDir.absolutePath))).isTrue()
    }

    @Test
    @DisplayName("isDirectory returns false for file")
    fun isDirectoryReturnsFalseForFile() = runTest {
        val file = File(tempDir, "test.txt")
        file.writeText("hello")
        assertThat(fileSystem.isDirectory(FilePath(file.absolutePath))).isFalse()
    }

    @Test
    @DisplayName("delete removes file and returns true")
    fun deleteRemovesFile() = runTest {
        val file = File(tempDir, "todelete.txt")
        file.writeText("hello")
        assertThat(fileSystem.delete(FilePath(file.absolutePath))).isTrue()
        assertThat(file.exists()).isFalse()
    }

    @Test
    @DisplayName("renameTo moves file to new location")
    fun renameToMovesFile() = runTest {
        val source = File(tempDir, "source.txt")
        source.writeText("hello")
        val dest = File(tempDir, "dest.txt")
        assertThat(fileSystem.renameTo(FilePath(source.absolutePath), FilePath(dest.absolutePath)))
            .isTrue()
        assertThat(source.exists()).isFalse()
        assertThat(dest.exists()).isTrue()
        assertThat(dest.readText()).isEqualTo("hello")
    }

    @Test
    @DisplayName("mkdirs creates directory and parent directories")
    fun mkdirsCreatesNestedDirectories() = runTest {
        val newDir = File(tempDir, "a/b/c")
        assertThat(fileSystem.mkdirs(FilePath(newDir.absolutePath))).isTrue()
        assertThat(newDir.exists()).isTrue()
        assertThat(newDir.isDirectory).isTrue()
    }

    @Test
    @DisplayName("copy duplicates file content")
    fun copyDuplicatesFile() = runTest {
        val source = File(tempDir, "source.txt")
        source.writeText("hello world")
        val dest = File(tempDir, "dest.txt")
        assertThat(fileSystem.copy(FilePath(source.absolutePath), FilePath(dest.absolutePath)))
            .isTrue()
        assertThat(dest.exists()).isTrue()
        assertThat(dest.readText()).isEqualTo("hello world")
        assertThat(source.exists()).isTrue()
    }

    @Test
    @DisplayName("copy creates parent directories for destination")
    fun copyCreatesParentDirectories() = runTest {
        val source = File(tempDir, "source.txt")
        source.writeText("hello")
        val dest = File(tempDir, "subdir/nested/dest.txt")
        assertThat(fileSystem.copy(FilePath(source.absolutePath), FilePath(dest.absolutePath)))
            .isTrue()
        assertThat(dest.exists()).isTrue()
    }

    @Test
    @DisplayName("lastModified returns non-zero for existing file")
    fun lastModifiedReturnsNonZero() = runTest {
        val file = File(tempDir, "test.txt")
        file.writeText("hello")
        assertThat(fileSystem.lastModified(FilePath(file.absolutePath))).isGreaterThan(0)
    }

    @Test
    @DisplayName("length returns file size")
    fun lengthReturnsFileSize() = runTest {
        val file = File(tempDir, "test.txt")
        file.writeText("hello world")
        assertThat(fileSystem.length(FilePath(file.absolutePath))).isEqualTo(11L)
    }

    @Test
    @DisplayName("listFiles returns files in directory")
    fun listFilesReturnsDirectoryContents() = runTest {
        File(tempDir, "a.txt").writeText("a")
        File(tempDir, "b.txt").writeText("b")
        val result = fileSystem.listFiles(FilePath(tempDir.absolutePath))
        assertThat(result).hasSize(2)
        val names = result.map { it.name }
        assertThat(names).containsExactlyInAnyOrder("a.txt", "b.txt")
    }

    @Test
    @DisplayName("name, nameWithoutExtension, extension work correctly")
    fun nameOperationsWork() {
        val path = FilePath("/photos/IMG_001.jpg")
        assertThat(fileSystem.name(path)).isEqualTo("IMG_001.jpg")
        assertThat(fileSystem.nameWithoutExtension(path)).isEqualTo("IMG_001")
        assertThat(fileSystem.extension(path)).isEqualTo("jpg")
    }

    @Test
    @DisplayName("absolutePath returns absolute path")
    fun absolutePathReturnsAbsolutePath() {
        val path = FilePath("relative/path.txt")
        val absPath = fileSystem.absolutePath(path)
        assertThat(absPath).isNotEmpty()
        assertThat(absPath).contains("path.txt")
    }

    @Test
    @DisplayName("walkBottomUp walks directory tree")
    fun walkBottomUpWalksTree() {
        val dir = File(tempDir, "root")
        dir.mkdirs()
        File(dir, "a.txt").writeText("a")
        val subDir = File(dir, "sub")
        subDir.mkdirs()
        File(subDir, "b.txt").writeText("b")

        val result = fileSystem.walkBottomUp(FilePath(dir.absolutePath)).toList()
        val names = result.map { it.name }
        assertThat(names).containsExactlyInAnyOrder("b.txt", "sub", "a.txt", "root")
    }

    @Test
    @DisplayName("walkBottomUp returns empty sequence for non-directory")
    fun walkBottomUpEmptyForNonDirectory() {
        val file = File(tempDir, "test.txt")
        file.writeText("hello")
        val result = fileSystem.walkBottomUp(FilePath(file.absolutePath)).toList()
        assertThat(result).isEmpty()
    }

    @Test
    @DisplayName("readText and writeText work correctly")
    fun readWriteTextWork() {
        val file = File(tempDir, "test.txt")
        val path = FilePath(file.absolutePath)
        fileSystem.writeText(path, "hello world")
        assertThat(fileSystem.readText(path)).isEqualTo("hello world")
    }

    @Test
    @DisplayName("writeText creates parent directories")
    fun writeTextCreatesParentDirs() {
        val path = FilePath("${tempDir.absolutePath}/sub/nested/test.txt")
        fileSystem.writeText(path, "nested content")
        assertThat(fileSystem.readText(path)).isEqualTo("nested content")
    }

    @Test
    @DisplayName("canWrite returns true for writable file")
    fun canWriteReturnsTrueForWritableFile() {
        val file = File(tempDir, "test.txt")
        file.writeText("hello")
        assertThat(fileSystem.canWrite(FilePath(file.absolutePath))).isTrue()
    }
}
