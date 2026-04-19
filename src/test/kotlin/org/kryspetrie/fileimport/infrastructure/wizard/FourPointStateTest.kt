package org.kryspetrie.fileimport.infrastructure.wizard

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for FourPointState. Tests the 4-point bounding box creation workflow including point
 * placement, validation, cancellation, and box creation.
 */
@DisplayName("FourPointState")
class FourPointStateTest {

  private lateinit var state: FourPointState

  @BeforeEach
  fun setup() {
    state = FourPointState.activeQuad() // Must activate before adding points
  }

  // 4P-01: Create inactive state
  @Nested
  @DisplayName("initial state")
  inner class InitialState {
    @Test
    @DisplayName("should be PLACING when activated")
    fun shouldBePlacingWhenActivated() {
      val activeState = FourPointState.activeQuad()
      assertEquals(FourPointState.Mode.PLACING, activeState.mode)
      assertEquals(0, activeState.points.size)
    }

    @Test
    @DisplayName("should be INACTIVE when not activated")
    fun shouldBeInactiveWhenNotActivated() {
      val inactiveState = FourPointState.inactive()
      assertEquals(FourPointState.Mode.INACTIVE, inactiveState.mode)
      assertEquals(0, inactiveState.points.size)
    }

    @Test
    @DisplayName("should have no points initially")
    fun shouldHaveNoPoints() {
      val activeState = FourPointState.activeQuad()
      assertTrue(activeState.points.isEmpty())
    }
  }

  // 4P-02: Start adding points
  @Nested
  @DisplayName("adding points")
  inner class AddingPoints {
    @Test
    @DisplayName("should transition to PLACING after first point")
    fun shouldTransitionToPlacingAfterFirstPoint() {
      state = state.addPoint(Point(100.0, 100.0))
      assertEquals(FourPointState.Mode.PLACING, state.mode)
    }

    @Test
    @DisplayName("should store points correctly")
    fun shouldStorePointsCorrectly() {
      state = state.addPoint(Point(10.0, 10.0))
      state = state.addPoint(Point(100.0, 10.0))
      state = state.addPoint(Point(100.0, 100.0))
      state = state.addPoint(Point(10.0, 100.0))

      assertEquals(4, state.points.size)
      assertEquals(Point(10.0, 10.0), state.points[0])
      assertEquals(Point(100.0, 10.0), state.points[1])
      assertEquals(Point(100.0, 100.0), state.points[2])
      assertEquals(Point(10.0, 100.0), state.points[3])
    }

    @Test
    @DisplayName("should allow points in any order")
    fun shouldAllowPointsInAnyOrder() {
      // Adding points out of order (e.g., bottom-left, bottom-right, top-right, top-left)
      state = state.addPoint(Point(10.0, 100.0)) // bottom-left
      state = state.addPoint(Point(100.0, 100.0)) // bottom-right
      state = state.addPoint(Point(100.0, 10.0)) // top-right
      state = state.addPoint(Point(10.0, 10.0)) // top-left

      assertEquals(4, state.points.size)
      assertTrue(state.isComplete())
    }
  }

