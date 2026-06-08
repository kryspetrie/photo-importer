package org.kryspetrie.fileimport.infrastructure.wizard

import java.awt.image.BufferedImage
import java.io.File
import org.kryspetrie.fileimport.domain.model.geometry.BoundingBox

/**
 * Result of pre-processing a single image (load + detect).
 *
 * Stored in [PhotoScanWizardState.preProcessedCache] for batch processing navigation.
 */
data class PreProcessedImage(val file: File, val image: BufferedImage, val boxes: List<BoundingBox>)
