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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.kryspetrie.fileimport.application.ReorganizeService
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.ReorganizeJournalSummary
import org.kryspetrie.fileimport.domain.model.ReorganizeMode
import org.kryspetrie.fileimport.domain.model.ReorganizeSessionPreferences
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.domain.port.SettingsPort
import org.kryspetrie.fileimport.ui.components.CenteredContentPane
import org.kryspetrie.fileimport.ui.components.ChunkyScrollbar
import org.kryspetrie.fileimport.ui.components.ConfigWorkLayout
import org.kryspetrie.fileimport.ui.components.FolderSelectionField
import org.kryspetrie.fileimport.ui.components.SessionPreferencesEffect
import org.kryspetrie.fileimport.ui.components.SettingsToggle
import org.kryspetrie.fileimport.ui.components.WorkPanelHeading
import org.kryspetrie.fileimport.ui.components.WorkPanelSectionSpacer
import org.kryspetrie.fileimport.ui.components.shouldApplyReorganizeOnEnter
import org.kryspetrie.fileimport.ui.components.shouldConfirmUndoDialogOnEnter
import org.kryspetrie.fileimport.ui.components.shouldLeaveReorganizePreviewOnEscape
import org.kryspetrie.fileimport.ui.components.shouldSubmitSetupOnEnter
import org.kryspetrie.fileimport.ui.i18n.strings
import org.kryspetrie.fileimport.ui.screens.reorganize.ReorganizeActionBar
import org.kryspetrie.fileimport.ui.screens.reorganize.ReorganizePreviewSection
import org.kryspetrie.fileimport.ui.screens.reorganize.ReorganizeProgressSection
import org.kryspetrie.fileimport.ui.screens.reorganize.ReorganizeSettingsSection
import org.kryspetrie.fileimport.ui.screens.reorganize.ReorganizeUndoSection
import org.kryspetrie.fileimport.ui.theme.DefaultColors
import org.kryspetrie.fileimport.ui.theme.DefaultSpacing

