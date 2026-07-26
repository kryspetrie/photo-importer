package org.kryspetrie.fileimport.ui.screens

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.model.ImportConfiguration
import org.kryspetrie.fileimport.domain.port.DispatcherProvider
import org.kryspetrie.fileimport.infrastructure.i18n.JsonLocaleAdapter
import org.kryspetrie.fileimport.ui.i18n.Strings
import org.kryspetrie.fileimport.ui.i18n.configSummary

@DisplayName("configSummary")
class ConfigSummaryTest {

    companion object {
        private lateinit var strings: Strings

        @BeforeAll
        @JvmStatic
        fun loadLocale() {
            val localePort =
                JsonLocaleAdapter(
                    dispatchers =
                        object : DispatcherProvider {
                            override val io = Dispatchers.Unconfined
                            override val default = Dispatchers.Unconfined
                        },
                    appLogger = null,
                )
            runBlocking { localePort.setLocale("en") }
            strings = Strings(localePort)
        }
    }

    @Test
    @DisplayName("should show folder pattern and original names for defaults")
    fun shouldShowDefaults() {
        val config = ImportConfiguration()
        val summary = strings.configSummary(config)

        assertThat(summary).contains("{yyyy-MM-dd}")
        assertThat(summary).contains("original names")
        assertThat(summary).contains("verify")
        assertThat(summary).doesNotContain("delete source")
        assertThat(summary).doesNotContain("dedup")
    }

    @Test
    @DisplayName("should show Flat when no subfolders")
    fun shouldShowFlat() {
        val config = ImportConfiguration(createSubfolders = false)
        val summary = strings.configSummary(config)

        assertThat(summary).startsWith("Flat")
        assertThat(summary).doesNotContain("{yyyy-MM-dd}")
    }

    @Test
    @DisplayName("should show custom filename pattern when not preserving original")
    fun shouldShowCustomFilename() {
        val config =
            ImportConfiguration(preserveOriginalName = false, fileNamePattern = "{yyyy}_{counter}")
        val summary = strings.configSummary(config)

        assertThat(summary).contains("{yyyy}_{counter}")
        assertThat(summary).doesNotContain("original names")
    }

    @Test
    @DisplayName("should include delete source when enabled")
    fun shouldShowDeleteSource() {
        val config = ImportConfiguration(deleteAfterImport = true)
        val summary = strings.configSummary(config)

        assertThat(summary).contains("delete source")
    }

    @Test
    @DisplayName("should include dedup when visual duplicates enabled")
    fun shouldShowDedup() {
        val config = ImportConfiguration(detectVisualDuplicates = true)
        val summary = strings.configSummary(config)

        assertThat(summary).contains("dedup")
    }

    @Test
    @DisplayName("should not include verify when disabled")
    fun shouldNotShowVerify() {
        val config = ImportConfiguration(verifyAfterCopy = false)
        val summary = strings.configSummary(config)

        assertThat(summary).doesNotContain("verify")
    }

    @Test
    @DisplayName("should show custom folder pattern")
    fun shouldShowCustomFolderPattern() {
        val config = ImportConfiguration(folderPattern = "{yyyy}/{MM}/{dd}")
        val summary = strings.configSummary(config)

        assertThat(summary).contains("{yyyy}/{MM}/{dd}")
    }

    @Test
    @DisplayName("should combine multiple flags")
    fun shouldCombineFlags() {
        val config =
            ImportConfiguration(
                deleteAfterImport = true,
                detectVisualDuplicates = true,
                verifyAfterCopy = true,
            )
        val summary = strings.configSummary(config)

        assertThat(summary).contains("verify")
        assertThat(summary).contains("delete source")
        assertThat(summary).contains("dedup")
    }
}
