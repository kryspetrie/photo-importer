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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File
import org.koin.compose.koinInject
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.PhotoScanImportSessionPreferences
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.domain.port.PathsPort
import org.kryspetrie.fileimport.domain.port.SettingsPort
import org.kryspetrie.fileimport.ui.components.AutoOrientIndicator
import org.kryspetrie.fileimport.ui.components.CenteredContentPane
import org.kryspetrie.fileimport.ui.components.ChunkyScrollbar
import org.kryspetrie.fileimport.ui.components.ConfigWorkLayout
import org.kryspetrie.fileimport.ui.components.SessionPreferencesEffect
import org.kryspetrie.fileimport.ui.components.WorkPanelHeading
import org.kryspetrie.fileimport.ui.components.isImageFile
import org.kryspetrie.fileimport.ui.i18n.strings
import org.kryspetrie.fileimport.ui.screens.wizard.photoscan.ScanModeCard
import org.kryspetrie.fileimport.ui.screens.wizard.photoscan.SourceDestRow
import org.kryspetrie.fileimport.ui.theme.LocalUiDensityScale
import org.kryspetrie.fileimport.ui.wizard.state.PhotoScanWizardState

/** Import screen for the wizard - source selection and configuration. */
@Composable
fun PhotoScanImportScreen(
    state: PhotoScanWizardState,
    settingsPort: SettingsPort,
    onSettingsChange: (AppSettings) -> Unit,
    onImageSelected: (java.io.File, List<java.io.File>?) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    val settings by settingsPort.observeSettings().collectAsState()
    val pathsPort: PathsPort = koinInject()
    val density = LocalUiDensityScale.current

    val cvAutoDetectEnabled by state.importSettings.cvAutoDetectEnabled.collectAsState()
    val singlePhotoMode by state.importSettings.singlePhotoMode.collectAsState()

    var settingsExpanded by remember { mutableStateOf(false) }

    var customConfig by remember {
        mutableStateOf(settings.photoScanImportTabSettings.configuration)
    }

    var sourcePath by remember {
        mutableStateOf(settings.photoScanImportTabSettings.lastSourcePath)
    }
    var destinationPath by remember {
        mutableStateOf(
            settings.photoScanImportTabSettings.lastDestinationPath.ifBlank {
                pathsPort.defaultDestination
            }
        )
    }

    val currentTabSettings =
        settings.photoScanImportTabSettings
            .withRecentSourcePath(sourcePath)
            .withRecentDestinationPath(destinationPath)
            .withConfiguration(customConfig)
    SessionPreferencesEffect(
        stored = settings.photoScanImportTabSettings,
        current = currentTabSettings,
        onRestore = { tab ->
            sourcePath = tab.lastSourcePath
            destinationPath = tab.lastDestinationPath.ifBlank { pathsPort.defaultDestination }
            customConfig = tab.configuration
            state.importSettings.setConfiguration(tab.configuration)
        },
        onPersist = { tab -> onSettingsChange(settings.withPhotoScanImportTabSettings(tab)) },
    )

    val sessionPrefs =
        PhotoScanImportSessionPreferences(
            cvAutoDetectEnabled = cvAutoDetectEnabled,
            singlePhotoMode = singlePhotoMode,
            settingsExpanded = settingsExpanded,
            perspectiveCorrectionEnabled = state.exportSettings.perspectiveCorrectionEnabled.value,
            exportMarginPercent = state.exportSettings.exportMarginPercent.value,
            defaultCorrectionStrategy = state.exportSettings.defaultCorrectionStrategy.value,
            skipCropAndRotate = settings.skipCropAndRotate,
            autoSkipBackFiles = settings.autoSkipBackFiles,
        )
    SessionPreferencesEffect(
        stored = settings.photoScanImportSessionPreferences,
        current = sessionPrefs,
        onRestore = { prefs ->
            state.importSettings.setCvAutoDetectEnabled(prefs.cvAutoDetectEnabled)
            state.importSettings.setSinglePhotoMode(prefs.singlePhotoMode)
            settingsExpanded = prefs.settingsExpanded
            state.exportSettings.setPerspectiveCorrectionEnabled(prefs.perspectiveCorrectionEnabled)
            state.exportSettings.setExportMarginPercent(prefs.normalizedExportMarginPercent())
            state.exportSettings.setDefaultCorrectionStrategy(prefs.defaultCorrectionStrategy)
        },
        onPersist = { prefs ->
            onSettingsChange(
                settings
                    .withPhotoScanImportSessionPreferences(prefs)
                    .copy(
                        skipCropAndRotate = prefs.skipCropAndRotate,
                        autoSkipBackFiles = prefs.autoSkipBackFiles,
                    )
            )
        },
    )

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

    fun startImport() {
        firstImageFile?.let { file ->
            // Skip Photo is multi-folder only — file-by-filename never becomes a batch session.
            onImageSelected(file, resolveImportBatchFiles(sourceFile))
        }
    }

    Column(
        modifier =
            modifier.fillMaxSize().onPreviewKeyEvent { keyEvent ->
                if (
                    keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Enter && canStart
                ) {
                    startImport()
                    true
                } else {
                    false
                }
            },
    ) {
        // Single header: title + subtitle (merged top bar + hero)
        Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            CenteredContentPane(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    s.t(StringKey.NAV_PHOTO_SCAN),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    s.t(StringKey.WIZARD_LANDING_SUBTITLE),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    s.t(StringKey.WIZARD_LANDING_STEPS),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        ChunkyScrollbar(modifier = Modifier.weight(1f)) {
            ConfigWorkLayout(
                modifier = Modifier.padding(density.spacingMd),
                contentSpacing = density.spacingMd,
                configuration = {
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

                    ScanModeCard(
                        cvAutoDetectEnabled = cvAutoDetectEnabled,
                        onCvAutoDetectChange = { state.importSettings.setCvAutoDetectEnabled(it) },
                        singlePhotoMode = singlePhotoMode,
                        onSinglePhotoModeChange = { state.importSettings.setSinglePhotoMode(it) },
                    )

                    ExportSettingsCard(
                        state = state,
                        skipCropAndRotate = settings.skipCropAndRotate,
                        onSkipCropAndRotateChange = {
                            onSettingsChange(settings.copy(skipCropAndRotate = it))
                        },
                        // Auto-skip backs only applies to multi-file folder batches.
                        autoSkipBackFiles = settings.autoSkipBackFiles,
                        onAutoSkipBackFilesChange =
                            if (sourceFile?.isDirectory == true) {
                                { onSettingsChange(settings.copy(autoSkipBackFiles = it)) }
                            } else {
                                null
                            },
                    )

                    PhotoScanSettingsSection(
                        config = customConfig,
                        onConfigChange = {
                            customConfig = it
                            state.importSettings.setConfiguration(it)
                        },
                        settingsExpanded = settingsExpanded,
                        onSettingsExpandedChange = { settingsExpanded = it },
                    )
                    if (customConfig.autoOrientEnabled) {
                        AutoOrientIndicator()
                    }
                },
                work = {
                    WorkPanelHeading(s.t(StringKey.TAB_ACTIONS))
                    Button(
                        onClick = { startImport() },
                        modifier =
                            Modifier.fillMaxWidth()
                                .height(density.controlMinHeight + density.spacingSm),
                        enabled = canStart,
                    ) {
                        Icon(Icons.Default.Scanner, null, Modifier.size(density.iconSize))
                        Spacer(Modifier.width(density.spacingSm))
                        Text(
                            if (singlePhotoMode) s.t(StringKey.WIZARD_IMPORT_SINGLE)
                            else s.t(StringKey.WIZARD_IMPORT_SCANS),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                },
            )
        }
    }
}
