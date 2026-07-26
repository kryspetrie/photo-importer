package org.kryspetrie.fileimport.ui.screens.metadataeditor

/** A navigable row in list/icons folder views (folders first, then files). */
sealed interface MetadataBrowserNavItem {
    data class Folder(val node: MetadataFolderNode, val path: String) : MetadataBrowserNavItem

    data class File(val index: Int) : MetadataBrowserNavItem
}

/** Resolves the folder node for the current navigation stack. */
fun resolveMetadataBrowserFolder(
    fileTree: MetadataFolderNode,
    folderPathStack: List<String>,
): MetadataFolderNode {
    var node = fileTree
    for (path in folderPathStack) {
        node =
            node.children.firstOrNull { child ->
                child.folder?.absolutePath == path || (child.folder == null && child.name == path)
            } ?: return node
    }
    return node
}

/** Ordered items shown in list/icons views and used for arrow-key navigation. */
fun metadataBrowserNavItems(folder: MetadataFolderNode): List<MetadataBrowserNavItem> {
    val folders =
        folder.children.map { child ->
            MetadataBrowserNavItem.Folder(
                node = child,
                path = child.folder?.absolutePath ?: child.name,
            )
        }
    val files = folder.fileIndices.map { MetadataBrowserNavItem.File(it) }
    return folders + files
}

/** Finds the nav position for the current file selection or focused folder. */
fun metadataBrowserNavIndex(
    items: List<MetadataBrowserNavItem>,
    selectedFileIndex: Int,
    focusedFolderPath: String?,
): Int {
    if (focusedFolderPath != null) {
        val folderIndex =
            items.indexOfFirst { item ->
                item is MetadataBrowserNavItem.Folder && item.path == focusedFolderPath
            }
        if (folderIndex >= 0) return folderIndex
    }
    if (selectedFileIndex >= 0) {
        val fileIndex =
            items.indexOfFirst { item ->
                item is MetadataBrowserNavItem.File && item.index == selectedFileIndex
            }
        if (fileIndex >= 0) return fileIndex
    }
    return -1
}

/** Moves a nav index by [delta], wrapping within bounds. */
fun metadataBrowserNavIndexAfterDelta(
    items: List<MetadataBrowserNavItem>,
    currentIndex: Int,
    delta: Int,
): Int {
    if (items.isEmpty()) return -1
    val start =
        when {
            currentIndex >= 0 -> currentIndex
            delta > 0 -> -1
            else -> items.size
        }
    return (start + delta).coerceIn(0, items.lastIndex)
}
