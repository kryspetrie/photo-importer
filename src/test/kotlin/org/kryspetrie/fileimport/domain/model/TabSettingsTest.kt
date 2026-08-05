package org.kryspetrie.fileimport.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("TabSettings")
class TabSettingsTest {

    @Test
    @DisplayName("withConfiguration replaces import configuration")
    fun withConfigurationReplacesImportConfiguration() {
        val config =
            ImportConfiguration(
                autoOrientEnabled = true,
                detectVisualDuplicates = true,
                createSubfolders = false,
            )

        val tab = TabSettings().withConfiguration(config)

        assertThat(tab.configuration.autoOrientEnabled).isTrue()
        assertThat(tab.configuration.detectVisualDuplicates).isTrue()
        assertThat(tab.configuration.createSubfolders).isFalse()
    }

    @Test
    @DisplayName("withConfiguration preserves paths")
    fun withConfigurationPreservesPaths() {
        val tab =
            TabSettings(lastSourcePath = "/src", lastDestinationPath = "/dest")
                .withConfiguration(ImportConfiguration(deleteAfterImport = true))

        assertThat(tab.lastSourcePath).isEqualTo("/src")
        assertThat(tab.lastDestinationPath).isEqualTo("/dest")
        assertThat(tab.configuration.deleteAfterImport).isTrue()
    }
}
