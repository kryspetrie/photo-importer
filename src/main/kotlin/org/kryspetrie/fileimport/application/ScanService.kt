package org.kryspetrie.fileimport.application

import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.PhotoCorner
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.domain.port.ImageRepositoryPort
import org.kryspetrie.fileimport.infrastructure.photoscan.RectangleDetector
import org.kryspetrie.fileimport.infrastructure.photoscan.YoloKeypointDetector

class ScanService(
    private val imageRepository: ImageRepositoryPort,
    private val yoloKeypointDetector: YoloKeypointDetector,
    private val rectangleDetector: RectangleDetector,
) {

  fun detectPhotos(filePath: String): List<DetectedPhoto> {
    val imageFile = File(filePath)
    if (!imageFile.exists()) {
      return emptyList()
    }

    return try {
      val bufferedImage = ImageIO.read(imageFile) ?: return emptyList()

      // Try ML-based corner detection first; fall back to classical CV if unavailable or no results
      val cornerSets =
          yoloKeypointDetector.detectCorners(bufferedImage).ifEmpty {
            rectangleDetector.detectRectangles(bufferedImage).map { quad ->
              // Convert RectangleDetector.Point corners to PhotoCorner
              listOf(
                  PhotoCorner(quad.corners[0].x.toFloat(), quad.corners[0].y.toFloat()),
                  PhotoCorner(quad.corners[1].x.toFloat(), quad.corners[1].y.toFloat()),
                  PhotoCorner(quad.corners[2].x.toFloat(), quad.corners[2].y.toFloat()),
                  PhotoCorner(quad.corners[3].x.toFloat(), quad.corners[3].y.toFloat()),
              )
            }
          }

      cornerSets.map { corners ->
        DetectedPhoto(
            topLeft = corners[0],
            topRight = corners[1],
            bottomRight = corners[2],
            bottomLeft = corners[3])
      }
    } catch (e: Exception) {
      // Log the exception
      emptyList()
    }
  }

  fun extractPhoto(scannedImage: BufferedImage, detectedPhoto: DetectedPhoto): BufferedImage {
    val bounds = detectedPhoto.getBounds()
    val width = bounds.getWidth()
    val height = bounds.getHeight()

    // Source quad (detected corners)
    val srcQuad =
        listOf(
            detectedPhoto.topLeft,
            detectedPhoto.topRight,
            detectedPhoto.bottomLeft,
            detectedPhoto.bottomRight)

    // Destination rectangle
    val dstRect =
        listOf(
            Corner(0f, 0f),
            Corner(width.toFloat(), 0f),
            Corner(0f, height.toFloat()),
            Corner(width.toFloat(), height.toFloat()))

    // Calculate transform
    val transform = calculatePerspectiveTransform(srcQuad, dstRect)

    // Apply transform
    val outputImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val op =
        java.awt.image.AffineTransformOp(transform, java.awt.image.AffineTransformOp.TYPE_BILINEAR)
    op.filter(scannedImage, outputImage)

    return outputImage
  }

  private fun calculatePerspectiveTransform(
      srcQuad: List<PhotoCorner>,
      dstRect: List<Corner>
  ): AffineTransform {
    // Calculate scaling factors
    val srcTopWidth = distance(srcQuad[0], srcQuad[1])
    val srcLeftHeight = distance(srcQuad[0], srcQuad[2])
    val dstWidth = dstRect[1].x - dstRect[0].x
    val dstHeight = dstRect[2].y - dstRect[0].y

    val scaleX = dstWidth / srcTopWidth
    val scaleY = dstHeight / srcLeftHeight

    // Create transform with translation and scaling
    val transform = AffineTransform()
    transform.translate(dstRect[0].x.toDouble(), dstRect[0].y.toDouble())
    transform.scale(scaleX.toDouble(), scaleY.toDouble())

    return transform
  }

  private fun distance(c1: PhotoCorner, c2: PhotoCorner): Float {
    val dx = c2.x - c1.x
    val dy = c2.y - c1.y
    return kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
  }

  fun exportPhoto(
      photoImage: BufferedImage,
      destinationPath: String,
      originalFile: File,
      photoIndex: Int,
      configuration: PhotoScanConfiguration
  ): String {
    // Determine output filename with incrementing
    val outputFile =
        getUniqueOutputFile(
            destinationPath, originalFile.nameWithoutExtension, originalFile.extension, photoIndex)

    // Write the image
    ImageIO.write(photoImage, originalFile.extension, outputFile)

    // TODO: EXIF metadata writing would go here with Apache Commons Imaging
    // For now, skip EXIF modification - image written by ImageIO

    return outputFile.absolutePath
  }

  private fun getUniqueOutputFile(
      destinationPath: String,
      baseName: String,
      extension: String,
      photoIndex: Int
  ): File {
    val destDir = File(destinationPath)
    destDir.mkdirs()

    var counter = if (photoIndex > 0) photoIndex else 1

    while (true) {
      val filename =
          if (counter > 1) {
            "${baseName}_$counter.$extension"
          } else {
            "$baseName.$extension"
          }

      val outputFile = File(destDir, filename)
      if (!outputFile.exists()) {
        return outputFile
      }

      counter++
    }
  }

  data class Corner(val x: Float, val y: Float)
}
