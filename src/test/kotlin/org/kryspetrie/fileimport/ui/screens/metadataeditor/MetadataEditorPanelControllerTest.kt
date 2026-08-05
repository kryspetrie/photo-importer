package org.kryspetrie.fileimport.ui.screens.metadataeditor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.model.FaceRegion

@DisplayName("MetadataEditorPanelController")
class MetadataEditorPanelControllerTest {

    @Test
    fun shouldShowPreviewForSingleSelectionInMultiMode() {
        // GIVEN / WHEN
        val show =
            MetadataEditorPanelController.shouldShowPreview(
                isMultiEditMode = true,
                selectedCount = 1,
            )

        // THEN
        assertThat(show).isTrue()
    }

    @Test
    fun hidesPreviewWhenNoSelectionInMultiMode() {
        // GIVEN / WHEN
        val show =
            MetadataEditorPanelController.shouldShowPreview(
                isMultiEditMode = true,
                selectedCount = 0,
            )

        // THEN
        assertThat(show).isFalse()
    }

    @Test
    fun hidesPreviewForMultiPhotoSelection() {
        // GIVEN / WHEN
        val show =
            MetadataEditorPanelController.shouldShowPreview(
                isMultiEditMode = true,
                selectedCount = 3,
            )

        // THEN
        assertThat(show).isFalse()
    }

    @Test
    fun mergeFaceNamesIntoSubjectsPreservesManualEntries() {
        // GIVEN
        val regions =
            listOf(FaceRegion(name = "Alice", type = "Face", x = 0.5, y = 0.5, w = 0.1, h = 0.1))

        // WHEN
        val merged = MetadataEditorPanelController.mergeFaceNamesIntoSubjects("Bob, Carol", regions)

        // THEN
        assertThat(merged).contains("Bob", "Carol", "Alice")
    }

    @Test
    fun recentSetsForBatchOnlyWhenMultiPhotoSelection() {
        // GIVEN
        val history =
            org.kryspetrie.fileimport.domain.model.MetadataHistory(
                recentSets =
                    listOf(org.kryspetrie.fileimport.domain.model.RecentMetadataSet(keywords = "a"))
            )

        // WHEN / THEN
        assertThat(
                MetadataEditorPanelController.recentSetsForBatch(
                    history,
                    isMultiPhotoSelection = true,
                )
            )
            .hasSize(1)
        assertThat(
                MetadataEditorPanelController.recentSetsForBatch(
                    history,
                    isMultiPhotoSelection = false,
                )
            )
            .isEmpty()
    }
}
