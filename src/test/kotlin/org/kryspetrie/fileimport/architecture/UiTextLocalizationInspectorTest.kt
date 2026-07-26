package org.kryspetrie.fileimport.architecture

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("UiTextLocalizationInspector")
class UiTextLocalizationInspectorTest {

    @Test
    @DisplayName("detects hardcoded Text in composable source")
    fun detectsHardcodedText() {
        val source =
            """
            @Composable
            fun Example() {
                Text("Hello world")
            }
            """
                .trimIndent()

        val violations = UiTextLocalizationInspector.inspectFile("ui/Example.kt", source)

        assertThat(violations).anyMatch { it.literal == "Hello world" }
    }

    @Test
    @DisplayName("ignores strings resolved via StringKey")
    fun ignoresStringKeyUsage() {
        val source =
            """
            @Composable
            fun Example() {
                val s = strings()
                Text(s.t(StringKey.ACTION_OK))
            }
            """
                .trimIndent()

        assertThat(UiTextLocalizationInspector.inspectFile("ui/Example.kt", source)).isEmpty()
    }

    @Test
    @DisplayName("ignores file-level LocalizedExempt")
    fun ignoresFileLevelExempt() {
        val source =
            """
            @file:LocalizedExempt("test fixture")
            fun nativeOnly() {
                Text("Not translated")
            }
            """
                .trimIndent()

        assertThat(UiTextLocalizationInspector.inspectFile("ui/Native.kt", source)).isEmpty()
    }
}
