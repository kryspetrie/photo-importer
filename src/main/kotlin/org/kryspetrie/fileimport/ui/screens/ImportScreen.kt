package org.kryspetrie.fileimport.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.kryspetrie.fileimport.application.ImportService
import org.kryspetrie.fileimport.application.WatchFolderConfig
import org.kryspetrie.fileimport.application.WatchFolderService
import org.kryspetrie.fileimport.domain.model.*
import org.kryspetrie.fileimport.domain.model.ImportHistoryEntry
import org.kryspetrie.fileimport.domain.port.DeviceEvent
import org.kryspetrie.fileimport.domain.port.DevicePort
import org.kryspetrie.fileimport.domain.port.NamingPort
import org.kryspetrie.fileimport.infrastructure.adapter.ImportHistoryAdapter
import org.kryspetrie.fileimport.ui.components.PlaceholderHelpTooltip
import org.kryspetrie.fileimport.ui.components.formatFileSize

private fun pickDirectory(title: String): String? {
  val os = System.getProperty("os.name").lowercase()
  if (os.contains("mac")) {
    System.setProperty("apple.awt.fileDialogForDirectories", "true")
    try {
      val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
      dialog.isVisible = true
      return if (dialog.directory != null && dialog.file != null)
          File(dialog.directory, dialog.file).absolutePath
      else null
    } finally {
      System.setProperty("apple.awt.fileDialogForDirectories", "false")
    }
  }
  try {
    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
  } catch (_: Exception) {}
  val chooser =
      JFileChooser().apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = title
      }
  return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION)
      chooser.selectedFile.absolutePath
  else null
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
fun ImportScreen(settings: AppSettings, onSettingsChange: (AppSettings) -> Unit) {
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

  // Profile
  var selectedProfileId by remember { mutableStateOf(settings.activeProfileId) }
  val selectedProfile = settings.profiles.find { it.id == selectedProfileId }
  val config = selectedProfile?.configuration ?: ImportConfiguration()

  // Paths
  var sourcePath by
      remember(selectedProfileId) { mutableStateOf(selectedProfile?.lastSourcePath ?: "") }
  var destinationPath by
      remember(selectedProfileId) { mutableStateOf(selectedProfile?.lastDestinationPath ?: "") }

  // Import mode and custom config
  var importMode by remember { mutableStateOf(ImportMode.ALL) }
  var customConfig by remember(selectedProfileId) { mutableStateOf(config) }
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

  // Detect cameras on launch, then monitor for hot-plug events
  LaunchedEffect(Unit) {
    val initialDevices =
        try {
          devicePort.detectDevices()
        } catch (_: Exception) {
          emptyList()
        }
    detectedDevices = initialDevices
    if (initialDevices.isNotEmpty() && currentSettings.profiles.isNotEmpty()) {
      val device = initialDevices.first()
      val matchingProfile =
          currentSettings.profiles.find { profile ->
            profile.cameraName.isNotBlank() &&
                (device.name.equals(profile.cameraName, ignoreCase = true) ||
                    device.displayName.equals(profile.cameraName, ignoreCase = true))
          }
      if (matchingProfile != null) {
        selectedProfileId = matchingProfile.id
        sourcePath = device.mountPoint ?: matchingProfile.lastSourcePath
        destinationPath = matchingProfile.lastDestinationPath
        customConfig = matchingProfile.configuration
      }
    }

    devicePort.observeDeviceChanges().collect { event ->
      when (event) {
        is DeviceEvent.Connected -> {
          val device = event.device
          detectedDevices = detectedDevices.filter { it.id != device.id } + device
          if (flowStep == FlowStep.SETUP && currentSettings.profiles.isNotEmpty()) {
            val matchingProfile =
                currentSettings.profiles.find { profile ->
                  profile.cameraName.isNotBlank() &&
                      (device.name.equals(profile.cameraName, ignoreCase = true) ||
                          device.displayName.equals(profile.cameraName, ignoreCase = true))
                }
            if (matchingProfile != null) {
              selectedProfileId = matchingProfile.id
              sourcePath = device.mountPoint ?: matchingProfile.lastSourcePath
              destinationPath = matchingProfile.lastDestinationPath
              customConfig = matchingProfile.configuration
            }
          }
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
  var showSaveProfileDialog by remember { mutableStateOf(false) }
  var showDeleteConfirm by remember { mutableStateOf<ImportProfile?>(null) }
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

  fun updateProfilePaths() {
    if (selectedProfile != null) {
      val updated =
          selectedProfile.copy(
              lastSourcePath = sourcePath,
              lastDestinationPath = destinationPath,
              updatedAt = System.currentTimeMillis())
      onSettingsChange(
          settings.copy(
              profiles = settings.profiles.map { if (it.id == updated.id) updated else it }))
    }
  }

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
            result.historyEntry?.let { entry ->
              historyAdapter.addEntry(entry)
            } ?: run {
              // Fallback for backward compatibility
              historyAdapter.addEntry(
                  ImportHistoryEntry(
                      timestamp = System.currentTimeMillis(),
                      timestampString = ImportHistoryEntry.createTimestampString(System.currentTimeMillis()),
                      sourcePath = sourcePath,
                      destinationPath = destinationPath,
                      profileName = selectedProfile?.name ?: "Default",
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
    updateProfilePaths()
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
  if (showSaveProfileDialog) {
    val detectedCamera = detectedDevices.find { it.mountPoint == sourcePath }
    SaveProfileDialog(
        detectedCameraName = detectedCamera?.displayName ?: "",
        onSave = { name, cameraName ->
          showSaveProfileDialog = false
          val profile =
              ImportProfile(
                  name = name,
                  configuration = customConfig,
                  cameraName = cameraName,
                  lastSourcePath = sourcePath,
                  lastDestinationPath = destinationPath)
          onSettingsChange(
              settings.copy(profiles = settings.profiles + profile, activeProfileId = profile.id))
          selectedProfileId = profile.id
        },
        onDismiss = { showSaveProfileDialog = false })
  }

  showDeleteConfirm?.let { profile ->
    AlertDialog(
        onDismissRequest = { showDeleteConfirm = null },
        title = { Text("Delete Profile") },
        text = { Text("Delete profile \"${profile.name}\"? This cannot be undone.") },
        confirmButton = {
          Button(
              onClick = {
                onSettingsChange(
                    settings.copy(
                        profiles = settings.profiles.filter { it.id != profile.id },
                        activeProfileId =
                            if (settings.activeProfileId == profile.id) null
                            else settings.activeProfileId))
                if (selectedProfileId == profile.id) selectedProfileId = null
                showDeleteConfirm = null
              },
              colors =
                  ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Text("Delete")
              }
        },
        dismissButton = { TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancel") } })
  }

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
                      flowStep = FlowStep.SETUP
                      continueAfterSelection()
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

          // ── #2: Prominent profile selector (chips) ──
          ProfileChipBar(
              profiles = settings.profiles,
              selectedId = selectedProfileId,
              onSelect = { id ->
                selectedProfileId = id
                if (id != null) {
                  val p = settings.profiles.find { it.id == id }
                  if (p != null) {
                    customConfig = p.configuration
                    if (p.lastSourcePath.isNotBlank()) sourcePath = p.lastSourcePath
                    if (p.lastDestinationPath.isNotBlank()) destinationPath = p.lastDestinationPath
                  }
                } else {
                  customConfig = ImportConfiguration()
                }
              },
              onDelete = { showDeleteConfirm = it },
              onCreateNew = { showSaveProfileDialog = true })

          if (selectedProfile != null) {
            Text(
                configSummary(customConfig),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis)
          } else if (settings.profiles.isEmpty()) {
            Text(
                "Save your first profile to speed up future imports",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

          // ── #8: Camera detection banner ──
          if (detectedDevices.isNotEmpty()) {
            detectedDevices.forEach { device ->
              OutlinedCard(
                  Modifier.fillMaxWidth(),
                  border =
                      BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                          Icon(
                              Icons.Default.CameraAlt,
                              null,
                              Modifier.size(22.dp),
                              tint = MaterialTheme.colorScheme.primary)
                          Column(Modifier.weight(1f)) {
                            Text(
                                "Camera detected: ${device.displayName}",
                                style = MaterialTheme.typography.titleSmall)
                            if (device.mountPoint != null) {
                              Text(
                                  device.mountPoint,
                                  style = MaterialTheme.typography.labelSmall,
                                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                                  maxLines = 1,
                                  overflow = TextOverflow.Ellipsis)
                            }
                          }
                          OutlinedButton(
                              onClick = { device.mountPoint?.let { sourcePath = it } },
                              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                              modifier = Modifier.height(32.dp)) {
                                Text("Use as Source", style = MaterialTheme.typography.labelSmall)
                              }
                          OutlinedButton(
                              onClick = {
                                device.mountPoint?.let { sourcePath = it }
                                showSaveProfileDialog = true
                              },
                              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                              modifier = Modifier.height(32.dp)) {
                                Text("Create Profile", style = MaterialTheme.typography.labelSmall)
                              }
                        }
                  }
            }
          }

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
                    trailingIcon = {
                      IconButton(
                          onClick = {
                            pickDirectory("Select Source Folder")?.let { sourcePath = it }
                          }) {
                            Icon(Icons.Default.FolderOpen, "Browse", Modifier.size(20.dp))
                          }
                    },
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
                OutlinedTextField(
                    value = destinationPath,
                    onValueChange = { destinationPath = it },
                    label = { Text("Destination Folder") },
                    placeholder = { Text("Select destination...") },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    singleLine = true,
                    isError = destinationPath.isNotBlank() && !destValid,
                    trailingIcon = {
                      IconButton(
                          onClick = {
                            pickDirectory("Select Destination Folder")?.let { destinationPath = it }
                          }) {
                            Icon(Icons.Default.CreateNewFolder, "Browse", Modifier.size(20.dp))
                          }
                    },
                    supportingText = {
                      when {
                        destinationPath.isBlank() ->
                            Text(
                                "Paste a path or browse",
                                style = MaterialTheme.typography.labelSmall)
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
              selectedProfile = selectedProfile,
              onSaveProfile = { showSaveProfileDialog = true },
              onUpdateProfile = {
                if (selectedProfile != null) {
                  val updated =
                      selectedProfile.copy(
                          configuration = customConfig,
                          lastSourcePath = sourcePath,
                          lastDestinationPath = destinationPath,
                          updatedAt = System.currentTimeMillis())
                  onSettingsChange(
                      settings.copy(
                          profiles =
                              settings.profiles.map { if (it.id == updated.id) updated else it }))
                }
              },
              onClearCache = { showClearCacheConfirm = true },
              sourcePath = sourcePath,
              destinationPath = destinationPath,
              watchFolderService = watchFolderService,
              watchStatus = watchStatus,
              scope = scope)

          // ── #6: Import history reload on flowStep change ──
          var historyEntries by remember { mutableStateOf<List<ImportHistoryEntry>>(emptyList()) }
          var historyExpanded by remember { mutableStateOf(false) }
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
                      Text(
                          "Import History (${historyEntries.size})",
                          style = MaterialTheme.typography.titleSmall,
                          modifier = Modifier.weight(1f))
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
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                              historyEntries.take(10).forEach { entry ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                      Icon(
                                          if (entry.errorCount == 0) Icons.Default.CheckCircle
                                          else Icons.Default.Warning,
                                          null,
                                          Modifier.size(14.dp),
                                          tint =
                                              if (entry.errorCount == 0)
                                                  MaterialTheme.colorScheme.primary
                                              else MaterialTheme.colorScheme.error)
                                      Column(Modifier.weight(1f)) {
                                        Text(
                                            "${entry.profileName} — ${entry.successCount} files",
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis)
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
                onClick = { startFlow(withReview = true, mode = importMode) }, enabled = canStart) {
                  Icon(Icons.Default.Preview, null, Modifier.size(16.dp))
                  Spacer(Modifier.width(6.dp))
                  Text("Preview First \u25B8")
                }
          }
    }
  }
}

// ---------------------------------------------------------------------------
// #2: Profile chip bar
// ---------------------------------------------------------------------------

@Composable
private fun ProfileChipBar(
    profiles: List<ImportProfile>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onDelete: (ImportProfile) -> Unit,
    onCreateNew: () -> Unit
) {
  Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically) {
        // "No profile" chip
        FilterChip(
            selected = selectedId == null,
            onClick = { onSelect(null) },
            label = { Text("No profile", style = MaterialTheme.typography.labelMedium) },
            leadingIcon = {
              if (selectedId == null) Icon(Icons.Default.Check, null, Modifier.size(16.dp))
              else Icon(Icons.Default.Settings, null, Modifier.size(16.dp))
            })

        profiles.forEach { profile ->
          FilterChip(
              selected = profile.id == selectedId,
              onClick = { onSelect(profile.id) },
              label = { Text(profile.name, style = MaterialTheme.typography.labelMedium) },
              leadingIcon = {
                val icon =
                    if (profile.cameraName.isNotBlank()) Icons.Default.CameraAlt
                    else if (profile.id == selectedId) Icons.Default.Check else Icons.Default.Person
                Icon(icon, null, Modifier.size(16.dp))
              },
              trailingIcon = {
                if (profile.id == selectedId) {
                  Icon(
                      Icons.Default.Close,
                      "Delete",
                      Modifier.size(14.dp).clickable { onDelete(profile) },
                      tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                }
              })
        }

        // "+" create new profile chip
        AssistChip(
            onClick = onCreateNew,
            label = { Text("+", style = MaterialTheme.typography.labelLarge) },
            leadingIcon = { Icon(Icons.Default.Add, "New profile", Modifier.size(16.dp)) })
      }
}

// ---------------------------------------------------------------------------
// #3: Settings section with 3 collapsible subsections
// ---------------------------------------------------------------------------

@Composable
private fun SettingsSection(
    expanded: Boolean,
    onToggle: () -> Unit,
    configuration: ImportConfiguration,
    onConfigChange: (ImportConfiguration) -> Unit,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    selectedProfile: ImportProfile?,
    onSaveProfile: () -> Unit,
    onUpdateProfile: () -> Unit,
    onClearCache: () -> Unit,
    sourcePath: String,
    destinationPath: String,
    watchFolderService: WatchFolderService,
    watchStatus: org.kryspetrie.fileimport.application.WatchFolderStatus,
    scope: kotlinx.coroutines.CoroutineScope
) {
  OutlinedCard(Modifier.fillMaxWidth()) {
    Column {
      Row(
          Modifier.fillMaxWidth()
              .clickable(onClick = onToggle)
              .padding(horizontal = 14.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                Icons.Default.Tune,
                null,
                Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
              Text("Custom Settings", style = MaterialTheme.typography.titleSmall)
              if (!expanded) {
                Text(
                    configSummary(configuration),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
              }
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                "Toggle",
                Modifier.size(18.dp))
          }
      AnimatedVisibility(expanded, enter = expandVertically(), exit = shrinkVertically()) {
        Column {
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {

            // ── Subsection 1: Organization ──
            var orgExpanded by remember { mutableStateOf(true) }
            CollapsibleSubsection(
                title = "Organization",
                icon = Icons.Default.FolderCopy,
                expanded = orgExpanded,
                onToggle = { orgExpanded = !orgExpanded }) {
                  // Folder organization
                  SectionLabel("Folder Organization")
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        configuration.createSubfolders,
                        { onConfigChange(configuration.copy(createSubfolders = it)) })
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Create date-based subfolders", style = MaterialTheme.typography.bodyMedium)
                  }
                  if (configuration.createSubfolders) {
                    OutlinedTextField(
                        configuration.folderPattern,
                        { onConfigChange(configuration.copy(folderPattern = it)) },
                        label = { Text("Folder Pattern") },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth())
                    FolderPresets.examples[configuration.folderPattern]?.let {
                      Text(
                          "Example: $it",
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                          FolderPresets.builtIn.take(4).forEach { preset ->
                            FilterChip(
                                configuration.folderPattern == preset.pattern,
                                {
                                  onConfigChange(configuration.copy(folderPattern = preset.pattern))
                                },
                                label = {
                                  Text(preset.name, style = MaterialTheme.typography.labelSmall)
                                },
                                modifier = Modifier.height(28.dp))
                          }
                        }
                    PlaceholderHelpTooltip(NamePlaceholders.folderPlaceholders)
                  }

                  Spacer(Modifier.height(6.dp))

                  // Filename
                  SectionLabel("Filename")
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        configuration.preserveOriginalName,
                        {
                          onConfigChange(
                              configuration.copy(
                                  preserveOriginalName = it,
                                  fileNamePattern =
                                      if (it) "{original}" else configuration.fileNamePattern))
                        })
                    Spacer(Modifier.width(4.dp))
                    Text("Preserve original filename", style = MaterialTheme.typography.bodyMedium)
                  }
                  OutlinedTextField(
                      configuration.fileNamePattern,
                      { onConfigChange(configuration.copy(fileNamePattern = it)) },
                      enabled = !configuration.preserveOriginalName,
                      label = { Text("Filename Pattern") },
                      textStyle = MaterialTheme.typography.bodyMedium,
                      modifier = Modifier.fillMaxWidth())
                  if (!configuration.preserveOriginalName) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                          FilenamePresets.builtIn.take(4).forEach { preset ->
                            FilterChip(
                                configuration.fileNamePattern == preset.pattern,
                                {
                                  onConfigChange(
                                      configuration.copy(fileNamePattern = preset.pattern))
                                },
                                label = {
                                  Text(preset.name, style = MaterialTheme.typography.labelSmall)
                                },
                                modifier = Modifier.height(28.dp))
                          }
                        }
                  }

                  Spacer(Modifier.height(6.dp))

                  // Conflict resolution
                  SectionLabel("Conflict Resolution")
                  Row(Modifier.fillMaxWidth()) {
                    ConflictResolution.entries.forEach { r ->
                      Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            configuration.conflictResolution == r,
                            { onConfigChange(configuration.copy(conflictResolution = r)) })
                        Text(
                            r.name.replace("_", " ").lowercase().replaceFirstChar {
                              it.uppercase()
                            },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1)
                      }
                    }
                  }

                  // Date source
                  SectionLabel("Date Source")
                  Row(Modifier.fillMaxWidth()) {
                    DateSource.entries.forEach { s ->
                      Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            configuration.dateSource == s,
                            { onConfigChange(configuration.copy(dateSource = s)) })
                        Text(
                            s.name.replace("_", " ").lowercase().replaceFirstChar {
                              it.uppercase()
                            },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1)
                      }
                    }
                  }
                }

            // ── Subsection 2: Deduplication ──
            var dedupExpanded by remember { mutableStateOf(false) }
            CollapsibleSubsection(
                title = "Deduplication",
                icon = Icons.Default.FindReplace,
                expanded = dedupExpanded,
                onToggle = { dedupExpanded = !dedupExpanded }) {
                  // Already-transferred detection
                  SectionLabel("Already-Transferred Detection")
                  Text(
                      "How to detect files that have already been copied to the destination.",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant)
                  Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                      CompactCheck(
                          configuration.detectTransferredByHash,
                          { onConfigChange(configuration.copy(detectTransferredByHash = it)) },
                          "Match by file hash (MD5)")
                    }
                    Column(Modifier.weight(1f)) {
                      CompactCheck(
                          configuration.detectTransferredByExif,
                          { onConfigChange(configuration.copy(detectTransferredByExif = it)) },
                          "Match by EXIF data")
                    }
                  }

                  Spacer(Modifier.height(6.dp))

                  // Visual duplicates
                  SectionLabel("Visual Duplicate Detection")
                  CompactCheck(
                      configuration.detectVisualDuplicates,
                      { onConfigChange(configuration.copy(detectVisualDuplicates = it)) },
                      "Detect visual/resolution duplicates among source files")
                  if (configuration.detectVisualDuplicates) {
                    CompactCheck(
                        configuration.useSurfMatching,
                        { onConfigChange(configuration.copy(useSurfMatching = it)) },
                        "Use SURF feature matching (slow, high accuracy)")
                    if (configuration.useSurfMatching) {
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
                                  "SURF matching is CPU-intensive and will be slow for large collections. It compares visual features between images to find near-duplicates regardless of resolution or format differences.",
                                  style = MaterialTheme.typography.bodySmall,
                                  color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                      }
                    }
                  }
                }

            // ── Subsection 3: Advanced ──
            var advancedExpanded by remember { mutableStateOf(false) }
            CollapsibleSubsection(
                title = "Advanced",
                icon = Icons.Default.SettingsApplications,
                expanded = advancedExpanded,
                onToggle = { advancedExpanded = !advancedExpanded }) {
                  // RAW+JPEG pair handling
                  SectionLabel("RAW+JPEG Pairs")
                  Text(
                      "How to handle cameras that shoot RAW+JPEG simultaneously.",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant)
                  Row(Modifier.fillMaxWidth()) {
                    RawJpegPairMode.entries.forEach { mode ->
                      Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            configuration.rawJpegPairMode == mode,
                            { onConfigChange(configuration.copy(rawJpegPairMode = mode)) })
                        Text(
                            when (mode) {
                              RawJpegPairMode.IMPORT_BOTH -> "Import both"
                              RawJpegPairMode.RAW_ONLY -> "RAW only"
                              RawJpegPairMode.JPEG_ONLY -> "JPEG only"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1)
                      }
                    }
                  }
                  if (configuration.rawJpegPairMode == RawJpegPairMode.IMPORT_BOTH) {
                    CompactCheck(
                        configuration.keepPairsTogether,
                        { onConfigChange(configuration.copy(keepPairsTogether = it)) },
                        "Keep RAW+JPEG pairs in the same folder")
                  }

                  Spacer(Modifier.height(6.dp))

                  // Sidecar files
                  SectionLabel("Sidecar Files")
                  CompactCheck(
                      configuration.importSidecars,
                      { onConfigChange(configuration.copy(importSidecars = it)) },
                      "Import sidecar files (.xmp, .thm, .lrv, .aae, etc.)")
                  if (configuration.importSidecars) {
                    Text(
                        "Sidecar files will be copied alongside their parent media file to the same destination folder.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }

                  Spacer(Modifier.height(6.dp))

                  // Post-import
                  SectionLabel("Post-Import")
                  Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                      CompactCheck(
                          configuration.verifyAfterCopy,
                          { onConfigChange(configuration.copy(verifyAfterCopy = it)) },
                          "Verify copies (hash check)")
                    }
                    Column(Modifier.weight(1f)) {
                      CompactCheck(
                          configuration.deleteAfterImport,
                          { onConfigChange(configuration.copy(deleteAfterImport = it)) },
                          "Delete source after import")
                    }
                  }
                  if (configuration.deleteAfterImport) {
                    Text(
                        "Warning: Source files will be deleted after successful copy and verification.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                  }

                  Spacer(Modifier.height(6.dp))

                  // #1: Watch folder moved into Advanced
                  SectionLabel("Watch Folder")
                  if (!watchStatus.isWatching) {
                    val canWatch = sourcePath.isNotBlank() && destinationPath.isNotBlank()
                    OutlinedButton(
                        onClick = {
                          watchFolderService.startWatching(
                              WatchFolderConfig(
                                  watchPath = sourcePath,
                                  destinationPath = destinationPath,
                                  configuration = configuration,
                                  profileName = selectedProfile?.name ?: "Default"),
                              scope)
                        },
                        enabled = canWatch) {
                          Icon(Icons.Default.Visibility, null, Modifier.size(16.dp))
                          Spacer(Modifier.width(6.dp))
                          Text("Watch Folder")
                        }
                    Text(
                        "Automatically import new files dropped into the source folder.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                  } else {
                    Text(
                        "Currently watching: ${watchStatus.watchPath}",
                        style = MaterialTheme.typography.bodySmall)
                  }

                  Spacer(Modifier.height(6.dp))

                  // Cache management
                  SectionLabel("Index Cache")
                  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onClearCache) {
                      Icon(Icons.Default.Delete, null, Modifier.size(14.dp))
                      Spacer(Modifier.width(4.dp))
                      Text("Clear Cache")
                    }
                  }
                }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(vertical = 4.dp))

            // ── #10: Update profile / save as new ──
            if (selectedProfile != null) {
              Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onUpdateProfile, modifier = Modifier.weight(1f)) {
                  Icon(Icons.Default.Save, null, Modifier.size(16.dp))
                  Spacer(Modifier.width(6.dp))
                  Text(
                      "Update ${selectedProfile.name}",
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(onClick = onSaveProfile, modifier = Modifier.weight(1f)) {
                  Icon(Icons.Default.AddCircle, null, Modifier.size(16.dp))
                  Spacer(Modifier.width(6.dp))
                  Text("Save as New Profile", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
              }
            } else {
              Button(onClick = onSaveProfile, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Save, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Save as New Profile")
              }
            }
          }
        }
      }
    }
  }
}

