package org.kryspetrie.fileimport.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import org.kryspetrie.fileimport.domain.model.DeduplicationSettings
import org.kryspetrie.fileimport.domain.model.DuplicateAction
import org.kryspetrie.fileimport.domain.model.DuplicateInfo
import org.kryspetrie.fileimport.domain.model.ScanProgress

class DuplicateScannerViewModel {
    enum class ScanStep {
        SETUP,
        SCANNING,
        RESULTS,
        RESOLVING,
    }

    var folderPath by mutableStateOf("")
    var step by mutableStateOf(ScanStep.SETUP)
    var scanProgress by mutableStateOf(ScanProgress())
    var duplicates by mutableStateOf<List<DuplicateInfo>>(emptyList())
    var errorMessage by mutableStateOf<String?>(null)
    var resolveAction by mutableStateOf(DuplicateAction.KEEP_HIGHEST_RES)
    var moveToTrash by mutableStateOf(true)
    var resolveProgress by mutableStateOf(0 to 0)
    var showResolveConfirm by mutableStateOf(false)

    /** Active coroutine job for scan/resolve — used for cooperative cancellation. */
    var activeJob: Job? = null

    // Dedup detection settings
    var enableHash by mutableStateOf(true)
    var enableExif by mutableStateOf(true)
    var enableSurf by mutableStateOf(false)

    fun cancelOperation() {
        activeJob?.cancel()
        activeJob = null
        step = ScanStep.SETUP
        errorMessage = null
    }

    fun reset() {
        step = ScanStep.SETUP
        duplicates = emptyList()
        errorMessage = null
        scanProgress = ScanProgress()
    }

    fun buildDedupSettings(): DeduplicationSettings =
        DeduplicationSettings(
            enableHashDeduplication = enableHash,
            enableExifDeduplication = enableExif,
            enablePerceptualHash = false,
            enableFilenameDeduplication = false,
            enableSurfMatching = enableSurf,
            ignoreDifferentFileTypes = true,
        )

    fun setPrimaryImage(groupId: String, selectedId: String) {
        duplicates =
            duplicates.map { group ->
                if (group.primaryImage.id != groupId) group
                else {
                    val all = listOf(group.primaryImage) + group.duplicateImages
                    val newPrimary = all.first { it.id == selectedId }
                    val newDuplicates = all.filter { it.id != selectedId }
                    group.copy(primaryImage = newPrimary, duplicateImages = newDuplicates)
                }
            }
    }

    val totalDupeFiles: Int
        get() = duplicates.sumOf { it.duplicateImages.size }

    val totalWastedBytes: Long
        get() = duplicates.sumOf { group -> group.duplicateImages.sumOf { it.fileSize } }
}
