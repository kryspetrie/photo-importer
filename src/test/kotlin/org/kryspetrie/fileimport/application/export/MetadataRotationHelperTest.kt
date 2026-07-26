package org.kryspetrie.fileimport.application.export

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.model.ImageFileType

@DisplayName("MetadataRotationHelper")
class MetadataRotationHelperTest {

    @Test
    fun jpegUsesPixelRotationWhenDegreesNonZero() {
        assertThat(MetadataRotationHelper.usesPixelRotation(ImageFileType.JPEG, 90)).isTrue()
        assertThat(MetadataRotationHelper.usesPixelRotation(ImageFileType.JPEG, 0)).isFalse()
    }

    @Test
    fun rawUsesMetadataOrientationOnly() {
        assertThat(MetadataRotationHelper.usesPixelRotation(ImageFileType.RAW_CR2, 90)).isFalse()
        assertThat(MetadataRotationHelper.usesMetadataOrientation(ImageFileType.RAW_CR2, 90)).isTrue()
    }

    @Test
    fun mapsRotationDegreesToExifOrientationLabels() {
        assertThat(MetadataRotationHelper.exifOrientationTag(90)).isEqualTo("Rotate 90 CW")
        assertThat(MetadataRotationHelper.exifOrientationTag(180)).isEqualTo("Rotate 180")
        assertThat(MetadataRotationHelper.exifOrientationTag(270)).isEqualTo("Rotate 270 CW")
        assertThat(MetadataRotationHelper.exifOrientationTag(0)).isEqualTo("Horizontal (normal)")
        assertThat(MetadataRotationHelper.exifOrientationTag(450)).isEqualTo("Rotate 90 CW")
        assertThat(MetadataRotationHelper.exifOrientationTag(-90)).isEqualTo("Rotate 270 CW")
    }

    @Test
    fun tiffUsesPixelRotation() {
        assertThat(MetadataRotationHelper.usesPixelRotation(ImageFileType.TIFF, 90)).isTrue()
        assertThat(MetadataRotationHelper.usesMetadataOrientation(ImageFileType.TIFF, 90)).isFalse()
    }

    @Test
    fun pngUsesMetadataOrientationOnly() {
        assertThat(MetadataRotationHelper.usesPixelRotation(ImageFileType.PNG, 90)).isFalse()
        assertThat(MetadataRotationHelper.usesMetadataOrientation(ImageFileType.PNG, 90)).isTrue()
    }

    @Test
    fun zeroRotationSkipsBothRotationModes() {
        assertThat(MetadataRotationHelper.usesPixelRotation(ImageFileType.JPEG, 0)).isFalse()
        assertThat(MetadataRotationHelper.usesMetadataOrientation(ImageFileType.RAW_CR2, 0))
            .isFalse()
    }
}
