package org.kryspetrie.fileimport.ui.screens.metadataeditor

import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("MetadataEditor column navigation")
class MetadataEditorColumnNavigationTest {

    @Test
    fun initialColumnStackContainsRootOnly() {
        // GIVEN
        val root = buildMetadataFileTree(listOf(File("/tmp/a.jpg")), "/tmp")

        // WHEN / THEN
        assertThat(metadataEditorInitialColumnNodes(root)).containsExactly(root)
    }

    @Test
    fun folderClickExtendsColumnStack() {
        // GIVEN
        val root = File("/tmp/album")
        val tree =
            buildMetadataFileTree(
                listOf(File(root, "a.jpg"), File(root, "nested/b.jpg")),
                root.absolutePath,
            )
        val nested = tree.children.first { it.name == "nested" }
        val initial = metadataEditorInitialColumnNodes(tree)

        // WHEN
        val afterClick =
            metadataEditorColumnNodesAfterFolderClick(
                initial,
                columnIndex = 0,
                selectedChild = nested,
            )

        // THEN
        assertThat(afterClick).containsExactly(tree, nested)
    }

    @Test
    fun folderClickTruncatesForwardColumns() {
        // GIVEN — three-level stack: root → nested → deep
        val root = File("/tmp/album")
        val tree =
            buildMetadataFileTree(
                listOf(File(root, "a.jpg"), File(root, "nested/deep/c.jpg")),
                root.absolutePath,
            )
        val nested = tree.children.first { it.name == "nested" }
        val deep = nested.children.first { it.name == "deep" }
        val deepStack =
            metadataEditorColumnNodesAfterFolderClick(
                metadataEditorColumnNodesAfterFolderClick(
                    metadataEditorInitialColumnNodes(tree),
                    0,
                    nested,
                ),
                1,
                deep,
            )

        // WHEN — re-select nested from column 0
        val truncated = metadataEditorColumnNodesAfterFolderClick(deepStack, 0, nested)

        // THEN
        assertThat(deepStack).hasSize(3)
        assertThat(truncated).containsExactly(tree, nested)
    }

    @Test
    fun columnStackResetsWhenSourceChanges() {
        // GIVEN
        val root = File("/tmp/album")
        val tree =
            buildMetadataFileTree(
                listOf(File(root, "a.jpg"), File(root, "nested/b.jpg")),
                root.absolutePath,
            )
        val nested = tree.children.first { it.name == "nested" }
        val expanded =
            metadataEditorColumnNodesAfterFolderClick(
                metadataEditorInitialColumnNodes(tree),
                0,
                nested,
            )

        // WHEN — new tree simulates sourcePath change resetting remember()
        val newTree = buildMetadataFileTree(listOf(File("/other/x.jpg")), "/other")
        val reset = metadataEditorInitialColumnNodes(newTree)

        // THEN
        assertThat(expanded).hasSize(2)
        assertThat(reset).containsExactly(newTree)
    }
}
