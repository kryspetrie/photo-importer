package org.kryspetrie.fileimport.ui.screens.wizard

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.kryspetrie.fileimport.application.OrientationCorrectionService
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.port.DispatcherProvider
import org.kryspetrie.fileimport.domain.port.FaceDetectionPort
import org.kryspetrie.fileimport.domain.port.FaceRegionTransformerPort
import org.kryspetrie.fileimport.domain.port.ImageProcessingPort
import org.kryspetrie.fileimport.domain.port.LocalePort
import org.kryspetrie.fileimport.domain.port.PathsPort
import org.kryspetrie.fileimport.domain.port.PerspectiveCorrectionPort
import org.kryspetrie.fileimport.domain.port.PhotoScanDetectorPort
import org.kryspetrie.fileimport.domain.port.PhotoScanExportPort
import org.kryspetrie.fileimport.domain.port.SettingsPort
import org.kryspetrie.fileimport.infrastructure.logging.AppLogger
import org.kryspetrie.fileimport.ui.components.PreviewCache

/**
 * Holds Photo Scan wizard dependencies and side-effect helpers so [WizardContainer] stays a thin
 * composable shell (mirrors [org.kryspetrie.fileimport.ui.screens.MediaImportViewModel]).
 */
class WizardContainerViewModel(
    val detectorService: PhotoScanDetectorPort,
    val exportService: PhotoScanExportPort,
    val perspectiveService: PerspectiveCorrectionPort,
    val appLogger: AppLogger,
    val settingsPort: SettingsPort,
    val dispatcherProvider: DispatcherProvider,
    val faceRegionTransformer: FaceRegionTransformerPort,
    val faceDetectionPort: FaceDetectionPort,
    val orientationCorrection: OrientationCorrectionService,
    val imageProcessing: ImageProcessingPort,
    val pathsPort: PathsPort,
    val localePort: LocalePort,
) {
    val previewCache = PreviewCache(perspectiveService)

    fun initialExportDestination(settings: AppSettings): String =
        settings.photoScanImportTabSettings.lastDestinationPath.ifBlank {
            pathsPort.defaultDestination
        }

    suspend fun preloadModels() {
        withContext(dispatcherProvider.io) {
            try {
                detectorService.preload()
            } catch (_: Exception) {
                // Best-effort; first detection still works
            }
            try {
                faceDetectionPort.preload()
            } catch (_: Exception) {
                // Face model is optional
            }
        }
    }

    suspend fun persistExportDestination(destination: String) {
        val currentSettings = settingsPort.observeSettings().first()
        settingsPort.saveSettings(
            currentSettings.withPhotoScanImportTabSettings(
                currentSettings.photoScanImportTabSettings.withRecentDestinationPath(destination)
            )
        )
    }

    suspend fun saveSettings(settings: AppSettings) {
        settingsPort.saveSettings(settings)
    }

    suspend fun addMetadataHistory(fieldKey: String, value: String) {
        val current = settingsPort.observeSettings().first()
        settingsPort.saveSettings(current.addMetadataHistory(fieldKey, value))
    }

    suspend fun removeMetadataHistory(fieldKey: String, value: String) {
        val current = settingsPort.observeSettings().first()
        settingsPort.saveSettings(current.removeMetadataHistory(fieldKey, value))
    }

    suspend fun recordMetadataSet(set: org.kryspetrie.fileimport.domain.model.RecentMetadataSet) {
        val current = settingsPort.observeSettings().first()
        settingsPort.saveSettings(current.addMetadataSet(set))
    }
}
