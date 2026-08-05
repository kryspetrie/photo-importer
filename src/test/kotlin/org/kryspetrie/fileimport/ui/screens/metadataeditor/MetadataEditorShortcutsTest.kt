package org.kryspetrie.fileimport.ui.screens.metadataeditor

import androidx.compose.ui.input.key.Key
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("MetadataEditorShortcuts")
class MetadataEditorShortcutsTest {

    @Test
    fun mapsMetaEnterToApplyMultiEdit() {
        // GIVEN / WHEN
        val action = metadataEditorShortcutActionForKey(Key.Enter, isMeta = true)

        // THEN
        assertThat(action).isEqualTo(MetadataEditorShortcutAction.APPLY_MULTI_EDIT)
    }

    @Test
    fun mapsMetaBToBrowserDrawerToggle() {
        // GIVEN / WHEN
        val action = metadataEditorShortcutActionForKey(Key.B, isMeta = true)

        // THEN
        assertThat(action).isEqualTo(MetadataEditorShortcutAction.TOGGLE_BROWSER_DRAWER)
    }

    @Test
    fun ignoresShortcutsWithoutMetaKey() {
        // GIVEN / WHEN
        val action = metadataEditorShortcutActionForKey(Key.T, isMeta = false)

        // THEN
        assertThat(action).isNull()
    }
}
