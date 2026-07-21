package org.kryspetrie.fileimport.application

import org.kryspetrie.fileimport.domain.port.SettingsPort
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.ImageFileType
import org.kryspetrie.fileimport.domain.model.WatchFolderConfig
import org.kryspetrie.fileimport.domain.model.WatchFolderStatus
import org.kryspetrie.fileimport.domain.port.DispatcherProvider
import org.kryspetrie.fileimport.domain.port.FileSystemPort
import org.kryspetrie.fileimport.domain.port.TimeProvider

/**
 * Watches a folder for new/modified image files and automatically imports them.
 *
 * Uses the Java [WatchService] API to monitor filesystem events (ENTRY_CREATE, ENTRY_MODIFY).
 * When an image file is detected, it accumulates the change and triggers an import after a
 * cooldown period to avoid rapid-fire imports during bulk file copies.
 *
 * ## Lifecycle
 * - Call [startWatching] to begin monitoring a folder
 * - Call [stopWatching] to cancel monitoring and release resources
 * - The [status] StateFlow provides real-time status updates
 *
 * ## Threading Model
 * - File watching runs on the IO dispatcher
 * - Status updates are thread-safe via [MutableStateFlow.update]
 * - Cancellation is handled gracefully — [WatchService] is always closed
 *
 * @see WatchFolderConfig configuration options
 * @see WatchFolderStatus status reporting
 */
