package org.kryspetrie.fileimport.application.export

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.model.ImageFileType

@DisplayName("FileFormatSupport")
class FileFormatSupportTest {

    @Test
    fun onlyJpegAndTiffSupportPixelRotation() {
        assertThat(FileFormatSupport.canRotatePixels(ImageFileType.JPEG)).isTrue()
        assertThat(FileFormatSupport.canRotatePixels(ImageFileType.TIFF)).isTrue()
        assertThat(FileFormatSupport.canRotatePixels(ImageFileType.PNG)).isFalse()
        assertThat(FileFormatSupport.canRotatePixels(ImageFileType.RAW_CR2)).isFalse()
    }

    @Test
    fun rawFormatsSupportLosslessOrientationAndInPlaceMetadata() {
        ImageFileType.entries.filter { it.isRawFormat }.forEach { rawType ->
            assertThat(FileFormatSupport.canSetOrientationLossless(rawType)).isTrue()
            assertThat(FileFormatSupport.canWriteMetadataInPlace(rawType)).isTrue()
            assertThat(FileFormatSupport.metadataSupport(rawType)).isEqualTo(MetadataSupport.FULL)
        }
    }

    @Test
    fun videoFormatsDoNotSupportMetadataEditing() {
        ImageFileType.entries.filter { it.isVideo }.forEach { videoType ->
            assertThat(FileFormatSupport.metadataSupport(videoType)).isEqualTo(MetadataSupport.NONE)
            assertThat(FileFormatSupport.canWriteMetadataInPlace(videoType)).isFalse()
        }
    }
}
