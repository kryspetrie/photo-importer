package org.kryspetrie.fileimport.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("OverrideUiSemantics")
class OverrideUiSemanticsTest {

    @Test
    fun nullAndKeepSourceAreIncluded() {
        assertThat(OverrideUiSemantics.isIncluded(null)).isTrue()
        assertThat(OverrideUiSemantics.isIncluded(OverrideState.KEEP_SOURCE)).isTrue()
        assertThat(OverrideUiSemantics.isIncluded(OverrideState.OVERRIDE)).isTrue()
    }

    @Test
    fun nullOutIsExcluded() {
        assertThat(OverrideUiSemantics.isIncluded(OverrideState.NULL_OUT)).isFalse()
    }

    @Test
    fun fromIncludedMapsToKeepSourceOrNullOut() {
        assertThat(OverrideUiSemantics.fromIncluded(true)).isEqualTo(OverrideState.KEEP_SOURCE)
        assertThat(OverrideUiSemantics.fromIncluded(false)).isEqualTo(OverrideState.NULL_OUT)
    }
}