// ---------------------------------------------------------------------------
// Collapsible subsection within settings
// ---------------------------------------------------------------------------

@Composable
private fun CollapsibleSubsection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
  Column {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
          Text(
              title,
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.weight(1f))
          Icon(
              if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
              "Toggle",
              Modifier.size(16.dp))
        }
    AnimatedVisibility(expanded, enter = expandVertically(), exit = shrinkVertically()) {
      Column(
          Modifier.padding(start = 24.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
          content = content)
    }
  }
}

// ---------------------------------------------------------------------------
// Small helpers
// ---------------------------------------------------------------------------

@Composable
private fun SectionLabel(text: String) {
  Text(
      text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun CompactCheck(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String) {
  Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(vertical = 2.dp)) {
        Checkbox(checked, onCheckedChange, Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
      }
}

// ---------------------------------------------------------------------------
// Progress / result cards
// ---------------------------------------------------------------------------

@Composable
private fun ProgressCard(title: String, current: Int, total: Int, currentFile: String) {
  OutlinedCard(Modifier.fillMaxWidth()) {
    Column(
        Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                Text(title, style = MaterialTheme.typography.titleSmall)
              }
          if (total > 0) {
            Text(
                "$current of $total",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(
                progress = { current.toFloat() / total },
                Modifier.fillMaxWidth().height(4.dp),
                trackColor = MaterialTheme.colorScheme.outlineVariant)
            Text(
                currentFile,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis)
          }
        }
  }
}

@Composable
private fun ImportProgressInline(progress: ImportProgress, onCancel: () -> Unit) {
  OutlinedCard(Modifier.fillMaxWidth()) {
    Column(
        Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                Text("Importing...", style = MaterialTheme.typography.titleSmall)
              }
          if (progress.totalFiles > 0) {
            val percent = (progress.progressPercent * 100).toInt()
            Text(
                "${progress.currentIndex} of ${progress.totalFiles} ($percent%)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                progress.currentFile,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            LinearProgressIndicator(
                progress = { progress.progressPercent },
                Modifier.fillMaxWidth().height(4.dp),
                trackColor = MaterialTheme.colorScheme.outlineVariant)
            Text(
                "${formatFileSize(progress.copiedBytes)} / ${formatFileSize(progress.totalBytes)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          OutlinedButton(onClick = onCancel) { Text("Cancel") }
        }
  }
}

// #11: ImportResultInline now takes destinationPath and shows "Open Destination" button
@Composable
private fun ImportResultInline(result: ImportResult, destinationPath: String, onReset: () -> Unit) {
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
                  if (result.errorCount == 0) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.error)
          Text(
              if (result.errorCount == 0) "Import Complete!" else "Completed with Errors",
              style = MaterialTheme.typography.titleSmall)
          if (destinationPath.isNotBlank()) {
            Text(
                destinationPath,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis)
          }
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatCol("${result.successCount}", "Copied")
            StatCol("${result.skippedCount}", "Skipped")
            StatCol("${result.errorCount}", "Errors")
            StatCol("${result.duplicateCount}", "Duplicates")
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (destinationPath.isNotBlank()) {
              OutlinedButton(
                  onClick = {
                    try {
                      java.awt.Desktop.getDesktop().open(File(destinationPath))
                    } catch (_: Exception) {}
                  }) {
                    Icon(Icons.Default.FolderOpen, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open Destination")
                  }
            }
            OutlinedButton(onClick = onReset) { Text("New Import") }
          }
        }
  }
}

