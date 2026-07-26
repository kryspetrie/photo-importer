package org.kryspetrie.fileimport.ui.components

import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.model.ImageFileType

@DisplayName("FileDialogs helpers")
class FileDialogsTest {

    @Test
    fun isMetadataEditableFileAcceptsCommonRasterAndRawFormats() {
        assertThat(isMetadataEditableFile(File("photo.jpg"))).isTrue()
        assertThat(isMetadataEditableFile(File("scan.tif"))).isTrue()
        assertThat(isMetadataEditableFile(File("capture.CR2"))).isTrue()
        assertThat(isMetadataEditableFile(File("capture.NEF"))).isTrue()
        assertThat(isMetadataEditableFile(File("capture.DNG"))).isTrue()
    }

    @Test
    fun isMetadataEditableFileRejectsVideoAndUnknownExtensions() {
        assertThat(isMetadataEditableFile(File("clip.mp4"))).isFalse()
        assertThat(isMetadataEditableFile(File("clip.mov"))).isFalse()
        assertThat(isMetadataEditableFile(File("unknown.xyz"))).isFalse()
    }

    @Test
    fun metadataEditableExtensionsIncludeRawFormats() {
        val rawExtensions =
            ImageFileType.entries.filter { it.isRawFormat }.flatMap { it.extensions }.toSet()
        assertThat(METADATA_EDITABLE_EXTENSIONS).containsAll(rawExtensions)
    }

    @Test
    fun metadataEditableExtensionsExcludeVideoFormats() {
        val videoExtensions = ImageFileType.videoExtensions()
        assertThat(METADATA_EDITABLE_EXTENSIONS).doesNotContainAnyElementsOf(videoExtensions)
    }
}
