package org.kryspetrie.fileimport.domain.port

import org.kryspetrie.fileimport.domain.model.*

interface DeduplicationPort {
  suspend fun findDuplicates(
      images: List<ImageFile>,
      settings: DeduplicationSettings
  ): List<DuplicateInfo>

  suspend fun areDuplicates(
      image1: ImageFile,
      image2: ImageFile,
      settings: DeduplicationSettings
  ): Boolean

  suspend fun calculateSimilarity(image1: ImageFile, image2: ImageFile): Float

  suspend fun groupSimilarImages(
      images: List<ImageFile>,
      threshold: Float
  ): Map<String, List<ImageFile>>

  suspend fun findCameraPairs(images: List<ImageFile>): List<Pair<ImageFile, ImageFile>>

  suspend fun getDuplicateType(
      image1: ImageFile,
      image2: ImageFile,
      settings: DeduplicationSettings
  ): DuplicateType?
}
