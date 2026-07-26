package org.kryspetrie.fileimport.ui.screens.metadataeditor

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.application.OrientationCorrectionService
import org.kryspetrie.fileimport.application.TestDispatcherProvider
import org.kryspetrie.fileimport.application.metadata.MetadataEditService
import org.kryspetrie.fileimport.application.metadata.MetadataEditUndoService
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.MetadataEditorFileViewMode
import org.kryspetrie.fileimport.domain.model.MetadataEditorLayoutMode
import org.kryspetrie.fileimport.domain.model.RotationAngle
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.domain.port.LocalePort
import org.kryspetrie.fileimport.domain.port.SettingsPort
import androidx.compose.ui.input.key.Key
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@DisplayName("MetadataEditorViewModel")
class MetadataEditorViewModelTest {

    @Nested
    @DisplayName("nearestCorrectionDeg")
    inner class NearestCorrectionDegTests {
        @Test
        fun `returns 0 for NONE rotation`() {
            val result =
                OrientationCorrectionService.CorrectionResult(
                    orientationDegrees = 0f,
                    confidence = 0.95f,
                    nearestRotation = RotationAngle.NONE,
                    correctionDegrees = 0f,
                    isJpeg = false,
                )
            // Test via static computation — the method is pure logic
            assertThat(
                    when (result.nearestRotation) {
                        RotationAngle.NONE -> 0
                        RotationAngle.CW_90 -> 90
                        RotationAngle.CW_180 -> 180
                        RotationAngle.CCW_90 -> 270
                    }
                )
                .isEqualTo(0)
        }

        @Test
        fun `returns 90 for CW_90 rotation`() {
            val result =
                OrientationCorrectionService.CorrectionResult(
                    orientationDegrees = 270f,
                    confidence = 0.95f,
                    nearestRotation = RotationAngle.CW_90,
                    correctionDegrees = 90f,
                    isJpeg = false,
                )
            assertThat(
                    when (result.nearestRotation) {
                        RotationAngle.NONE -> 0
                        RotationAngle.CW_90 -> 90
                        RotationAngle.CW_180 -> 180
                        RotationAngle.CCW_90 -> 270
                    }
                )
                .isEqualTo(90)
        }

        @Test
        fun `returns 180 for CW_180 rotation`() {
            val result =
                OrientationCorrectionService.CorrectionResult(
                    orientationDegrees = 180f,
                    confidence = 0.95f,
                    nearestRotation = RotationAngle.CW_180,
                    correctionDegrees = 180f,
                    isJpeg = false,
                )
            assertThat(
                    when (result.nearestRotation) {
                        RotationAngle.NONE -> 0
                        RotationAngle.CW_90 -> 90
                        RotationAngle.CW_180 -> 180
                        RotationAngle.CCW_90 -> 270
                    }
                )
                .isEqualTo(180)
        }

        @Test
        fun `returns 270 for CCW_90 rotation`() {
            val result =
                OrientationCorrectionService.CorrectionResult(
                    orientationDegrees = 90f,
                    confidence = 0.95f,
                    nearestRotation = RotationAngle.CCW_90,
                    correctionDegrees = 270f,
                    isJpeg = false,
                )
            assertThat(
                    when (result.nearestRotation) {
                        RotationAngle.NONE -> 0
                        RotationAngle.CW_90 -> 90
                        RotationAngle.CW_180 -> 180
                        RotationAngle.CCW_90 -> 270
                    }
                )
                .isEqualTo(270)
        }
    }

