package org.kryspetrie.fileimport.ui.screens.metadataeditor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.ImageFile
import org.kryspetrie.fileimport.domain.model.MetadataEditorFileViewMode
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.infrastructure.adapter.toBufferedImage
import org.kryspetrie.fileimport.ui.components.isMetadataEditableFile
import org.kryspetrie.fileimport.ui.wizard.state.SourceExifSummary

/** Start observing settings from the settings port. Call once in a LaunchedEffect. */
internal fun MetadataEditorViewModel.observeSettings(scope: CoroutineScope) {
    scope.launch {
        settingsPort.observeSettings().collect { settings ->
            currentSettings = settings
            browserDrawerOpen = settings.metadataEditorLayoutPreferences.browserDrawerOpen
        }
    }
}

/**
 * Load a source path (file or folder). Includes subfolders if [state.includeSubfolders] is true.
 */
internal fun MetadataEditorViewModel.loadSource(
    path: String,
    onSettingsChange: (AppSettings) -> Unit,
) {
    state.isLoading = true
    state.message = null
    val source = File(path)
    if (source.isFile) {
        if (!isMetadataEditableFile(source)) {
            state.showError(t(StringKey.META_ERROR_NOT_EDITABLE, "path" to path))
            return
        }
        state.loadSingleFile(source)
        resetBrowserNavigation()
        state.editingActive = true
        onSettingsChange(currentSettings.withMetadataEditorRecentPath(path))
    } else if (source.isDirectory) {
        val imageFiles =
            runCatching { collectImageFiles(source, state.includeSubfolders) }
                .getOrDefault(emptyList())
        if (imageFiles.isEmpty()) {
            state.showError(t(StringKey.META_ERROR_NO_IMAGES_IN_PATH, "path" to path))
            return
        }
        state.sourcePath = path
        state.loadFiles(imageFiles)
        resetBrowserNavigation()
        state.editingActive = true
        onSettingsChange(currentSettings.withMetadataEditorRecentPath(path))
    } else {
        state.showError(t(StringKey.META_ERROR_PATH_NOT_FOUND, "path" to path))
    }
    state.isLoading = false
}

/** Load a source path asynchronously (for use from composable callbacks). */
internal fun MetadataEditorViewModel.loadSourceAsync(
    path: String,
    scope: CoroutineScope,
    onSettingsChange: (AppSettings) -> Unit,
) {
    state.isLoading = true
    state.message = null
    scope.launch {
        try {
            val source = File(path)
            if (source.isFile) {
                if (!isMetadataEditableFile(source)) {
                    state.showError(t(StringKey.META_ERROR_NOT_EDITABLE, "path" to path))
                    return@launch
                }
                state.loadSingleFile(source)
                resetBrowserNavigation()
                state.editingActive = true
                onSettingsChange(currentSettings.withMetadataEditorRecentPath(path))
            } else if (source.isDirectory) {
                val imageFiles =
                    withContext(dispatcherProvider.io) {
                        collectImageFiles(source, state.includeSubfolders)
                    }
                if (imageFiles.isEmpty()) {
                    state.showError(t(StringKey.META_ERROR_NO_IMAGES_IN_PATH, "path" to path))
                    return@launch
                }
                state.sourcePath = path
                state.loadFiles(imageFiles)
                resetBrowserNavigation()
                state.editingActive = true
                onSettingsChange(currentSettings.withMetadataEditorRecentPath(path))
            } else {
                state.showError(t(StringKey.META_ERROR_PATH_NOT_FOUND, "path" to path))
            }
        } catch (_: CancellationException) {
            // Cancellation must propagate
        } catch (e: Exception) {
            state.showError(t(StringKey.META_ERROR_LOAD, "message" to (e.message ?: "")))
        } finally {
            state.isLoading = false
        }
    }
}

/** Load explicitly selected image files from a native multi-select dialog. */
internal fun MetadataEditorViewModel.loadSelectedFiles(
    paths: List<String>,
    scope: CoroutineScope,
    onSettingsChange: (AppSettings) -> Unit,
) {
    if (paths.isEmpty()) return
    state.isLoading = true
    state.message = null
    scope.launch {
        try {
            val imageFiles =
                withContext(dispatcherProvider.io) {
                    paths.map { File(it) }.filter { it.isFile && isMetadataEditableFile(it) }
                }
            if (imageFiles.isEmpty()) {
                state.showError(t(StringKey.META_ERROR_NO_EDITABLE_SELECTION))
                return@launch
            }
            val displayPath =
                if (imageFiles.size == 1) {
                    imageFiles.first().absolutePath
                } else
                    t(StringKey.META_SELECTED_IMAGES_SUMMARY, "count" to imageFiles.size.toString())
            state.sourcePath = displayPath
            state.loadFiles(imageFiles)
            resetBrowserNavigation()
            state.editingActive = true
            isMultiEditMode = false
            selectedIndices = emptySet()
            onSettingsChange(
                currentSettings.withMetadataEditorRecentPath(imageFiles.first().parent)
            )
        } catch (_: CancellationException) {
            throw CancellationException()
        } catch (e: Exception) {
            state.showError(t(StringKey.META_ERROR_LOAD, "message" to (e.message ?: "")))
        } finally {
            state.isLoading = false
        }
    }
}

/** Updates the file browser view mode (column, list, hierarchy, icons). */
internal fun MetadataEditorViewModel.setFileViewMode(
    mode: MetadataEditorFileViewMode,
    onSettingsChange: (AppSettings) -> Unit,
) {
    onSettingsChange(currentSettings.withMetadataEditorFileViewMode(mode))
}

