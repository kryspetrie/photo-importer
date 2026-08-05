@file:Suppress("TooManyFunctions", "MagicNumber")

package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.strings

@Suppress("InjectDispatcher")
@Composable
internal fun NearbyFilesStrip(
    files: List<File>,
    selectedFile: File?,
    onSelect: (File) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    val selectedIndex = files.indexOf(selectedFile).coerceAtLeast(0)
    val windowSize = 20
    val startIdx = (selectedIndex - windowSize / 2).coerceIn(0, maxOf(0, files.size - windowSize))
    val endIdx = minOf(startIdx + windowSize, files.size)
    val nearbyFiles = files.subList(startIdx, endIdx)
    val listState =
        rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, selectedIndex - startIdx - 2))

    Column(modifier = modifier.fillMaxWidth()) {
        Text(s.t(StringKey.WIZARD_NEARBY_FILES), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(nearbyFiles) { _, file ->
                val isSelected = file.absolutePath == selectedFile?.absolutePath
                val thumbnail = remember(file.absolutePath) { mutableStateOf<BufferedImage?>(null) }
                LaunchedEffect(file.absolutePath) {
                    withContext(Dispatchers.IO) {
                        try {
                            val img = ImageIO.read(file)
                            if (img != null) {
                                val maxDim = 60
                                val scale =
                                    minOf(
                                        maxDim.toDouble() / img.width,
                                        maxDim.toDouble() / img.height,
                                    )
                                val w = (img.width * scale).toInt().coerceAtLeast(1)
                                val h = (img.height * scale).toInt().coerceAtLeast(1)
                                val scaled =
                                    java.awt.image.BufferedImage(
                                        w,
                                        h,
                                        java.awt.image.BufferedImage.TYPE_INT_RGB,
                                    )
                                val g = scaled.createGraphics()
                                g.drawImage(
                                    img.getScaledInstance(w, h, java.awt.Image.SCALE_FAST),
                                    0,
                                    0,
                                    null,
                                )
                                g.dispose()
                                thumbnail.value = scaled
                            }
                        } catch (_: Exception) {
                            thumbnail.value = null
                        }
                    }
                }
                Card(
                    modifier = Modifier.width(64.dp).height(80.dp).clickable { onSelect(file) },
                    shape = RoundedCornerShape(4.dp),
                    border =
                        if (isSelected)
                            androidx.compose.foundation.BorderStroke(
                                2.dp,
                                MaterialTheme.colorScheme.primary,
                            )
                        else
                            androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                            ),
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface
                        ),
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        val img = thumbnail.value
                        if (img != null) {
                            Image(
                                bitmap = img.toComposeImageBitmap(),
                                contentDescription = file.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                        } else {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                        }
                    }
                }
            }
        }
    }
}
