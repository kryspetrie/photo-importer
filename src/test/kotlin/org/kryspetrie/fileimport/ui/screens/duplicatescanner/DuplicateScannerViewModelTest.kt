package org.kryspetrie.fileimport.ui.screens.duplicatescanner

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.model.DeduplicationSettings
import org.kryspetrie.fileimport.domain.model.DuplicateAction
import org.kryspetrie.fileimport.domain.model.DuplicateInfo
import org.kryspetrie.fileimport.domain.model.DuplicateType
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.ImageFile
import org.kryspetrie.fileimport.domain.model.ScanProgress
import org.kryspetrie.fileimport.ui.screens.DuplicateScannerViewModel

@DisplayName("DuplicateScannerViewModel")
@Tag("UiComponentTest")
class DuplicateScannerViewModelTest {

    private lateinit var viewModel: DuplicateScannerViewModel

    @BeforeEach
    fun setup() {
        viewModel = DuplicateScannerViewModel()
    }

    @Nested
    @DisplayName("Initial State")
    inner class InitialState {

        @Test
        @DisplayName("should start in SETUP step")
        fun shouldStartInSetupStep() {
            assertThat(viewModel.step).isEqualTo(DuplicateScannerViewModel.ScanStep.SETUP)
        }

        @Test
        @DisplayName("should have empty folder path")
        fun shouldHaveEmptyFolderPath() {
            assertThat(viewModel.folderPath).isEmpty()
        }

        @Test
        @DisplayName("should have hash detection enabled by default")
        fun shouldHaveHashDetectionEnabled() {
            assertThat(viewModel.enableHash).isTrue()
        }

        @Test
        @DisplayName("should have EXIF detection enabled by default")
        fun shouldHaveExifDetectionEnabled() {
            assertThat(viewModel.enableExif).isTrue()
        }

        @Test
        @DisplayName("should have SURF detection disabled by default")
        fun shouldHaveSurfDetectionDisabled() {
            assertThat(viewModel.enableSurf).isFalse()
        }

        @Test
        @DisplayName("should have empty duplicates list")
        fun shouldHaveEmptyDuplicatesList() {
            assertThat(viewModel.duplicates).isEmpty()
        }

        @Test
        @DisplayName("should have no error message")
        fun shouldHaveNoErrorMessage() {
            assertThat(viewModel.errorMessage).isNull()
        }

        @Test
        @DisplayName("should have default resolve action")
        fun shouldHaveDefaultResolveAction() {
            assertThat(viewModel.resolveAction).isEqualTo(DuplicateAction.KEEP_HIGHEST_RES)
        }

        @Test
        @DisplayName("should have move to trash enabled by default")
        fun shouldHaveMoveToTrashEnabled() {
            assertThat(viewModel.moveToTrash).isTrue()
        }

        @Test
        @DisplayName("should have no active job")
        fun shouldHaveNoActiveJob() {
            assertThat(viewModel.activeJob).isNull()
        }
    }

    @Nested
    @DisplayName("State Transitions")
    inner class StateTransitions {

        @Test
        @DisplayName("should transition from SETUP to SCANNING when scan starts")
        fun shouldTransitionFromSetupToScanning() {
            assertThat(viewModel.step).isEqualTo(DuplicateScannerViewModel.ScanStep.SETUP)

            viewModel.step = DuplicateScannerViewModel.ScanStep.SCANNING

            assertThat(viewModel.step).isEqualTo(DuplicateScannerViewModel.ScanStep.SCANNING)
        }

        @Test
        @DisplayName("should transition from SCANNING to RESULTS when scan completes")
        fun shouldTransitionFromScanningToResults() {
            viewModel.step = DuplicateScannerViewModel.ScanStep.SCANNING

            viewModel.duplicates = listOf(
                DuplicateInfo(
                    primaryImage = ImageFile(
                        path = FilePath("photo1.jpg"),
                        fileSize = 1024,
                    ),
                    duplicateImages = listOf(
                        ImageFile(
                            path = FilePath("photo1_copy.jpg"),
                            fileSize = 1024,
                        )
                    ),
                    duplicateType = DuplicateType.EXACT_HASH,
                )
            )
            viewModel.step = DuplicateScannerViewModel.ScanStep.RESULTS

            assertThat(viewModel.step).isEqualTo(DuplicateScannerViewModel.ScanStep.RESULTS)
            assertThat(viewModel.duplicates).isNotEmpty()
        }

        @Test
        @DisplayName("should transition back to SETUP on error")
        fun shouldTransitionBackToSetupOnError() {
            viewModel.step = DuplicateScannerViewModel.ScanStep.SCANNING

            viewModel.errorMessage = "Scan failed"
            viewModel.step = DuplicateScannerViewModel.ScanStep.SETUP

            assertThat(viewModel.step).isEqualTo(DuplicateScannerViewModel.ScanStep.SETUP)
            assertThat(viewModel.errorMessage).isNotNull()
        }

        @Test
        @DisplayName("should transition to RESOLVING when resolve starts")
        fun shouldTransitionToResolving() {
            viewModel.step = DuplicateScannerViewModel.ScanStep.RESULTS

            viewModel.step = DuplicateScannerViewModel.ScanStep.RESOLVING

            assertThat(viewModel.step).isEqualTo(DuplicateScannerViewModel.ScanStep.RESOLVING)
        }

        @Test
        @DisplayName("should transition from RESOLVING to RESULTS when done")
        fun shouldTransitionFromResolvingToResults() {
            viewModel.step = DuplicateScannerViewModel.ScanStep.RESOLVING

            viewModel.step = DuplicateScannerViewModel.ScanStep.RESULTS

            assertThat(viewModel.step).isEqualTo(DuplicateScannerViewModel.ScanStep.RESULTS)
        }
    }