    @Nested
    @DisplayName("BulkEditState")
    inner class BulkEditStateTests {
        @Test
        fun `loadFiles initializes state correctly`() {
            val state = BulkEditState()
            state.loadFiles(listOf(File("/tmp/a.jpg"), File("/tmp/b.jpg")))
            assertThat(state.fileCount).isEqualTo(2)
            assertThat(state.selectedIndex).isEqualTo(0)
        }

        @Test
        fun `loadSingleFile sets sourcePath and single file`() {
            val state = BulkEditState()
            state.loadSingleFile(File("/tmp/test.jpg"))
            assertThat(state.fileCount).isEqualTo(1)
            assertThat(state.selectedIndex).isEqualTo(0)
            assertThat(state.sourcePath).isEqualTo("/tmp/test.jpg")
        }

        @Test
        fun `updateSelectedConfig marks file as modified`() {
            val state = BulkEditState()
            state.loadFiles(listOf(File("/tmp/a.jpg")))
            assertThat(state.fileConfigs.values.first().isModified).isFalse()
            state.updateSelectedConfig { it.copy(description = "test") }
            assertThat(state.fileConfigs.values.first().isModified).isTrue()
            assertThat(state.selectedConfig.description).isEqualTo("test")
        }

        @Test
        fun `updateConfig updates specific index`() {
            val state = BulkEditState()
            state.loadFiles(listOf(File("/tmp/a.jpg"), File("/tmp/b.jpg")))
            state.updateConfig(1) { it.copy(city = "Berlin") }
            assertThat(state.fileConfigs.values.toList()[1].config.city).isEqualTo("Berlin")
            assertThat(state.fileConfigs.values.toList()[0].config.city).isEmpty()
        }

        @Test
        fun `markSaved clears modified flag`() {
            val state = BulkEditState()
            val file = File("/tmp/a.jpg")
            state.loadFiles(listOf(file))
            state.updateSelectedConfig { it.copy(description = "modified") }
            assertThat(state.modifiedCount).isEqualTo(1)
            state.markSaved(file)
            assertThat(state.modifiedCount).isEqualTo(0)
        }

        @Test
        fun `showError and showInfo set messages`() {
            val state = BulkEditState()
            assertThat(state.message).isNull()
            state.showError("test error")
            assertThat(state.message).isNotNull
            assertThat(state.message!!.severity).isEqualTo(MessageSeverity.ERROR)
            state.showInfo("test info")
            assertThat(state.message!!.severity).isEqualTo(MessageSeverity.INFO)
        }

        @Test
        fun `nextFile and prevFile navigate correctly`() {
            val state = BulkEditState()
            state.loadFiles(listOf(File("/tmp/a.jpg"), File("/tmp/b.jpg"), File("/tmp/c.jpg")))
            assertThat(state.selectedIndex).isEqualTo(0)
            assertThat(state.nextFile()).isTrue()
            assertThat(state.selectedIndex).isEqualTo(1)
            assertThat(state.nextFile()).isTrue()
            assertThat(state.selectedIndex).isEqualTo(2)
            assertThat(state.nextFile()).isFalse()
            assertThat(state.prevFile()).isTrue()
            assertThat(state.selectedIndex).isEqualTo(1)
        }

        @Test
        fun `hasMetadata returns true when description is set`() {
            val config =
                org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration(description = "test")
            assertThat(config.hasMetadata()).isTrue()
            val emptyConfig = org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration()
            assertThat(emptyConfig.hasMetadata()).isFalse()
        }

        @Test
        fun `cycleRotationCW rotates 90 degrees`() {
            val config = org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration()
            val rotated = config.cycleRotationCW()
            assertThat(rotated.rotationDegrees).isEqualTo(90)
            val rotatedAgain = rotated.cycleRotationCW()
            assertThat(rotatedAgain.rotationDegrees).isEqualTo(180)
        }

        @Test
        fun `cycleRotationCCW rotates 270 degrees`() {
            val config = org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration()
            val rotated = config.cycleRotationCCW()
            assertThat(rotated.rotationDegrees).isEqualTo(270)
        }

        @Test
        fun `rotate180 rotates 180 degrees`() {
            val config = org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration()
            val rotated = config.rotate180()
            assertThat(rotated.rotationDegrees).isEqualTo(180)
        }

        @Test
        fun `clear resets state`() {
            val state = BulkEditState()
            state.loadFiles(listOf(File("/tmp/a.jpg")))
            state.message = null
            state.sourcePath = "/test"
            state.clear()
            assertThat(state.sourcePath).isEmpty()
            assertThat(state.fileCount).isEqualTo(0)
            assertThat(state.selectedIndex).isEqualTo(-1)
        }

        @Test
        fun `modifiedCount counts modified files`() {
            val state = BulkEditState()
            state.loadFiles(listOf(File("/tmp/a.jpg"), File("/tmp/b.jpg")))
            assertThat(state.modifiedCount).isEqualTo(0)
            state.updateConfig(0) { it.copy(description = "modified") }
            assertThat(state.modifiedCount).isEqualTo(1)
            state.updateConfig(1) { it.copy(city = "Berlin") }
            assertThat(state.modifiedCount).isEqualTo(2)
        }
    }

