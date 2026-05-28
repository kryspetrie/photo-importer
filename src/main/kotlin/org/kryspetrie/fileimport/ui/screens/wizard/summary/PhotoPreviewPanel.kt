package org.kryspetrie.fileimport.ui.screens.wizard.summary

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage

/** Panel displaying a cropped preview of the selected photo with a hint overlay. */
@Composable
fun PhotoPreviewPanel(previewImage: BufferedImage?, modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                .background(Color.DarkGray)
    ) {
        if (previewImage != null) {
            Image(
                bitmap = previewImage.toComposeImageBitmap(),
                contentDescription = "Photo preview",
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentScale = ContentScale.Fit,
            )
            PreviewHintOverlay()
        } else {
            EmptyPreviewPlaceholder()
        }
    }
}

@Composable
private fun BoxScope.PreviewHintOverlay() {
    Surface(
        modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
        shape = RoundedCornerShape(4.dp),
        color = Color.Black.copy(alpha = 0.6f),
    ) {
        Text(
            "Click on photo in list to preview",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}

@Composable
private fun EmptyPreviewPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "Select a photo to preview",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.7f),
        )
    }
}