/** Load the image and EXIF for the currently selected file. Called in LaunchedEffect. */
internal suspend fun MetadataEditorViewModel.loadSelectedImage() {
    val file = state.selectedFile
    if (file != null) {
        isLoadingImage = true
        try {
            val img = withContext(dispatcherProvider.io) { ImageIO.read(file) }
            currentImage = img
            try {
                val meta =
                    withContext(dispatcherProvider.io) {
                        imageRepository.getMetadata(
                            ImageFile(path = FilePath(file.absolutePath), fileSize = file.length())
                        )
                    }
                sourceExif =
                    meta?.let {
                        SourceExifSummary(
                            cameraMake = it.make,
                            cameraModel = it.model,
                            lensModel = it.lensModel,
                            focalLength = it.focalLength?.let { f -> "${f}mm" },
                            aperture = it.aperture?.let { a -> "f/$a" },
                            shutterSpeed = it.shutterSpeed,
                            iso = it.iso?.toString(),
                            description = it.description,
                            dateOriginal = it.dateTimeOriginal?.toString(),
                            keywords = it.keywords?.joinToString(", "),
                            locationName = it.subLocation,
                            address = null, // No direct mapping in ImageMetadata
                            city = it.city,
                            state = it.provinceState,
                            country = it.countryName,
                            gpsLatitude = it.latitude?.toString(),
                            gpsLongitude = it.longitude?.toString(),
                            imageWidth = it.imageWidth,
                            imageHeight = it.imageHeight,
                            orientation = it.orientation,
                            software = it.software,
                            copyright = it.copyright,
                            artist = it.artist,
                            colorSpace = it.colorSpace,
                            flash = it.flash,
                            whiteBalance = it.whiteBalance,
                            meteringMode = it.meteringMode,
                            exposureProgram = it.exposureProgram,
                            exposureCompensation =
                                it.exposureCompensation?.let { ec ->
                                    if (ec >= 0) "+${ec}" else "${ec}"
                                },
                            focalLength35mm = it.focalLength35mm,
                        )
                    }
                state.markSourceExifLoaded(file)
            } catch (_: Exception) {
                sourceExif = null
            }
        } catch (_: CancellationException) {
            throw CancellationException()
        } catch (_: Exception) {
            currentImage = null
            sourceExif = null
        } finally {
            isLoadingImage = false
        }
    } else {
        currentImage = null
        sourceExif = null
    }
}

/** Reconcile `.thumbs` on disk and clear the in-memory cache after loading a new file set. */
internal fun MetadataEditorViewModel.onFilesLoaded(scope: CoroutineScope) {
    thumbnailCache.clear()
    thumbnailCacheRevision++
    if (!currentSettings.metadataEditorDiskThumbnailCache) return
    scope.launch {
        folderThumbnailCache.reconcileSources(
            sourceFiles = state.files.map { FilePath(it.absolutePath) },
            editorSourcePath = state.sourcePath,
            maxPx = THUMBNAIL_SIZE,
            diskCacheEnabled = true,
        )
    }
}

/** Lazily load a thumbnail for [file] (memory → disk → generate). */
internal suspend fun MetadataEditorViewModel.ensureThumbnail(file: File) {
    val path = file.absolutePath
    if (thumbnailCache.containsKey(path) || !thumbnailsInFlight.add(path)) return
    val diskCacheEnabled = currentSettings.metadataEditorDiskThumbnailCache
    try {
        val thumb =
            folderThumbnailCache.getThumbnail(
                sourceFile = FilePath(path),
                editorSourcePath = state.sourcePath,
                maxPx = THUMBNAIL_SIZE,
                diskCacheEnabled = diskCacheEnabled,
            )
        if (thumb != null) {
            thumbnailCache[path] = thumb.toBufferedImage()
            thumbnailCacheRevision++
        }
    } finally {
        thumbnailsInFlight.remove(path)
    }
}

internal fun MetadataEditorViewModel.setDiskThumbnailCacheEnabled(
    enabled: Boolean,
    onSettingsChange: (AppSettings) -> Unit,
) {
    onSettingsChange(currentSettings.withMetadataEditorDiskThumbnailCache(enabled))
}

/** Clears in-memory thumbnails and deletes the `.thumbs` folder for the current source. */
internal fun MetadataEditorViewModel.clearDiskThumbnailCache(scope: CoroutineScope) {
    thumbnailCache.clear()
    thumbnailCacheRevision++
    val sourcePath = state.sourcePath
    if (sourcePath.isBlank()) return
    scope.launch {
        folderThumbnailCache.deleteThumbsFolder(FilePath(sourcePath))
        state.showInfo(t(StringKey.META_THUMBS_CLEARED))
    }
}

internal fun MetadataEditorViewModel.invalidateThumbnail(file: File, scope: CoroutineScope) {
    thumbnailCache.remove(file.absolutePath)
    thumbnailCacheRevision++
    if (!currentSettings.metadataEditorDiskThumbnailCache) return
    scope.launch {
        folderThumbnailCache.invalidate(
            sourceFile = FilePath(file.absolutePath),
            editorSourcePath = state.sourcePath,
            maxPx = THUMBNAIL_SIZE,
        )
    }
}

/**
 * Recursively collects image files from a directory, optionally including subdirectories. Files are
 * sorted by path for deterministic ordering.
 */
private fun collectImageFiles(directory: File, includeSubfolders: Boolean): List<File> {
    val files = mutableListOf<File>()
    fun walk(dir: File) {
        dir.listFiles()
            ?.sortedBy { it.name.lowercase() }
            ?.forEach { file ->
                if (file.isFile && isMetadataEditableFile(file)) {
                    files.add(file)
                } else if (file.isDirectory && includeSubfolders) {
                    walk(file)
                }
            }
    }
    walk(directory)
    return files
}
