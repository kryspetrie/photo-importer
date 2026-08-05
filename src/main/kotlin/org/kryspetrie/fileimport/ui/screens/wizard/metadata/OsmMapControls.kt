package org.kryspetrie.fileimport.ui.screens.wizard.metadata

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun OsmMapControls(
    mapStyle: MapStyle,
    zoom: Double,
    onToggleStyle: () -> Unit,
    onZoomRequested: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        FloatingActionButton(
            onClick = onToggleStyle,
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.surface,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
        ) {
            Text(if (mapStyle == MapStyle.STREET) "🛰" else "🗺", fontSize = 14.sp)
        }

        Spacer(Modifier.size(6.dp))

        FloatingActionButton(
            onClick = {
                onZoomRequested((zoom + 1.0).coerceIn(TileLoader.MIN_ZOOM, TileLoader.MAX_ZOOM))
            },
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.surface,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
        ) {
            Text("+", fontSize = 18.sp)
        }

        Spacer(Modifier.size(4.dp))

        FloatingActionButton(
            onClick = {
                onZoomRequested((zoom - 1.0).coerceIn(TileLoader.MIN_ZOOM, TileLoader.MAX_ZOOM))
            },
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.surface,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
        ) {
            Text("−", fontSize = 18.sp)
        }
    }
}
