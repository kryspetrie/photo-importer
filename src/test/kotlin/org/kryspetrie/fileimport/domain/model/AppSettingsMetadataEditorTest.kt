package org.kryspetrie.fileimport.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("AppSettings metadata editor")
class AppSettingsMetadataEditorTest {

    @Test
    fun defaultsToSidebarLayoutMode() {
        assertThat(AppSettings().metadataEditorLayoutMode).isEqualTo(MetadataEditorLayoutMode.SIDEBAR)
    }

    @Test
    fun withMetadataEditorLayoutModeUpdatesMode() {
        val updated =
            AppSettings().withMetadataEditorLayoutMode(MetadataEditorLayoutMode.FILE_PICKER)
        assertThat(updated.metadataEditorLayoutMode).isEqualTo(MetadataEditorLayoutMode.FILE_PICKER)
    }

    @Test
    fun layoutModeSurvivesJsonRoundTrip() {
        val original =
            AppSettings(metadataEditorLayoutMode = MetadataEditorLayoutMode.FILE_PICKER)
        val json =
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .encodeToString(AppSettings.serializer(), original)
        val restored =
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .decodeFromString(AppSettings.serializer(), json)

        assertThat(restored.metadataEditorLayoutMode).isEqualTo(MetadataEditorLayoutMode.FILE_PICKER)
    }

    @Test
    fun defaultsToIconsFileViewMode() {
        // GIVEN / WHEN
        val settings = AppSettings()

        // THEN
        assertThat(settings.metadataEditorFileViewMode).isEqualTo(MetadataEditorFileViewMode.ICONS)
    }

    @Test
    fun withMetadataEditorFileViewModeUpdatesMode() {
        // GIVEN
        val settings = AppSettings()

        // WHEN
        val updated = settings.withMetadataEditorFileViewMode(MetadataEditorFileViewMode.COLUMN)

        // THEN
        assertThat(updated.metadataEditorFileViewMode).isEqualTo(MetadataEditorFileViewMode.COLUMN)
    }

    @Test
    fun fileViewModeSurvivesJsonRoundTrip() {
        // GIVEN
        val original = AppSettings(metadataEditorFileViewMode = MetadataEditorFileViewMode.HIERARCHY)
        val jsonCodec = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

        // WHEN
        val json = jsonCodec.encodeToString(AppSettings.serializer(), original)
        val restored = jsonCodec.decodeFromString(AppSettings.serializer(), json)

        // THEN
        assertThat(restored.metadataEditorFileViewMode).isEqualTo(MetadataEditorFileViewMode.HIERARCHY)
    }

    @Test
    fun withMetadataEditorRecentPathPromotesRecentAndLimitsToFive() {
        val settings =
            AppSettings()
                .withMetadataEditorRecentPath("/1")
                .withMetadataEditorRecentPath("/2")
                .withMetadataEditorRecentPath("/3")
                .withMetadataEditorRecentPath("/4")
                .withMetadataEditorRecentPath("/5")
                .withMetadataEditorRecentPath("/6")

        assertThat(settings.metadataEditorRecentPaths).hasSize(5)
        assertThat(settings.metadataEditorRecentPaths.first()).isEqualTo("/6")
        assertThat(settings.metadataEditorRecentPaths).doesNotContain("/1")
    }
}
