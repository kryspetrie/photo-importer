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

/** Stable path key for a folder node in hierarchy view. */
fun hierarchyFolderPath(node: MetadataFolderNode): String = node.folder?.absolutePath ?: node.name

/** Flat row in virtualized hierarchy view. */
sealed interface MetadataHierarchyItem {
    data class FolderRow(
        val node: MetadataFolderNode,
        val depth: Int,
        val path: String,
        val expanded: Boolean,
    ) : MetadataHierarchyItem

    data class FileRow(val index: Int, val depth: Int) : MetadataHierarchyItem
}

/** Folder paths expanded by default (depth < [maxDepth]). */
fun defaultExpandedHierarchyPaths(
    node: MetadataFolderNode,
    depth: Int = 0,
    maxDepth: Int = 2,
): Set<String> {
    if (depth >= maxDepth) return emptySet()
    val result = mutableSetOf<String>()
    if (node.folder != null && (node.children.isNotEmpty() || node.fileIndices.isNotEmpty())) {
        result.add(hierarchyFolderPath(node))
    }
    val childDepth = if (node.folder != null) depth + 1 else depth
    node.children.forEach { child ->
        result.addAll(defaultExpandedHierarchyPaths(child, childDepth, maxDepth))
    }
    return result
}

/** Builds the visible flat list for a virtualized hierarchy view. */
fun buildHierarchyListItems(
    node: MetadataFolderNode,
    expandedPaths: Set<String>,
    depth: Int = 0,
): List<MetadataHierarchyItem> {
    val items = mutableListOf<MetadataHierarchyItem>()
    if (node.folder != null && (node.children.isNotEmpty() || node.fileIndices.isNotEmpty())) {
        val path = hierarchyFolderPath(node)
        val expanded = path in expandedPaths
        items.add(MetadataHierarchyItem.FolderRow(node, depth, path, expanded))
        if (!expanded) return items
    }
    val fileDepth = if (node.folder != null) depth + 1 else depth
    node.fileIndices.forEach { index -> items.add(MetadataHierarchyItem.FileRow(index, fileDepth)) }
    val childDepth = if (node.folder != null) depth + 1 else depth
    node.children.forEach { child ->
        items.addAll(buildHierarchyListItems(child, expandedPaths, childDepth))
    }
    return items
}

/** Column stack reset when [fileTree] or source path changes. */
fun metadataEditorInitialColumnNodes(fileTree: MetadataFolderNode): List<MetadataFolderNode> =
    listOf(fileTree)

/** Column stack after the user opens a folder in column [columnIndex]. */
fun metadataEditorColumnNodesAfterFolderClick(
    currentColumns: List<MetadataFolderNode>,
    columnIndex: Int,
    selectedChild: MetadataFolderNode,
): List<MetadataFolderNode> = currentColumns.take(columnIndex + 1) + selectedChild
