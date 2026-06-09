package org.kryspetrie.fileimport.ui.screens.mediaimport

import androidx.compose.runtime.Composable
import kotlinx.coroutines.Job
import org.kryspetrie.fileimport.domain.model.ImportProgress
import org.kryspetrie.fileimport.domain.model.ImportResult
import org.kryspetrie.fileimport.domain.model.IndexProgress
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
    when (flowStep) {
        MediaImportFlowStep.SCANNING ->
            ProgressCard("Scanning source folder...", scanCurrent, scanTotal, scanProgress)
        MediaImportFlowStep.INDEXING ->
            ProgressCard(
                "Indexing destination...",
                indexProgress.indexed,
                indexProgress.total,
                indexProgress.currentFile,
            )
        MediaImportFlowStep.CHECKING_DUPES -> ProgressCard("Checking for duplicates...", 0, 0, "")
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
