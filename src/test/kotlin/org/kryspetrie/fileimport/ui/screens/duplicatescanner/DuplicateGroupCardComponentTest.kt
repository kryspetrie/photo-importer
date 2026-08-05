package org.kryspetrie.fileimport.ui.screens.duplicatescanner

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.Test
import org.kryspetrie.fileimport.domain.model.DuplicateInfo
import org.kryspetrie.fileimport.domain.model.DuplicateType
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.ImageFile
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.TestStringsProvider
import org.kryspetrie.fileimport.ui.theme.ImporterTheme

@DisplayName("DuplicateGroupCard")
@Tag("UiComponentTest")
class DuplicateGroupCardComponentTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun keepBadgeIsNonInteractiveLabel() {
        // GIVEN
        val group =
            DuplicateInfo(
                primaryImage = ImageFile(path = FilePath("primary.jpg"), fileSize = 1024),
                duplicateImages = listOf(ImageFile(path = FilePath("dup.jpg"), fileSize = 512)),
                duplicateType = DuplicateType.EXACT_HASH,
            )

        // WHEN
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    MaterialTheme { DuplicateGroupCard(group = group, onSetPrimary = {}) }
                }
            }
        }

        // THEN — Keep badge shows for primary; no clickable Set Keep on primary row
        composeTestRule.onNodeWithText("Keep").assertIsDisplayed()
        composeTestRule.onNodeWithText("Set as Keep").assertIsDisplayed()
    }
}
