package org.kryspetrie.fileimport.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.kryspetrie.fileimport.application.ImportService
import org.kryspetrie.fileimport.application.WatchFolderManager
import org.kryspetrie.fileimport.domain.model.CameraDevice
import org.kryspetrie.fileimport.domain.model.DuplicateInfo
import org.kryspetrie.fileimport.domain.model.ImageFile
import org.kryspetrie.fileimport.domain.model.ImportConfiguration
import org.kryspetrie.fileimport.domain.model.ImportHistoryEntry
import org.kryspetrie.fileimport.domain.model.ImportMode
import org.kryspetrie.fileimport.domain.model.ImportProgress
import org.kryspetrie.fileimport.domain.model.ImportResult
import org.kryspetrie.fileimport.domain.model.IndexProgress
import org.kryspetrie.fileimport.domain.model.WatchFolderConfig
import org.kryspetrie.fileimport.domain.model.WatchFolderStatus
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.domain.port.DeviceEvent
import org.kryspetrie.fileimport.domain.port.DevicePort
import org.kryspetrie.fileimport.domain.port.ImportHistoryPort
import org.kryspetrie.fileimport.domain.port.LocalePort
import org.kryspetrie.fileimport.domain.port.PathsPort
import org.kryspetrie.fileimport.domain.port.SettingsPort
import org.kryspetrie.fileimport.domain.port.TimeProvider
import org.kryspetrie.fileimport.ui.screens.mediaimport.MediaImportFlowStep

/**
 * ViewModel for the media import screen.
 *
 * Hoists all business logic, flow control, device detection, and import orchestration from the
 * MediaImportScreen composable. The composable becomes a thin rendering shell that observes this
 * ViewModel's state and delegates actions to it.
 */
