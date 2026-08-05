package org.kryspetrie.fileimport.ui.screens.metadataeditor

import androidx.compose.ui.input.key.Key
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.application.OrientationCorrectionService
import org.kryspetrie.fileimport.application.TestDispatcherProvider
import org.kryspetrie.fileimport.application.metadata.MetadataEditService
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.MetadataEditEntry
import org.kryspetrie.fileimport.domain.model.MetadataEditorFileViewMode
import org.kryspetrie.fileimport.domain.model.ProcessedImage
import org.kryspetrie.fileimport.domain.model.RotationAngle
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.domain.port.FolderThumbnailCachePort
import org.kryspetrie.fileimport.domain.port.LocalePort
import org.kryspetrie.fileimport.domain.port.ModelDownloadPort
import org.kryspetrie.fileimport.domain.port.SettingsPort
import org.kryspetrie.fileimport.infrastructure.adapter.toProcessedImage
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@DisplayName("MetadataEditorViewModel")
class MetadataEditorViewModelTest {

    @Nested
    @DisplayName("applyBatchRotationCorrection")
    inner class ApplyBatchRotationCorrectionTests {
        private lateinit var imageProcessing:
            org.kryspetrie.fileimport.domain.port.ImageProcessingPort
        private lateinit var orientationCorrection: OrientationCorrectionService
        private lateinit var modelDownloadPort: ModelDownloadPort
        private lateinit var vm: MetadataEditorViewModel

        @Before
        fun setUp() {
            imageProcessing = mock()
            orientationCorrection = mock()
            modelDownloadPort = mock()
            whenever(modelDownloadPort.isModelDownloaded(any())).thenReturn(true)
            whenever(orientationCorrection.isAvailable()).thenReturn(true)
            vm =
                MetadataEditorViewModel(
                    dispatcherProvider = TestDispatcherProvider(),
                    imageRepository = mock(),
                    imageProcessing = imageProcessing,
                    locationSearchService = mock(),
                    geocodingPort = mock(),
                    settingsPort = stubSettingsPort(),
                    editService = mock(),
                    undoService = mock(),
                    faceRegionTransformer = mock(),
                    fileSystemAdapter = mock(),
                    orientationCorrection = orientationCorrection,
                    modelDownloadPort = modelDownloadPort,
                    faceDetectionPort = mock(),
                    folderThumbnailCache = FakeFolderThumbnailCache(),
                    localePort = stubLocalePort(),
                )
            vm.currentSettings = AppSettings()
        }

        @Test
        fun appliesCw90ViaDetectionThenApply() = runBlocking {
            val file = Files.createTempFile("rot", ".jpg").toFile().apply { deleteOnExit() }
            val image = BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB).toProcessedImage()
            whenever(imageProcessing.readImage(any())).thenReturn(image)
            whenever(orientationCorrection.detectOnly(any()))
                .thenReturn(
                    OrientationCorrectionService.CorrectionResult(
                        orientationDegrees = 270f,
                        confidence = 0.9f,
                        nearestRotation = RotationAngle.CW_90,
                        correctionDegrees = 90f,
                        isJpeg = true,
                    )
                )

            vm.state.loadFiles(listOf(file))
            vm.startBatchOrientationDetection(this)
            while (vm.isDetectingOrientation) {
                kotlinx.coroutines.yield()
            }

            assertThat(vm.orientationResults).containsKey(file.absolutePath)
            assertThat(vm.showRotationPreview).isTrue()

            vm.applyBatchRotationCorrection()

            assertThat(vm.state.fileConfigs[file.absolutePath]!!.config.rotationDegrees)
                .isEqualTo(90)
            assertThat(vm.orientationResults).isEmpty()
            assertThat(vm.showRotationPreview).isFalse()
            assertThat(vm.state.message?.text).contains("META_APPLIED_ROTATION_N")
        }

