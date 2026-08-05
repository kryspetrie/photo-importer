package org.kryspetrie.fileimport.application

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.ImageFile
import org.kryspetrie.fileimport.domain.model.JournalEntry
import org.kryspetrie.fileimport.domain.model.ReorganizeMapping
import org.kryspetrie.fileimport.domain.model.ReorganizeMode
import org.kryspetrie.fileimport.domain.port.FileSystemPort
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@DisplayName("FileOperationExecutor")
class FileOperationExecutorTest {

    private val fileSystem = mock<FileSystemPort>()
    private val executor = FileOperationExecutor(TestDispatcherProvider(), fileSystem)

    @Test
    fun moveFallsBackToCopyDeleteWhenRenameFails() = runTest {
        val source = FilePath("/volA/photo.jpg")
        val dest = FilePath("/volB/2024/photo.jpg")
        whenever(fileSystem.exists(source)).thenReturn(true)
        whenever(fileSystem.exists(dest)).thenReturn(false)
        whenever(fileSystem.absolutePath(source)).thenReturn(source.path)
        whenever(fileSystem.absolutePath(dest)).thenReturn(dest.path)
        whenever(fileSystem.name(source)).thenReturn("photo.jpg")
        whenever(fileSystem.name(dest)).thenReturn("photo.jpg")
        whenever(fileSystem.length(dest)).thenReturn(42L)
        whenever(fileSystem.renameTo(source, dest)).thenReturn(false)
        whenever(fileSystem.copy(source, dest)).thenReturn(true)
        whenever(fileSystem.delete(source)).thenReturn(true)

        val result =
            executor.executeOperation(
                ReorganizeMapping(
                    file = ImageFile(path = source, fileSize = 42L),
                    currentPath = source.path,
                    newPath = dest.path,
                    newFileName = "photo.jpg",
                    mode = ReorganizeMode.MOVE,
                )
            )

        assertThat(result.error).isNull()
        assertThat(result.movedCount).isEqualTo(1)
        assertThat(result.journalEntry?.wasSuccessful).isTrue()
        verify(fileSystem).copy(source, dest)
        verify(fileSystem).delete(source)
    }

    @Test
    fun moveUsesRenameWhenPossible() = runTest {
        val source = FilePath("/lib/a.jpg")
        val dest = FilePath("/lib/2024/a.jpg")
        whenever(fileSystem.exists(source)).thenReturn(true)
        whenever(fileSystem.exists(dest)).thenReturn(false)
        whenever(fileSystem.absolutePath(source)).thenReturn(source.path)
        whenever(fileSystem.absolutePath(dest)).thenReturn(dest.path)
        whenever(fileSystem.name(source)).thenReturn("a.jpg")
        whenever(fileSystem.name(dest)).thenReturn("a.jpg")
        whenever(fileSystem.length(dest)).thenReturn(10L)
        whenever(fileSystem.renameTo(source, dest)).thenReturn(true)

        val result =
            executor.executeOperation(
                ReorganizeMapping(
                    file = ImageFile(path = source, fileSize = 10L),
                    currentPath = source.path,
                    newPath = dest.path,
                    newFileName = "a.jpg",
                    mode = ReorganizeMode.MOVE,
                )
            )

        assertThat(result.movedCount).isEqualTo(1)
        verify(fileSystem, never()).copy(any(), any())
        verify(fileSystem, never()).delete(any())
    }

    @Test
    fun undoMoveFallsBackToCopyDeleteWhenRenameFails() = runTest {
        val current = FilePath("/volB/2024/photo.jpg")
        val original = FilePath("/volA/photo.jpg")
        whenever(fileSystem.exists(current)).thenReturn(true)
        whenever(fileSystem.renameTo(current, original)).thenReturn(false)
        whenever(fileSystem.copy(current, original)).thenReturn(true)
        whenever(fileSystem.delete(current)).thenReturn(true)

        val result =
            executor.executeUndo(
                JournalEntry(
                    originalPath = original.path,
                    newPath = current.path,
                    originalFilename = "photo.jpg",
                    newFilename = "photo.jpg",
                    originalParent = "/volA",
                    newParent = "/volB/2024",
                    operationType = ReorganizeMode.MOVE,
                    wasSuccessful = true,
                    fileSize = 42L,
                )
            )

        assertThat(result.error).isNull()
        assertThat(result.restoredCount).isEqualTo(1)
        verify(fileSystem).copy(current, original)
        verify(fileSystem).delete(current)
    }
}
