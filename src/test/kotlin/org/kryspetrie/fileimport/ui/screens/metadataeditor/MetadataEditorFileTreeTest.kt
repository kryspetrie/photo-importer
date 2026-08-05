package org.kryspetrie.fileimport.ui.screens.metadataeditor

import java.io.File
import java.nio.file.Files
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("buildMetadataFileTree")
class MetadataEditorFileTreeTest {

    @Test
    fun buildsHierarchyFromSingleSourceFolder() {
        // GIVEN
        val root = File("/tmp/album")
        val files =
            listOf(File(root, "a.jpg"), File(root, "nested/b.jpg"), File(root, "nested/deep/c.jpg"))

        // WHEN
        val tree = buildMetadataFileTree(files, root.absolutePath)

        // THEN
        assertThat(tree.folder?.absolutePath).isEqualTo(root.absolutePath)
        assertThat(tree.fileIndices).containsExactly(0)
        assertThat(tree.children).hasSize(1)
        assertThat(tree.children.first().name).isEqualTo("nested")
        assertThat(tree.children.first().fileIndices).containsExactly(1)
        assertThat(tree.children.first().children.first().name).isEqualTo("deep")
    }

    @Test
    fun groupsFilesUnderCommonAncestorWhenParentsDiffer() {
        // GIVEN
        val files = listOf(File("/tmp/one/a.jpg"), File("/tmp/two/b.jpg"))

        // WHEN
        val tree = buildMetadataFileTree(files, "")

        // THEN
        assertThat(tree.folder?.absolutePath).isEqualTo(File("/tmp").absolutePath)
        assertThat(tree.children.map { it.name }).containsExactlyInAnyOrder("one", "two")
    }

    @Test
    fun resolveMetadataRootFolderUsesExistingSourceDirectory() {
        // GIVEN
        val source = Files.createTempDirectory("metadata-editor-root").toFile()
        source.deleteOnExit()
        val files = listOf(File("/tmp/other/a.jpg"))

        // WHEN
        val resolved = resolveMetadataRootFolder(files, source.absolutePath)

        // THEN
        assertThat(resolved?.absolutePath).isEqualTo(source.absolutePath)
    }

    @Test
    fun displayNameUsesSourceDirectoryWhenRootIsVirtual() {
        // GIVEN
        val root =
            MetadataFolderNode(
                folder = null,
                name = "",
                fileIndices = emptyList(),
                children = emptyList(),
            )
        val sourceDir = Files.createTempDirectory("metadata-album").toFile()
        sourceDir.deleteOnExit()

        // WHEN / THEN
        assertThat(root.displayName(sourceDir.absolutePath)).isEqualTo(sourceDir.name)
    }
}
