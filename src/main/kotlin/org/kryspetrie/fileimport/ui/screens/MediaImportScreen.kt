package org.kryspetrie.fileimport.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.components.AutoOrientIndicator
import org.kryspetrie.fileimport.ui.i18n.strings
import org.kryspetrie.fileimport.ui.components.ChunkyScrollbar
import org.kryspetrie.fileimport.ui.screens.components.SettingsSection
import org.kryspetrie.fileimport.ui.screens.mediaimport.ClearCacheConfirmDialog
import org.kryspetrie.fileimport.ui.screens.mediaimport.DuplicateReviewDialog
import org.kryspetrie.fileimport.ui.screens.mediaimport.ErrorCard
import org.kryspetrie.fileimport.ui.screens.mediaimport.ImageSelectionDialog
import org.kryspetrie.fileimport.ui.screens.mediaimport.ImportHistorySection
import org.kryspetrie.fileimport.ui.screens.mediaimport.MediaImportActionBar
import org.kryspetrie.fileimport.ui.screens.mediaimport.MediaImportFlowStep
import org.kryspetrie.fileimport.ui.screens.mediaimport.MediaImportProgressView
import org.kryspetrie.fileimport.ui.screens.mediaimport.PreviewStructureDialog
import org.kryspetrie.fileimport.ui.screens.mediaimport.SourceDestinationFields
import org.kryspetrie.fileimport.ui.screens.mediaimport.WatchFolderManagement
import org.kryspetrie.fileimport.ui.screens.mediaimport.WatchFolderStatusCard

