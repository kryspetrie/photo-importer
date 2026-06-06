package org.kryspetrie.fileimport.ui.screens.wizard.metadata

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.infrastructure.wizard.OverrideState

/**
 * Compact override indicator button for EXIF metadata fields.
 *
 * Two states:
 * - **KEEP_SOURCE / OVERRIDE** (default): Shown as a faint eye-off icon.
 *   Tooltip: "Keep source EXIF value — click to remove from output".
 *   Clicking toggles to NULL_OUT, which removes the field from output.
 * - **NULL_OUT**: Shown as a prominent eye-off icon with error tint.
 *   Tooltip: "Field removed from output — click to restore source".
 *   Clicking toggles back to KEEP_SOURCE.
 *
 * The caller should auto-transition from KEEP_SOURCE to OVERRIDE when the user types a value.
 *
 * @param state Current override state
 * @param onStateChange Callback when user clicks to change state
 * @param modifier Optional modifier
 * @param enabled Whether the indicator is interactive
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun OverrideIndicator(
    state: OverrideState,
    onStateChange: (OverrideState) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val tooltipState = rememberTooltipState()
    val scope = rememberCoroutineScope()

    val isNulledOut = state == OverrideState.NULL_OUT

    TooltipBox(
        positionProvider =
            TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            Surface(
                tonalElevation = 4.dp,
                shape = MaterialTheme.shapes.extraSmall,
                shadowElevation = 4.dp,
            ) {
                Text(
                    if (isNulledOut)
                        "Field removed from output — click to restore source"
                    else
                        "Keep source EXIF value — click to remove from output",
                    modifier = Modifier,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        },
        state = tooltipState,
    ) {
        IconButton(
            onClick = {
                val next =
                    if (isNulledOut) OverrideState.KEEP_SOURCE else OverrideState.NULL_OUT
                onStateChange(next)
            },
            modifier =
                modifier.size(24.dp).semantics {
                    contentDescription =
                        if (isNulledOut) "Restore source EXIF" else "Remove field from output"
                },
            enabled = enabled,
        ) {
            if (isNulledOut) {
                Icon(
                    Icons.Default.VisibilityOff,
                    contentDescription = "Field nullified",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp),
                )
            } else {
                // Keep/OVERRIDE state — faint icon indicating "preserving source data"
                Icon(
                    Icons.Default.VisibilityOff,
                    contentDescription = "Keep source",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/**
 * Returns a background tint color for a field row based on its override state.
 * - KEEP_SOURCE / OVERRIDE: transparent (no tint)
 * - NULL_OUT: faint error tint to indicate the field is explicitly removed
 */
@Composable
fun overrideRowTintColor(state: OverrideState): Color =
    when (state) {
        OverrideState.KEEP_SOURCE -> Color.Transparent
        OverrideState.OVERRIDE -> Color.Transparent
        OverrideState.NULL_OUT -> MaterialTheme.colorScheme.error.copy(alpha = 0.06f)
    }