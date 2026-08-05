package org.kryspetrie.fileimport.application.thumbnails

import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.kryspetrie.fileimport.domain.model.FilePath

@DisplayName("FolderThumbnailPaths")
class FolderThumbnailPathsTest {

    @TempDir lateinit var tempDir: File

    @Test
    fun albumRootUsesEditorFolderWhenProvided() {
        // GIVEN
        val album = File(tempDir, "album").apply { mkdirs() }
        val photo = File(album, "IMG_001.jpg").apply { writeBytes(byteArrayOf(1)) }

        // WHEN
        val root = FolderThumbnailPaths.albumRoot(FilePath(photo.absolutePath), album.absolutePath)

        // THEN
        assertThat(root.path).isEqualTo(album.canonicalPath)
    }

    @Test
    fun albumRootUsesParentWhenEditorSourceIsSingleFile() {
        // GIVEN
        val album = File(tempDir, "album").apply { mkdirs() }
        val photo = File(album, "IMG_001.jpg").apply { writeBytes(byteArrayOf(1)) }

        // WHEN
        val root = FolderThumbnailPaths.albumRoot(FilePath(photo.absolutePath), photo.absolutePath)

        // THEN
        assertThat(root.path).isEqualTo(album.canonicalPath)
    }

    @Test
    fun albumRootUsesSourceParentWhenEditorSourceNull() {
        // GIVEN
        val album = File(tempDir, "album").apply { mkdirs() }
        val photo = File(album, "IMG_001.jpg").apply { writeBytes(byteArrayOf(1)) }

        // WHEN
        val root =
            FolderThumbnailPaths.albumRoot(FilePath(photo.absolutePath), editorSourcePath = null)

        // THEN
        assertThat(root.path).isEqualTo(album.canonicalPath)
    }

    @Test
    fun thumbsDirectoryIsUnderAlbumRoot() {
        // GIVEN
        val album = File(tempDir, "album").apply { mkdirs() }
        val root = FilePath(album.canonicalPath)

        // WHEN
        val thumbs = FolderThumbnailPaths.thumbsDirectory(root)

        // THEN
        assertThat(thumbs.path).endsWith("${File.separator}.thumbs")
    }

    @Test
    fun cachePathMirrorsRelativeLayout() {
        // GIVEN
        val album = File(tempDir, "album").apply { mkdirs() }
        val sub = File(album, "2024").apply { mkdirs() }
        val photo = File(sub, "IMG_001.cr2").apply { writeBytes(byteArrayOf(1)) }
        val root = FolderThumbnailPaths.albumRoot(FilePath(photo.absolutePath), album.absolutePath)

        // WHEN
        val cachePath = FolderThumbnailPaths.cacheFilePath(root, FilePath(photo.absolutePath))

        // THEN
        assertThat(cachePath.path)
            .endsWith("${File.separator}.thumbs${File.separator}2024${File.separator}IMG_001.jpg")
    }
}
