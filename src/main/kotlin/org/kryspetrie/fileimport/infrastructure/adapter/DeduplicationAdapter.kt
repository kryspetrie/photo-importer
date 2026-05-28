package org.kryspetrie.fileimport.infrastructure.adapter

import boofcv.abst.feature.associate.AssociateDescription
import boofcv.abst.feature.associate.ScoreAssociation
import boofcv.abst.feature.detdesc.DetectDescribePoint
import boofcv.factory.feature.associate.ConfigAssociateGreedy
import boofcv.factory.feature.associate.FactoryAssociation
import boofcv.factory.feature.detdesc.FactoryDetectDescribe
import boofcv.struct.feature.TupleDesc_F64
import boofcv.struct.image.GrayF32
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.imageio.stream.FileImageInputStream
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.ddogleg.struct.FastAccess
import org.imgscalr.Scalr
import org.kryspetrie.fileimport.domain.model.DeduplicationSettings
import org.kryspetrie.fileimport.domain.model.DuplicateInfo
import org.kryspetrie.fileimport.domain.model.DuplicateType
import org.kryspetrie.fileimport.domain.model.ImageFile
import org.kryspetrie.fileimport.domain.model.ImageFileType
import org.kryspetrie.fileimport.domain.port.DeduplicationPort
import org.kryspetrie.fileimport.domain.port.DispatcherProvider

private const val SURF_MAX_DIMENSION = 800
private const val SURF_CONCURRENCY = 2

class DeduplicationAdapter(private val dispatcherProvider: DispatcherProvider) : DeduplicationPort {

    /**
     * BoofCV association using greedy matcher with forwards-backwards validation for O(n log n)
     * matching instead of brute-force O(n×m). Uses FactoryAssociation.greedy with Lowe's ratio test
     * (0.7) via ConfigAssociateGreedy.scoreRatioThreshold.
     */
    private val scoreAssociation: ScoreAssociation<TupleDesc_F64> =
        FactoryAssociation.defaultScore(TupleDesc_F64::class.java)

    private val associator: AssociateDescription<TupleDesc_F64> =
        FactoryAssociation.greedy(ConfigAssociateGreedy(true, 0.7), scoreAssociation)

