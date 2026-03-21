package org.kryspetrie.fileimport.ui.components

import java.awt.datatransfer.DataFlavor
import java.awt.dnd.*
import java.io.File

/**
 * Extracts a folder path from a drag-and-drop (DnD) event by examining the transferable data.
 *
 * This function handles the Java AWT drag-and-drop protocol to retrieve file paths when users
 * drag folders or files from their file explorer and drop them onto a Compose UI component.
 * It supports dropping:
 * - Directories: Returns the directory path
 * - Files: Returns the parent directory path
 * - Multiple items: Returns the first directory, or parent of first file
 *
 * ## Drag-and-Drop Protocol
 *
 * Java AWT uses the [DataFlavor] system to negotiate data types during drag-and-drop:
 * - [DataFlavor.javaFileListFlavor]: Standard flavor for file lists (what we support)
 * - Other flavors: Ignored (returns null)
 *
 * ## Usage Pattern
 *
 * ```kotlin
 * // In a Composable with drag-and-drop support
 * val dropTarget = remember {
 *     DropTarget(
 *         component,
 *         createFolderDropListener { path ->
 *             // Handle dropped folder path
 *             selectedPath = path
 *         }
 *     )
 * }
 * ```
 *
 * ## Error Handling
 *
 * Returns `null` in these cases:
 * - No files in the drop
 * - Unsupported data flavor
 * - Exception during data extraction
 * - Transferable data is inaccessible
 *
 * This defensive approach ensures the UI doesn't crash on invalid drops.
 *
 * @param transferable The data being transferred during the drop operation.
 *                     Contains the file list in Java FileList flavor.
 * @return The absolute path of the dropped directory, or the parent directory of a dropped file.
 *         Returns `null` if extraction fails or no valid path is found.
 *
 * @see createFolderDropListener Creates a DropTargetListener that uses this function
 * @see DataFlavor.javaFileListFlavor Standard Java file list data flavor
 * @see DropTargetListener AWT interface for handling drop events
 */
fun extractDroppedPath(transferable: java.awt.datatransfer.Transferable): String? {
  return try {
    // Check if the transferable supports Java file list flavor
    // This is the standard way to transfer files in Java AWT
    if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
      @Suppress("UNCHECKED_CAST")
      // Extract the file list from the transferable
      // Cast is safe because we checked the flavor first
      val files = transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
      
      // Get the first file/directory from the drop
      val first = files.firstOrNull() ?: return null
      
      // Return directory path if dropped item is a directory,
      // or parent directory path if dropped item is a file
      if (first.isDirectory) first.absolutePath else first.parentFile?.absolutePath
    } else null
  } catch (_: Exception) {
    // Catch any exceptions during data extraction
    // Returns null to indicate drop was not successful
    null
  }
}

/**
 * Creates a [DropTargetListener] that handles folder drag-and-drop operations.
 *
 * This factory function creates a listener that can be attached to any AWT/Swing component
 * to enable drag-and-drop support for folders. When a user drops a folder (or file) onto
 * the component, the provided [onDrop] callback is invoked with the extracted path.
 *
 * ## Drag-and-Drop Lifecycle
 *
 * 1. User drags file/folder over the component
 * 2. AWT calls [DropTargetListener.dragEnter] (not implemented, uses default)
 * 3. User releases mouse (drops)
 * 4. AWT calls [drop] method
 * 5. [extractDroppedPath] extracts the path
 * 6. [onDrop] callback is invoked with the path
 * 7. Drop is marked as complete (success or failure)
 *
 * ## Usage with Compose
 *
 * Compose Desktop doesn't have built-in drag-and-drop, so we use AWT interop:
 *
 * ```kotlin
 * @Composable
 * fun DropZone(onFolderDrop: (String) -> Unit) {
 *     var isHovered by remember { mutableStateOf(false) }
 *     
 *     // Get the underlying AWT component
 *     val compositionLocal = LocalComposeView.current
 *     
 *     LaunchedEffect(Unit) {
 *         // Create drop target on the AWT component
 *         DropTarget(
 *             compositionLocal,
 *             createFolderDropListener { path ->
 *                 onFolderDrop(path)
 *             }
 *         )
 *     }
 *     
 *     Box(
 *         modifier = Modifier
 *             .fillMaxSize()
 *             .background(if (isHovered) Color.Blue else Color.Gray)
 *     ) {
 *         Text("Drop folder here")
 *     }
 * }
 * ```
 *
 * ## Drop Acceptance
 *
 * The listener accepts drops with [DnDConstants.ACTION_COPY], indicating the operation
 * will copy (not move) the files. This is the standard behavior for file imports.
 *
 * ## Error Handling
 *
 * If path extraction fails or an exception occurs:
 * - Drop is marked as unsuccessful (`dropComplete(false)`)
 * - No callback is invoked
 * - UI remains unchanged
 *
 * This prevents crashes and provides clear feedback to the drag-and-drop system.
 *
 * @param onDrop Callback invoked when a folder/file is successfully dropped.
 *               Receives the absolute path of the dropped directory or file's parent.
 *               Not called if drop fails or path extraction fails.
 * @return A [DropTargetListener] instance ready to be attached to a [DropTarget].
 *
 * @see extractDroppedPath Function that extracts path from drop event
 * @see DropTarget AWT class that manages drag-and-drop for a component
 * @see DropTargetAdapter Base class for implementing drop listeners
 */
fun createFolderDropListener(onDrop: (String) -> Unit): DropTargetListener {
  // Create an anonymous object extending DropTargetAdapter
  // DropTargetAdapter provides default implementations for all listener methods
  // We only override the drop() method we care about
  return object : DropTargetAdapter() {
    /**
     * Called when the user releases the drag operation (drops the file/folder).
     *
     * This is the main entry point for handling drop events. The method:
     * 1. Accepts the drop with COPY action
     * 2. Extracts the folder path using [extractDroppedPath]
     * 3. Invokes the callback if path is valid
     * 4. Marks the drop as complete (success or failure)
     *
     * @param event The drop event containing transferable data and drop context
     */
    override fun drop(event: DropTargetDropEvent) {
      try {
        // Accept the drop with COPY action
        // This tells the drag source we'll copy (not move) the files
        event.acceptDrop(DnDConstants.ACTION_COPY)
        
        // Extract the folder path from the drop event
        val path = extractDroppedPath(event.transferable)
        
        // If we successfully extracted a path, invoke callback and mark success
        if (path != null) {
          onDrop(path)
          event.dropComplete(true)
        } else {
          // Path extraction failed, mark drop as unsuccessful
          event.dropComplete(false)
        }
      } catch (_: Exception) {
        // Any exception during drop handling
        // Mark drop as unsuccessful to prevent UI inconsistencies
        event.dropComplete(false)
      }
    }
  }
}
