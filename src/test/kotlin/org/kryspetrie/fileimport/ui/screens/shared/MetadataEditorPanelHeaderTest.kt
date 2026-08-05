package org.kryspetrie.fileimport.ui.screens.shared

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@DisplayName("MetadataEditorPanelHeader Component Tests")
@Tag("UiComponentTest")
class MetadataEditorPanelHeaderTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    @DisplayName("single-edit shows title and Clear without Apply")
    fun singleEditShowsClearOnly() {
        composeTestRule.setContent {
            MetadataEditorPanelHeader(title = "IMG_001.jpg", onClear = {})
        }

        composeTestRule.onNodeWithText("IMG_001.jpg").assertIsDisplayed()
        composeTestRule.onNodeWithText("Clear").assertIsDisplayed()
    }

    @Test
    @DisplayName("multi-edit shows Clear, Apply, and hint")
    fun multiEditShowsClearApplyAndHint() {
        composeTestRule.setContent {
            MetadataEditorPanelHeader(
                title = "3 photos selected",
                onClear = {},
                onApply = {},
                hint = "Fill fields to apply to all selected photos",
            )
        }

        composeTestRule.onNodeWithText("3 photos selected").assertIsDisplayed()
        composeTestRule.onNodeWithText("Clear").assertIsDisplayed()
        composeTestRule.onNodeWithText("Apply").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Fill fields to apply to all selected photos")
            .assertIsDisplayed()
    }
}
