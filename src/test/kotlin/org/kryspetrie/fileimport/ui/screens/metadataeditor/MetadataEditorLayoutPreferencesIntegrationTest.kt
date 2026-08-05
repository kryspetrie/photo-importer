package org.kryspetrie.fileimport.ui.screens.metadataeditor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.model.MetadataEditorLayoutPreferences

@DisplayName("Metadata editor layout preferences integration")
class MetadataEditorLayoutPreferencesIntegrationTest {

    @Test
    fun browserPaneWidthCoercesToAllowedRange() {
        // GIVEN
        val prefs = MetadataEditorLayoutPreferences()

        // WHEN
        val updated = prefs.withBrowserPaneWidthDp(900)

        // THEN
        assertThat(updated.browserPaneWidthDp).isEqualTo(600)
    }

    @Test
    fun previewPaneWeightCoercesToAllowedRange() {
        // GIVEN
        val prefs = MetadataEditorLayoutPreferences()

        // WHEN
        val updated = prefs.withPreviewPaneWeight(0.9f)

        // THEN
        assertThat(updated.previewPaneWeight).isEqualTo(0.75f)
    }

    @Test
    fun drawerOpenPreferenceRoundTrips() {
        // GIVEN
        val prefs = MetadataEditorLayoutPreferences(browserDrawerOpen = false)

        // WHEN
        val updated = prefs.withBrowserDrawerOpen(true)

        // THEN
        assertThat(updated.browserDrawerOpen).isTrue()
    }
}
