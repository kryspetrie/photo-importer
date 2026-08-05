package org.kryspetrie.fileimport.ui.screens.metadataeditor

import org.kryspetrie.fileimport.ui.theme.UiDensityDefaults

enum class MetadataEditorLayoutBreakpoint {
    NARROW,
    STANDARD,
    ULTRA_WIDE,
}

fun layoutBreakpointForWidth(widthDp: Float): MetadataEditorLayoutBreakpoint =
    when {
        widthDp < UiDensityDefaults.metadataEditorNarrowBreakpoint.value ->
            MetadataEditorLayoutBreakpoint.NARROW
        widthDp >= UiDensityDefaults.metadataEditorUltraWideBreakpoint.value ->
            MetadataEditorLayoutBreakpoint.ULTRA_WIDE
        else -> MetadataEditorLayoutBreakpoint.STANDARD
    }
