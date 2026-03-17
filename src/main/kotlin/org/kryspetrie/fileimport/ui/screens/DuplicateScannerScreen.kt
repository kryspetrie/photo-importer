package org.kryspetrie.fileimport.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.kryspetrie.fileimport.application.DuplicateAction
import org.kryspetrie.fileimport.application.DuplicateScannerService
import org.kryspetrie.fileimport.application.ScanProgress
import org.kryspetrie.fileimport.domain.model.*
import org.kryspetrie.fileimport.ui.components.ThumbnailImage
import org.kryspetrie.fileimport.ui.components.formatFileSize

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

private enum class ScanStep {
  SETUP,
  SCANNING,
  RESULTS,
  RESOLVING
}

@Composable
fun DuplicateScannerScreen(settings: AppSettings, onSettingsChange: (AppSettings) -> Unit) {
  val scannerService = koinInject<DuplicateScannerService>()
  val scope = rememberCoroutineScope()

  var folderPath by remember { mutableStateOf("") }
  var step by remember { mutableStateOf(ScanStep.SETUP) }
  var scanProgress by remember { mutableStateOf(ScanProgress()) }
  var duplicates by remember { mutableStateOf<List<DuplicateInfo>>(emptyList()) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var resolveAction by remember { mutableStateOf(DuplicateAction.KEEP_HIGHEST_RES) }
  var moveToTrash by remember { mutableStateOf(true) }
  var resolveProgress by remember { mutableStateOf(0 to 0) }

  // Dedup settings
  var enableHash by remember { mutableStateOf(true) }
  var enableExif by remember { mutableStateOf(true) }
  var enableSurf by remember { mutableStateOf(false) }

  var showResolveConfirm by remember { mutableStateOf(false) }

  fun reset() {
    step = ScanStep.SETUP
    duplicates = emptyList()
    errorMessage = null
    scanProgress = ScanProgress()
  }

  fun startScan() {
    errorMessage = null
    step = ScanStep.SCANNING
    scope.launch {
      try {
        val dedupSettings =
            DeduplicationSettings(
                enableHashDeduplication = enableHash,
                enableExifDeduplication = enableExif,
                enablePerceptualHash = false,
                enableFilenameDeduplication = false,
                enableSurfMatching = enableSurf,
                ignoreDifferentFileTypes = true)
        val found =
            scannerService.scanForDuplicates(folderPath, dedupSettings) { scanProgress = it }
        duplicates = found
        step = ScanStep.RESULTS
      } catch (e: Exception) {
        errorMessage = e.message ?: "Scan failed"
        step = ScanStep.SETUP
      }
    }
  }

  // Resolve all confirm dialog
  if (showResolveConfirm) {
    val trashFolder = if (moveToTrash) File(folderPath, "duplicates_review").absolutePath else null
    AlertDialog(
        onDismissRequest = { showResolveConfirm = false },
        title = { Text("Resolve All Duplicates") },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("This will process ${duplicates.size} duplicate groups.")
            val actionText =
                when (resolveAction) {
                  DuplicateAction.KEEP_HIGHEST_RES -> "Keep highest resolution"
                  DuplicateAction.KEEP_RAW_OVER_JPEG -> "Keep RAW over JPEG"
                  DuplicateAction.KEEP_NEWEST -> "Keep newest file"
                  DuplicateAction.KEEP_OLDEST -> "Keep oldest file"
                  DuplicateAction.KEEP_LARGEST -> "Keep largest file"
                }
            Text("Strategy: $actionText")
            if (moveToTrash) {
              Text(
                  "Removed files will be moved to duplicates_review/ for safe review.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
              Text(
                  "WARNING: Removed files will be permanently deleted!",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.error)
            }
          }
        },
        confirmButton = {
          Button(
              onClick = {
                showResolveConfirm = false
                step = ScanStep.RESOLVING
                scope.launch {
                  try {
                    scannerService.resolveAll(duplicates, resolveAction, trashFolder) { c, t ->
                      resolveProgress = c to t
                    }
                    duplicates = emptyList()
                    step = ScanStep.RESULTS
                  } catch (e: Exception) {
                    errorMessage = "Resolve failed: ${e.message}"
                    step = ScanStep.RESULTS
                  }
                }
              },
              colors =
                  ButtonDefaults.buttonColors(
                      containerColor =
                          if (moveToTrash) MaterialTheme.colorScheme.primary
                          else MaterialTheme.colorScheme.error)) {
                Text("Resolve")
              }
        },
        dismissButton = { TextButton(onClick = { showResolveConfirm = false }) { Text("Cancel") } })
  }

  Column(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text("Find Duplicates", style = MaterialTheme.typography.headlineSmall)
          Text(
              "Scan an existing library to find duplicate files. Duplicates can be resolved automatically or reviewed individually.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant)

          // Folder selection
          OutlinedTextField(
              value = folderPath,
              onValueChange = { folderPath = it },
              label = { Text("Library Folder") },
              placeholder = { Text("Select folder to scan...") },
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

          // Detection methods
          OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text("Detection Methods", style = MaterialTheme.typography.titleSmall)
              Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                  Row(
                      verticalAlignment = Alignment.CenterVertically,
                      modifier = Modifier.padding(vertical = 2.dp)) {
                        Checkbox(enableHash, { enableHash = it }, Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Exact hash match", style = MaterialTheme.typography.bodySmall)
                      }
                }
                Column(Modifier.weight(1f)) {
                  Row(
                      verticalAlignment = Alignment.CenterVertically,
                      modifier = Modifier.padding(vertical = 2.dp)) {
                        Checkbox(enableExif, { enableExif = it }, Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("EXIF metadata match", style = MaterialTheme.typography.bodySmall)
                      }
                }
              }
              Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(vertical = 2.dp)) {
                    Checkbox(enableSurf, { enableSurf = it }, Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("SURF visual matching (slow)", style = MaterialTheme.typography.bodySmall)
                  }
            }
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

          // Scanning progress
          if (step == ScanStep.SCANNING) {
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
                              scanProgress.phase.ifBlank { "Scanning..." },
                              style = MaterialTheme.typography.titleSmall)
                        }
                    if (scanProgress.total > 0) {
                      Text(
                          "${scanProgress.current} of ${scanProgress.total}",
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant)
                      LinearProgressIndicator(
                          progress = { scanProgress.current.toFloat() / scanProgress.total },
                          modifier = Modifier.fillMaxWidth().height(4.dp),
                          trackColor = MaterialTheme.colorScheme.outlineVariant)
                    }
                  }
            }
          }

          // Resolving progress
          if (step == ScanStep.RESOLVING) {
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
                              "Resolving duplicates...",
                              style = MaterialTheme.typography.titleSmall)
                        }
                    if (resolveProgress.second > 0) {
                      Text(
                          "${resolveProgress.first} of ${resolveProgress.second}",
                          style = MaterialTheme.typography.bodySmall)
                      LinearProgressIndicator(
                          progress = { resolveProgress.first.toFloat() / resolveProgress.second },
                          modifier = Modifier.fillMaxWidth().height(4.dp),
                          trackColor = MaterialTheme.colorScheme.outlineVariant)
                    }
                  }
            }
          }

          // Results
          if (step == ScanStep.RESULTS) {
            if (duplicates.isEmpty()) {
              OutlinedCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                      Icon(
                          Icons.Default.CheckCircle,
                          null,
                          Modifier.size(36.dp),
                          tint = MaterialTheme.colorScheme.primary)
                      Text("No Duplicates Found", style = MaterialTheme.typography.titleSmall)
                      Text(
                          "Your library is clean!",
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant)
                      OutlinedButton(onClick = { reset() }) { Text("Done") }
                    }
              }
            } else {
              // Summary
              val totalDupeFiles = duplicates.sumOf { it.duplicateImages.size }
              val totalWastedBytes =
                  duplicates.sumOf { group -> group.duplicateImages.sumOf { it.fileSize } }

              OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  Text(
                      "Found ${duplicates.size} duplicate groups",
                      style = MaterialTheme.typography.titleSmall)
                  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                      Text("$totalDupeFiles", style = MaterialTheme.typography.titleSmall)
                      Text(
                          "Duplicate files",
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                      Text(
                          formatFileSize(totalWastedBytes),
                          style = MaterialTheme.typography.titleSmall)
                      Text(
                          "Reclaimable space",
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                  }
                }
              }

              // Resolution controls
              OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  Text("Auto-Resolve Strategy", style = MaterialTheme.typography.titleSmall)
                  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
                          selected = resolveAction == action,
                          onClick = { resolveAction = action },
                          label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                          modifier = Modifier.height(28.dp))
                    }
                  }
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(moveToTrash, { moveToTrash = it }, Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Move to review folder instead of deleting",
                        style = MaterialTheme.typography.bodySmall)
                  }
                }
              }

              // Duplicate groups list
              Text("Duplicate Groups", style = MaterialTheme.typography.titleSmall)
            }
          }
        }

    // Duplicate group list (outside of scrollable area for better performance)
    if (step == ScanStep.RESULTS && duplicates.isNotEmpty()) {
      LazyColumn(
          modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(duplicates, key = { it.primaryImage.id }) { group ->
              DuplicateGroupCard(
                  group = group,
                  onSetPrimary = { selectedId ->
                    duplicates =
                        duplicates.map { g ->
                          if (g.primaryImage.id != group.primaryImage.id) g
                          else {
                            val all = listOf(g.primaryImage) + g.duplicateImages
                            val newPrimary = all.first { it.id == selectedId }
                            val newDuplicates = all.filter { it.id != selectedId }
                            g.copy(primaryImage = newPrimary, duplicateImages = newDuplicates)
                          }
                        }
                  })
            }
          }
    }

    // Bottom action bar
    if (step == ScanStep.SETUP || step == ScanStep.RESULTS) {
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically) {
            if (step == ScanStep.RESULTS && duplicates.isNotEmpty()) {
              OutlinedButton(onClick = { reset() }, modifier = Modifier.padding(end = 8.dp)) {
                Text("Back")
              }
              Button(onClick = { showResolveConfirm = true }) {
                Icon(Icons.Default.AutoFixHigh, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Resolve All (${duplicates.size} groups)")
              }
            }
            if (step == ScanStep.SETUP) {
              Button(onClick = { startScan() }, enabled = folderPath.isNotBlank()) {
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
          horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                Icons.Default.FileCopy,
                null,
                Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.error)
            Text(
                "${1 + group.duplicateImages.size} files — ${group.duplicateType.name.replace("_", " ")}",
                style = MaterialTheme.typography.titleSmall)
          }

      val all = listOf(group.primaryImage) + group.duplicateImages
      all.forEach { image ->
        val isPrimary = image.id == group.primaryImage.id
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              ThumbnailImage(
                  file = image.file,
                  maxPx = 60,
                  modifier = Modifier.size(40.dp).clip(MaterialTheme.shapes.small),
                  contentScale = ContentScale.Crop)
              Column(Modifier.weight(1f)) {
                Text(
                    image.fileName,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Text(
                    "${formatFileSize(image.fileSize)} · ${image.fileType.displayName}" +
                        (image.metadata?.resolution?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              if (isPrimary) {
                AssistChip(
                    onClick = {},
                    label = { Text("Keep", style = MaterialTheme.typography.labelSmall) },
                    colors =
                        AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer),
                    modifier = Modifier.height(24.dp))
              } else {
                OutlinedButton(
                    onClick = { onSetPrimary(image.id) },
                    modifier = Modifier.height(24.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                      Text("Set as Keep", style = MaterialTheme.typography.labelSmall)
                    }
              }
            }
      }
    }
  }
}
