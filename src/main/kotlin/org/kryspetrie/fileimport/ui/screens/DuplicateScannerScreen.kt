package org.kryspetrie.fileimport.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.kryspetrie.fileimport.application.DuplicateScannerService
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.DuplicateScannerSessionPreferences
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.domain.port.SettingsPort
import org.kryspetrie.fileimport.ui.components.CenteredContentPane
import org.kryspetrie.fileimport.ui.components.ChunkyScrollbar
import org.kryspetrie.fileimport.ui.components.ConfigWorkLayout
import org.kryspetrie.fileimport.ui.components.DefaultContentPaneMaxWidth
import org.kryspetrie.fileimport.ui.components.SessionPreferencesEffect
import org.kryspetrie.fileimport.ui.components.WorkPanelHeading
import org.kryspetrie.fileimport.ui.components.WorkPanelSectionSpacer
import org.kryspetrie.fileimport.ui.components.shouldCancelDuplicateOperationOnEscape
import org.kryspetrie.fileimport.ui.components.shouldConfirmDuplicateResolveOnEnter
import org.kryspetrie.fileimport.ui.components.shouldLeaveDuplicateResultsOnEscape
import org.kryspetrie.fileimport.ui.components.shouldSubmitSetupOnEnter
import org.kryspetrie.fileimport.ui.i18n.strings
import org.kryspetrie.fileimport.ui.screens.duplicatescanner.DuplicateGroupCard
import org.kryspetrie.fileimport.ui.screens.duplicatescanner.DuplicateResolveConfirmDialog
import org.kryspetrie.fileimport.ui.screens.duplicatescanner.DuplicateResolvingProgress
import org.kryspetrie.fileimport.ui.screens.duplicatescanner.DuplicateResultsView
import org.kryspetrie.fileimport.ui.screens.duplicatescanner.DuplicateScanSetup
import org.kryspetrie.fileimport.ui.screens.duplicatescanner.DuplicateScanningProgress
import org.kryspetrie.fileimport.ui.theme.DefaultSpacing