@Composable
private fun StatCol(value: String, label: String) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(value, style = MaterialTheme.typography.titleSmall)
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

// ---------------------------------------------------------------------------
// Save profile dialog
// ---------------------------------------------------------------------------

@Composable
private fun SaveProfileDialog(
    detectedCameraName: String,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
  var name by remember { mutableStateOf("") }
  var cameraName by remember { mutableStateOf(detectedCameraName) }
  Dialog(onDismissRequest = onDismiss) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp) {
          Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Save as Profile", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                name,
                { name = it },
                label = { Text("Profile Name") },
                textStyle = MaterialTheme.typography.bodyMedium,
                singleLine = true,
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                cameraName,
                { cameraName = it },
                label = { Text("Camera Name (optional)") },
                placeholder = { Text("Auto-select this profile when camera is connected") },
                textStyle = MaterialTheme.typography.bodyMedium,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText =
                    if (detectedCameraName.isNotBlank()) {
                      {
                        Text(
                            "Detected: $detectedCameraName",
                            style = MaterialTheme.typography.labelSmall)
                      }
                    } else null)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
              TextButton(onClick = onDismiss) { Text("Cancel") }
              Spacer(Modifier.width(8.dp))
              Button(onClick = { onSave(name, cameraName.trim()) }, enabled = name.isNotBlank()) {
                Text("Save")
              }
            }
          }
        }
  }
}
