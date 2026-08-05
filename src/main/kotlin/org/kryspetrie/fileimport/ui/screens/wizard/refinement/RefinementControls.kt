package org.kryspetrie.fileimport.ui.screens.wizard.refinement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.geometry.Corner
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.strings

@Composable
internal fun RefinementControls(
    currentIndex: Int,
    totalCount: Int,
    selectedCorner: Corner?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDeselect: () -> Unit,
    refocus: () -> Unit = {},
) {
    val s = strings()
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(36.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Navigation: ← Image N of M →
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        onPrevious()
                        refocus()
                    },
                    enabled = totalCount > 1,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        s.t(StringKey.WIZARD_PREVIOUS_PHOTO),
                        Modifier.size(18.dp),
                    )
                }
                Text(
                    s.t(
                        StringKey.SCAN_PHOTO_LABEL,
                        "index" to "${currentIndex + 1}",
                        "total" to "$totalCount",
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(
                    onClick = {
                        onNext()
                        refocus()
                    },
                    enabled = totalCount > 1,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        s.t(StringKey.WIZARD_NEXT_PHOTO_NAV),
                        Modifier.size(18.dp),
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selectedCorner != null) {
                    Text(
                        s.t(StringKey.WIZARD_SELECTED, "corner" to selectedCorner.name),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    OutlinedButton(
                        onClick = {
                            onDeselect()
                            refocus()
                        },
                        modifier = Modifier.height(32.dp),
                    ) {
                        Text(
                            s.t(StringKey.WIZARD_DESELECT),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                } else {
                    Text(
                        s.t(StringKey.WIZARD_CLICK_CORNER),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
