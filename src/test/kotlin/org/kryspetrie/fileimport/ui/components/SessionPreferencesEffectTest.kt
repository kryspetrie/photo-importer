package org.kryspetrie.fileimport.ui.components

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.model.MetadataEditorSessionPreferences

@DisplayName("SessionPreferencesEffect")
class SessionPreferencesEffectTest {

    @Test
    fun doesNotPersistBeforeRestoreCompletes() {
        val stored = MetadataEditorSessionPreferences(includeSubfolders = true)
        val current = MetadataEditorSessionPreferences(includeSubfolders = false)

        assertThat(shouldPersistSessionPreferences(false, current, stored)).isFalse()
    }

    @Test
    fun doesNotPersistWhenCurrentMatchesStored() {
        val prefs =
            MetadataEditorSessionPreferences(outputMode = "SAVE_NEW", outputDirectory = "/out")

        assertThat(shouldPersistSessionPreferences(true, prefs, prefs)).isFalse()
    }

    @Test
    fun persistsWhenCurrentDiffersFromStoredAfterRestore() {
        val stored = MetadataEditorSessionPreferences(includeSubfolders = false)
        val current = MetadataEditorSessionPreferences(includeSubfolders = true)

        assertThat(shouldPersistSessionPreferences(true, current, stored)).isTrue()
    }
}
