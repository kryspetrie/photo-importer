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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.kryspetrie.fileimport.application.ReorganizeService
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.ReorganizeJournalSummary
import org.kryspetrie.fileimport.domain.model.ReorganizeMode
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.components.ChunkyScrollbar
import org.kryspetrie.fileimport.ui.components.FolderSelectionField
import org.kryspetrie.fileimport.ui.components.SettingsToggle
import org.kryspetrie.fileimport.ui.i18n.strings
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
    val s = strings()
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
                viewModel.errorMessage = e.message ?: s.t(StringKey.REORG_SCAN_FAILED)
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
                viewModel.errorMessage = e.message ?: s.t(StringKey.REORG_FAILED)
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
                    viewModel.errorMessage = "${s.t(StringKey.REORG_FAILED)}: ${e.message}"
                    viewModel.step = ReorganizeViewModel.ReorgStep.SETUP
                }
            }
        }
    }

    viewModel.showUndoConfirm?.let { journal ->
        AlertDialog(
            onDismissRequest = { viewModel.showUndoConfirm = null },
            title = { Text(s.t(StringKey.REORG_UNDO_TITLE)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(s.t(StringKey.REORG_UNDO_MESSAGE))
                    Spacer(Modifier.height(8.dp))
                    Text(s.t(StringKey.REORG_UNDO_JOURNAL), style = MaterialTheme.typography.labelMedium)
                    Text(
                        s.t(StringKey.REORG_UNDO_FOLDER, "path" to journal.rootFolder),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        s.t(StringKey.REORG_UNDO_MODE, "mode" to journal.operationMode.name),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        s.t(StringKey.REORG_UNDO_FILES_CHANGED, "count" to "${journal.changedFiles}"),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        s.t(StringKey.REORG_UNDO_DATE, "date" to journal.timestampString),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    when (journal.operationMode) {
                        ReorganizeMode.MOVE -> Text(s.t(StringKey.REORG_UNDO_MOVE_BACK))
                        ReorganizeMode.COPY -> Text(s.t(StringKey.REORG_UNDO_DELETE_COPIES))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { undoJournal(journal) }) {
                    Text(s.t(StringKey.META_UNDO), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showUndoConfirm = null }) {
                    Text(s.t(StringKey.ACTION_CANCEL))
                }
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ChunkyScrollbar(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    s.t(StringKey.REORG_TITLE),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    s.t(StringKey.REORG_DESCRIPTION),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                FolderSelectionField(
                    value = viewModel.folderPath,
                    onValueChange = { viewModel.folderPath = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = s.t(StringKey.REORG_LIBRARY_FOLDER),
                    placeholder = s.t(StringKey.REORG_LIBRARY_PLACEHOLDER),
                    title = s.t(StringKey.ACTION_SELECT_FOLDER),
                    supportingText = {
                        Text(s.t(StringKey.IMPORT_PATH_HINT), style = MaterialTheme.typography.labelSmall)
                    },
                )

                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            s.t(StringKey.REORG_OPERATION_MODE),
                            style =
                                MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            ReorganizeMode.entries.forEach { mode ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        viewModel.reorgMode == mode,
                                        { viewModel.reorgMode = mode },
                                    )
                                    Spacer(Modifier.width(2.dp))
                                    Text(
                                        when (mode) {
                                            ReorganizeMode.MOVE -> s.t(StringKey.REORG_MOVE)
                                            ReorganizeMode.COPY -> s.t(StringKey.REORG_COPY)
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                }

                SettingsToggle(
                    checked = viewModel.renameOnly,
                    onCheckedChange = { viewModel.renameOnly = it },
                    label = s.t(StringKey.REORG_RENAME_ONLY),
                    description = s.t(StringKey.REORG_RENAME_ONLY_DESC),
                )

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
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }

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

                ReorganizeSettingsSection(
                    config = viewModel.config,
                    onConfigChange = { viewModel.config = it },
                    settingsExpanded = viewModel.settingsExpanded,
                    onSettingsExpandedChange = { viewModel.settingsExpanded = it },
                    renameOnly = viewModel.renameOnly,
                )

                ReorganizeUndoSection(
                    journals = viewModel.journals,
                    onUndoRequest = { viewModel.showUndoConfirm = it },
                    onViewJournal = { viewModel.selectedJournal = it },
                )
            }
        }

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
