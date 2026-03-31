package org.kryspetrie.fileimport.application

import java.awt.image.BufferedImage
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.PhotoCorner

/**
 * Service for detecting photographs within scanned images.
 *
 * Uses Otsu's thresholding with connected components:
 * 1. Converts to grayscale
 * 2. Applies Otsu's threshold to separate background/photos
 * 3. Uses connected components to find individual photo regions
 * 4. Creates bounding rectangles from components
 */
@Singleton
class PhotoScanDetectorService @Inject constructor() {

  var targetDetectionWidth = 600
  var minAreaRatio = 0.02
  var maxAreaRatio = 0.85
  var minSideLength = 30
  var targetPhotoCount: Int? = null
  var maxPhotos = 5

  fun detectPhotos(image: BufferedImage): List<DetectedPhoto> {
    val originalWidth = image.width
    val originalHeight = image.height

    val (workingImage, scaleFactor) = downsampleForDetection(image)
    val photos = detectPhotosInternal(workingImage)

    val invScale = scaleFactor.toFloat()
    var scaledPhotos = photos.map { photo ->
      DetectedPhoto(
          topLeft = PhotoCorner(photo.topLeft.x / invScale, photo.topLeft.y / invScale),
          topRight = PhotoCorner(photo.topRight.x / invScale, photo.topRight.y / invScale),
          bottomLeft = PhotoCorner(photo.bottomLeft.x / invScale, photo.bottomLeft.y / invScale),
          bottomRight = PhotoCorner(photo.bottomRight.x / invScale, photo.bottomRight.y / invScale)
      )
    }
    
    if (targetPhotoCount != null && scaledPhotos.size != targetPhotoCount) {
      scaledPhotos = adjustPhotoCount(scaledPhotos, targetPhotoCount!!, originalWidth, originalHeight)
    }
    
    return scaledPhotos
  }

  private fun downsampleForDetection(image: BufferedImage): Pair<BufferedImage, Double> {
    val width = image.width
    val height = image.height

    if (width <= targetDetectionWidth) {
      return image to 1.0
    }

    val scale = targetDetectionWidth.toDouble() / width
    val newWidth = targetDetectionWidth
    val newHeight = (height * scale).toInt()

    val scaled = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB)
    val g = scaled.graphics
    g.drawImage(image.getScaledInstance(newWidth, newHeight, BufferedImage.SCALE_FAST), 0, 0, null)
    g.dispose()

