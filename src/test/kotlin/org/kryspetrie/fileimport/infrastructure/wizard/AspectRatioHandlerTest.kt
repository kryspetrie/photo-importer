package org.kryspetrie.fileimport.infrastructure.wizard

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Unit tests for AspectRatioHandler. Tests AR-01 through AR-06 from the implementation plan. */
class AspectRatioHandlerTest {

  // AR-01: Landscape photo + landscape ratio (as-is)
  @Test
  fun landscapePhotoLandscapeRatioAsIs() {
    // Box 200x100 (landscape)
    val output =
        AspectRatioHandler.getOutputAspectRatio(
            detectedWidth = 200.0, detectedHeight = 100.0, selectedRatio = 1.5 // 3:2 (landscape)
            )

    assertEquals(1.5, output, 0.01)
  }

  // AR-02: Landscape photo + portrait ratio (flip)
  @Test
  fun landscapePhotoPortraitRatioFlips() {
    // Box 200x100 (landscape)
    val output =
        AspectRatioHandler.getOutputAspectRatio(
            detectedWidth = 200.0, detectedHeight = 100.0, selectedRatio = 0.75 // 3:4 (portrait)
            )

    // Should flip to 4:3 = 1.333
    assertEquals(1.333, output, 0.01)
  }

  // AR-03: Portrait photo + portrait ratio (as-is)
  @Test
  fun portraitPhotoPortraitRatioAsIs() {
    // Box 100x200 (portrait)
    val output =
        AspectRatioHandler.getOutputAspectRatio(
            detectedWidth = 100.0, detectedHeight = 200.0, selectedRatio = 0.75 // 3:4 (portrait)
            )

    assertEquals(0.75, output, 0.01)
  }

  // AR-04: Portrait photo + landscape ratio (flip)
  @Test
  fun portraitPhotoLandscapeRatioFlips() {
    // Box 100x200 (portrait)
    val output =
        AspectRatioHandler.getOutputAspectRatio(
            detectedWidth = 100.0, detectedHeight = 200.0, selectedRatio = 1.5 // 3:2 (landscape)
            )

    // Should flip to 2:3 = 0.667
    assertEquals(0.667, output, 0.01)
  }

  // AR-05: Square box + any ratio (1:1)
  @Test
  fun squareBoxAlwaysReturnsOneToOne() {
    // Box 150x150 (square)
    val output =
        AspectRatioHandler.getOutputAspectRatio(
            detectedWidth = 150.0, detectedHeight = 150.0, selectedRatio = 1.5 // 3:2
            )

    assertEquals(1.0, output, 0.01)
  }

  // AR-05b: Square box with 0.75 ratio
  @Test
  fun squareBoxWithPortraitRatioStillOneToOne() {
    val output =
        AspectRatioHandler.getOutputAspectRatio(
            detectedWidth = 150.0, detectedHeight = 150.0, selectedRatio = 0.75 // 3:4
            )

    assertEquals(1.0, output, 0.01)
  }

  // AR-06: Auto-select closest
  @Test
  fun autoSelectClosestRatio() {
    // Detected ratio 1.5 (3:2)
    val selected = AspectRatioHandler.autoSelectClosest(1.5)
    assertEquals(AspectRatioHandler.AspectRatio.RATIO_3_2, selected)
  }

  // AR-06b: Auto-select closest for other ratios
  @Test
  fun autoSelectClosestFor67Ratio() {
    // Detected ratio about 0.67 (2:3)
    val selected = AspectRatioHandler.autoSelectClosest(0.67)
    assertEquals(AspectRatioHandler.AspectRatio.RATIO_2_3, selected)
  }

  // AR-06c: Auto-select for nearly square
  @Test
  fun autoSelectForNearlySquare() {
    // Detected ratio 1.05 (close to 1:1)
    val selected = AspectRatioHandler.autoSelectClosest(1.05)
    assertEquals(AspectRatioHandler.AspectRatio.SQUARE, selected)
  }

  // Test isPortrait
  @Test
  fun isPortraitReturnsTrueForPortraitBox() {
    assertTrue(AspectRatioHandler.isPortrait(100.0, 200.0))
    assertTrue(AspectRatioHandler.isPortrait(80.0, 100.0))
  }

  // Test isPortrait returns false for landscape
  @Test
  fun isPortraitReturnsFalseForLandscapeBox() {
    assertFalse(AspectRatioHandler.isPortrait(200.0, 100.0))
    assertFalse(AspectRatioHandler.isPortrait(100.0, 80.0))
  }

  // Test isSquare
  @Test
  fun isSquareReturnsTrueForSquareBox() {
    assertTrue(AspectRatioHandler.isSquare(150.0, 150.0))
    assertTrue(AspectRatioHandler.isSquare(100.0, 98.0, 0.05)) // within 5% threshold
  }

