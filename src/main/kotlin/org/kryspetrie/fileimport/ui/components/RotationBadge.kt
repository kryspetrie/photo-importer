package org.kryspetrie.fileimport.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A small badge indicating rotation was applied to a photo.
 *
 * Shows the rotation degrees (e.g. "90°") and optionally an "auto" icon when rotation was
 * auto-detected by ML orientation detection.
 *
 * @param rotationDegrees The rotation in degrees (0, 90, 180, 270).
 * @param isAutoDetected Whether this rotation was auto-detected (ML) vs manually set.
 * @param modifier Optional modifier.
 */
@Composable
fun RotationBadge(
    rotationDegrees: Int,
    isAutoDetected: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (rotationDegrees == 0) return

    val backgroundColor =
        if (isAutoDetected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.tertiaryContainer
        }
    val textColor =
        if (isAutoDetected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onTertiaryContainer
        }

    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(4.dp))
                .background(backgroundColor)
                .padding(horizontal = 4.dp, vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isAutoDetected) {
            Icon(
                Icons.Default.AutoFixHigh,
                contentDescription = "Auto-detected",
                modifier = Modifier.size(10.dp),
                tint = textColor,
            )
        }
        Text(
            "${rotationDegrees}°",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
            ),
            color = textColor,
        )
    }
}

/**
 * A compact badge indicating that auto-orientation detection is enabled for an import.
 *
 * Displayed in import screens to give users a visual indication that photos will be
 * automatically rotated on import.
 */
@Composable
fun AutoOrientIndicator(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                .padding(horizontal = 6.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.AutoFixHigh,
            contentDescription = "Auto-orient enabled",
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            "Auto-orient",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}