package org.kryspetrie.fileimport.di

import org.koin.dsl.module
import org.kryspetrie.fileimport.application.DuplicateScannerService
import org.kryspetrie.fileimport.application.FileOperationExecutor
import org.kryspetrie.fileimport.application.ImportExecutor
import org.kryspetrie.fileimport.application.ImportScanner
import org.kryspetrie.fileimport.application.ImportService
import org.kryspetrie.fileimport.application.LocationSearchService
import org.kryspetrie.fileimport.application.OrientationCorrectionService
import org.kryspetrie.fileimport.application.PhotoScanExportService
import org.kryspetrie.fileimport.application.ReorganizeJournalRepository
import org.kryspetrie.fileimport.application.ReorganizeService
import org.kryspetrie.fileimport.application.ScanService
import org.kryspetrie.fileimport.application.WatchFolderManager
import org.kryspetrie.fileimport.application.WatchFolderService
import org.kryspetrie.fileimport.application.export.MetadataWritingService
import org.kryspetrie.fileimport.application.metadata.MetadataEditJournalRepository
import org.kryspetrie.fileimport.application.metadata.MetadataEditService
import org.kryspetrie.fileimport.application.metadata.MetadataEditUndoService
import org.kryspetrie.fileimport.domain.port.DeduplicationPort
import org.kryspetrie.fileimport.domain.port.DevicePort
import org.kryspetrie.fileimport.domain.port.DispatcherProvider
import org.kryspetrie.fileimport.domain.port.FaceDetectionPort
import org.kryspetrie.fileimport.domain.port.FaceRegionTransformerPort
import org.kryspetrie.fileimport.domain.port.FileSystemPort
import org.kryspetrie.fileimport.domain.port.FolderThumbnailCachePort
import org.kryspetrie.fileimport.domain.port.GeocodingPort
import org.kryspetrie.fileimport.domain.port.HashCachePort
import org.kryspetrie.fileimport.domain.port.IdGenerator
import org.kryspetrie.fileimport.domain.port.ImageProcessingPort
import org.kryspetrie.fileimport.domain.port.ImageRepositoryPort
import org.kryspetrie.fileimport.domain.port.ImportHistoryPort
import org.kryspetrie.fileimport.domain.port.LocalePort
import org.kryspetrie.fileimport.domain.port.LocationSearchPort
import org.kryspetrie.fileimport.domain.port.ModelDownloadPort
import org.kryspetrie.fileimport.domain.port.ModelResourcePort
import org.kryspetrie.fileimport.domain.port.NamingPort
import org.kryspetrie.fileimport.domain.port.OrientationDetectionPort
import org.kryspetrie.fileimport.domain.port.PathsPort
import org.kryspetrie.fileimport.domain.port.PerspectiveCorrectionPort
import org.kryspetrie.fileimport.domain.port.PhotoScanDetectorPort
import org.kryspetrie.fileimport.domain.port.PhotoScanExportPort
import org.kryspetrie.fileimport.domain.port.PlatformPort
import org.kryspetrie.fileimport.domain.port.SettingsPort
import org.kryspetrie.fileimport.domain.port.ThumbnailExtractorPort
import org.kryspetrie.fileimport.domain.port.TimeProvider
import org.kryspetrie.fileimport.infrastructure.adapter.AwtImageProcessingAdapter
import org.kryspetrie.fileimport.infrastructure.adapter.ClasspathModelResourceAdapter
import org.kryspetrie.fileimport.infrastructure.adapter.DeduplicationAdapter
import org.kryspetrie.fileimport.infrastructure.adapter.DefaultDispatcherProvider
import org.kryspetrie.fileimport.infrastructure.adapter.DefaultIdGenerator
import org.kryspetrie.fileimport.infrastructure.adapter.DefaultTimeProvider
import org.kryspetrie.fileimport.infrastructure.adapter.DeviceAdapter
import org.kryspetrie.fileimport.infrastructure.adapter.FileSystemAdapter
import org.kryspetrie.fileimport.infrastructure.adapter.HashCacheAdapter
import org.kryspetrie.fileimport.infrastructure.adapter.ImageRepositoryAdapter
import org.kryspetrie.fileimport.infrastructure.adapter.ImportHistoryAdapter
import org.kryspetrie.fileimport.infrastructure.adapter.NamingAdapter
import org.kryspetrie.fileimport.infrastructure.adapter.NominatimGeocodingAdapter
import org.kryspetrie.fileimport.infrastructure.adapter.OrtSessionFactory
import org.kryspetrie.fileimport.infrastructure.adapter.PathsAdapter
import org.kryspetrie.fileimport.infrastructure.adapter.PlatformInfoAdapter
import org.kryspetrie.fileimport.infrastructure.adapter.SettingsAdapter
import org.kryspetrie.fileimport.infrastructure.adapter.SurfDeduplicationService
import org.kryspetrie.fileimport.infrastructure.adapter.ThumbnailExtractorAdapter
import org.kryspetrie.fileimport.infrastructure.download.HuggingFaceModelDownloadAdapter
import org.kryspetrie.fileimport.infrastructure.i18n.JsonLocaleAdapter
import org.kryspetrie.fileimport.infrastructure.logging.AppLogger
import org.kryspetrie.fileimport.infrastructure.photoscan.FaceDetectionService
import org.kryspetrie.fileimport.infrastructure.photoscan.FaceRegionTransformer
import org.kryspetrie.fileimport.infrastructure.photoscan.HybridCornerDetector
import org.kryspetrie.fileimport.infrastructure.photoscan.OrientationDetectionService
import org.kryspetrie.fileimport.infrastructure.photoscan.PerspectiveCorrectionService
import org.kryspetrie.fileimport.infrastructure.photoscan.PhotoScanDetectorService
import org.kryspetrie.fileimport.infrastructure.photoscan.RectangleDetector
import org.kryspetrie.fileimport.infrastructure.thumbnails.FolderThumbnailCacheAdapter
import org.kryspetrie.fileimport.ui.screens.MediaImportViewModel
import org.kryspetrie.fileimport.ui.screens.metadataeditor.MetadataEditorViewModel
import org.kryspetrie.fileimport.ui.screens.wizard.WizardContainerViewModel

