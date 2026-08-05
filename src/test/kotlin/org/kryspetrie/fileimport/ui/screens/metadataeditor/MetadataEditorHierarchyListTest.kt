package org.kryspetrie.fileimport.ui.screens.metadataeditor

import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

@DisplayName("buildHierarchyListItems")
class MetadataEditorHierarchyListTest {

    @TempDir lateinit var tempDir: File

    @Test
    fun collapsedFolderHidesDescendants() {
        val root = tempDir.resolve("album")
        root.mkdirs()
        val sub = root.resolve("2024").also { it.mkdirs() }
        val files =
            listOf(
                root.resolve("a.jpg").also { it.writeText("a") },
                sub.resolve("b.jpg").also { it.writeText("b") },
            )
        val tree = buildMetadataFileTree(files, root.absolutePath)
        val subPath = hierarchyFolderPath(tree.children.single())

        val expanded = defaultExpandedHierarchyPaths(tree) - subPath
        val items = buildHierarchyListItems(tree, expanded)

        assertThat(items.filterIsInstance<MetadataHierarchyItem.FileRow>().map { it.index })
            .containsExactly(0)
    }

    @Test
    fun expandedFoldersIncludeNestedFiles() {
        val root = tempDir.resolve("album")
        root.mkdirs()
        val sub = root.resolve("2024").also { it.mkdirs() }
        val files =
            listOf(
                root.resolve("a.jpg").also { it.writeText("a") },
                sub.resolve("b.jpg").also { it.writeText("b") },
            )
        val tree = buildMetadataFileTree(files, root.absolutePath)
        val items = buildHierarchyListItems(tree, defaultExpandedHierarchyPaths(tree))

        assertThat(items.filterIsInstance<MetadataHierarchyItem.FileRow>().map { it.index })
            .containsExactly(0, 1)
    }
}
