package org.kryspetrie.fileimport.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.Test
import org.kryspetrie.fileimport.domain.model.ImportProgress
import org.kryspetrie.fileimport.domain.model.ImportResult
import org.kryspetrie.fileimport.domain.model.ImportStatus
import org.kryspetrie.fileimport.ui.screens.components.ImportProgressInline
import org.kryspetrie.fileimport.ui.screens.components.ImportResultInline

@Tag("UiComponentTest")
@DisplayName("ImportResultComponent")
class ImportResultComponentTest {

    @Nested
    @DisplayName("ImportResultInline")
    inner class ImportResultInlineTest {

        @get:Rule val composeRule = createComposeRule()

        private val successfulResult =
            ImportResult(
                totalFiles = 10,
                successCount = 8,
                errorCount = 0,
                duplicateCount = 1,
                skippedCount = 1,
                deletedSourceCount = 0,
                startTime = 1000L,
                endTime = 6000L,
            )

        private val errorResult =
            ImportResult(
                totalFiles = 10,
                successCount = 7,
                errorCount = 2,
                duplicateCount = 1,
                skippedCount = 0,
                deletedSourceCount = 0,
                startTime = 1000L,
                endTime = 4000L,
            )

        @Test
        @DisplayName("shows Import Complete! text when result has no errors")
        fun showsImportCompleteText() {
            composeRule.setContent {
                ImportResultInline(
                    result = successfulResult,
                    destinationPath = "/tmp/imports",
                    onReset = {},
                )
            }

            composeRule.onNodeWithText("Import Complete!").assertIsDisplayed()
        }

        @Test
        @DisplayName("shows Completed with Errors text when result has errors")
        fun showsCompletedWithErrorsText() {
            composeRule.setContent {
                ImportResultInline(
                    result = errorResult,
                    destinationPath = "/tmp/imports",
                    onReset = {},
                )
            }

            composeRule.onNodeWithText("Completed with Errors").assertIsDisplayed()
        }

        @Test
        @DisplayName("shows New Import reset button")
        fun showsNewImportButton() {
            composeRule.setContent {
                ImportResultInline(
                    result = successfulResult,
                    destinationPath = "/tmp/imports",
                    onReset = {},
                )
            }

            composeRule.onNodeWithText("New Import").assertIsDisplayed()
        }

        @Test
        @DisplayName("shows stat column labels")
        fun showsStatLabels() {
            composeRule.setContent {
                ImportResultInline(
                    result = errorResult,
                    destinationPath = "/tmp/imports",
                    onReset = {},
                )
            }

            composeRule.onNodeWithText("Copied").assertIsDisplayed()
            composeRule.onNodeWithText("Skipped").assertIsDisplayed()
            composeRule.onNodeWithText("Errors").assertIsDisplayed()
            composeRule.onNodeWithText("Duplicates").assertIsDisplayed()
        }

        @Test
        @DisplayName("shows destination path when not blank")
        fun showsDestinationPath() {
            composeRule.setContent {
                ImportResultInline(
                    result = successfulResult,
                    destinationPath = "/tmp/imports",
                    onReset = {},
                )
            }

            composeRule.onNodeWithText("/tmp/imports").assertIsDisplayed()
        }

        @Test
        @DisplayName("clicking New Import calls onReset callback")
        fun clickingNewImportCallsOnReset() {
            var resetCalled = false

            composeRule.setContent {
                ImportResultInline(
                    result = successfulResult,
                    destinationPath = "/tmp/imports",
                    onReset = { resetCalled = true },
                )
            }

            composeRule.onNodeWithText("New Import").performClick()

            assertThat(resetCalled).isTrue()
        }
    }

    @Nested
    @DisplayName("ImportProgressInline")
    inner class ImportProgressInlineTest {

        @get:Rule val composeRule = createComposeRule()

        private val defaultProgress =
            ImportProgress(
                currentFile = "",
                currentIndex = 0,
                totalFiles = 0,
                copiedBytes = 0L,
                totalBytes = 0L,
                status = ImportStatus.PENDING,
            )

        private val activeProgress =
            ImportProgress(
                currentFile = "photo.jpg",
                currentIndex = 3,
                totalFiles = 10,
                copiedBytes = 1500L,
                totalBytes = 5000L,
                status = ImportStatus.PROCESSING,
            )

        @Test
        @DisplayName("shows progress UI with default empty progress")
        fun showsProgressUiWithDefaultProgress() {
            composeRule.setContent {
                ImportProgressInline(progress = defaultProgress, onCancel = {})
            }

            // The composable should render without error, showing Cancel button
            composeRule.onNodeWithText("Cancel").assertIsDisplayed()
        }

        @Test
        @DisplayName("shows file name when progress data includes currentFile")
        fun showsFileNameInProgress() {
            composeRule.setContent {
                ImportProgressInline(progress = activeProgress, onCancel = {})
            }

            composeRule.onNodeWithText("photo.jpg").assertIsDisplayed()
        }

        @Test
        @DisplayName("clicking cancel calls onCancel callback")
        fun clickingCancelCallsOnCancel() {
            var cancelCalled = false

            composeRule.setContent {
                ImportProgressInline(progress = activeProgress, onCancel = { cancelCalled = true })
            }

            composeRule.onNodeWithText("Cancel").performClick()

            assertThat(cancelCalled).isTrue()
        }

        @Test
        @DisplayName("shows progress when totalFiles > 0")
        fun showsProgressWhenTotalFilesGreaterThanZero() {
            composeRule.setContent {
                ImportProgressInline(progress = activeProgress, onCancel = {})
            }

            // With totalFiles = 10 and currentIndex = 3, progress should be visible
            composeRule.onNodeWithText("3", substring = true).assertIsDisplayed()
            composeRule.onNodeWithText("10", substring = true).assertIsDisplayed()
        }

        @Test
        @DisplayName("shows filename when currentFile is set")
        fun showsFilenameWhenCurrentFileIsSet() {
            val progressWithFile =
                ImportProgress(
                    currentFile = "document.pdf",
                    currentIndex = 5,
                    totalFiles = 20,
                    copiedBytes = 3000L,
                    totalBytes = 10000L,
                    status = ImportStatus.PROCESSING,
                )

            composeRule.setContent {
                ImportProgressInline(progress = progressWithFile, onCancel = {})
            }

            composeRule.onNodeWithText("document.pdf").assertIsDisplayed()
        }
    }
}
