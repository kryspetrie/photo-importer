package org.kryspetrie.fileimport.ui.screens.mediaimport

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@DisplayName("MediaImportLandingHero Component Tests")
@Tag("UiComponentTest")
class MediaImportLandingHeroTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    @DisplayName("should display landing hero subtitle and steps")
    fun shouldDisplayLandingHero() {
        composeTestRule.setContent { MediaImportLandingHero() }

        composeTestRule.onNodeWithText("Import").assertIsDisplayed()
        composeTestRule
            .onNodeWithText(
                "Import photos and videos from folders or cameras — organize, deduplicate, and copy to your library."
            )
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Choose source → Configure naming → Import (or preview first)")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Press Enter to import all when source and destination are set.")
            .assertIsDisplayed()
    }
}
