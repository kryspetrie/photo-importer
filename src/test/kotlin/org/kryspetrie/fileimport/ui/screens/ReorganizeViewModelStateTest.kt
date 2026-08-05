package org.kryspetrie.fileimport.ui.screens

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.model.ImportConfiguration
import org.kryspetrie.fileimport.domain.model.ReorganizeMode
import org.kryspetrie.fileimport.domain.model.ReorganizePreview
import org.kryspetrie.fileimport.domain.model.ReorganizeProgress
import org.kryspetrie.fileimport.domain.model.ReorganizeResult

@DisplayName("ReorganizeViewModel State Transitions")
@Tag("UiComponentTest")
class ReorganizeViewModelStateTest {

    private lateinit var viewModel: ReorganizeViewModel

    @BeforeEach
    fun setup() {
        viewModel = ReorganizeViewModel()
    }

    @Nested
    @DisplayName("Setup to Preview Transition")
    inner class SetupToPreview {

        @Test
        @DisplayName("should transition from SETUP to SCANNING when start preview triggered")
        fun shouldTransitionFromSetupToScanning() {
            assertThat(viewModel.step).isEqualTo(ReorganizeViewModel.ReorgStep.SETUP)

            viewModel.step = ReorganizeViewModel.ReorgStep.SCANNING

            assertThat(viewModel.step).isEqualTo(ReorganizeViewModel.ReorgStep.SCANNING)
        }

        @Test
        @DisplayName("should transition from SCANNING to PREVIEW when scan completes")
        fun shouldTransitionFromScanningToPreview() {
            viewModel.step = ReorganizeViewModel.ReorgStep.SCANNING

            viewModel.preview = ReorganizePreview(
                mappings = emptyList(),
                totalFiles = 10,
                changedFiles = 3,
                conflictCount = 0,
                newFolderCount = 1,
            )
            viewModel.step = ReorganizeViewModel.ReorgStep.PREVIEW

            assertThat(viewModel.step).isEqualTo(ReorganizeViewModel.ReorgStep.PREVIEW)
            assertThat(viewModel.preview).isNotNull()
        }

        @Test
        @DisplayName("should transition back to SETUP on error")
        fun shouldTransitionBackToSetupOnError() {
            viewModel.step = ReorganizeViewModel.ReorgStep.SCANNING

            viewModel.errorMessage = "Scan failed"
            viewModel.step = ReorganizeViewModel.ReorgStep.SETUP

            assertThat(viewModel.step).isEqualTo(ReorganizeViewModel.ReorgStep.SETUP)
            assertThat(viewModel.errorMessage).isNotNull()
        }
    }

    @Nested
    @DisplayName("Preview to Complete Transition")
    inner class PreviewToComplete {

        @Test
        @DisplayName("should transition from PREVIEW to EXECUTING when reorg starts")
        fun shouldTransitionFromPreviewToExecuting() {
            viewModel.step = ReorganizeViewModel.ReorgStep.PREVIEW

            viewModel.step = ReorganizeViewModel.ReorgStep.EXECUTING

            assertThat(viewModel.step).isEqualTo(ReorganizeViewModel.ReorgStep.EXECUTING)
        }

        @Test
        @DisplayName("should transition from EXECUTING to COMPLETE when done")
        fun shouldTransitionFromExecutingToComplete() {
            viewModel.step = ReorganizeViewModel.ReorgStep.EXECUTING

            viewModel.result = ReorganizeResult(
                movedCount = 3,
                renamedCount = 0,
                skippedCount = 0,
                errorCount = 0,
            )
            viewModel.step = ReorganizeViewModel.ReorgStep.COMPLETE

            assertThat(viewModel.step).isEqualTo(ReorganizeViewModel.ReorgStep.COMPLETE)
            assertThat(viewModel.result).isNotNull()
        }

        @Test
        @DisplayName("should transition back to SETUP on execution error")
        fun shouldTransitionBackToSetupOnExecutionError() {
            viewModel.step = ReorganizeViewModel.ReorgStep.EXECUTING

            viewModel.errorMessage = "Reorg failed"
            viewModel.step = ReorganizeViewModel.ReorgStep.SETUP

            assertThat(viewModel.step).isEqualTo(ReorganizeViewModel.ReorgStep.SETUP)
            assertThat(viewModel.errorMessage).isNotNull()
        }
    }

