package org.kryspetrie.fileimport.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.ui.components.SettingsToggle
import org.kryspetrie.fileimport.ui.screens.components.CollapsibleSubsection
import org.kryspetrie.fileimport.ui.screens.components.CompactCheck
import org.kryspetrie.fileimport.ui.screens.components.ProgressCard

@Tag("UiComponentTest")
class SettingsToggleTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun `displays label text`() {
        composeTestRule.setContent {
            SettingsToggle(checked = false, onCheckedChange = {}, label = "Test Toggle")
        }

        composeTestRule.onNodeWithText("Test Toggle").assertIsDisplayed()
    }

    @Test
    fun `displays description text`() {
        composeTestRule.setContent {
            SettingsToggle(
                checked = false,
                onCheckedChange = {},
                label = "Test Toggle",
                description = "Helper text",
            )
        }

        composeTestRule.onNodeWithText("Test Toggle").assertIsDisplayed()
        composeTestRule.onNodeWithText("Helper text").assertIsDisplayed()
    }

    @Test
    fun `calls onCheckedChange when label clicked`() {
        var clickedValue: Boolean? = null

        composeTestRule.setContent {
            SettingsToggle(
                checked = false,
                onCheckedChange = { clickedValue = it },
                label = "Toggle Me",
            )
        }

        composeTestRule.onNodeWithText("Toggle Me").performClick()

        assertThat(clickedValue).isTrue()
    }
}

@Tag("UiComponentTest")
class CompactCheckTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun `displays label text`() {
        composeTestRule.setContent {
            CompactCheck(checked = false, onCheckedChange = {}, label = "Test Label")
        }

        composeTestRule.onNodeWithText("Test Label").assertIsDisplayed()
    }

    @Test
    fun `displays when checked is true`() {
        composeTestRule.setContent {
            CompactCheck(checked = true, onCheckedChange = {}, label = "Enabled")
        }

        composeTestRule.onNodeWithText("Enabled").assertIsDisplayed()
    }

    @Test
    fun `calls onCheckedChange when clicked`() {
        var clickedValue: Boolean? = null

        composeTestRule.setContent {
            CompactCheck(checked = false, onCheckedChange = { clickedValue = it }, label = "Toggle")
        }

        composeTestRule.onNodeWithText("Toggle").performClick()

        assertThat(clickedValue).isTrue()
    }
}

@Tag("UiComponentTest")
class ProgressCardTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun `displays title text`() {
        composeTestRule.setContent {
            ProgressCard(title = "Scanning...", current = 0, total = 0, currentFile = "")
        }

        composeTestRule.onNodeWithText("Scanning...").assertIsDisplayed()
    }

    @Test
    fun `does not show progress count when total is zero`() {
        composeTestRule.setContent {
            ProgressCard(title = "Scanning...", current = 0, total = 0, currentFile = "")
        }

        composeTestRule.onNodeWithText("0 / 0").assertDoesNotExist()
    }

    @Test
    fun `displays progress count when total is greater than zero`() {
        composeTestRule.setContent {
            ProgressCard(
                title = "Loading",
                current = 5,
                total = 10,
                currentFile = "file.jpg",
                icon = Icons.Default.FolderOpen,
            )
        }

        composeTestRule.onNodeWithText("5 / 10", substring = true).assertIsDisplayed()
    }

    @Test
    fun `displays current file name when non-blank`() {
        composeTestRule.setContent {
            ProgressCard(
                title = "Loading",
                current = 5,
                total = 10,
                currentFile = "file.jpg",
                icon = Icons.Default.FolderOpen,
            )
        }

        composeTestRule.onNodeWithText("file.jpg").assertIsDisplayed()
    }

    @Test
    fun `displays title with icon provided`() {
        composeTestRule.setContent {
            ProgressCard(
                title = "Loading",
                current = 5,
                total = 10,
                currentFile = "file.jpg",
                icon = Icons.Default.FolderOpen,
            )
        }

        composeTestRule.onNodeWithText("Loading").assertIsDisplayed()
    }

    @Test
    fun `displays title with null icon`() {
        composeTestRule.setContent {
            ProgressCard(
                title = "Loading",
                current = 5,
                total = 10,
                currentFile = "file.jpg",
                icon = null,
            )
        }

        composeTestRule.onNodeWithText("Loading").assertIsDisplayed()
    }
}

@Tag("UiComponentTest")
class CollapsibleSubsectionTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun `collapsed state hides content`() {
        composeTestRule.setContent {
            CollapsibleSubsection(
                title = "Settings",
                icon = Icons.Default.Tune,
                expanded = false,
                onToggle = {},
            ) {
                androidx.compose.material.Text("Hidden Content")
            }
        }

        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hidden Content").assertDoesNotExist()
    }

    @Test
    fun `expanded state shows content`() {
        composeTestRule.setContent {
            CollapsibleSubsection(
                title = "Settings",
                icon = Icons.Default.Tune,
                expanded = true,
                onToggle = {},
            ) {
                androidx.compose.material.Text("Visible Content")
            }
        }

        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("Visible Content").assertIsDisplayed()
    }

    @Test
    fun `clicking toggle calls onToggle callback`() {
        var toggleCalled = false

        composeTestRule.setContent {
            CollapsibleSubsection(
                title = "Settings",
                icon = Icons.Default.Tune,
                expanded = false,
                onToggle = { toggleCalled = true },
            ) {
                androidx.compose.material.Text("Content")
            }
        }

        composeTestRule.onNodeWithText("Settings").performClick()

        assertThat(toggleCalled).isTrue()
    }
}
