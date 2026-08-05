package org.kryspetrie.fileimport.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Photo Scan Import tab settings")
class PhotoScanImportTabSettingsTest {

    @Test
    fun tabSettingsFromScreenStateMatchesPersistedShape() {
        // GIVEN — same shape PhotoScanImportScreen passes to SessionPreferencesEffect
        val config = ImportConfiguration(autoOrientEnabled = true, createSubfolders = false)
        val base = TabSettings()

        // WHEN
        val current =
            base
                .withRecentSourcePath("/scan-src")
                .withRecentDestinationPath("/scan-dest")
                .withConfiguration(config)

        // THEN
        assertThat(current.lastSourcePath).isEqualTo("/scan-src")
        assertThat(current.lastDestinationPath).isEqualTo("/scan-dest")
        assertThat(current.configuration.autoOrientEnabled).isTrue()
        assertThat(current.configuration.createSubfolders).isFalse()
    }

    @Test
    fun photoScanImportTabSettingsSurviveAppSettingsRoundTrip() {
        val original =
            AppSettings(
                photoScanImportTabSettings =
                    TabSettings(
                        lastSourcePath = "/in",
                        lastDestinationPath = "/out",
                        configuration = ImportConfiguration(detectVisualDuplicates = true),
                    )
            )
        val jsonCodec = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val restored =
            jsonCodec.decodeFromString(
                AppSettings.serializer(),
                jsonCodec.encodeToString(AppSettings.serializer(), original),
            )

        assertThat(restored.photoScanImportTabSettings.lastSourcePath).isEqualTo("/in")
        assertThat(restored.photoScanImportTabSettings.configuration.detectVisualDuplicates)
            .isTrue()
    }
}
