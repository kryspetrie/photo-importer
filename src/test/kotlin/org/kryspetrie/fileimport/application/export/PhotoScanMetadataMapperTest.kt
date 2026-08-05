package org.kryspetrie.fileimport.application.export

import java.nio.file.Paths
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.model.OverrideState
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration

@DisplayName("PhotoScanMetadataMapper")
class PhotoScanMetadataMapperTest {

    @Test
    fun mapsDescriptionToExifIptcAndXmpTags() {
        val mapped =
            PhotoScanMetadataMapper.map(
                filePath = Paths.get("/tmp/test.jpg"),
                config = PhotoScanConfiguration(description = "Family reunion"),
            )

        assertThat(mapped.command.changes["EXIF:ImageDescription"]).isEqualTo("Family reunion")
        assertThat(mapped.command.changes["IPTC:Caption-Abstract"]).isEqualTo("Family reunion")
        assertThat(mapped.command.changes["XMP-dc:Description"]).isEqualTo("Family reunion")
        assertThat(mapped.command.allowProtectedWrites).isFalse()
    }

    @Test
    fun mergesKeywordsAndSubjectsWithoutDuplicates() {
        val mapped =
            PhotoScanMetadataMapper.map(
                filePath = Paths.get("/tmp/test.jpg"),
                config = PhotoScanConfiguration(keywords = "vacation, beach", subjects = "Alice"),
            )

        assertThat(mapped.command.changes["IPTC:Keywords"]).isEqualTo("vacation, beach, Alice")
        assertThat(mapped.command.changes["XMP-dc:Subject"]).isEqualTo("vacation, beach, Alice")
    }

    @Test
    fun nullOutKeywordsClearsTagValues() {
        val mapped =
            PhotoScanMetadataMapper.map(
                filePath = Paths.get("/tmp/test.jpg"),
                config =
                    PhotoScanConfiguration(
                        keywords = "ignored",
                        overrideKeywords = OverrideState.NULL_OUT,
                    ),
            )

        assertThat(mapped.command.changes["IPTC:Keywords"]).isEmpty()
        assertThat(mapped.command.changes["XMP-dc:Subject"]).isEmpty()
    }

    @Test
    fun cameraFieldsRequireProtectedWrites() {
        val mapped =
            PhotoScanMetadataMapper.map(
                filePath = Paths.get("/tmp/test.CR2"),
                config =
                    PhotoScanConfiguration(
                        cameraMake = "Canon",
                        cameraModel = "EOS R6",
                        iso = "400",
                        overrideCameraMake = null,
                        overrideCameraModel = null,
                        overrideIso = null,
                    ),
            )

        assertThat(mapped.command.allowProtectedWrites).isTrue()
        assertThat(mapped.command.changes["EXIF:Make"]).isEqualTo("Canon")
        assertThat(mapped.command.changes["EXIF:Model"]).isEqualTo("EOS R6")
        assertThat(mapped.command.changes["EXIF:ISO"]).isEqualTo("400")
    }

    @Test
    fun mapsGpsCoordinatesAsDecimalDegrees() {
        val mapped =
            PhotoScanMetadataMapper.map(
                filePath = Paths.get("/tmp/test.jpg"),
                config = PhotoScanConfiguration(gpsLatitude = "42.2626", gpsLongitude = "-71.8023"),
            )

        assertThat(mapped.command.changes["EXIF:GPSLatitude"]).isEqualTo("42.2626")
        assertThat(mapped.command.changes["EXIF:GPSLongitude"]).isEqualTo("-71.8023")
    }

    @Test
    fun buildsMwgFaceRegionStruct() {
        val mapped =
            PhotoScanMetadataMapper.map(
                filePath = Paths.get("/tmp/test.jpg"),
                config =
                    PhotoScanConfiguration(
                        faceRegions =
                            listOf(
                                org.kryspetrie.fileimport.domain.model.FaceRegion(
                                    name = "Alice",
                                    x = 0.3,
                                    y = 0.4,
                                    w = 0.15,
                                    h = 0.2,
                                )
                            )
                    ),
                preRotationWidth = 200,
                preRotationHeight = 150,
            )

        val regionInfo = mapped.command.changes["XMP-mwg-rs:RegionInfo"]
        assertThat(regionInfo).contains("Alice")
        assertThat(regionInfo).contains("AppliedToDimensions={W=200,H=150,Unit=pixel}")
        assertThat(regionInfo).contains("X=0.3")
    }

    @Test
    fun formatsFocalLengthWithMmSuffix() {
        val mapped =
            PhotoScanMetadataMapper.map(
                filePath = Paths.get("/tmp/test.jpg"),
                config = PhotoScanConfiguration(focalLength = "50mm", overrideFocalLength = null),
            )
        assertThat(mapped.command.changes["EXIF:FocalLength"]).isEqualTo("50 mm")
        assertThat(mapped.command.allowProtectedWrites).isTrue()
    }

    @Test
    fun nullOutProtectedTagsRequiresAllowProtectedWrites() {
        val mapped =
            PhotoScanMetadataMapper.map(
                filePath = Paths.get("/tmp/test.CR2"),
                config =
                    PhotoScanConfiguration(
                        cameraMake = "Canon",
                        overrideCameraMake = OverrideState.NULL_OUT,
                    ),
            )
        assertThat(mapped.command.changes["EXIF:Make"]).isEmpty()
        assertThat(mapped.command.allowProtectedWrites).isTrue()
    }

    @Test
    fun pixelRotationResetsOrientationToNormal() {
        val mapped =
            PhotoScanMetadataMapper.map(
                filePath = Paths.get("/tmp/test.jpg"),
                config = PhotoScanConfiguration(rotationDegrees = 90),
                physicalPixelRotationApplied = true,
            )

        assertThat(mapped.command.changes["EXIF:Orientation"]).isEqualTo("Horizontal (normal)")
        assertThat(mapped.command.allowProtectedWrites).isTrue()
    }

    @Test
    fun metadataRotationSetsOrientationTagForRaw() {
        val mapped =
            PhotoScanMetadataMapper.map(
                filePath = Paths.get("/tmp/test.CR2"),
                config = PhotoScanConfiguration(rotationDegrees = 90),
                physicalPixelRotationApplied = false,
            )

        assertThat(mapped.command.changes["EXIF:Orientation"]).isEqualTo("Rotate 90 CW")
        assertThat(mapped.command.allowProtectedWrites).isTrue()
    }
}
