package org.kryspetrie.fileimport.architecture

import java.nio.file.Paths
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("UI localization architecture")
class UiLocalizationArchitectureTest {

    @Test
    @DisplayName("UI text must use StringKey locale files or LocalizedExempt")
    fun uiTextMustUseLocaleFiles() {
        val projectDir = Paths.get("").toAbsolutePath()
        val violations = UiTextLocalizationInspector.findViolations(projectDir)

        assertThat(violations)
            .withFailMessage {
                buildString {
                    appendLine(
                        "Hardcoded user-facing UI strings found. Use StringKey + locale JSON, or @LocalizedExempt for exceptions."
                    )
                    appendLine("See docs/LOCALIZATION.md")
                    violations.forEach { v ->
                        appendLine("  ${v.file}:${v.line} \"${v.literal}\"  // ${v.context}")
                    }
                }
            }
            .isEmpty()
    }
}