@Composable
fun MediaImportScreen(settings: AppSettings, onSettingsChange: (AppSettings) -> Unit) {
    val s = strings()
    val vm: MediaImportViewModel = koinInject()
    val scope = rememberCoroutineScope()
    val watchStatuses by vm.watchStatuses.collectAsState()

    // Show the first active watch status in the main flow area
    val activeWatchStatus = watchStatuses.values.firstOrNull { it.isWatching }

    // Initialize paths and import configuration from settings
    LaunchedEffect(Unit) {
        val tab = settings.importTabSettings
        val initialDest = tab.lastDestinationPath.ifBlank { vm.getDefaultDestination() }
        vm.initializeFromSettings(tab.lastSourcePath, initialDest, tab.configuration)
    }

    // Sync paths from settings when they change externally
    LaunchedEffect(
        settings.importTabSettings.lastSourcePath,
        settings.importTabSettings.lastDestinationPath,
    ) {
        vm.syncFromSettings(
            settings.importTabSettings.lastSourcePath,
            settings.importTabSettings.lastDestinationPath,
        )
    }

    // Persist paths and import configuration (mirrors Photo Scan Import tab)
    LaunchedEffect(vm.sourcePath, vm.destinationPath, vm.customConfig) {
        val tabSettings =
            settings.importTabSettings
                .withRecentSourcePath(vm.sourcePath)
                .withRecentDestinationPath(vm.destinationPath)
                .withConfiguration(vm.customConfig)
        onSettingsChange(settings.withImportTabSettings(tabSettings))
    }

    // Detect cameras on launch, then monitor for hot-plug events
    LaunchedEffect(Unit) {
        vm.detectDevices(scope)
        vm.observeDeviceChanges(scope)
    }

    // Load history when step changes
    LaunchedEffect(vm.flowStep) { vm.loadHistory() }

    // ── Dialogs ──

    if (vm.showClearCacheConfirm) {
        ClearCacheConfirmDialog(
            onConfirm = { vm.clearAllIndexes(scope) },
            onDismiss = { vm.showClearCacheConfirm = false },
        )
    }

    if (vm.flowStep == MediaImportFlowStep.SELECTING) {
        ImageSelectionDialog(
            images = vm.images,
            onToggleSelection = { id -> vm.toggleImageSelection(id) },
            onSelectAll = { vm.selectAllImages() },
            onSelectNone = { vm.selectNoImages() },
            onContinue = {
                val selectedImages = vm.images.filter { it.isSelected }
                vm.filteredImages = selectedImages
                vm.flowStep = MediaImportFlowStep.SETUP
                vm.continueAfterSelection(scope, selectedImages)
            },
            onBack = { vm.flowStep = MediaImportFlowStep.SETUP },
            selectedCount = vm.images.count { it.isSelected },
        )
    }

    if (vm.flowStep == MediaImportFlowStep.DUPE_REVIEW) {
        DuplicateReviewDialog(
            duplicates = vm.duplicates,
            onContinue = {
                if (vm.wantsReview) vm.flowStep = MediaImportFlowStep.PREVIEW
                else vm.doImport(scope)
            },
            onBack = { vm.resetFlow() },
        )
    }

    if (vm.flowStep == MediaImportFlowStep.PREVIEW) {
        PreviewStructureDialog(
            images = vm.filteredImages,
            sourcePath = vm.sourcePath,
            destinationPath = vm.destinationPath,
            configuration = vm.customConfig,
            onImport = { vm.doImport(scope) },
            onBack = { vm.resetFlow() },
        )
    }

    // ── Main layout ──

    Column(modifier = Modifier.fillMaxSize()) {
        ChunkyScrollbar(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(s.t(StringKey.IMPORT_TITLE), style = MaterialTheme.typography.headlineSmall)
                SourceDestinationFields(
                    sourcePath = vm.sourcePath,
                    onSourcePathChange = { vm.sourcePath = it },
                    destinationPath = vm.destinationPath,
                    onDestinationPathChange = { vm.destinationPath = it },
                    sourceValid = vm.sourceValid,
                    destValid = vm.destValid,
                    destCanCreate = vm.destCanCreate,
                    sourceDirName = vm.sourceDir?.name,
                    destDirName = vm.destDir?.name,
                )
                // Show active watch status inline in the import area
                activeWatchStatus?.let { status ->
                    WatchFolderStatusCard(
                        watchStatus = status,
                        onStopWatching = {
                            val configId = status.configId
                            if (configId.isNotEmpty()) vm.stopWatching(configId)
                        },
                    )
                }
                vm.errorMessage?.let { ErrorCard(message = it) }
                MediaImportProgressView(
                    flowStep = vm.flowStep,
                    scanCurrent = vm.scanCurrent,
                    scanTotal = vm.scanTotal,
                    scanProgress = vm.scanProgress,
                    indexProgress = vm.indexProgress,
                    importProgress = vm.importProgress,
                    importResult = vm.importResult,
                    importJob = vm.importJob,
                    destinationPath = vm.destinationPath,
                    onReset = { vm.resetFlow() },
                )
                SettingsSection(
                    expanded = vm.settingsExpanded,
                    onToggle = { vm.settingsExpanded = !vm.settingsExpanded },
                    configuration = vm.customConfig,
                    onConfigChange = { vm.customConfig = it },
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    onClearCache = { vm.showClearCacheConfirm = true },
                    sourcePath = vm.sourcePath,
                    destinationPath = vm.destinationPath,
                    onStartWatchFolder = { config -> vm.startWatching(config) },
                    watchStatus =
                        activeWatchStatus
                            ?: watchStatuses.values.firstOrNull()
                            ?: org.kryspetrie.fileimport.domain.model.WatchFolderStatus(),
                    scope = scope,
                )
                // Full watch folder management (add/remove/configure watches)
                WatchFolderManagement(watchFolderManager = vm.watchFolderManager)
                if (vm.customConfig.autoOrientEnabled) {
                    AutoOrientIndicator()
                }
                ImportHistorySection(
                    historyEntries = vm.historyEntries,
                    expanded = vm.historyExpanded,
                    onToggle = { vm.historyExpanded = !vm.historyExpanded },
                )
                if (vm.flowStep == MediaImportFlowStep.SETUP) {
                    MediaImportActionBar(
                        canStart = vm.canStart,
                        importMode = vm.importMode,
                        onImportModeChange = {},
                        onStartFlow = { withReview, mode -> vm.startFlow(scope, withReview, mode) },
                    )
                }
            }
        }
    }
}
