package org.kryspetrie.fileimport.infrastructure.wizard

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Unit tests for PhotoConfiguration.
 *
 * Tests configuration creation, defaults, and serialization.
 */
class PhotoConfigurationTest {

  // ==================== Default Values ====================

  @Test
  fun `default configuration has correct defaults`() {
    val config = PhotoConfiguration()

    assertFalse(config.perspectiveCorrectionEnabled)
    assertFalse(config.rotationCorrectionEnabled)
    assertEquals(0, config.rotationDegrees)
    assertEquals(0.0, config.aspectRatio)
  }

  @Test
  fun `custom configuration overrides defaults`() {
    val config = PhotoConfiguration(
        perspectiveCorrectionEnabled = true,
        rotationCorrectionEnabled = true,
        rotationDegrees = 90,
        aspectRatio = 1.5
    )

    assertTrue(config.perspectiveCorrectionEnabled)
    assertTrue(config.rotationCorrectionEnabled)
    assertEquals(90, config.rotationDegrees)
    assertEquals(1.5, config.aspectRatio)
  }

  // ==================== Copy Tests ====================

  @Test
  fun `copy creates new instance with same values`() {
    val original = PhotoConfiguration(
        perspectiveCorrectionEnabled = true,
        rotationCorrectionEnabled = true,
        rotationDegrees = -90,
        aspectRatio = 3.0 / 4.0
    )

    val copy = original.copy()

    assertEquals(original.perspectiveCorrectionEnabled, copy.perspectiveCorrectionEnabled)
    assertEquals(original.rotationCorrectionEnabled, copy.rotationCorrectionEnabled)
    assertEquals(original.rotationDegrees, copy.rotationDegrees)
    assertEquals(original.aspectRatio, copy.aspectRatio)
  }

  @Test
  fun `copy with modification changes only specified field`() {
    val original = PhotoConfiguration(
        perspectiveCorrectionEnabled = false,
        rotationCorrectionEnabled = false
    )

    val modified = original.copy(perspectiveCorrectionEnabled = true)

    assertTrue(modified.perspectiveCorrectionEnabled)
    assertFalse(modified.rotationCorrectionEnabled) // Unchanged
  }

  // ==================== Mutex Behavior ====================

  // Note: Mutex is enforced in UI, not in data class
  // These tests document expected behavior when UI applies mutex

  @Test
  fun `perspective and rotation can both be false`() {
    val config = PhotoConfiguration(
        perspectiveCorrectionEnabled = false,
        rotationCorrectionEnabled = false
    )

    assertFalse(config.perspectiveCorrectionEnabled)
    assertFalse(config.rotationCorrectionEnabled)
  }

  @Test
  fun `perspective can be true when rotation is false`() {
    val config = PhotoConfiguration(
        perspectiveCorrectionEnabled = true,
        rotationCorrectionEnabled = false
    )

    assertTrue(config.perspectiveCorrectionEnabled)
    assertFalse(config.rotationCorrectionEnabled)
  }

  @Test
  fun `rotation can be true when perspective is false`() {
    val config = PhotoConfiguration(
        perspectiveCorrectionEnabled = false,
        rotationCorrectionEnabled = true
    )

    assertFalse(config.perspectiveCorrectionEnabled)
    assertTrue(config.rotationCorrectionEnabled)
  }

  // ==================== Rotation Degrees ====================

  @Test
  fun `rotation degrees can be 0`() {
    val config = PhotoConfiguration(rotationDegrees = 0)
    assertEquals(0, config.rotationDegrees)
  }

  @Test
  fun `rotation degrees can be 90`() {
    val config = PhotoConfiguration(rotationDegrees = 90)
    assertEquals(90, config.rotationDegrees)
  }

  @Test
  fun `rotation degrees can be -90`() {
    val config = PhotoConfiguration(rotationDegrees = -90)
    assertEquals(-90, config.rotationDegrees)
  }

  @Test
  fun `rotation degrees can be 180`() {
    val config = PhotoConfiguration(rotationDegrees = 180)
    assertEquals(180, config.rotationDegrees)
  }

  // ==================== Aspect Ratio ====================

  @Test
  fun `aspect ratio of 0 means original`() {
    val config = PhotoConfiguration(aspectRatio = 0.0)
    assertEquals(0.0, config.aspectRatio)
  }

  @Test
  fun `aspect ratio can be 1_0 for square`() {
    val config = PhotoConfiguration(aspectRatio = 1.0)
    assertEquals(1.0, config.aspectRatio)
  }

  @Test
  fun `aspect ratio can be 3_2 for landscape photos`() {
    val config = PhotoConfiguration(aspectRatio = 3.0 / 2.0)
    assertEquals(3.0 / 2.0, config.aspectRatio)
  }

  @Test
  fun `aspect ratio can be 4_3 for standard photos`() {
    val config = PhotoConfiguration(aspectRatio = 4.0 / 3.0)
    assertEquals(4.0 / 3.0, config.aspectRatio)
  }
}
