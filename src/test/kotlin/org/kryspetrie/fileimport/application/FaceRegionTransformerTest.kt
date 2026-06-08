package org.kryspetrie.fileimport.application

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.FaceRegion
import org.kryspetrie.fileimport.domain.model.GeometryUtils
import org.kryspetrie.fileimport.domain.model.PhotoCorner
import org.kryspetrie.fileimport.domain.model.RotationAngle

@DisplayName("Face Region Transformer")
class FaceRegionTransformerTest {

    private lateinit var transformer: FaceRegionTransformer
    @TempDir lateinit var tempDir: File

    @BeforeEach
    fun setup() {
        transformer = FaceRegionTransformer()
    }

    private fun fullFramePhoto(
        w: Int = 800,
        h: Int = 600,
        perspectiveCorrection: Boolean = false,
        rotation: RotationAngle = RotationAngle.NONE,
    ) =
        DetectedPhoto(
            topLeft = PhotoCorner(0f, 0f),
            topRight = PhotoCorner(w.toFloat(), 0f),
            bottomRight = PhotoCorner(w.toFloat(), h.toFloat()),
            bottomLeft = PhotoCorner(0f, h.toFloat()),
            applyPerspectiveCorrection = perspectiveCorrection,
            rotation = rotation,
        )

    private fun quadrantPhoto(
        sw: Int = 800,
        sh: Int = 600,
        perspectiveCorrection: Boolean = false,
        rotation: RotationAngle = RotationAngle.NONE,
    ) =
        DetectedPhoto(
            topLeft = PhotoCorner(0f, 0f),
            topRight = PhotoCorner((sw / 2).toFloat(), 0f),
            bottomRight = PhotoCorner((sw / 2).toFloat(), (sh / 2).toFloat()),
            bottomLeft = PhotoCorner(0f, (sh / 2).toFloat()),
            applyPerspectiveCorrection = perspectiveCorrection,
            rotation = rotation,
        )

    @Nested
    @DisplayName("Forward homography computation")
    inner class ForwardHomography {
        @Test
        @DisplayName("Corners map correctly for full-frame photo")
        fun cornersMapCorrectly() {
            val photo = fullFramePhoto()
            val H = transformer.computeForwardHomography(photo, 800, 600)
            val tr = transformer.transformPoint(800.0, 0.0, H)
            assertThat(tr.x).isCloseTo(800.0, Offset.offset(5.0))
            assertThat(tr.y).isCloseTo(0.0, Offset.offset(5.0))
        }

        @Test
        @DisplayName("Quadrant center maps correctly")
        fun quadrantCenter() {
            val photo = quadrantPhoto(perspectiveCorrection = true)
            val H = transformer.computeForwardHomography(photo, 400, 300)
            val center = transformer.transformPoint(200.0, 150.0, H)
            assertThat(center.x).isCloseTo(200.0, Offset.offset(10.0))
            assertThat(center.y).isCloseTo(150.0, Offset.offset(10.0))
        }
    }

    @Nested
    @DisplayName("Rotation transformation")
    inner class RotationPointTransform {
        @Test
        @DisplayName("NONE preserves coordinates")
        fun noneRotation() {
            val p = transformer.applyRotationToPixelPoint(100.0, 50.0, 800, 600, RotationAngle.NONE)
            assertThat(p.x).isCloseTo(100.0, Offset.offset(0.01))
            assertThat(p.y).isCloseTo(50.0, Offset.offset(0.01))
        }

        @Test
        @DisplayName("CW_90 swaps correctly")
        fun cw90() {
            val p =
                transformer.applyRotationToPixelPoint(100.0, 50.0, 800, 600, RotationAngle.CW_90)
            assertThat(p.x).isCloseTo(550.0, Offset.offset(0.01))
            assertThat(p.y).isCloseTo(100.0, Offset.offset(0.01))
        }

        @Test
        @DisplayName("CW_180 flips both")
        fun cw180() {
            val p =
                transformer.applyRotationToPixelPoint(100.0, 50.0, 800, 600, RotationAngle.CW_180)
            assertThat(p.x).isCloseTo(700.0, Offset.offset(0.01))
            assertThat(p.y).isCloseTo(550.0, Offset.offset(0.01))
        }

        @Test
        @DisplayName("CCW_90 swaps correctly")
        fun ccw90() {
            val p =
                transformer.applyRotationToPixelPoint(100.0, 50.0, 800, 600, RotationAngle.CCW_90)
            assertThat(p.x).isCloseTo(50.0, Offset.offset(0.01))
            assertThat(p.y).isCloseTo(700.0, Offset.offset(0.01))
        }

        @Test
        @DisplayName("Output dimensions swap for 90°")
        fun dimensionsSwap() {
            val (w1, h1) =
                transformer.getOutputDimensionsAfterRotation(800, 600, RotationAngle.CW_90)
            assertThat(w1).isEqualTo(600)
            assertThat(h1).isEqualTo(800)
            val (w2, h2) =
                transformer.getOutputDimensionsAfterRotation(800, 600, RotationAngle.NONE)
            assertThat(w2).isEqualTo(800)
            assertThat(h2).isEqualTo(600)
        }
    }

