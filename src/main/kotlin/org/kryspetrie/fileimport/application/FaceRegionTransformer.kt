package org.kryspetrie.fileimport.application

import boofcv.abst.geo.h.HomographyDLT_to_Epipolar
import boofcv.factory.geo.FactoryMultiView
import boofcv.struct.geo.AssociatedPair
import georegression.struct.point.Point2D_F64
import java.io.File
import org.apache.commons.imaging.Imaging
import org.ejml.data.DMatrixRMaj
import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.FaceRegion
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.GeometryUtils
import org.kryspetrie.fileimport.domain.model.RotationAngle
import org.kryspetrie.fileimport.domain.port.FaceRegionTransformerPort

/**
 * Transforms face region coordinates from a source image's XMP metadata to match cropped and
 * corrected output photos.
 *
 * The source image (e.g., a scanned page) may contain MWG-RS face regions with normalized
 * coordinates (0-1 relative to the source image). When individual photos are extracted from the
 * source via perspective correction and cropping, those face region coordinates must be transformed
 * to match the output photo dimensions.
 *
 * ## Transformation Pipeline
 *
 * Source-normalized → Source-pixel → Forward homography → Cropped-pixel → Rotation → Output-pixel →
 * Output-normalized
 *
 * @see PerspectiveCorrectionService for the image warping that uses the backward homography
 */
class FaceRegionTransformer : FaceRegionTransformerPort {

    /**
     * Port implementation: transforms face regions using [FilePath]. Converts to [File] internally
     * for Apache Commons Imaging XMP parsing.
     */
    override fun transformFaceRegionsFromSource(
        sourceFile: FilePath,
        detectedPhoto: DetectedPhoto,
        outputWidth: Int,
        outputHeight: Int,
        sourceWidth: Int,
        sourceHeight: Int,
        marginFraction: Double,
    ): List<FaceRegion> =
        transformFaceRegionsFromSource(
            sourceFile = sourceFile.toFile(),
            detectedPhoto = detectedPhoto,
            outputWidth = outputWidth,
            outputHeight = outputHeight,
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            marginFraction = marginFraction,
        )

    /** Port implementation: reads face regions from XMP using [FilePath]. */
    override fun readFaceRegionsFromXmp(file: FilePath): List<FaceRegion> =
        readFaceRegionsFromXmp(file.toFile())

    /**
     * Reads face regions from a source image's XMP metadata and transforms them to match a specific
     * cropped output photo.
     */
    fun transformFaceRegionsFromSource(
        sourceFile: File,
        detectedPhoto: DetectedPhoto,
        outputWidth: Int,
        outputHeight: Int,
        sourceWidth: Int,
        sourceHeight: Int,
        marginFraction: Double = 0.02,
    ): List<FaceRegion> {
        val sourceRegions = readFaceRegionsFromXmp(sourceFile)
        if (sourceRegions.isEmpty()) return emptyList()
        return transformFaceRegions(
            sourceRegions,
            detectedPhoto,
            outputWidth,
            outputHeight,
            sourceWidth,
            sourceHeight,
            marginFraction,
        )
    }

