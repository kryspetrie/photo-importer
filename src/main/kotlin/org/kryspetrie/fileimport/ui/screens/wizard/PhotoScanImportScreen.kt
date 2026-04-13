package org.kryspetrie.fileimport.ui.screens.wizard

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import org.kryspetrie.fileimport.domain.model.*
import org.kryspetrie.fileimport.domain.port.SettingsPort
import org.kryspetrie.fileimport.infrastructure.adapter.AppPaths
import org.kryspetrie.fileimport.infrastructure.wizard.PhotoScanWizardState
import org.kryspetrie.fileimport.ui.components.PlaceholderHelpTooltip
import org.kryspetrie.fileimport.ui.components.SectionLabel

/** Import screen for the wizard - source selection and configuration. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoScanImportScreen(
    state: PhotoScanWizardState,
    settingsPort: SettingsPort,
    onSettingsChange: (AppSettings) -> Unit,
    onImageSelected: (java.io.File) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
  val settings by settingsPort.observeSettings().collectAsState(initial = AppSettings())
  val cvAutoDetectEnabled by state.cvAutoDetectEnabled.collectAsState()
  val configuration by state.configuration.collectAsState()

  // Expanded settings state
  var settingsExpanded by remember { mutableStateOf(false) }

  // Configuration state (initialized from tab settings, mutable copy for editing)
  var customConfig by remember { mutableStateOf(settings.photoScanImportTabSettings.configuration) }

  // Source path - can be a file or folder
  var sourcePath by remember { mutableStateOf(settings.photoScanImportTabSettings.lastSourcePath) }
  var destinationPath by remember {
    mutableStateOf(
        settings.photoScanImportTabSettings.lastDestinationPath.ifBlank {
          AppPaths.defaultDestination.absolutePath
        })
  }

  // Sync local state from settings (persists across tabs)
  LaunchedEffect(
      settings.photoScanImportTabSettings.lastSourcePath,
      settings.photoScanImportTabSettings.lastDestinationPath) {
        sourcePath = settings.photoScanImportTabSettings.lastSourcePath
        if (settings.photoScanImportTabSettings.lastDestinationPath.isNotBlank()) {
          destinationPath = settings.photoScanImportTabSettings.lastDestinationPath
        }
      }

  // Persist paths immediately when changed
  LaunchedEffect(sourcePath) {
    if (sourcePath.isNotBlank()) {
      val newSettings =
          settings.withPhotoScanImportTabSettings(
              settings.photoScanImportTabSettings.withRecentSourcePath(sourcePath))
      onSettingsChange(newSettings)
    }
  }

  LaunchedEffect(destinationPath) {
    if (destinationPath.isNotBlank()) {
      val newSettings =
          settings.withPhotoScanImportTabSettings(
              settings.photoScanImportTabSettings.withRecentDestinationPath(destinationPath))
      onSettingsChange(newSettings)
    }
  }

  // Validation states
  val sourceFile =
      remember(sourcePath) {
        sourcePath.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() }
      }
  val destDir =
      remember(destinationPath) {
        destinationPath
            .takeIf { it.isNotBlank() }
            ?.let { File(it) }
            ?.takeIf { it.exists() && it.isDirectory }
      }
  val sourceValid = sourceFile != null
  val destValid = destDir != null

  // Can start when either source or destination has valid path
  val canStart = sourceValid && destValid

  Scaffold(
      topBar = {
        TopAppBar(
            title = { Text("Photo Scan Import") },
            navigationIcon = {
              IconButton(onClick = onCancel) { Icon(Icons.Default.Close, "Cancel") }
            })
      },
      content = { paddingValues ->
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {

              // ── Auto-detect Option ──
              Card(
                  modifier = Modifier.fillMaxWidth(),
                  colors =
                      CardDefaults.cardColors(
                          containerColor =
                              MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                          Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Auto-detect bounding boxes",
                                style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Uses computer vision to find photo boundaries",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                          }
                          Switch(
                              checked = cvAutoDetectEnabled,
                              onCheckedChange = { state.setCvAutoDetectEnabled(it) })
                        }
                  }

              // ── Source Selection ──
              Text("Source", style = MaterialTheme.typography.titleMedium)

              // Source path field
              OutlinedTextField(
                  value = sourcePath,
                  onValueChange = { sourcePath = it },
                  label = { Text("Source") },
                  placeholder = { Text("Select source file or folder...") },
                  modifier = Modifier.fillMaxWidth(),
                  textStyle = MaterialTheme.typography.bodyMedium,
                  singleLine = true,
                  isError = sourcePath.isNotBlank() && !sourceValid,
                  supportingText = {
                    when {
                      sourcePath.isBlank() ->
                          Text("Select a scanned image file or folder of images")
                      !sourceValid ->
                          Text("Path not found", color = MaterialTheme.colorScheme.error)
                      sourceFile.isDirectory ->
                          Text("Folder: ${sourceFile.listFiles()?.size ?: 0} files")
                      else -> Text("File: ${sourceFile.name}")
                    }
                  })

              // File/Folder selection buttons
              Row(
                  modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                  horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { pickFile("Select Image File")?.let { sourcePath = it } },
                        modifier = Modifier.weight(1f)) {
                          Icon(Icons.Default.Image, null, Modifier.size(18.dp))
                          Spacer(Modifier.width(4.dp))
                          Text("Select File")
                        }
                    OutlinedButton(
                        onClick = { pickFolder("Select Folder")?.let { sourcePath = it } },
                        modifier = Modifier.weight(1f)) {
                          Icon(Icons.Default.Folder, null, Modifier.size(18.dp))
                          Spacer(Modifier.width(4.dp))
                          Text("Select Folder")
                        }
                  }

              // ── Destination ──
              Text("Destination", style = MaterialTheme.typography.titleMedium)

              OutlinedTextField(
                  value = destinationPath,
                  onValueChange = { destinationPath = it },
                  label = { Text("Destination Folder") },
                  placeholder = { Text("Select destination...") },
                  modifier = Modifier.fillMaxWidth(),
                  textStyle = MaterialTheme.typography.bodyMedium,
                  singleLine = true,
                  isError = destinationPath.isNotBlank() && !destValid,
                  supportingText = {
                    when {
                      destinationPath.isBlank() -> Text("Paste a path or browse")
                      !destValid ->
                          Text("Folder not found", color = MaterialTheme.colorScheme.error)
                      else -> Text(destDir.name)
                    }
                  })

              // Destination selection button
              Row(
                  modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                  horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                          pickFolder("Select Destination Folder")?.let { destinationPath = it }
                        },
                        modifier = Modifier.fillMaxWidth()) {
                          Icon(Icons.Default.CreateNewFolder, null, Modifier.size(18.dp))
                          Spacer(Modifier.width(4.dp))
                          Text("Select Destination Folder")
                        }
                  }

              // ── Import Photo Scans Button ──
              Button(
                  onClick = { sourceFile?.let { onImageSelected(it) } },
                  modifier = Modifier.fillMaxWidth().height(56.dp),
                  enabled = canStart) {
                    Icon(Icons.Default.Scanner, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Import Photo Scan(s)", style = MaterialTheme.typography.titleMedium)
                  }

              // ── Custom Settings ──
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
                        Column(Modifier.weight(1f)) {
                          Text("Custom Settings", style = MaterialTheme.typography.titleSmall)
                          if (!settingsExpanded) {
                            Text(
                                configSummary(customConfig),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                          }
                        }
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
                              verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                // ── Organization ──
                                var orgExpanded by remember { mutableStateOf(true) }
                                PhotoScanCollapsibleSubsection(
                                    title = "Organization",
                                    icon = Icons.Default.FolderCopy,
                                    expanded = orgExpanded,
                                    onToggle = { orgExpanded = !orgExpanded }) {
                                      SectionLabel("Folder Organization")
                                      Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            customConfig.createSubfolders,
                                            {
                                              customConfig =
                                                  customConfig.copy(createSubfolders = it)
                                            })
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            "Create date-based subfolders",
                                            style = MaterialTheme.typography.bodyMedium)
                                      }
                                      if (customConfig.createSubfolders) {
                                        OutlinedTextField(
                                            customConfig.folderPattern,
                                            {
                                              customConfig = customConfig.copy(folderPattern = it)
                                            },
                                            label = { Text("Folder Pattern") },
                                            textStyle = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.fillMaxWidth())
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                              FolderPresets.builtIn.take(4).forEach { preset ->
                                                FilterChip(
                                                    customConfig.folderPattern == preset.pattern,
                                                    {
                                                      customConfig =
                                                          customConfig.copy(
                                                              folderPattern = preset.pattern)
                                                    },
                                                    label = {
                                                      Text(
                                                          preset.name,
                                                          style =
                                                              MaterialTheme.typography.labelSmall)
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
                                            customConfig.preserveOriginalName,
                                            {
                                              customConfig =
                                                  customConfig.copy(
                                                      preserveOriginalName = it,
                                                      fileNamePattern =
                                                          if (it) "{original}"
                                                          else customConfig.fileNamePattern)
                                            })
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            "Preserve original filename",
                                            style = MaterialTheme.typography.bodyMedium)
                                      }
                                      OutlinedTextField(
                                          customConfig.fileNamePattern,
                                          {
                                            customConfig = customConfig.copy(fileNamePattern = it)
                                          },
                                          enabled = !customConfig.preserveOriginalName,
                                          label = { Text("Filename Pattern") },
                                          textStyle = MaterialTheme.typography.bodyMedium,
                                          modifier = Modifier.fillMaxWidth())
                                      if (!customConfig.preserveOriginalName) {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                              FilenamePresets.builtIn.take(4).forEach { preset ->
                                                FilterChip(
                                                    customConfig.fileNamePattern == preset.pattern,
                                                    {
                                                      customConfig =
                                                          customConfig.copy(
                                                              fileNamePattern = preset.pattern)
                                                    },
                                                    label = {
                                                      Text(
                                                          preset.name,
                                                          style =
                                                              MaterialTheme.typography.labelSmall)
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
                                          Row(
                                              Modifier.weight(1f),
                                              verticalAlignment = Alignment.CenterVertically) {
                                                RadioButton(
                                                    customConfig.conflictResolution == r,
                                                    {
                                                      customConfig =
                                                          customConfig.copy(conflictResolution = r)
                                                    })
                                                Text(
                                                    r.name
                                                        .replace("_", " ")
                                                        .lowercase()
                                                        .replaceFirstChar { it.uppercase() },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 1)
                                              }
                                        }
                                      }

                                      // Date source
                                      SectionLabel("Date Source")
                                      Row(Modifier.fillMaxWidth()) {
                                        DateSource.entries.forEach { s ->
                                          Row(
                                              Modifier.weight(1f),
                                              verticalAlignment = Alignment.CenterVertically) {
                                                RadioButton(
                                                    customConfig.dateSource == s,
                                                    {
                                                      customConfig =
                                                          customConfig.copy(dateSource = s)
                                                    })
                                                Text(
                                                    s.name
                                                        .replace("_", " ")
                                                        .lowercase()
                                                        .replaceFirstChar { it.uppercase() },
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
              }
            }
      })

  // Save config to wizard state when it changes
  LaunchedEffect(customConfig) { state.setConfiguration(customConfig) }

  // Save paths and config when they change
  LaunchedEffect(sourcePath, destinationPath, customConfig) {
    val tabSettings =
        settings.photoScanImportTabSettings
            .withRecentSourcePath(sourcePath)
            .withRecentDestinationPath(destinationPath)
            .withConfiguration(customConfig)
    onSettingsChange(settings.withPhotoScanImportTabSettings(tabSettings))
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoScanCollapsibleSubsection(
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
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
          Text(title, style = MaterialTheme.typography.labelLarge)
          Icon(
              if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
              null,
              Modifier.size(16.dp))
        }
    AnimatedVisibility(expanded, enter = expandVertically(), exit = shrinkVertically()) {
      Column(content = content)
    }
  }
}

private fun configSummary(c: ImportConfiguration): String = buildString {
  if (c.createSubfolders) append("${c.folderPattern} • ")
  if (c.preserveOriginalName) append("Original name") else append(c.fileNamePattern)
  append(" • ${c.conflictResolution.name.lowercase().replace("_", " ")}")
}

private fun pickFile(title: String): String? {
  val os = System.getProperty("os.name").lowercase()
  val isMac = os.contains("mac") || os.contains("darwin")

  if (isMac) {
    System.setProperty("apple.awt.fileDialogForDirectories", "false")
  }
  try {
    val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
    dialog.setFilenameFilter { _, name ->
      val lower = name.lowercase()
      lower.endsWith(".jpg") ||
          lower.endsWith(".jpeg") ||
          lower.endsWith(".png") ||
          lower.endsWith(".gif") ||
          lower.endsWith(".bmp") ||
          lower.endsWith(".webp")
    }
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
