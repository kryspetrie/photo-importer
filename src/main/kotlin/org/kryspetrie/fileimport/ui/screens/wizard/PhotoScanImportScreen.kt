package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Scanner
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.port.SettingsPort
import org.kryspetrie.fileimport.infrastructure.adapter.AppPaths
import org.kryspetrie.fileimport.ui.wizard.state.PhotoScanWizardState

import org.kryspetrie.fileimport.ui.components.ChunkyScrollbar

import org.kryspetrie.fileimport.ui.components.isImageFile
import org.kryspetrie.fileimport.ui.screens.wizard.photoscan.ScanModeCard
import org.kryspetrie.fileimport.ui.screens.wizard.photoscan.SourceDestRow

/** Import screen for the wizard - source selection and configuration. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoScanImportScreen(
    state: PhotoScanWizardState,
    settingsPort: SettingsPort,
    onSettingsChange: (AppSettings) -> Unit,
    onImageSelected: (java.io.File, List<java.io.File>?) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by settingsPort.observeSettings().collectAsState()
    val scope = rememberCoroutineScope()

    val cvAutoDetectEnabled by state.importSettings.cvAutoDetectEnabled.collectAsState()
    val singlePhotoMode by state.importSettings.singlePhotoMode.collectAsState()

    // Settings expanded state
    var settingsExpanded by remember { mutableStateOf(false) }

    // Configuration state (initialized from tab settings, mutable copy for editing)
    var customConfig by remember {
        mutableStateOf(settings.photoScanImportTabSettings.configuration)
    }

    // Source path - can be a file or folder
    var sourcePath by remember {
        mutableStateOf(settings.photoScanImportTabSettings.lastSourcePath)
    }
    var destinationPath by remember {
        mutableStateOf(
            settings.photoScanImportTabSettings.lastDestinationPath.ifBlank {
                AppPaths.defaultDestination.absolutePath
            }
        )
    }

    // Sync local state from settings (persists across tabs)
    LaunchedEffect(
        settings.photoScanImportTabSettings.lastSourcePath,
        settings.photoScanImportTabSettings.lastDestinationPath,
    ) {
        sourcePath = settings.photoScanImportTabSettings.lastSourcePath
        if (settings.photoScanImportTabSettings.lastDestinationPath.isNotBlank()) {
            destinationPath = settings.photoScanImportTabSettings.lastDestinationPath
        }
    }

    // Persist paths immediately when changed
    LaunchedEffect(sourcePath) {
        if (sourcePath.isNotBlank()) {
            val currentSettings = settingsPort.observeSettings().first()
            val newSettings =
                currentSettings.withPhotoScanImportTabSettings(
                    currentSettings.photoScanImportTabSettings.withRecentSourcePath(sourcePath)
                )
            scope.launch { settingsPort.saveSettings(newSettings) }
        }
    }

    LaunchedEffect(destinationPath) {
        if (destinationPath.isNotBlank()) {
            val currentSettings = settingsPort.observeSettings().first()
            val newSettings =
                currentSettings.withPhotoScanImportTabSettings(
                    currentSettings.photoScanImportTabSettings.withRecentDestinationPath(
                        destinationPath
                    )
                )
            scope.launch { settingsPort.saveSettings(newSettings) }
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

    val destValid = destDir != null
    val destCanCreate =
        remember(destinationPath) {
            if (destinationPath.isBlank()) false
            else {
                val dir = File(destinationPath)
                if (dir.isDirectory) false
                else {
                    var parent = dir.parentFile
                    while (parent != null) {
                        if (parent.isDirectory) break
                        parent = parent.parentFile
                    }
                    parent != null
                }
            }
        }

    // Get the first image file - either the selected file or first file from selected folder
    val firstImageFile: File? =
        remember(sourceFile) {
            when {
                sourceFile == null -> null
                sourceFile.isFile -> sourceFile
                sourceFile.isDirectory ->
                    sourceFile.listFiles { f -> f.isFile && isImageFile(f) }?.firstOrNull()
                else -> null
            }
        }

    val canStart = firstImageFile != null && (destValid || destCanCreate)

    Scaffold(
        modifier = Modifier.onPreviewKeyEvent { keyEvent ->
            if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Enter && canStart) {
                firstImageFile?.let { file ->
                    val batchFiles =
                        if (sourceFile?.isDirectory == true) {
                            sourceFile
                                .listFiles { f -> f.isFile && isImageFile(f) }
                                ?.sortedBy { it.name }
                                ?.toList()
                        } else null
                    onImageSelected(file, batchFiles)
                }
                true
            } else false
        },
        topBar = {
            TopAppBar(
                title = { Text("Import Photos") },
                navigationIcon = {},
            )
        },
        content = { paddingValues ->
            ChunkyScrollbar(modifier = modifier.fillMaxSize().padding(paddingValues)) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // ── Source & Destination ──
                    SourceDestRow(
                        sourcePath = sourcePath,
                        onSourcePathChange = { sourcePath = it },
                        sourceFile = sourceFile,
                        destinationPath = destinationPath,
                        onDestinationPathChange = { destinationPath = it },
                        destValid = destValid,
                        destCanCreate = destCanCreate,
                        destDirName = destDir?.name,
                    )

                    // ── Scan Mode ──
                    ScanModeCard(
                        cvAutoDetectEnabled = cvAutoDetectEnabled,
                        onCvAutoDetectChange = { state.importSettings.setCvAutoDetectEnabled(it) },
                        singlePhotoMode = singlePhotoMode,
                        onSinglePhotoModeChange = { state.importSettings.setSinglePhotoMode(it) },
                    )

                    // ── Export Settings ──
                    ExportSettingsCard(
                        state = state,
                        skipCropAndRotate = settings.skipCropAndRotate,
                        onSkipCropAndRotateChange = { newValue ->
                            scope.launch {
                                val currentSettings = settingsPort.observeSettings().first()
                                settingsPort.saveSettings(
                                    currentSettings.copy(skipCropAndRotate = newValue)
                                )
                            }
                        },
                    )

                    // ── Import Photo Scans Button ──
                    Button(
                        onClick = {
                            firstImageFile?.let { file ->
                                val batchFiles =
                                    if (sourceFile?.isDirectory == true) {
                                        sourceFile
                                            .listFiles { f -> f.isFile && isImageFile(f) }
                                            ?.sortedBy { it.name }
                                            ?.toList()
                                    } else null
                                onImageSelected(file, batchFiles)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        enabled = canStart,
                    ) {
                        Icon(Icons.Default.Scanner, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (singlePhotoMode) "Import Single Photo" else "Import Photo Scan(s)",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }

                    // ── Custom Settings ──
                    PhotoScanSettingsSection(
                        config = customConfig,
                        onConfigChange = { customConfig = it },
                        settingsExpanded = settingsExpanded,
                        onSettingsExpandedChange = { settingsExpanded = it },
                    )
                }
            }
        }
    )

    // Save config to wizard state when it changes
    LaunchedEffect(customConfig) { state.importSettings.setConfiguration(customConfig) }

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
