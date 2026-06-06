package org.kryspetrie.fileimport.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.kryspetrie.fileimport.application.ImportService
import org.kryspetrie.fileimport.application.WatchFolderService
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.CameraDevice
import org.kryspetrie.fileimport.domain.model.DuplicateInfo
import org.kryspetrie.fileimport.domain.model.ImageFile
import org.kryspetrie.fileimport.domain.model.ImportConfiguration
import org.kryspetrie.fileimport.domain.model.ImportHistoryEntry
import org.kryspetrie.fileimport.domain.model.ImportMode
import org.kryspetrie.fileimport.domain.model.ImportProgress
import org.kryspetrie.fileimport.domain.model.ImportResult
import org.kryspetrie.fileimport.domain.model.IndexProgress
import org.kryspetrie.fileimport.domain.port.DeviceEvent
import org.kryspetrie.fileimport.domain.port.DevicePort
import org.kryspetrie.fileimport.domain.port.SettingsPort
import org.kryspetrie.fileimport.domain.port.TimeProvider
import org.kryspetrie.fileimport.infrastructure.adapter.AppPaths
import org.kryspetrie.fileimport.infrastructure.adapter.ImportHistoryAdapter
import org.kryspetrie.fileimport.ui.screens.components.SettingsSection
import org.kryspetrie.fileimport.ui.screens.mediaimport.ClearCacheConfirmDialog
import org.kryspetrie.fileimport.ui.screens.mediaimport.DuplicateReviewDialog
import org.kryspetrie.fileimport.ui.screens.mediaimport.ErrorCard
import org.kryspetrie.fileimport.ui.screens.mediaimport.ImageSelectionDialog
import org.kryspetrie.fileimport.ui.screens.mediaimport.ImportHistorySection
import org.kryspetrie.fileimport.ui.screens.mediaimport.MediaImportActionBar
import org.kryspetrie.fileimport.ui.screens.mediaimport.MediaImportFlowStep
import org.kryspetrie.fileimport.ui.screens.mediaimport.MediaImportProgressView
import org.kryspetrie.fileimport.ui.components.ChunkyScrollbar
import org.kryspetrie.fileimport.ui.screens.mediaimport.PreviewStructureDialog
import org.kryspetrie.fileimport.ui.screens.mediaimport.SourceDestinationFields
import org.kryspetrie.fileimport.ui.screens.mediaimport.WatchFolderStatusCard

internal fun configSummary(c: ImportConfiguration): String = buildString {
    if (c.createSubfolders) append(c.folderPattern) else append("Flat")
    append(" · ")
    if (c.preserveOriginalName) append("original names") else append(c.fileNamePattern)
    if (c.verifyAfterCopy) append(" · verify")
    if (c.deleteAfterImport) append(" · delete source")
    if (c.detectVisualDuplicates) append(" · dedup")
}

