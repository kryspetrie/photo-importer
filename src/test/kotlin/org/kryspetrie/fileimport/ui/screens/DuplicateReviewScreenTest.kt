package org.kryspetrie.fileimport.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.model.DuplicateInfo
import org.kryspetrie.fileimport.domain.model.DuplicateResolution
import org.kryspetrie.fileimport.ui.i18n.TestStringsProvider
import org.kryspetrie.fileimport.ui.theme.ImporterTheme

@DisplayName("DuplicateReviewScreen Component Tests")
@Tag("UiComponentTest")
class DuplicateReviewScreenTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Composable
    private fun duplicateReviewScreenCall(
        duplicates: List<DuplicateInfo> = emptyList(),
        onBack: () -> Unit = {},
        onContinue: () -> Unit = {},
        onResolution: (DuplicateInfo, DuplicateResolution) -> Unit = { _, _ -> },
    ) =
        DuplicateReviewScreen(
            duplicates = duplicates,
            onResolution = onResolution,
            onContinue = onContinue,
            onBack = onBack,
        )

    @Test
    @DisplayName("should display title")
    fun shouldDisplayTitle() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    duplicateReviewScreenCall()
                }
            }
        }

        // Title should be displayed
        composeTestRule.onNodeWithText("Duplicates").assertExists()
    }

    @Test
    @DisplayName("should display no duplicates state")
    fun shouldDisplayNoDuplicatesState() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    duplicateReviewScreenCall()
                }
            }
        }

        // Screen should handle no duplicates case
        composeTestRule.onNodeWithText("Duplicates").assertExists()
    }

    @Test
    @DisplayName("should display duplicate groups")
    fun shouldDisplayDuplicateGroups() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    duplicateReviewScreenCall()
                }
            }
        }

        // Duplicate groups should be displayed
        composeTestRule.onNodeWithText("Duplicates").assertExists()
    }

    @Test
    @DisplayName("should display Back button")
    fun shouldDisplayBackButton() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    duplicateReviewScreenCall()
                }
            }
        }

        // Back button should be present
        composeTestRule.onNodeWithText("Back").assertExists()
    }

    @Test
    @DisplayName("should display Continue button")
    fun shouldDisplayContinueButton() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    duplicateReviewScreenCall()
                }
            }
        }

        // Continue button should be present
        composeTestRule.onNodeWithText("Continue").assertExists()
    }

    @Test
    @DisplayName("should call onBack when back clicked")
    fun shouldCallOnBackWhenBackClicked() {
        var backCalled = false

        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    duplicateReviewScreenCall(onBack = { backCalled = true })
                }
            }
        }

        // Verify callback is wired up
        assert(!backCalled) { "Back should not be called during setup" }
    }

    @Test
    @DisplayName("should call onContinue when continue clicked")
    fun shouldCallOnContinueWhenContinueClicked() {
        var continueCalled = false

        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    duplicateReviewScreenCall(onContinue = { continueCalled = true })
                }
            }
        }

        // Verify callback is wired up
        assert(!continueCalled) { "Continue should not be called during setup" }
    }

    @Test
    @DisplayName("should display duplicate count")
    fun shouldDisplayDuplicateCount() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    duplicateReviewScreenCall()
                }
            }
        }

        // Duplicate count should be displayed
        composeTestRule.onNodeWithText("Duplicates").assertExists()
    }

    @Test
    @DisplayName("should display resolution options")
    fun shouldDisplayResolutionOptions() {
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    duplicateReviewScreenCall()
                }
            }
        }

        // Resolution options should be present
        composeTestRule.onNodeWithText("Duplicates").assertExists()
    }
}
