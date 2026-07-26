package org.kryspetrie.fileimport.ui.screens.metadataeditor

import androidx.compose.ui.unit.dp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.model.MetadataEditorFileViewMode

@DisplayName("MetadataEditorFileViewMode")
class MetadataEditorFileViewModeTest {

    @Test
    fun panelWidthMatchesViewMode() {
        // GIVEN
        val modes =
            mapOf(
                MetadataEditorFileViewMode.ICONS to 120.dp,
                MetadataEditorFileViewMode.LIST to 260.dp,
                MetadataEditorFileViewMode.HIERARCHY to 300.dp,
                MetadataEditorFileViewMode.COLUMN to 440.dp,
            )

        // WHEN / THEN
        modes.forEach { (mode, expectedWidth) ->
            assertThat(mode.panelWidth()).isEqualTo(expectedWidth)
        }
    }

    @Test
    fun onlyIconsViewUsesFullHeightPreview() {
        // GIVEN / WHEN / THEN
        assertThat(MetadataEditorFileViewMode.ICONS.usesCompactPreview()).isFalse()
        assertThat(MetadataEditorFileViewMode.LIST.usesCompactPreview()).isTrue()
        assertThat(MetadataEditorFileViewMode.HIERARCHY.usesCompactPreview()).isTrue()
        assertThat(MetadataEditorFileViewMode.COLUMN.usesCompactPreview()).isTrue()
    }
}