@Composable
fun ReorganizeScreen(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    viewModel: ReorganizeViewModel = remember { ReorganizeViewModel() },
) {
    val s = strings()
    val reorgService = koinInject<ReorganizeService>()
    val settingsPort = koinInject<SettingsPort>()
    val currentSettings by settingsPort.observeSettings().collectAsState(initial = settings)
    val scope = rememberCoroutineScope()

    val sessionPrefs =
        ReorganizeSessionPreferences(
            folderPath = viewModel.folderPath,
            configuration = viewModel.config,
            renameOnly = viewModel.renameOnly,
            reorgMode = viewModel.reorgMode.name,
            settingsExpanded = viewModel.settingsExpanded,
        )
    SessionPreferencesEffect(
        stored = currentSettings.reorganizeSessionPreferences,
        current = sessionPrefs,
        onRestore = { prefs ->
            viewModel.folderPath = prefs.folderPath
            viewModel.config = prefs.configuration
            viewModel.renameOnly = prefs.renameOnly
            viewModel.reorgMode = prefs.resolvedReorgMode()
            viewModel.settingsExpanded = prefs.settingsExpanded
        },
        onPersist = { prefs ->
            onSettingsChange(currentSettings.withReorganizeSessionPreferences(prefs))
        },
    )

    LaunchedEffect(Unit) {
        if (viewModel.journals.isEmpty()) {
            viewModel.journals = reorgService.listJournals()
        }
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
                Column(verticalArrangement = Arrangement.spacedBy(DefaultSpacing.md)) {
                    Text(s.t(StringKey.REORG_UNDO_MESSAGE))
                    Spacer(Modifier.height(DefaultSpacing.md))
                    Text(
                        s.t(StringKey.REORG_UNDO_JOURNAL),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        s.t(StringKey.REORG_UNDO_FOLDER, "path" to journal.rootFolder),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        s.t(StringKey.REORG_UNDO_MODE, "mode" to journal.operationMode.name),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        s.t(
                            StringKey.REORG_UNDO_FILES_CHANGED,
                            "count" to "${journal.changedFiles}",
                        ),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        s.t(StringKey.REORG_UNDO_DATE, "date" to journal.timestampString),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Spacer(Modifier.height(DefaultSpacing.md))
                    when (journal.operationMode) {
                        ReorganizeMode.MOVE -> Text(s.t(StringKey.REORG_UNDO_MOVE_BACK))
                        ReorganizeMode.COPY -> Text(s.t(StringKey.REORG_UNDO_DELETE_COPIES))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { undoJournal(journal) }) {
                    Text(s.t(StringKey.META_UNDO), color = DefaultColors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showUndoConfirm = null }) {
                    Text(s.t(StringKey.ACTION_CANCEL))
                }
            },
        )
    }

    Column(
        modifier =
            Modifier.fillMaxSize().onPreviewKeyEvent { keyEvent ->
                val isKeyDown = keyEvent.type == KeyEventType.KeyDown
                val undoDialogOpen = viewModel.showUndoConfirm != null
                when {
                    shouldConfirmUndoDialogOnEnter(
                        isKeyDown = isKeyDown,
                        key = keyEvent.key,
                        dialogOpen = undoDialogOpen,
                    ) -> {
                        viewModel.showUndoConfirm?.let { undoJournal(it) }
                        true
                    }
                    shouldSubmitSetupOnEnter(
                        isKeyDown = isKeyDown,
                        key = keyEvent.key,
                        isSetupStep = viewModel.step == ReorganizeViewModel.ReorgStep.SETUP,
                        folderPath = viewModel.folderPath,
                    ) -> {
                        startPreview()
                        true
                    }
                    shouldApplyReorganizeOnEnter(
                        isKeyDown = isKeyDown,
                        key = keyEvent.key,
                        isPreviewStep = viewModel.step == ReorganizeViewModel.ReorgStep.PREVIEW,
                        changeCount = viewModel.preview?.changedFiles ?: 0,
                        undoDialogOpen = undoDialogOpen,
                    ) -> {
                        executeReorg()
                        true
                    }
                    shouldLeaveReorganizePreviewOnEscape(
                        isKeyDown = isKeyDown,
                        key = keyEvent.key,
                        isPreviewStep = viewModel.step == ReorganizeViewModel.ReorgStep.PREVIEW,
                        undoDialogOpen = undoDialogOpen,
                    ) -> {
                        viewModel.reset()
                        true
                    }
                    else -> false
                }
            }
    ) {
        // Single header
        Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            CenteredContentPane(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    s.t(StringKey.REORG_TITLE),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    s.t(StringKey.REORG_DESCRIPTION),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        ChunkyScrollbar(modifier = Modifier.weight(1f)) {
            ConfigWorkLayout(
                modifier = Modifier.padding(DefaultSpacing.lg),
                contentSpacing = DefaultSpacing.md,
                configuration = {
                    FolderSelectionField(
                        value = viewModel.folderPath,
                        onValueChange = { viewModel.folderPath = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = s.t(StringKey.REORG_LIBRARY_FOLDER),
                        placeholder = s.t(StringKey.REORG_LIBRARY_PLACEHOLDER),
                        title = s.t(StringKey.ACTION_SELECT_FOLDER),
                        supportingText = {
                            Text(
                                s.t(StringKey.IMPORT_PATH_HINT),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )

                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(DefaultSpacing.md + DefaultSpacing.xs),
                            verticalArrangement = Arrangement.spacedBy(DefaultSpacing.sm),
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
                                horizontalArrangement = Arrangement.spacedBy(DefaultSpacing.sm),
                            ) {
                                ReorganizeMode.entries.forEach { mode ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(
                                            viewModel.reorgMode == mode,
                                            { viewModel.reorgMode = mode },
                                        )
                                        Spacer(Modifier.width(DefaultSpacing.xs))
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
                },
                work = {
                    val showActions =
                        viewModel.step == ReorganizeViewModel.ReorgStep.SETUP ||
                            viewModel.step == ReorganizeViewModel.ReorgStep.PREVIEW
                    if (showActions) {
                        WorkPanelHeading(s.t(StringKey.TAB_ACTIONS))
                        ReorganizeActionBar(
                            step = viewModel.step,
                            canPreview = viewModel.folderPath.isNotBlank(),
                            changeCount = viewModel.preview?.changedFiles ?: 0,
                            onBack = { viewModel.reset() },
                            onPreview = { startPreview() },
                            onApply = { executeReorg() },
                        )
                    }
                    val showStatus =
                        viewModel.errorMessage != null ||
                            viewModel.step == ReorganizeViewModel.ReorgStep.SCANNING ||
                            viewModel.step == ReorganizeViewModel.ReorgStep.EXECUTING ||
                            viewModel.step == ReorganizeViewModel.ReorgStep.COMPLETE ||
                            (viewModel.step == ReorganizeViewModel.ReorgStep.PREVIEW &&
                                viewModel.preview != null)
                    if (showStatus) {
                        if (showActions) {
                            WorkPanelSectionSpacer()
                        }
                        WorkPanelHeading(s.t(StringKey.TAB_RUN_STATUS))
                    }
                    viewModel.errorMessage?.let {
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.padding(DefaultSpacing.md + DefaultSpacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(DefaultSpacing.md),
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = s.t(StringKey.REORG_FAILED),
                                    Modifier.size(DefaultSpacing.iconMedium),
                                    tint = DefaultColors.error,
                                )
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DefaultColors.error,
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
                },
            )
        }
    }
}
