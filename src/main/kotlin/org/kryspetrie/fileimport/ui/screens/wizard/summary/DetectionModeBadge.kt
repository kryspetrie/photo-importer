package org.kryspetrie.fileimport.ui.screens.wizard.summary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.kryspetrie.fileimport.domain.model.DetectionMode

/**
 * Small badge chip showing the detection mode of a photo.
 *
 * Displayed on the overview canvas and summary cards so users can see at a glance how each photo
 * was detected (CV, YOLO bounding box, YOLO pose keypoints, or hybrid).
 */
@Composable
fun DetectionModeBadge(mode: DetectionMode, modifier: Modifier = Modifier) {
    val backgroundColor =
        when (mode) {
            DetectionMode.COMPUTER_VISION -> Color(0xFF4CAF50) // Green
            DetectionMode.BOUNDING_BOX -> Color(0xFF2196F3) // Blue
            DetectionMode.PERSPECTIVE_CORRECTION -> Color(0xFFFF9800) // Orange
            DetectionMode.HYBRID -> Color(0xFF9C27B0) // Purple
        }

    Text(
        text = mode.displayName,
        color = Color.White,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        modifier =
            modifier
                .background(backgroundColor, RoundedCornerShape(3.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}
