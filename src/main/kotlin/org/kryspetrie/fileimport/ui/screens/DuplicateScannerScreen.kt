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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.kryspetrie.fileimport.application.DuplicateScannerService
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.components.ChunkyScrollbar
import org.kryspetrie.fileimport.ui.i18n.strings
import org.kryspetrie.fileimport.ui.screens.duplicatescanner.DuplicateGroupCard
import org.kryspetrie.fileimport.ui.screens.duplicatescanner.DuplicateResolveConfirmDialog
import org.kryspetrie.fileimport.ui.screens.duplicatescanner.DuplicateResolvingProgress
import org.kryspetrie.fileimport.ui.screens.duplicatescanner.DuplicateResultsView
import org.kryspetrie.fileimport.ui.screens.duplicatescanner.DuplicateScanSetup
import org.kryspetrie.fileimport.ui.screens.duplicatescanner.DuplicateScanningProgress

@Composable
fun DuplicateScannerScreen(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    viewModel: DuplicateScannerViewModel = remember { DuplicateScannerViewModel() },
) {
    val s = strings()
    val scannerService = koinInject<DuplicateScannerService>()
    val scope = rememberCoroutineScope()

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
                    // Cancellation is expected — don't show error
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
                    // Cancellation is expected — don't show error
                    viewModel.step = DuplicateScannerViewModel.ScanStep.RESULTS
                } catch (e: Exception) {
                    viewModel.errorMessage =
                        s.t(StringKey.DUP_RESOLVE_FAILED, "message" to (e.message ?: ""))
                    viewModel.step = DuplicateScannerViewModel.ScanStep.RESULTS
                }
            }
    }

    // Resolve all confirm dialog
    if (viewModel.showResolveConfirm) {
        DuplicateResolveConfirmDialog(
            duplicateCount = viewModel.duplicates.size,
            resolveAction = viewModel.resolveAction,
            moveToTrash = viewModel.moveToTrash,
            onConfirm = { resolveAll() },
            onDismiss = { viewModel.showResolveConfirm = false },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ChunkyScrollbar(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(s.t(StringKey.DUP_TITLE), style = MaterialTheme.typography.headlineSmall)
                Text(
                    s.t(StringKey.DUP_DESCRIPTION),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Setup section (folder + detection methods + error)
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
                        errorMessage = viewModel.errorMessage,
                    )
                }

                // Scanning progress
                if (viewModel.step == DuplicateScannerViewModel.ScanStep.SCANNING) {
                    DuplicateScanningProgress(
                        phase = viewModel.scanProgress.phase,
                        current = viewModel.scanProgress.current,
                        total = viewModel.scanProgress.total,
                    )
                }

                // Resolving progress
                if (viewModel.step == DuplicateScannerViewModel.ScanStep.RESOLVING) {
                    DuplicateResolvingProgress(
                        current = viewModel.resolveProgress.first,
                        total = viewModel.resolveProgress.second,
                    )
                }

                // Results (summary + strategy + heading)
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
            }
        }

        // Duplicate group list (outside of scrollable area for better performance)
        if (
            viewModel.step == DuplicateScannerViewModel.ScanStep.RESULTS &&
                viewModel.duplicates.isNotEmpty()
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(viewModel.duplicates, key = { it.primaryImage.id }) { group ->
                    DuplicateGroupCard(
                        group = group,
                        onSetPrimary = { selectedId ->
                            viewModel.setPrimaryImage(group.primaryImage.id, selectedId)
                        },
                    )
                }
            }
        }

        // Bottom action bar — always show (cancel during operations, actions during setup/results)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (viewModel.step) {
                DuplicateScannerViewModel.ScanStep.SCANNING,
                DuplicateScannerViewModel.ScanStep.RESOLVING -> {
                    OutlinedButton(onClick = { viewModel.cancelOperation() }) {
                        Text(s.t(StringKey.ACTION_CANCEL))
                    }
                }
                DuplicateScannerViewModel.ScanStep.RESULTS -> {
                    if (viewModel.duplicates.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { viewModel.reset() },
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Text(s.t(StringKey.ACTION_BACK))
                        }
                        Button(onClick = { viewModel.showResolveConfirm = true }) {
                            Icon(Icons.Default.AutoFixHigh, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                s.t(
                                    StringKey.DUP_RESOLVE_ALL,
                                    "count" to viewModel.duplicates.size.toString(),
                                )
                            )
                        }
                    }
                }
                DuplicateScannerViewModel.ScanStep.SETUP -> {
                    Button(onClick = { startScan() }, enabled = viewModel.folderPath.isNotBlank()) {
                        Icon(Icons.Default.Search, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(s.t(StringKey.DUP_SCAN))
                    }
                }
            }
        }
    }
}
