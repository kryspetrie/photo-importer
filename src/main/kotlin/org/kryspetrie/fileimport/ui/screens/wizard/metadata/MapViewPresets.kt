@file:Suppress("TooManyFunctions", "MagicNumber")
@file:OptIn(kotlinx.coroutines.FlowPreview::class)

package org.kryspetrie.fileimport.ui.screens.wizard.metadata

// ──────────────────────────────────────────────────────────────────────────────
// Map style — street vs satellite
// ──────────────────────────────────────────────────────────────────────────────

/** Available map tile styles. */
enum class MapStyle {
    STREET,
    SATELLITE,
}

// ──────────────────────────────────────────────────────────────────────────────
