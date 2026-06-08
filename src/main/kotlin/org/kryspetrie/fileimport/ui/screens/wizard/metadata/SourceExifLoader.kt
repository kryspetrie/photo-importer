package org.kryspetrie.fileimport.ui.screens.wizard.metadata

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import java.io.File
import kotlinx.coroutines.withContext
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.ImageFile
import org.kryspetrie.fileimport.domain.port.DispatcherProvider
import org.kryspetrie.fileimport.domain.port.ImageRepositoryPort
import org.kryspetrie.fileimport.infrastructure.wizard.PhotoScanWizardState
import org.kryspetrie.fileimport.infrastructure.wizard.SourceExifSummary

/**
 * Loads source EXIF metadata from the current image file when entering a metadata/edit screen. Only
 * loads once per source file (skips if sourceExif is already populated).
 */
@Composable
fun LoadSourceExifEffect(
    imageFile: File?,
    sourceExif: SourceExifSummary?,
    state: PhotoScanWizardState,
    imageRepository: ImageRepositoryPort,
    dispatcherProvider: DispatcherProvider,
) {
    LaunchedEffect(imageFile) {
        if (imageFile != null && sourceExif == null) {
            try {
                val meta =
                    withContext(dispatcherProvider.io) {
                        imageRepository.getMetadata(
                            ImageFile(
                                path = FilePath(imageFile.absolutePath),
                                fileSize = imageFile.length(),
                            )
                        )
                    }
                if (meta != null) {
                    state.setSourceExif(
                        SourceExifSummary(
                            cameraMake = meta.make,
                            cameraModel = meta.model,
                            lensModel = meta.lensModel,
                            focalLength = meta.focalLength?.let { "${it}mm" },
                            aperture = meta.aperture?.let { "f/$it" },
                            shutterSpeed = meta.shutterSpeed,
                            iso = meta.iso?.toString(),
                            description = meta.description,
                            dateOriginal = meta.dateTimeOriginal?.toString(),
                            gpsLatitude = meta.latitude?.toString(),
                            gpsLongitude = meta.longitude?.toString(),
                        )
                    )
                }
            } catch (_: Exception) {
                // Source EXIF read failed — leave sourceExif as null, UI will show no hints
            }
        }
    }
}
