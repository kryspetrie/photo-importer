package org.kryspetrie.fileimport.ui.screens.metadataeditor

import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("MetadataEditorBrowserNavigation")
class MetadataEditorBrowserNavigationTest {

    @Test
    fun navItemsListFoldersBeforeFiles() {
        // GIVEN
        val root = File("/tmp/album")
        val tree =
            buildMetadataFileTree(
                listOf(
                    File(root, "a.jpg"),
                    File(root, "nested/b.jpg"),
                ),
                root.absolutePath,
            )

        // WHEN
        val items = metadataBrowserNavItems(tree)

        // THEN
        assertThat(items).hasSize(2)
        assertThat(items[0]).isInstanceOf(MetadataBrowserNavItem.Folder::class.java)
        assertThat(items[1]).isEqualTo(MetadataBrowserNavItem.File(0))
    }

    @Test
    fun resolveFolderStackDescendsIntoChild() {
        // GIVEN
        val root = File("/tmp/album")
        val tree =
            buildMetadataFileTree(
                listOf(
                    File(root, "a.jpg"),
                    File(root, "nested/b.jpg"),
                ),
                root.absolutePath,
            )
        val nestedPath = File(root, "nested").absolutePath

        // WHEN
        val resolved = resolveMetadataBrowserFolder(tree, listOf(nestedPath))

        // THEN
        assertThat(resolved.fileIndices).containsExactly(1)
        assertThat(resolved.children).isEmpty()
    }

    @Test
    fun navIndexFindsFocusedFolderOrSelectedFile() {
        // GIVEN
        val items =
            listOf(
                MetadataBrowserNavItem.Folder(
                    node =
                        MetadataFolderNode(
                            folder = File("/tmp/nested"),
                            name = "nested",
                            fileIndices = emptyList(),
                            children = emptyList(),
                        ),
                    path = "/tmp/nested",
                ),
                MetadataBrowserNavItem.File(0),
            )

        // WHEN / THEN
        assertThat(metadataBrowserNavIndex(items, selectedFileIndex = 0, focusedFolderPath = null))
            .isEqualTo(1)
        assertThat(metadataBrowserNavIndex(items, selectedFileIndex = -1, focusedFolderPath = "/tmp/nested"))
            .isEqualTo(0)
        assertThat(metadataBrowserNavIndex(items, selectedFileIndex = -1, focusedFolderPath = null))
            .isEqualTo(-1)
    }

    @Test
    fun navIndexAfterDeltaMovesWithinBounds() {
        // GIVEN
        val items =
            listOf(
                MetadataBrowserNavItem.Folder(
                    node =
                        MetadataFolderNode(
                            folder = File("/tmp/nested"),
                            name = "nested",
                            fileIndices = emptyList(),
                            children = emptyList(),
                        ),
                    path = "/tmp/nested",
                ),
                MetadataBrowserNavItem.File(0),
            )

        // WHEN / THEN
        assertThat(metadataBrowserNavIndexAfterDelta(items, currentIndex = -1, delta = 1)).isEqualTo(0)
        assertThat(metadataBrowserNavIndexAfterDelta(items, currentIndex = 0, delta = 1)).isEqualTo(1)
        assertThat(metadataBrowserNavIndexAfterDelta(items, currentIndex = 1, delta = 1)).isEqualTo(1)
        assertThat(metadataBrowserNavIndexAfterDelta(items, currentIndex = 0, delta = -1)).isEqualTo(0)
    }
}
