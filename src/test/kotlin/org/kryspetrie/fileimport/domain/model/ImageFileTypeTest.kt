package org.kryspetrie.fileimport.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@DisplayName("ImageFileType")
class ImageFileTypeTest {
    @ParameterizedTest
    @CsvSource(
        "jpg, JPEG",
        "jpeg, JPEG",
        "tiff, TIFF",
        "tif, TIFF",
        "png, PNG",
        "dng, RAW_DNG",
        "arw, RAW_ARW",
        "raf, RAW_RAF",
        "nef, RAW_NEF",
        "cr2, RAW_CR2",
        "cr3, RAW_CR3",
        "xyz, UNKNOWN",
    )
    @DisplayName("should correctly identify file type from extension")
    fun shouldIdentifyFileType(extension: String, expectedType: ImageFileType) {
        // WHEN
        val type = ImageFileType.fromExtension(extension)

        // THEN
        assertThat(type).isEqualTo(expectedType)
    }

    @Test
    @DisplayName("should detect supported extensions")
    fun shouldDetectSupportedExtensions() {
        // WHEN
        val supported = ImageFileType.supportedExtensions()

        // THEN
        assertThat(supported).contains("jpg", "jpeg", "png", "dng", "arw")
    }

    @Test
    @DisplayName("should correctly identify JPEG")
    fun shouldIdentifyJpeg() {
        // WHEN & THEN
        assertThat(ImageFileType.JPEG.isJpeg).isTrue()
        assertThat(ImageFileType.TIFF.isJpeg).isFalse()
    }

    @Test
    @DisplayName("should correctly identify RAW formats")
    fun shouldIdentifyRawFormats() {
        // WHEN & THEN
        assertThat(ImageFileType.RAW_DNG.isRawFormat).isTrue()
        assertThat(ImageFileType.RAW_ARW.isRawFormat).isTrue()
        assertThat(ImageFileType.JPEG.isRawFormat).isFalse()
    }
}