class MediaImportViewModel(
    val importService: ImportService,
    val devicePort: DevicePort,
    val historyPort: ImportHistoryPort,
    val settingsPort: SettingsPort,
    val watchFolderManager: WatchFolderManager,
    val timeProvider: TimeProvider,
    val pathsPort: PathsPort,
    private val localePort: LocalePort,
) {
    private fun t(key: StringKey, vararg params: Pair<String, String>): String =
        localePort.t(key, *params)

    // ── Device detection ─────────────────────────────────────────

    var detectedDevices by mutableStateOf<List<CameraDevice>>(emptyList())
        private set

    var errorMessage by mutableStateOf<String?>(null)

    // ── Flow state ───────────────────────────────────────────────

    var flowStep by mutableStateOf(MediaImportFlowStep.SETUP)

    var images by mutableStateOf<List<ImageFile>>(emptyList())

    var filteredImages by mutableStateOf<List<ImageFile>>(emptyList())

    var duplicates by mutableStateOf<List<DuplicateInfo>>(emptyList())

    /**
     * Count of duplicates detected before copy (already-transferred filters + visual-dupe groups).
     * Reported in [ImportResult.duplicateCount] / history.
     */
    var detectedDuplicateCount by mutableStateOf(0)
        private set

    var importProgress by mutableStateOf(ImportProgress())

    var importResult by mutableStateOf<ImportResult?>(null)

    /**
     * Sets import result and flow step atomically to prevent UI from seeing COMPLETE with
     * stale/null result data during recomposition.
     */
    private fun completeImport(result: ImportResult) {
        importResult = result
        flowStep = MediaImportFlowStep.COMPLETE
    }

    var importJob by mutableStateOf<Job?>(null)

    var importMode by mutableStateOf(ImportMode.ALL)

    // ── Paths ────────────────────────────────────────────────────

    var sourcePath by mutableStateOf("")

    var destinationPath by mutableStateOf("")

    // ── Settings ─────────────────────────────────────────────────

    var settingsExpanded by mutableStateOf(false)

    var wantsReview by mutableStateOf(false)

    var customConfig by mutableStateOf(ImportConfiguration())

    // ── Scan progress ────────────────────────────────────────────

    var scanProgress by mutableStateOf("")

    var scanTotal by mutableStateOf(0)

    var scanCurrent by mutableStateOf(0)

    var indexProgress by mutableStateOf(IndexProgress())

    var showClearCacheConfirm by mutableStateOf(false)

    // ── History ───────────────────────────────────────────────────

    var historyExpanded by mutableStateOf(false)

    var historyEntries by mutableStateOf<List<ImportHistoryEntry>>(emptyList())

    // ── Computed ─────────────────────────────────────────────────

    val canStart: Boolean
        get() =
            sourcePath.isNotBlank() &&
                destinationPath.isNotBlank() &&
                flowStep == MediaImportFlowStep.SETUP

    val sourceDir: File?
        get() = if (sourcePath.isNotBlank()) File(sourcePath) else null

    val sourceValid: Boolean
        get() = sourceDir?.isDirectory == true

    val destDir: File?
        get() = if (destinationPath.isNotBlank()) File(destinationPath) else null

    val destValid: Boolean
        get() = destDir?.isDirectory == true

    val destCanCreate: Boolean
        get() {
            if (destinationPath.isBlank()) return false
            val dir = File(destinationPath)
            if (dir.isDirectory) return false
            var parent = dir.parentFile
            while (parent != null) {
                if (parent.isDirectory) break
                parent = parent.parentFile
            }
            return parent != null
        }

    // ── Initialization ────────────────────────────────────────────

    fun initializeFromSettings(
        initialSourcePath: String,
        initialDestPath: String,
        initialConfig: ImportConfiguration = ImportConfiguration(),
    ) {
        sourcePath = initialSourcePath
        destinationPath = initialDestPath
        customConfig = initialConfig
    }

    fun syncFromSettings(lastSourcePath: String, lastDestPath: String) {
        sourcePath = lastSourcePath
        if (lastDestPath.isNotBlank()) {
            destinationPath = lastDestPath
        }
    }

    fun getDefaultDestination(): String = pathsPort.defaultDestination

    // ── Settings persistence ──────────────────────────────────────

    fun persistSourcePath(scope: CoroutineScope) {
        if (sourcePath.isNotBlank()) {
            scope.launch {
                val s = settingsPort.observeSettings().first()
                settingsPort.saveSettings(
                    s.withImportTabSettings(s.importTabSettings.withRecentSourcePath(sourcePath))
                )
            }
        }
    }

    fun persistDestinationPath(scope: CoroutineScope) {
        if (destinationPath.isNotBlank()) {
            scope.launch {
                val s = settingsPort.observeSettings().first()
                settingsPort.saveSettings(
                    s.withImportTabSettings(
                        s.importTabSettings.withRecentDestinationPath(destinationPath)
                    )
                )
            }
        }
    }

    // ── Device detection ──────────────────────────────────────────

    fun detectDevices(scope: CoroutineScope) {
        scope.launch {
            try {
                detectedDevices = devicePort.detectDevices()
            } catch (e: Exception) {
                errorMessage =
                    t(
                        StringKey.ERROR_DETECT_DEVICES,
                        "message" to (e.message ?: t(StringKey.ERROR_UNKNOWN)),
                    )
                detectedDevices = emptyList()
            }
        }
    }

    fun observeDeviceChanges(scope: CoroutineScope) {
        scope.launch {
            devicePort.observeDeviceChanges().collect { event ->
                when (event) {
                    is DeviceEvent.Connected -> {
                        val device = event.device
                        detectedDevices = detectedDevices.filter { it.id != device.id } + device
                    }
                    is DeviceEvent.Disconnected ->
                        detectedDevices = detectedDevices.filter { it.id != event.deviceId }
                    is DeviceEvent.MountChanged -> {}
                }
            }
        }
    }

    // ── Import flow ───────────────────────────────────────────────

    fun resetFlow() {
        flowStep = MediaImportFlowStep.SETUP
        images = emptyList()
        filteredImages = emptyList()
        duplicates = emptyList()
        detectedDuplicateCount = 0
        importResult = null
        errorMessage = null
        scanProgress = ""
        scanTotal = 0
        scanCurrent = 0
        indexProgress = IndexProgress()
    }

    fun doImport(scope: CoroutineScope, toImport: List<ImageFile> = filteredImages) {
        val currentDestPath = destinationPath
        val currentConfig = customConfig
        val currentSourcePath = sourcePath
        flowStep = MediaImportFlowStep.IMPORTING
        importProgress = ImportProgress()
        importResult = null
        importJob =
            scope.launch {
                try {
                    val result =
                        importService.executeImport(
                            images = toImport,
                            destinationPath = currentDestPath,
                            configuration = currentConfig,
                            onProgress = { importProgress = it },
                            detectedDuplicateCount = detectedDuplicateCount,
                        )
                    completeImport(result)
                    result.historyEntry?.let { entry -> historyPort.addEntry(entry) }
                        ?: run {
                            historyPort.addEntry(
                                ImportHistoryEntry(
                                    timestamp = timeProvider.currentTimeMillis(),
                                    timestampString =
                                        ImportHistoryEntry.createTimestampString(
                                            timeProvider.currentTimeMillis()
                                        ),
                                    sourcePath = currentSourcePath,
                                    destinationPath = currentDestPath,
                                    folderPattern = currentConfig.folderPattern,
                                    filenamePattern = currentConfig.fileNamePattern,
                                    totalFiles = result.totalFiles,
                                    successCount = result.successCount,
                                    errorCount = result.errorCount,
                                    skippedCount = result.skippedCount,
                                    duplicateCount = result.duplicateCount,
                                    deletedSourceCount = result.deletedSourceCount,
                                    totalBytes = toImport.sumOf { it.fileSize },
                                    durationMs = result.duration,
                                )
                            )
                        }
                    importService.indexFolder(currentDestPath, true) {}

                    completeImport(importResult!!)
                } catch (_: CancellationException) {
                    throw CancellationException()
                } catch (e: Exception) {
                    errorMessage =
                        e.message
                            ?: t(
                                StringKey.ERROR_IMPORT_FAILED,
                                "message" to t(StringKey.ERROR_UNKNOWN),
                            )
                    completeImport(
                        ImportResult(
                            totalFiles = toImport.size,
                            successCount = 0,
                            errorCount = 1,
                            duplicateCount = 0,
                            skippedCount = 0,
                            deletedSourceCount = 0,
                            endTime = timeProvider.currentTimeMillis(),
                        )
                    )
                }
            }
    }

    fun continueAfterSelection(
        scope: CoroutineScope,
        selectedImages: List<ImageFile> = images.filter { it.isSelected },
    ) {
        val currentConfig = customConfig
        val currentImportMode = importMode
        val currentWantsReview = wantsReview
        val currentDestPath = destinationPath
        importJob =
            scope.launch {
                try {
                    var toImport = importService.applyPairFilter(selectedImages, currentConfig)
                    var alreadyTransferredCount = 0
                    if (currentImportMode == ImportMode.NEW) {
                        flowStep = MediaImportFlowStep.INDEXING
                        importService.indexFolder(currentDestPath, true) { indexProgress = it }
                        val destHashes = importService.getDestinationHashes(currentDestPath)
                        val beforeFilter = toImport.size
                        toImport =
                            importService.filterAlreadyTransferred(
                                toImport,
                                destHashes,
                                currentConfig,
                            )
                        alreadyTransferredCount = (beforeFilter - toImport.size).coerceAtLeast(0)
                    }
                    filteredImages = toImport
                    if (currentConfig.detectVisualDuplicates) {
                        flowStep = MediaImportFlowStep.CHECKING_DUPES
                        val found = importService.findVisualDuplicates(toImport, currentConfig)
                        if (found.isNotEmpty()) {
                            duplicates = found
                            detectedDuplicateCount =
                                alreadyTransferredCount + found.sumOf { it.duplicateImages.size }
                            flowStep = MediaImportFlowStep.DUPE_REVIEW
                            return@launch
                        }
                    }
                    detectedDuplicateCount = alreadyTransferredCount
                    if (currentWantsReview) flowStep = MediaImportFlowStep.PREVIEW
                    else doImport(scope, toImport)
                } catch (_: CancellationException) {
                    throw CancellationException()
                } catch (e: Exception) {
                    errorMessage = e.message ?: t(StringKey.ERROR_PROCESSING_FAILED)
                    flowStep = MediaImportFlowStep.SETUP
                }
            }
    }

    fun startFlow(scope: CoroutineScope, withReview: Boolean, mode: ImportMode = importMode) {
        val currentSourcePath = sourcePath
        importMode = mode
        wantsReview = withReview
        errorMessage = null
        flowStep = MediaImportFlowStep.SCANNING
        importJob =
            scope.launch {
                try {
                    val scanned =
                        importService.scanSource(currentSourcePath, true) { current, total, file ->
                            scanCurrent = current
                            scanTotal = total
                            scanProgress = file
                        }
                    images = scanned.map { it.copy(isSelected = true) }
                    if (mode == ImportMode.SELECT) {
                        flowStep = MediaImportFlowStep.SELECTING
                        return@launch
                    }
                    continueAfterSelection(scope, scanned.map { it.copy(isSelected = true) })
                } catch (_: CancellationException) {
                    throw CancellationException()
                } catch (e: Exception) {
                    errorMessage = e.message ?: t(StringKey.ERROR_SCAN_FAILED)
                    flowStep = MediaImportFlowStep.SETUP
                }
            }
    }

    fun toggleImageSelection(imageId: String) {
        images = images.map { if (it.id == imageId) it.copy(isSelected = !it.isSelected) else it }
    }

    fun selectAllImages() {
        images = images.map { it.copy(isSelected = true) }
    }

    fun selectNoImages() {
        images = images.map { it.copy(isSelected = false) }
    }

    suspend fun loadHistory() {
        historyEntries = historyPort.loadHistory()
    }

    fun clearAllIndexes(scope: CoroutineScope) {
        scope.launch { importService.clearAllIndexes() }
        showClearCacheConfirm = false
    }

    // ── Watch Folder (delegated to WatchFolderManager) ──────────────

    /** Starts watching a folder with the given configuration. */
    fun startWatching(config: WatchFolderConfig) {
        watchFolderManager.startWatching(config)
    }

    /** Stops watching a folder by config ID. */
    fun stopWatching(configId: String) {
        watchFolderManager.stopWatching(configId)
    }

    /** Stops all active watches. */
    fun stopAllWatches() {
        watchFolderManager.stopAll()
    }

    /** Returns the current status for all active watches. */
    val watchStatuses: StateFlow<Map<String, WatchFolderStatus>>
        get() = watchFolderManager.statuses

    /** Adds a watch config and persists it. */
    suspend fun addWatchConfig(config: WatchFolderConfig) {
        watchFolderManager.addConfig(config)
    }

    /** Removes a watch config and stops watching if active. */
    fun removeWatchConfig(configId: String) {
        watchFolderManager.removeConfig(configId)
    }

    /** Updates a watch config. Restarts the watch if currently active. */
    fun updateWatchConfig(config: WatchFolderConfig) {
        watchFolderManager.updateConfig(config)
    }

    /** Returns all persisted watch configs. */
    suspend fun getWatchConfigs(): List<WatchFolderConfig> {
        return watchFolderManager.getConfigs()
    }
}