    override suspend fun findDuplicates(
        images: List<ImageFile>,
        settings: DeduplicationSettings,
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
                            hashMatch = true,
                        )
                    )
                }
        }

        if (settings.enableExifDeduplication) {
            images
                .filter { it.id !in claimed && it.metadata?.dateTimeOriginal != null }
                .groupBy {
                    "${it.metadata?.dateTimeOriginal}_${it.metadata?.make}_${it.metadata?.model}"
                }
                .filter { it.value.size > 1 }
                .forEach { (_, group) ->
                    claimed.addAll(group.map { it.id })
                    duplicates.add(
                        DuplicateInfo(
                            primaryImage = group.first(),
                            duplicateImages = group.drop(1),
                            duplicateType = DuplicateType.EXIF_MATCH,
                            exifMatch = true,
                        )
                    )
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
                        fileTypeMatch = true,
                    )
                )
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
        threshold: Int,
    ): List<DuplicateInfo> = coroutineScope {
        if (images.size < 2) return@coroutineScope emptyList()

        val semaphore = Semaphore(SURF_CONCURRENCY)
        val descriptorMap =
            images
                .map { image ->
                    async(dispatcherProvider.io) {
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
                        similarityScore = matches.size.toFloat(),
                    )
                )
            }
        }
        duplicates
    }

    /**
     * Extracts SURF descriptors using subsampled image read to avoid loading full-resolution
     * images. Uses ImageIO ImageReadParam with source subsampling for memory-efficient loading.
     */
    @Suppress("ReturnCount")
    private fun extractSurfDescriptors(file: File): List<TupleDesc_F64> {
        try {
            val original = readSubsampled(file, SURF_MAX_DIMENSION) ?: return emptyList()
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

    /**
     * Reads an image with subsampling when its dimensions exceed maxDim, avoiding loading the
     * full-resolution image into memory.
     */
    @Suppress("ReturnCount")
    private fun readSubsampled(file: File, maxDim: Int): BufferedImage? {
        try {
            val readers =
                ImageIO.getImageReadersByFormatName(
                    when (file.extension.lowercase()) {
                        "jpg",
                        "jpeg" -> "jpg"
                        "png" -> "png"
                        "bmp" -> "bmp"
                        "gif" -> "gif"
                        else -> file.extension.lowercase()
                    }
                )
            if (!readers.hasNext()) {
                return ImageIO.read(file)
            }

            val reader = readers.next()
            val input = FileImageInputStream(file)
            reader.input = input

            val width = reader.getWidth(0)
            val height = reader.getHeight(0)

            if (width <= maxDim && height <= maxDim) {
                val image = reader.read(0)
                input.close()
                reader.dispose()
                return image
            }

            val subsample = maxOf((width + maxDim - 1) / maxDim, (height + maxDim - 1) / maxDim)

            val param = reader.defaultReadParam
            param.setSourceSubsampling(subsample, subsample, 0, 0)

            val image = reader.read(0, param)
            input.close()
            reader.dispose()
            return image
        } catch (_: Exception) {
            return ImageIO.read(file)
        }
    }

    /**
     * Converts a BufferedImage to BoofCV GrayF32. Uses bulk pixel reads via Raster.getPixels()
     * instead of per-pixel getRGB() for significantly better performance. Falls back to per-pixel
     * if Raster fails.
     */
    private fun toGrayF32(image: BufferedImage): GrayF32 {
        val gray = GrayF32(image.width, image.height)
        try {
            // Try bulk pixel read via Raster — much faster than per-pixel getRGB()
            val raster = image.raster
            val pixels = IntArray(image.width)
            for (y in 0 until image.height) {
                raster.getPixels(0, y, image.width, 1, pixels)
                for (x in pixels.indices) {
                    val rgb = pixels[x]
                    val r = (rgb shr 16) and 0xFF
                    val g = (rgb shr 8) and 0xFF
                    val b = rgb and 0xFF
                    gray.set(x, y, 0.299f * r + 0.587f * g + 0.114f * b)
                }
            }
        } catch (_: Exception) {
            // Fallback to per-pixel getRGB if Raster approach fails
            for (y in 0 until image.height) {
                for (x in 0 until image.width) {
                    val rgb = image.getRGB(x, y)
                    val r = (rgb shr 16) and 0xFF
                    val g = (rgb shr 8) and 0xFF
                    val b = rgb and 0xFF
                    gray.set(x, y, 0.299f * r + 0.587f * g + 0.114f * b)
                }
            }
        }
        return gray
    }

    /**
     * Counts SURF descriptor matches using BoofCV's greedy association. Uses forwards-backwards
     * validation with Lowe's ratio test (0.7) for O(n log n) matching instead of brute-force
     * O(n×m).
     */
    @Suppress("ReturnCount")
    private fun countSurfMatches(descA: List<TupleDesc_F64>, descB: List<TupleDesc_F64>): Int {
        if (descA.isEmpty() || descB.isEmpty()) return 0
        try {
            // Convert Lists to FastAccess for BoofCV API compatibility
            val srcAccess = listToFastAccess(descA)
            val dstAccess = listToFastAccess(descB)

            associator.setSource(srcAccess)
            associator.setDestination(dstAccess)
            associator.associate()

            val matches: FastAccess<boofcv.struct.feature.AssociatedIndex> = associator.matches
            var count = 0
            for (i in 0 until matches.size) {
                val match = matches.get(i)
                // dst >= 0 means a valid association was found
                if (match.dst >= 0) {
                    count++
                }
            }
            return count
        } catch (_: Exception) {
            // Fallback to brute-force if associator fails
            return bruteForceMatchCount(descA, descB)
        }
    }

    /** Converts a List of TupleDesc_F64 to a FastAccess wrapper for BoofCV API. */
    private fun listToFastAccess(list: List<TupleDesc_F64>): FastAccess<TupleDesc_F64> {
        val array = list.toTypedArray()
        return object : FastAccess<TupleDesc_F64>(TupleDesc_F64::class.java) {
            init {
                @Suppress("UNCHECKED_CAST")
                data = array as Array<TupleDesc_F64>
                size = list.size
            }

            override fun remove(index: Int): TupleDesc_F64 {
                throw UnsupportedOperationException("Read-only FastAccess wrapper")
            }

            override fun removeSwap(index: Int): TupleDesc_F64 {
                throw UnsupportedOperationException("Read-only FastAccess wrapper")
            }

            override fun toList(): MutableList<TupleDesc_F64> {
                return list.toMutableList()
            }
        }
    }

    /** Fallback brute-force matching with Lowe's ratio test. */
    private fun bruteForceMatchCount(descA: List<TupleDesc_F64>, descB: List<TupleDesc_F64>): Int {
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

    /**
     * Scales down an image using imgscalr's Scalr.resize with balanced quality/speed. Replaces
     * hand-rolled Graphics2D scaling that lacked anti-aliasing.
     */
    @Suppress("ReturnCount")
    private fun scaleDown(img: BufferedImage, maxDim: Int): BufferedImage {
        if (img.width <= maxDim && img.height <= maxDim) return img
        try {
            return Scalr.resize(img, Scalr.Method.BALANCED, maxDim)
        } catch (_: Exception) {
            // Fallback to manual scaling if imgscalr fails
            val scale = maxDim.toDouble() / maxOf(img.width, img.height)
            val w = (img.width * scale).toInt()
            val h = (img.height * scale).toInt()
            val scaled = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
            val g = scaled.createGraphics()
            g.drawImage(img, 0, 0, w, h, null)
            g.dispose()
            return scaled
        }
    }

    override suspend fun areDuplicates(
        image1: ImageFile,
        image2: ImageFile,
        settings: DeduplicationSettings,
    ): Boolean = getDuplicateType(image1, image2, settings) != null

    override suspend fun calculateSimilarity(image1: ImageFile, image2: ImageFile): Float {
        val descA = extractSurfDescriptors(image1.file)
        val descB = extractSurfDescriptors(image2.file)
        return countSurfMatches(descA, descB).toFloat()
    }

    override suspend fun groupSimilarImages(
        images: List<ImageFile>,
        threshold: Float,
    ): Map<String, List<ImageFile>> = emptyMap()

    /**
     * Finds RAW+JPEG camera pairs using O(1) lookup instead of O(JPEG×RAW) nested scan. Builds a
     * map keyed by (dateTaken, cameraModel, last4Chars) for instant matching.
     */
    override suspend fun findCameraPairs(
        images: List<ImageFile>
    ): List<Pair<ImageFile, ImageFile>> {
        val pairs = mutableListOf<Pair<ImageFile, ImageFile>>()
        val jpegImages = images.filter { it.fileType == ImageFileType.JPEG }
        val rawImages = images.filter { it.fileType.isRawFormat }

        // Build map: (dateTaken, cameraModel, last4) → RAW for O(1) lookup
        val rawByKey = mutableMapOf<String, ImageFile>()
        for (raw in rawImages) {
            val key =
                buildPairKey(
                    raw.dateTaken,
                    raw.metadata?.cameraModel,
                    raw.file.nameWithoutExtension,
                )
            rawByKey[key] = raw
        }

        for (jpeg in jpegImages) {
            val key =
                buildPairKey(
                    jpeg.dateTaken,
                    jpeg.metadata?.cameraModel,
                    jpeg.file.nameWithoutExtension,
                )
            rawByKey[key]?.let { raw -> pairs.add(raw to jpeg) }
        }
        return pairs
    }

    /** Builds a composite key for RAW+JPEG pair matching. */
    private fun buildPairKey(
        dateTaken: java.time.LocalDateTime?,
        cameraModel: String?,
        nameWithoutExtension: String,
    ): String {
        return "${dateTaken}_${cameraModel}_${nameWithoutExtension.takeLast(4)}"
    }

    @Suppress("ReturnCount", "NestedBlockDepth")
    override suspend fun getDuplicateType(
        image1: ImageFile,
        image2: ImageFile,
        settings: DeduplicationSettings,
    ): DuplicateType? {
        val hashesAvailable = image1.hash != null && image2.hash != null
        val hashesMatch = image1.hash == image2.hash
        if (settings.enableHashDeduplication && hashesAvailable && hashesMatch)
            return DuplicateType.EXACT_HASH
        if (settings.enableExifDeduplication) {
            val e1 = image1.metadata
            val e2 = image2.metadata
            val datesAvailable = e1?.dateTimeOriginal != null && e2?.dateTimeOriginal != null
            val datesMatch = e1?.dateTimeOriginal == e2?.dateTimeOriginal
            val cameraMatch = e1?.cameraModel == e2?.cameraModel
            if (datesAvailable && datesMatch && cameraMatch) return DuplicateType.EXIF_MATCH
        }
        if (settings.ignoreDifferentFileTypes) {
            val isRaw1 = image1.fileType.isRawFormat
            val isRaw2 = image2.fileType.isRawFormat
            val isJpeg1 = image1.fileType == ImageFileType.JPEG
            val isJpeg2 = image2.fileType == ImageFileType.JPEG
            val isRawJpegPair = (isRaw1 && isJpeg2) || (isRaw2 && isJpeg1)
            if (isRawJpegPair) {
                val d1 = image1.dateTaken
                val d2 = image2.dateTaken
                if (d1 != null && d2 != null) {
                    val withinThreshold = java.time.Duration.between(d1, d2).seconds < 5
                    if (withinThreshold) return DuplicateType.CAMERA_PAIR
                }
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
