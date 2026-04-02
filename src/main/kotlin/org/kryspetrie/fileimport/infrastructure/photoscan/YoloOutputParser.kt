package org.kryspetrie.fileimport.infrastructure.photoscan

import kotlin.math.max
import kotlin.math.min
import org.kryspetrie.fileimport.domain.model.PhotoCorner
import org.kryspetrie.fileimport.domain.model.Rectangle

class YoloOutputParser {
  private val confidenceThreshold = 0.5f
  private val nmsThreshold = 0.45f

  fun parse(
      output: Array<Array<FloatArray>>,
      originalWidth: Int,
      originalHeight: Int
  ): List<List<PhotoCorner>> {
    val proposals = mutableListOf<DetectionProposal>()
    val outputData = output[0]

    val scaleX = originalWidth / 640.0
    val scaleY = originalHeight / 640.0

    for (i in 0 until outputData[0].size) {
      val confidence = outputData[4][i]
      if (confidence > confidenceThreshold) {
        // Bounding box
        val cx = outputData[0][i]
        val cy = outputData[1][i]
        val w = outputData[2][i]
        val h = outputData[3][i]
        val x1 = ((cx - w / 2) * scaleX).toFloat()
        val y1 = ((cy - h / 2) * scaleY).toFloat()
        val x2 = ((cx + w / 2) * scaleX).toFloat()
        val y2 = ((cy + h / 2) * scaleY).toFloat()
        val bbox = Rectangle(x1, y1, x2, y2)

        // Keypoints
        val corners = mutableListOf<PhotoCorner>()
        for (j in 0..3) {
          val baseIndex = 5 + (j * 3)
          val kptX = (outputData[baseIndex][i] * scaleX).toFloat()
          val kptY = (outputData[baseIndex + 1][i] * scaleY).toFloat()
          corners.add(PhotoCorner(kptX, kptY))
        }

        proposals.add(DetectionProposal(bbox, confidence, corners))
      }
    }

    return nonMaxSuppression(proposals).map { it.keypoints }
  }

  private fun nonMaxSuppression(proposals: List<DetectionProposal>): List<DetectionProposal> {
    if (proposals.isEmpty()) {
      return emptyList()
    }
    val sortedProposals = proposals.sortedByDescending { it.confidence }
    val finalDetections = mutableListOf<DetectionProposal>()

    val proposalStatus = BooleanArray(sortedProposals.size) { true }

    for (i in sortedProposals.indices) {
      if (proposalStatus[i]) {
        finalDetections.add(sortedProposals[i])
        for (j in i + 1 until sortedProposals.size) {
          if (proposalStatus[j]) {
            if (iou(sortedProposals[i].bbox, sortedProposals[j].bbox) > nmsThreshold) {
              proposalStatus[j] = false
            }
          }
        }
      }
    }

    return finalDetections
  }

  private fun iou(box1: Rectangle, box2: Rectangle): Float {
    val xA = max(box1.x1, box2.x1)
    val yA = max(box1.y1, box2.y1)
    val xB = min(box1.x2, box2.x2)
    val yB = min(box1.y2, box2.y2)

    val interArea = max(0f, xB - xA) * max(0f, yB - yA)
    val box1Area = (box1.x2 - box1.x1) * (box1.y2 - box1.y1)
    val box2Area = (box2.x2 - box2.x1) * (box2.y2 - box2.y1)

    return interArea / (box1Area + box2Area - interArea)
  }

  private data class DetectionProposal(
      val bbox: Rectangle,
      val confidence: Float,
      val keypoints: List<PhotoCorner>
  )
}