        @Test
        fun accumulatesRotationDegreesModulo360() = runBlocking {
            val file = Files.createTempFile("rot2", ".jpg").toFile().apply { deleteOnExit() }
            val image = BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB).toProcessedImage()
            whenever(imageProcessing.readImage(any())).thenReturn(image)
            whenever(orientationCorrection.detectOnly(any()))
                .thenReturn(
                    OrientationCorrectionService.CorrectionResult(
                        orientationDegrees = 270f,
                        confidence = 0.9f,
                        nearestRotation = RotationAngle.CW_90,
                        correctionDegrees = 90f,
                        isJpeg = true,
                    )
                )

            vm.state.loadFiles(listOf(file))
            vm.state.updateConfig(0) { it.copy(rotationDegrees = 90) }
            vm.startBatchOrientationDetection(this)
            while (vm.isDetectingOrientation) {
                kotlinx.coroutines.yield()
            }
            vm.applyBatchRotationCorrection()

            assertThat(vm.state.fileConfigs[file.absolutePath]!!.config.rotationDegrees)
                .isEqualTo(180)
        }

        @Test
        fun skipsExcludedPaths() = runBlocking {
            val file = Files.createTempFile("rot-excl", ".jpg").toFile().apply { deleteOnExit() }
            val image = BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB).toProcessedImage()
            whenever(imageProcessing.readImage(any())).thenReturn(image)
            whenever(orientationCorrection.detectOnly(any()))
                .thenReturn(
                    OrientationCorrectionService.CorrectionResult(
                        orientationDegrees = 270f,
                        confidence = 0.9f,
                        nearestRotation = RotationAngle.CW_90,
                        correctionDegrees = 90f,
                        isJpeg = true,
                    )
                )

            vm.state.loadFiles(listOf(file))
            vm.startBatchOrientationDetection(this)
            while (vm.isDetectingOrientation) {
                kotlinx.coroutines.yield()
            }
            vm.toggleRotationExclusion(file.absolutePath)
            vm.applyBatchRotationCorrection()

            assertThat(vm.state.fileConfigs[file.absolutePath]!!.config.rotationDegrees)
                .isEqualTo(0)
        }

        @Test
        fun skipsNoneRotationResults() = runBlocking {
            val file = Files.createTempFile("rot3", ".jpg").toFile().apply { deleteOnExit() }
            val image = BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB).toProcessedImage()
            whenever(imageProcessing.readImage(any())).thenReturn(image)
            whenever(orientationCorrection.detectOnly(any()))
                .thenReturn(
                    OrientationCorrectionService.CorrectionResult(
                        orientationDegrees = 0f,
                        confidence = 0.9f,
                        nearestRotation = RotationAngle.NONE,
                        correctionDegrees = 0f,
                        isJpeg = true,
                    )
                )

            vm.state.loadFiles(listOf(file))
            vm.startBatchOrientationDetection(this)
            while (vm.isDetectingOrientation) {
                kotlinx.coroutines.yield()
            }
            // NONE results are auto-excluded
            assertThat(vm.rotationExcludedPaths).contains(file.absolutePath)
            vm.selectAllForRotation()
            vm.applyBatchRotationCorrection()

            assertThat(vm.state.fileConfigs[file.absolutePath]!!.config.rotationDegrees)
                .isEqualTo(0)
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
    @DisplayName("Thumbnail loading")
    inner class ThumbnailLoadingTests {
        private lateinit var folderThumbnailCache: FakeFolderThumbnailCache
        private lateinit var vm: MetadataEditorViewModel

        @Before
        fun setUpThumbnailVm() {
            folderThumbnailCache = FakeFolderThumbnailCache()
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
                    faceDetectionPort = mock(),
                    folderThumbnailCache = folderThumbnailCache,
                    localePort = stubLocalePort(),
                )
            vm.currentSettings = AppSettings()
        }

        @Test
        fun ensureThumbnailPopulatesMemoryCache() = runBlocking {
            // GIVEN
            val file = Files.createTempFile("thumb", ".jpg").toFile().apply { deleteOnExit() }
            folderThumbnailCache.thumbnailToReturn =
                BufferedImage(32, 24, BufferedImage.TYPE_INT_RGB).toProcessedImage()
            vm.state.loadFiles(listOf(file))
            vm.state.sourcePath = file.parent

            // WHEN
            vm.ensureThumbnail(file)

            // THEN
            assertThat(vm.thumbnailCache).containsKey(file.absolutePath)
            assertThat(vm.thumbnailCache[file.absolutePath]?.width).isEqualTo(32)
            assertThat(vm.thumbnailCacheRevision).isEqualTo(1)
            assertThat(folderThumbnailCache.getThumbnailCalls)
                .containsExactly(FilePath(file.absolutePath))
        }

        @Test
        fun ensureThumbnailSkipsWhenAlreadyCached() = runBlocking {
            // GIVEN
            val file =
                Files.createTempFile("cached-thumb", ".jpg").toFile().apply { deleteOnExit() }
            vm.thumbnailCache[file.absolutePath] = BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB)
            vm.state.loadFiles(listOf(file))

            // WHEN
            vm.ensureThumbnail(file)

            // THEN
            assertThat(folderThumbnailCache.getThumbnailCalls).isEmpty()
        }

        @Test
        fun onFilesLoadedClearsMemoryCacheAndReconciles() = runBlocking {
            // GIVEN
            val file = Files.createTempFile("reconcile", ".jpg").toFile().apply { deleteOnExit() }
            vm.thumbnailCache[file.absolutePath] = BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB)
            val revisionBefore = vm.thumbnailCacheRevision
            vm.state.loadFiles(listOf(file))
            vm.state.sourcePath = file.parent
            vm.currentSettings = AppSettings(metadataEditorDiskThumbnailCache = true)

            // WHEN
            vm.onFilesLoaded(this)

            // THEN
            assertThat(vm.thumbnailCache).isEmpty()
            assertThat(vm.thumbnailCacheRevision).isEqualTo(revisionBefore + 1)
            assertThat(folderThumbnailCache.reconcileInvoked).isTrue()
            assertThat(folderThumbnailCache.reconciledSources)
                .containsExactly(FilePath(file.absolutePath))
            assertThat(folderThumbnailCache.reconciledEditorSource).isEqualTo(file.parent)
        }

        @Test
        fun clearDiskThumbnailCacheDeletesThumbsFolder() = runBlocking {
            // GIVEN
            val file =
                Files.createTempFile("clear-thumbs", ".jpg").toFile().apply { deleteOnExit() }
            vm.state.loadFiles(listOf(file))
            vm.state.sourcePath = file.parent
            vm.thumbnailCache[file.absolutePath] = BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB)

            // WHEN
            vm.clearDiskThumbnailCache(this)

            // THEN
            assertThat(vm.thumbnailCache).isEmpty()
            assertThat(folderThumbnailCache.deletedThumbsRoots)
                .containsExactly(FilePath(file.parent))
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

    @Nested
    @DisplayName("Save invalidates thumbnails")
    inner class SaveInvalidatesThumbnailTests {
        private lateinit var folderThumbnailCache: FakeFolderThumbnailCache
        private lateinit var editService: MetadataEditService
        private lateinit var vm: MetadataEditorViewModel

        @Before
        fun setUpSaveVm() {
            folderThumbnailCache = FakeFolderThumbnailCache()
            editService = mock()
            vm =
                MetadataEditorViewModel(
                    dispatcherProvider = TestDispatcherProvider(),
                    imageRepository = mock(),
                    imageProcessing = mock(),
                    locationSearchService = mock(),
                    geocodingPort = mock(),
                    settingsPort = stubSettingsPort(),
                    editService = editService,
                    undoService = mock(),
                    faceRegionTransformer = mock(),
                    fileSystemAdapter = mock(),
                    orientationCorrection = mock(),
                    modelDownloadPort = mock(),
                    faceDetectionPort = mock(),
                    folderThumbnailCache = folderThumbnailCache,
                    localePort = stubLocalePort(),
                )
            vm.currentSettings = AppSettings(metadataEditorDiskThumbnailCache = true)
        }

        @Test
        fun saveCurrentFileInvalidatesMemoryAndDiskCache() = runBlocking {
            // GIVEN
            val file = Files.createTempFile("save-thumb", ".jpg").toFile().apply { deleteOnExit() }
            whenever(editService.saveFile(any(), any(), any(), any()))
                .thenReturn(
                    MetadataEditService.SaveResult(
                        entry =
                            MetadataEditEntry(
                                filePath = file.absolutePath,
                                backupPath = "/tmp/backup",
                            )
                    )
                )
            whenever(editService.saveJournal(any(), any(), any())).thenReturn("/tmp/journal.json")
            vm.state.loadFiles(listOf(file))
            vm.state.sourcePath = file.parent
            vm.thumbnailCache[file.absolutePath] = BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB)

            // WHEN — disk cache enabled
            vm.saveCurrentFile(this)

            // THEN
            assertThat(vm.thumbnailCache).doesNotContainKey(file.absolutePath)
            assertThat(folderThumbnailCache.invalidatedPaths)
                .containsExactly(FilePath(file.absolutePath))
        }

        @Test
        fun saveAllModifiedInvalidatesEachSavedFile() = runBlocking {
            // GIVEN
            val fileA = Files.createTempFile("save-a", ".jpg").toFile().apply { deleteOnExit() }
            val fileB = Files.createTempFile("save-b", ".jpg").toFile().apply { deleteOnExit() }
            whenever(editService.saveFile(any(), any(), any(), any())).thenAnswer { inv ->
                val savedFile = inv.getArgument<File>(0)
                MetadataEditService.SaveResult(
                    entry =
                        MetadataEditEntry(
                            filePath = savedFile.absolutePath,
                            backupPath = "/tmp/${savedFile.name}.bak",
                        )
                )
            }
            whenever(editService.saveJournal(any(), any(), any())).thenReturn("/tmp/journal.json")
            vm.state.loadFiles(listOf(fileA, fileB))
            vm.state.sourcePath = fileA.parent
            vm.state.updateConfig(0) { it.copy(description = "a") }
            vm.state.updateConfig(1) { it.copy(description = "b") }
            vm.thumbnailCache[fileA.absolutePath] =
                BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB)
            vm.thumbnailCache[fileB.absolutePath] =
                BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB)

            // WHEN
            vm.saveAllModified(this)

            // THEN
            assertThat(vm.thumbnailCache).isEmpty()
            assertThat(folderThumbnailCache.invalidatedPaths)
                .containsExactlyInAnyOrder(
                    FilePath(fileA.absolutePath),
                    FilePath(fileB.absolutePath),
                )
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

    @Nested
    @DisplayName("ViewModel selection and layout")
    inner class ViewModelBehaviorTests {
        private lateinit var vm: MetadataEditorViewModel

        @Before
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
                    faceDetectionPort = mock(),
                    folderThumbnailCache = mock(),
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
        fun toggleSelectionSyncsPrimaryIndexInMultiEditMode() {
            // GIVEN
            vm.state.loadFiles(listOf(File("/tmp/a.jpg"), File("/tmp/b.jpg"), File("/tmp/c.jpg")))
            vm.isMultiEditMode = true
            vm.selectedIndices = setOf(0, 2)

            // WHEN
            vm.toggleSelection(2)

            // THEN
            assertThat(vm.selectedIndices).containsExactly(0)
            assertThat(vm.state.selectedIndex).isEqualTo(0)
        }

        @Test
        fun setFileViewModeUpdatesSettingsOnly() {
            // GIVEN
            var captured: AppSettings? = null

            // WHEN
            vm.setFileViewMode(MetadataEditorFileViewMode.COLUMN) { captured = it }

            // THEN
            assertThat(captured?.metadataEditorFileViewMode)
                .isEqualTo(MetadataEditorFileViewMode.COLUMN)

            // WHEN
            vm.setFileViewMode(MetadataEditorFileViewMode.ICONS) { captured = it }

            // THEN
            assertThat(captured?.metadataEditorFileViewMode)
                .isEqualTo(MetadataEditorFileViewMode.ICONS)
        }

        @Test
        fun handleBrowserKeyNavigatesFoldersAndFilesInListView() {
            // GIVEN
            vm.state.editingActive = true
            vm.state.sourcePath = "/tmp/album"
            vm.state.loadFiles(listOf(File("/tmp/album/a.jpg"), File("/tmp/album/nested/b.jpg")))
            vm.state.selectFile(0)
            val nestedPath = File("/tmp/album/nested").absolutePath

            // WHEN — move focus to the subfolder
            val movedToFolder =
                vm.handleBrowserKey(Key.DirectionUp, MetadataEditorFileViewMode.LIST)

            // THEN
            assertThat(movedToFolder).isTrue()
            assertThat(vm.browserFocusedFolderPath).isEqualTo(nestedPath)

            // WHEN — open the focused folder
            val openedFolder =
                vm.handleBrowserKey(Key.DirectionRight, MetadataEditorFileViewMode.LIST)

            // THEN
            assertThat(openedFolder).isTrue()
            assertThat(vm.browserFolderPathStack).containsExactly(nestedPath)
            assertThat(vm.browserFocusedFolderPath).isNull()

            // WHEN — select the file inside the folder
            val movedToFile =
                vm.handleBrowserKey(Key.DirectionDown, MetadataEditorFileViewMode.LIST)

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
            val handled =
                vm.handleBrowserKey(Key.DirectionDown, MetadataEditorFileViewMode.HIERARCHY)

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
            vm.state.loadFiles(listOf(File("/tmp/album/a.jpg"), File("/tmp/album/nested/b.jpg")))
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
            runBlocking { vm.loadSelectedFiles(listOf(tempFile.absolutePath), this) {} }

            // THEN
            assertThat(vm.browserFolderPathStack).isEmpty()
            assertThat(vm.browserFocusedFolderPath).isNull()
        }

        @Test
        fun applyMultiEditWithEmptySelectionShowsError() {
            // GIVEN
            vm.state.loadFiles(listOf(File("/tmp/a.jpg")))
            vm.isMultiEditMode = true
            vm.selectedIndices = emptySet()

            // WHEN
            vm.applyMultiEdit {}

            // THEN
            assertThat(vm.state.message?.severity).isEqualTo(MessageSeverity.ERROR)
        }

        @Test
        fun onLocationSelectedBuffersEditStateInMultiEditMode() {
            // GIVEN
            vm.isMultiEditMode = true
            vm.locationPickerTargetIndices = listOf(0)
            val result =
                org.kryspetrie.fileimport.domain.model.LocationResult(
                    name = "Central Park",
                    displayName = "Central Park, NY",
                    latitude = 40.7829,
                    longitude = -73.9654,
                    city = "New York",
                    state = "NY",
                    country = "USA",
                )

            // WHEN
            vm.onLocationSelected(result)

            // THEN
            assertThat(vm.editState.locationName).isEqualTo("Central Park")
            assertThat(vm.editState.city).isEqualTo("New York")
            assertThat(vm.editState.gpsLatitude).isEqualTo("40.7829")
            assertThat(vm.showLocationPicker).isFalse()
        }

        @Test
        fun toggleBrowserDrawerPersistsPreference() {
            // GIVEN
            var captured: AppSettings? = null
            vm.browserDrawerOpen = true

            // WHEN
            vm.toggleBrowserDrawer { captured = it }

            // THEN
            assertThat(vm.browserDrawerOpen).isFalse()
            assertThat(captured?.metadataEditorLayoutPreferences?.browserDrawerOpen).isFalse()
        }

        @Test
        fun requestKeywordsFocusIncrementsTrigger() {
            // GIVEN
            val before = vm.keywordsFocusTrigger

            // WHEN
            vm.requestKeywordsFocus()

            // THEN
            assertThat(vm.keywordsFocusTrigger).isEqualTo(before + 1)
        }

        @Test
        fun confirmFaceNameMergesSubjectsIntoEditState() {
            // GIVEN
            vm.state.loadFiles(listOf(File("/tmp/a.jpg")))
            vm.faceNameInput = "Alice"
            vm.pendingFaceCoords = Triple(0, 0.5, 0.5)

            // WHEN
            vm.confirmFaceName()

            // THEN
            assertThat(vm.state.selectedConfig.subjects).contains("Alice")
            assertThat(vm.editState.subjects).contains("Alice")
        }

        @Test
        fun dismissFaceTaggingSyncsEditStateSubjects() {
            // GIVEN
            vm.state.loadFiles(listOf(File("/tmp/a.jpg")))
            vm.faceRegionMutator.addFaceRegion(0, "Eve", 0.5, 0.5)
            vm.showFaceTagging = true
            vm.editState.subjects = ""

            // WHEN
            vm.dismissFaceTagging()

            // THEN
            assertThat(vm.showFaceTagging).isFalse()
            assertThat(vm.editState.subjects).contains("Eve")
        }

        @Test
        fun handleMetadataShortcutToggleBrowserDrawerPersistsPreference() {
            // GIVEN
            var captured: AppSettings? = null
            vm.browserDrawerOpen = true

            // WHEN
            val handled =
                vm.handleMetadataShortcut(
                    MetadataEditorShortcutAction.TOGGLE_BROWSER_DRAWER,
                    onSettingsChange = { captured = it },
                    scope =
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
                )

            // THEN
            assertThat(handled).isTrue()
            assertThat(vm.browserDrawerOpen).isFalse()
            assertThat(captured?.metadataEditorLayoutPreferences?.browserDrawerOpen).isFalse()
        }

        @Test
        fun handleMetadataShortcutFocusKeywordsRequestsFocus() {
            // GIVEN
            val before = vm.keywordsFocusTrigger

            // WHEN
            val handled =
                vm.handleMetadataShortcut(
                    MetadataEditorShortcutAction.FOCUS_KEYWORDS,
                    onSettingsChange = {},
                    scope =
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
                )

            // THEN
            assertThat(handled).isTrue()
            assertThat(vm.keywordsFocusTrigger).isEqualTo(before + 1)
        }

        @Test
        fun loadSelectedFilesLoadsMetadataEditableImages() {
            val tempFile = Files.createTempFile("metadata-editor", ".jpg").toFile()
            tempFile.deleteOnExit()

            runBlocking { vm.loadSelectedFiles(listOf(tempFile.absolutePath), this) {} }

            assertThat(vm.state.editingActive).isTrue()
            assertThat(vm.state.fileCount).isEqualTo(1)
            assertThat(vm.state.selectedFile?.absolutePath).isEqualTo(tempFile.absolutePath)
            assertThat(vm.isMultiEditMode).isFalse()
        }

        @Test
        fun loadSelectedFilesIgnoresUnsupportedExtensions() {
            val tempFile = Files.createTempFile("metadata-editor", ".mp4").toFile()
            tempFile.deleteOnExit()

            runBlocking { vm.loadSelectedFiles(listOf(tempFile.absolutePath), this) {} }

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

private class FakeFolderThumbnailCache : FolderThumbnailCachePort {
    val getThumbnailCalls = mutableListOf<FilePath>()
    val invalidatedPaths = mutableListOf<FilePath>()
    var thumbnailToReturn: ProcessedImage? = null
    var reconcileInvoked = false
    var reconciledSources: List<FilePath> = emptyList()
    var reconciledEditorSource: String? = null
    var deletedThumbsRoots = mutableListOf<FilePath>()

    override suspend fun reconcileSources(
        sourceFiles: List<FilePath>,
        editorSourcePath: String?,
        maxPx: Int,
        diskCacheEnabled: Boolean,
    ) {
        if (!diskCacheEnabled) return
        reconcileInvoked = true
        reconciledSources = sourceFiles
        reconciledEditorSource = editorSourcePath
    }

    override suspend fun getThumbnail(
        sourceFile: FilePath,
        editorSourcePath: String?,
        maxPx: Int,
        diskCacheEnabled: Boolean,
    ): ProcessedImage? {
        getThumbnailCalls.add(sourceFile)
        return thumbnailToReturn
    }

    override suspend fun invalidate(sourceFile: FilePath, editorSourcePath: String?, maxPx: Int) {
        invalidatedPaths.add(sourceFile)
    }

    override suspend fun invalidateSources(sourceFiles: List<FilePath>, libraryRoot: String?) {
        invalidatedPaths.addAll(sourceFiles)
    }

    override suspend fun deleteThumbsFolder(libraryRoot: FilePath) {
        deletedThumbsRoots.add(libraryRoot)
    }
}
