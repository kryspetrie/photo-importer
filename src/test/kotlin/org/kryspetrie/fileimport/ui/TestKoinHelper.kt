package org.kryspetrie.fileimport.ui

import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.kryspetrie.fileimport.application.DuplicateScannerService
import org.kryspetrie.fileimport.application.ReorganizeService
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.domain.port.DispatcherProvider
import org.kryspetrie.fileimport.domain.port.LocalePort
import org.kryspetrie.fileimport.domain.port.ModelDownloadPort
import org.kryspetrie.fileimport.domain.port.ModelDownloadState
import org.kryspetrie.fileimport.domain.port.PathsPort
import org.kryspetrie.fileimport.domain.port.SettingsPort
import org.kryspetrie.fileimport.infrastructure.adapter.SettingsAdapter
import org.kryspetrie.fileimport.infrastructure.i18n.JsonLocaleAdapter
import org.kryspetrie.fileimport.ui.screens.MediaImportViewModel
import org.kryspetrie.fileimport.ui.screens.metadataeditor.MetadataEditorViewModel
import org.kryspetrie.fileimport.ui.screens.wizard.WizardContainerViewModel
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Helper to set up a minimal Koin test module for Compose UI tests.
 *
 * Usage:
 * ```
 * @BeforeEach fun setupKoin() { TestKoinHelper.start() }
 * @AfterEach fun tearDownKoin() { TestKoinHelper.stop() }
 * ```
 */
object TestKoinHelper {

    private val tmpDir = File(System.getProperty("java.io.tmpdir"), "test-koin-${System.nanoTime()}")

    /** Creates a real [SettingsAdapter] backed by a temp file. */
    fun createSettingsPort(): SettingsPort = SettingsAdapter(tmpDir)

    /** Creates a real [LocalePort] with English strings. */
    fun createLocalePort(): LocalePort =
        JsonLocaleAdapter(
            dispatchers =
                object : DispatcherProvider {
                    override val io = Dispatchers.Unconfined
                    override val default = Dispatchers.Unconfined
                },
            appLogger = null,
        )

    /** Creates a mock [ModelDownloadPort] that reports model as already downloaded. */
    fun createModelDownloadPort(): ModelDownloadPort {
        val port = mock<ModelDownloadPort>()
        whenever(port.isModelDownloaded(any())).thenReturn(true)
        return port
    }

    /** Creates a mock [PathsPort] returning a temp default destination. */
    fun createPathsPort(): PathsPort {
        val port = mock<PathsPort>()
        whenever(port.defaultDestination).thenReturn(tmpDir.absolutePath)
        return port
    }

    /** Creates a mock [ReorganizeService]. */
    fun createReorganizeService(): ReorganizeService = mock()

    /** Creates a mock [DuplicateScannerService]. */
    fun createDuplicateScannerService(): DuplicateScannerService = mock()

    /** Creates a mock [ImportService]. */
    fun createImportService(): org.kryspetrie.fileimport.application.ImportService = mock()

    /** Creates a mock [DevicePort]. */
    fun createDevicePort(): org.kryspetrie.fileimport.domain.port.DevicePort = mock()

    /** Creates a mock [ImportHistoryPort]. */
    fun createImportHistoryPort(): org.kryspetrie.fileimport.domain.port.ImportHistoryPort = mock()

    /** Creates a mock [WatchFolderManager]. */
    fun createWatchFolderManager(): org.kryspetrie.fileimport.application.WatchFolderManager {
        val statusesFlow = kotlinx.coroutines.flow.MutableStateFlow<Map<String, org.kryspetrie.fileimport.domain.model.WatchFolderStatus>>(emptyMap())
        val mock = mock<org.kryspetrie.fileimport.application.WatchFolderManager>()
        org.mockito.kotlin.whenever(mock.statuses).thenReturn(statusesFlow)
        return mock
    }

    /** Creates a mock [TimeProvider]. */
    fun createTimeProvider(): org.kryspetrie.fileimport.domain.port.TimeProvider = mock()

    /** Creates a mock [MediaImportViewModel]. */
    fun createMediaImportViewModel(): MediaImportViewModel {
        val statusesFlow = kotlinx.coroutines.flow.MutableStateFlow<Map<String, org.kryspetrie.fileimport.domain.model.WatchFolderStatus>>(emptyMap())
        val mock = mock<MediaImportViewModel>()
        // Mock the watchStatuses property getter
        org.mockito.kotlin.whenever(mock.watchStatuses).thenReturn(statusesFlow)
        // Mock sourcePath and destinationPath to return empty strings (not null)
        org.mockito.kotlin.whenever(mock.sourcePath).thenReturn("")
        org.mockito.kotlin.whenever(mock.destinationPath).thenReturn("")
        // Mock customConfig to return a non-null ImportConfiguration
        org.mockito.kotlin.whenever(mock.customConfig).thenReturn(org.kryspetrie.fileimport.domain.model.ImportConfiguration())
        return mock
    }

    /** Creates a mock [MetadataEditorViewModel]. */
    fun createMetadataEditorViewModel(): MetadataEditorViewModel = mock()

