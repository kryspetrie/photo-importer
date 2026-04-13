package org.kryspetrie.fileimport.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.kryspetrie.fileimport.application.ImportService
import org.kryspetrie.fileimport.application.WatchFolderService
import org.kryspetrie.fileimport.domain.model.*
import org.kryspetrie.fileimport.domain.model.ImportHistoryEntry
import org.kryspetrie.fileimport.domain.port.DeviceEvent
import org.kryspetrie.fileimport.domain.port.DevicePort
import org.kryspetrie.fileimport.domain.port.NamingPort
import org.kryspetrie.fileimport.infrastructure.adapter.AppPaths
import org.kryspetrie.fileimport.infrastructure.adapter.ImportHistoryAdapter
import org.kryspetrie.fileimport.ui.components.formatFileSize
import org.kryspetrie.fileimport.ui.screens.components.ImportProgressInline
import org.kryspetrie.fileimport.ui.screens.components.ImportResultInline
import org.kryspetrie.fileimport.ui.screens.components.ProgressCard
import org.kryspetrie.fileimport.ui.screens.components.SettingsSection

private fun pickFile(title: String): String? {
  val os = System.getProperty("os.name").lowercase()
  val isMac = os.contains("mac") || os.contains("darwin")

  if (isMac) {
    System.setProperty("apple.awt.fileDialogForDirectories", "false")
  }
  try {
    val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
    dialog.isVisible = true
    if (dialog.directory != null && dialog.file != null) {
      return File(dialog.directory, dialog.file).absolutePath
    }
  } finally {
    if (isMac) {
      System.setProperty("apple.awt.fileDialogForDirectories", "false")
    }
  }
  return null
}

private fun pickFolder(title: String): String? {
  val os = System.getProperty("os.name").lowercase()
  val isMac = os.contains("mac") || os.contains("darwin")

  if (isMac) {
    System.setProperty("apple.awt.fileDialogForDirectories", "true")
  }
  try {
    val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
    dialog.isVisible = true
    if (dialog.directory != null && dialog.file != null) {
      return File(dialog.directory, dialog.file).absolutePath
    }
  } finally {
    if (isMac) {
      System.setProperty("apple.awt.fileDialogForDirectories", "false")
    }
  }
  return null
}

private enum class FlowStep {
  SETUP,
  SCANNING,
  SELECTING,
  INDEXING,
  CHECKING_DUPES,
  DUPE_REVIEW,
  PREVIEW,
  IMPORTING,
  COMPLETE
}

internal fun configSummary(c: ImportConfiguration): String = buildString {
  if (c.createSubfolders) append(c.folderPattern) else append("Flat")
  append(" · ")
  if (c.preserveOriginalName) append("original names") else append(c.fileNamePattern)
  if (c.verifyAfterCopy) append(" · verify")
  if (c.deleteAfterImport) append(" · delete source")
  if (c.detectVisualDuplicates) append(" · dedup")
}

