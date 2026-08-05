package org.kryspetrie.fileimport.ui.screens.wizard.edit

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.Test
import org.kryspetrie.fileimport.domain.model.MetadataHistory
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.TestStringsProvider
import org.kryspetrie.fileimport.ui.theme.ImporterTheme

@DisplayName("QuickEditMetadataFields keywords")
@Tag("UiComponentTest")
class QuickEditMetadataFieldsComponentTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun showsKeywordInputWhenHistoryIsEmpty() {
        // GIVEN / WHEN
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    MaterialTheme {
                        QuickEditMetadataFields(
                            description = "",
                            onDescriptionChange = {},
                            keywords = "",
                            onKeywordsChange = {},
                            originalDate = "",
                            onOriginalDateChange = {},
                            year = "",
                            onYearChange = {},
                            metadataHistory = MetadataHistory(),
                            onMetadataHistoryUpdate = { _, _ -> },
                            onMetadataHistoryRemove = { _, _ -> },
                        )
                    }
                }
            }
        }

        // THEN
        composeTestRule.onNodeWithText("Keywords").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Add keyword...")
            .assertIsDisplayed()
    }
}
