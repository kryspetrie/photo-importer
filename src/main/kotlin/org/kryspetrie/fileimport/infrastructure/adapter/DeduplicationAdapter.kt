package org.kryspetrie.fileimport.infrastructure.adapter

import boofcv.abst.feature.detdesc.DetectDescribePoint
import boofcv.factory.feature.detdesc.FactoryDetectDescribe
import boofcv.struct.feature.TupleDesc_F64
import boofcv.struct.image.GrayF32
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.kryspetrie.fileimport.domain.model.*
import org.kryspetrie.fileimport.domain.port.DeduplicationPort
import org.kryspetrie.fileimport.domain.port.ImageRepositoryPort

private const val SURF_MAX_DIMENSION = 800
private const val SURF_CONCURRENCY = 2

class DeduplicationAdapter(private val imageRepository: ImageRepositoryPort) : DeduplicationPort {

  override suspend fun findDuplicates(
      images: List<ImageFile>,
      settings: DeduplicationSettings
  ): List<DuplicateInfo> {
    val duplicates = mutableListOf<DuplicateInfo>()
    val claimed = mutableSetOf<String>()

    if (settings.enableHashDeduplication) {
      images
          .filter { it.hash != null }
          .groupBy { it.hash!! }
          .filter { it.value.size > 1 }
          .forEach { (_, group) ->
            claimed.addAll(group.map { it.id })
            duplicates.add(
                DuplicateInfo(
                    primaryImage = group.first(),
                    duplicateImages = group.drop(1),
                    duplicateType = DuplicateType.EXACT_HASH,
                    hashMatch = true))
          }
    }

    if (settings.enableExifDeduplication) {
      images
          .filter { it.id !in claimed && it.metadata?.dateTimeOriginal != null }
          .groupBy { "${it.metadata?.dateTimeOriginal}_${it.metadata?.make}_${it.metadata?.model}" }
          .filter { it.value.size > 1 }
          .forEach { (_, group) ->
            claimed.addAll(group.map { it.id })
            duplicates.add(
                DuplicateInfo(
                    primaryImage = group.first(),
                    duplicateImages = group.drop(1),
                    duplicateType = DuplicateType.EXIF_MATCH,
                    exifMatch = true))
          }
    }

    if (settings.ignoreDifferentFileTypes) {
      findCameraPairs(images.filter { it.id !in claimed }).forEach { (raw, jpeg) ->
        claimed.addAll(listOf(raw.id, jpeg.id))
        duplicates.add(
            DuplicateInfo(
                primaryImage = raw,
                duplicateImages = listOf(jpeg),
                duplicateType = DuplicateType.CAMERA_PAIR,
                fileTypeMatch = true))
      }
    }

    if (settings.enableSurfMatching) {
      val unclaimed = images.filter { it.id !in claimed && !it.fileType.isVideo }
      val surfDuplicates = findSurfDuplicates(unclaimed, settings.surfMatchThreshold)
      duplicates.addAll(surfDuplicates)
    }

    return duplicates
  }

  private suspend fun findSurfDuplicates(
      images: List<ImageFile>,
      threshold: Int
  ): List<DuplicateInfo> = coroutineScope {
    if (images.size < 2) return@coroutineScope emptyList()

    val semaphore = Semaphore(SURF_CONCURRENCY)
    val descriptorMap =
        images
            .map { image ->
              async(Dispatchers.IO) {
                semaphore.withPermit { image to extractSurfDescriptors(image.file) }
              }
            }
            .awaitAll()
            .filter { it.second.isNotEmpty() }

    val duplicates = mutableListOf<DuplicateInfo>()
    val claimed = mutableSetOf<String>()

    for (i in descriptorMap.indices) {
      if (descriptorMap[i].first.id in claimed) continue
      val (imgA, descA) = descriptorMap[i]
      val matches = mutableListOf<ImageFile>()

      for (j in i + 1 until descriptorMap.size) {
        if (descriptorMap[j].first.id in claimed) continue
        val (imgB, descB) = descriptorMap[j]
        val matchCount = countSurfMatches(descA, descB)
        if (matchCount >= threshold) {
          matches.add(imgB)
          claimed.add(imgB.id)
        }
      }

      if (matches.isNotEmpty()) {
        claimed.add(imgA.id)
        duplicates.add(
            DuplicateInfo(
                primaryImage = imgA,
                duplicateImages = matches,
                duplicateType = DuplicateType.SURF_MATCH,
                similarityScore = matches.size.toFloat()))
      }
    }
    duplicates
  }

  private fun extractSurfDescriptors(file: File): List<TupleDesc_F64> {
    try {
      val original = ImageIO.read(file) ?: return emptyList()
      val scaled = scaleDown(original, SURF_MAX_DIMENSION)
      val gray = toGrayF32(scaled)

      val detector: DetectDescribePoint<GrayF32, TupleDesc_F64> =
          FactoryDetectDescribe.surfStable(null, null, null, GrayF32::class.java)
      detector.detect(gray)

      return (0 until detector.numberOfFeatures).map { detector.getDescription(it).copy() }
    } catch (_: Exception) {
      return emptyList()
    }
  }

