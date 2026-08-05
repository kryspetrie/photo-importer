package org.kryspetrie.fileimport.ui.screens

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.model.ImportConfiguration
import org.kryspetrie.fileimport.domain.model.ReorganizeJournalSummary
import org.kryspetrie.fileimport.domain.model.ReorganizeMode
import org.kryspetrie.fileimport.domain.model.ReorganizePreview
import org.kryspetrie.fileimport.domain.model.ReorganizeProgress
import org.kryspetrie.fileimport.domain.model.ReorganizeResult

@DisplayName("ReorganizeViewModel")
class ReorganizeViewModelTest {

    private lateinit var viewModel: ReorganizeViewModel

    @BeforeEach
    fun setup() {
        viewModel = ReorganizeViewModel()
    }

    @Nested
    @DisplayName("Initial State")
    inner class InitialState {

        @Test
        @DisplayName("should start in SETUP step")
        fun shouldStartInSetupStep() {
            assertThat(viewModel.step).isEqualTo(ReorganizeViewModel.ReorgStep.SETUP)
        }

        @Test
        @DisplayName("should have empty folder path")
        fun shouldHaveEmptyFolderPath() {
            assertThat(viewModel.folderPath).isEmpty()
        }

        @Test
        @DisplayName("should have default configuration")
        fun shouldHaveDefaultConfiguration() {
            assertThat(viewModel.config).isNotNull()
        }

        @Test
        @DisplayName("should have renameOnly disabled")
        fun shouldHaveRenameOnlyDisabled() {
            assertThat(viewModel.renameOnly).isFalse()
        }

        @Test
        @DisplayName("should have MOVE as default reorg mode")
        fun shouldHaveMoveAsDefaultReorgMode() {
            assertThat(viewModel.reorgMode).isEqualTo(ReorganizeMode.MOVE)
        }

        @Test
        @DisplayName("should have settings collapsed")
        fun shouldHaveSettingsCollapsed() {
            assertThat(viewModel.settingsExpanded).isFalse()
        }

        @Test
        @DisplayName("should have no preview")
        fun shouldHaveNoPreview() {
            assertThat(viewModel.preview).isNull()
        }

        @Test
        @DisplayName("should have no result")
        fun shouldHaveNoResult() {
            assertThat(viewModel.result).isNull()
        }

        @Test
        @DisplayName("should have no error message")
        fun shouldHaveNoErrorMessage() {
            assertThat(viewModel.errorMessage).isNull()
        }

        @Test
        @DisplayName("should have empty journals list")
        fun shouldHaveEmptyJournalsList() {
            assertThat(viewModel.journals).isEmpty()
        }

        @Test
        @DisplayName("should have no undo confirm")
        fun shouldHaveNoUndoConfirm() {
            assertThat(viewModel.showUndoConfirm).isNull()
        }
    }

    @Nested
    @DisplayName("State Mutations")
    inner class StateMutations {

        @Test
        @DisplayName("should update folder path")
        fun shouldUpdateFolderPath() {
            viewModel.folderPath = "/test/path"
            assertThat(viewModel.folderPath).isEqualTo("/test/path")
        }

        @Test
        @DisplayName("should update reorg mode")
        fun shouldUpdateReorgMode() {
            viewModel.reorgMode = ReorganizeMode.COPY
            assertThat(viewModel.reorgMode).isEqualTo(ReorganizeMode.COPY)
        }

        @Test
        @DisplayName("should toggle renameOnly")
        fun shouldToggleRenameOnly() {
            viewModel.renameOnly = true
            assertThat(viewModel.renameOnly).isTrue()
        }

        @Test
        @DisplayName("should toggle settings expanded")
        fun shouldToggleSettingsExpanded() {
            viewModel.settingsExpanded = true
            assertThat(viewModel.settingsExpanded).isTrue()
        }

        @Test
        @DisplayName("should update configuration")
        fun shouldUpdateConfiguration() {
            val newConfig = ImportConfiguration()
            viewModel.config = newConfig
            assertThat(viewModel.config).isEqualTo(newConfig)
        }

        @Test
        @DisplayName("should set error message")
        fun shouldSetErrorMessage() {
            viewModel.errorMessage = "Something went wrong"
            assertThat(viewModel.errorMessage).isEqualTo("Something went wrong")
        }

        @Test
        @DisplayName("should update journals")
        fun shouldUpdateJournals() {
            val journals =
                listOf(
                    ReorganizeJournalSummary(
                        id = "1",
                        rootFolder = "/test",
                        operationMode = ReorganizeMode.MOVE,
                        changedFiles = 5,
                        timestamp = 1234567890L,
                        timestampString = "2024-01-01",
                        totalFiles = 10,
                        undone = false,
                    )
                )
            viewModel.updateJournals(journals)
            assertThat(viewModel.journals).hasSize(1)
        }
    }

    @Nested
    @DisplayName("Reset")
    inner class Reset {

        @Test
        @DisplayName("should reset to SETUP step")
        fun shouldResetToSetupStep() {
            viewModel.step = ReorganizeViewModel.ReorgStep.PREVIEW
            viewModel.reset()
            assertThat(viewModel.step).isEqualTo(ReorganizeViewModel.ReorgStep.SETUP)
        }

        @Test
        @DisplayName("should clear preview on reset")
        fun shouldClearPreviewOnReset() {
            viewModel.preview =
                ReorganizePreview(
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
            viewModel.result =
                ReorganizeResult(
                    movedCount = 3,
                    renamedCount = 0,
                    skippedCount = 0,
                    errorCount = 0,
                    journalPath = "/tmp/journal",
                )
            viewModel.reset()
            assertThat(viewModel.result).isNull()
        }

        @Test
        @DisplayName("should clear error message on reset")
        fun shouldClearErrorMessageOnReset() {
            viewModel.errorMessage = "Error"
            viewModel.reset()
            assertThat(viewModel.errorMessage).isNull()
        }

        @Test
        @DisplayName("should reset progress on reset")
        fun shouldResetProgressOnReset() {
            viewModel.progress =
                ReorganizeProgress(
                    current = 5,
                    total = 10,
                    currentFile = "test.jpg",
                )
            viewModel.reset()
            assertThat(viewModel.progress.current).isEqualTo(0)
            assertThat(viewModel.progress.total).isEqualTo(0)
        }
    }
}