    @Nested
    @DisplayName("Detection Method Toggles")
    inner class DetectionMethodToggles {

        @Test
        @DisplayName("should toggle hash detection")
        fun shouldToggleHashDetection() {
            viewModel.enableHash = false

            assertThat(viewModel.enableHash).isFalse()

            viewModel.enableHash = true

            assertThat(viewModel.enableHash).isTrue()
        }

        @Test
        @DisplayName("should toggle EXIF detection")
        fun shouldToggleExifDetection() {
            viewModel.enableExif = false

            assertThat(viewModel.enableExif).isFalse()

            viewModel.enableExif = true

            assertThat(viewModel.enableExif).isTrue()
        }

        @Test
        @DisplayName("should toggle SURF detection")
        fun shouldToggleSurfDetection() {
            viewModel.enableSurf = true

            assertThat(viewModel.enableSurf).isTrue()

            viewModel.enableSurf = false

            assertThat(viewModel.enableSurf).isFalse()
        }
    }

    @Nested
    @DisplayName("Duplicate Management")
    inner class DuplicateManagement {

        @Test
        @DisplayName("should update duplicates list")
        fun shouldUpdateDuplicatesList() {
            val duplicates = listOf(
                DuplicateInfo(
                    primaryImage = ImageFile(
                        path = FilePath("photo1.jpg"),
                        fileSize = 1024,
                    ),
                    duplicateImages = emptyList(),
                    duplicateType = DuplicateType.EXACT_HASH,
                )
            )

            viewModel.duplicates = duplicates

            assertThat(viewModel.duplicates).hasSize(1)
        }

        @Test
        @DisplayName("should clear duplicates on reset")
        fun shouldClearDuplicatesOnReset() {
            viewModel.duplicates = listOf(
                DuplicateInfo(
                    primaryImage = ImageFile(
                        path = FilePath("photo1.jpg"),
                        fileSize = 1024,
                    ),
                    duplicateImages = emptyList(),
                    duplicateType = DuplicateType.EXACT_HASH,
                )
            )

            viewModel.reset()

            assertThat(viewModel.duplicates).isEmpty()
        }

        @Test
        @DisplayName("should calculate total duplicate files")
        fun shouldCalculateTotalDuplicateFiles() {
            viewModel.duplicates = listOf(
                DuplicateInfo(
                    primaryImage = ImageFile(
                        path = FilePath("photo1.jpg"),
                        fileSize = 1024,
                    ),
                    duplicateImages = listOf(
                        ImageFile(path = FilePath("dup1.jpg"), fileSize = 1024),
                        ImageFile(path = FilePath("dup2.jpg"), fileSize = 1024),
                    ),
                    duplicateType = DuplicateType.EXACT_HASH,
                )
            )

            assertThat(viewModel.totalDupeFiles).isEqualTo(2)
        }

        @Test
        @DisplayName("should calculate total wasted bytes")
        fun shouldCalculateTotalWastedBytes() {
            viewModel.duplicates = listOf(
                DuplicateInfo(
                    primaryImage = ImageFile(
                        path = FilePath("photo1.jpg"),
                        fileSize = 1024,
                    ),
                    duplicateImages = listOf(
                        ImageFile(path = FilePath("dup1.jpg"), fileSize = 512),
                    ),
                    duplicateType = DuplicateType.EXACT_HASH,
                )
            )

            assertThat(viewModel.totalWastedBytes).isEqualTo(512)
        }

        @Test
        @DisplayName("should set primary image for a group")
        fun shouldSetPrimaryImageForGroup() {
            val duplicates = listOf(
                DuplicateInfo(
                    primaryImage = ImageFile(
                        path = FilePath("photo1.jpg"),
                        fileSize = 1024,
                    ),
                    duplicateImages = listOf(
                        ImageFile(path = FilePath("dup1.jpg"), fileSize = 512),
                    ),
                    duplicateType = DuplicateType.EXACT_HASH,
                )
            )

            viewModel.duplicates = duplicates
            viewModel.setPrimaryImage("photo1.jpg", "dup1.jpg")

            assertThat(viewModel.duplicates.first().primaryImage.path.path).isEqualTo("dup1.jpg")
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
        @DisplayName("should clear error message when scan restarts")
        fun shouldClearErrorMessageWhenScanRestarts() {
            viewModel.errorMessage = "Previous error"

            viewModel.step = DuplicateScannerViewModel.ScanStep.SCANNING

            assertThat(viewModel.errorMessage).isNull()
        }
    }

    @Nested
    @DisplayName("Settings")
    inner class Settings {

        @Test
        @DisplayName("should update folder path")
        fun shouldUpdateFolderPath() {
            viewModel.folderPath = "/test/path"

            assertThat(viewModel.folderPath).isEqualTo("/test/path")
        }

        @Test
        @DisplayName("should clear folder path on reset")
        fun shouldClearFolderPathOnReset() {
            viewModel.folderPath = "/test/path"

            viewModel.reset()

            assertThat(viewModel.folderPath).isEmpty()
        }

        @Test
        @DisplayName("should update resolve action")
        fun shouldUpdateResolveAction() {
            viewModel.resolveAction = DuplicateAction.KEEP_NEWEST

            assertThat(viewModel.resolveAction).isEqualTo(DuplicateAction.KEEP_NEWEST)
        }

        @Test
        @DisplayName("should toggle move to trash")
        fun shouldToggleMoveToTrash() {
            viewModel.moveToTrash = false

            assertThat(viewModel.moveToTrash).isFalse()

            viewModel.moveToTrash = true

            assertThat(viewModel.moveToTrash).isTrue()
        }

        @Test
        @DisplayName("should build dedup settings from detection flags")
        fun shouldBuildDedupSettingsFromDetectionFlags() {
            viewModel.enableHash = true
            viewModel.enableExif = false
            viewModel.enableSurf = true

            val settings: DeduplicationSettings = viewModel.buildDedupSettings()

            assertThat(settings.enableHashDeduplication).isTrue()
            assertThat(settings.enableExifDeduplication).isFalse()
            assertThat(settings.enableSurfMatching).isTrue()
        }
    }

    @Nested
    @DisplayName("Cancel Operation")
    inner class CancelOperation {

        @Test
        @DisplayName("should cancel active job and reset to SETUP")
        fun shouldCancelActiveJobAndResetToSetup() {
            viewModel.step = DuplicateScannerViewModel.ScanStep.SCANNING
            viewModel.errorMessage = "Error"

            viewModel.cancelOperation()

            assertThat(viewModel.step).isEqualTo(DuplicateScannerViewModel.ScanStep.SETUP)
            assertThat(viewModel.errorMessage).isNull()
            assertThat(viewModel.activeJob).isNull()
        }
    }

    @Nested
    @DisplayName("Reset")
    inner class Reset {

        @Test
        @DisplayName("should reset to SETUP step")
        fun shouldResetToSetupStep() {
            viewModel.step = DuplicateScannerViewModel.ScanStep.RESULTS

            viewModel.reset()

            assertThat(viewModel.step).isEqualTo(DuplicateScannerViewModel.ScanStep.SETUP)
        }

        @Test
        @DisplayName("should clear duplicates on reset")
        fun shouldClearDuplicatesOnResetFull() {
            viewModel.duplicates = listOf(
                DuplicateInfo(
                    primaryImage = ImageFile(
                        path = FilePath("photo1.jpg"),
                        fileSize = 1024,
                    ),
                    duplicateImages = emptyList(),
                    duplicateType = DuplicateType.EXACT_HASH,
                )
            )

            viewModel.reset()

            assertThat(viewModel.duplicates).isEmpty()
        }

        @Test
        @DisplayName("should clear error message on reset")
        fun shouldClearErrorMessageOnResetFull() {
            viewModel.errorMessage = "Error"

            viewModel.reset()

            assertThat(viewModel.errorMessage).isNull()
        }

        @Test
        @DisplayName("should reset scan progress on reset")
        fun shouldResetScanProgressOnReset() {
            viewModel.scanProgress = ScanProgress(current = 5, total = 10)

            viewModel.reset()

            assertThat(viewModel.scanProgress.current).isEqualTo(0)
            assertThat(viewModel.scanProgress.total).isEqualTo(0)
        }
    }
}
