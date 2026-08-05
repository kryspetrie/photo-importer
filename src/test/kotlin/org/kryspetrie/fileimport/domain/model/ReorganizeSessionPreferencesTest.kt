package org.kryspetrie.fileimport.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ReorganizeSessionPreferences")
class ReorganizeSessionPreferencesTest {

    @Test
    fun resolvesInvalidReorgModeToMove() {
        val prefs = ReorganizeSessionPreferences(reorgMode = "INVALID")
        assertThat(prefs.resolvedReorgMode()).isEqualTo(ReorganizeMode.MOVE)
    }

    @Test
    fun preservesConfigurationFields() {
        val config = ImportConfiguration(createSubfolders = false, autoOrientEnabled = true)
        val prefs =
            ReorganizeSessionPreferences(
                folderPath = "/photos",
                configuration = config,
                renameOnly = true,
                reorgMode = ReorganizeMode.COPY.name,
                settingsExpanded = true,
            )

        assertThat(prefs.folderPath).isEqualTo("/photos")
        assertThat(prefs.configuration.createSubfolders).isFalse()
        assertThat(prefs.renameOnly).isTrue()
        assertThat(prefs.resolvedReorgMode()).isEqualTo(ReorganizeMode.COPY)
        assertThat(prefs.settingsExpanded).isTrue()
    }
}