    /** Reads MWG-RS face regions from a JPEG file's XMP metadata. */
    fun readFaceRegionsFromXmp(file: File): List<FaceRegion> {
        return try {
            val jpegBytes = file.readBytes()
            val xmpXml = Imaging.getXmpXml(jpegBytes) ?: return emptyList()
            parseMwgRsRegions(xmpXml)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Transforms source face regions to output coordinates for a specific detected photo. */
    fun transformFaceRegions(
        sourceRegions: List<FaceRegion>,
        detectedPhoto: DetectedPhoto,
        outputWidth: Int,
        outputHeight: Int,
        sourceWidth: Int,
        sourceHeight: Int,
        marginFraction: Double = 0.02,
    ): List<FaceRegion> {
        val result = mutableListOf<FaceRegion>()
        val photo =
            if (marginFraction > 0.0) GeometryUtils.applyMargin(detectedPhoto, marginFraction)
            else detectedPhoto
        val forwardH: DMatrixRMaj? =
            if (photo.applyPerspectiveCorrection) {
                computeForwardHomography(photo, outputWidth, outputHeight)
            } else null
        val bounds = detectedPhoto.getBounds()

        for (region in sourceRegions) {
            val srcCenterX = region.x * sourceWidth
            val srcCenterY = region.y * sourceHeight
            val toleranceX = region.w * sourceWidth * 0.5
            val toleranceY = region.h * sourceHeight * 0.5

            if (
                srcCenterX < bounds.minX - toleranceX ||
                    srcCenterX > bounds.maxX + toleranceX ||
                    srcCenterY < bounds.minY - toleranceY ||
                    srcCenterY > bounds.maxY + toleranceY
            )
                continue

            val srcLeft = (region.x - region.w / 2) * sourceWidth
            val srcTop = (region.y - region.h / 2) * sourceHeight
            val srcRight = (region.x + region.w / 2) * sourceWidth
            val srcBottom = (region.y + region.h / 2) * sourceHeight

            val transformedCorners =
                if (forwardH != null) {
                    listOf(
                        transformPoint(srcLeft, srcTop, forwardH),
                        transformPoint(srcRight, srcTop, forwardH),
                        transformPoint(srcRight, srcBottom, forwardH),
                        transformPoint(srcLeft, srcBottom, forwardH),
                    )
                } else {
                    listOf(
                        Point2D_F64(
                            srcLeft - bounds.minX.toDouble(),
                            srcTop - bounds.minY.toDouble(),
                        ),
                        Point2D_F64(
                            srcRight - bounds.minX.toDouble(),
                            srcTop - bounds.minY.toDouble(),
                        ),
                        Point2D_F64(
                            srcRight - bounds.minX.toDouble(),
                            srcBottom - bounds.minY.toDouble(),
                        ),
                        Point2D_F64(
                            srcLeft - bounds.minX.toDouble(),
                            srcBottom - bounds.minY.toDouble(),
                        ),
                    )
                }

            val rotatedCorners =
                transformedCorners.map { pt ->
                    applyRotationToPixelPoint(pt.x, pt.y, outputWidth, outputHeight, photo.rotation)
                }
            val outMinX = rotatedCorners.minOf { it.x }
            val outMaxX = rotatedCorners.maxOf { it.x }
            val outMinY = rotatedCorners.minOf { it.y }
            val outMaxY = rotatedCorners.maxOf { it.y }
            val (actualWidth, actualHeight) =
                getOutputDimensionsAfterRotation(outputWidth, outputHeight, photo.rotation)

            result.add(
                FaceRegion(
                    name = region.name,
                    type = region.type,
                    x = ((outMinX + outMaxX) / 2 / actualWidth).coerceIn(0.0, 1.0),
                    y = ((outMinY + outMaxY) / 2 / actualHeight).coerceIn(0.0, 1.0),
                    w = ((outMaxX - outMinX) / actualWidth).coerceIn(0.0, 1.0),
                    h = ((outMaxY - outMinY) / actualHeight).coerceIn(0.0, 1.0),
                )
            )
        }
        return result
    }

    /** Computes forward homography (source-pixel → output-pixel). */
    internal fun computeForwardHomography(
        detectedPhoto: DetectedPhoto,
        outputWidth: Int,
        outputHeight: Int,
    ): DMatrixRMaj {
        val srcCorners =
            arrayOf(
                Point2D_F64(detectedPhoto.topLeft.x.toDouble(), detectedPhoto.topLeft.y.toDouble()),
                Point2D_F64(
                    detectedPhoto.topRight.x.toDouble(),
                    detectedPhoto.topRight.y.toDouble(),
                ),
                Point2D_F64(
                    detectedPhoto.bottomRight.x.toDouble(),
                    detectedPhoto.bottomRight.y.toDouble(),
                ),
                Point2D_F64(
                    detectedPhoto.bottomLeft.x.toDouble(),
                    detectedPhoto.bottomLeft.y.toDouble(),
                ),
            )
        val dstCorners =
            arrayOf(
                Point2D_F64(0.0, 0.0),
                Point2D_F64(outputWidth.toDouble(), 0.0),
                Point2D_F64(outputWidth.toDouble(), outputHeight.toDouble()),
                Point2D_F64(0.0, outputHeight.toDouble()),
            )
        val pairs = ArrayList<AssociatedPair>()
        for (i in srcCorners.indices) {
            pairs.add(AssociatedPair(srcCorners[i].copy(), dstCorners[i].copy()))
        }
        val estimator: HomographyDLT_to_Epipolar = FactoryMultiView.homographyDLT(true)
        val H = DMatrixRMaj(3, 3)
        if (!estimator.process(pairs, H)) {
            H[0, 0] = 1.0
            H[0, 1] = 0.0
            H[0, 2] = 0.0
            H[1, 0] = 0.0
            H[1, 1] = 1.0
            H[1, 2] = 0.0
            H[2, 0] = 0.0
            H[2, 1] = 0.0
            H[2, 2] = 1.0
        }
        return H
    }

    /** Applies a 3×3 homography to transform a source-pixel point. */
    internal fun transformPoint(x: Double, y: Double, H: DMatrixRMaj): Point2D_F64 {
        val w = H[2, 0] * x + H[2, 1] * y + H[2, 2]
        if (w == 0.0) return Point2D_F64(Double.NaN, Double.NaN)
        return Point2D_F64(
            (H[0, 0] * x + H[0, 1] * y + H[0, 2]) / w,
            (H[1, 0] * x + H[1, 1] * y + H[1, 2]) / w,
        )
    }

    /** Applies rotation to a pixel point in the cropped (pre-rotation) image. */
    internal fun applyRotationToPixelPoint(
        x: Double,
        y: Double,
        preRotWidth: Int,
        preRotHeight: Int,
        rotation: RotationAngle,
    ): Point2D_F64 =
        when (rotation) {
            RotationAngle.NONE -> Point2D_F64(x, y)
            RotationAngle.CW_90 -> Point2D_F64(preRotHeight.toDouble() - y, x)
            RotationAngle.CW_180 ->
                Point2D_F64(preRotWidth.toDouble() - x, preRotHeight.toDouble() - y)
            RotationAngle.CCW_90 -> Point2D_F64(y, preRotWidth.toDouble() - x)
        }

    /** Returns output image dimensions after rotation. */
    internal fun getOutputDimensionsAfterRotation(
        width: Int,
        height: Int,
        rotation: RotationAngle,
    ): Pair<Int, Int> =
        when (rotation) {
            RotationAngle.CW_90,
            RotationAngle.CCW_90 -> Pair(height, width)
            RotationAngle.NONE,
            RotationAngle.CW_180 -> Pair(width, height)
        }

    /**
     * Parses MWG-RS face regions from XMP XML using regex for robustness.
     *
     * Supports both our own output format and Adobe Lightroom style.
     */
    internal fun parseMwgRsRegions(xmpXml: String): List<FaceRegion> {
        val regions = mutableListOf<FaceRegion>()

        // Strategy 1: Our output format — mwg-rs:Name, mwg-rs:Type, mwg-rs:Area on same element
        // The Area attribute can span multiple lines and ends with "/> (quote then self-closing
        // tag)
        @Suppress("MaxLineLength")
        val ourFormatRegex =
            Regex(
                """mwg-rs:Name\s*=\s*["']([^"']+)["'][\s\S]*?mwg-rs:Type\s*=\s*["']([^"']+)["'][\s\S]*?mwg-rs:Area\s*=\s*"([\s\S]+?)"\s*/>"""
            )
        for (match in ourFormatRegex.findAll(xmpXml)) {
            val name = match.groupValues[1]
            val type = match.groupValues[2]
            val areaStr = match.groupValues[3]
            val region = parseAreaString(areaStr, name, type)
            if (region != null) regions.add(region)
        }

        // Strategy 2: stArea:x/y/w/h as attributes (Adobe Lightroom style)
        // These can appear on any element, possibly with stArea: namespace prefixes
        if (regions.isEmpty()) {
            // Match elements containing stArea:x attribute
            val stAreaPattern =
                Regex(
                    """<[^>]*stArea:x\s*=\s*["']([^"']+)["'][^>]*>""",
                    RegexOption.DOT_MATCHES_ALL,
                )
            for (elementMatch in stAreaPattern.findAll(xmpXml)) {
                val element = elementMatch.value
                val x =
                    Regex("""stArea:x\s*=\s*["']([^"']+)["']""")
                        .find(element)
                        ?.groupValues
                        ?.get(1)
                        ?.toDoubleOrNull() ?: continue
                val y =
                    Regex("""stArea:y\s*=\s*["']([^"']+)["']""")
                        .find(element)
                        ?.groupValues
                        ?.get(1)
                        ?.toDoubleOrNull() ?: continue
                val w =
                    Regex("""stArea:w\s*=\s*["']([^"']+)["']""")
                        .find(element)
                        ?.groupValues
                        ?.get(1)
                        ?.toDoubleOrNull() ?: 0.1
                val h =
                    Regex("""stArea:h\s*=\s*["']([^"']+)["']""")
                        .find(element)
                        ?.groupValues
                        ?.get(1)
                        ?.toDoubleOrNull() ?: 0.1
                val name =
                    Regex("""(?:mwg-rs:)?Name\s*=\s*["']([^"']+)["']""")
                        .find(element)
                        ?.groupValues
                        ?.get(1) ?: "Unknown"
                val type =
                    Regex("""(?:mwg-rs:)?Type\s*=\s*["']([^"']+)["']""")
                        .find(element)
                        ?.groupValues
                        ?.get(1) ?: "Face"
                regions.add(FaceRegion(name = name, type = type, x = x, y = y, w = w, h = h))
            }
        }

        return regions
    }

    /** Parses an area string like "x='0.3' y='0.4' w='0.15' h='0.2' unit='normalized'". */
    internal fun parseAreaString(areaStr: String, name: String, type: String): FaceRegion? {
        val map = mutableMapOf<String, Double>()
        val parts = areaStr.split(Regex("\\s+"))
        for (part in parts) {
            val kv = part.split("=", limit = 2)
            if (kv.size == 2) {
                kv[1].trim('\'', '"').toDoubleOrNull()?.let { map[kv[0].trim()] = it }
            }
        }
        val x = map["x"] ?: return null
        val y = map["y"] ?: return null
        val w = map["w"] ?: 0.1
        val h = map["h"] ?: 0.1
        return FaceRegion(name = name, type = type, x = x, y = y, w = w, h = h)
    }

    internal fun isPointInPhoto(
        srcX: Double,
        srcY: Double,
        detectedPhoto: DetectedPhoto,
        tolerance: Double = 0.0,
    ): Boolean {
        val bounds = detectedPhoto.getBounds()
        return srcX >= bounds.minX - tolerance &&
            srcX <= bounds.maxX + tolerance &&
            srcY >= bounds.minY - tolerance &&
            srcY <= bounds.maxY + tolerance
    }
}
