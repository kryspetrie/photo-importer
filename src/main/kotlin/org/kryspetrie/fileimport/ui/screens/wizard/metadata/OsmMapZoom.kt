package org.kryspetrie.fileimport.ui.screens.wizard.metadata

/** Adjust center for zoom toward a pointer position (sets center + zoom immediately). */
internal fun zoomAtPointer(
    camera: MapCameraState,
    sourceTileSize: Int,
    pointerX: Float,
    pointerY: Float,
    oldZoom: Double,
    newZoom: Double,
    viewW: Float,
    viewH: Float,
    onZoomChanged: (Double) -> Unit,
) {
    val (centerPx, centerPy) =
        MapTileRenderer.latLonToPixelOffset(
            camera.centerLat,
            camera.centerLon,
            oldZoom,
            sourceTileSize,
        )
    val pointerOffX = pointerX - viewW / 2f
    val pointerOffY = pointerY - viewH / 2f
    val pointerWorldPx = centerPx + pointerOffX
    val pointerWorldPy = centerPy + pointerOffY
    val (pointerLat, pointerLon) =
        MapTileRenderer.pixelOffsetToLatLon(pointerWorldPx, pointerWorldPy, oldZoom, sourceTileSize)

    val (newPointerPx, newPointerPy) =
        MapTileRenderer.latLonToPixelOffset(pointerLat, pointerLon, newZoom, sourceTileSize)

    val newOffX = pointerX - viewW / 2f
    val newOffY = pointerY - viewH / 2f
    val newCenterPx = newPointerPx - newOffX
    val newCenterPy = newPointerPy - newOffY
    val (newLat, newLon) =
        MapTileRenderer.pixelOffsetToLatLon(newCenterPx, newCenterPy, newZoom, sourceTileSize)
    camera.centerLat = MapTileRenderer.clampLat(newLat)
    camera.centerLon = MapTileRenderer.clampLon(newLon)
    camera.zoom = newZoom
    onZoomChanged(newZoom)
}

/**
 * Adjust center for zoom toward a pointer position without setting zoom (for animated pointer
 * zoom).
 */
internal fun adjustCenterForPointerZoom(
    camera: MapCameraState,
    sourceTileSize: Int,
    pointerX: Float,
    pointerY: Float,
    oldZoom: Double,
    newZoom: Double,
    viewW: Float,
    viewH: Float,
) {
    val (centerPx, centerPy) =
        MapTileRenderer.latLonToPixelOffset(
            camera.centerLat,
            camera.centerLon,
            oldZoom,
            sourceTileSize,
        )
    val pointerOffX = pointerX - viewW / 2f
    val pointerOffY = pointerY - viewH / 2f
    val pointerWorldPx = centerPx + pointerOffX
    val pointerWorldPy = centerPy + pointerOffY
    val (pointerLat, pointerLon) =
        MapTileRenderer.pixelOffsetToLatLon(pointerWorldPx, pointerWorldPy, oldZoom, sourceTileSize)
    val (newPointerPx, newPointerPy) =
        MapTileRenderer.latLonToPixelOffset(pointerLat, pointerLon, newZoom, sourceTileSize)
    val newOffX = pointerX - viewW / 2f
    val newOffY = pointerY - viewH / 2f
    val newCenterPx = newPointerPx - newOffX
    val newCenterPy = newPointerPy - newOffY
    val (newLat, newLon) =
        MapTileRenderer.pixelOffsetToLatLon(newCenterPx, newCenterPy, newZoom, sourceTileSize)
    camera.centerLat = MapTileRenderer.clampLat(newLat)
    camera.centerLon = MapTileRenderer.clampLon(newLon)
}