    @Nested
    @DisplayName("Progress Updates")
    inner class ProgressUpdates {

        @Test
        @DisplayName("should update progress during scanning")
        fun shouldUpdateProgressDuringScanning() {
            viewModel.progress = ReorganizeProgress(
                current = 5,
                total = 10,
                currentFile = "test.jpg",
            )

            assertThat(viewModel.progress.current).isEqualTo(5)
            assertThat(viewModel.progress.total).isEqualTo(10)
            assertThat(viewModel.progress.currentFile).isEqualTo("test.jpg")
        }

        @Test
        @DisplayName("should update progress during execution")
        fun shouldUpdateProgressDuringExecution() {
            viewModel.step = ReorganizeViewModel.ReorgStep.EXECUTING

            viewModel.progress = ReorganizeProgress(
                current = 3,
                total = 10,
                currentFile = "moving.jpg",
            )

            assertThat(viewModel.progress.current).isEqualTo(3)
            assertThat(viewModel.step).isEqualTo(ReorganizeViewModel.ReorgStep.EXECUTING)
        }

        @Test
        @DisplayName("should reset progress on reset")
        fun shouldResetProgressOnReset() {
            viewModel.progress = ReorganizeProgress(
                current = 5,
                total = 10,
                currentFile = "test.jpg",
            )

            viewModel.reset()

            assertThat(viewModel.progress.current).isEqualTo(0)
            assertThat(viewModel.progress.total).isEqualTo(0)
        }
    }

    @Nested
    @DisplayName("Configuration Changes")
    inner class ConfigurationChanges {

        @Test
        @DisplayName("should update configuration")
        fun shouldUpdateConfiguration() {
            val newConfig = ImportConfiguration(
                folderPattern = "{yyyy}/{MM}",
                preserveOriginalName = false,
            )

            viewModel.config = newConfig

            assertThat(viewModel.config.folderPattern).isEqualTo("{yyyy}/{MM}")
            assertThat(viewModel.config.preserveOriginalName).isFalse()
        }

        @Test
        @DisplayName("should update reorganize mode")
        fun shouldUpdateReorganizeMode() {
            viewModel.reorgMode = ReorganizeMode.COPY

            assertThat(viewModel.reorgMode).isEqualTo(ReorganizeMode.COPY)
        }

        @Test
        @DisplayName("should toggle rename only")
        fun shouldToggleRenameOnly() {
            viewModel.renameOnly = true

            assertThat(viewModel.renameOnly).isTrue()

            viewModel.renameOnly = false

            assertThat(viewModel.renameOnly).isFalse()
        }

        @Test
        @DisplayName("should toggle settings expanded")
        fun shouldToggleSettingsExpanded() {
            viewModel.settingsExpanded = true

            assertThat(viewModel.settingsExpanded).isTrue()

            viewModel.settingsExpanded = false

            assertThat(viewModel.settingsExpanded).isFalse()
        }
    }

    @Nested
    @DisplayName("Error Handling")
    inner class ErrorHandling {

        @Test
        @DisplayName("should set error message")
        fun shouldSetErrorMessage() {
            viewModel.errorMessage = "Something went wrong"

            assertThat(viewModel.errorMessage).isEqualTo("Something went wrong")
        }

        @Test
        @DisplayName("should clear error message on reset")
        fun shouldClearErrorMessageOnReset() {
            viewModel.errorMessage = "Error"

            viewModel.reset()

            assertThat(viewModel.errorMessage).isNull()
        }

        @Test
        @DisplayName("should clear preview on reset")
        fun shouldClearPreviewOnReset() {
            viewModel.preview = ReorganizePreview(
                mappings = emptyList(),
                totalFiles = 10,
                changedFiles = 3,
                conflictCount = 0,
                newFolderCount = 1,
            )

            viewModel.reset()

            assertThat(viewModel.preview).isNull()
        }

        @Test
        @DisplayName("should clear result on reset")
        fun shouldClearResultOnReset() {
            viewModel.result = ReorganizeResult(
                movedCount = 3,
                renamedCount = 0,
                skippedCount = 0,
                errorCount = 0,
            )

            viewModel.reset()

            assertThat(viewModel.result).isNull()
        }
    }
}
