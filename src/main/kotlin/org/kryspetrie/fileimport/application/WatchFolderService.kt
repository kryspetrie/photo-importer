package org.kryspetrie.fileimport.application

import java.nio.file.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.kryspetrie.fileimport.domain.model.ImageFileType
import org.kryspetrie.fileimport.domain.model.ImportConfiguration

data class WatchFolderConfig(
    val watchPath: String,
    val destinationPath: String,
    val profileName: String = "",
    val configuration: ImportConfiguration = ImportConfiguration(),
    val cooldownMs: Long = 5000,
    val recursive: Boolean = true
)

data class WatchFolderStatus(
    val isWatching: Boolean = false,
    val watchPath: String = "",
    val lastEventTime: Long = 0,
    val filesDetected: Int = 0,
    val autoImportsPending: Int = 0,
    val lastError: String? = null
)

class WatchFolderService(private val importService: ImportService) {
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
        scope.launch(Dispatchers.IO) {
          try {
            val watchService = FileSystems.getDefault().newWatchService()
            val watchPath = Paths.get(config.watchPath)

            if (!watchPath.toFile().exists()) {
              _status.value =
                  _status.value.copy(isWatching = false, lastError = "Watch path does not exist")
              return@launch
            }

            watchPath.register(
                watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY)

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
                              StandardWatchEventKinds.ENTRY_MODIFY)
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
                            lastEventTime = System.currentTimeMillis(),
                            filesDetected = _status.value.filesDetected + 1,
                            autoImportsPending = pendingFiles.size)
                  }
                }
                key.reset()
              }

              val now = System.currentTimeMillis()
              if (pendingFiles.isNotEmpty() &&
                  now - _status.value.lastEventTime >= config.cooldownMs &&
                  now - lastTriggerTime >= config.cooldownMs) {

                lastTriggerTime = now
                val filesToImport = pendingFiles.toSet()
                pendingFiles = mutableSetOf()
                _status.value = _status.value.copy(autoImportsPending = 0)

                try {
                  val scanned = importService.scanSource(config.watchPath, config.recursive)
                  if (scanned.isNotEmpty()) {
                    importService.executeImport(
                        scanned, config.destinationPath, config.configuration)
                  }
                } catch (e: Exception) {
                  _status.value = _status.value.copy(lastError = e.message)
                }
              }
            }

            watchService.close()
          } catch (e: Exception) {
            if (e !is CancellationException) {
              _status.value = _status.value.copy(isWatching = false, lastError = e.message)
            }
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
