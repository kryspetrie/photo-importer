package org.kryspetrie.fileimport.ui.wizard.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.kryspetrie.fileimport.domain.model.geometry.BoundingBoxCorners
import org.kryspetrie.fileimport.domain.model.geometry.Point

/**
 * Sub-state holding zoom/pan state for the wizard canvas view. Wraps a [ZoomController] in a
 * [StateFlow] and provides mutation methods.
 */
class ZoomState {

    internal val _zoomController = MutableStateFlow(ZoomController())
    val zoomController: StateFlow<ZoomController> = _zoomController.asStateFlow()

    /** Updates the zoom controller to fit the given image dimensions. */
    fun fitToImage(
        imageWidth: Double,
        imageHeight: Double,
        viewportWidth: Double = 800.0,
        viewportHeight: Double = 600.0,
    ) {
        _zoomController.value =
            ZoomController.fit(imageWidth, imageHeight, viewportWidth, viewportHeight)
    }

    /** Zooms in the view. */
    fun zoomIn(cursorX: Double? = null, cursorY: Double? = null) {
        _zoomController.value = _zoomController.value.zoomIn(cursorX, cursorY)
    }

    /** Zooms out the view. */
    fun zoomOut(cursorX: Double? = null, cursorY: Double? = null) {
        _zoomController.value = _zoomController.value.zoomOut(cursorX, cursorY)
    }

    /** Pans the view by the given delta. */
    fun pan(deltaX: Double, deltaY: Double) {
        _zoomController.value = _zoomController.value.pan(deltaX, deltaY)
    }

    /** Fits the view to the given bounding box corners. */
    fun fitToBox(
        corners: BoundingBoxCorners,
        viewportWidth: Double = 800.0,
        viewportHeight: Double = 600.0,
    ) {
        _zoomController.value =
            _zoomController.value.fitToBox(corners, viewportWidth, viewportHeight)
    }

    /** Resets zoom to default (identity). */
    fun reset() {
        _zoomController.value = ZoomController()
    }

    /** Converts screen coordinates to image coordinates. */
    fun screenToImage(screenX: Double, screenY: Double): Point {
        return _zoomController.value.screenToImage(screenX, screenY)
    }

    /** Converts image coordinates to screen coordinates. */
    fun imageToScreen(imageX: Double, imageY: Double): Point {
        return _zoomController.value.imageToScreen(imageX, imageY)
    }
}
