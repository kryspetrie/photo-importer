package org.kryspetrie.fileimport.infrastructure.adapter

import org.kryspetrie.fileimport.domain.model.DeduplicationSettings
import org.kryspetrie.fileimport.domain.model.DuplicateInfo
import org.kryspetrie.fileimport.domain.model.DuplicateType
import org.kryspetrie.fileimport.domain.model.ImageFile
import org.kryspetrie.fileimport.domain.model.ImageFileType
import org.kryspetrie.fileimport.domain.port.DeduplicationPort
import org.kryspetrie.fileimport.domain.port.DispatcherProvider

class DeduplicationAdapter(
    private val surfService: SurfDeduplicationService,
    private val dispatcherProvider: DispatcherProvider,
) : DeduplicationPort {

    override suspend fun findDuplicates(
        images: List<ImageFile>,
        settings: DeduplicationSettings,
    ): List<DuplicateInfo> {
        val duplicates = mutableListOf<DuplicateInfo>()
        val claimed = mutableSetOf<String>()

        if (settings.enableHashDeduplication) {
            images
                .filter { it.hash != null && it.hash.isNotEmpty() }
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
            val surfDuplicates =
                surfService.findSurfDuplicates(unclaimed, settings.surfMatchThreshold)
            duplicates.addAll(surfDuplicates)
        }

        return duplicates
    }

    override suspend fun areDuplicates(
        image1: ImageFile,
        image2: ImageFile,
        settings: DeduplicationSettings,
    ): Boolean = getDuplicateType(image1, image2, settings) != null

    override suspend fun calculateSimilarity(image1: ImageFile, image2: ImageFile): Float =
        surfService.calculateSimilarity(image1, image2)

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

    @Suppress("NestedBlockDepth")
    override suspend fun getDuplicateType(
        image1: ImageFile,
        image2: ImageFile,
        settings: DeduplicationSettings,
    ): DuplicateType? {
        val hashesAvailable = !image1.hash.isNullOrEmpty() && !image2.hash.isNullOrEmpty()
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
            val descA = surfService.extractSurfDescriptors(image1.file)
            val descB = surfService.extractSurfDescriptors(image2.file)
            if (surfService.countSurfMatches(descA, descB) >= settings.surfMatchThreshold)
                return DuplicateType.SURF_MATCH
        }
        return null
    }
}
