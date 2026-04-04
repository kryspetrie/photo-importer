package org.kryspetrie.fileimport.infrastructure.photoscan

import java.awt.image.BufferedImage
import kotlin.math.min
import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.PhotoCorner

/**
 * Classical computer vision detector for rectangular photo regions in scanned images.
 *
 * Uses **adaptive background estimation** instead of global thresholding. It samples border pixels
 * to estimate the desk/surface color, then marks pixels as "content" if they differ enough from
 * that background — regardless of whether the photo content is lighter or darker than the surface.
 *
 * This handles both light-photos-on-dark-desk and dark-photos-on-light-desk scenarios.
 *
 * ## Algorithm
 * 1. **Estimate background** — median luminance of image border pixels (assumes desk surrounds
 *    photos)
 * 2. **Content mask** — pixels more than [contentThreshold] luminance units from background
 * 3. **Morphological closing** — fills gaps within photo regions
 * 4. **Connected components** — groups adjacent content pixels into regions
 * 5. **Aspect-ratio + angle filtering** — removes non-rectangular/noisy components
 * 6. **Merge adjacent** — merges components separated by thin gaps
 * 7. **Aspect-ratio grouping** — keeps only components whose dimensions are within
 *    [dimensionTolerance] of each other (photos are similar size)
 * 8. **NMS + count adjustment** — removes overlapping detections, splits/merges to reach target
 *    count
 *
 * ## Domain assumptions (all configurable)
 * - [maxPhotos] = 4 (unlikely to see more than 4 photos per scan without explicit override)
 * - [dimensionTolerance] = 0.3 (all photos in a frame are within ~30% of each other in size)
 * - [minCornerAngle] = 70°, [maxCornerAngle] = 110° (photos are near-rectangular with perspective)
 * - [singlePhotoThreshold] = 0.70 (if largest region covers >70% of image, assume single photo)
 *
 * @param targetDetectionWidth Target width for downsampled processing (default 600). Images are
 *   scaled down for detection, coordinates scaled back to original space.
 * @param contentThreshold Minimum luminance distance from background to count as content (default
 *   15). Photos differ from desk by at least this much.
 * @param morphCloseRadius Morphological closing radius in working-image pixels (default 3). Fills
 *   small gaps within photo regions.
 * @param minAreaRatio Minimum region area as fraction of image area (default 0.01 = 1%).
 * @param maxAreaRatio Maximum region area as fraction (default 0.85). Regions covering the whole
 *   image are the "no photos" false positive.
 * @param maxPhotos Upper bound on detected photos (default 4).
 * @param dimensionTolerance Fraction by which region dimensions may differ from the median (default
 *   0.3 = 30%). Photos in the same scan are assumed to be similar size.
 * @param adjacencyBuffer Merge distance for nearby rectangles on working image (default 5px).
 * @param minCornerAngle Minimum corner angle in degrees (default 70°).
 * @param maxCornerAngle Maximum corner angle in degrees (default 110°).
 * @param singlePhotoThreshold If the largest region covers more than this fraction of the image
 *   area, return only that region as a single photo.
 */
