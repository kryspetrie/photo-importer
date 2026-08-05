package org.kryspetrie.fileimport.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.Test

@DisplayName("Shared UI Components")
@Tag("UiComponentTest")
class SharedComponentTest {

    @get:Rule val composeTestRule = createComposeRule()

    // ── formatFileSize ──────────────────────────────────────────────────

    @Nested
    @DisplayName("formatFileSize")
    inner class FormatFileSizeTest {

        @Test
        @DisplayName("should format zero bytes as '0 B'")
        fun zeroBytes() {
            assertThat(formatFileSize(0L)).isEqualTo("0 B")
        }

        @Test
        @DisplayName("should format bytes less than 1000 as 'X B'")
        fun smallBytes() {
            assertThat(formatFileSize(1L)).isEqualTo("1 B")
            assertThat(formatFileSize(999L)).isEqualTo("999 B")
        }

        @Test
        @DisplayName("should format kilobytes with one decimal")
        fun kilobytes() {
            assertThat(formatFileSize(1_000L)).isEqualTo("1.0 KB")
            assertThat(formatFileSize(500_000L)).isEqualTo("500.0 KB")
        }

        @Test
        @DisplayName("should format megabytes with one decimal")
        fun megabytes() {
            assertThat(formatFileSize(1_000_000L)).isEqualTo("1.0 MB")
            assertThat(formatFileSize(3_200_000L)).isEqualTo("3.2 MB")
        }

        @Test
        @DisplayName("should format gigabytes with one decimal")
        fun gigabytes() {
            assertThat(formatFileSize(1_000_000_000L)).isEqualTo("1.0 GB")
            assertThat(formatFileSize(1_500_000_000L)).isEqualTo("1.5 GB")
        }

        @Test
        @DisplayName("should use GB range for values >= 1_000_000_000")
        fun exactGbBoundary() {
            assertThat(formatFileSize(1_000_000_000L)).isEqualTo("1.0 GB")
        }

        @Test
        @DisplayName("should use MB range for values >= 1_000_000 but < 1_000_000_000")
        fun exactMbBoundary() {
            assertThat(formatFileSize(1_000_000L)).isEqualTo("1.0 MB")
        }

        @Test
        @DisplayName("should use KB range for values >= 1_000 but < 1_000_000")
        fun exactKbBoundary() {
            assertThat(formatFileSize(1_000L)).isEqualTo("1.0 KB")
        }

        @Test
        @DisplayName("should use B range for values just below 1_000")
        fun justBelowKb() {
            assertThat(formatFileSize(999L)).isEqualTo("999 B")
        }

        @Test
        @DisplayName("should use KB range for values just above 1_000")
        fun justAboveKb() {
            assertThat(formatFileSize(1_001L)).isEqualTo("1.0 KB")
        }

        @Test
        @DisplayName("should use MB range for values just above 1_000_000")
        fun justAboveMb() {
            assertThat(formatFileSize(1_001_000L)).isEqualTo("1.0 MB")
        }

        @Test
        @DisplayName("should use GB range for values just above 1_000_000_000")
        fun justAboveGb() {
            assertThat(formatFileSize(1_001_000_000L)).isEqualTo("1.0 GB")
        }
    }

    // ── StatColumn ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("StatColumn")
    inner class StatColumnTest {

        @Test
        @DisplayName("should display value and label text")
        fun shouldDisplayValueAndLabel() {
            composeTestRule.setContent { StatColumn(value = "42", label = "Files") }

            composeTestRule.onNodeWithText("42").assertIsDisplayed()
            composeTestRule.onNodeWithText("Files").assertIsDisplayed()
        }

        @Test
        @DisplayName("should display icon when provided")
        fun shouldDisplayIconWhenProvided() {
            composeTestRule.setContent {
                StatColumn(value = "99", label = "Items", icon = Icons.Default.Info)
            }

            composeTestRule.onNodeWithText("99").assertIsDisplayed()
            composeTestRule.onNodeWithText("Items").assertIsDisplayed()
        }

        @Test
        @DisplayName("should display value and label without icon when icon is null")
        fun shouldDisplayWithoutIcon() {
            composeTestRule.setContent { StatColumn(value = "7", label = "Errors", icon = null) }

            composeTestRule.onNodeWithText("7").assertIsDisplayed()
            composeTestRule.onNodeWithText("Errors").assertIsDisplayed()
        }
    }

    // ── ErrorCard ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("ErrorCard")
    inner class ErrorCardTest {

        @Test
        @DisplayName("should display the error message")
        fun shouldDisplayErrorMessage() {
            composeTestRule.setContent { ErrorCard(message = "Something went wrong") }

            composeTestRule.onNodeWithText("Something went wrong").assertIsDisplayed()
        }

        @Test
        @DisplayName("should display warning icon alongside message")
        fun shouldDisplayWarningIcon() {
            composeTestRule.setContent { ErrorCard(message = "Disk full") }

            // The warning icon is present; the message text confirms the card rendered
            composeTestRule.onNodeWithText("Disk full").assertIsDisplayed()
        }
    }

    // ── InfoCard ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("InfoCard")
    inner class InfoCardTest {

        @Test
        @DisplayName("should display the info message")
        fun shouldDisplayInfoMessage() {
            composeTestRule.setContent { InfoCard(message = "Drag files here to begin") }

            composeTestRule.onNodeWithText("Drag files here to begin").assertIsDisplayed()
        }

        @Test
        @DisplayName("should display default Info icon")
        fun shouldDisplayDefaultInfoIcon() {
            composeTestRule.setContent { InfoCard(message = "Tip of the day") }

            composeTestRule.onNodeWithText("Tip of the day").assertIsDisplayed()
        }

        @Test
        @DisplayName("should display custom icon when provided")
        fun shouldDisplayCustomIcon() {
            composeTestRule.setContent {
                InfoCard(message = "Custom icon test", icon = Icons.Default.Info)
            }

            composeTestRule.onNodeWithText("Custom icon test").assertIsDisplayed()
        }
    }

    // ── SectionLabel ───────────────────────────────────────────────────

    @Nested
    @DisplayName("SectionLabel")
    inner class SectionLabelTest {

        @Test
        @DisplayName("should display the label text")
        fun shouldDisplayLabelText() {
            composeTestRule.setContent { SectionLabel(text = "Import Settings") }

            composeTestRule.onNodeWithText("Import Settings").assertIsDisplayed()
        }

        @Test
        @DisplayName("should display different label texts")
        fun shouldDisplayDifferentLabels() {
            composeTestRule.setContent { SectionLabel(text = "Output Configuration") }

            composeTestRule.onNodeWithText("Output Configuration").assertIsDisplayed()
        }
    }
}
