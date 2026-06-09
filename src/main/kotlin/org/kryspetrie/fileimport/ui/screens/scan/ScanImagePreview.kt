package org.kryspetrie.fileimport.ui.screens.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage
import java.io.File
import org.kryspetrie.fileimport.domain.model.DetectedPhoto

@Composable
fun ScanImagePreview(
    currentImage: BufferedImage,
    currentFile: File?,
    detectedPhotos: List<DetectedPhoto>,
    imagePreviewBounds: Rect,
    onBoundsChanged: (Rect) -> Unit,
    draggedCornerPhotoIndex: Int?,
    draggedCornerName: String?,
    onCornerDrag: (Int, String, Float, Float) -> Unit,
    onCornerClick: (Int, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .aspectRatio(currentImage.width.toFloat() / currentImage.height.toFloat())
                        .onGloballyPositioned { layoutCoordinates ->
                            onBoundsChanged(
                                Rect(
                                    0f,
                                    0f,
                                    layoutCoordinates.size.width.toFloat(),
                                    layoutCoordinates.size.height.toFloat(),
                                )
                            )
                        }
                        .pointerInput(detectedPhotos) {
                            detectDragGestures { change, _ ->
                                if (draggedCornerPhotoIndex != null && draggedCornerName != null) {
                                    onCornerDrag(
                                        draggedCornerPhotoIndex,
                                        draggedCornerName,
                                        change.position.x,
                                        change.position.y,
                                    )
                                }
                            }
                        }
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Gray)
                        .clickable { /* Open for detailed editing */ }
            ) {
                if (currentImage != null) {
                    Text(
                        "Image: ${currentFile?.name} (${currentImage.width}x${currentImage.height})",
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                detectedPhotos.forEachIndexed { i, photo ->
                    DrawCorners(
                        photo.topLeft.x,
                        photo.topLeft.y,
                        photo.topRight.x,
                        photo.topRight.y,
                        photo.bottomLeft.x,
                        photo.bottomLeft.y,
                        photo.bottomRight.x,
                        photo.bottomRight.y,
                        imagePreviewBounds.width,
                        imagePreviewBounds.height,
                        draggedCornerPhotoIndex == i,
                        { onCornerClick(i, "topLeft") },
                        { onCornerClick(i, "topRight") },
                        { onCornerClick(i, "bottomLeft") },
                        { onCornerClick(i, "bottomRight") },
                    )
                }
            }
        }
    }
}

/** Draw corner indicators on the photo. */
@Composable
fun DrawCorners(
    tlX: Float,
    tlY: Float,
    trX: Float,
    trY: Float,
    blX: Float,
    blY: Float,
    brX: Float,
    brY: Float,
    width: Float,
    height: Float,
    isDragging: Boolean,
    onTLClick: () -> Unit,
    onTRClick: () -> Unit,
    onBLClick: () -> Unit,
    onBRClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier.size(12.dp)
                .offset { IntOffset(x = tlX.toInt(), y = tlY.toInt()) }
                .background(Color.Red)
                .clickable { onTLClick() }
    )
    Box(
        modifier =
            Modifier.size(12.dp)
                .offset { IntOffset(x = trX.toInt(), y = trY.toInt()) }
                .background(Color.Red)
                .clickable { onTRClick() }
    )
    Box(
        modifier =
            Modifier.size(12.dp)
                .offset { IntOffset(x = blX.toInt(), y = blY.toInt()) }
                .background(Color.Red)
                .clickable { onBLClick() }
    )
    Box(
        modifier =
            Modifier.size(12.dp)
                .offset { IntOffset(x = brX.toInt(), y = brY.toInt()) }
                .background(Color.Red)
                .clickable { onBRClick() }
    )
}