@Composable
fun DuplicateScannerScreen(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    viewModel: DuplicateScannerViewModel = remember { DuplicateScannerViewModel() },
) {
    val s = strings()
    val scannerService = koinInject<DuplicateScannerService>()
    val settingsPort = koinInject<SettingsPort>()
    val currentSettings by settingsPort.observeSettings().collectAsState(initial = settings)
    val scope = rememberCoroutineScope()

    val sessionPrefs =
        DuplicateScannerSessionPreferences(
            folderPath = viewModel.folderPath,
            enableHash = viewModel.enableHash,
            enableExif = viewModel.enableExif,
            enableSurf = viewModel.enableSurf,
            resolveAction = viewModel.resolveAction.name,
            moveToTrash = viewModel.moveToTrash,
        )
    SessionPreferencesEffect(
        stored = currentSettings.duplicateScannerSessionPreferences,
        current = sessionPrefs,
        onRestore = { prefs ->
            viewModel.folderPath = prefs.folderPath
            viewModel.enableHash = prefs.enableHash
            viewModel.enableExif = prefs.enableExif
            viewModel.enableSurf = prefs.enableSurf
            viewModel.resolveAction = prefs.resolvedResolveAction()
            viewModel.moveToTrash = prefs.moveToTrash
        },
        onPersist = { prefs ->
            onSettingsChange(currentSettings.withDuplicateScannerSessionPreferences(prefs))
        },
    )

    fun startScan() {
        viewModel.errorMessage = null
        viewModel.step = DuplicateScannerViewModel.ScanStep.SCANNING
        viewModel.activeJob =
            scope.launch {
                try {
                    val found =
                        scannerService.scanForDuplicates(
                            viewModel.folderPath,
                            viewModel.buildDedupSettings(),
                        ) {
                            viewModel.scanProgress = it
                        }
                    viewModel.duplicates = found
                    viewModel.step = DuplicateScannerViewModel.ScanStep.RESULTS
                } catch (_: kotlinx.coroutines.CancellationException) {
                    viewModel.step = DuplicateScannerViewModel.ScanStep.SETUP
                } catch (e: Exception) {
                    viewModel.errorMessage = e.message ?: s.t(StringKey.DUP_SCAN_FAILED)
                    viewModel.step = DuplicateScannerViewModel.ScanStep.SETUP
                }
            }
    }

    fun resolveAll() {
        val trashFolder =
            if (viewModel.moveToTrash) File(viewModel.folderPath, "duplicates_review").absolutePath
            else null
        viewModel.showResolveConfirm = false
        viewModel.step = DuplicateScannerViewModel.ScanStep.RESOLVING
        viewModel.activeJob =
            scope.launch {
                try {
                    scannerService.resolveAll(
                        viewModel.duplicates,
                        viewModel.resolveAction,
                        trashFolder,
                    ) { c, t ->
                        viewModel.resolveProgress = c to t
                    }
                    viewModel.duplicates = emptyList()
                    viewModel.step = DuplicateScannerViewModel.ScanStep.RESULTS
                } catch (_: kotlinx.coroutines.CancellationException) {
                    viewModel.step = DuplicateScannerViewModel.ScanStep.RESULTS
                } catch (e: Exception) {
                    viewModel.errorMessage =
                        s.t(StringKey.DUP_RESOLVE_FAILED, "message" to (e.message ?: ""))
                    viewModel.step = DuplicateScannerViewModel.ScanStep.RESULTS
                }
            }
    }

    if (viewModel.showResolveConfirm) {
        DuplicateResolveConfirmDialog(
            duplicateCount = viewModel.duplicates.size,
            resolveAction = viewModel.resolveAction,
            moveToTrash = viewModel.moveToTrash,
            onConfirm = { resolveAll() },
            onDismiss = { viewModel.showResolveConfirm = false },
        )
    }

    Column(
        modifier =
            Modifier.fillMaxSize().onPreviewKeyEvent { keyEvent ->
                val isKeyDown = keyEvent.type == KeyEventType.KeyDown
                when {
                    shouldSubmitSetupOnEnter(
                        isKeyDown = isKeyDown,
                        key = keyEvent.key,
                        isSetupStep = viewModel.step == DuplicateScannerViewModel.ScanStep.SETUP,
                        folderPath = viewModel.folderPath,
                    ) -> {
                        startScan()
                        true
                    }
                    shouldCancelDuplicateOperationOnEscape(
                        isKeyDown = isKeyDown,
                        key = keyEvent.key,
                        isBusy =
                            viewModel.step == DuplicateScannerViewModel.ScanStep.SCANNING ||
                                viewModel.step == DuplicateScannerViewModel.ScanStep.RESOLVING,
                    ) -> {
                        viewModel.cancelOperation()
                        true
                    }
                    shouldConfirmDuplicateResolveOnEnter(
                        isKeyDown = isKeyDown,
                        key = keyEvent.key,
                        isResultsStep =
                            viewModel.step == DuplicateScannerViewModel.ScanStep.RESULTS,
                        hasDuplicates = viewModel.duplicates.isNotEmpty(),
                        dialogOpen = viewModel.showResolveConfirm,
                    ) -> {
                        viewModel.showResolveConfirm = true
                        true
                    }
                    shouldLeaveDuplicateResultsOnEscape(
                        isKeyDown = isKeyDown,
                        key = keyEvent.key,
                        isResultsStep =
                            viewModel.step == DuplicateScannerViewModel.ScanStep.RESULTS,
                        dialogOpen = viewModel.showResolveConfirm,
                    ) -> {
                        viewModel.reset()
                        true
                    }
                    else -> false
                }
            }
    ) {
        // Single header matching other pages
        Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            CenteredContentPane(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    s.t(StringKey.DUP_TITLE),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    s.t(StringKey.DUP_DESCRIPTION),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        ChunkyScrollbar(modifier = Modifier.weight(1f)) {
            ConfigWorkLayout(
                modifier = Modifier.padding(DefaultSpacing.lg),
                contentSpacing = DefaultSpacing.md,
                configuration = {
                    if (viewModel.step == DuplicateScannerViewModel.ScanStep.SETUP) {
                        DuplicateScanSetup(
                            folderPath = viewModel.folderPath,
                            onFolderPathChange = { viewModel.folderPath = it },
                            enableHash = viewModel.enableHash,
                            onEnableHashChange = { viewModel.enableHash = it },
                            enableExif = viewModel.enableExif,
                            onEnableExifChange = { viewModel.enableExif = it },
                            enableSurf = viewModel.enableSurf,
                            onEnableSurfChange = { viewModel.enableSurf = it },
                            errorMessage = null,
                        )
                    }
                    if (viewModel.step == DuplicateScannerViewModel.ScanStep.RESULTS) {
                        DuplicateResultsView(
                            duplicates = viewModel.duplicates,
                            totalDupeFiles = viewModel.totalDupeFiles,
                            totalWastedBytes = viewModel.totalWastedBytes,
                            resolveAction = viewModel.resolveAction,
                            onResolveActionChange = { viewModel.resolveAction = it },
                            moveToTrash = viewModel.moveToTrash,
                            onMoveToTrashChange = { viewModel.moveToTrash = it },
                            onReset = { viewModel.reset() },
                        )
                    }
                },
                work = {
                    WorkPanelHeading(s.t(StringKey.TAB_ACTIONS))
                    when (viewModel.step) {
                        DuplicateScannerViewModel.ScanStep.SCANNING,
                        DuplicateScannerViewModel.ScanStep.RESOLVING -> {
                            OutlinedButton(
                                onClick = { viewModel.cancelOperation() },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(s.t(StringKey.ACTION_CANCEL))
                            }
                        }
                        DuplicateScannerViewModel.ScanStep.RESULTS -> {
                            if (viewModel.duplicates.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = { viewModel.reset() },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(s.t(StringKey.ACTION_BACK))
                                }
                                Button(
                                    onClick = { viewModel.showResolveConfirm = true },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(
                                        Icons.Default.AutoFixHigh,
                                        null,
                                        Modifier.size(DefaultSpacing.iconMedium),
                                    )
                                    Spacer(Modifier.width(DefaultSpacing.sm + DefaultSpacing.xs))
                                    Text(
                                        s.t(
                                            StringKey.DUP_RESOLVE_ALL,
                                            "count" to viewModel.duplicates.size.toString(),
                                        )
                                    )
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { viewModel.reset() },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(s.t(StringKey.ACTION_BACK))
                                }
                            }
                        }
                        DuplicateScannerViewModel.ScanStep.SETUP -> {
                            Button(
                                onClick = { startScan() },
                                enabled = viewModel.folderPath.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    null,
                                    Modifier.size(DefaultSpacing.iconMedium),
                                )
                                Spacer(Modifier.width(DefaultSpacing.sm + DefaultSpacing.xs))
                                Text(s.t(StringKey.DUP_SCAN))
                            }
                        }
                    }

                    val showStatus =
                        viewModel.errorMessage != null ||
                            viewModel.step == DuplicateScannerViewModel.ScanStep.SCANNING ||
                            viewModel.step == DuplicateScannerViewModel.ScanStep.RESOLVING
                    if (showStatus) {
                        WorkPanelSectionSpacer()
                        WorkPanelHeading(s.t(StringKey.TAB_RUN_STATUS))
                    }
                    viewModel.errorMessage?.let { msg ->
                        Text(
                            msg,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (viewModel.step == DuplicateScannerViewModel.ScanStep.SCANNING) {
                        DuplicateScanningProgress(
                            phase = viewModel.scanProgress.phase,
                            current = viewModel.scanProgress.current,
                            total = viewModel.scanProgress.total,
                        )
                    }
                    if (viewModel.step == DuplicateScannerViewModel.ScanStep.RESOLVING) {
                        DuplicateResolvingProgress(
                            current = viewModel.resolveProgress.first,
                            total = viewModel.resolveProgress.second,
                        )
                    }
                },
            )
        }

        // Duplicate group list (outside of scrollable area for better performance)
        if (
            viewModel.step == DuplicateScannerViewModel.ScanStep.RESULTS &&
                viewModel.duplicates.isNotEmpty()
        ) {
            LazyColumn(
                modifier =
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = DefaultSpacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(DefaultSpacing.md),
            ) {
                items(viewModel.duplicates, key = { it.primaryImage.id }) { group ->
                    Column(
                        modifier =
                            Modifier.widthIn(max = DefaultContentPaneMaxWidth).fillMaxWidth()
                    ) {
                        DuplicateGroupCard(
                            group = group,
                            onSetPrimary = { selectedId ->
                                viewModel.setPrimaryImage(group.primaryImage.id, selectedId)
                            },
                        )
                    }
                }
            }
        }
    }
}
