package org.kryspetrie.fileimport.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("AppSettings metadata editor")
class AppSettingsMetadataEditorTest {

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
        val original =
            AppSettings(metadataEditorFileViewMode = MetadataEditorFileViewMode.HIERARCHY)
        val jsonCodec = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

        // WHEN
        val json = jsonCodec.encodeToString(AppSettings.serializer(), original)
        val restored = jsonCodec.decodeFromString(AppSettings.serializer(), json)

        // THEN
        assertThat(restored.metadataEditorFileViewMode)
            .isEqualTo(MetadataEditorFileViewMode.HIERARCHY)
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

    @Test
    fun defaultsToComfortableDensity() {
        // GIVEN / WHEN
        val settings = AppSettings()

        // THEN
        assertThat(settings.uiDensity).isEqualTo(UiDensity.COMFORTABLE)
    }

    @Test
    fun uiPreferencesSurviveJsonRoundTrip() {
        // GIVEN
        val original =
            AppSettings(
                uiDensity = UiDensity.SPACIOUS,
                metadataEditorLayoutPreferences =
                    MetadataEditorLayoutPreferences(
                        browserPaneWidthDp = 320,
                        previewPaneWeight = 0.6f,
                        browserDrawerOpen = false,
                    ),
            )
        val jsonCodec = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

        // WHEN
        val json = jsonCodec.encodeToString(AppSettings.serializer(), original)
        val restored = jsonCodec.decodeFromString(AppSettings.serializer(), json)

        // THEN
        assertThat(restored.uiDensity).isEqualTo(UiDensity.SPACIOUS)
        assertThat(restored.metadataEditorLayoutPreferences.browserPaneWidthDp).isEqualTo(320)
        assertThat(restored.metadataEditorLayoutPreferences.previewPaneWeight).isEqualTo(0.6f)
        assertThat(restored.metadataEditorLayoutPreferences.browserDrawerOpen).isFalse()
    }

    @Test
    fun withUiDensityAndLayoutPreferencesUpdateSettings() {
        // GIVEN
        val settings = AppSettings()

        // WHEN
        val updated =
            settings
                .withUiDensity(UiDensity.COMPACT)
                .withMetadataEditorLayoutPreferences(
                    MetadataEditorLayoutPreferences(browserPaneWidthDp = 400)
                )

        // THEN
        assertThat(updated.uiDensity).isEqualTo(UiDensity.COMPACT)
        assertThat(updated.metadataEditorLayoutPreferences.browserPaneWidthDp).isEqualTo(400)
    }

    @Test
    fun defaultsSessionPreferences() {
        val settings = AppSettings()
        assertThat(settings.metadataEditorSessionPreferences.outputMode).isEqualTo("OVERWRITE")
        assertThat(settings.metadataEditorSessionPreferences.includeSubfolders).isFalse()
    }

    @Test
    fun sessionPreferencesSurviveJsonRoundTrip() {
        val original =
            AppSettings(
                metadataEditorSessionPreferences =
                    MetadataEditorSessionPreferences(
                        outputMode = "SAVE_NEW",
                        outputDirectory = "/out",
                        includeSubfolders = true,
                    )
            )
        val jsonCodec = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val restored =
            jsonCodec.decodeFromString(
                AppSettings.serializer(),
                jsonCodec.encodeToString(AppSettings.serializer(), original),
            )

        assertThat(restored.metadataEditorSessionPreferences.outputMode).isEqualTo("SAVE_NEW")
        assertThat(restored.metadataEditorSessionPreferences.outputDirectory).isEqualTo("/out")
        assertThat(restored.metadataEditorSessionPreferences.includeSubfolders).isTrue()
    }

    @Test
    fun reorganizeSessionPreferencesSurviveJsonRoundTrip() {
        val original =
            AppSettings(
                reorganizeSessionPreferences =
                    ReorganizeSessionPreferences(
                        folderPath = "/lib",
                        configuration = ImportConfiguration(deleteAfterImport = true),
                        renameOnly = true,
                        reorgMode = ReorganizeMode.COPY.name,
                    )
            )
        val jsonCodec = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val restored =
            jsonCodec.decodeFromString(
                AppSettings.serializer(),
                jsonCodec.encodeToString(AppSettings.serializer(), original),
            )

        assertThat(restored.reorganizeSessionPreferences.folderPath).isEqualTo("/lib")
        assertThat(restored.reorganizeSessionPreferences.renameOnly).isTrue()
        assertThat(restored.reorganizeSessionPreferences.resolvedReorgMode())
            .isEqualTo(ReorganizeMode.COPY)
    }

    @Test
    fun duplicateScannerSessionPreferencesSurviveJsonRoundTrip() {
        val original =
            AppSettings(
                duplicateScannerSessionPreferences =
                    DuplicateScannerSessionPreferences(
                        folderPath = "/dupes",
                        enableSurf = true,
                        resolveAction = DuplicateAction.KEEP_LARGEST.name,
                        moveToTrash = false,
                    )
            )
        val jsonCodec = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val restored =
            jsonCodec.decodeFromString(
                AppSettings.serializer(),
                jsonCodec.encodeToString(AppSettings.serializer(), original),
            )

        assertThat(restored.duplicateScannerSessionPreferences.folderPath).isEqualTo("/dupes")
        assertThat(restored.duplicateScannerSessionPreferences.enableSurf).isTrue()
        assertThat(restored.duplicateScannerSessionPreferences.resolvedResolveAction())
            .isEqualTo(DuplicateAction.KEEP_LARGEST)
        assertThat(restored.duplicateScannerSessionPreferences.moveToTrash).isFalse()
    }
}
