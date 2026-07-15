package org.kryspetrie.fileimport.domain.port

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.kryspetrie.fileimport.domain.model.RotationAngle

/**
 * Tests for the [toNearestRotationAngle] extension function.
 *
 * Verifies that continuous orientation angles (0°–359.9°) are correctly mapped to the nearest
 * discrete [RotationAngle] for the metadata editor's auto-rotation feature.
 */
@DisplayName("toNearestRotationAngle")
class ToNearestRotationAngleTest {

    @Nested
    @DisplayName("Upright images (NONE)")
    inner class UprightTests {
        @Test
        @DisplayName("0° maps to NONE (upright)")
        fun zeroDegrees() {
            assertThat(0f.toNearestRotationAngle()).isEqualTo(RotationAngle.NONE)
        }

        @Test
        @DisplayName("44.9° maps to NONE")
        fun near45() {
            assertThat(44.9f.toNearestRotationAngle()).isEqualTo(RotationAngle.NONE)
        }

        @Test
        @DisplayName("315° maps to NONE")
        fun at315() {
            assertThat(315f.toNearestRotationAngle()).isEqualTo(RotationAngle.NONE)
        }

        @Test
        @DisplayName("359° maps to NONE")
        fun near360() {
            assertThat(359f.toNearestRotationAngle()).isEqualTo(RotationAngle.NONE)
        }
    }

    @Nested
    @DisplayName("90° CW rotation needed")
    inner class Cw90Tests {
        @Test
        @DisplayName("90° maps to CW_90")
        fun at90() {
            assertThat(90f.toNearestRotationAngle()).isEqualTo(RotationAngle.CW_90)
        }

        @Test
        @DisplayName("45° maps to CW_90")
        fun at45() {
            assertThat(45f.toNearestRotationAngle()).isEqualTo(RotationAngle.CW_90)
        }

        @Test
        @DisplayName("134° maps to CW_90")
        fun near135() {
            assertThat(134f.toNearestRotationAngle()).isEqualTo(RotationAngle.CW_90)
        }
    }

    @Nested
    @DisplayName("180° rotation needed")
    inner class Cw180Tests {
        @Test
        @DisplayName("180° maps to CW_180")
        fun at180() {
            assertThat(180f.toNearestRotationAngle()).isEqualTo(RotationAngle.CW_180)
        }

        @Test
        @DisplayName("135° maps to CW_180")
        fun at135() {
            assertThat(135f.toNearestRotationAngle()).isEqualTo(RotationAngle.CW_180)
        }

        @Test
        @DisplayName("224° maps to CW_180")
        fun near225() {
            assertThat(224f.toNearestRotationAngle()).isEqualTo(RotationAngle.CW_180)
        }
    }

    @Nested
    @DisplayName("90° CCW rotation needed")
    inner class Ccw90Tests {
        @Test
        @DisplayName("270° maps to CCW_90")
        fun at270() {
            assertThat(270f.toNearestRotationAngle()).isEqualTo(RotationAngle.CCW_90)
        }

        @Test
        @DisplayName("225° maps to CCW_90")
        fun at225() {
            assertThat(225f.toNearestRotationAngle()).isEqualTo(RotationAngle.CCW_90)
        }

        @Test
        @DisplayName("314° maps to CCW_90")
        fun near315() {
            assertThat(314f.toNearestRotationAngle()).isEqualTo(RotationAngle.CCW_90)
        }
    }

    @Nested
    @DisplayName("Boundary and edge cases")
    inner class EdgeCaseTests {
        @ParameterizedTest(name = "{0}° maps to {1}")
        @CsvSource(
            "0, NONE",
            "45, CW_90",
            "90, CW_90",
            "135, CW_180",
            "180, CW_180",
            "225, CCW_90",
            "270, CCW_90",
            "315, NONE",
            "360, NONE",
        )
        fun parameterizedAngles(angle: Float, expected: RotationAngle) {
            assertThat(angle.toNearestRotationAngle()).isEqualTo(expected)
        }

        @Test
        @DisplayName("Negative angles are normalized")
        fun negativeAngle() {
            assertThat((-90f).toNearestRotationAngle()).isEqualTo(RotationAngle.CCW_90)
        }

        @Test
        @DisplayName("Angles above 360° are normalized")
        fun above360() {
            assertThat(450f.toNearestRotationAngle()).isEqualTo(RotationAngle.CW_90)
        }
    }
}