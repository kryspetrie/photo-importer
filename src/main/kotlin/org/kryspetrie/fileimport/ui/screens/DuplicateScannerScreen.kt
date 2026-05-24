package org.kryspetrie.fileimport.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.io.File
import org.koin.compose.koinInject
import org.kryspetrie.fileimport.application.DuplicateAction
import org.kryspetrie.fileimport.application.DuplicateScannerService
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.DuplicateInfo
import org.kryspetrie.fileimport.ui.components.ThumbnailImage
import org.kryspetrie.fileimport.ui.components.formatFileSize
import org.kryspetrie.fileimport.ui.components.pickFolder

@Suppress("UnusedParameter")
@Composable
fun DuplicateScannerScreen(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    viewModel: DuplicateScannerViewModel = remember { DuplicateScannerViewModel() },
) {
    val scannerService = koinInject<DuplicateScannerService>()
    val scope = rememberCoroutineScope()

    fun startScan() {
        viewModel.errorMessage = null
        viewModel.step = DuplicateScannerViewModel.ScanStep.SCANNING
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
            } catch (e: Exception) {
                viewModel.errorMessage = e.message ?: "Scan failed"
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
        scope.launch {
            try {
                scannerService.resolveAll(viewModel.duplicates, viewModel.resolveAction, trashFolder) { c, t ->
                    viewModel.resolveProgress = c to t
                }
                viewModel.duplicates = emptyList()
                viewModel.step = DuplicateScannerViewModel.ScanStep.RESULTS
            } catch (e: Exception) {
                viewModel.errorMessage = "Resolve failed: ${e.message}"
                viewModel.step = DuplicateScannerViewModel.ScanStep.RESULTS
            }
        }
    }

    // Resolve all confirm dialog
    if (viewModel.showResolveConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.showResolveConfirm = false },
            title = { Text("Resolve All Duplicates") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("This will process ${viewModel.duplicates.size} duplicate groups.")
                    val actionText =
                        when (viewModel.resolveAction) {
                            DuplicateAction.KEEP_HIGHEST_RES -> "Keep highest resolution"
                            DuplicateAction.KEEP_RAW_OVER_JPEG -> "Keep RAW over JPEG"
                            DuplicateAction.KEEP_NEWEST -> "Keep newest file"
                            DuplicateAction.KEEP_OLDEST -> "Keep oldest file"
                            DuplicateAction.KEEP_LARGEST -> "Keep largest file"
                        }
                    Text("Strategy: $actionText")
                    if (viewModel.moveToTrash) {
                        Text(
                            "Removed files will be moved to duplicates_review/ for safe review.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            "WARNING: Removed files will be permanently deleted!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { resolveAll() },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor =
                                if (viewModel.moveToTrash) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                        ),
                ) {
                    Text("Resolve")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showResolveConfirm = false }) { Text("Cancel") }
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Find Duplicates", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Scan an existing library to find duplicate files. " +
                    "Duplicates can be resolved automatically or reviewed individually.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Folder selection
            OutlinedTextField(
                value = viewModel.folderPath,
                onValueChange = { viewModel.folderPath = it },
                label = { Text("Library Folder") },
                placeholder = { Text("Select folder to scan...") },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium,
                singleLine = true,
                trailingIcon = {
                    IconButton(
                        onClick = { pickFolder("Select Library Folder")?.let { viewModel.folderPath = it } }
                    ) {
                        Icon(Icons.Default.FolderOpen, "Browse", Modifier.size(20.dp))
                    }
                },
                supportingText = {
                    Text("Paste a path or browse", style = MaterialTheme.typography.labelSmall)
                },
            )

            // Detection methods
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Detection Methods", style = MaterialTheme.typography.titleSmall)
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 2.dp),
                            ) {
                                Checkbox(viewModel.enableHash, { viewModel.enableHash = it }, Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Exact hash match", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 2.dp),
                            ) {
                                Checkbox(viewModel.enableExif, { viewModel.enableExif = it }, Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "EXIF metadata match",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp),
                    ) {
                        Checkbox(viewModel.enableSurf, { viewModel.enableSurf = it }, Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "SURF visual matching (slow)",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            // Error
            viewModel.errorMessage?.let {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            null,
                            Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            // Scanning progress
            if (viewModel.step == DuplicateScannerViewModel.ScanStep.SCANNING) {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                            Text(
                                viewModel.scanProgress.phase.ifBlank { "Scanning..." },
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        if (viewModel.scanProgress.total > 0) {
                            Text(
                                "${viewModel.scanProgress.current} of ${viewModel.scanProgress.total}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            LinearProgressIndicator(
                                progress = { viewModel.scanProgress.current.toFloat() / viewModel.scanProgress.total },
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                trackColor = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }
            }

            // Resolving progress
            if (viewModel.step == DuplicateScannerViewModel.ScanStep.RESOLVING) {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                            Text(
                                "Resolving duplicates...",
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        if (viewModel.resolveProgress.second > 0) {
                            Text(
                                "${viewModel.resolveProgress.first} of ${viewModel.resolveProgress.second}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            LinearProgressIndicator(
                                progress = {
                                    viewModel.resolveProgress.first.toFloat() / viewModel.resolveProgress.second
                                },
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                trackColor = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }
            }

            // Results
            if (viewModel.step == DuplicateScannerViewModel.ScanStep.RESULTS) {
                if (viewModel.duplicates.isEmpty()) {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                null,
                                Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text("No Duplicates Found", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Your library is clean!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(onClick = { viewModel.reset() }) { Text("Done") }
                        }
                    }
                } else {
                    // Summary
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "Found ${viewModel.duplicates.size} duplicate groups",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "${viewModel.totalDupeFiles}",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        "Duplicate files",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        formatFileSize(viewModel.totalWastedBytes),
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        "Reclaimable space",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    // Resolution controls
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "Auto-Resolve Strategy",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                DuplicateAction.entries.forEach { action ->
                                    val label =
                                        when (action) {
                                            DuplicateAction.KEEP_HIGHEST_RES -> "Highest Res"
                                            DuplicateAction.KEEP_RAW_OVER_JPEG -> "Prefer RAW"
                                            DuplicateAction.KEEP_NEWEST -> "Newest"
                                            DuplicateAction.KEEP_OLDEST -> "Oldest"
                                            DuplicateAction.KEEP_LARGEST -> "Largest"
                                        }
                                    FilterChip(
                                        selected = viewModel.resolveAction == action,
                                        onClick = { viewModel.resolveAction = action },
                                        label = {
                                            Text(label, style = MaterialTheme.typography.labelSmall)
                                        },
                                        modifier = Modifier.height(28.dp),
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(viewModel.moveToTrash, { viewModel.moveToTrash = it }, Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Move to review folder instead of deleting",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }

                    // Duplicate groups list
                    Text("Duplicate Groups", style = MaterialTheme.typography.titleSmall)
                }
            }
        }

        // Duplicate group list (outside of scrollable area for better performance)
        if (viewModel.step == DuplicateScannerViewModel.ScanStep.RESULTS && viewModel.duplicates.isNotEmpty()) {
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

        // Bottom action bar
        if (viewModel.step == DuplicateScannerViewModel.ScanStep.SETUP ||
            viewModel.step == DuplicateScannerViewModel.ScanStep.RESULTS
        ) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (viewModel.step == DuplicateScannerViewModel.ScanStep.RESULTS && viewModel.duplicates.isNotEmpty()) {
                    OutlinedButton(onClick = { viewModel.reset() }, modifier = Modifier.padding(end = 8.dp)) {
                        Text("Back")
                    }
                    Button(onClick = { viewModel.showResolveConfirm = true }) {
                        Icon(Icons.Default.AutoFixHigh, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Resolve All (${viewModel.duplicates.size} groups)")
                    }
                }
                if (viewModel.step == DuplicateScannerViewModel.ScanStep.SETUP) {
                    Button(onClick = { startScan() }, enabled = viewModel.folderPath.isNotBlank()) {
                        Icon(Icons.Default.Search, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Scan for Duplicates")
                    }
                }
            }
        }
    }
}

@Composable
private fun DuplicateGroupCard(group: DuplicateInfo, onSetPrimary: (String) -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.FileCopy,
                    null,
                    Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Text(
                    "${1 + group.duplicateImages.size} files — ${group.duplicateType.name.replace("_", " ")}",
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            val all = listOf(group.primaryImage) + group.duplicateImages
            all.forEach { image ->
                val isPrimary = image.id == group.primaryImage.id
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ThumbnailImage(
                        file = image.file,
                        maxPx = 60,
                        modifier = Modifier.size(40.dp).clip(MaterialTheme.shapes.small),
                        contentScale = ContentScale.Crop,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            image.fileName,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${formatFileSize(image.fileSize)} · ${image.fileType.displayName}" +
                                (image.metadata?.resolution?.let { " · $it" } .orEmpty()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (isPrimary) {
                        AssistChip(
                            onClick = {},
                            label = { Text("Keep", style = MaterialTheme.typography.labelSmall) },
                            colors =
                                AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                            modifier = Modifier.height(24.dp),
                        )
                    } else {
                        OutlinedButton(
                            onClick = { onSetPrimary(image.id) },
                            modifier = Modifier.height(24.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        ) {
                            Text("Set as Keep", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