    @Nested
    @DisplayName("Containment check")
    inner class ContainmentCheck {
        @Test
        @DisplayName("Point inside")
        fun inside() {
            assertThat(transformer.isPointInPhoto(100.0, 100.0, fullFramePhoto())).isTrue()
        }

        @Test
        @DisplayName("Point outside")
        fun outside() {
            assertThat(transformer.isPointInPhoto(700.0, 500.0, quadrantPhoto())).isFalse()
        }

        @Test
        @DisplayName("Tolerance includes near-edge")
        fun tolerance() {
            assertThat(transformer.isPointInPhoto(410.0, 150.0, quadrantPhoto(), tolerance = 20.0))
                .isTrue()
        }
    }

    @Nested
    @DisplayName("Simple crop transformation")
    inner class SimpleCrop {
        @Test
        @DisplayName("Face in quadrant transforms correctly")
        fun faceInQuadrant() {
            val regions = listOf(FaceRegion(name = "Alice", x = 0.1, y = 0.1, w = 0.1, h = 0.1))
            val photo = quadrantPhoto(perspectiveCorrection = false)
            val result =
                transformer.transformFaceRegions(
                    regions,
                    photo,
                    400,
                    300,
                    800,
                    600,
                    marginFraction = 0.0,
                )
            assertThat(result).hasSize(1)
            assertThat(result[0].name).isEqualTo("Alice")
            assertThat(result[0].x).isCloseTo(0.2, Offset.offset(0.05))
            assertThat(result[0].y).isCloseTo(0.2, Offset.offset(0.05))
        }

        @Test
        @DisplayName("Face outside photo is excluded")
        fun faceOutsideExcluded() {
            val regions = listOf(FaceRegion(name = "Bob", x = 0.8, y = 0.8, w = 0.1, h = 0.1))
            val result =
                transformer.transformFaceRegions(
                    regions,
                    quadrantPhoto(perspectiveCorrection = false),
                    400,
                    300,
                    800,
                    600,
                    marginFraction = 0.0,
                )
            assertThat(result).isEmpty()
        }

        @Test
        @DisplayName("Face at boundary edge included")
        fun faceAtBoundary() {
            val regions = listOf(FaceRegion(name = "Edge", x = 0.5, y = 0.25, w = 0.05, h = 0.05))
            val result =
                transformer.transformFaceRegions(
                    regions,
                    quadrantPhoto(perspectiveCorrection = false),
                    400,
                    300,
                    800,
                    600,
                    marginFraction = 0.0,
                )
            assertThat(result).hasSize(1)
        }
    }

    @Nested
    @DisplayName("Rotation of face regions")
    inner class RotationFaceTransform {
        @Test
        @DisplayName("CW_90 rotation transforms coordinates")
        fun cw90Rotation() {
            val regions = listOf(FaceRegion(name = "Rotated", x = 0.3, y = 0.4, w = 0.1, h = 0.1))
            val photo = fullFramePhoto(rotation = RotationAngle.CW_90)
            val result =
                transformer.transformFaceRegions(
                    regions,
                    photo,
                    800,
                    600,
                    800,
                    600,
                    marginFraction = 0.0,
                )
            assertThat(result).hasSize(1)
            assertThat(result[0].x).isCloseTo(0.6, Offset.offset(0.1))
            assertThat(result[0].y).isCloseTo(0.3, Offset.offset(0.1))
        }

        @Test
        @DisplayName("CW_90 on non-square image uses pre-rotation dimensions correctly")
        fun cw90NonSquarePreRotationDims() {
            // Source is 800x600, cropped quadrant is 400x300 (pre-rotation).
            // After CW_90 rotation, output is 300x400.
            // If we incorrectly passed post-rotation dims (300x400) as outputWidth/outputHeight,
            // the homography and rotation transform would be wrong.
            // The correct call passes 400x300 (pre-rotation) as outputWidth/outputHeight.
            val regions = listOf(FaceRegion(name = "Test", x = 0.1, y = 0.1, w = 0.05, h = 0.05))
            val photo = quadrantPhoto(perspectiveCorrection = false, rotation = RotationAngle.CW_90)

            // CORRECT: pre-rotation dimensions (400x300)
            val correctResult =
                transformer.transformFaceRegions(
                    regions,
                    photo,
                    400,
                    300,
                    800,
                    600,
                    marginFraction = 0.0,
                )

            // WRONG: post-rotation dimensions (300x400) — what the bug was
            val wrongResult =
                transformer.transformFaceRegions(
                    regions,
                    photo,
                    300,
                    400,
                    800,
                    600,
                    marginFraction = 0.0,
                )

            assertThat(correctResult).hasSize(1)
            assertThat(wrongResult).hasSize(1)

            // The correct result should be different from the wrong one for non-square crops
            // (they'd only be the same for square images where width == height)
            assertThat(correctResult[0].x).isNotEqualTo(wrongResult[0].x)
        }
    }

