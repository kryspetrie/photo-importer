package org.kryspetrie.fileimport.ui.screens.metadataeditor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.ui.theme.UiDensityDefaults

@DisplayName("Metadata editor layout breakpoints")
class MetadataEditorLayoutBreakpointTest {

    @Test
    fun classifiesNarrowStandardAndUltraWide() {
        // GIVEN / WHEN / THEN
        assertThat(layoutBreakpointForWidth(800f)).isEqualTo(MetadataEditorLayoutBreakpoint.NARROW)
        assertThat(layoutBreakpointForWidth(1200f))
            .isEqualTo(MetadataEditorLayoutBreakpoint.STANDARD)
        assertThat(layoutBreakpointForWidth(1800f))
            .isEqualTo(MetadataEditorLayoutBreakpoint.ULTRA_WIDE)
    }

    @Test
    fun narrowThresholdMatchesUiDefaults() {
        // GIVEN / WHEN
        val breakpoint =
            layoutBreakpointForWidth(UiDensityDefaults.metadataEditorNarrowBreakpoint.value - 1)

        // THEN
        assertThat(breakpoint).isEqualTo(MetadataEditorLayoutBreakpoint.NARROW)
    }
}
