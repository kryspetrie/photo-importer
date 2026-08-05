package org.kryspetrie.fileimport.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("MetadataEditorSessionPreferences")
class MetadataEditorSessionPreferencesTest {

    @Test
    fun normalizedOutputModeReturnsValidModes() {
        assertThat(
                MetadataEditorSessionPreferences(outputMode = "OVERWRITE").normalizedOutputMode()
            )
            .isEqualTo("OVERWRITE")
        assertThat(MetadataEditorSessionPreferences(outputMode = "SAVE_NEW").normalizedOutputMode())
            .isEqualTo("SAVE_NEW")
    }

    @Test
    fun normalizedOutputModeFallsBackToOverwriteForInvalidValues() {
        assertThat(MetadataEditorSessionPreferences(outputMode = "INVALID").normalizedOutputMode())
            .isEqualTo("OVERWRITE")
        assertThat(MetadataEditorSessionPreferences(outputMode = "").normalizedOutputMode())
            .isEqualTo("OVERWRITE")
    }

    @Test
    fun preservesConfigurationFields() {
        val prefs =
            MetadataEditorSessionPreferences(
                outputMode = "SAVE_NEW",
                outputDirectory = "/exports",
                includeSubfolders = true,
            )

        assertThat(prefs.outputDirectory).isEqualTo("/exports")
        assertThat(prefs.includeSubfolders).isTrue()
        assertThat(prefs.normalizedOutputMode()).isEqualTo("SAVE_NEW")
    }
}
