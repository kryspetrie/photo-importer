package org.kryspetrie.fileimport.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("UiComponentTest")
class PlaceholderHelpTooltipTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun shouldDisplayShowPlaceholdersButton() {
        composeTestRule.setContent {
            PlaceholderHelpTooltip(placeholders = mapOf("{yyyy}" to "Year"))
        }

        composeTestRule.onNodeWithText("Show placeholders").assertIsDisplayed()
    }

    @Test
    fun shouldOpenDialogOnButtonClick() {
        composeTestRule.setContent {
            PlaceholderHelpTooltip(placeholders = mapOf("{yyyy}" to "Year"))
        }

        composeTestRule.onNodeWithText("Show placeholders").performClick()

        composeTestRule.onNodeWithText("Available Placeholders").assertIsDisplayed()
    }

    @Test
    fun shouldDisplayPlaceholderKeysInDialog() {
        composeTestRule.setContent {
            PlaceholderHelpTooltip(placeholders = mapOf("{yyyy}" to "Year", "{MM}" to "Month"))
        }

        composeTestRule.onNodeWithText("Show placeholders").performClick()

        composeTestRule.onNodeWithText("{yyyy}").assertIsDisplayed()
        composeTestRule.onNodeWithText("{MM}").assertIsDisplayed()
    }

    @Test
    fun shouldDisplayCloseButtonInDialog() {
        composeTestRule.setContent {
            PlaceholderHelpTooltip(placeholders = mapOf("{yyyy}" to "Year"))
        }

        composeTestRule.onNodeWithText("Show placeholders").performClick()

        composeTestRule.onNodeWithText("Close").assertIsDisplayed()
    }

    @Test
    fun shouldCloseDialogOnCloseClick() {
        composeTestRule.setContent {
            PlaceholderHelpTooltip(placeholders = mapOf("{yyyy}" to "Year"))
        }

        composeTestRule.onNodeWithText("Show placeholders").performClick()
        composeTestRule.onNodeWithText("Available Placeholders").assertIsDisplayed()

        composeTestRule.onNodeWithText("Close").performClick()

        composeTestRule.onNodeWithText("Available Placeholders").assertIsNotDisplayed()
    }

    @Test
    fun shouldHandleEmptyPlaceholders() {
        composeTestRule.setContent { PlaceholderHelpTooltip(placeholders = emptyMap()) }

        composeTestRule.onNodeWithText("Show placeholders").performClick()

        composeTestRule.onNodeWithText("Available Placeholders").assertIsDisplayed()
    }
}
