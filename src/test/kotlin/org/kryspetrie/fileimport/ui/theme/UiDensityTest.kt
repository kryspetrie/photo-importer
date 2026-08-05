package org.kryspetrie.fileimport.ui.theme

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.model.UiDensity

@DisplayName("UiDensity scale")
class UiDensityTest {

    @Test
    fun compactHasSmallestControlHeight() {
        // GIVEN / WHEN
        val compact = UiDensity.COMPACT.toScale()
        val comfortable = UiDensity.COMFORTABLE.toScale()
        val spacious = UiDensity.SPACIOUS.toScale()

        // THEN
        assertThat(compact.controlMinHeight.value).isLessThan(comfortable.controlMinHeight.value)
        assertThat(comfortable.controlMinHeight.value).isLessThan(spacious.controlMinHeight.value)
    }

    @Test
    fun comfortableUsesExpectedDefaults() {
        // GIVEN / WHEN
        val scale = UiDensity.COMFORTABLE.toScale()

        // THEN
        assertThat(scale.controlMinHeight.value).isEqualTo(36f)
        assertThat(scale.iconSize.value).isEqualTo(20f)
        assertThat(scale.thumbnailCardSize.value).isEqualTo(88f)
        assertThat(scale.commandBarHeight.value).isEqualTo(48f)
    }

    @Test
    fun spacingIncreasesWithDensity() {
        // GIVEN / WHEN
        val compact = UiDensity.COMPACT.toScale()
        val spacious = UiDensity.SPACIOUS.toScale()

        // THEN
        assertThat(compact.spacingMd.value).isLessThan(spacious.spacingMd.value)
        assertThat(compact.thumbnailCardSize.value).isLessThan(spacious.thumbnailCardSize.value)
    }
}
