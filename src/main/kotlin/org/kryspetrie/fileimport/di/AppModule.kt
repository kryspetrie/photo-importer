package org.kryspetrie.fileimport.di

import org.koin.dsl.module
import org.kryspetrie.fileimport.application.DuplicateScannerService
import org.kryspetrie.fileimport.application.ImportExecutor
import org.kryspetrie.fileimport.application.ImportScanner
import org.kryspetrie.fileimport.application.ImportService
import org.kryspetrie.fileimport.application.LocationSearchService
import org.kryspetrie.fileimport.application.PhotoScanExportService
import org.kryspetrie.fileimport.application.export.BackImageService
import org.kryspetrie.fileimport.application.export.JpegImageWriter
import org.kryspetrie.fileimport.application.export.MetadataWritingService
import org.kryspetrie.fileimport.application.FileOperationExecutor
import org.kryspetrie.fileimport.application.ReorganizeJournalRepository
import org.kryspetrie.fileimport.application.ReorganizeService
import org.kryspetrie.fileimport.application.ScanService
import org.kryspetrie.fileimport.application.WatchFolderService
import org.kryspetrie.fileimport.domain.port.DeduplicationPort
import org.kryspetrie.fileimport.domain.port.DevicePort
import org.kryspetrie.fileimport.domain.port.DispatcherProvider
import org.kryspetrie.fileimport.domain.port.FaceDetectionPort
import org.kryspetrie.fileimport.domain.port.FaceRegionTransformerPort
import org.kryspetrie.fileimport.domain.port.FileSystemPort
import org.kryspetrie.fileimport.domain.port.GeocodingPort
import org.kryspetrie.fileimport.domain.port.HashCachePort
import org.kryspetrie.fileimport.domain.port.IdGenerator
import org.kryspetrie.fileimport.domain.port.ImageRepositoryPort
import org.kryspetrie.fileimport.domain.port.ImportHistoryPort
import org.kryspetrie.fileimport.domain.port.LocationSearchPort
import org.kryspetrie.fileimport.domain.port.ModelResourcePort
import org.kryspetrie.fileimport.domain.port.NamingPort
import org.kryspetrie.fileimport.domain.port.PerspectiveCorrectionPort
import org.kryspetrie.fileimport.domain.port.PhotoScanDetectorPort
import org.kryspetrie.fileimport.domain.port.PhotoScanExportPort
import org.kryspetrie.fileimport.domain.port.SettingsPort
import org.kryspetrie.fileimport.domain.port.TimeProvider
import org.kryspetrie.fileimport.infrastructure.adapter.ClasspathModelResourceAdapter
import org.kryspetrie.fileimport.infrastructure.adapter.DeduplicationAdapter
import org.kryspetrie.fileimport.infrastructure.adapter.SurfDeduplicationService
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
import org.kryspetrie.fileimport.infrastructure.adapter.SettingsAdapter
import org.kryspetrie.fileimport.infrastructure.logging.AppLogger
import org.kryspetrie.fileimport.infrastructure.photoscan.FaceDetectionService
import org.kryspetrie.fileimport.infrastructure.photoscan.FaceRegionTransformer
import org.kryspetrie.fileimport.infrastructure.photoscan.HybridCornerDetector
import org.kryspetrie.fileimport.infrastructure.photoscan.PerspectiveCorrectionService
import org.kryspetrie.fileimport.infrastructure.photoscan.PhotoScanDetectorService
import org.kryspetrie.fileimport.infrastructure.photoscan.RectangleDetector

/**
 * Koin DI module for the Petrie File Importer application.
 *
 * Registers all domain port implementations, application services, and infrastructure components.
 * See [docs/ARCHITECTURE.md](../../../docs/ARCHITECTURE.md) for the full dependency graph and
 * design rationale.
 */
val appModule = module {
    // ── Domain Ports → Adapter Implementations ──────────────────────

    single<ImageRepositoryPort> { ImageRepositoryAdapter(dispatcherProvider = get()) }
    single<SettingsPort> { SettingsAdapter(timeProvider = get()) }
    single<NamingPort> { NamingAdapter() }
    single { SurfDeduplicationService(dispatcherProvider = get()) }
    single<DeduplicationPort> { DeduplicationAdapter(surfService = get(), dispatcherProvider = get()) }
    single<HashCachePort> { HashCacheAdapter(dispatcherProvider = get(), timeProvider = get()) }
    single<FileSystemPort> { FileSystemAdapter() }
    single<DevicePort> { DeviceAdapter(dispatcherProvider = get()) }
    single<ModelResourcePort> { ClasspathModelResourceAdapter() }
    single<TimeProvider> { DefaultTimeProvider() }
    single<IdGenerator> { DefaultIdGenerator() }
    single<DispatcherProvider> { DefaultDispatcherProvider() }

    // ── Infrastructure ──────────────────────────────────────────────

    single<GeocodingPort> { NominatimGeocodingAdapter(dispatcherProvider = get()) }

    single<ImportHistoryPort> { ImportHistoryAdapter(dispatcherProvider = get()) }
    single { AppLogger() }

    // ── Application Services ────────────────────────────────────────

    single { ImportScanner(imageRepository = get(), hashCache = get(), dispatcherProvider = get()) }
    single { ImportExecutor(imageRepository = get(), namingPort = get(), timeProvider = get()) }
    single {
        ImportService(
            importScanner = get(),
            importExecutor = get(),
            deduplicationPort = get(),
            namingPort = get(),
            devicePort = get(),
        )
    }
    single { ReorganizeJournalRepository() }
    single { FileOperationExecutor(get(), get()) }
    single { ReorganizeService(get(), get(), get(), get(), get(), get(), get()) }
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
        WatchFolderService(importService = get(), timeProvider = get(), dispatcherProvider = get())
    }

    // ── Photo Scan Pipeline ─────────────────────────────────────────

    single { RectangleDetector() }
    single { HybridCornerDetector(rectangleDetector = get()) }
    single<PhotoScanDetectorPort> { get<PhotoScanDetectorService>() }
    single { PhotoScanDetectorService(modelResourcePort = get(), appLogger = getOrNull()) }
    single<FaceDetectionPort> { FaceDetectionService(modelResourcePort = get()) }
    single { ScanService(photoDetector = get()) }
    single<PerspectiveCorrectionPort> { PerspectiveCorrectionService() }
    single { PerspectiveCorrectionService() }
    single<FaceRegionTransformerPort> { FaceRegionTransformer() }
    single { FaceRegionTransformer() }
    single { JpegImageWriter() }
    single { BackImageService() }
    single { MetadataWritingService(faceRegionTransformer = get<FaceRegionTransformerPort>()) }
    single<PhotoScanExportPort> { get<PhotoScanExportService>() }
    single { PhotoScanExportService(get(), get(), get(), get()) }

    // ── Location Search ─────────────────────────────────────────────

    single<LocationSearchPort> {
        LocationSearchService(geocodingPort = get(), dispatcherProvider = get())
    }
    single { LocationSearchService(geocodingPort = get(), dispatcherProvider = get()) }
}
