package org.kryspetrie.fileimport.ui.shared.face

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.Test
import org.kryspetrie.fileimport.domain.model.FaceRegion
import org.kryspetrie.fileimport.domain.model.RegionType
import org.kryspetrie.fileimport.ui.i18n.TestStringsProvider
import org.kryspetrie.fileimport.ui.theme.ImporterTheme

@DisplayName("FaceRegionPreviewOverlay")
@Tag("UiComponentTest")
class FaceRegionPreviewOverlayComponentTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun rendersWithoutCrashWhenRegionsPresent() {
        // GIVEN
        val regions =
            listOf(
                FaceRegion(
                    name = "Alice",
                    type = RegionType.FACE.mwgRsValue,
                    x = 0.5,
                    y = 0.5,
                    w = 0.2,
                    h = 0.2,
                )
            )

        // WHEN / THEN — no assertion beyond successful composition
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    MaterialTheme {
                        Box(Modifier.size(200.dp)) {
                            FaceRegionPreviewOverlay(
                                faceRegions = regions,
                                containerWidthPx = 200,
                                containerHeightPx = 200,
                                imageWidth = 100,
                                imageHeight = 100,
                            )
                        }
                    }
                }
            }
        }
    }
}