/**
 * Koin DI module for the Petrie File Importer application.
 *
 * Registers all domain port implementations, application services, and infrastructure components.
 * See [docs/ARCHITECTURE.md](../../../docs/ARCHITECTURE.md) for the full dependency graph and
 * design rationale.
 */
val appModule = module {
    includes(metadataEditorIntegrationModule)

    // ── Domain Ports → Adapter Implementations ──────────────────────

    single<ImageRepositoryPort> { ImageRepositoryAdapter(dispatcherProvider = get()) }
    single<SettingsPort> { SettingsAdapter(timeProvider = get()) }
    single<NamingPort> { NamingAdapter() }
    single { SurfDeduplicationService(dispatcherProvider = get()) }
    single<DeduplicationPort> {
        DeduplicationAdapter(surfService = get(), dispatcherProvider = get())
    }
    single<HashCachePort> { HashCacheAdapter(dispatcherProvider = get(), timeProvider = get()) }
    single<FileSystemPort> { FileSystemAdapter() }
    single<ThumbnailExtractorPort> { ThumbnailExtractorAdapter }
    single<FolderThumbnailCachePort> {
        FolderThumbnailCacheAdapter(
            fileSystem = get(),
            thumbnailExtractor = get(),
            dispatcherProvider = get(),
        )
    }
    single<DevicePort> { DeviceAdapter(dispatcherProvider = get()) }
    single<ModelResourcePort> { ClasspathModelResourceAdapter() }

    // ── Model Download ────────────────────────────────────────────────────

    single<ModelDownloadPort> {
        HuggingFaceModelDownloadAdapter(dispatcherProvider = get(), appLogger = getOrNull())
    }
    single<TimeProvider> { DefaultTimeProvider() }
    single<IdGenerator> { DefaultIdGenerator() }
    single<DispatcherProvider> { DefaultDispatcherProvider() }

    // ── Localization ────────────────────────────────────────────────────

    single<LocalePort> { JsonLocaleAdapter(dispatchers = get(), appLogger = getOrNull()) }

    // ── Infrastructure ──────────────────────────────────────────────

    single<GeocodingPort> { NominatimGeocodingAdapter(dispatcherProvider = get()) }

    single<ImportHistoryPort> { ImportHistoryAdapter(dispatcherProvider = get()) }
    single { AppLogger() }

    // ── Application Services ────────────────────────────────────────

    single {
        ImportScanner(
            imageRepository = get(),
            hashCache = get(),
            dispatcherProvider = get(),
            fileSystem = get(),
        )
    }
    single {
        ImportExecutor(
            imageRepository = get(),
            namingPort = get(),
            timeProvider = get(),
            fileSystem = get(),
        )
    }
    single {
        ImportService(
            importScanner = get(),
            importExecutor = get(),
            deduplicationPort = get(),
            namingPort = get(),
            devicePort = get(),
        )
    }
    single { ReorganizeJournalRepository(get()) }
    single { FileOperationExecutor(get(), get()) }
    single { ReorganizeService(get(), get(), get(), get(), get(), get(), get(), get()) }
    single {
        DuplicateScannerService(
            imageRepository = get(),
            deduplicationPort = get(),
            fileSystem = get(),
            hashCache = get(),
            timeProvider = get(),
            dispatcherProvider = get(),
        )
    }
    single {
        WatchFolderService(
            importService = get(),
            timeProvider = get(),
            dispatcherProvider = get(),
            fileSystem = get(),
        )
    }
    single {
        WatchFolderManager(
            importService = get(),
            timeProvider = get(),
            dispatcherProvider = get(),
            fileSystem = get(),
            settingsPort = get(),
        )
    }

    // ── Photo Scan Pipeline ─────────────────────────────────────────

    single { OrtSessionFactory(appLogger = getOrNull()) }
    single { RectangleDetector() }
    single { HybridCornerDetector(rectangleDetector = get()) }
    single<PhotoScanDetectorPort> { get<PhotoScanDetectorService>() }
    single {
        PhotoScanDetectorService(
            modelResourcePort = get(),
            ortSessionFactory = get(),
            appLogger = getOrNull(),
        )
    }
    single<FaceDetectionPort> {
        FaceDetectionService(modelResourcePort = get(), ortSessionFactory = get())
    }
    single<OrientationDetectionPort> {
        OrientationDetectionService(modelResourcePort = get(), ortSessionFactory = get())
    }
    single {
        OrientationCorrectionService(
            orientationDetection = get<OrientationDetectionPort>(),
            imageProcessing = get<ImageProcessingPort>(),
        )
    }
    single<ImageProcessingPort> { AwtImageProcessingAdapter(get(), get()) }
    single { ScanService(photoDetector = get(), fileSystem = get(), imageProcessing = get()) }
    single<PerspectiveCorrectionPort> { PerspectiveCorrectionService() }
    single<FaceRegionTransformerPort> { FaceRegionTransformer() }
    single {
        MetadataWritingService(
            metadataEditor = get(),
            faceRegionTransformer = get<FaceRegionTransformerPort>(),
            imageProcessing = get<ImageProcessingPort>(),
        )
    }
    single<PhotoScanExportPort> { PhotoScanExportService(get(), get(), get(), get()) }

    // ── Metadata Edit Undo ──────────────────────────────────────────

    single { MetadataEditJournalRepository(get()) }
    single { MetadataEditUndoService(get(), get(), get()) }
    single { MetadataEditService(get(), get(), get(), get(), get()) }

    // ── Location Search ─────────────────────────────────────────────

    single<LocationSearchPort> {
        LocationSearchService(geocodingPort = get(), dispatcherProvider = get())
    }

    // ── Platform & Paths ────────────────────────────────────────────

    single<PathsPort> { PathsAdapter() }
    single<PlatformPort> { PlatformInfoAdapter() }

    // ── ViewModels ──────────────────────────────────────────────────

    single {
        MetadataEditorViewModel(
            dispatcherProvider = get(),
            imageRepository = get(),
            imageProcessing = get(),
            locationSearchService = get(),
            geocodingPort = get(),
            settingsPort = get(),
            editService = get(),
            undoService = get(),
            faceRegionTransformer = get(),
            fileSystemAdapter = get(),
            orientationCorrection = get(),
            modelDownloadPort = get(),
            faceDetectionPort = get(),
            folderThumbnailCache = get(),
            localePort = get(),
        )
    }
    single {
        MediaImportViewModel(
            importService = get(),
            devicePort = get(),
            historyPort = get(),
            settingsPort = get(),
            watchFolderManager = get(),
            timeProvider = get(),
            pathsPort = get(),
            localePort = get(),
        )
    }
    single {
        WizardContainerViewModel(
            detectorService = get(),
            exportService = get(),
            perspectiveService = get(),
            appLogger = get(),
            settingsPort = get(),
            dispatcherProvider = get(),
            faceRegionTransformer = get(),
            faceDetectionPort = get(),
            orientationCorrection = get(),
            imageProcessing = get(),
            pathsPort = get(),
            localePort = get(),
        )
    }
}
