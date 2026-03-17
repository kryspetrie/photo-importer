package org.kryspetrie.fileimport.ui.components

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.io.TempDir

@DisplayName("DropTarget")
class DropTargetTest {

  @TempDir lateinit var tempDir: File

  @Nested
  @DisplayName("extractDroppedPath")
  inner class ExtractDroppedPathTest {

    @Test
    @DisplayName("should return directory path when dropping a directory")
    fun shouldReturnDirPath() {
      val dir = File(tempDir, "photos").apply { mkdirs() }
      val transferable = FileListTransferable(listOf(dir))

      val result = extractDroppedPath(transferable)

      assertThat(result).isEqualTo(dir.absolutePath)
    }

    @Test
    @DisplayName("should return parent directory when dropping a file")
    fun shouldReturnParentOfFile() {
      val file = File(tempDir, "image.jpg").apply { createNewFile() }
      val transferable = FileListTransferable(listOf(file))

      val result = extractDroppedPath(transferable)

      assertThat(result).isEqualTo(tempDir.absolutePath)
    }

    @Test
    @DisplayName("should return null for empty file list")
    fun shouldReturnNullForEmpty() {
      val transferable = FileListTransferable(emptyList())

      val result = extractDroppedPath(transferable)

      assertThat(result).isNull()
    }

    @Test
    @DisplayName("should return null for unsupported flavor")
    fun shouldReturnNullForUnsupported() {
      val transferable = UnsupportedTransferable()

      val result = extractDroppedPath(transferable)

      assertThat(result).isNull()
    }

    @Test
    @DisplayName("should return null when exception occurs")
    fun shouldReturnNullOnException() {
      val transferable = ThrowingTransferable()

      val result = extractDroppedPath(transferable)

      assertThat(result).isNull()
    }

    @Test
    @DisplayName("should use first directory when multiple files dropped")
    fun shouldUseFirstDir() {
      val dir1 = File(tempDir, "dir1").apply { mkdirs() }
      val dir2 = File(tempDir, "dir2").apply { mkdirs() }
      val transferable = FileListTransferable(listOf(dir1, dir2))

      val result = extractDroppedPath(transferable)

      assertThat(result).isEqualTo(dir1.absolutePath)
    }
  }

  @Nested
  @DisplayName("createFolderDropListener")
  inner class CreateFolderDropListenerTest {

    @Test
    @DisplayName("should create non-null listener")
    fun shouldCreateListener() {
      val listener = createFolderDropListener {}

      assertThat(listener).isNotNull()
    }
  }
}

private class FileListTransferable(private val files: List<File>) : Transferable {
  override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.javaFileListFlavor)

  override fun isDataFlavorSupported(flavor: DataFlavor): Boolean =
      flavor == DataFlavor.javaFileListFlavor

  override fun getTransferData(flavor: DataFlavor): Any {
    if (flavor == DataFlavor.javaFileListFlavor) return files
    throw UnsupportedFlavorException(flavor)
  }
}

private class UnsupportedTransferable : Transferable {
  override fun getTransferDataFlavors(): Array<DataFlavor> = emptyArray()

  override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = false

  override fun getTransferData(flavor: DataFlavor): Any = throw UnsupportedFlavorException(flavor)
}

private class ThrowingTransferable : Transferable {
  override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.javaFileListFlavor)

  override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = true

  override fun getTransferData(flavor: DataFlavor): Any = throw RuntimeException("Test exception")
}
