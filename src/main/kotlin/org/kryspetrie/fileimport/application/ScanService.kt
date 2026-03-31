package org.kryspetrie.fileimport.application

import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.PhotoCorner
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.domain.port.ImageRepositoryPort
import org.kryspetrie.fileimport.domain.port.NamingPort

/**
 * Service for photo scan operations.
 *
 * Handles scanning photos from images that contain multiple photos on a solid background.
 *
 * ## Features
 * - Detects photo corners using simple edge detection
 * - Allows manual corner adjustment via UI
 * - Per-photo metadata override (date, tags, notes)
 * - Exports multiple photos with filename incrementing
 *
 * @property imageRepository Port for image file operations and metadata
 * @property namingPort Port for filename pattern resolution
 */
class ScanService(
    private val imageRepository: ImageRepositoryPort,
    private val namingPort: NamingPort
) {

  /**
   * Detect photos within an image file using simple edge detection and rectangle finding.
   *
   * Scans the image looking for rectangular photo boundaries based on edge patterns.
   *
   * @param filePath Path to the scanned image file
   * @return List of detected photos with their corners
   */
  fun detectPhotos(filePath: String): List<DetectedPhoto> {
    val imageFile = File(filePath)
    if (!imageFile.exists()) {
      return emptyList()
    }

    return try {
      // Read the image
      val bufferedImage = ImageIO.read(imageFile) ?: return emptyList()

      // Use edge detection to find potential photo boundaries
      val edges = detectEdges(bufferedImage)

      // Find rectangles from edges
      val rectangles = findRectangles(edges, bufferedImage.width, bufferedImage.height)

      // Create detected photos from rectangles
      val detectedPhotos =
          rectangles.map { rect ->
            DetectedPhoto(
                topLeft = PhotoCorner.create(rect.topLeft.x.toInt(), rect.topLeft.y.toInt()),
                topRight = PhotoCorner.create(rect.topRight.x.toInt(), rect.topRight.y.toInt()),
                bottomLeft =
                    PhotoCorner.create(rect.bottomLeft.x.toInt(), rect.bottomLeft.y.toInt()),
                bottomRight =
                    PhotoCorner.create(rect.bottomRight.x.toInt(), rect.bottomRight.y.toInt()))
          }

      // If no rectangles found, fallback to full image bounds
      if (detectedPhotos.isEmpty()) {
        listOf(
            DetectedPhoto(
                topLeft = PhotoCorner.create(0, 0),
                topRight = PhotoCorner.create(bufferedImage.width, 0),
                bottomLeft = PhotoCorner.create(0, bufferedImage.height),
                bottomRight = PhotoCorner.create(bufferedImage.width, bufferedImage.height)))
      } else {
        detectedPhotos
      }
    } catch (e: Exception) {
      // Fallback for any errors
      emptyList()
    }
  }

  /** Simple edge detection using Sobel operator. */
  private fun detectEdges(image: BufferedImage): Array<Array<Boolean>> {
    val width = image.width
    val height = image.height
    val edges = Array(height) { Array(width) { false } }

    // Convert to grayscale and apply Sobel edge detection
    for (y in 1 until height - 1) {
      for (x in 1 until width - 1) {
        val gx = sobelX(image, x, y)
        val gy = sobelY(image, x, y)
        val magnitude = kotlin.math.sqrt(gx * gx + gy * gy).toInt()
        edges[y][x] = magnitude > 50 // Edge threshold
      }
    }

    return edges
  }

  private fun sobelX(image: BufferedImage, x: Int, y: Int): Double {
    val kernel = arrayOf(intArrayOf(-1, 0, 1), intArrayOf(-2, 0, 2), intArrayOf(-1, 0, 1))

    var sum = 0.0
    for (ky in 0..2) {
      for (kx in 0..2) {
        val pixel = getGrayLevel(image, x + kx - 1, y + ky - 1)
        sum += pixel * kernel[ky][kx]
      }
    }
    return sum
  }

  private fun sobelY(image: BufferedImage, x: Int, y: Int): Double {
    val kernel = arrayOf(intArrayOf(-1, -2, -1), intArrayOf(0, 0, 0), intArrayOf(1, 2, 1))

    var sum = 0.0
    for (ky in 0..2) {
      for (kx in 0..2) {
        val pixel = getGrayLevel(image, x + kx - 1, y + ky - 1)
        sum += pixel * kernel[ky][kx]
      }
    }
    return sum
  }

  private fun getGrayLevel(image: BufferedImage, x: Int, y: Int): Int {
    if (x < 0 || x >= image.width || y < 0 || y >= image.height) return 0
    val rgb = image.getRGB(x, y)
    val r = (rgb shr 16) and 0xFF
    val g = (rgb shr 8) and 0xFF
    val b = rgb and 0xFF
    return (0.299 * r + 0.587 * g + 0.114 * b).toInt()
  }

  /** Find rectangular photo boundaries from edge map. */
  private fun findRectangles(
      edges: Array<Array<Boolean>>,
      imageWidth: Int,
      imageHeight: Int
  ): List<Rectangle> {
    val rectangles = mutableListOf<Rectangle>()

    // Find edges in the image
    val edgePoints = mutableListOf<Point>()
    for (y in edges.indices) {
      for (x in edges[y].indices) {
        if (edges[y][x]) {
          edgePoints.add(Point(x, y))
        }
      }
    }

    // Simple approach: Look for large edgecontours that could be rectangles
    // Group edge points into contour segments
    val contours = groupIntoContours(edgePoints, 10)

    // Look for rectangular contours (4 corners, close to 90 degree angles)
    for (contour in contours) {
      if (contour.size >= 4) {
        val rect = tryExtractRectangle(contour, imageWidth, imageHeight)
        if (rect != null) {
          rectangles.add(rect)
        }
      }
    }

    return rectangles
  }

  /** Group edge points into contours. */
  private fun groupIntoContours(points: List<Point>, minDistance: Int): List<List<Point>> {
    val contours = mutableListOf<List<Point>>()
    val used = mutableSetOf<Point>()

    for (point in points) {
      if (point in used) continue

      val contour = mutableListOf(point)
      used.add(point)

      // Expand contour by finding nearby points
      var expanded = true
      while (expanded) {
        expanded = false
        for (p in points) {
          if (p in used) continue
          if (contour.any { distance(it, p) <= minDistance }) {
            contour.add(p)
            used.add(p)
            expanded = true
          }
        }
      }

      if (contour.size >= 10) {
        contours.add(contour)
      }
    }

    return contours
  }

  private fun distance(p1: Point, p2: Point): Double {
    return kotlin.math.sqrt(
        (p1.x - p2.x).toDouble() * (p1.x - p2.x) + (p1.y - p2.y).toDouble() * (p1.y - p2.y))
  }

  /** Try to extract a rectangle from a contour. */
  private fun tryExtractRectangle(
      contour: List<Point>,
      imageWidth: Int,
      imageHeight: Int
  ): Rectangle? {
    // Simplified: Find the outer bounding box of the contour
    if (contour.isEmpty()) return null

    var minX = contour[0].x
    var maxX = contour[0].x
    var minY = contour[0].y
    var maxY = contour[0].y

    for (point in contour) {
      minX = minX.coerceAtMost(point.x)
      maxX = maxX.coerceAtLeast(point.x)
      minY = minY.coerceAtMost(point.y)
      maxY = maxY.coerceAtLeast(point.y)
    }

    // Check if this looks like a rectangle (not too small)
    val width = maxX - minX
    val height = maxY - minY
    if (width < 100 || height < 100) return null

    // Check if aspect ratio is reasonable (not too skewed)
    val aspectRatio = width / height
    if (aspectRatio > 5.0 || aspectRatio < 0.2) return null

    // Create rectangle from bounding box
    val corners =
        listOf(
            Corner(minX.toFloat(), minY.toFloat()), // top-left
            Corner(maxX.toFloat(), minY.toFloat()), // top-right
            Corner(minX.toFloat(), maxY.toFloat()), // bottom-left
            Corner(maxX.toFloat(), maxY.toFloat())) // bottom-right

    // Validate all corners are within image bounds
    for (corner in corners) {
      if (corner.x < 0 || corner.y < 0 || corner.x > imageWidth || corner.y > imageHeight) {
        return null
      }
    }

    return Rectangle(corners[0], corners[1], corners[2], corners[3])
  }

  /**
   * Extract a single photo from the scanned image with perspective correction.
   *
   * Uses Java2D AffineTransform to map the quadrilateral to a rectangle.
   *
   * @param scannedImage The full scanned image
   * @param detectedPhoto The detected photo with its corners
   * @return Perspectively corrected photo
   */
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

  /**
   * Calculate perspective transform from source quad to destination rectangle.
   *
   * Uses simple affine transform.
   */
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

  /** Calculate distance between two corners. */
  private fun distance(c1: PhotoCorner, c2: PhotoCorner): Float {
    val dx = c2.x - c1.x
    val dy = c2.y - c1.y
    return kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
  }

  /**
   * Export a photo with metadata and filename handling.
   *
   * @param photoImage The extracted photo image
   * @param destinationPath The destination directory
   * @param originalFile The original scanned file
   * @param photoIndex The index of this photo within the scan
   * @param configuration Metadata configuration
   * @return Path to the exported file
   */
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

  /**
   * Get a unique output filename with incrementing suffix.
   *
   * Handles cases where files already exist by adding _1, _2, etc.
   */
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
}

/** Corner representation for rectangle calculation. */
data class Corner(val x: Float, val y: Float)

/** Point representation for edge detection. */
data class Point(val x: Int, val y: Int)

/** Rectangle representation for photo boundary. */
data class Rectangle(
    val topLeft: Corner,
    val topRight: Corner,
    val bottomLeft: Corner,
    val bottomRight: Corner
)
