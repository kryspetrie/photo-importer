package org.kryspetrie.fileimport.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.ImportMode
import org.kryspetrie.fileimport.domain.model.MediaImportSessionPreferences
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.components.AutoOrientIndicator
import org.kryspetrie.fileimport.ui.components.CenteredContentPane
import org.kryspetrie.fileimport.ui.components.ChunkyScrollbar
import org.kryspetrie.fileimport.ui.components.ConfigWorkLayout
import org.kryspetrie.fileimport.ui.components.SessionPreferencesEffect
import org.kryspetrie.fileimport.ui.components.WorkPanelHeading
import org.kryspetrie.fileimport.ui.components.WorkPanelSectionSpacer
import org.kryspetrie.fileimport.ui.i18n.strings
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
import org.kryspetrie.fileimport.ui.theme.LocalUiDensityScale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaImportScreen(settings: AppSettings, onSettingsChange: (AppSettings) -> Unit) {
    val vm: MediaImportViewModel = koinInject()
    val s = strings()
    val scope = rememberCoroutineScope()
    val density = LocalUiDensityScale.current
    val watchStatuses by vm.watchStatuses.collectAsState()

    // Show the first active watch status in the main flow area
    val activeWatchStatus = watchStatuses.values.firstOrNull { it.isWatching }

    val currentTabSettings =
        settings.importTabSettings
            .withRecentSourcePath(vm.sourcePath)
            .withRecentDestinationPath(vm.destinationPath)
            .withConfiguration(vm.customConfig)
    
    // Combine tab settings and UI state into a single session preferences object
    val sessionPrefs =
        MediaImportSessionPreferences(
            settingsExpanded = vm.settingsExpanded,
            historyExpanded = vm.historyExpanded,
        )
    
    val combinedCurrent = Pair(currentTabSettings, sessionPrefs)
    val combinedStored = Pair(settings.importTabSettings, settings.mediaImportSessionPreferences)
    
    SessionPreferencesEffect(
        stored = combinedStored,
        current = combinedCurrent,
        onRestore = { (tab, prefs) ->
            val initialDest = tab.lastDestinationPath.ifBlank { vm.getDefaultDestination() }
            vm.initializeFromSettings(tab.lastSourcePath, initialDest, tab.configuration)
            vm.settingsExpanded = prefs.settingsExpanded
            vm.historyExpanded = prefs.historyExpanded
        },
        onPersist = { (tab, prefs) ->
            val newSettings = settings
                .withImportTabSettings(tab)
                .withMediaImportSessionPreferences(prefs)
            onSettingsChange(newSettings)
        },
    )

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

    Column(
        modifier =
            Modifier.fillMaxSize().onPreviewKeyEvent { keyEvent ->
                if (
                    keyEvent.type == KeyEventType.KeyDown &&
                        keyEvent.key == Key.Enter &&
                        vm.canStart
                ) {
                    vm.startFlow(scope, withReview = false, mode = ImportMode.ALL)
                    true
                } else {
                    false
                }
            }
    ) {
        // Single header: title + subtitle (merged top bar + hero)
        Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            CenteredContentPane(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    s.t(StringKey.IMPORT_TITLE),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    s.t(StringKey.IMPORT_LANDING_SUBTITLE),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    s.t(StringKey.IMPORT_LANDING_STEPS),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        ChunkyScrollbar(modifier = Modifier.weight(1f)) {
            ConfigWorkLayout(
                modifier = Modifier.padding(density.spacingMd),
                contentSpacing = density.spacingMd,
                configuration = {
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
                    activeWatchStatus?.let { status ->
                        WatchFolderStatusCard(
                            watchStatus = status,
                            onStopWatching = {
                                val configId = status.configId
                                if (configId.isNotEmpty()) vm.stopWatching(configId)
                            },
                        )
                    }
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
                    WatchFolderManagement(watchFolderManager = vm.watchFolderManager)
                    if (vm.customConfig.autoOrientEnabled) {
                        AutoOrientIndicator()
                    }
                    ImportHistorySection(
                        historyEntries = vm.historyEntries,
                        expanded = vm.historyExpanded,
                        onToggle = { vm.historyExpanded = !vm.historyExpanded },
                    )
                },
                work = {
                    if (vm.flowStep == MediaImportFlowStep.SETUP) {
                        WorkPanelHeading(s.t(StringKey.TAB_ACTIONS))
                        MediaImportActionBar(
                            canStart = vm.canStart,
                            onStartFlow = { withReview, mode ->
                                vm.startFlow(scope, withReview, mode)
                            },
                        )
                    }
                    if (
                        vm.flowStep != MediaImportFlowStep.SETUP ||
                            vm.errorMessage != null ||
                            vm.importResult != null
                    ) {
                        if (vm.flowStep == MediaImportFlowStep.SETUP) {
                            WorkPanelSectionSpacer()
                        }
                        WorkPanelHeading(s.t(StringKey.TAB_RUN_STATUS))
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
                    }
                },
            )
        }
    }
}