  // Test isSquare returns false for non-square
  @Test
  fun isSquareReturnsFalseForNonSquareBox() {
    assertFalse(AspectRatioHandler.isSquare(200.0, 100.0))
    assertFalse(AspectRatioHandler.isSquare(100.0, 80.0))
  }

  // Test getLabelForRatio
  @Test
  fun getLabelForRatioReturnsCurrentForZero() {
    val label = AspectRatioHandler.getLabelForRatio(0.0)
    assertEquals("Current", label)
  }

  // Test getLabelForRatio for 3:2
  @Test
  fun getLabelForRatioReturns3To2For1Point5() {
    val label = AspectRatioHandler.getLabelForRatio(1.5)
    assertEquals("3:2", label)
  }

  // Test getLabelForRatio for 4:3
  @Test
  fun getLabelForRatioReturns4To3For1Point333() {
    val label = AspectRatioHandler.getLabelForRatio(1.333)
    assertEquals("4:3", label)
  }

  // Test getLabelForRatio for unknown ratio
  @Test
  fun getLabelForRatioReturnsFormattedForUnknownRatio() {
    val label = AspectRatioHandler.getLabelForRatio(1.234)
    // Should return formatted string like "1:81"
    assertTrue(label.contains(":"))
  }

  // Test getAvailableRatios
  @Test
  fun getAvailableRatiosReturnsAllRatios() {
    val ratios = AspectRatioHandler.getAvailableRatios()

    assertTrue(ratios.size > 5)
    assertTrue(ratios.any { it.first == 0.0 && it.second == "Current" })
    assertTrue(ratios.any { it.first == 1.0 && it.second == "1:1" })
    assertTrue(ratios.any { it.first == 1.5 && it.second == "3:2" })
  }

  // Test aspect ratio enum values
  @Test
  fun aspectRatioEnumValuesAreCorrect() {
    assertEquals(0.0, AspectRatioHandler.AspectRatio.CURRENT.ratio)
    assertEquals(1.0, AspectRatioHandler.AspectRatio.SQUARE.ratio)
    assertEquals(4.0 / 3.0, AspectRatioHandler.AspectRatio.RATIO_4_3.ratio)
    assertEquals(3.0 / 2.0, AspectRatioHandler.AspectRatio.RATIO_3_2.ratio)
    assertEquals(5.0 / 4.0, AspectRatioHandler.AspectRatio.RATIO_5_4.ratio)
    assertEquals(3.0 / 4.0, AspectRatioHandler.AspectRatio.RATIO_3_4.ratio)
    assertEquals(2.0 / 3.0, AspectRatioHandler.AspectRatio.RATIO_2_3.ratio)
    assertEquals(16.0 / 9.0, AspectRatioHandler.AspectRatio.RATIO_16_9.ratio)
  }

  // Test aspect ratio labels
  @Test
  fun aspectRatioLabelsAreCorrect() {
    assertEquals("Current", AspectRatioHandler.AspectRatio.CURRENT.label)
    assertEquals("1:1", AspectRatioHandler.AspectRatio.SQUARE.label)
    assertEquals("4:3", AspectRatioHandler.AspectRatio.RATIO_4_3.label)
    assertEquals("3:2", AspectRatioHandler.AspectRatio.RATIO_3_2.label)
  }

  // Test isPortrait for enum
  @Test
  fun aspectRatioEnumIsPortraitWorks() {
    assertFalse(AspectRatioHandler.AspectRatio.SQUARE.isPortrait())
    assertFalse(AspectRatioHandler.AspectRatio.RATIO_3_2.isPortrait())
    assertFalse(AspectRatioHandler.AspectRatio.RATIO_16_9.isPortrait())
    assertTrue(AspectRatioHandler.AspectRatio.RATIO_3_4.isPortrait())
    assertTrue(AspectRatioHandler.AspectRatio.RATIO_2_3.isPortrait())
  }

  // Test handling of CURRENT (0.0 ratio)
  @Test
  fun currentRatioReturnsDetectedAspectRatio() {
    val output =
        AspectRatioHandler.getOutputAspectRatio(
            detectedWidth = 200.0, detectedHeight = 100.0, selectedRatio = 0.0 // Current
            )

    assertEquals(2.0, output, 0.01) // 200/100 = 2
  }

  // Test very close to square threshold
  @Test
  fun nearSquareThresholdHandledCorrectly() {
    // 150x151 is within 0.1 threshold of square
    val output =
        AspectRatioHandler.getOutputAspectRatio(
            detectedWidth = 150.0, detectedHeight = 151.0, selectedRatio = 1.5)

    // Should treat as square, return 1:1
    assertEquals(1.0, output, 0.01)
  }
}
