package org.kryspetrie.fileimport.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.io.File
import org.kryspetrie.fileimport.application.ReorganizeService
import org.kryspetrie.fileimport.ui.components.pickFolder
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.ConflictResolution
import org.kryspetrie.fileimport.domain.model.FilenamePresets
import org.kryspetrie.fileimport.domain.model.FolderPresets
import org.kryspetrie.fileimport.domain.model.ReorganizeJournalSummary
import org.kryspetrie.fileimport.domain.model.ReorganizeMode
import org.kryspetrie.fileimport.domain.model.ReorganizePhase

@Suppress("UnusedParameter")
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
                Button(onClick = { undoJournal(journal) }) { Text("Undo") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showUndoConfirm = null }) { Text("Cancel") }
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Reorganize Library", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Apply folder and filename patterns to an existing media library.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Folder selection
            OutlinedTextField(
                value = viewModel.folderPath,
                onValueChange = { viewModel.folderPath = it },
                label = { Text("Library Folder") },
                placeholder = { Text("Select folder to reorganize...") },
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

            // Operation mode selection
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Operation Mode", style = MaterialTheme.typography.labelMedium)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        ReorganizeMode.entries.forEach { mode ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(viewModel.reorgMode == mode, { viewModel.reorgMode = mode })
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(viewModel.renameOnly, { viewModel.renameOnly = it })
                Spacer(Modifier.width(4.dp))
                Text(
                    "Rename files only (don't move to subfolders)",
                    style = MaterialTheme.typography.bodyMedium,
                )
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

            // Progress states
            when (viewModel.step) {
                ReorganizeViewModel.ReorgStep.SCANNING -> {
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
                                CircularProgressIndicator(
                                    Modifier.size(24.dp),
                                    strokeWidth = 2.5.dp,
                                )
                                Text(
                                    if (viewModel.progress.phase == ReorganizePhase.SCANNING)
                                        "Scanning files..."
                                    else "Reading metadata...",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                            if (viewModel.progress.total > 0) {
                                Text(
                                    "${viewModel.progress.current} of ${viewModel.progress.total}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                LinearProgressIndicator(
                                    progress = { viewModel.progress.current.toFloat() / viewModel.progress.total },
                                    modifier = Modifier.fillMaxWidth().height(4.dp),
                                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                        }
                    }
                }
                ReorganizeViewModel.ReorgStep.EXECUTING -> {
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
                                CircularProgressIndicator(
                                    Modifier.size(24.dp),
                                    strokeWidth = 2.5.dp,
                                )
                                Text(
                                    when (viewModel.progress.phase) {
                                        ReorganizePhase.ROLLING_BACK -> "Undoing changes..."
                                        ReorganizePhase.UNDOING -> "Undoing changes..."
                                        ReorganizePhase.EXECUTING -> "Reorganizing files..."
                                        else -> "Processing..."
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                            if (viewModel.progress.total > 0) {
                                Text(
                                    "${viewModel.progress.current} of ${viewModel.progress.total}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                LinearProgressIndicator(
                                    progress = { viewModel.progress.current.toFloat() / viewModel.progress.total },
                                    modifier = Modifier.fillMaxWidth().height(4.dp),
                                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                                )
                                Text(
                                    viewModel.progress.currentFile,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                ReorganizeViewModel.ReorgStep.COMPLETE -> {
                    viewModel.result?.let { r ->
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
                                    tint =
                                        if (r.errorCount == 0) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error,
                                )
                                Text(
                                    if (r.errorCount == 0) "Reorganization Complete"
                                    else "Completed with Errors",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                ) {
                                    when (r.operationMode) {
                                        ReorganizeMode.MOVE -> {
                                            StatItem("${r.movedCount}", "Moved")
                                            StatItem("${r.renamedCount}", "Renamed")
                                        }
                                        ReorganizeMode.COPY -> {
                                            StatItem("${r.copiedCount}", "Copied")
                                        }
                                    }
                                    StatItem("${r.skippedCount}", "Skipped")
                                    StatItem("${r.errorCount}", "Errors")
                                }
                                r.journalPath?.let {
                                    Text(
                                        "Undo journal saved. You can reverse these changes at any time.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (r.errors.isNotEmpty()) {
                                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                                    r.errors.take(5).forEach { err ->
                                        Text(
                                            err,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                    if (r.errors.size > 5) {
                                        Text(
                                            "...and ${r.errors.size - 5} more",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                                OutlinedButton(onClick = { viewModel.reset() }) { Text("Done") }
                            }
                        }
                    }
                }
                else -> {}
            }

            // Preview results — summary card
            if (viewModel.step == ReorganizeViewModel.ReorgStep.PREVIEW && viewModel.preview != null) {
                val p = viewModel.preview!!
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Dry Run Preview",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "No files have been changed. Review the planned operations below before applying.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            StatItem("${p.totalFiles}", "Total")
                            StatItem("${p.changedFiles}", "Will Change")
                            StatItem("${p.conflictCount}", "Conflicts")
                            StatItem("${p.newFolderCount}", "New Folders")
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            Text(
                                "Mode: ${p.operationMode}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (p.changedFiles == 0) {
                            Text(
                                "All files are already organized according to this pattern.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Preview results — file-by-file mapping list
            if (viewModel.step == ReorganizeViewModel.ReorgStep.PREVIEW &&
                viewModel.preview != null &&
                viewModel.preview!!.changedFiles > 0
            ) {
                val p = viewModel.preview!!
                val changed = remember(p) { p.mappings.filter { it.isChanged } }
                val rootPath =
                    remember(p) {
                        p.mappings.firstOrNull()?.file?.file?.parentFile?.parent ?: viewModel.folderPath
                    }

                Text("File Changes (${changed.size})", style = MaterialTheme.typography.titleSmall)

                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Current",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(24.dp))
                    Text(
                        "New",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                    )
                }

                OutlinedCard(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    LazyColumn {
                        items(changed) { mapping ->
                            val currentRel =
                                mapping.currentPath.removePrefix(rootPath).removePrefix("/")
                            val newRel = mapping.newPath.removePrefix(rootPath).removePrefix("/")
                            Row(
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        mapping.file.fileName,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        currentRel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    null,
                                    modifier = Modifier.padding(horizontal = 6.dp).size(14.dp),
                                    tint =
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                            alpha = 0.5f
                                        ),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        mapping.newFileName,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (mapping.wouldConflict) {
                                            Icon(
                                                Icons.Default.Warning,
                                                null,
                                                modifier = Modifier.size(12.dp).padding(end = 2.dp),
                                                tint = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                        Text(
                                            newRel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color =
                                                if (mapping.wouldConflict)
                                                    MaterialTheme.colorScheme.error
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }

            // Settings
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { viewModel.settingsExpanded = !viewModel.settingsExpanded }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Default.Tune,
                            null,
                            Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "Organization Settings",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            if (viewModel.settingsExpanded) Icons.Default.ExpandLess
                            else Icons.Default.ExpandMore,
                            "Toggle",
                            Modifier.size(18.dp),
                        )
                    }
                    AnimatedVisibility(
                        viewModel.settingsExpanded,
                        enter = expandVertically(),
                        exit = shrinkVertically(),
                    ) {
                        Column {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Column(
                                Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    "Folder Pattern",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                if (!viewModel.renameOnly) {
                                    OutlinedTextField(
                                        viewModel.config.folderPattern,
                                        { viewModel.config = viewModel.config.copy(folderPattern = it) },
                                        label = { Text("Folder Pattern") },
                                        textStyle = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        FolderPresets.builtIn.take(5).forEach { preset ->
                                            FilterChip(
                                                viewModel.config.folderPattern == preset.pattern,
                                                {
                                                    viewModel.config =
                                                        viewModel.config.copy(folderPattern = preset.pattern)
                                                },
                                                label = {
                                                    Text(
                                                        preset.name,
                                                        style = MaterialTheme.typography.labelSmall,
                                                    )
                                                },
                                                modifier = Modifier.height(28.dp),
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Text(
                                    "Filename",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        viewModel.config.preserveOriginalName,
                                        {
                                            viewModel.config =
                                                viewModel.config.copy(
                                                    preserveOriginalName = it,
                                                    fileNamePattern =
                                                        if (it) "{original}"
                                                        else viewModel.config.fileNamePattern,
                                                )
                                        },
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "Preserve original filename",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                                OutlinedTextField(
                                    viewModel.config.fileNamePattern,
                                    { viewModel.config = viewModel.config.copy(fileNamePattern = it) },
                                    enabled = !viewModel.config.preserveOriginalName,
                                    label = { Text("Filename Pattern") },
                                    textStyle = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                if (!viewModel.config.preserveOriginalName) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        FilenamePresets.builtIn.take(4).forEach { preset ->
                                            FilterChip(
                                                viewModel.config.fileNamePattern == preset.pattern,
                                                {
                                                    viewModel.config =
                                                        viewModel.config.copy(
                                                            fileNamePattern = preset.pattern
                                                        )
                                                },
                                                label = {
                                                    Text(
                                                        preset.name,
                                                        style = MaterialTheme.typography.labelSmall,
                                                    )
                                                },
                                                modifier = Modifier.height(28.dp),
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Text(
                                    "Conflict Resolution",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Row(Modifier.fillMaxWidth()) {
                                    ConflictResolution.entries.forEach { r ->
                                        Row(
                                            Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            RadioButton(
                                                viewModel.config.conflictResolution == r,
                                                { viewModel.config = viewModel.config.copy(conflictResolution = r) },
                                            )
                                            Text(
                                                r.name
                                                    .replace("_", " ")
                                                    .lowercase()
                                                    .replaceFirstChar { it.uppercase() },
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Undo journals
            if (viewModel.journals.isNotEmpty()) {
                var undoExpanded by remember { mutableStateOf(false) }
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column {
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { undoExpanded = !undoExpanded }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Undo,
                                null,
                                Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Column(Modifier.weight(1f)) {
                                Text("Undo History", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${viewModel.journals.size} operations • " +
                                        "${viewModel.journals.sumOf { it.changedFiles }} files changed",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                if (undoExpanded) Icons.Default.ExpandLess
                                else Icons.Default.ExpandMore,
                                "Toggle",
                                Modifier.size(18.dp),
                            )
                        }
                        AnimatedVisibility(
                            undoExpanded,
                            enter = expandVertically(),
                            exit = shrinkVertically(),
                        ) {
                            Column {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Column(
                                    Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    viewModel.journals.forEach { journal ->
                                        Row(
                                            Modifier.fillMaxWidth()
                                                .clip(MaterialTheme.shapes.small)
                                                .clickable {
                                                    if (!journal.undone) viewModel.showUndoConfirm = journal
                                                    else viewModel.selectedJournal = journal
                                                }
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.Undo,
                                                null,
                                                Modifier.size(16.dp),
                                                tint =
                                                    if (journal.undone)
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    else MaterialTheme.colorScheme.primary,
                                            )
                                            Column(Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        journal.rootFolder.substringAfterLast("/"),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(
                                                        "— ${journal.operationMode}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color =
                                                            MaterialTheme.colorScheme
                                                                .onSurfaceVariant,
                                                    )
                                                    if (journal.undone) {
                                                        Spacer(Modifier.width(4.dp))
                                                        Badge {
                                                            Text(
                                                                "Undone",
                                                                Modifier.padding(horizontal = 4.dp),
                                                            )
                                                        }
                                                    }
                                                }
                                                Text(
                                                    "${journal.changedFiles} files • ${journal.timestampString}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color =
                                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            Text(
                                                if (!journal.undone) "Undo" else "View",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom action bar
        if (viewModel.step == ReorganizeViewModel.ReorgStep.SETUP ||
            viewModel.step == ReorganizeViewModel.ReorgStep.PREVIEW
        ) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (viewModel.step == ReorganizeViewModel.ReorgStep.PREVIEW) {
                    OutlinedButton(onClick = { viewModel.reset() }, modifier = Modifier.padding(end = 8.dp)) {
                        Text("Back")
                    }
                }
                if (viewModel.step == ReorganizeViewModel.ReorgStep.SETUP) {
                    Button(onClick = { startPreview() }, enabled = viewModel.folderPath.isNotBlank()) {
                        Icon(Icons.Default.Preview, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Preview Changes")
                    }
                }
                if (viewModel.step == ReorganizeViewModel.ReorgStep.PREVIEW &&
                    viewModel.preview != null &&
                    viewModel.preview!!.changedFiles > 0
                ) {
                    Button(onClick = { executeReorg() }) {
                        Icon(Icons.AutoMirrored.Filled.DriveFileMove, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Apply ${viewModel.preview!!.changedFiles} Changes")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
