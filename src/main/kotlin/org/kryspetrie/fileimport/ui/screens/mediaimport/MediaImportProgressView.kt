package org.kryspetrie.fileimport.ui.screens.mediaimport

import androidx.compose.runtime.Composable
import kotlinx.coroutines.Job
import org.kryspetrie.fileimport.domain.model.ImportProgress
import org.kryspetrie.fileimport.domain.model.ImportResult
import org.kryspetrie.fileimport.domain.model.IndexProgress
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.strings
import org.kryspetrie.fileimport.ui.screens.components.ImportProgressInline
import org.kryspetrie.fileimport.ui.screens.components.ImportResultInline
import org.kryspetrie.fileimport.ui.screens.components.ProgressCard

@Composable
fun MediaImportProgressView(
    flowStep: MediaImportFlowStep,
    scanCurrent: Int,
    scanTotal: Int,
    scanProgress: String,
    indexProgress: IndexProgress,
    importProgress: ImportProgress,
    importResult: ImportResult?,
    importJob: Job?,
    destinationPath: String,
    onReset: () -> Unit,
) {
    val s = strings()
    when (flowStep) {
        MediaImportFlowStep.SCANNING ->
            ProgressCard(
                s.t(StringKey.IMPORT_SCANNING_FOLDER),
                scanCurrent,
                scanTotal,
                scanProgress,
            )
        MediaImportFlowStep.INDEXING ->
            ProgressCard(
                s.t(StringKey.IMPORT_INDEXING_DEST),
                indexProgress.indexed,
                indexProgress.total,
                indexProgress.currentFile,
            )
        MediaImportFlowStep.CHECKING_DUPES ->
            ProgressCard(s.t(StringKey.IMPORT_CHECKING_DUPLICATES), 0, 0, "")
        MediaImportFlowStep.IMPORTING ->
            ImportProgressInline(importProgress) {
                importJob?.cancel()
                onReset()
            }
        MediaImportFlowStep.COMPLETE ->
            importResult?.let { ImportResultInline(it, destinationPath) { onReset() } }
        else -> {}
    }
}