    /** Creates a mock [WizardContainerViewModel]. */
    fun createWizardContainerViewModel(): WizardContainerViewModel = mock()

    /** Creates a mock [MetadataEditService]. */
    fun createMetadataEditService(): org.kryspetrie.fileimport.application.metadata.MetadataEditService = mock()

    /** Creates a mock [MetadataEditUndoService]. */
    fun createMetadataEditUndoService(): org.kryspetrie.fileimport.application.metadata.MetadataEditUndoService = mock()

    /** Creates a mock [FaceDetectionPort]. */
    fun createFaceDetectionPort(): org.kryspetrie.fileimport.domain.port.FaceDetectionPort = mock()

    /** Creates a mock [FaceRegionTransformerPort]. */
    fun createFaceRegionTransformerPort(): org.kryspetrie.fileimport.domain.port.FaceRegionTransformerPort = mock()

    /** Creates a mock [FileSystemPort]. */
    fun createFileSystemPort(): org.kryspetrie.fileimport.domain.port.FileSystemPort = mock()

    /** Creates a mock [FolderThumbnailCachePort]. */
    fun createFolderThumbnailCachePort(): org.kryspetrie.fileimport.domain.port.FolderThumbnailCachePort = mock()

    /** Creates a mock [GeocodingPort]. */
    fun createGeocodingPort(): org.kryspetrie.fileimport.domain.port.GeocodingPort = mock()

    /** Creates a mock [ImageProcessingPort]. */
    fun createImageProcessingPort(): org.kryspetrie.fileimport.domain.port.ImageProcessingPort = mock()

    /** Creates a mock [ImageRepositoryPort]. */
    fun createImageRepositoryPort(): org.kryspetrie.fileimport.domain.port.ImageRepositoryPort = mock()

    /** Creates a mock [LocationSearchPort]. */
    fun createLocationSearchPort(): org.kryspetrie.fileimport.domain.port.LocationSearchPort = mock()

    /** Creates a mock [OrientationCorrectionService]. */
    fun createOrientationCorrectionService(): org.kryspetrie.fileimport.application.OrientationCorrectionService = mock()

    /**
     * Starts Koin with a minimal test module. Call in [org.junit.jupiter.api.BeforeEach].
     *
     * All ports/services are mocked by default. Override specific bindings by passing
     * additional module declarations.
     */
    fun start(vararg extraModules: org.koin.core.module.Module) {
        stopKoin()
        tmpDir.mkdirs()

        val baseModule =
            module {
                single<SettingsPort> { createSettingsPort() }
                single<LocalePort> { createLocalePort() }
                single<ModelDownloadPort> { createModelDownloadPort() }
                single<PathsPort> { createPathsPort() }
                single<ReorganizeService> { createReorganizeService() }
                single<DuplicateScannerService> { createDuplicateScannerService() }
                single<org.kryspetrie.fileimport.application.ImportService> { createImportService() }
                single<org.kryspetrie.fileimport.domain.port.DevicePort> { createDevicePort() }
                single<org.kryspetrie.fileimport.domain.port.ImportHistoryPort> { createImportHistoryPort() }
                single<org.kryspetrie.fileimport.application.WatchFolderManager> { createWatchFolderManager() }
                single<org.kryspetrie.fileimport.domain.port.TimeProvider> { createTimeProvider() }
                single<MediaImportViewModel> { createMediaImportViewModel() }
                single<MetadataEditorViewModel> { createMetadataEditorViewModel() }
                single<WizardContainerViewModel> { createWizardContainerViewModel() }
                single<org.kryspetrie.fileimport.application.metadata.MetadataEditService> { createMetadataEditService() }
                single<org.kryspetrie.fileimport.application.metadata.MetadataEditUndoService> { createMetadataEditUndoService() }
                single<org.kryspetrie.fileimport.domain.port.FaceDetectionPort> { createFaceDetectionPort() }
                single<org.kryspetrie.fileimport.domain.port.FaceRegionTransformerPort> { createFaceRegionTransformerPort() }
                single<org.kryspetrie.fileimport.domain.port.FileSystemPort> { createFileSystemPort() }
                single<org.kryspetrie.fileimport.domain.port.FolderThumbnailCachePort> { createFolderThumbnailCachePort() }
                single<org.kryspetrie.fileimport.domain.port.GeocodingPort> { createGeocodingPort() }
                single<org.kryspetrie.fileimport.domain.port.ImageProcessingPort> { createImageProcessingPort() }
                single<org.kryspetrie.fileimport.domain.port.ImageRepositoryPort> { createImageRepositoryPort() }
                single<org.kryspetrie.fileimport.domain.port.LocationSearchPort> { createLocationSearchPort() }
                single<org.kryspetrie.fileimport.application.OrientationCorrectionService> { createOrientationCorrectionService() }
            }

        startKoin {
            modules(listOf(baseModule) + extraModules)
        }
    }

    /** Stops Koin. Call in [org.junit.jupiter.api.AfterEach]. */
    fun stop() {
        stopKoin()
        tmpDir.deleteRecursively()
    }
}
