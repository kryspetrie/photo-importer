package org.kryspetrie.fileimport.ui.screens.metadataeditor

import java.io.File

/**
 * Folder node for hierarchy and column views in the metadata editor.
 *
 * @property folder Absolute folder on disk, or null for a virtual root (multi-folder selection).
 * @property name Display name for this node.
 * @property fileIndices Indices into [BulkEditState.files] for images directly in this folder.
 * @property children Immediate subfolders sorted by name.
 */
data class MetadataFolderNode(
    val folder: File?,
    val name: String,
    val fileIndices: List<Int>,
    val children: List<MetadataFolderNode>,
) {
    val isEmpty: Boolean
        get() = fileIndices.isEmpty() && children.all { it.isEmpty }

    /** User-visible label for this node in folder navigation. */
    fun displayName(sourcePath: String): String {
        if (folder != null) return name
        if (sourcePath.isNotBlank()) {
            val source = File(sourcePath)
            return when {
                source.isDirectory -> source.name
                source.isFile -> source.parentFile?.name ?: source.name
                else -> source.name
            }
        }
        return name
    }
}

/** Builds a folder tree from loaded files and the current source path. */
fun buildMetadataFileTree(files: List<File>, sourcePath: String): MetadataFolderNode {
    if (files.isEmpty()) {
        return MetadataFolderNode(folder = null, name = "", fileIndices = emptyList(), children = emptyList())
    }

    val root = resolveMetadataRootFolder(files, sourcePath)
    if (root == null) {
        return buildVirtualRootByParent(files)
    }

    val pathToIndex = files.withIndex().associate { (index, file) -> file.absolutePath to index }
    return buildFolderNode(root, pathToIndex)
}

internal fun resolveMetadataRootFolder(files: List<File>, sourcePath: String): File? {
    if (sourcePath.isNotBlank()) {
        val source = File(sourcePath)
        if (source.isDirectory) return source
    }
    if (files.size == 1) return files.first().parentFile
    val parents = files.mapNotNull { it.parentFile }.distinct()
    if (parents.size == 1) return parents.first()
    return findCommonAncestorFolder(files)
}

private fun buildVirtualRootByParent(files: List<File>): MetadataFolderNode {
    val grouped =
        files.withIndex().groupBy { (_, file) -> file.parentFile?.absolutePath ?: "" }.toSortedMap()
    val children =
        grouped.map { (parentPath, entries) ->
            val folder = parentPath.takeIf { it.isNotBlank() }?.let { File(it) }
            MetadataFolderNode(
                folder = folder,
                name = folder?.name ?: entries.first().value.name,
                fileIndices = entries.map { it.index }.sorted(),
                children = emptyList(),
            )
        }
    return MetadataFolderNode(
        folder = null,
        name = "",
        fileIndices = emptyList(),
        children = children,
    )
}

private fun buildFolderNode(folder: File, pathToIndex: Map<String, Int>): MetadataFolderNode {
    val prefix = folder.absolutePath + File.separator
    val direct = mutableListOf<Int>()
    val childNames = linkedSetOf<String>()

    for ((path, index) in pathToIndex) {
        when {
            path == folder.absolutePath -> direct.add(index)
            path.startsWith(prefix) -> {
                val relative = path.removePrefix(prefix)
                val segment = relative.substringBefore(File.separatorChar)
                if (relative.contains(File.separator)) {
                    childNames.add(segment)
                } else {
                    direct.add(index)
                }
            }
        }
    }

    val children =
        childNames.sortedBy { it.lowercase() }.map { name ->
            buildFolderNode(File(folder, name), pathToIndex)
        }

    return MetadataFolderNode(
        folder = folder,
        name = folder.name,
        fileIndices = direct.sorted(),
        children = children,
    )
}

private fun findCommonAncestorFolder(files: List<File>): File? {
    if (files.isEmpty()) return null
    var common = files.first().parentFile ?: return null
    for (file in files.drop(1)) {
        common = commonAncestor(common, file.parentFile) ?: return null
    }
    return common
}

private fun commonAncestor(a: File?, b: File?): File? {
    if (a == null || b == null) return null
    val aPath = a.absolutePath
    val bPath = b.absolutePath
    var index = 0
    while (index < aPath.length && index < bPath.length && aPath[index] == bPath[index]) {
        index++
    }
    val commonPath = aPath.substring(0, index).trimEnd(File.separatorChar)
    return commonPath.takeIf { it.isNotBlank() }?.let { File(it) }
}
