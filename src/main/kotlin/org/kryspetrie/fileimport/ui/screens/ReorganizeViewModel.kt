package org.kryspetrie.fileimport.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.kryspetrie.fileimport.domain.model.ImportConfiguration
import org.kryspetrie.fileimport.domain.model.ReorganizeJournalSummary
import org.kryspetrie.fileimport.domain.model.ReorganizeMode
import org.kryspetrie.fileimport.domain.model.ReorganizePreview
import org.kryspetrie.fileimport.domain.model.ReorganizeProgress
import org.kryspetrie.fileimport.domain.model.ReorganizeResult

class ReorganizeViewModel {
    enum class ReorgStep {
        SETUP,
        SCANNING,
        PREVIEW,
        EXECUTING,
        COMPLETE,
    }

    var folderPath by mutableStateOf("")
    var config by mutableStateOf(ImportConfiguration())
    var renameOnly by mutableStateOf(false)
    var reorgMode by mutableStateOf(ReorganizeMode.MOVE)
    var settingsExpanded by mutableStateOf(false)
    var step by mutableStateOf(ReorgStep.SETUP)
    var preview by mutableStateOf<ReorganizePreview?>(null)
    var progress by mutableStateOf(ReorganizeProgress())
    var result by mutableStateOf<ReorganizeResult?>(null)
    var errorMessage by mutableStateOf<String?>(null)
    var journals by mutableStateOf<List<ReorganizeJournalSummary>>(emptyList())
    var showUndoConfirm by mutableStateOf<ReorganizeJournalSummary?>(null)
    var selectedJournal by mutableStateOf<ReorganizeJournalSummary?>(null)

    fun reset() {
        step = ReorgStep.SETUP
        preview = null
        result = null
        errorMessage = null
        progress = ReorganizeProgress()
    }

    fun updateJournals(journals: List<ReorganizeJournalSummary>) {
        this.journals = journals
    }
}
