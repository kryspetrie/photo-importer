package org.kryspetrie.fileimport.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("MediaImportSessionPreferences")
class MediaImportSessionPreferencesTest {

    @Test
    fun defaultsAreCollapsed() {
        val prefs = MediaImportSessionPreferences()
        assertThat(prefs.settingsExpanded).isFalse()
        assertThat(prefs.historyExpanded).isFalse()
    }

    @Test
    fun survivesAppSettingsJsonRoundTrip() {
        val original =
            AppSettings(
                mediaImportSessionPreferences =
                    MediaImportSessionPreferences(settingsExpanded = true, historyExpanded = true)
            )
        val jsonCodec = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val restored =
            jsonCodec.decodeFromString(
                AppSettings.serializer(),
                jsonCodec.encodeToString(AppSettings.serializer(), original),
            )

        assertThat(restored.mediaImportSessionPreferences.settingsExpanded).isTrue()
        assertThat(restored.mediaImportSessionPreferences.historyExpanded).isTrue()
    }
}
