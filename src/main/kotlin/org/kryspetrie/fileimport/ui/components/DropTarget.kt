package org.kryspetrie.fileimport.ui.components

import java.awt.datatransfer.DataFlavor
import java.awt.dnd.*
import java.io.File

/**
 * Extracts a folder path from a DnD drop event by checking java file list flavors. Returns the path
 * of the first dropped directory (or parent of a dropped file).
 */
fun extractDroppedPath(transferable: java.awt.datatransfer.Transferable): String? {
  return try {
    if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
      @Suppress("UNCHECKED_CAST")
      val files = transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
      val first = files.firstOrNull() ?: return null
      if (first.isDirectory) first.absolutePath else first.parentFile?.absolutePath
    } else null
  } catch (_: Exception) {
    null
  }
}

/** Creates a DropTargetListener that calls onDrop with the dropped folder path. */
fun createFolderDropListener(onDrop: (String) -> Unit): DropTargetListener {
  return object : DropTargetAdapter() {
    override fun drop(event: DropTargetDropEvent) {
      try {
        event.acceptDrop(DnDConstants.ACTION_COPY)
        val path = extractDroppedPath(event.transferable)
        if (path != null) {
          onDrop(path)
          event.dropComplete(true)
        } else {
          event.dropComplete(false)
        }
      } catch (_: Exception) {
        event.dropComplete(false)
      }
    }
  }
}
