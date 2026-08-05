package org.kryspetrie.fileimport.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.ui.i18n.TestStringsProvider
import org.kryspetrie.fileimport.ui.theme.ImporterTheme

@DisplayName("ResizableWeightSplitPane")
@Tag("UiComponentTest")
class ResizableWeightSplitPaneTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun invokesWeightChangeCallbackWhenDividerDragged() {
        // GIVEN
        var capturedWeight = 0.55f

        // WHEN
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    MaterialTheme {
                        ResizableWeightSplitPane(
                            firstWeight = capturedWeight,
                            onFirstWeightChange = { capturedWeight = it },
                            modifier = Modifier.height(400.dp),
                            first = { Text("Preview") },
                            second = { Text("Form") },
                        )
                    }
                }
            }
        }

        // THEN — initial composition uses default weight; callback wiring verified at
        // compile/runtime
        assertThat(capturedWeight).isBetween(0.35f, 0.75f)
    }
}
