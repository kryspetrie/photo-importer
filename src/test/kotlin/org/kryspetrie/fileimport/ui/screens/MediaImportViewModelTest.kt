package org.kryspetrie.fileimport.ui.screens

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.model.ImportConfiguration
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.domain.port.LocalePort
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@DisplayName("MediaImportViewModel")
class MediaImportViewModelTest {

    private fun createViewModel(): MediaImportViewModel {
        val localePort = mock<LocalePort>()
        whenever(localePort.t(any<StringKey>())).thenAnswer { inv ->
            inv.getArgument<StringKey>(0).name
        }
        whenever(localePort.t(any<StringKey>(), any())).thenAnswer { inv ->
            inv.getArgument<StringKey>(0).name
        }
        return MediaImportViewModel(
            importService = mock(),
            devicePort = mock(),
            historyPort = mock(),
            settingsPort = mock(),
            watchFolderManager = mock(),
            timeProvider = mock(),
            pathsPort = mock(),
            localePort = localePort,
        )
    }

    @Test
    fun initializeFromSettingsRestoresPathsAndConfiguration() {
        // GIVEN
        val vm = createViewModel()
        val config =
            ImportConfiguration(
                autoOrientEnabled = true,
                createSubfolders = false,
                deleteAfterImport = true,
            )

        // WHEN
        vm.initializeFromSettings("/source", "/destination", config)

        // THEN
        assertThat(vm.sourcePath).isEqualTo("/source")
        assertThat(vm.destinationPath).isEqualTo("/destination")
        assertThat(vm.customConfig.autoOrientEnabled).isTrue()
        assertThat(vm.customConfig.createSubfolders).isFalse()
        assertThat(vm.customConfig.deleteAfterImport).isTrue()
    }

    @Test
    fun syncFromSettingsUpdatesSourceAndDestinationWhenPresent() {
        // GIVEN
        val vm = createViewModel()
        vm.sourcePath = "/old"
        vm.destinationPath = "/old-dest"

        // WHEN
        vm.syncFromSettings("/new-source", "/new-dest")

        // THEN
        assertThat(vm.sourcePath).isEqualTo("/new-source")
        assertThat(vm.destinationPath).isEqualTo("/new-dest")
    }

    @Test
    fun syncFromSettingsPreservesDestinationWhenBlank() {
        // GIVEN
        val vm = createViewModel()
        vm.destinationPath = "/keep-me"

        // WHEN
        vm.syncFromSettings("/src", "")

        // THEN
        assertThat(vm.destinationPath).isEqualTo("/keep-me")
    }

    @Test
    fun tabSettingsFromViewModelStateMatchesPersistedShape() {
        // GIVEN
        val vm = createViewModel()
        val config = ImportConfiguration(detectVisualDuplicates = true)
        vm.initializeFromSettings("/src", "/dest", config)

        // WHEN — same shape MediaImportScreen passes to SessionPreferencesEffect
        val base = org.kryspetrie.fileimport.domain.model.TabSettings()
        val current =
            base
                .withRecentSourcePath(vm.sourcePath)
                .withRecentDestinationPath(vm.destinationPath)
                .withConfiguration(vm.customConfig)

        // THEN
        assertThat(current.lastSourcePath).isEqualTo("/src")
        assertThat(current.lastDestinationPath).isEqualTo("/dest")
        assertThat(current.configuration.detectVisualDuplicates).isTrue()
    }

    @Test
    fun resetFlowClearsDetectedDuplicateCount() {
        val vm = createViewModel()
        // WHEN
        vm.resetFlow()
        // THEN
        assertThat(vm.detectedDuplicateCount).isEqualTo(0)
        assertThat(vm.duplicates).isEmpty()
    }
}