class WatchFolderService(
    private val importService: ImportService,
    private val timeProvider: TimeProvider,
    private val dispatcherProvider: DispatcherProvider,
    private val fileSystem: FileSystemPort,
    private val faceGroupingService: FaceGroupingService? = null,
    private val settingsPort: SettingsPort? = null,
) {
    companion object {
        /** Maximum number of consecutive errors before pausing the watch. */
        private const val MAX_CONSECUTIVE_ERRORS = 5

        /** Maximum backoff delay in milliseconds (1 minute). */
        private const val MAX_BACKOFF_MS = 60_000L
    }
    private val _status = MutableStateFlow(WatchFolderStatus())
    val status: StateFlow<WatchFolderStatus> = _status

    private var watchJob: Job? = null
    @Volatile
    private var currentConfig: WatchFolderConfig? = null
    private val supportedExtensions = ImageFileType.supportedExtensions()

    /**
     * Starts watching the specified folder for new/modified image files.
     *
     * Any previous watch is stopped first. The watch runs on the IO dispatcher within the
     * provided [scope]. When new images are detected (after a cooldown period to batch
     * rapid changes), they are automatically imported to the configured destination.
     *
     * @param config The watch folder configuration (paths, cooldown, etc.)
     * @param scope The coroutine scope to run the watch loop in
     */
    fun startWatching(config: WatchFolderConfig, scope: CoroutineScope) {
        stopWatching()
        currentConfig = config
        _status.value = WatchFolderStatus(
            configId = config.id,
            isWatching = true,
            watchPath = config.watchPath,
        )

        watchJob =
            scope.launch(dispatcherProvider.io) {
                var watchService: java.nio.file.WatchService? = null
                try {
                    watchService = FileSystems.getDefault().newWatchService()
                    val watchPath = Paths.get(config.watchPath)

                    if (!fileSystem.exists(FilePath(config.watchPath))) {
                        _status.update { it.copy(
                            isWatching = false,
                            lastError = "Watch path does not exist: ${config.watchPath}",
                        ) }
                        return@launch
                    }

                    watchPath.register(
                        watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                    )

                    if (config.recursive) {
                        val directories =
                            fileSystem.listDirectoriesRecursive(FilePath(config.watchPath))
                        for (dirPath in directories) {
                            // Skip the root path (already registered above)
                            if (dirPath.path == config.watchPath) continue
                            try {
                                Paths.get(dirPath.path)
                                    .register(
                                        watchService,
                                        StandardWatchEventKinds.ENTRY_CREATE,
                                        StandardWatchEventKinds.ENTRY_MODIFY,
                                    )
                            } catch (_: Exception) {
                                // Skip directories that can't be registered (e.g. permission denied)
                            }
                        }
                    }

                    var pendingFiles = mutableSetOf<String>()
                    var lastTriggerTime = 0L
                    var importCount = 0
                    var consecutiveErrors = 0

                    while (isActive) {
                        val key =
                            try {
                                watchService.poll(1, java.util.concurrent.TimeUnit.SECONDS)
                            } catch (_: InterruptedException) {
                                // Poll was interrupted — check if still active
                                continue
                            }

                        if (key != null) {
                            // Track new directories for recursive watching
                            for (event in key.pollEvents()) {
                                val kind = event.kind()
                                if (kind == StandardWatchEventKinds.OVERFLOW) continue

                                @Suppress("UNCHECKED_CAST") val ev = event as WatchEvent<Path>
                                val parentDir = key.watchable() as? Path ?: continue
                                val fileName = ev.context().toString()
                                val fullPath = parentDir.resolve(fileName)

                                // Register new subdirectories for recursive watching
                                if (config.recursive && kind == StandardWatchEventKinds.ENTRY_CREATE) {
                                    try {
                                        if (fileSystem.isDirectory(FilePath(fullPath.toString()))) {
                                            fullPath.register(
                                                watchService,
                                                StandardWatchEventKinds.ENTRY_CREATE,
                                                StandardWatchEventKinds.ENTRY_MODIFY,
                                            )
                                        }
                                    } catch (_: Exception) {
                                        // Skip directories that can't be registered
                                    }
                                }

                                val ext = fileName.substringAfterLast('.', "").lowercase()
                                if (supportedExtensions.contains(ext)) {
                                    pendingFiles.add(fileName)
                                    _status.update { it.copy(
                                        lastEventTime = timeProvider.currentTimeMillis(),
                                        filesDetected = it.filesDetected + 1,
                                        autoImportsPending = pendingFiles.size,
                                    ) }
                                }
                            }
                            key.reset()
                        }

                        val now = timeProvider.currentTimeMillis()
                        if (
                            pendingFiles.isNotEmpty() &&
                                now - _status.value.lastEventTime >= config.cooldownMs &&
                                now - lastTriggerTime >= config.cooldownMs
                        ) {
                            val fileCount = pendingFiles.size
                            lastTriggerTime = now
                            pendingFiles = mutableSetOf()
                            importCount++

                            _status.update { it.copy(
                                autoImportsPending = 0,
                                lastError = null, // Clear previous errors on new attempt
                            ) }

                            try {
                                val scanned =
                                    importService.scanSource(config.watchPath, config.recursive)
                                if (scanned.isNotEmpty()) {
                                    val result = importService.executeImport(
                                        scanned,
                                        config.destinationPath,
                                        config.configuration,
                                    )

                                    // Auto-detect faces on import if enabled
                                    val shouldAutoDetect = settingsPort?.let { sp ->
                                        sp.observeSettings().value.autoDetectFacesOnImport
                                    } ?: false
                                    if (shouldAutoDetect && faceGroupingService?.isDetectionAvailable() == true) {
                                        try {
                                            val importedPaths = result.copiedFiles.map { it.destinationPath }
                                            if (importedPaths.isNotEmpty()) {
                                                faceGroupingService.autoDetectFacesForImports(importedPaths)
                                            }
                                        } catch (_: Exception) {
                                            // Face detection failure should not affect import status
                                        }
                                    }

                                    _status.update { it.copy(
                                        lastEventTime = now,
                                        importCount = it.importCount + 1,
                                        lastImportTime = now,
                                        lastImportFileCount = scanned.size,
                                    ) }
                                    consecutiveErrors = 0
                                }
                            } catch (e: Exception) {
                                consecutiveErrors++
                                _status.update { it.copy(
                                    lastError = "Import failed (attempt $consecutiveErrors): ${e.message}",
                                ) }

                                // Exponential backoff: wait before retrying to avoid rapid error loops
                                val backoffMs = minOf(
                                    config.cooldownMs * (1L shl minOf(consecutiveErrors - 1, 5)),
                                    MAX_BACKOFF_MS,
                                )
                                kotlinx.coroutines.delay(backoffMs)

                                // Pause after 5 consecutive errors (increased from 3 for resilience)
                                if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                                    _status.update { it.copy(
                                        isWatching = false,
                                        lastError = "Paused after $MAX_CONSECUTIVE_ERRORS consecutive errors. Last error: ${e.message}. Restart watch to retry.",
                                    ) }
                                    return@launch
                                }
                            }
                        }
                    }
                } catch (_: CancellationException) {
                    // Coroutine cancelled — normal shutdown
                } catch (e: Exception) {
                    _status.update { it.copy(isWatching = false, lastError = e.message) }
                } finally {
                    // Always close the WatchService to prevent resource leaks
                    watchService?.close()
                }
            }
    }

    /**
     * Stops watching the folder and releases all resources.
     *
     * Cancels the watch coroutine, which triggers the finally block that closes the
     * [java.nio.file.WatchService]. Resets status to idle.
     */
    fun stopWatching() {
        watchJob?.cancel()
        watchJob = null
        currentConfig = null
        _status.value = WatchFolderStatus()
    }

    /** Returns whether a folder is currently being watched. */
    fun isWatching(): Boolean = _status.value.isWatching

    /** Returns the current watch configuration, or null if not watching. */
    fun currentConfig(): WatchFolderConfig? = currentConfig
}