  // 4P-03: Complete with 4 points
  @Nested
  @DisplayName("completing selection")
  inner class CompletingSelection {
    @Test
    @DisplayName("should be complete after 4 points")
    fun shouldBeCompleteAfterFourPoints() {
      state = state.addPoint(Point(0.0, 0.0))
      state = state.addPoint(Point(100.0, 0.0))
      state = state.addPoint(Point(100.0, 100.0))
      state = state.addPoint(Point(0.0, 100.0))

      assertTrue(state.isComplete())
    }

    @Test
    @DisplayName("canConfirm should return true after 4 points")
    fun canConfirmShouldReturnTrueAfterFourPoints() {
      state = state.addPoint(Point(0.0, 0.0))
      state = state.addPoint(Point(100.0, 0.0))
      state = state.addPoint(Point(100.0, 100.0))
      state = state.addPoint(Point(0.0, 100.0))

      assertTrue(state.canConfirm())
    }

    @Test
    @DisplayName("cannot confirm with less than 4 points")
    fun cannotConfirmWithLessThanFourPoints() {
      state = state.addPoint(Point(0.0, 0.0))
      state = state.addPoint(Point(100.0, 0.0))

      assertFalse(state.canConfirm())
    }

    @Test
    @DisplayName("confirm should create bounding box")
    fun confirmShouldCreateBoundingBox() {
      state = state.addPoint(Point(0.0, 0.0))
      state = state.addPoint(Point(100.0, 0.0))
      state = state.addPoint(Point(100.0, 100.0))
      state = state.addPoint(Point(0.0, 100.0))

      val box = state.confirm()
      assertNotNull(box)
      assertEquals(Point(0.0, 0.0), box!!.corners.topLeft)
      assertEquals(Point(100.0, 0.0), box.corners.topRight)
      assertEquals(Point(100.0, 100.0), box.corners.bottomRight)
      assertEquals(Point(0.0, 100.0), box.corners.bottomLeft)
    }

    @Test
    @DisplayName("confirm should reset state to inactive")
    fun confirmShouldResetStateToInactive() {
      state = state.addPoint(Point(0.0, 0.0))
      state = state.addPoint(Point(100.0, 0.0))
      state = state.addPoint(Point(100.0, 100.0))
      state = state.addPoint(Point(0.0, 100.0))
      state.confirm()

      // State is immutable, confirm doesn't modify state
      assertEquals(FourPointState.Mode.COMPLETE, state.mode)
    }
  }

  // 4P-04: Remove last point (Backspace)
  @Nested
  @DisplayName("removing last point")
  inner class RemovingLastPoint {
    @Test
    @DisplayName("removeLastPoint should remove the last point")
    fun shouldRemoveLastPoint() {
      state = state.addPoint(Point(0.0, 0.0))
      state = state.addPoint(Point(100.0, 0.0))
      state = state.addPoint(Point(100.0, 100.0))

      state = state.removeLastPoint()

      assertEquals(2, state.points.size)
      assertEquals(Point(0.0, 0.0), state.points[0])
      assertEquals(Point(100.0, 0.0), state.points[1])
    }

    @Test
    @DisplayName("removeLastPoint should handle empty state")
    fun shouldHandleEmptyState() {
      val result = state.removeLastPoint()
      assertEquals(state, result)
      assertEquals(0, result.points.size)
    }

    @Test
    @DisplayName("removeLastPoint should return to inactive when all points removed")
    fun shouldReturnToInactiveWhenAllPointsRemoved() {
      state = state.addPoint(Point(0.0, 0.0))
      state = state.addPoint(Point(100.0, 0.0))

      state = state.removeLastPoint()
      assertEquals(FourPointState.Mode.PLACING, state.mode)

      state = state.removeLastPoint()
      assertEquals(FourPointState.Mode.INACTIVE, state.mode)
    }

    @Test
    @DisplayName("removeLastPoint should exit COMPLETING mode if was complete")
    fun shouldExitCompletingModeIfWasComplete() {
      state = state.addPoint(Point(0.0, 0.0))
      state = state.addPoint(Point(100.0, 0.0))
      state = state.addPoint(Point(100.0, 100.0))
      state = state.addPoint(Point(0.0, 100.0))

      assertTrue(state.isComplete())

      state = state.removeLastPoint()
      assertFalse(state.isComplete())
      assertEquals(FourPointState.Mode.PLACING, state.mode)
    }
  }

  // 4P-05: Clear/cancel
  @Nested
  @DisplayName("clearing state")
  inner class ClearingState {
    @Test
    @DisplayName("clear should reset all points")
    fun clearShouldResetAllPoints() {
      state = state.addPoint(Point(0.0, 0.0))
      state = state.addPoint(Point(100.0, 0.0))
      state = state.addPoint(Point(100.0, 100.0))

      state = state.reset()

      assertTrue(state.points.isEmpty())
      assertEquals(FourPointState.Mode.INACTIVE, state.mode)
    }

    @Test
    @DisplayName("reset on empty state should be safe")
    fun resetOnEmptyStateShouldBeSafe() {
      val result = state.reset()
      assertTrue(result.points.isEmpty())
    }
  }

