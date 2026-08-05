package org.kryspetrie.fileimport.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("DuplicateScannerSessionPreferences")
class DuplicateScannerSessionPreferencesTest {

    @Test
    fun resolvesInvalidResolveActionToDefault() {
        val prefs = DuplicateScannerSessionPreferences(resolveAction = "INVALID")
        assertThat(prefs.resolvedResolveAction()).isEqualTo(DuplicateAction.KEEP_HIGHEST_RES)
    }

    @Test
    fun preservesDetectionSettings() {
        val prefs =
            DuplicateScannerSessionPreferences(
                folderPath = "/library",
                enableHash = false,
                enableExif = true,
                enableSurf = true,
                resolveAction = DuplicateAction.KEEP_NEWEST.name,
                moveToTrash = false,
            )

        assertThat(prefs.folderPath).isEqualTo("/library")
        assertThat(prefs.enableHash).isFalse()
        assertThat(prefs.enableSurf).isTrue()
        assertThat(prefs.resolvedResolveAction()).isEqualTo(DuplicateAction.KEEP_NEWEST)
        assertThat(prefs.moveToTrash).isFalse()
    }
}