  private fun toGrayF32(image: BufferedImage): GrayF32 {
    val gray = GrayF32(image.width, image.height)
    for (y in 0 until image.height) {
      for (x in 0 until image.width) {
        val rgb = image.getRGB(x, y)
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        gray.set(x, y, 0.299f * r + 0.587f * g + 0.114f * b)
      }
    }
    return gray
  }

  private fun countSurfMatches(descA: List<TupleDesc_F64>, descB: List<TupleDesc_F64>): Int {
    if (descA.isEmpty() || descB.isEmpty()) return 0
    var matches = 0
    for (a in descA) {
      var bestDist = Double.MAX_VALUE
      var secondBest = Double.MAX_VALUE
      for (b in descB) {
        val dist = euclideanDistance(a, b)
        if (dist < bestDist) {
          secondBest = bestDist
          bestDist = dist
        } else if (dist < secondBest) {
          secondBest = dist
        }
      }
      // Lowe's ratio test
      if (bestDist < 0.7 * secondBest) matches++
    }
    return matches
  }

  private fun euclideanDistance(a: TupleDesc_F64, b: TupleDesc_F64): Double {
    var sum = 0.0
    for (i in a.data.indices) {
      val diff = a.data[i] - b.data[i]
      sum += diff * diff
    }
    return Math.sqrt(sum)
  }

  private fun scaleDown(img: BufferedImage, maxDim: Int): BufferedImage {
    if (img.width <= maxDim && img.height <= maxDim) return img
    val scale = maxDim.toDouble() / maxOf(img.width, img.height)
    val w = (img.width * scale).toInt()
    val h = (img.height * scale).toInt()
    val scaled = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    val g = scaled.createGraphics()
    g.drawImage(img, 0, 0, w, h, null)
    g.dispose()
    return scaled
  }

  override suspend fun areDuplicates(
      image1: ImageFile,
      image2: ImageFile,
      settings: DeduplicationSettings
  ): Boolean = getDuplicateType(image1, image2, settings) != null

  override suspend fun calculateSimilarity(image1: ImageFile, image2: ImageFile): Float {
    val descA = extractSurfDescriptors(image1.file)
    val descB = extractSurfDescriptors(image2.file)
    return countSurfMatches(descA, descB).toFloat()
  }

  override suspend fun groupSimilarImages(
      images: List<ImageFile>,
      threshold: Float
  ): Map<String, List<ImageFile>> = emptyMap()

  override suspend fun findCameraPairs(images: List<ImageFile>): List<Pair<ImageFile, ImageFile>> {
    val pairs = mutableListOf<Pair<ImageFile, ImageFile>>()
    val jpegImages = images.filter { it.fileType == ImageFileType.JPEG }
    val rawImages = images.filter { it.fileType.isRawFormat }
    for (jpeg in jpegImages) {
      rawImages
          .find { raw ->
            jpeg.dateTaken == raw.dateTaken &&
                jpeg.metadata?.cameraModel == raw.metadata?.cameraModel &&
                jpeg.file.nameWithoutExtension.takeLast(4) ==
                    raw.file.nameWithoutExtension.takeLast(4)
          }
          ?.let { pairs.add(it to jpeg) }
    }
    return pairs
  }

  override suspend fun getDuplicateType(
      image1: ImageFile,
      image2: ImageFile,
      settings: DeduplicationSettings
  ): DuplicateType? {
    if (settings.enableHashDeduplication &&
        image1.hash != null &&
        image2.hash != null &&
        image1.hash == image2.hash)
        return DuplicateType.EXACT_HASH
    if (settings.enableExifDeduplication) {
      val e1 = image1.metadata
      val e2 = image2.metadata
      if (e1?.dateTimeOriginal != null &&
          e2?.dateTimeOriginal != null &&
          e1.dateTimeOriginal == e2.dateTimeOriginal &&
          e1.cameraModel == e2.cameraModel)
          return DuplicateType.EXIF_MATCH
    }
    if (settings.ignoreDifferentFileTypes) {
      val isRaw1 = image1.fileType.isRawFormat
      val isRaw2 = image2.fileType.isRawFormat
      val isJpeg1 = image1.fileType == ImageFileType.JPEG
      val isJpeg2 = image2.fileType == ImageFileType.JPEG
      if ((isRaw1 && isJpeg2) || (isRaw2 && isJpeg1)) {
        val d1 = image1.dateTaken
        val d2 = image2.dateTaken
        if (d1 != null && d2 != null && java.time.Duration.between(d1, d2).seconds < 5)
            return DuplicateType.CAMERA_PAIR
      }
    }
    if (settings.enableSurfMatching) {
      val descA = extractSurfDescriptors(image1.file)
      val descB = extractSurfDescriptors(image2.file)
      if (countSurfMatches(descA, descB) >= settings.surfMatchThreshold)
          return DuplicateType.SURF_MATCH
    }
    return null
  }
}
