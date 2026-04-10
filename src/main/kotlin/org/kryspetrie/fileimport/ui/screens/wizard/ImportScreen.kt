package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.infrastructure.wizard.*

/** Import screen for the wizard - mode selection and CV toggle configuration. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    state: PhotoScanWizardState,
    onImageSelected: (java.io.File) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
  val importMode by state.importMode.collectAsState()
  val cvAutoDetectEnabled by state.cvAutoDetectEnabled.collectAsState()

  // File picker state
  var showFilePicker by remember { mutableStateOf(false) }

  Scaffold(
      topBar = {
        TopAppBar(
            title = { Text("Import Photos") },
            navigationIcon = {
              IconButton(onClick = onCancel) { Icon(Icons.Default.Close, "Cancel") }
            })
      },
      content = { paddingValues ->
        Column(
            modifier = modifier.fillMaxSize().padding(paddingValues).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
              Spacer(Modifier.height(32.dp))

              // Mode selection header
              Text(
                  "Import Mode",
                  style = MaterialTheme.typography.headlineMedium,
                  textAlign = TextAlign.Center)

              Text(
                  "Choose how you want to import photos",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  textAlign = TextAlign.Center)

              Spacer(Modifier.height(16.dp))

              // Mode selection cards
              Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement =
                      Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
                    // Photo Scan Mode Card
                    ModeSelectionCard(
                        title = "Photo Scan",
                        description =
                            "Import multiple photos from a scanned image or flatbed capture",
                        icon = Icons.Default.Scanner,
                        isSelected = importMode == ImportMode.PHOTO_SCAN,
                        onClick = { state.setImportMode(ImportMode.PHOTO_SCAN) },
                        modifier = Modifier.weight(1f))

                    // Single Photo Mode Card
                    ModeSelectionCard(
                        title = "Single Photo",
                        description =
                            "Import a single photo with manual or auto-detected bounding box",
                        icon = Icons.Default.Image,
                        isSelected = importMode == ImportMode.SINGLE_PHOTO,
                        onClick = { state.setImportMode(ImportMode.SINGLE_PHOTO) },
                        modifier = Modifier.weight(1f))
                  }

              Spacer(Modifier.height(24.dp))

              // Photo Scan options (shown when Photo Scan mode selected)
              if (importMode == ImportMode.PHOTO_SCAN) {
                Card(
                    modifier = Modifier.fillMaxWidth(0.7f),
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                      Column(
                          modifier = Modifier.padding(16.dp),
                          verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Photo Scan Options", style = MaterialTheme.typography.titleSmall)

                            HorizontalDivider()

                            // CV Auto-Detection toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                  Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Auto-detect bounding boxes",
                                        style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "Uses computer vision to find photo boundaries automatically",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                  }
                                  Switch(
                                      checked = cvAutoDetectEnabled,
                                      onCheckedChange = { state.setCvAutoDetectEnabled(it) })
                                }
                          }
                    }
              }

              Spacer(Modifier.weight(1f))

              // Import button
              Button(
                  onClick = { showFilePicker = true },
                  modifier = Modifier.fillMaxWidth(0.5f).height(56.dp)) {
                    Icon(Icons.Default.FolderOpen, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Select Image", style = MaterialTheme.typography.titleMedium)
                  }

              Spacer(Modifier.height(32.dp))
            }
      })

  // File picker dialog
  if (showFilePicker) {
    FilePickerDialog(
        onFileSelected = { file ->
          showFilePicker = false
          onImageSelected(file)
        },
        onDismiss = { showFilePicker = false })
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSelectionCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
  val borderColor =
      if (isSelected) {
        MaterialTheme.colorScheme.primary
      } else {
        MaterialTheme.colorScheme.outlineVariant
      }

  Card(
      modifier = modifier.height(200.dp),
      border =
          androidx.compose.foundation.BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
      colors =
          CardDefaults.cardColors(
              containerColor =
                  if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                  } else {
                    MaterialTheme.colorScheme.surface
                  }),
      onClick = onClick) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
              Icon(
                  icon,
                  contentDescription = null,
                  modifier = Modifier.size(48.dp),
                  tint =
                      if (isSelected) {
                        MaterialTheme.colorScheme.primary
                      } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                      })

              Spacer(Modifier.height(12.dp))

              Text(
                  title,
                  style = MaterialTheme.typography.titleMedium,
                  color =
                      if (isSelected) {
                        MaterialTheme.colorScheme.primary
                      } else {
                        MaterialTheme.colorScheme.onSurface
                      })

              Spacer(Modifier.height(4.dp))

              Text(
                  description,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  textAlign = TextAlign.Center)

              if (isSelected) {
                Spacer(Modifier.height(8.dp))
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp))
              }
            }
      }
}

@Composable
private fun FilePickerDialog(onFileSelected: (java.io.File) -> Unit, onDismiss: () -> Unit) {
  // Use AWT FileDialog for native file picking
  val dialog = remember {
    java.awt
        .FileDialog(null as java.awt.Frame?, "Select Image File", java.awt.FileDialog.LOAD)
        .apply {
          // Set to accept image files
          setFilenameFilter { _, name ->
            val lower = name.lowercase()
            lower.endsWith(".jpg") ||
                lower.endsWith(".jpeg") ||
                lower.endsWith(".png") ||
                lower.endsWith(".gif") ||
                lower.endsWith(".bmp") ||
                lower.endsWith(".webp")
          }
        }
  }

  // Show the file dialog and wait for selection
  LaunchedEffect(Unit) {
    dialog.isVisible = true
    val selectedFile = dialog.file
    val directory = dialog.directory
    if (selectedFile != null && directory != null) {
      onFileSelected(java.io.File(directory, selectedFile))
    } else {
      onDismiss()
    }
  }
}
