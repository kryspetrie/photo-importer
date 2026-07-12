@file:Suppress("MagicNumber")

package org.kryspetrie.fileimport.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * A scrollable container with a visible, chunky scrollbar.
 *
 * Shows a rounded track and proportionally-sized thumb that indicates position and visible
 * proportion. Click on the track to jump, drag the thumb to fast-scroll.
 *
 * @param modifier Layout modifier
 * @param scrollState Scroll state (use [rememberScrollState] for basic usage)
 * @param scrollbarWidth Width of the scrollbar (default 10.dp)
 * @param trackPadding Padding between track edge and content (default 4.dp)
 * @param content The scrollable content
 */
@Composable
fun ChunkyScrollbar(
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    scrollbarWidth: Dp = 10.dp,
    trackPadding: Dp = 4.dp,
    content: @Composable () -> Unit,
) {
    var containerHeight by remember { mutableStateOf(0f) }
    var contentHeight by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var dragStartY by remember { mutableStateOf(0f) }
    var dragStartScroll by remember { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    val needsScrollbar = contentHeight > containerHeight && containerHeight > 0f
    val maxScroll = if (needsScrollbar) contentHeight - containerHeight else 0f
    val scrollFraction = if (maxScroll > 0f) scrollState.value.toFloat() / maxScroll else 0f
    val thumbHeight =
        if (needsScrollbar) {
            (containerHeight / contentHeight * containerHeight).coerceAtLeast(40f)
        } else 0f
    val thumbOffset = scrollFraction * (containerHeight - thumbHeight)

    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val thumbColor =
        MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDragging) 0.5f else 0.25f)

    Box(modifier = modifier) {
        // Scrollable content with padding for scrollbar
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .padding(end = scrollbarWidth + trackPadding * 2)
                    .verticalScroll(scrollState)
                    .onGloballyPositioned { coords -> contentHeight = coords.size.height.toFloat() }
        ) {
            content()
        }

        // Container height measurement
        Box(
            modifier =
                Modifier.fillMaxSize().onGloballyPositioned { coords ->
                    containerHeight = coords.size.height.toFloat()
                }
        )

        // Scrollbar overlay
        if (needsScrollbar) {
            Box(
                modifier =
                    Modifier.align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(scrollbarWidth + trackPadding * 2)
                        .padding(vertical = 4.dp, horizontal = trackPadding)
                        .drawBehind {
                            // Track background
                            drawRoundRect(
                                color = trackColor,
                                topLeft = Offset(0f, 0f),
                                size = Size(scrollbarWidth.toPx(), size.height),
                                cornerRadius = CornerRadius(scrollbarWidth.toPx() / 2),
                            )
                            // Thumb
                            drawRoundRect(
                                color = thumbColor,
                                topLeft = Offset(0f, thumbOffset),
                                size = Size(scrollbarWidth.toPx(), thumbHeight),
                                cornerRadius = CornerRadius(scrollbarWidth.toPx() / 2),
                            )
                        }
                        .pointerInput(contentHeight, containerHeight) {
                            detectTapGestures { offset ->
                                // Tap on track — jump to position
                                val y = offset.y
                                val targetFraction =
                                    ((y - thumbHeight / 2) / (containerHeight - thumbHeight))
                                        .coerceIn(0f, 1f)
                                // scrollTo() uses MutatorMutex internally. During rapid calls,
                                // one scroll can cancel another via CancellationException.
                                coroutineScope.launch {
                                    try {
                                        scrollState.scrollTo((targetFraction * maxScroll).toInt())
                                    } catch (_: CancellationException) {
                                        // Scroll cancelled by a new scroll — safe to ignore
                                    }
                                }
                            }
                        }
                        .pointerInput(isDragging, contentHeight, containerHeight) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val y = offset.y
                                    if (y in thumbOffset..(thumbOffset + thumbHeight)) {
                                        isDragging = true
                                        dragStartY = y
                                        dragStartScroll = scrollState.value
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    if (!isDragging) return@detectDragGestures
                                    change.consume()
                                    val totalDragY = (change.position.y - dragStartY)
                                    val scrollPerPx = maxScroll / (containerHeight - thumbHeight)
                                    val newScroll =
                                        (dragStartScroll + totalDragY * scrollPerPx)
                                            .toInt()
                                            .coerceIn(0, maxScroll.toInt())
                                    // scrollTo() uses MutatorMutex internally. During rapid drag
                                    // calls,
                                    // one scroll can cancel another via CancellationException.
                                    coroutineScope.launch {
                                        try {
                                            scrollState.scrollTo(newScroll)
                                        } catch (_: CancellationException) {
                                            // Scroll cancelled by a new scroll — safe to ignore
                                        }
                                    }
                                },
                                onDragEnd = { isDragging = false },
                                onDragCancel = { isDragging = false },
                            )
                        }
            )
        }
    }
}
