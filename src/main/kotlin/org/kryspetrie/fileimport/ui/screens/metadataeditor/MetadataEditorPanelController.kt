package org.kryspetrie.fileimport.ui.screens.metadataeditor

import org.kryspetrie.fileimport.domain.model.FaceRegion
import org.kryspetrie.fileimport.domain.model.MetadataHistory
import org.kryspetrie.fileimport.domain.model.RecentMetadataSet

/**
 * Shared wiring helpers for bulk and wizard metadata panels — section visibility, face/subject
 * merge rules, and batch-edit semantics.
 */
object MetadataEditorPanelController {
    fun isBatchEditMode(isMultiEditMode: Boolean): Boolean = isMultiEditMode

    fun isMultiPhotoSelection(isMultiEditMode: Boolean, selectedCount: Int): Boolean =
        isMultiEditMode && selectedCount > 1

    fun shouldShowPreview(isMultiEditMode: Boolean, selectedCount: Int): Boolean =
        when {
            !isMultiEditMode -> true
            selectedCount == 1 -> true
            else -> false
        }

    fun mergeFaceNamesIntoSubjects(subjects: String, faceRegions: List<FaceRegion>): String {
        val names = buildSet {
            subjects.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach { add(it) }
            faceRegions.map { it.name.trim() }.filter { it.isNotBlank() }.forEach { add(it) }
        }
        return names.joinToString(", ")
    }

    fun subjectsSectionExpanded(subjects: String, faceRegions: List<FaceRegion>): Boolean =
        faceRegions.isNotEmpty() || subjects.isNotBlank()

    fun faceCountBadge(faceRegions: List<FaceRegion>): Int = faceRegions.size

    fun recentSetsForBatch(
        metadataHistory: MetadataHistory,
        isMultiPhotoSelection: Boolean,
    ): List<RecentMetadataSet> =
        if (isMultiPhotoSelection) metadataHistory.recentSets else emptyList()
}