    @Nested
    @DisplayName("XMP MWG-RS parsing")
    inner class XmpMwgRsParsing {
        @Test
        @DisplayName("Parse our own output format (mwg-rs:Area inline)")
        fun parseOurFormat() {
            // This matches the format written by PhotoScanExportService.writeXmpFaceRegions
            val xmp =
                """
                <?xpacket begin='﻿' id='W5M0MpCehiHzreSzNTczkc9d'?>
                <x:xmpmeta xmlns:x='adobe:ns:meta/'>
                <rdf:RDF xmlns:rdf='http://www.w3.org/1999/02/22-rdf-syntax-ns#'>
                <rdf:Description rdf:about=''
                   xmlns:mwg-rs='http://www.metadataworkinggroup.com/schemas/regions/'>
                  <mwg-rs:Regions>
                    <rdf:Alt>
                <rdf:Description rdf:about=""
                   mwg-rs:Name="Alice"
                   mwg-rs:Type="Face"
                   mwg-rs:Area="
                    x='0.300000'
                    y='0.400000'
                    w='0.150000'
                    h='0.200000'
                    unit='normalized'"/>
                    </rdf:Alt>
                  </mwg-rs:Regions>
                </rdf:Description>
                </rdf:RDF>
                </x:xmpmeta>
                <?xpacket end='w'?>
                """
                    .trimIndent()
            val regions = transformer.parseMwgRsRegions(xmp)
            assertThat(regions).hasSize(1)
            assertThat(regions[0].name).isEqualTo("Alice")
            assertThat(regions[0].type).isEqualTo("Face")
            assertThat(regions[0].x).isCloseTo(0.3, Offset.offset(0.001))
            assertThat(regions[0].y).isCloseTo(0.4, Offset.offset(0.001))
            assertThat(regions[0].w).isCloseTo(0.15, Offset.offset(0.001))
            assertThat(regions[0].h).isCloseTo(0.2, Offset.offset(0.001))
        }

        @Test
        @DisplayName("Parse multiple face regions")
        fun parseMultiple() {
            val xmp =
                """
                <?xpacket begin='﻿' id='W5M0MpCehiHzreSzNTczkc9d'?>
                <x:xmpmeta xmlns:x='adobe:ns:meta/'>
                <rdf:RDF xmlns:rdf='http://www.w3.org/1999/02/22-rdf-syntax-ns#'>
                <rdf:Description rdf:about=''
                   xmlns:mwg-rs='http://www.metadataworkinggroup.com/schemas/regions/'>
                  <mwg-rs:Regions>
                    <rdf:Alt>
                <rdf:Description rdf:about=""
                   mwg-rs:Name="Alice"
                   mwg-rs:Type="Face"
                   mwg-rs:Area="x='0.3' y='0.4' w='0.15' h='0.2' unit='normalized'"/>
                <rdf:Description rdf:about=""
                   mwg-rs:Name="Bob"
                   mwg-rs:Type="Face"
                   mwg-rs:Area="x='0.7' y='0.5' w='0.12' h='0.18' unit='normalized'"/>
                    </rdf:Alt>
                  </mwg-rs:Regions>
                </rdf:Description>
                </rdf:RDF>
                </x:xmpmeta>
                <?xpacket end='w'?>
                """
                    .trimIndent()
            val regions = transformer.parseMwgRsRegions(xmp)
            assertThat(regions).hasSize(2)
            assertThat(regions.map { it.name }).containsExactly("Alice", "Bob")
        }

        @Test
        @DisplayName("Empty/null XMP returns empty list")
        fun emptyXmp() {
            assertThat(transformer.parseMwgRsRegions("")).isEmpty()
            assertThat(
                    transformer.parseMwgRsRegions(
                        "<rdf:RDF xmlns:rdf='http://www.w3.org/1999/02/22-rdf-syntax-ns#'/>"
                    )
                )
                .isEmpty()
        }
    }

    @Nested
    @DisplayName("Margin application")
    inner class MarginApp {
        @Test
        @DisplayName("Zero margin returns same photo")
        fun zeroMargin() {
            val photo = fullFramePhoto()
            val result = GeometryUtils.applyMargin(photo, 0.0)
            assertThat(result.topLeft.x).isEqualTo(photo.topLeft.x)
        }

        @Test
        @DisplayName("Positive margin expands corners")
        fun positiveMargin() {
            val photo = quadrantPhoto()
            val result = GeometryUtils.applyMargin(photo, 0.02)
            assertThat(result.topLeft.x).isLessThan(photo.topLeft.x)
        }
    }

    @Nested
    @DisplayName("Integration: source JPEG without regions")
    inner class Integration {
        @Test
        @DisplayName("Source file without face regions returns empty")
        fun noRegions() {
            val img = BufferedImage(200, 150, BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics()
            g.fillRect(0, 0, 200, 150)
            g.dispose()
            val sourceFile = File(tempDir, "no_faces_${System.nanoTime()}.jpg")
            ImageIO.write(img, "jpg", sourceFile)
            val photo = fullFramePhoto(w = 200, h = 150)
            val result =
                transformer.transformFaceRegionsFromSource(sourceFile, photo, 200, 150, 200, 150)
            assertThat(result).isEmpty()
        }
    }
}
