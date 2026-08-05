@file:Suppress("TooManyFunctions", "MagicNumber")

package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
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
import org.kryspetrie.fileimport.ui.wizard.state.ZoomController

@Composable
internal fun BackImageZoomControls(
    zoomController: ZoomController,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onFitToView: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    Box(modifier = modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(4.dp),
            ) {
                IconButton(onClick = onZoomOut, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ZoomOut, s.t(StringKey.ACC_ZOOM_OUT), Modifier.size(18.dp))
                }
                Text(
                    zoomController.zoomPercent(),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(48.dp),
                    textAlign = TextAlign.Center,
                )
                IconButton(onClick = onZoomIn, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ZoomIn, s.t(StringKey.ACC_ZOOM_IN), Modifier.size(18.dp))
                }
                IconButton(onClick = onFitToView, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.FitScreen,
                        s.t(StringKey.WIZARD_FIT_TO_VIEW),
                        Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

// ─── Nearby files strip ───────────────────────────────────────────────────────────────────