    return scaled to scale
  }

  private data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
    val area get() = width.toLong() * height
    fun centerX() = (left + right) / 2f
    fun centerY() = (top + bottom) / 2f
  }

  private fun detectPhotosInternal(image: BufferedImage): List<DetectedPhoto> {
    val width = image.width
    val height = image.height
    val imageArea = width.toLong() * height.toLong()

    // Convert to grayscale
    val gray = convertToGray(image)
    
    // Check if image is uniform
    if (isUniformImage(gray, width, height)) {
      return emptyList()
    }
    
    // Compute Otsu's threshold
    val threshold = computeOtsuThreshold(gray, width, height)
    
    // Create binary mask: pixels above threshold = potential photo content
    val binary = Array(height) { BooleanArray(width) }
    for (y in 0 until height) {
      for (x in 0 until width) {
        binary[y][x] = gray[y][x] > threshold
      }
    }
    
    // Find connected components in the binary mask
    val visited = Array(height) { BooleanArray(width) }
    val components = mutableListOf<MutableList<Pair<Int, Int>>>()
    
    for (y in 0 until height) {
      for (x in 0 until width) {
        if (binary[y][x] && !visited[y][x]) {
          val component = mutableListOf<Pair<Int, Int>>()
          floodFill(binary, visited, x, y, component)
          if (component.size > 100) {
            components.add(component)
          }
        }
      }
    }
    
    // Create bounding rectangles from components
    val rectangles = components.map { pixels ->
      val minX = pixels.minOf { it.first }
      val maxX = pixels.maxOf { it.first }
      val minY = pixels.minOf { it.second }
      val maxY = pixels.maxOf { it.second }
      Rect(minX, minY, maxX + 1, maxY + 1)
    }
    
    // Filter by area
    val minArea = imageArea * minAreaRatio
    val maxArea = imageArea * maxAreaRatio
    
    val filtered = rectangles.filter { r ->
      r.area >= minArea && r.area <= maxArea &&
      r.width >= minSideLength && r.height >= minSideLength
    }
    
    // Merge overlapping rectangles
    val merged = mergeRectangles(filtered)
    
    // Convert to DetectedPhoto
    return merged.take(maxPhotos).map { rect ->
      DetectedPhoto(
        topLeft = PhotoCorner(rect.left.toFloat(), rect.top.toFloat()),
        topRight = PhotoCorner(rect.right.toFloat(), rect.top.toFloat()),
        bottomLeft = PhotoCorner(rect.left.toFloat(), rect.bottom.toFloat()),
        bottomRight = PhotoCorner(rect.right.toFloat(), rect.bottom.toFloat())
      )
    }
  }
  
  private fun floodFill(mask: Array<BooleanArray>, visited: Array<BooleanArray>, startX: Int, startY: Int, component: MutableList<Pair<Int, Int>>) {
    val stack = ArrayDeque<Pair<Int, Int>>()
    stack.add(startX to startY)
    
    while (stack.isNotEmpty()) {
      val (x, y) = stack.removeLast()
      
      if (x < 0 || x >= mask[0].size || y < 0 || y >= mask.size) continue
      if (visited[y][x] || !mask[y][x]) continue
      
      visited[y][x] = true
      component.add(x to y)
      
      stack.add(x + 1 to y)
      stack.add(x - 1 to y)
      stack.add(x to y + 1)
      stack.add(x to y - 1)
    }
  }
  
  private fun computeOtsuThreshold(gray: Array<FloatArray>, width: Int, height: Int): Float {
    val histogram = IntArray(256)
    for (y in 0 until height) {
      for (x in 0 until width) {
        val pixel = gray[y][x].toInt().coerceIn(0, 255)
        histogram[pixel]++
      }
    }
    
    val total = width.toLong() * height
    
    var sum = 0L
    for (i in 0 until 256) {
      sum += i * histogram[i]
    }
    
    var sumB = 0L
    var wB = 0
    var maxVariance = 0.0
    var threshold = 128
    
    for (t in 0 until 256) {
      wB += histogram[t]
      if (wB == 0) continue
      
      val wF = (total - wB).toInt()
      if (wF == 0) break
      
      sumB += t * histogram[t]
      
      val mB = sumB.toDouble() / wB
      val mF = (sum - sumB).toDouble() / wF
      
      val variance = wB.toDouble() * wF * (mB - mF) * (mB - mF)
      
      if (variance > maxVariance) {
        maxVariance = variance
        threshold = t
      }
    }
    
    return threshold.toFloat()
  }

  private fun convertToGray(image: BufferedImage): Array<FloatArray> {
    val width = image.width
    val height = image.height
    val gray = Array(height) { FloatArray(width) }

    for (y in 0 until height) {
      for (x in 0 until width) {
        val rgb = image.getRGB(x, y)
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        gray[y][x] = (0.299f * r + 0.587f * g + 0.114f * b)
      }
    }
    return gray
  }
  
  private fun isUniformImage(gray: Array<FloatArray>, width: Int, height: Int): Boolean {
    var minVal = Float.MAX_VALUE
    var maxVal = Float.MIN_VALUE
    
    for (y in 0 until height) {
      for (x in 0 until width) {
        val v = gray[y][x]
        if (v < minVal) minVal = v
        if (v > maxVal) maxVal = v
      }
    }
    
    return (maxVal - minVal) < 10f
  }
  
  private fun mergeRectangles(rectangles: List<Rect>): List<Rect> {
    if (rectangles.isEmpty()) return emptyList()
    
    val sorted = rectangles.sortedByDescending { it.area }.toMutableList()
    val result = mutableListOf<Rect>()
    
    while (sorted.isNotEmpty()) {
      val current = sorted.removeAt(0)
      var merged = current
      var changed = true
      
      while (changed) {
        changed = false
        val iterator = sorted.iterator()
        while (iterator.hasNext()) {
          val other = iterator.next()
          if (overlaps(merged, other) || isAdjacent(merged, other)) {
            merged = mergeTwo(merged, other)
            iterator.remove()
            changed = true
          }
        }
      }
      
      result.add(merged)
    }
    
    return result
  }
  
  private fun overlaps(a: Rect, b: Rect): Boolean {
    return a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top
  }
  
  private fun isAdjacent(a: Rect, b: Rect): Boolean {
    val buffer = 10
    return a.left - buffer <= b.right && a.right + buffer >= b.left &&
           a.top - buffer <= b.bottom && a.bottom + buffer >= b.top
  }
  
  private fun mergeTwo(a: Rect, b: Rect): Rect {
    return Rect(
      minOf(a.left, b.left),
      minOf(a.top, b.top),
      maxOf(a.right, b.right),
      maxOf(a.bottom, b.bottom)
    )
  }

  fun detectPhotos(file: File): List<DetectedPhoto> {
    val image = javax.imageio.ImageIO.read(file)
    return if (image != null) detectPhotos(image) else emptyList()
  }

  private fun adjustPhotoCount(photos: List<DetectedPhoto>, target: Int, width: Int, height: Int): List<DetectedPhoto> {
    if (photos.size == target) return photos
    
    if (photos.isEmpty()) {
      return createGridPhotos(target, width, height)
    }
    
    if (photos.size < target) {
      val result = photos.toMutableList()
      val sorted = photos.sortedByDescending { it.getBounds().getWidth().toLong() * it.getBounds().getHeight() }
      
      for (photo in sorted) {
        if (result.size >= target) break
        val split = splitPhoto(photo, width, height)
        if (split != null) {
          result.remove(photo)
          result.addAll(split)
        }
      }
      return result.take(target)
    }
    
    if (photos.size > target) {
      val result = photos.toMutableList()
      while (result.size > target) {
        val sorted = result.sortedBy { it.getBounds().getWidth().toLong() * it.getBounds().getHeight() }
        if (sorted.size >= 2) {
          result.remove(sorted[0])
          result.remove(sorted[1])
          result.add(mergePhotos(sorted[0], sorted[1]))
        }
      }
      return result
    }
    
    return photos
  }
  
  private fun createGridPhotos(count: Int, width: Int, height: Int): List<DetectedPhoto> {
    val photos = mutableListOf<DetectedPhoto>()
    val rows = when {
      count <= 2 -> 1
      count <= 4 -> 2
      else -> (count + 2) / 3
    }
    val cols = (count + rows - 1) / rows
    
    val cellWidth = width / cols
    val cellHeight = height / rows
    val margin = 10
    
    var created = 0
    for (row in 0 until rows) {
      for (col in 0 until cols) {
        if (created >= count) break
        
        val x = col * cellWidth + margin
        val y = row * cellHeight + margin
        val w = cellWidth - margin * 2
        val h = cellHeight - margin * 2
        
        if (w > 20 && h > 20) {
          photos.add(DetectedPhoto(
            topLeft = PhotoCorner(x.toFloat(), y.toFloat()),
            topRight = PhotoCorner((x + w).toFloat(), y.toFloat()),
            bottomLeft = PhotoCorner(x.toFloat(), (y + h).toFloat()),
            bottomRight = PhotoCorner((x + w).toFloat(), (y + h).toFloat())
          ))
          created++
        }
      }
    }
    
    return photos
  }

  private fun splitPhoto(photo: DetectedPhoto, imageWidth: Int, imageHeight: Int): List<DetectedPhoto>? {
    val bounds = photo.getBounds()
    val width = bounds.maxX - bounds.minX
    val height = bounds.maxY - bounds.minY
    
    if (width < 50 || height < 50) return null
    
    return if (width > height) {
      val mid = bounds.minX + width / 2f
      listOf(
        DetectedPhoto(
          topLeft = photo.topLeft,
          topRight = PhotoCorner(mid, photo.topRight.y),
          bottomLeft = photo.bottomLeft,
          bottomRight = PhotoCorner(mid, photo.bottomRight.y)
        ),
        DetectedPhoto(
          topLeft = PhotoCorner(mid, photo.topLeft.y),
          topRight = photo.topRight,
          bottomLeft = PhotoCorner(mid, photo.bottomLeft.y),
          bottomRight = photo.bottomRight
        )
      )
    } else {
      val mid = bounds.minY + height / 2f
      listOf(
        DetectedPhoto(
          topLeft = photo.topLeft,
          topRight = photo.topRight,
          bottomLeft = PhotoCorner(photo.bottomLeft.x, mid),
          bottomRight = PhotoCorner(photo.bottomRight.x, mid)
        ),
        DetectedPhoto(
          topLeft = PhotoCorner(photo.topLeft.x, mid),
          topRight = PhotoCorner(photo.topRight.x, mid),
          bottomLeft = photo.bottomLeft,
          bottomRight = photo.bottomRight
        )
      )
    }
  }

  private fun mergePhotos(p1: DetectedPhoto, p2: DetectedPhoto): DetectedPhoto {
    return DetectedPhoto(
      topLeft = PhotoCorner(
        minOf(p1.topLeft.x, p2.topLeft.x),
        minOf(p1.topLeft.y, p2.topLeft.y)
      ),
      topRight = PhotoCorner(
        maxOf(p1.topRight.x, p2.topRight.x),
        minOf(p1.topRight.y, p2.topRight.y)
      ),
      bottomRight = PhotoCorner(
        maxOf(p1.bottomRight.x, p2.bottomRight.x),
        maxOf(p1.bottomRight.y, p2.bottomRight.y)
      ),
      bottomLeft = PhotoCorner(
        minOf(p1.bottomLeft.x, p2.bottomLeft.x),
        maxOf(p1.bottomLeft.y, p2.bottomLeft.y)
      )
    )
  }

  fun addNewPhoto(imageWidth: Int, imageHeight: Int): DetectedPhoto {
    val centerX = imageWidth / 2f
    val centerY = imageHeight / 2f
    val size = minOf(imageWidth, imageHeight) / 3f

    return DetectedPhoto(
        topLeft = PhotoCorner(centerX - size, centerY - size),
        topRight = PhotoCorner(centerX + size, centerY - size),
        bottomLeft = PhotoCorner(centerX - size, centerY + size),
        bottomRight = PhotoCorner(centerX + size, centerY + size))
  }
}