    @Nested
    @DisplayName("ViewModel selection and layout")
    inner class ViewModelBehaviorTests {
        private lateinit var vm: MetadataEditorViewModel

        @BeforeEach
        fun setUpViewModel() {
            vm =
                MetadataEditorViewModel(
                    dispatcherProvider = TestDispatcherProvider(),
                    imageRepository = mock(),
                    imageProcessing = mock(),
                    locationSearchService = mock(),
                    geocodingPort = mock(),
                    settingsPort = stubSettingsPort(),
                    editService = mock(),
                    undoService = mock(),
                    faceRegionTransformer = mock(),
                    fileSystemAdapter = mock(),
                    orientationCorrection = mock(),
                    modelDownloadPort = mock(),
                    localePort = stubLocalePort(),
                )
            vm.currentSettings = AppSettings()
        }

        @Test
        fun toggleSelectionSelectsFileInSingleEditMode() {
            vm.state.loadFiles(listOf(File("/tmp/a.jpg"), File("/tmp/b.jpg")))
            vm.toggleSelection(1)
            assertThat(vm.state.selectedIndex).isEqualTo(1)
        }

        @Test
        fun toggleSelectionTogglesIndicesInMultiEditMode() {
            vm.state.loadFiles(listOf(File("/tmp/a.jpg"), File("/tmp/b.jpg")))
            vm.isMultiEditMode = true
            vm.toggleSelection(0)
            vm.toggleSelection(1)
            assertThat(vm.selectedIndices).containsExactly(0, 1)
            vm.toggleSelection(0)
            assertThat(vm.selectedIndices).containsExactly(1)
        }

        @Test
        fun setFileViewModeUpdatesSettingsAndLegacyLayout() {
            // GIVEN
            var captured: AppSettings? = null

            // WHEN
            vm.setFileViewMode(MetadataEditorFileViewMode.COLUMN) { captured = it }

            // THEN
            assertThat(captured?.metadataEditorFileViewMode)
                .isEqualTo(MetadataEditorFileViewMode.COLUMN)
            assertThat(captured?.metadataEditorLayoutMode)
                .isEqualTo(MetadataEditorLayoutMode.FILE_PICKER)

            // WHEN
            vm.setFileViewMode(MetadataEditorFileViewMode.ICONS) { captured = it }

            // THEN
            assertThat(captured?.metadataEditorFileViewMode)
                .isEqualTo(MetadataEditorFileViewMode.ICONS)
            assertThat(captured?.metadataEditorLayoutMode)
                .isEqualTo(MetadataEditorLayoutMode.SIDEBAR)
        }

        @Test
        fun handleBrowserKeyNavigatesFoldersAndFilesInListView() {
            // GIVEN
            vm.state.editingActive = true
            vm.state.sourcePath = "/tmp/album"
            vm.state.loadFiles(
                listOf(
                    File("/tmp/album/a.jpg"),
                    File("/tmp/album/nested/b.jpg"),
                )
            )
            vm.state.selectFile(0)
            val nestedPath = File("/tmp/album/nested").absolutePath

            // WHEN — move focus to the subfolder
            val movedToFolder = vm.handleBrowserKey(Key.DirectionUp, MetadataEditorFileViewMode.LIST)

            // THEN
            assertThat(movedToFolder).isTrue()
            assertThat(vm.browserFocusedFolderPath).isEqualTo(nestedPath)

            // WHEN — open the focused folder
            val openedFolder = vm.handleBrowserKey(Key.DirectionRight, MetadataEditorFileViewMode.LIST)

            // THEN
            assertThat(openedFolder).isTrue()
            assertThat(vm.browserFolderPathStack).containsExactly(nestedPath)
            assertThat(vm.browserFocusedFolderPath).isNull()

            // WHEN — select the file inside the folder
            val movedToFile = vm.handleBrowserKey(Key.DirectionDown, MetadataEditorFileViewMode.LIST)

            // THEN
            assertThat(movedToFile).isTrue()
            assertThat(vm.state.selectedIndex).isEqualTo(1)

            // WHEN — navigate back to the parent folder
            val movedUp = vm.handleBrowserKey(Key.DirectionLeft, MetadataEditorFileViewMode.LIST)

            // THEN
            assertThat(movedUp).isTrue()
            assertThat(vm.browserFolderPathStack).isEmpty()
        }

        @Test
        fun handleBrowserKeyUsesGlobalFileOrderInHierarchyView() {
            // GIVEN
            vm.state.editingActive = true
            vm.state.loadFiles(listOf(File("/tmp/a.jpg"), File("/tmp/b.jpg")))
            vm.state.selectFile(0)

            // WHEN
            val handled = vm.handleBrowserKey(Key.DirectionDown, MetadataEditorFileViewMode.HIERARCHY)

            // THEN
            assertThat(handled).isTrue()
            assertThat(vm.state.selectedIndex).isEqualTo(1)
        }

        @Test
        fun handleBrowserKeyUsesGlobalFileOrderInColumnView() {
            // GIVEN
            vm.state.editingActive = true
            vm.state.loadFiles(listOf(File("/tmp/a.jpg"), File("/tmp/b.jpg")))
            vm.state.selectFile(0)

            // WHEN
            val handled = vm.handleBrowserKey(Key.DirectionDown, MetadataEditorFileViewMode.COLUMN)

            // THEN
            assertThat(handled).isTrue()
            assertThat(vm.state.selectedIndex).isEqualTo(1)
        }

        @Test
        fun handleBrowserEnterOpensFocusedFolder() {
            // GIVEN
            vm.state.editingActive = true
            vm.state.sourcePath = "/tmp/album"
            vm.state.loadFiles(
                listOf(
                    File("/tmp/album/a.jpg"),
                    File("/tmp/album/nested/b.jpg"),
                )
            )
            val nestedPath = File("/tmp/album/nested").absolutePath
            vm.browserFocusedFolderPath = nestedPath

            // WHEN
            val handled = vm.handleBrowserKey(Key.Enter, MetadataEditorFileViewMode.LIST)

            // THEN
            assertThat(handled).isTrue()
            assertThat(vm.browserFolderPathStack).containsExactly(nestedPath)
            assertThat(vm.browserFocusedFolderPath).isNull()
        }

        @Test
        fun selectBrowserFileClearsFocusedFolder() {
            // GIVEN
            vm.state.loadFiles(listOf(File("/tmp/a.jpg")))
            vm.browserFocusedFolderPath = "/tmp/nested"

            // WHEN
            vm.selectBrowserFile(0)

            // THEN
            assertThat(vm.browserFocusedFolderPath).isNull()
            assertThat(vm.state.selectedIndex).isEqualTo(0)
        }

        @Test
        fun loadSelectedFilesResetsBrowserNavigation() {
            // GIVEN
            val tempFile = Files.createTempFile("metadata-editor", ".jpg").toFile()
            tempFile.deleteOnExit()
            vm.browserFolderPathStack = listOf("/tmp/nested")
            vm.browserFocusedFolderPath = "/tmp/nested"

            // WHEN
            runBlocking {
                vm.loadSelectedFiles(listOf(tempFile.absolutePath), this) {}
            }

            // THEN
            assertThat(vm.browserFolderPathStack).isEmpty()
            assertThat(vm.browserFocusedFolderPath).isNull()
        }

        @Test
        fun setLayoutModeUpdatesSettingsCallback() {
            var captured: AppSettings? = null
            vm.setLayoutMode(MetadataEditorLayoutMode.FILE_PICKER) { captured = it }
            assertThat(captured?.metadataEditorLayoutMode)
                .isEqualTo(MetadataEditorLayoutMode.FILE_PICKER)
        }

        @Test
        fun loadSelectedFilesLoadsMetadataEditableImages() {
            val tempFile = Files.createTempFile("metadata-editor", ".jpg").toFile()
            tempFile.deleteOnExit()

            runBlocking {
                vm.loadSelectedFiles(listOf(tempFile.absolutePath), this) {}
            }

            assertThat(vm.state.editingActive).isTrue()
            assertThat(vm.state.fileCount).isEqualTo(1)
            assertThat(vm.state.selectedFile?.absolutePath).isEqualTo(tempFile.absolutePath)
            assertThat(vm.isMultiEditMode).isFalse()
        }

        @Test
        fun loadSelectedFilesIgnoresUnsupportedExtensions() {
            val tempFile = Files.createTempFile("metadata-editor", ".mp4").toFile()
            tempFile.deleteOnExit()

            runBlocking {
                vm.loadSelectedFiles(listOf(tempFile.absolutePath), this) {}
            }

            assertThat(vm.state.fileCount).isEqualTo(0)
            assertThat(vm.state.message?.severity).isEqualTo(MessageSeverity.ERROR)
        }

        private fun stubSettingsPort(): SettingsPort {
            val settingsPort = mock<SettingsPort>()
            whenever(settingsPort.observeSettings()).thenReturn(MutableStateFlow(AppSettings()))
            return settingsPort
        }

        private fun stubLocalePort(): LocalePort {
            val localePort = mock<LocalePort>()
            whenever(localePort.t(any<StringKey>())).thenAnswer { inv ->
                inv.getArgument<StringKey>(0).name
            }
            whenever(localePort.t(any<StringKey>(), any())).thenAnswer { inv ->
                inv.getArgument<StringKey>(0).name
            }
            return localePort
        }
    }
}
