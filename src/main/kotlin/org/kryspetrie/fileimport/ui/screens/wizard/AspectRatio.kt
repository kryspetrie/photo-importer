package org.kryspetrie.fileimport.ui.screens.wizard

enum class AspectRatio(val displayName: String, val value: Double) {
    CURRENT("Current", 0.0),
    SQUARE("1:1", 1.0),
    PORTRAIT_2_3("2:3", 2.0 / 3.0),
    LANDSCAPE_3_2("3:2", 3.0 / 2.0),
    PORTRAIT_3_4("3:4", 3.0 / 4.0),
    LANDSCAPE_4_3("4:3", 4.0 / 3.0),
    PORTRAIT_4_5("4:5", 4.0 / 5.0),
    LANDSCAPE_5_4("5:4", 5.0 / 4.0),
    WIDE_16_9("16:9", 16.0 / 9.0),
    WIDE_3_1("3:1 (Panorama)", 3.0),
}