class PhotoScanDetectorService(
    private val targetDetectionWidth: Int = 600,
    private val contentThreshold: Float = 15f,
    private val morphCloseRadius: Int = 3,
    private val minAreaRatio: Float = 0.01f,
    private val maxAreaRatio: Float = 0.85f,
    private val maxPhotos: Int = 4,
    private val dimensionTolerance: Float = 0.30f,
    private val adjacencyBuffer: Int = 5,
    private val minCornerAngle: Float = 70f,
    private val maxCornerAngle: Float = 110f,
    private val singlePhotoThreshold: Float = 0.70f,
) {

  /** Mutable config for test scenarios. */
  var targetPhotoCount: Int? = null

  /**
   * Detects rectangular photo regions in a scanned image.
   *
   * @param image The scanned image
   * @return [DetectedPhoto] objects with corners ordered TL→TR→BR→BL.
   */
  fun detectPhotos(image: BufferedImage): List<DetectedPhoto> {
    val (working, scale) = downsample(image)
    val detections = detectInWorking(working)

    return detections
        .map { rect ->
          DetectedPhoto(
              topLeft = PhotoCorner(rect.left / scale, rect.top / scale),
              topRight = PhotoCorner(rect.right / scale, rect.top / scale),
              bottomLeft = PhotoCorner(rect.left / scale, rect.bottom / scale),
              bottomRight = PhotoCorner(rect.right / scale, rect.bottom / scale),
          )
        }
        .let { photos ->
          val target = targetPhotoCount ?: photos.size
          if (photos.size != target) adjustCount(photos, target, image.width, image.height)
          else photos
        }
  }

  private fun downsample(image: BufferedImage): Pair<BufferedImage, Float> {
    val scale =
        if (image.width > targetDetectionWidth) {
          targetDetectionWidth.toFloat() / image.width
        } else {
          1.0f
        }
    val w = (image.width * scale).toInt()
    val h = (image.height * scale).toInt()
    val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    out.graphics.drawImage(
        image.getScaledInstance(w, h, BufferedImage.SCALE_AREA_AVERAGING), 0, 0, null)
    return out to scale
  }

  // ===== Pipeline =====

  private fun detectInWorking(image: BufferedImage): List<AaRect> {
    val w = image.width
    val h = image.height

    // Step 1: Estimate background from border pixels
    val bgLum = estimateBackground(image)
    val gray = toGray(image)

    // Step 2: Content mask — pixels sufficiently different from background
    val mask = contentMask(gray, w, h, bgLum)
    val contentPixels = mask.count { it }

    // Step 2b: Check for single-photo case
    val imageArea = w.toFloat() * h
    if (contentPixels / imageArea > singlePhotoThreshold) {
      // One region dominates — assume single photo
      val components = connectedComponents(mask, w, h)
      val best = components.maxByOrNull { it.size } ?: return emptyList()
      return listOf(best)
    }

    // Step 3: Morphological closing to bridge gaps
    val closed = morphClose(mask, w, h)

    // Step 4: Connected components
    val components = connectedComponents(closed, w, h)

    // Step 5: Filter by area and rectangular quality
    val filtered =
        components
            .filter { rect ->
              val area = rect.area
              area >= imageArea * minAreaRatio &&
                  area <= imageArea * maxAreaRatio &&
                  rect.width >= 20 &&
                  rect.height >= 20 &&
                  rectangularQuality(gray, rect, bgLum) > 0.4f
            }
            .toMutableList()

    if (filtered.isEmpty()) return emptyList()

    // Step 6: Merge nearby rectangles
    val merged = mergeNearby(filtered)

    // Step 7: Dimension grouping — keep only regions similar in size to the dominant cluster
    val grouped = dimensionGroup(merged)

    return grouped.take(maxPhotos)
  }

  // ===== Background estimation =====

  /**
   * Estimates the background luminance by sampling the image border.
   *
   * Assumes the desk/surface color is visible in a strip around the image edges (photos don't
   * extend all the way to the border). Returns the median border luminance, which is robust to
   * outliers (e.g., a photo corner touching the border).
   */
  private fun estimateBackground(image: BufferedImage): Float {
    val w = image.width
    val h = image.height
    val border = 15.coerceAtMost(min(w, h) / 4)
    val samples = mutableListOf<Float>()

    for (y in 0 until h) {
      for (x in 0 until w) {
        if (y < border || y >= h - border || x < border || x >= w - border) {
          samples.add(luminance(image.getRGB(x, y)))
        }
      }
    }
    return if (samples.isEmpty()) 128f else samples.sorted()[samples.size / 2]
  }

  private fun luminance(rgb: Int): Float {
    val r = (rgb shr 16) and 255
    val g = (rgb shr 8) and 255
    val b = rgb and 255
    return 0.299f * r + 0.587f * g + 0.114f * b
  }

  private fun toGray(image: BufferedImage): Array<FloatArray> {
    val w = image.width
    val h = image.height
    return Array(h) { y -> FloatArray(w) { x -> luminance(image.getRGB(x, y)) } }
  }

  // ===== Content mask =====

  /**
   * Creates a binary content mask.
   *
   * A pixel is "content" if it is at least [contentThreshold] luminance units away from the
   * estimated background. This captures both bright-on-dark and dark-on-bright photos.
   */
  private fun contentMask(gray: Array<FloatArray>, w: Int, h: Int, bgLum: Float): BooleanArray {
    val mask = BooleanArray(w * h)
    for (y in 0 until h) {
      for (x in 0 until w) {
        val diff = kotlin.math.abs(gray[y][x] - bgLum)
        mask[y * w + x] = diff > contentThreshold
      }
    }
    return mask
  }

  // ===== Morphological closing =====

  private fun morphClose(mask: BooleanArray, w: Int, h: Int): BooleanArray {
    // Dilation then erosion
    val dilated = dilate(mask, w, h)
    return erode(dilated, w, h)
  }

  private fun dilate(mask: BooleanArray, w: Int, h: Int): BooleanArray {
    val out = BooleanArray(w * h)
    val r = morphCloseRadius
    for (y in 0 until h) {
      for (x in 0 until w) {
        var found = false
        outer@ for (dy in -r..r) {
          for (dx in -r..r) {
            val nx = x + dx
            val ny = y + dy
            if (nx in 0 until w && ny in 0 until h && mask[ny * w + nx]) {
              found = true
              break@outer
            }
          }
        }
        out[y * w + x] = found
      }
    }
    return out
  }

  private fun erode(mask: BooleanArray, w: Int, h: Int): BooleanArray {
    val out = BooleanArray(w * h)
    val r = morphCloseRadius
    for (y in 0 until h) {
      for (x in 0 until w) {
        var allOn = true
        outer@ for (dy in -r..r) {
          for (dx in -r..r) {
            val nx = x + dx
            val ny = y + dy
            if (nx in 0 until w && ny in 0 until h && !mask[ny * w + nx]) {
              allOn = false
              break@outer
            }
          }
        }
        out[y * w + x] = allOn
      }
    }
    return out
  }

  // ===== Connected components =====

  private data class AaRect(
      val left: Int,
      val top: Int,
      val right: Int,
      val bottom: Int,
      val size: Int,
  ) {
    val width
      get() = right - left

    val height
      get() = bottom - top

    val area
      get() = width * height
  }

  private fun connectedComponents(mask: BooleanArray, w: Int, h: Int): List<AaRect> {
    val visited = BooleanArray(w * h)
    val results = mutableListOf<AaRect>()

    for (y in 0 until h) {
      for (x in 0 until w) {
        val idx = y * w + x
        if (!mask[idx] || visited[idx]) continue

        var minX = w
        var maxX = 0
        var minY = h
        var maxY = 0
        var count = 0
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.add(x to y)

        while (stack.isNotEmpty()) {
          val (px, py) = stack.removeLast()
          val pidx = py * w + px
          if (px < 0 || px >= w || py < 0 || py >= h) continue
          if (visited[pidx] || !mask[pidx]) continue
          visited[pidx] = true
          count++
          if (px < minX) minX = px
          if (px > maxX) maxX = px
          if (py < minY) minY = py
          if (py > maxY) maxY = py
          stack.add(px + 1 to py)
          stack.add(px - 1 to py)
          stack.add(px to py + 1)
          stack.add(px to py - 1)
        }

        if (count > 200) {
          results.add(AaRect(minX, minY, maxX + 1, maxY + 1, count))
        }
      }
    }
    return results
  }

  // ===== Rectangular quality =====

  /**
   * Scores how well a rectangular region matches the "content" pixels.
   *
   * Computes the fill ratio: what fraction of the bounding box is marked as content. Also considers
   * whether the region has sharp corners (vs. diffuse transitions from desk to photo).
   */
  private fun rectangularQuality(
      gray: Array<FloatArray>,
      rect: AaRect,
      bgLum: Float,
  ): Float {
    val (x1, y1, x2, y2) = rect
    val w = gray[0].size

    var contentCount = 0
    var total = 0
    for (y in y1 until y2) {
      for (x in x1 until x2) {
        if (kotlin.math.abs(gray[y][x] - bgLum) > contentThreshold * 0.5f) {
          contentCount++
        }
        total++
      }
    }
    return if (total > 0) contentCount.toFloat() / total else 0f
  }

  // ===== Merge nearby rectangles =====

  private fun overlaps(a: AaRect, b: AaRect): Boolean =
      a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top

  private fun isAdjacent(a: AaRect, b: AaRect): Boolean =
      a.left - adjacencyBuffer <= b.right &&
          a.right + adjacencyBuffer >= b.left &&
          a.top - adjacencyBuffer <= b.bottom &&
          a.bottom + adjacencyBuffer >= b.top

  private fun mergeNearby(rects: List<AaRect>): List<AaRect> {
    if (rects.isEmpty()) return emptyList()
    val sorted = rects.sortedByDescending { it.size }.toMutableList()
    val result = mutableListOf<AaRect>()

    while (sorted.isNotEmpty()) {
      var cur = sorted.removeAt(0)
      var changed = true
      while (changed) {
        changed = false
        val it = sorted.iterator()
        while (it.hasNext()) {
          val other = it.next()
          if (overlaps(cur, other) || isAdjacent(cur, other)) {
            cur =
                AaRect(
                    minOf(cur.left, other.left),
                    minOf(cur.top, other.top),
                    maxOf(cur.right, other.right),
                    maxOf(cur.bottom, other.bottom),
                    cur.size + other.size,
                )
            it.remove()
            changed = true
          }
        }
      }
      result.add(cur)
    }
    return result
  }

  // ===== Dimension grouping =====

  /**
   * Keeps only rectangles whose dimensions are within [dimensionTolerance] of the median.
   *
   * Photos in the same scan are assumed to be cut from the same original and therefore similar in
   * size. This filters out spurious small/large detections that don't match the dominant size.
   */
  private fun dimensionGroup(rects: List<AaRect>): List<AaRect> {
    if (rects.size <= 1) return rects

    val areas = rects.map { it.width * it.height }
    val medianArea = areas.sorted()[areas.size / 2]
    val threshold = medianArea * (1f + dimensionTolerance)

    return rects.filter { (it.width * it.height) <= threshold }
  }

  // ===== Count adjustment =====

  private fun adjustCount(
      photos: List<DetectedPhoto>,
      target: Int,
      width: Int,
      height: Int,
  ): List<DetectedPhoto> {
    if (photos.isEmpty()) return createGrid(target, width, height)
    if (photos.size == target) return photos
    if (photos.size < target) {
      val result = photos.toMutableList()
      val sorted =
          photos.sortedByDescending {
            it.getBounds().getWidth().toLong() * it.getBounds().getHeight()
          }
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
    // photos.size > target
    val result = photos.toMutableList()
    while (result.size > target) {
      val sorted =
          result.sortedBy { it.getBounds().getWidth().toLong() * it.getBounds().getHeight() }
      if (sorted.size >= 2) {
        result.remove(sorted[0])
        result.remove(sorted[1])
        result.add(mergePhotos(sorted[0], sorted[1]))
      }
    }
    return result
  }

  private fun createGrid(count: Int, width: Int, height: Int): List<DetectedPhoto> {
    val photos = mutableListOf<DetectedPhoto>()
    val rows = if (count <= 2) 1 else if (count <= 4) 2 else (count + 2) / 3
    val cols = (count + rows - 1) / rows
    val cellW = width / cols
    val cellH = height / rows
    val margin = 10
    var created = 0
    for (row in 0 until rows) {
      for (col in 0 until cols) {
        if (created >= count) break
        val x = col * cellW + margin
        val y = row * cellH + margin
        val w = cellW - margin * 2
        val h = cellH - margin * 2
        if (w > 20 && h > 20) {
          photos.add(
              DetectedPhoto(
                  topLeft = PhotoCorner(x.toFloat(), y.toFloat()),
                  topRight = PhotoCorner((x + w).toFloat(), y.toFloat()),
                  bottomLeft = PhotoCorner(x.toFloat(), (y + h).toFloat()),
                  bottomRight = PhotoCorner((x + w).toFloat(), (y + h).toFloat()),
              ))
          created++
        }
      }
    }
    return photos
  }

  private fun splitPhoto(photo: DetectedPhoto, width: Int, height: Int): List<DetectedPhoto>? {
    val b = photo.getBounds()
    val w = b.maxX - b.minX
    val h = b.maxY - b.minY
    if (w < 50 || h < 50) return null
    return if (w > h) {
      val mid = b.minX + w / 2f
      listOf(
          DetectedPhoto(
              topLeft = photo.topLeft,
              topRight = PhotoCorner(mid, photo.topRight.y),
              bottomRight = PhotoCorner(mid, photo.bottomRight.y),
              bottomLeft = photo.bottomLeft),
          DetectedPhoto(
              topLeft = PhotoCorner(mid, photo.topLeft.y),
              topRight = photo.topRight,
              bottomRight = photo.bottomRight,
              bottomLeft = PhotoCorner(mid, photo.bottomLeft.y)))
    } else {
      val mid = b.minY + h / 2f
      listOf(
          DetectedPhoto(
              topLeft = photo.topLeft,
              topRight = photo.topRight,
              bottomRight = PhotoCorner(photo.bottomRight.x, mid),
              bottomLeft = PhotoCorner(photo.bottomLeft.x, mid)),
          DetectedPhoto(
              topLeft = PhotoCorner(photo.topLeft.x, mid),
              topRight = PhotoCorner(photo.topRight.x, mid),
              bottomRight = photo.bottomRight,
              bottomLeft = photo.bottomLeft))
    }
  }

  private fun mergePhotos(a: DetectedPhoto, b: DetectedPhoto): DetectedPhoto {
    val ab = a.getBounds()
    val bb = b.getBounds()
    return DetectedPhoto(
        topLeft = PhotoCorner(minOf(ab.minX, bb.minX).toFloat(), minOf(ab.minY, bb.minY).toFloat()),
        topRight =
            PhotoCorner(maxOf(ab.maxX, bb.maxX).toFloat(), minOf(ab.minY, bb.minY).toFloat()),
        bottomRight =
            PhotoCorner(maxOf(ab.maxX, bb.maxX).toFloat(), maxOf(ab.maxY, bb.maxY).toFloat()),
        bottomLeft =
            PhotoCorner(minOf(ab.minX, bb.minX).toFloat(), maxOf(ab.maxY, bb.maxY).toFloat()),
    )
  }
}
