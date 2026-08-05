package org.kryspetrie.fileimport.ui.screens.wizard.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.strings

/** Rotation controls section. */
@Composable
internal fun RotationSection(
    rotationDegrees: Int,
    onRotateCW: () -> Unit,
    onRotateCCW: () -> Unit,
    onRotate180: () -> Unit,
) {
    val s = strings()
    Surface(
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(s.t(StringKey.FIELD_ROTATION), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRotateCCW, modifier = Modifier.size(24.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.RotateLeft,
                    s.t(StringKey.ACC_ROTATE_CCW),
                    Modifier.size(16.dp),
                )
            }
            IconButton(onClick = onRotate180, modifier = Modifier.size(24.dp)) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = s.t(StringKey.FIELD_ROTATE_180),
                    modifier = Modifier.size(16.dp),
                )
            }
            IconButton(onClick = onRotateCW, modifier = Modifier.size(24.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.RotateRight,
                    s.t(StringKey.ACC_ROTATE_CW),
                    Modifier.size(16.dp),
                )
            }
            Text(
                s.t(StringKey.META_ROTATION_DEGREES, "degrees" to "$rotationDegrees"),
                style = MaterialTheme.typography.labelSmall,
                color =
                    if (rotationDegrees != 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(36.dp),
                textAlign = TextAlign.End,
            )
        }
    }
}
