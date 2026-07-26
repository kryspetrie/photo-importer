package org.kryspetrie.fileimport.ui.screens.scan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.strings

@Composable
fun ScanPhotoList(
    detectedPhotos: List<DetectedPhoto>,
    onRemovePhoto: (Int) -> Unit,
    onEditPhoto: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    Card(modifier = modifier.fillMaxWidth().padding(8.dp)) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                s.t(StringKey.SCAN_DETECTED_PHOTOS_LABEL, "count" to "${detectedPhotos.size}"),
                style = MaterialTheme.typography.titleMedium,
            )
            detectedPhotos.forEachIndexed { i, _ ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onEditPhoto(i) },
                ) {
                    Text(
                        s.t(StringKey.ACC_THUMBNAIL, "index" to "${i + 1}"),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Row {
                        Button(
                            onClick = { onRemovePhoto(i) },
                            contentPadding = PaddingValues(4.dp),
                        ) {
                            Icon(Icons.Default.Delete, s.t(StringKey.META_REMOVE), Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
