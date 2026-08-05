package org.kryspetrie.fileimport.application.export

import com.petrielabs.metadataeditor.domain.MetadataTag
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("SourceExifBaselineMerger")
class SourceExifBaselineMergerTest {

    @Test
    fun mergesSourceTagsUnderOverrides() {
        val source =
            listOf(
                MetadataTag("EXIF", "Make", "Canon"),
                MetadataTag("EXIF", "Model", "EOS R5"),
                MetadataTag("EXIF", "ISO", "400"),
            )
        val overrides = mapOf("EXIF:Make" to "OverrideMake")

        val merged = SourceExifBaselineMerger.merge(source, overrides)

        assertThat(merged["EXIF:Make"]).isEqualTo("OverrideMake")
        assertThat(merged["EXIF:Model"]).isEqualTo("EOS R5")
        assertThat(merged["EXIF:ISO"]).isEqualTo("400")
    }

    @Test
    fun skipsFileSystemAndGeometryTags() {
        val source =
            listOf(
                MetadataTag("File", "FileName", "scan.jpg"),
                MetadataTag("EXIF", "ImageWidth", "4000"),
                MetadataTag("EXIF", "Orientation", "6"),
                MetadataTag("EXIF", "Make", "Nikon"),
                MetadataTag("Composite", "ImageSize", "4000x3000"),
                MetadataTag("JFIF", "XResolution", "72"),
            )

        val merged = SourceExifBaselineMerger.merge(source, emptyMap())

        assertThat(merged).containsOnlyKeys("EXIF:Make")
        assertThat(merged["EXIF:Make"]).isEqualTo("Nikon")
    }

    @Test
    fun allowsIfd0AndXmpGroups() {
        val source =
            listOf(
                MetadataTag("IFD0", "Make", "Canon"),
                MetadataTag("XMP-dc", "Subject", "kids"),
                MetadataTag("JFIF", "JFIFVersion", "1.01"),
            )

        val merged = SourceExifBaselineMerger.merge(source, emptyMap())

        assertThat(merged).containsOnlyKeys("IFD0:Make", "XMP-dc:Subject")
    }

    @Test
    fun skipsBinaryBlobValues() {
        val source =
            listOf(
                MetadataTag("EXIF", "ThumbnailImage", "(Binary data 12345 bytes)"),
                MetadataTag("EXIF", "Make", "Sony"),
            )

        val merged = SourceExifBaselineMerger.merge(source, emptyMap())

        assertThat(merged).containsOnlyKeys("EXIF:Make")
    }

    @Test
    fun returnsOverridesWhenSourceEmpty() {
        val overrides = mapOf("EXIF:Make" to "OnlyOverride")
        assertThat(SourceExifBaselineMerger.merge(emptyList(), overrides)).isEqualTo(overrides)
    }
}