@Composable
fun MediaImportScreen(settings: AppSettings, onSettingsChange: (AppSettings) -> Unit) {
  val importService = koinInject<ImportService>()
  val namingPort = koinInject<NamingPort>()
  val devicePort = koinInject<DevicePort>()
  val historyAdapter = koinInject<ImportHistoryAdapter>()
  val watchFolderService = koinInject<WatchFolderService>()
  val watchStatus by watchFolderService.status.collectAsState()
  val scope = rememberCoroutineScope()

  // Camera detection
  var detectedDevices by remember { mutableStateOf<List<CameraDevice>>(emptyList()) }
  val currentSettings by rememberUpdatedState(settings)

  var settingsExpanded by remember { mutableStateOf(false) }
  var wantsReview by remember { mutableStateOf(false) }

  // Flow state
  var flowStep by remember { mutableStateOf(FlowStep.SETUP) }
  var images by remember { mutableStateOf<List<ImageFile>>(emptyList()) }
  var filteredImages by remember { mutableStateOf<List<ImageFile>>(emptyList()) }
  var duplicates by remember { mutableStateOf<List<DuplicateInfo>>(emptyList()) }
  var importProgress by remember { mutableStateOf(ImportProgress()) }
  var importResult by remember { mutableStateOf<ImportResult?>(null) }
  var importJob by remember { mutableStateOf<Job?>(null) }
  var importMode by remember { mutableStateOf(ImportMode.ALL) }

  // Paths - initialized from settings, persists across tabs
  var sourcePath by remember { mutableStateOf(settings.importTabSettings.lastSourcePath) }
  var destinationPath by remember {
    mutableStateOf(
        settings.importTabSettings.lastDestinationPath.ifBlank {
          AppPaths.defaultDestination.absolutePath
        })
  }

  // Sync local state from settings (persists across tabs)
  LaunchedEffect(
      settings.importTabSettings.lastSourcePath, settings.importTabSettings.lastDestinationPath) {
        sourcePath = settings.importTabSettings.lastSourcePath
        if (settings.importTabSettings.lastDestinationPath.isNotBlank()) {
          destinationPath = settings.importTabSettings.lastDestinationPath
        }
      }

  // Persist paths immediately when changed
  LaunchedEffect(sourcePath) {
    if (sourcePath.isNotBlank()) {
      val newSettings =
          settings.withImportTabSettings(
              settings.importTabSettings.withRecentSourcePath(sourcePath))
      onSettingsChange(newSettings)
    }
  }

  LaunchedEffect(destinationPath) {
    if (destinationPath.isNotBlank()) {
      val newSettings =
          settings.withImportTabSettings(
              settings.importTabSettings.withRecentDestinationPath(destinationPath))
      onSettingsChange(newSettings)
    }
  }
  var customConfig by remember { mutableStateOf(ImportConfiguration()) }

  // Detect cameras on launch, then monitor for hot-plug events
  LaunchedEffect(Unit) {
    val initialDevices =
        try {
          devicePort.detectDevices()
        } catch (_: Exception) {
          emptyList()
        }
    detectedDevices = initialDevices

    devicePort.observeDeviceChanges().collect { event ->
      when (event) {
        is DeviceEvent.Connected -> {
          val device = event.device
          detectedDevices = detectedDevices.filter { it.id != device.id } + device
        }
        is DeviceEvent.Disconnected -> {
          detectedDevices = detectedDevices.filter { it.id != event.deviceId }
        }
        is DeviceEvent.MountChanged -> {}
      }
    }
  }

  // Scanning / indexing progress
  var scanProgress by remember { mutableStateOf("") }
  var scanTotal by remember { mutableStateOf(0) }
  var scanCurrent by remember { mutableStateOf(0) }
  var indexProgress by remember { mutableStateOf(IndexProgress()) }

  // Error
  var errorMessage by remember { mutableStateOf<String?>(null) }

  // Dialogs
  var showClearCacheConfirm by remember { mutableStateOf(false) }

  val canStart =
      sourcePath.isNotBlank() && destinationPath.isNotBlank() && flowStep == FlowStep.SETUP

  // Path validation
  val sourceDir = remember(sourcePath) { if (sourcePath.isNotBlank()) File(sourcePath) else null }
  val sourceValid = remember(sourcePath) { sourceDir?.isDirectory == true }
  val destDir =
      remember(destinationPath) {
        if (destinationPath.isNotBlank()) File(destinationPath) else null
      }
  val destValid = remember(destinationPath) { destDir?.isDirectory == true }

  fun resetFlow() {
    flowStep = FlowStep.SETUP
    images = emptyList()
    filteredImages = emptyList()
    duplicates = emptyList()
    importResult = null
    errorMessage = null
    scanProgress = ""
    scanTotal = 0
    scanCurrent = 0
    indexProgress = IndexProgress()
  }

  fun doImport(toImport: List<ImageFile> = filteredImages) {
    flowStep = FlowStep.IMPORTING
    importProgress = ImportProgress()
    importResult = null
    importJob =
        scope.launch {
          try {
            val result =
                importService.executeImport(toImport, destinationPath, customConfig) { p ->
                  importProgress = p
                }
            importResult = result
            // Save detailed history entry with per-file information
            result.historyEntry?.let { entry -> historyAdapter.addEntry(entry) }
                ?: run {
                  // Fallback for backward compatibility
                  historyAdapter.addEntry(
                      ImportHistoryEntry(
                          timestamp = System.currentTimeMillis(),
                          timestampString =
                              ImportHistoryEntry.createTimestampString(System.currentTimeMillis()),
                          sourcePath = sourcePath,
                          destinationPath = destinationPath,
                          folderPattern = customConfig.folderPattern,
                          filenamePattern = customConfig.fileNamePattern,
                          totalFiles = result.totalFiles,
                          successCount = result.successCount,
                          errorCount = result.errorCount,
                          skippedCount = result.skippedCount,
                          duplicateCount = result.duplicateCount,
                          deletedSourceCount = result.deletedSourceCount,
                          totalBytes = toImport.sumOf { it.fileSize },
                          durationMs = result.duration))
                }
            importService.indexFolder(destinationPath, true) {}
            flowStep = FlowStep.COMPLETE
          } catch (e: Exception) {
            importResult =
                ImportResult(
                    totalFiles = toImport.size,
                    successCount = 0,
                    errorCount = 1,
                    duplicateCount = 0,
                    skippedCount = 0,
                    deletedSourceCount = 0,
                    endTime = System.currentTimeMillis())
            flowStep = FlowStep.COMPLETE
          }
        }
  }

  fun continueAfterSelection(selectedImages: List<ImageFile> = images.filter { it.isSelected }) {
    importJob =
        scope.launch {
          try {
            var toImport = importService.applyPairFilter(selectedImages, customConfig)

            if (importMode == ImportMode.NEW) {
              flowStep = FlowStep.INDEXING
              importService.indexFolder(destinationPath, true) { indexProgress = it }
              val destHashes = importService.getDestinationHashes(destinationPath)
              toImport = importService.filterAlreadyTransferred(toImport, destHashes, customConfig)
            }

            filteredImages = toImport

            if (customConfig.detectVisualDuplicates) {
              flowStep = FlowStep.CHECKING_DUPES
              val found = importService.findVisualDuplicates(toImport, customConfig)
              if (found.isNotEmpty()) {
                duplicates = found
                flowStep = FlowStep.DUPE_REVIEW
                return@launch
              }
            }

            if (wantsReview) {
              flowStep = FlowStep.PREVIEW
            } else {
              doImport(toImport)
            }
          } catch (e: Exception) {
            errorMessage = e.message ?: "Processing failed"
            flowStep = FlowStep.SETUP
          }
        }
  }

  fun startFlow(withReview: Boolean, mode: ImportMode = importMode) {
    importMode = mode
    wantsReview = withReview
    errorMessage = null

    flowStep = FlowStep.SCANNING
    importJob =
        scope.launch {
          try {
            val scanned =
                importService.scanSource(sourcePath, true) { current, total, file ->
                  scanCurrent = current
                  scanTotal = total
                  scanProgress = file
                }
            images = scanned.map { it.copy(isSelected = true) }

            if (mode == ImportMode.SELECT) {
              flowStep = FlowStep.SELECTING
              return@launch
            }

            continueAfterSelection(scanned.map { it.copy(isSelected = true) })
          } catch (e: Exception) {
            errorMessage = e.message ?: "Scan failed"
            flowStep = FlowStep.SETUP
          }
        }
  }

  // --- Dialogs ---

  if (showClearCacheConfirm) {
    AlertDialog(
        onDismissRequest = { showClearCacheConfirm = false },
        title = { Text("Clear Index Cache") },
        text = { Text("Clear all cached folder indexes? Folders will be re-indexed on next use.") },
        confirmButton = {
          Button(
              onClick = {
                scope.launch { importService.clearAllIndexes() }
                showClearCacheConfirm = false
              }) {
                Text("Clear")
              }
        },
        dismissButton = {
          TextButton(onClick = { showClearCacheConfirm = false }) { Text("Cancel") }
        })
  }

  // --- Media selection dialog ---
  if (flowStep == FlowStep.SELECTING) {
    Dialog(
        onDismissRequest = { flowStep = FlowStep.SETUP },
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
          Surface(
              Modifier.fillMaxSize(0.95f),
              shape = MaterialTheme.shapes.large,
              color = MaterialTheme.colorScheme.surface,
              tonalElevation = 2.dp) {
                ImagePreviewScreen(
                    images = images,
                    onToggleSelection = { id ->
                      images =
                          images.map {
                            if (it.id == id) it.copy(isSelected = !it.isSelected) else it
                          }
                    },
                    onSelectAll = { images = images.map { it.copy(isSelected = true) } },
                    onSelectNone = { images = images.map { it.copy(isSelected = false) } },
                    onContinue = {
                      // Filter to only selected images, then continue
                      val selectedImages = images.filter { it.isSelected }
                      filteredImages = selectedImages
                      flowStep = FlowStep.SETUP
                      continueAfterSelection(selectedImages)
                    },
                    onBack = { flowStep = FlowStep.SETUP },
                    selectedCount = images.count { it.isSelected })
              }
        }
  }

  // --- Duplicate review dialog ---
  if (flowStep == FlowStep.DUPE_REVIEW) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
      Surface(
          Modifier.fillMaxSize(0.95f),
          shape = MaterialTheme.shapes.large,
          color = MaterialTheme.colorScheme.surface,
          tonalElevation = 2.dp) {
            DuplicateReviewScreen(
                duplicates = duplicates,
                onResolution = { _, _ -> },
                onContinue = { if (wantsReview) flowStep = FlowStep.PREVIEW else doImport() },
                onBack = { resetFlow() })
          }
    }
  }

  // --- Preview dialog ---
  if (flowStep == FlowStep.PREVIEW) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
      Surface(
          Modifier.fillMaxSize(0.95f),
          shape = MaterialTheme.shapes.large,
          color = MaterialTheme.colorScheme.surface,
          tonalElevation = 2.dp) {
            PreviewStructureScreen(
                images = filteredImages,
                sourcePath = sourcePath,
                destinationPath = destinationPath,
                configuration = customConfig,
                onImport = { doImport() },
                onBack = { resetFlow() })
          }
    }
  }

  // --- Main layout ---
  Column(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text("Import", style = MaterialTheme.typography.headlineSmall)

          // ── #5 / #9: Source & Destination with path validation ──

          // ── #5 / #9: Source & Destination with path validation ──
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = sourcePath,
                    onValueChange = { sourcePath = it },
                    label = { Text("Source Folder") },
                    placeholder = { Text("Select source...") },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    singleLine = true,
                    isError = sourcePath.isNotBlank() && !sourceValid,
                    supportingText = {
                      when {
                        sourcePath.isBlank() ->
                            Text(
                                "Paste a path or browse",
                                style = MaterialTheme.typography.labelSmall)
                        !sourceValid ->
                            Text(
                                "Folder not found",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error)
                        else ->
                            Text(
                                sourceDir!!.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF2E7D32))
                      }
                    })
                OutlinedButton(
                    onClick = { pickFolder("Select Source Folder")?.let { sourcePath = it } },
                    modifier = Modifier.height(56.dp)) {
                      Icon(Icons.Default.FolderOpen, null, Modifier.size(20.dp))
                      Spacer(Modifier.width(4.dp))
                      Text("Browse")
                    }
              }

          // ── Destination ──
          Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = destinationPath,
                onValueChange = { destinationPath = it },
                label = { Text("Destination Folder") },
                placeholder = { Text("Select destination...") },
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyMedium,
                singleLine = true,
                isError = destinationPath.isNotBlank() && !destValid,
                supportingText = {
                  when {
                    destinationPath.isBlank() ->
                        Text("Paste a path or browse", style = MaterialTheme.typography.labelSmall)
                    !destValid ->
                        Text(
                            "Folder not found",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error)
                    else ->
                        Text(
                            destDir!!.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF2E7D32))
                  }
                })
            OutlinedButton(
                onClick = { pickFolder("Select Destination Folder")?.let { destinationPath = it } },
                modifier = Modifier.height(56.dp)) {
                  Icon(Icons.Default.CreateNewFolder, null, Modifier.size(20.dp))
                  Spacer(Modifier.width(4.dp))
                  Text("Browse")
                }
          }

          // Watch folder status
          if (watchStatus.isWatching) {
            OutlinedCard(Modifier.fillMaxWidth()) {
              Row(
                  Modifier.padding(10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        Icons.Default.Visibility,
                        null,
                        Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                      Text(
                          "Watching: ${watchStatus.watchPath}",
                          style = MaterialTheme.typography.bodySmall,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis)
                      Text(
                          "${watchStatus.filesDetected} files detected",
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(
                        onClick = { watchFolderService.stopWatching() },
                        modifier = Modifier.height(28.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)) {
                          Text("Stop", style = MaterialTheme.typography.labelSmall)
                        }
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

          // Progress states
          when (flowStep) {
            FlowStep.SCANNING ->
                ProgressCard("Scanning source folder...", scanCurrent, scanTotal, scanProgress)
            FlowStep.INDEXING ->
                ProgressCard(
                    "Indexing destination...",
                    indexProgress.indexed,
                    indexProgress.total,
                    indexProgress.currentFile)
            FlowStep.CHECKING_DUPES -> ProgressCard("Checking for duplicates...", 0, 0, "")
            FlowStep.IMPORTING ->
                ImportProgressInline(importProgress) {
                  importJob?.cancel()
                  resetFlow()
                }
            // #11: pass destinationPath to ImportResultInline
            FlowStep.COMPLETE ->
                importResult?.let { ImportResultInline(it, destinationPath) { resetFlow() } }
            else -> {}
          }

          // ── #3: Custom settings with 3 collapsible subsections ──
          SettingsSection(
              expanded = settingsExpanded,
              onToggle = { settingsExpanded = !settingsExpanded },
              configuration = customConfig,
              onConfigChange = { customConfig = it },
              settings = settings,
              onSettingsChange = onSettingsChange,
              onClearCache = { showClearCacheConfirm = true },
              sourcePath = sourcePath,
              destinationPath = destinationPath,
              watchFolderService = watchFolderService,
              watchStatus = watchStatus,
              scope = scope)

          // ── #6: Import history (collapsible) ──
          var historyExpanded by remember { mutableStateOf(false) }
          var historyEntries by remember { mutableStateOf<List<ImportHistoryEntry>>(emptyList()) }
          LaunchedEffect(flowStep) { historyEntries = historyAdapter.loadHistory() }

          if (historyEntries.isNotEmpty()) {
            OutlinedCard(Modifier.fillMaxWidth()) {
              Column {
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { historyExpanded = !historyExpanded }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                      Icon(
                          Icons.Default.History,
                          null,
                          Modifier.size(18.dp),
                          tint = MaterialTheme.colorScheme.primary)
                      Column(Modifier.weight(1f)) {
                        Text("Import History", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${historyEntries.size} imports • ${historyEntries.take(10).sumOf { it.successCount }} files",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                      }
                      Icon(
                          if (historyExpanded) Icons.Default.ExpandLess
                          else Icons.Default.ExpandMore,
                          "Toggle",
                          Modifier.size(18.dp))
                    }
                AnimatedVisibility(
                    historyExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                      Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                              historyEntries.take(10).forEach { entry ->
                                Row(
                                    Modifier.fillMaxWidth()
                                        .clip(MaterialTheme.shapes.small)
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                      Icon(
                                          if (entry.errorCount == 0) Icons.Default.CheckCircle
                                          else Icons.Default.Warning,
                                          null,
                                          Modifier.size(16.dp),
                                          tint =
                                              if (entry.errorCount == 0)
                                                  MaterialTheme.colorScheme.primary
                                              else MaterialTheme.colorScheme.error)
                                      Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                          Text(
                                              entry.sourcePath,
                                              style = MaterialTheme.typography.bodySmall,
                                              maxLines = 1,
                                              overflow = TextOverflow.Ellipsis)
                                          Spacer(Modifier.width(4.dp))
                                          Text(
                                              "— ${entry.successCount} files",
                                              style = MaterialTheme.typography.bodySmall,
                                              color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Text(
                                            java.text
                                                .SimpleDateFormat("yyyy-MM-dd HH:mm")
                                                .format(java.util.Date(entry.timestamp)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                      }
                                      Text(
                                          formatFileSize(entry.totalBytes),
                                          style = MaterialTheme.typography.labelSmall,
                                          color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                              }
                            }
                      }
                    }
              }
            }
          }

          // ── #4: Unified bottom action bar ──
          if (flowStep == FlowStep.SETUP) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                  Button(
                      onClick = { startFlow(withReview = false, mode = ImportMode.ALL) },
                      enabled = canStart) {
                        Icon(Icons.Default.PhotoLibrary, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Import All")
                      }
                  Button(
                      onClick = { startFlow(withReview = false, mode = ImportMode.NEW) },
                      enabled = canStart,
                      colors =
                          ButtonDefaults.buttonColors(
                              containerColor = MaterialTheme.colorScheme.secondary,
                              contentColor = MaterialTheme.colorScheme.onSecondary)) {
                        Icon(Icons.Default.NewReleases, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Import New")
                      }
                  Button(
                      onClick = { startFlow(withReview = false, mode = ImportMode.SELECT) },
                      enabled = canStart,
                      colors =
                          ButtonDefaults.buttonColors(
                              containerColor = MaterialTheme.colorScheme.tertiary,
                              contentColor = MaterialTheme.colorScheme.onTertiary)) {
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Select & Import")
                      }
                  Spacer(Modifier.weight(1f))
                  OutlinedButton(
                      onClick = { startFlow(withReview = true, mode = importMode) },
                      enabled = canStart) {
                        Icon(Icons.Default.Preview, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Preview First ▶")
                      }
                }
          }
        }
  }
}
