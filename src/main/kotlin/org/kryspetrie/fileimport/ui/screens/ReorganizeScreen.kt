package org.kryspetrie.fileimport.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.kryspetrie.fileimport.application.ReorganizeService
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.ReorganizeJournalSummary
import org.kryspetrie.fileimport.domain.model.ReorganizeMode
import org.kryspetrie.fileimport.ui.components.ChunkyScrollbar
import org.kryspetrie.fileimport.ui.components.FolderSelectionField
import org.kryspetrie.fileimport.ui.components.SettingsToggle
import org.kryspetrie.fileimport.ui.screens.reorganize.ReorganizeActionBar
import org.kryspetrie.fileimport.ui.screens.reorganize.ReorganizePreviewSection
import org.kryspetrie.fileimport.ui.screens.reorganize.ReorganizeProgressSection
import org.kryspetrie.fileimport.ui.screens.reorganize.ReorganizeSettingsSection
import org.kryspetrie.fileimport.ui.screens.reorganize.ReorganizeUndoSection

@Composable
fun ReorganizeScreen(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    viewModel: ReorganizeViewModel = remember { ReorganizeViewModel() },
) {
    val reorgService = koinInject<ReorganizeService>()
    val scope = rememberCoroutineScope()

    if (viewModel.journals.isEmpty()) {
        viewModel.journals = reorgService.listJournals()
    }

    fun startPreview() {
        viewModel.errorMessage = null
        viewModel.step = ReorganizeViewModel.ReorgStep.SCANNING
        scope.launch {
            try {
                val p =
                    reorgService.scanAndPreview(
                        viewModel.folderPath,
                        viewModel.config,
                        viewModel.renameOnly,
                        viewModel.reorgMode,
                    ) {
                        viewModel.progress = it
                    }
                viewModel.preview = p
                viewModel.step = ReorganizeViewModel.ReorgStep.PREVIEW
            } catch (e: Exception) {
                viewModel.errorMessage = e.message ?: "Scan failed"
                viewModel.step = ReorganizeViewModel.ReorgStep.SETUP
            }
        }
    }

    fun executeReorg() {
        val p = viewModel.preview ?: return
        viewModel.step = ReorganizeViewModel.ReorgStep.EXECUTING
        scope.launch {
            try {
                val r = reorgService.execute(p) { viewModel.progress = it }
                viewModel.result = r
                viewModel.journals = reorgService.listJournals()
                viewModel.step = ReorganizeViewModel.ReorgStep.COMPLETE
            } catch (e: Exception) {
                viewModel.errorMessage = e.message ?: "Reorganize failed"
                viewModel.step = ReorganizeViewModel.ReorgStep.SETUP
            }
        }
    }

    fun undoJournal(journal: ReorganizeJournalSummary) {
        viewModel.showUndoConfirm = null
        val journalDir = File(System.getProperty("user.home"), ".petrie-importer/journals")
        val journalFile =
            journalDir.listFiles()?.find { it.nameWithoutExtension == "reorg_${journal.id}" }
        if (journalFile != null && journalFile.exists()) {
            viewModel.step = ReorganizeViewModel.ReorgStep.EXECUTING
            scope.launch {
                try {
                    val r = reorgService.undo(journalFile.absolutePath) { viewModel.progress = it }
                    viewModel.result = r
                    viewModel.journals = reorgService.listJournals()
                    viewModel.step = ReorganizeViewModel.ReorgStep.COMPLETE
                } catch (e: Exception) {
                    viewModel.errorMessage = "Undo failed: ${e.message}"
                    viewModel.step = ReorganizeViewModel.ReorgStep.SETUP
                }
            }
        }
    }

    // Undo confirm dialog
    viewModel.showUndoConfirm?.let { journal ->
        AlertDialog(
            onDismissRequest = { viewModel.showUndoConfirm = null },
            title = { Text("Undo Reorganization") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Reverse all file operations recorded in this journal?")
                    Spacer(Modifier.height(8.dp))
                    Text("Journal details:", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "• Folder: ${journal.rootFolder}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "• Mode: ${journal.operationMode}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "• Files changed: ${journal.changedFiles}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "• Date: ${journal.timestampString}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    when (journal.operationMode) {
                        ReorganizeMode.MOVE ->
                            Text("Files will be moved back to their original locations.")
                        ReorganizeMode.COPY ->
                            Text("Copied files will be deleted (originals were preserved).")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { undoJournal(journal) }) {
                    Text("Undo", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showUndoConfirm = null }) { Text("Cancel") }
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ChunkyScrollbar(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Reorganize Library", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Apply folder and filename patterns to an existing media library.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Folder selection
                FolderSelectionField(
                    value = viewModel.folderPath,
                    onValueChange = { viewModel.folderPath = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "Library Folder",
                    placeholder = "Select folder to reorganize...",
                    title = "Select Library Folder",
                    supportingText = {
                        Text("Paste a path or browse", style = MaterialTheme.typography.labelSmall)
                    },
                )

                // Operation mode selection
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Operation Mode", style = MaterialTheme.typography.labelMedium)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            ReorganizeMode.entries.forEach { mode ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        viewModel.reorgMode == mode,
                                        { viewModel.reorgMode = mode },
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Column {
                                        Text(
                                            when (mode) {
                                                ReorganizeMode.MOVE -> "Move files"
                                                ReorganizeMode.COPY -> "Copy files"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        Text(
                                            when (mode) {
                                                ReorganizeMode.MOVE ->
                                                    "Originals removed, moved to new locations"
                                                ReorganizeMode.COPY ->
                                                    "Originals preserved, copies in new locations"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Rename only toggle
                SettingsToggle(
                    checked = viewModel.renameOnly,
                    onCheckedChange = { viewModel.renameOnly = it },
                    label = "Rename files only",
                    description = "Don't move files to subfolders",
                )

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

                // Progress states
                if (
                    viewModel.step == ReorganizeViewModel.ReorgStep.SCANNING ||
                        viewModel.step == ReorganizeViewModel.ReorgStep.EXECUTING ||
                        viewModel.step == ReorganizeViewModel.ReorgStep.COMPLETE
                ) {
                    ReorganizeProgressSection(
                        step = viewModel.step,
                        progress = viewModel.progress,
                        result = viewModel.result,
                        onReset = { viewModel.reset() },
                    )
                }

                // Preview results
                if (
                    viewModel.step == ReorganizeViewModel.ReorgStep.PREVIEW &&
                        viewModel.preview != null
                ) {
                    ReorganizePreviewSection(
                        preview = viewModel.preview!!,
                        folderPath = viewModel.folderPath,
                        showFileChanges = viewModel.preview!!.changedFiles > 0,
                    )
                }

                // Settings
                ReorganizeSettingsSection(
                    config = viewModel.config,
                    onConfigChange = { viewModel.config = it },
                    settingsExpanded = viewModel.settingsExpanded,
                    onSettingsExpandedChange = { viewModel.settingsExpanded = it },
                    renameOnly = viewModel.renameOnly,
                )

                // Undo journals
                ReorganizeUndoSection(
                    journals = viewModel.journals,
                    onUndoRequest = { viewModel.showUndoConfirm = it },
                    onViewJournal = { viewModel.selectedJournal = it },
                )
            }
        }

        // Bottom action bar
        ReorganizeActionBar(
            step = viewModel.step,
            canPreview = viewModel.folderPath.isNotBlank(),
            changeCount = viewModel.preview?.changedFiles ?: 0,
            onBack = { viewModel.reset() },
            onPreview = { startPreview() },
            onApply = { executeReorg() },
        )
    }
}
