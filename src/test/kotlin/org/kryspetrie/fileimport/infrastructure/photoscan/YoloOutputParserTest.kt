package org.kryspetrie.fileimport.infrastructure.photoscan

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.model.PhotoCorner

class YoloOutputParserTest {

  @Test
  fun `test parse correctly parses model output`() {
    val parser = YoloOutputParser()

    // Simulate a 3D Mat output [1, 17, 8400]
    val output = Array(1) { Array(17) { FloatArray(8400) } }
    output[0][4][0] = 0.9f // Confidence
    output[0][5][0] = 100.0f // Keypoint 1 x
    output[0][6][0] = 200.0f // Keypoint 1 y
    output[0][8][0] = 300.0f // Keypoint 2 x
    output[0][9][0] = 400.0f // Keypoint 2 y
    output[0][11][0] = 500.0f // Keypoint 3 x
    output[0][12][0] = 600.0f // Keypoint 3 y
    output[0][14][0] = 700.0f // Keypoint 4 x
    output[0][15][0] = 800.0f // Keypoint 4 y

    val result = parser.parse(output, 1280, 1280)

    assertEquals(1, result.size)
    val corners = result[0]

    assertEquals(4, corners.size)
    assertEquals(PhotoCorner(200.0f, 400.0f), corners[0])
    assertEquals(PhotoCorner(600.0f, 800.0f), corners[1])
    assertEquals(PhotoCorner(1000.0f, 1200.0f), corners[2])
    assertEquals(PhotoCorner(1400.0f, 1600.0f), corners[3])
  }
}
