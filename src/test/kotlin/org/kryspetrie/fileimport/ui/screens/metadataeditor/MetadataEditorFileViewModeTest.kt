package org.kryspetrie.fileimport.ui.screens.metadataeditor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.model.MetadataEditorFileViewMode

@DisplayName("MetadataEditorFileViewMode")
class MetadataEditorFileViewModeTest {

    @Test
    fun allViewModesAreDefined() {
        // GIVEN / WHEN / THEN
        assertThat(MetadataEditorFileViewMode.entries)
            .containsExactly(
                MetadataEditorFileViewMode.COLUMN,
                MetadataEditorFileViewMode.LIST,
                MetadataEditorFileViewMode.HIERARCHY,
                MetadataEditorFileViewMode.ICONS,
            )
    }
}
