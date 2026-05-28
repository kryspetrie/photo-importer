package org.kryspetrie.fileimport.application

import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.kryspetrie.fileimport.domain.model.ImageFileType
import org.kryspetrie.fileimport.domain.model.ImportConfiguration
import org.kryspetrie.fileimport.domain.port.DispatcherProvider
import org.kryspetrie.fileimport.domain.port.TimeProvider

data class WatchFolderConfig(
    val watchPath: String,
    val destinationPath: String,
    val profileName: String = "",
    val configuration: ImportConfiguration = ImportConfiguration(),
    val cooldownMs: Long = 5000,
    val recursive: Boolean = true,
)

data class WatchFolderStatus(
    val isWatching: Boolean = false,
    val watchPath: String = "",
    val lastEventTime: Long = 0,
    val filesDetected: Int = 0,
    val autoImportsPending: Int = 0,
    val lastError: String? = null,
)

class WatchFolderService(
    private val importService: ImportService,
    private val timeProvider: TimeProvider,
    private val dispatcherProvider: DispatcherProvider,
) {
    private val _status = MutableStateFlow(WatchFolderStatus())
    val status: StateFlow<WatchFolderStatus> = _status

    private var watchJob: Job? = null
    private var currentConfig: WatchFolderConfig? = null
    private val supportedExtensions = ImageFileType.supportedExtensions()

    fun startWatching(config: WatchFolderConfig, scope: CoroutineScope) {
        stopWatching()
        currentConfig = config
        _status.value = WatchFolderStatus(isWatching = true, watchPath = config.watchPath)

        watchJob =
            scope.launch(dispatcherProvider.io) {
                try {
                    val watchService = FileSystems.getDefault().newWatchService()
                    val watchPath = Paths.get(config.watchPath)

                    if (!watchPath.toFile().exists()) {
                        _status.value =
                            _status.value.copy(
                                isWatching = false,
                                lastError = "Watch path does not exist",
                            )
                        return@launch
                    }

                    watchPath.register(
                        watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                    )

                    if (config.recursive) {
                        watchPath
                            .toFile()
                            .walkTopDown()
                            .filter { it.isDirectory }
                            .forEach { dir ->
                                try {
                                    dir.toPath()
                                        .register(
                                            watchService,
                                            StandardWatchEventKinds.ENTRY_CREATE,
                                            StandardWatchEventKinds.ENTRY_MODIFY,
                                        )
                                } catch (_: Exception) {}
                            }
                    }

                    var pendingFiles = mutableSetOf<String>()
                    var lastTriggerTime = 0L

                    while (isActive) {
                        val key = watchService.poll(1, java.util.concurrent.TimeUnit.SECONDS)
                        if (key != null) {
                            for (event in key.pollEvents()) {
                                val kind = event.kind()
                                if (kind == StandardWatchEventKinds.OVERFLOW) continue

                                @Suppress("UNCHECKED_CAST") val ev = event as WatchEvent<Path>
                                val fileName = ev.context().toString()
                                val ext = fileName.substringAfterLast('.', "").lowercase()

                                if (supportedExtensions.contains(ext)) {
                                    pendingFiles.add(fileName)
                                    _status.value =
                                        _status.value.copy(
                                            lastEventTime = timeProvider.currentTimeMillis(),
                                            filesDetected = _status.value.filesDetected + 1,
                                            autoImportsPending = pendingFiles.size,
                                        )
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

                            lastTriggerTime = now
                            pendingFiles = mutableSetOf()
                            _status.value = _status.value.copy(autoImportsPending = 0)

                            try {
                                val scanned =
                                    importService.scanSource(config.watchPath, config.recursive)
                                if (scanned.isNotEmpty()) {
                                    importService.executeImport(
                                        scanned,
                                        config.destinationPath,
                                        config.configuration,
                                    )
                                }
                            } catch (e: Exception) {
                                _status.value = _status.value.copy(lastError = e.message)
                            }
                        }
                    }

                    watchService.close()
                } catch (_: CancellationException) {
                    // Coroutine cancelled — normal shutdown, do nothing
                } catch (e: Exception) {
                    _status.value = _status.value.copy(isWatching = false, lastError = e.message)
                }
            }
    }

    fun stopWatching() {
        watchJob?.cancel()
        watchJob = null
        currentConfig = null
        _status.value = WatchFolderStatus()
    }

    fun isWatching(): Boolean = _status.value.isWatching
}
