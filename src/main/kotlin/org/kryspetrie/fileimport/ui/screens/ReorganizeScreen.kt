package org.kryspetrie.fileimport.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.kryspetrie.fileimport.application.ReorganizeService
import org.kryspetrie.fileimport.domain.model.*

private fun pickDir(title: String): String? {
  val os = System.getProperty("os.name").lowercase()
  if (os.contains("mac")) {
    System.setProperty("apple.awt.fileDialogForDirectories", "true")
    try {
      val d = FileDialog(null as Frame?, title, FileDialog.LOAD)
      d.isVisible = true
      return if (d.directory != null && d.file != null) File(d.directory, d.file).absolutePath
      else null
    } finally {
      System.setProperty("apple.awt.fileDialogForDirectories", "false")
    }
  }
  try {
    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
  } catch (_: Exception) {}
  val c =
      JFileChooser().apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = title
      }
  return if (c.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) c.selectedFile.absolutePath
  else null
}

private enum class ReorgStep {
  SETUP,
  SCANNING,
  PREVIEW,
  EXECUTING,
  COMPLETE
}

@Composable
fun ReorganizeScreen(settings: AppSettings, onSettingsChange: (AppSettings) -> Unit) {
  val reorgService = koinInject<ReorganizeService>()
  val scope = rememberCoroutineScope()

  var folderPath by remember { mutableStateOf("") }
  var config by remember { mutableStateOf(ImportConfiguration()) }
  var renameOnly by remember { mutableStateOf(false) }
  var settingsExpanded by remember { mutableStateOf(false) }

  var step by remember { mutableStateOf(ReorgStep.SETUP) }
  var preview by remember { mutableStateOf<ReorganizePreview?>(null) }
  var progress by remember { mutableStateOf(ReorganizeProgress()) }
  var result by remember { mutableStateOf<ReorganizeResult?>(null) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  // Undo state
  var journals by remember { mutableStateOf(reorgService.listJournals()) }
  var showUndoConfirm by remember { mutableStateOf<File?>(null) }

  fun reset() {
    step = ReorgStep.SETUP
    preview = null
    result = null
    errorMessage = null
    progress = ReorganizeProgress()
  }

  fun startPreview() {
    errorMessage = null
    step = ReorgStep.SCANNING
    scope.launch {
      try {
        val p = reorgService.scanAndPreview(folderPath, config, renameOnly) { progress = it }
        preview = p
        step = ReorgStep.PREVIEW
      } catch (e: Exception) {
        errorMessage = e.message ?: "Scan failed"
        step = ReorgStep.SETUP
      }
    }
  }

  fun executeReorg() {
    val p = preview ?: return
    step = ReorgStep.EXECUTING
    scope.launch {
      try {
        val r = reorgService.execute(p) { progress = it }
        result = r
        journals = reorgService.listJournals()
        step = ReorgStep.COMPLETE
      } catch (e: Exception) {
        errorMessage = e.message ?: "Reorganize failed"
        step = ReorgStep.SETUP
      }
    }
  }

  // Undo confirm dialog
  showUndoConfirm?.let { journal ->
    AlertDialog(
        onDismissRequest = { showUndoConfirm = null },
        title = { Text("Undo Reorganization") },
        text = {
          Text(
              "Reverse all file moves recorded in this journal? Files will be moved back to their original locations.")
        },
        confirmButton = {
          Button(
              onClick = {
                val j = journal
                showUndoConfirm = null
                step = ReorgStep.EXECUTING
                scope.launch {
                  try {
                    val r = reorgService.undo(j.absolutePath) { progress = it }
                    result = r
                    journals = reorgService.listJournals()
                    step = ReorgStep.COMPLETE
                  } catch (e: Exception) {
                    errorMessage = "Undo failed: ${e.message}"
                    step = ReorgStep.SETUP
                  }
                }
              }) {
                Text("Undo")
              }
        },
        dismissButton = { TextButton(onClick = { showUndoConfirm = null }) { Text("Cancel") } })
  }

  Column(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text("Reorganize Library", style = MaterialTheme.typography.headlineSmall)
          Text(
              "Apply folder and filename patterns to an existing media library. Files will be moved/renamed in place.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant)

          // Folder selection
          OutlinedTextField(
              value = folderPath,
              onValueChange = { folderPath = it },
              label = { Text("Library Folder") },
              placeholder = { Text("Select folder to reorganize...") },
              modifier = Modifier.fillMaxWidth(),
              textStyle = MaterialTheme.typography.bodyMedium,
              singleLine = true,
              trailingIcon = {
                IconButton(
                    onClick = { pickDir("Select Library Folder")?.let { folderPath = it } }) {
                      Icon(Icons.Default.FolderOpen, "Browse", Modifier.size(20.dp))
                    }
              },
              supportingText = {
                Text("Paste a path or browse", style = MaterialTheme.typography.labelSmall)
              })

          // Rename only toggle
          Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(renameOnly, { renameOnly = it })
            Spacer(Modifier.width(4.dp))
            Text(
                "Rename files only (don't move to subfolders)",
                style = MaterialTheme.typography.bodyMedium)
          }

          // Error
          errorMessage?.let {
            OutlinedCard(Modifier.fillMaxWidth()) {
              Row(
                  Modifier.padding(10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        Icons.Default.Warning,
                        null,
                        Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error)
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                  }
            }
          }

          // Progress states
          when (step) {
            ReorgStep.SCANNING -> {
              OutlinedCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                      Row(
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                            Text(
                                if (progress.phase == ReorganizePhase.SCANNING) "Scanning files..."
                                else "Reading metadata...",
                                style = MaterialTheme.typography.titleSmall)
                          }
                      if (progress.total > 0) {
                        Text(
                            "${progress.current} of ${progress.total}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LinearProgressIndicator(
                            progress = { progress.current.toFloat() / progress.total },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            trackColor = MaterialTheme.colorScheme.outlineVariant)
                      }
                    }
              }
            }
            ReorgStep.EXECUTING -> {
              OutlinedCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                      Row(
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                            Text(
                                if (progress.phase == ReorganizePhase.ROLLING_BACK)
                                    "Undoing changes..."
                                else "Reorganizing files...",
                                style = MaterialTheme.typography.titleSmall)
                          }
                      if (progress.total > 0) {
                        Text(
                            "${progress.current} of ${progress.total}",
                            style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(
                            progress = { progress.current.toFloat() / progress.total },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            trackColor = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            progress.currentFile,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                      }
                    }
              }
            }
            ReorgStep.COMPLETE -> {
              result?.let { r ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                  Column(
                      Modifier.padding(16.dp),
                      horizontalAlignment = Alignment.CenterHorizontally,
                      verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            Icons.Default.CheckCircle,
                            null,
                            Modifier.size(36.dp),
                            tint =
                                if (r.errorCount == 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error)
                        Text(
                            if (r.errorCount == 0) "Reorganization Complete"
                            else "Completed with Errors",
                            style = MaterialTheme.typography.titleSmall)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly) {
                              StatItem("${r.movedCount}", "Moved")
                              StatItem("${r.renamedCount}", "Renamed")
                              StatItem("${r.skippedCount}", "Skipped")
                              StatItem("${r.errorCount}", "Errors")
                            }
                        r.journalPath?.let {
                          Text(
                              "Undo journal saved. You can reverse these changes at any time.",
                              style = MaterialTheme.typography.bodySmall,
                              color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (r.errors.isNotEmpty()) {
                          HorizontalDivider(Modifier.padding(vertical = 4.dp))
                          r.errors.take(5).forEach { err ->
                            Text(
                                err,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error)
                          }
                          if (r.errors.size > 5) {
                            Text(
                                "...and ${r.errors.size - 5} more",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error)
                          }
                        }
                        OutlinedButton(onClick = { reset() }) { Text("Done") }
                      }
                }
              }
            }
            else -> {}
          }

          // Preview results — summary card
          if (step == ReorgStep.PREVIEW && preview != null) {
            val p = preview!!
            OutlinedCard(Modifier.fillMaxWidth()) {
              Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Dry Run Preview",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary)
                Text(
                    "No files have been changed. Review the planned renames below before applying.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                  StatItem("${p.totalFiles}", "Total")
                  StatItem("${p.changedFiles}", "Will Change")
                  StatItem("${p.conflictCount}", "Conflicts")
                  StatItem("${p.newFolderCount}", "New Folders")
                }
                if (p.changedFiles == 0) {
                  Text(
                      "All files are already organized according to this pattern.",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
            }
          }

          // Preview results — file-by-file mapping list
          if (step == ReorgStep.PREVIEW && preview != null && preview!!.changedFiles > 0) {
            val p = preview!!
            val changed = remember(p) { p.mappings.filter { it.isChanged } }
            val rootPath =
                remember(p) {
                  p.mappings.firstOrNull()?.file?.file?.parentFile?.parent ?: folderPath
                }

            Text("File Changes (${changed.size})", style = MaterialTheme.typography.titleSmall)

            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                  Text(
                      "Current",
                      style = MaterialTheme.typography.labelMedium,
                      modifier = Modifier.weight(1f))
                  Spacer(Modifier.width(24.dp))
                  Text(
                      "New",
                      style = MaterialTheme.typography.labelMedium,
                      modifier = Modifier.weight(1f))
                }

            OutlinedCard(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
              LazyColumn {
                items(changed) { mapping ->
                  val currentRel = mapping.currentPath.removePrefix(rootPath).removePrefix("/")
                  val newRel = mapping.newPath.removePrefix(rootPath).removePrefix("/")
                  Row(
                      modifier =
                          Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                      verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                          Text(
                              mapping.file.fileName,
                              style = MaterialTheme.typography.bodySmall,
                              maxLines = 1,
                              overflow = TextOverflow.Ellipsis)
                          Text(
                              currentRel,
                              style = MaterialTheme.typography.labelSmall,
                              color = MaterialTheme.colorScheme.onSurfaceVariant,
                              maxLines = 1,
                              overflow = TextOverflow.Ellipsis)
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            null,
                            modifier = Modifier.padding(horizontal = 6.dp).size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        Column(modifier = Modifier.weight(1f)) {
                          Text(
                              mapping.newFileName,
                              style = MaterialTheme.typography.bodySmall,
                              maxLines = 1,
                              overflow = TextOverflow.Ellipsis)
                          Row(verticalAlignment = Alignment.CenterVertically) {
                            if (mapping.wouldConflict) {
                              Icon(
                                  Icons.Default.Warning,
                                  null,
                                  modifier = Modifier.size(12.dp).padding(end = 2.dp),
                                  tint = MaterialTheme.colorScheme.error)
                            }
                            Text(
                                newRel,
                                style = MaterialTheme.typography.labelSmall,
                                color =
                                    if (mapping.wouldConflict) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                          }
                        }
                      }
                  HorizontalDivider(
                      color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
              }
            }
          }

          // Settings
          OutlinedCard(Modifier.fillMaxWidth()) {
            Column {
              Row(
                  Modifier.fillMaxWidth()
                      .clickable { settingsExpanded = !settingsExpanded }
                      .padding(horizontal = 14.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        Icons.Default.Tune,
                        null,
                        Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "Organization Settings",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f))
                    Icon(
                        if (settingsExpanded) Icons.Default.ExpandLess
                        else Icons.Default.ExpandMore,
                        "Toggle",
                        Modifier.size(18.dp))
                  }
              AnimatedVisibility(
                  settingsExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                    Column {
                      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                      Column(
                          Modifier.padding(14.dp),
                          verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Folder Pattern",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary)
                            if (!renameOnly) {
                              OutlinedTextField(
                                  config.folderPattern,
                                  { config = config.copy(folderPattern = it) },
                                  label = { Text("Folder Pattern") },
                                  textStyle = MaterialTheme.typography.bodyMedium,
                                  modifier = Modifier.fillMaxWidth())
                              Row(
                                  Modifier.fillMaxWidth(),
                                  horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    FolderPresets.builtIn.take(5).forEach { preset ->
                                      FilterChip(
                                          config.folderPattern == preset.pattern,
                                          { config = config.copy(folderPattern = preset.pattern) },
                                          label = {
                                            Text(
                                                preset.name,
                                                style = MaterialTheme.typography.labelSmall)
                                          },
                                          modifier = Modifier.height(28.dp))
                                    }
                                  }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Text(
                                "Filename",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                              Checkbox(
                                  config.preserveOriginalName,
                                  {
                                    config =
                                        config.copy(
                                            preserveOriginalName = it,
                                            fileNamePattern =
                                                if (it) "{original}" else config.fileNamePattern)
                                  })
                              Spacer(Modifier.width(4.dp))
                              Text(
                                  "Preserve original filename",
                                  style = MaterialTheme.typography.bodyMedium)
                            }
                            OutlinedTextField(
                                config.fileNamePattern,
                                { config = config.copy(fileNamePattern = it) },
                                enabled = !config.preserveOriginalName,
                                label = { Text("Filename Pattern") },
                                textStyle = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth())
                            if (!config.preserveOriginalName) {
                              Row(
                                  Modifier.fillMaxWidth(),
                                  horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    FilenamePresets.builtIn.take(4).forEach { preset ->
                                      FilterChip(
                                          config.fileNamePattern == preset.pattern,
                                          {
                                            config = config.copy(fileNamePattern = preset.pattern)
                                          },
                                          label = {
                                            Text(
                                                preset.name,
                                                style = MaterialTheme.typography.labelSmall)
                                          },
                                          modifier = Modifier.height(28.dp))
                                    }
                                  }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Text(
                                "Conflict Resolution",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary)
                            Row(Modifier.fillMaxWidth()) {
                              ConflictResolution.entries.forEach { r ->
                                Row(
                                    Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically) {
                                      RadioButton(
                                          config.conflictResolution == r,
                                          { config = config.copy(conflictResolution = r) })
                                      Text(
                                          r.name.replace("_", " ").lowercase().replaceFirstChar {
                                            it.uppercase()
                                          },
                                          style = MaterialTheme.typography.bodySmall,
                                          maxLines = 1)
                                    }
                              }
                            }
                          }
                    }
                  }
            }
          }

          // Undo journals
          if (journals.isNotEmpty()) {
            OutlinedCard(Modifier.fillMaxWidth()) {
              Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Undo History", style = MaterialTheme.typography.titleSmall)
                journals.take(5).forEach { journal ->
                  Row(
                      Modifier.fillMaxWidth()
                          .clip(MaterialTheme.shapes.small)
                          .clickable { showUndoConfirm = journal }
                          .padding(8.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.Undo,
                            null,
                            Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                          Text(
                              journal.nameWithoutExtension,
                              style = MaterialTheme.typography.bodySmall,
                              maxLines = 1,
                              overflow = TextOverflow.Ellipsis)
                          Text(
                              java.text
                                  .SimpleDateFormat("yyyy-MM-dd HH:mm")
                                  .format(java.util.Date(journal.lastModified())),
                              style = MaterialTheme.typography.labelSmall,
                              color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            "Undo",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                      }
                }
              }
            }
          }
        }

    // Bottom action bar
    if (step == ReorgStep.SETUP || step == ReorgStep.PREVIEW) {
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically) {
            if (step == ReorgStep.PREVIEW) {
              OutlinedButton(onClick = { reset() }, modifier = Modifier.padding(end = 8.dp)) {
                Text("Back")
              }
            }
            if (step == ReorgStep.SETUP) {
              Button(onClick = { startPreview() }, enabled = folderPath.isNotBlank()) {
                Icon(Icons.Default.Preview, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Preview Changes")
              }
            }
            if (step == ReorgStep.PREVIEW && preview != null && preview!!.changedFiles > 0) {
              Button(onClick = { executeReorg() }) {
                Icon(Icons.AutoMirrored.Filled.DriveFileMove, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Apply ${preview!!.changedFiles} Changes")
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
        color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}