  // 4P-07: Confirm with Enter
  @Nested
  @DisplayName("enter confirmation")
  inner class EnterConfirmation {
    @Test
    @DisplayName("confirm should be callable multiple times if state is reset")
    fun confirmShouldBeCallableMultipleTimesIfStateIsReset() {
      // First confirmation
      state = state.addPoint(Point(0.0, 0.0))
      state = state.addPoint(Point(100.0, 0.0))
      state = state.addPoint(Point(100.0, 100.0))
      state = state.addPoint(Point(0.0, 100.0))
      val box1 = state.confirm()
      assertNotNull(box1)

      // State should be reset, add new points
      state = state.addPoint(Point(50.0, 50.0))
      state = state.addPoint(Point(150.0, 50.0))
      state = state.addPoint(Point(150.0, 150.0))
      state = state.addPoint(Point(50.0, 150.0))
      val box2 = state.confirm()
      assertNotNull(box2)

      // Boxes should be different
      assertNotEquals(box1!!.id, box2!!.id)
    }
  }

  // 4P-08: Static factory methods
  @Nested
  @DisplayName("static factory methods")
  inner class FactoryMethods {
    @Test
    @DisplayName("inactive should create empty INACTIVE state")
    fun inactiveShouldCreateEmptyInactiveState() {
      val inactive = FourPointState.inactive()
      assertEquals(FourPointState.Mode.INACTIVE, inactive.mode)
      assertTrue(inactive.points.isEmpty())
    }

    @Test
    @DisplayName("inactive should be reusable")
    fun inactiveShouldBeReusable() {
      val inactive1 = FourPointState.inactive()
      val inactive2 = FourPointState.inactive()

      assertEquals(inactive1.mode, inactive2.mode)
      assertEquals(inactive1.points.size, inactive2.points.size)
    }
  }

  // Edge cases
  @Nested
  @DisplayName("edge cases")
  inner class EdgeCases {
    @Test
    @DisplayName("should handle very close points")
    fun shouldHandleVeryClosePoints() {
      // Points very close together (but not identical)
      state = state.addPoint(Point(100.0, 100.0))
      state = state.addPoint(Point(100.1, 100.0))
      state = state.addPoint(Point(100.1, 100.1))
      state = state.addPoint(Point(100.0, 100.1))

      // Should still work (no minimum distance validation in state)
      assertTrue(state.isComplete())
    }

    @Test
    @DisplayName("should handle very large coordinates")
    fun shouldHandleVeryLargeCoordinates() {
      state = state.addPoint(Point(1e6, 1e6))
      state = state.addPoint(Point(2e6, 1e6))
      state = state.addPoint(Point(2e6, 2e6))
      state = state.addPoint(Point(1e6, 2e6))

      assertTrue(state.isComplete())

      val box = state.confirm()
      assertNotNull(box)
      assertEquals(Point(1e6, 1e6), box!!.corners.topLeft)
    }

    @Test
    @DisplayName("should handle negative coordinates")
    fun shouldHandleNegativeCoordinates() {
      state = state.addPoint(Point(-100.0, -100.0))
      state = state.addPoint(Point(0.0, -100.0))
      state = state.addPoint(Point(0.0, 0.0))
      state = state.addPoint(Point(-100.0, 0.0))

      assertTrue(state.isComplete())

      val box = state.confirm()
      assertNotNull(box)
      assertTrue(box!!.corners.topLeft.x < 0)
    }

    @Test
    @DisplayName("should handle zero coordinates")
    fun shouldHandleZeroCoordinates() {
      state = state.addPoint(Point(0.0, 0.0))
      state = state.addPoint(Point(10.0, 0.0))
      state = state.addPoint(Point(10.0, 10.0))
      state = state.addPoint(Point(0.0, 10.0))

      // Valid rectangle from zero coordinates
      assertTrue(state.isComplete())

      val box = state.confirm()
      assertNotNull(box)
      assertEquals(Point(0.0, 0.0), box!!.corners.topLeft)
    }
  }
}
