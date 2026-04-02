package org.kryspetrie.fileimport.infrastructure.photoscan

import ai.onnxruntime.OrtEnvironment
import java.io.File
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Inspects available ONNX models to determine their I/O shapes and suitability for YOLO keypoint
 * detection.
 */
class ModelInspectorTest {

  data class ModelInfo(
      val path: String,
      val label: String,
      val sizeBytes: Long,
      val inputs: List<String> = emptyList(),
      val inputShapes: List<String> = emptyList(),
      val outputs: List<String> = emptyList(),
      val outputShapes: List<String> = emptyList(),
      val error: String? = null
  )

  @Test
  fun `inspect available YOLO pose models`(@TempDir tempDir: File) {
    val models =
        listOf(
            "ml_models/yolo26n-pose-onnx/yolo26n-pose.onnx" to "yolo26n full",
            "ml_models/yolo26n-pose-onnx/yolo26n-pose_int8.onnx" to "yolo26n int8",
            "ml_models/yolo26n-pose-onnx/yolo26n-pose_uint8.onnx" to "yolo26n uint8",
            "ml_models/yolo26n-pose-onnx/yolo26n-pose_q4.onnx" to "yolo26n q4",
            "ml_models/yolo26n-pose-onnx/yolo26n-pose_q4f16.onnx" to "yolo26n q4f16",
            "ml_models/yolo26n-pose-onnx/yolo26n-pose_bnb4.onnx" to "yolo26n bnb4",
            "ml_models/yolo26n-pose-onnx/yolo26n-pose_fp16.onnx" to "yolo26n fp16",
            "ml_models/yolo26n-pose-onnx/yolo26n-pose_quantized.onnx" to "yolo26n quantized",
            "ml_models/yolo26x-pose-onnx/yolo26x-pose.onnx" to "yolo26x full",
            "ml_models/yolo26x-pose-onnx/yolo26x-pose_int8.onnx" to "yolo26x int8",
            "ml_models/yolo26x-pose-onnx/yolo26x-pose_uint8.onnx" to "yolo26x uint8",
            "ml_models/yolo26x-pose-onnx/yolo26x-pose_q4.onnx" to "yolo26x q4",
            "ml_models/yolo26x-pose-onnx/yolo26x-pose_q4f16.onnx" to "yolo26x q4f16",
            "ml_models/yolo26x-pose-onnx/yolo26x-pose_bnb4.onnx" to "yolo26x bnb4",
            "ml_models/yolo26x-pose-onnx/yolo26x-pose_fp16.onnx" to "yolo26x fp16",
            "ml_models/yolo26x-pose-onnx/yolo26x-pose_quantized.onnx" to "yolo26x quantized",
        )

    val results = mutableListOf<ModelInfo>()

    for ((path, label) in models) {
      val resourceStream = javaClass.classLoader.getResourceAsStream(path)
      if (resourceStream == null) {
        results.add(ModelInfo(path, label, 0, error = "NOT FOUND"))
        continue
      }
      val bytes = resourceStream.readAllBytes()
      resourceStream.close()

      val info =
          try {
            val env = OrtEnvironment.getEnvironment()
            val session = env.createSession(bytes)
            ModelInfo(
                path = path,
                label = label,
                sizeBytes = bytes.size.toLong(),
                inputs = session.inputNames.toList(),
                inputShapes =
                    session.inputNames.map { session.inputInfo[it]?.info?.toString() ?: "?" },
                outputs = session.outputNames.toList(),
                outputShapes =
                    session.outputNames.map { session.outputInfo[it]?.info?.toString() ?: "?" },
            )
          } catch (e: Exception) {
            ModelInfo(path, label, bytes.size.toLong(), error = e.message)
          }
      results.add(info)
    }

    // Print report
    println("\n" + "=".repeat(80))
    println("MODEL INSPECTION REPORT")
    println("=".repeat(80))
    for (r in results) {
      println("\n${r.label} (${r.sizeBytes / 1024}KB)")
      println("  path: ${r.path}")
      if (r.error != null) {
        println("  ERROR: ${r.error}")
      } else {
        for (i in r.inputs.indices) {
          val shape = if (i < r.inputShapes.size) r.inputShapes[i] else "?"
          println("  input[$i]: ${r.inputs[i]} -> $shape")
        }
        for (i in r.outputs.indices) {
          val shape = if (i < r.outputShapes.size) r.outputShapes[i] else "?"
          println("  output[$i]: ${r.outputs[i]} -> $shape")
        }
      }
    }
    println("\n" + "=".repeat(80))
  }
}
