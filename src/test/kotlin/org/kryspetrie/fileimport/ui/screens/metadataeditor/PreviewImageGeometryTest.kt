package org.kryspetrie.fileimport.ui.screens.metadataeditor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.ui.shared.image.PreviewImageGeometry

@DisplayName("PreviewImageGeometry")
class PreviewImageGeometryTest {

    @Test
    fun mapsTapAtCenterOfLetterboxedImage() {
        // GIVEN — 200×100 container, 100×100 image centered horizontally
        val containerWidth = 200.0
        val containerHeight = 100.0

        // WHEN
        val coords =
            PreviewImageGeometry.normalizedImageCoordinates(
                tapX = 100.0,
                tapY = 50.0,
                containerWidth = containerWidth,
                containerHeight = containerHeight,
                imageWidth = 100,
                imageHeight = 100,
            )

        // THEN
        assertThat(coords).isEqualTo(0.5 to 0.5)
    }

    @Test
    fun rejectsTapOutsideLetterbox() {
        // GIVEN — tap in left margin of letterboxed wide image
        val coords =
            PreviewImageGeometry.normalizedImageCoordinates(
                tapX = 10.0,
                tapY = 50.0,
                containerWidth = 200.0,
                containerHeight = 100.0,
                imageWidth = 100,
                imageHeight = 100,
            )

        // THEN
        assertThat(coords).isNull()
    }

    @Test
    fun fitBoundsCentersWideImage() {
        // GIVEN / WHEN
        val bounds = PreviewImageGeometry.fitBounds(200.0, 100.0, 100, 50)

        // THEN
        assertThat(bounds.displayWidth).isEqualTo(200.0)
        assertThat(bounds.displayHeight).isEqualTo(100.0)
        assertThat(bounds.offsetX).isEqualTo(0.0)
        assertThat(bounds.offsetY).isEqualTo(0.0)
    }

    @Test
    fun mapsBottomRightCorner() {
        // GIVEN
        val bounds = PreviewImageGeometry.fitBounds(200.0, 100.0, 100, 100)

        // WHEN
        val coords =
            PreviewImageGeometry.normalizedImageCoordinates(
                tapX = bounds.offsetX + bounds.displayWidth,
                tapY = bounds.offsetY + bounds.displayHeight,
                containerWidth = 200.0,
                containerHeight = 100.0,
                imageWidth = 100,
                imageHeight = 100,
            )

        // THEN
        assertThat(coords?.first).isEqualTo(1.0)
        assertThat(coords?.second).isEqualTo(1.0)
    }
}
