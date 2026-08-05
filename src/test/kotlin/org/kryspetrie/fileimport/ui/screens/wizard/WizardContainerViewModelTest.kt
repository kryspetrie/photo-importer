package org.kryspetrie.fileimport.ui.screens.wizard

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.TabSettings
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@DisplayName("WizardContainerViewModel")
class WizardContainerViewModelTest {
    @Test
    fun initialExportDestinationUsesTabPathOrDefault() {
        val pathsPort = mock<org.kryspetrie.fileimport.domain.port.PathsPort>()
        whenever(pathsPort.defaultDestination).thenReturn("/default/out")

        val vm =
            WizardContainerViewModel(
                detectorService = mock(),
                exportService = mock(),
                perspectiveService = mock(),
                appLogger = mock(),
                settingsPort = mock(),
                dispatcherProvider = mock(),
                faceRegionTransformer = mock(),
                faceDetectionPort = mock(),
                orientationCorrection = mock(),
                imageProcessing = mock(),
                pathsPort = pathsPort,
                localePort = mock(),
            )

        assertThat(
                vm.initialExportDestination(
                    AppSettings(
                        photoScanImportTabSettings = TabSettings(lastDestinationPath = "/saved")
                    )
                )
            )
            .isEqualTo("/saved")
        assertThat(vm.initialExportDestination(AppSettings())).isEqualTo("/default/out")
    }
}
