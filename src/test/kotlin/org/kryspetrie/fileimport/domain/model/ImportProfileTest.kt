package org.kryspetrie.fileimport.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ImportProfile")
class ImportProfileTest {

    @Test
    @DisplayName("should have sensible defaults")
    fun shouldHaveDefaults() {
        val profile = ImportProfile(name = "Test")

        assertThat(profile.name).isEqualTo("Test")
        assertThat(profile.description).isEmpty()
        assertThat(profile.cameraName).isEmpty()
        assertThat(profile.lastSourcePath).isEmpty()
        assertThat(profile.lastDestinationPath).isEmpty()
        assertThat(profile.configuration).isEqualTo(ImportConfiguration())
        assertThat(profile.id).isNotBlank()
    }

    @Test
    @DisplayName("should generate unique IDs")
    fun shouldGenerateUniqueIds() {
        val p1 = ImportProfile(name = "A")
        val p2 = ImportProfile(name = "B")

        assertThat(p1.id).isNotEqualTo(p2.id)
    }

    @Nested
    @DisplayName("AppSettings")
    inner class AppSettingsTest {
        @Test
        @DisplayName("should have empty defaults")
        fun shouldHaveEmptyDefaults() {
            val settings = AppSettings()

            assertThat(settings.profiles).isEmpty()
            assertThat(settings.activeProfileId).isNull()
            assertThat(settings.theme).isEqualTo(AppTheme.SYSTEM)
            assertThat(settings.savedFolderPresets).isEmpty()
            assertThat(settings.savedFilenamePresets).isEmpty()
        }

        @Test
        @DisplayName("should store and retrieve window state")
        fun shouldStoreWindowState() {
            val ws = WindowState(width = 1920, height = 1080, x = 100, y = 200, isMaximized = true)
            val settings = AppSettings(windowState = ws)

            assertThat(settings.windowState.width).isEqualTo(1920)
            assertThat(settings.windowState.isMaximized).isTrue()
        }
    }
}
