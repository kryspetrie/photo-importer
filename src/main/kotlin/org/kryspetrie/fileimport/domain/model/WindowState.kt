package org.kryspetrie.fileimport.domain.model

import kotlinx.serialization.Serializable

/**
 * Window state information for persistence.
 *
 * Stores window dimensions and position to restore on application restart.
 *
 * @property width Window width in pixels. Default 1200px.
 * @property height Window height in pixels. Default 800px.
 * @property x Window X position on screen. Null to let OS decide.
 * @property y Window Y position on screen. Null to let OS decide.
 * @property isMaximized Whether window was maximized when last closed.
 */
@Serializable
data class WindowState(
    val width: Int = 1200,
    val height: Int = 800,
    val x: Int? = null,
    val y: Int? = null,
    val isMaximized: Boolean = false,
)
