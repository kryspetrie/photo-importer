package org.kryspetrie.fileimport.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.ui.i18n.TestStringsProvider

@Tag("UiComponentTest")
@DisplayName("Loading Components")
class LoadingComponentTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Nested
    @DisplayName("CubeGridLoadingIndicator")
    inner class CubeGridLoadingIndicatorTest {

        @Test
        @DisplayName("renders without crashing with default parameters")
        fun rendersWithDefaults() {
            composeTestRule.setContent { MaterialTheme { CubeGridLoadingIndicator() } }

            composeTestRule.onRoot().assertIsDisplayed()
        }

        @Test
        @DisplayName("renders with custom modifier")
        fun rendersWithCustomModifier() {
            composeTestRule.setContent {
                MaterialTheme { CubeGridLoadingIndicator(modifier = Modifier.size(100.dp)) }
            }

            composeTestRule.onRoot().assertIsDisplayed()
        }

        @Test
        @DisplayName("renders with custom color")
        fun rendersWithCustomColor() {
            composeTestRule.setContent {
                MaterialTheme { CubeGridLoadingIndicator(color = Color.Red) }
            }

            composeTestRule.onRoot().assertIsDisplayed()
        }

        @Test
        @DisplayName("renders with custom animation duration")
        fun rendersWithCustomAnimationDuration() {
            composeTestRule.setContent {
                MaterialTheme { CubeGridLoadingIndicator(animationDuration = 800) }
            }

            composeTestRule.onRoot().assertIsDisplayed()
        }

        @Test
        @DisplayName("renders with all custom parameters")
        fun rendersWithAllCustomParameters() {
            composeTestRule.setContent {
                MaterialTheme {
                    CubeGridLoadingIndicator(
                        modifier = Modifier.size(80.dp),
                        color = Color.Green,
                        animationDuration = 600,
                    )
                }
            }

            composeTestRule.onRoot().assertIsDisplayed()
        }
    }

    @Nested
    @DisplayName("LoadingIndicator")
    inner class LoadingIndicatorTest {

        @Test
        @DisplayName("renders without crashing with default parameters")
        fun rendersWithDefaults() {
            composeTestRule.setContent { MaterialTheme { LoadingIndicator() } }

            composeTestRule.onRoot().assertIsDisplayed()
        }

        @Test
        @DisplayName("renders with custom modifier")
        fun rendersWithCustomModifier() {
            composeTestRule.setContent {
                MaterialTheme { LoadingIndicator(modifier = Modifier.size(60.dp)) }
            }

            composeTestRule.onRoot().assertIsDisplayed()
        }

        @Test
        @DisplayName("renders with custom color")
        fun rendersWithCustomColor() {
            composeTestRule.setContent { MaterialTheme { LoadingIndicator(color = Color.Blue) } }

            composeTestRule.onRoot().assertIsDisplayed()
        }

        @Test
        @DisplayName("renders with custom size of 60.dp")
        fun rendersWithCustomSize() {
            composeTestRule.setContent { MaterialTheme { LoadingIndicator(size = 60.dp) } }

            composeTestRule.onRoot().assertIsDisplayed()
        }

        @Test
        @DisplayName("renders with all custom parameters")
        fun rendersWithAllCustomParameters() {
            composeTestRule.setContent {
                MaterialTheme {
                    LoadingIndicator(
                        modifier = Modifier.size(60.dp),
                        color = Color.Magenta,
                        size = 60.dp,
                    )
                }
            }

            composeTestRule.onRoot().assertIsDisplayed()
        }
    }

    @Nested
    @DisplayName("LoadingOverlay")
    inner class LoadingOverlayTest {

        @Test
        @DisplayName("displays content when not loading")
        fun displaysContentWhenNotLoading() {
            composeTestRule.setContent {
                MaterialTheme {
                    LoadingOverlay(isLoading = false, message = "Processing...") {
                        TestContent(text = "Main Content")
                    }
                }
            }

            composeTestRule.onNodeWithText("Main Content").assertIsDisplayed()
        }

        @Test
        @DisplayName("displays loading message when loading")
        fun displaysMessageWhenLoading() {
            composeTestRule.setContent {
                MaterialTheme {
                    LoadingOverlay(isLoading = true, message = "Processing...") {
                        TestContent(text = "Main Content")
                    }
                }
            }

            composeTestRule.onNodeWithText("Processing...").assertIsDisplayed()
        }

        @Test
        @DisplayName("displays default message when loading with default message")
        fun displaysDefaultMessageWhenLoading() {
            composeTestRule.setContent {
                MaterialTheme {
                    TestStringsProvider {
                        LoadingOverlay(isLoading = true) { TestContent(text = "Main Content") }
                    }
                }
            }

            composeTestRule.onNodeWithText("Processing...").assertIsDisplayed()
        }

        @Test
        @DisplayName("displays custom message text when loading")
        fun displaysCustomMessageWhenLoading() {
            composeTestRule.setContent {
                MaterialTheme {
                    LoadingOverlay(isLoading = true, message = "Loading data...") {
                        TestContent(text = "Main Content")
                    }
                }
            }

            composeTestRule.onNodeWithText("Loading data...").assertIsDisplayed()
        }

        @Test
        @DisplayName("content remains in composition when overlay is shown")
        fun contentRemainsInCompositionWhenOverlayShown() {
            composeTestRule.setContent {
                MaterialTheme {
                    LoadingOverlay(isLoading = true, message = "Please wait") {
                        TestContent(text = "Background Content")
                    }
                }
            }

            // The overlay message should be displayed
            composeTestRule.onNodeWithText("Please wait").assertIsDisplayed()
            // The content behind the overlay should still exist in the composition
            composeTestRule.onNodeWithText("Background Content").assertIsDisplayed()
        }
    }

    @Nested
    @DisplayName("LoadingProgressIndicator")
    inner class LoadingProgressIndicatorTest {

        @Test
        @DisplayName("renders and displays message text")
        fun rendersAndDisplaysMessage() {
            composeTestRule.setContent {
                MaterialTheme {
                    LoadingProgressIndicator(progress = 0.5f, message = "Loading files...")
                }
            }

            composeTestRule.onNodeWithText("Loading files...").assertIsDisplayed()
        }

        @Test
        @DisplayName("renders with progress at zero")
        fun rendersWithProgressAtZero() {
            composeTestRule.setContent {
                MaterialTheme { LoadingProgressIndicator(progress = 0f, message = "Starting...") }
            }

            composeTestRule.onNodeWithText("Starting...").assertIsDisplayed()
        }

        @Test
        @DisplayName("renders with progress at halfway")
        fun rendersWithProgressAtHalfway() {
            composeTestRule.setContent {
                MaterialTheme {
                    LoadingProgressIndicator(progress = 0.5f, message = "Halfway there")
                }
            }

            composeTestRule.onNodeWithText("Halfway there").assertIsDisplayed()
        }

        @Test
        @DisplayName("renders with progress at full")
        fun rendersWithProgressAtFull() {
            composeTestRule.setContent {
                MaterialTheme { LoadingProgressIndicator(progress = 1f, message = "Almost done") }
            }

            composeTestRule.onNodeWithText("Almost done").assertIsDisplayed()
        }

        @Test
        @DisplayName("renders with custom modifier")
        fun rendersWithCustomModifier() {
            composeTestRule.setContent {
                MaterialTheme {
                    LoadingProgressIndicator(
                        progress = 0.3f,
                        message = "Progress test",
                        modifier = Modifier.size(200.dp),
                    )
                }
            }

            composeTestRule.onNodeWithText("Progress test").assertIsDisplayed()
        }
    }
}

/** Simple test composable that displays a text label. Used as content within overlay tests. */
@Composable
private fun TestContent(text: String) {
    androidx.compose.material3.Text(text = text)
}