@Composable
fun MediaImportScreen(settings: AppSettings, onSettingsChange: (AppSettings) -> Unit) {
    val importService = koinInject<ImportService>()
    val devicePort = koinInject<DevicePort>()
    val historyAdapter = koinInject<ImportHistoryAdapter>()
    val settingsPort = koinInject<SettingsPort>()
    val watchFolderService = koinInject<WatchFolderService>()
    val timeProvider = koinInject<TimeProvider>()
    val watchStatus by watchFolderService.status.collectAsState()
    val scope = rememberCoroutineScope()
    var detectedDevices by remember { mutableStateOf<List<CameraDevice>>(emptyList()) }
    var settingsExpanded by remember { mutableStateOf(false) }
    var wantsReview by remember { mutableStateOf(false) }

    // Flow state
    var flowStep by remember { mutableStateOf(MediaImportFlowStep.SETUP) }
    var images by remember { mutableStateOf<List<ImageFile>>(emptyList()) }
    var filteredImages by remember { mutableStateOf<List<ImageFile>>(emptyList()) }
    var duplicates by remember { mutableStateOf<List<DuplicateInfo>>(emptyList()) }
    var importProgress by remember { mutableStateOf(ImportProgress()) }
    var importResult by remember { mutableStateOf<ImportResult?>(null) }
    var importJob by remember { mutableStateOf<Job?>(null) }
    var importMode by remember { mutableStateOf(ImportMode.ALL) }

    // Paths - initialized from settings, persists across tabs
    var sourcePath by remember { mutableStateOf(settings.importTabSettings.lastSourcePath) }
    var destinationPath by remember {
        mutableStateOf(
            settings.importTabSettings.lastDestinationPath.ifBlank {
                AppPaths.defaultDestination.absolutePath
            }
        )
    }

    // Sync local state from settings
    LaunchedEffect(
        settings.importTabSettings.lastSourcePath,
        settings.importTabSettings.lastDestinationPath,
    ) {
        sourcePath = settings.importTabSettings.lastSourcePath
        if (settings.importTabSettings.lastDestinationPath.isNotBlank()) {
            destinationPath = settings.importTabSettings.lastDestinationPath
        }
    }

    // Persist sourcePath when changed
    LaunchedEffect(sourcePath) {
        if (sourcePath.isNotBlank()) {
            val s = settingsPort.observeSettings().first()
            scope.launch {
                settingsPort.saveSettings(
                    s.withImportTabSettings(s.importTabSettings.withRecentSourcePath(sourcePath))
                )
            }
        }
    }

    // Persist destinationPath when changed
    LaunchedEffect(destinationPath) {
        if (destinationPath.isNotBlank()) {
            val s = settingsPort.observeSettings().first()
            scope.launch {
                settingsPort.saveSettings(
                    s.withImportTabSettings(
                        s.importTabSettings.withRecentDestinationPath(destinationPath)
                    )
                )
            }
        }
    }

    var customConfig by remember { mutableStateOf(ImportConfiguration()) }

    // Detect cameras on launch, then monitor for hot-plug events
    LaunchedEffect(Unit) {
        detectedDevices =
            try {
                devicePort.detectDevices()
            } catch (_: Exception) {
                emptyList()
            }
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

    var scanProgress by remember { mutableStateOf("") }
    var scanTotal by remember { mutableStateOf(0) }
    var scanCurrent by remember { mutableStateOf(0) }
    var indexProgress by remember { mutableStateOf(IndexProgress()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showClearCacheConfirm by remember { mutableStateOf(false) }
    val canStart =
        sourcePath.isNotBlank() &&
            destinationPath.isNotBlank() &&
            flowStep == MediaImportFlowStep.SETUP

    // Path validation
    val sourceDir = remember(sourcePath) { if (sourcePath.isNotBlank()) File(sourcePath) else null }
    val sourceValid = remember(sourcePath) { sourceDir?.isDirectory == true }
    val destDir =
        remember(destinationPath) {
            if (destinationPath.isNotBlank()) File(destinationPath) else null
        }
    val destValid = remember(destinationPath) { destDir?.isDirectory == true }
    val destCanCreate =
        remember(destinationPath) {
            if (destinationPath.isBlank()) false
            else {
                val dir = File(destinationPath)
                if (dir.isDirectory) false // already exists, not "can create"
                else {
                    // Walk up to find an existing ancestor — if one exists, the path is creatable
                    var parent = dir.parentFile
                    while (parent != null) {
                        if (parent.isDirectory) break
                        parent = parent.parentFile
                    }
                    parent != null
                }
            }
        }

    fun resetFlow() {
        flowStep = MediaImportFlowStep.SETUP
        images = emptyList()
        filteredImages = emptyList()
        duplicates = emptyList()
        importResult = null
        errorMessage = null
        scanProgress = ""
        scanTotal = 0
        scanCurrent = 0
        indexProgress = IndexProgress()
    }

    fun doImport(toImport: List<ImageFile> = filteredImages) {
        flowStep = MediaImportFlowStep.IMPORTING
        importProgress = ImportProgress()
        importResult = null
        importJob =
            scope.launch {
                try {
                    val result =
                        importService.executeImport(toImport, destinationPath, customConfig) {
                            importProgress = it
                        }
                    importResult = result
                    result.historyEntry?.let { entry -> historyAdapter.addEntry(entry) }
                        ?: run {
                            historyAdapter.addEntry(
                                ImportHistoryEntry(
                                    timestamp = timeProvider.currentTimeMillis(),
                                    timestampString =
                                        ImportHistoryEntry.createTimestampString(
                                            timeProvider.currentTimeMillis()
                                        ),
                                    sourcePath = sourcePath,
                                    destinationPath = destinationPath,
                                    folderPattern = customConfig.folderPattern,
                                    filenamePattern = customConfig.fileNamePattern,
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
                    importService.indexFolder(destinationPath, true) {}
                    flowStep = MediaImportFlowStep.COMPLETE
                } catch (_: Exception) {
                    importResult =
                        ImportResult(
                            totalFiles = toImport.size,
                            successCount = 0,
                            errorCount = 1,
                            duplicateCount = 0,
                            skippedCount = 0,
                            deletedSourceCount = 0,
                            endTime = timeProvider.currentTimeMillis(),
                        )
                    flowStep = MediaImportFlowStep.COMPLETE
                }
            }
    }

    fun continueAfterSelection(selectedImages: List<ImageFile> = images.filter { it.isSelected }) {
        importJob =
            scope.launch {
                try {
                    var toImport = importService.applyPairFilter(selectedImages, customConfig)
                    if (importMode == ImportMode.NEW) {
                        flowStep = MediaImportFlowStep.INDEXING
                        importService.indexFolder(destinationPath, true) { indexProgress = it }
                        val destHashes = importService.getDestinationHashes(destinationPath)
                        toImport =
                            importService.filterAlreadyTransferred(
                                toImport,
                                destHashes,
                                customConfig,
                            )
                    }
                    filteredImages = toImport
                    if (customConfig.detectVisualDuplicates) {
                        flowStep = MediaImportFlowStep.CHECKING_DUPES
                        val found = importService.findVisualDuplicates(toImport, customConfig)
                        if (found.isNotEmpty()) {
                            duplicates = found
                            flowStep = MediaImportFlowStep.DUPE_REVIEW
                            return@launch
                        }
                    }
                    if (wantsReview) flowStep = MediaImportFlowStep.PREVIEW else doImport(toImport)
                } catch (e: Exception) {
                    errorMessage = e.message ?: "Processing failed"
                    flowStep = MediaImportFlowStep.SETUP
                }
            }
    }

    fun startFlow(withReview: Boolean, mode: ImportMode = importMode) {
        importMode = mode
        wantsReview = withReview
        errorMessage = null
        flowStep = MediaImportFlowStep.SCANNING
        importJob =
            scope.launch {
                try {
                    val scanned =
                        importService.scanSource(sourcePath, true) { current, total, file ->
                            scanCurrent = current
                            scanTotal = total
                            scanProgress = file
                        }
                    images = scanned.map { it.copy(isSelected = true) }
                    if (mode == ImportMode.SELECT) {
                        flowStep = MediaImportFlowStep.SELECTING
                        return@launch
                    }
                    continueAfterSelection(scanned.map { it.copy(isSelected = true) })
                } catch (e: Exception) {
                    errorMessage = e.message ?: "Scan failed"
                    flowStep = MediaImportFlowStep.SETUP
                }
            }
    }

    // --- Dialogs ---
    if (showClearCacheConfirm) {
        ClearCacheConfirmDialog(
            onConfirm = {
                scope.launch { importService.clearAllIndexes() }
                showClearCacheConfirm = false
            },
            onDismiss = { showClearCacheConfirm = false },
        )
    }

    if (flowStep == MediaImportFlowStep.SELECTING) {
        ImageSelectionDialog(
            images = images,
            onToggleSelection = { id ->
                images =
                    images.map { if (it.id == id) it.copy(isSelected = !it.isSelected) else it }
            },
            onSelectAll = { images = images.map { it.copy(isSelected = true) } },
            onSelectNone = { images = images.map { it.copy(isSelected = false) } },
            onContinue = {
                val selectedImages = images.filter { it.isSelected }
                filteredImages = selectedImages
                flowStep = MediaImportFlowStep.SETUP
                continueAfterSelection(selectedImages)
            },
            onBack = { flowStep = MediaImportFlowStep.SETUP },
            selectedCount = images.count { it.isSelected },
        )
    }

    if (flowStep == MediaImportFlowStep.DUPE_REVIEW) {
        DuplicateReviewDialog(
            duplicates = duplicates,
            onContinue = {
                if (wantsReview) flowStep = MediaImportFlowStep.PREVIEW else doImport()
            },
            onBack = { resetFlow() },
        )
    }

    if (flowStep == MediaImportFlowStep.PREVIEW) {
        PreviewStructureDialog(
            images = filteredImages,
            sourcePath = sourcePath,
            destinationPath = destinationPath,
            configuration = customConfig,
            onImport = { doImport() },
            onBack = { resetFlow() },
        )
    }

    // --- Main layout ---
    Column(modifier = Modifier.fillMaxSize()) {
        ChunkyScrollbar(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            Text("Import", style = MaterialTheme.typography.headlineSmall)
            SourceDestinationFields(
                sourcePath = sourcePath,
                onSourcePathChange = { sourcePath = it },
                destinationPath = destinationPath,
                onDestinationPathChange = { destinationPath = it },
                sourceValid = sourceValid,
                destValid = destValid,
                destCanCreate = destCanCreate,
                sourceDirName = sourceDir?.name,
                destDirName = destDir?.name,
            )
            if (watchStatus.isWatching) {
                WatchFolderStatusCard(
                    watchStatus = watchStatus,
                    onStopWatching = { watchFolderService.stopWatching() },
                )
            }
            errorMessage?.let { ErrorCard(message = it) }
            MediaImportProgressView(
                flowStep = flowStep,
                scanCurrent = scanCurrent,
                scanTotal = scanTotal,
                scanProgress = scanProgress,
                indexProgress = indexProgress,
                importProgress = importProgress,
                importResult = importResult,
                importJob = importJob,
                destinationPath = destinationPath,
                onReset = { resetFlow() },
            )
            SettingsSection(
                expanded = settingsExpanded,
                onToggle = { settingsExpanded = !settingsExpanded },
                configuration = customConfig,
                onConfigChange = { customConfig = it },
                settings = settings,
                onSettingsChange = onSettingsChange,
                onClearCache = { showClearCacheConfirm = true },
                sourcePath = sourcePath,
                destinationPath = destinationPath,
                watchFolderService = watchFolderService,
                watchStatus = watchStatus,
                scope = scope,
            )
            var historyExpanded by remember { mutableStateOf(false) }
            var historyEntries by remember { mutableStateOf<List<ImportHistoryEntry>>(emptyList()) }
            LaunchedEffect(flowStep) { historyEntries = historyAdapter.loadHistory() }
            ImportHistorySection(
                historyEntries = historyEntries,
                expanded = historyExpanded,
                onToggle = { historyExpanded = !historyExpanded },
            )
            if (flowStep == MediaImportFlowStep.SETUP) {
                MediaImportActionBar(
                    canStart = canStart,
                    importMode = importMode,
                    onImportModeChange = {},
                    onStartFlow = { withReview, mode -> startFlow(withReview, mode) },
                )
            }
        }
        }
    }
}
