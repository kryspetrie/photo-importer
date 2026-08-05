@file:Suppress("MagicNumber")

package org.kryspetrie.fileimport.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A scrollable container with a visible, chunky vertical scrollbar.
 *
 * Shows a rounded track and proportionally-sized thumb that indicates position and visible
 * proportion. Click on the track to jump, drag the thumb to fast-scroll. Mouse wheel / trackpad
 * vertical scroll is handled by [verticalScroll].
 *
 * Performance notes:
 * - Scroll position is read only in the draw phase so wheel/drag does not recompose [content].
 * - Gesture detectors use stable keys; live metrics are read via [rememberUpdatedState] so drags
 *   are not cancelled mid-gesture.
 * - Drag highlight state lives in the chrome so it never invalidates the scrolled page tree.
 */
@Composable
fun ChunkyScrollbar(
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    scrollbarWidth: Dp = 10.dp,
    trackPadding: Dp = 4.dp,
    content: @Composable () -> Unit,
) {
    var containerHeight by remember { mutableFloatStateOf(0f) }

    // maxValue changes when content size / viewport change — not every scroll pixel.
    val maxScroll = scrollState.maxValue.toFloat()
    val needsScrollbar = maxScroll > 0f && containerHeight > 0f

    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val thumbColorIdle = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    val thumbColorDragging = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)

    Box(modifier = modifier) {
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .padding(end = scrollbarWidth + trackPadding * 2)
                    .verticalScroll(scrollState)
        ) {
            content()
        }

        Box(
            modifier =
                Modifier.fillMaxSize().onSizeChanged { size ->
                    val h = size.height.toFloat()
                    if (h != containerHeight) containerHeight = h
                }
        )

        if (needsScrollbar) {
            VerticalScrollbarChrome(
                modifier = Modifier.align(Alignment.CenterEnd),
                scrollState = scrollState,
                maxScroll = maxScroll,
                containerHeight = containerHeight,
                scrollbarWidth = scrollbarWidth,
                trackPadding = trackPadding,
                trackColor = trackColor,
                thumbColorIdle = thumbColorIdle,
                thumbColorDragging = thumbColorDragging,
            )
        }
    }
}

@Composable
private fun VerticalScrollbarChrome(
    modifier: Modifier = Modifier,
    scrollState: ScrollState,
    maxScroll: Float,
    containerHeight: Float,
    scrollbarWidth: Dp,
    trackPadding: Dp,
    trackColor: Color,
    thumbColorIdle: Color,
    thumbColorDragging: Color,
) {
    // Local MutableState so pointer handlers see drag start immediately (same frame as onDrag),
    // without putting isDragging in pointerInput keys (which would cancel the gesture).
    val isDragging = remember { mutableStateOf(false) }
    val maxScrollState = rememberUpdatedState(maxScroll)
    val containerHeightState = rememberUpdatedState(containerHeight)

    val dragStartY = remember { mutableFloatStateOf(0f) }
    val dragStartScroll = remember { mutableIntStateOf(0) }
    val thumbColor = if (isDragging.value) thumbColorDragging else thumbColorIdle

    Box(
        modifier =
            modifier
                .fillMaxHeight()
                .width(scrollbarWidth + trackPadding * 2)
                .padding(vertical = 4.dp, horizontal = trackPadding)
                // Read scroll.value only here — invalidates draw, not composition of content.
                .drawBehind {
                    val metrics =
                        thumbMetrics(
                            maxScroll = scrollState.maxValue.toFloat(),
                            scrollValue = scrollState.value.toFloat(),
                            containerSize = size.height,
                        )
                    if (metrics == null) return@drawBehind
                    val barWidth = scrollbarWidth.toPx()
                    drawRoundRect(
                        color = trackColor,
                        topLeft = Offset(0f, 0f),
                        size = Size(barWidth, size.height),
                        cornerRadius = CornerRadius(barWidth / 2),
                    )
                    drawRoundRect(
                        color = thumbColor,
                        topLeft = Offset(0f, metrics.offset),
                        size = Size(barWidth, metrics.thumbSize),
                        cornerRadius = CornerRadius(barWidth / 2),
                    )
                }
                // Stable keys: restart only when track geometry (scroll range / viewport) changes.
                .pointerInput(maxScroll, containerHeight) {
                    detectTapGestures { offset ->
                        val metrics =
                            thumbMetrics(
                                maxScroll = maxScrollState.value,
                                scrollValue = scrollState.value.toFloat(),
                                containerSize = containerHeightState.value,
                            ) ?: return@detectTapGestures
                        val trackTravel =
                            (containerHeightState.value - metrics.thumbSize).coerceAtLeast(1f)
                        val targetFraction =
                            ((offset.y - metrics.thumbSize / 2) / trackTravel).coerceIn(0f, 1f)
                        scrollState.jumpToScroll((targetFraction * maxScrollState.value).toInt())
                    }
                }
                .pointerInput(maxScroll, containerHeight) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val metrics =
                                thumbMetrics(
                                    maxScroll = maxScrollState.value,
                                    scrollValue = scrollState.value.toFloat(),
                                    containerSize = containerHeightState.value,
                                ) ?: return@detectDragGestures
                            if (offset.y in metrics.offset..(metrics.offset + metrics.thumbSize)) {
                                isDragging.value = true
                                dragStartY.floatValue = offset.y
                                dragStartScroll.intValue = scrollState.value
                            }
                        },
                        onDrag = { change, _ ->
                            if (!isDragging.value) return@detectDragGestures
                            change.consume()
                            val metrics =
                                thumbMetrics(
                                    maxScroll = maxScrollState.value,
                                    scrollValue = 0f,
                                    containerSize = containerHeightState.value,
                                ) ?: return@detectDragGestures
                            val trackTravel =
                                (containerHeightState.value - metrics.thumbSize).coerceAtLeast(1f)
                            val scrollPerPx = maxScrollState.value / trackTravel
                            val totalDragY = change.position.y - dragStartY.floatValue
                            val newScroll =
                                (dragStartScroll.intValue + totalDragY * scrollPerPx)
                                    .toInt()
                                    .coerceIn(0, maxScrollState.value.toInt())
                            scrollState.jumpToScroll(newScroll)
                        },
                        onDragEnd = { isDragging.value = false },
                        onDragCancel = { isDragging.value = false },
                    )
                }
    )
}

/**
 * A scrollable container with a visible, chunky horizontal scrollbar.
 *
 * Mouse wheel / trackpad horizontal scroll is handled by [horizontalScroll].
 */
@Composable
fun ChunkyHorizontalScrollbar(
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    scrollbarHeight: Dp = 10.dp,
    trackPadding: Dp = 4.dp,
    content: @Composable () -> Unit,
) {
    var containerWidth by remember { mutableFloatStateOf(0f) }

    val maxScroll = scrollState.maxValue.toFloat()
    val needsScrollbar = maxScroll > 0f && containerWidth > 0f

    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val thumbColorIdle = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    val thumbColorDragging = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)

    Box(modifier = modifier) {
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .padding(bottom = scrollbarHeight + trackPadding * 2)
                    .horizontalScroll(scrollState)
        ) {
            content()
        }

        Box(
            modifier =
                Modifier.fillMaxSize().onSizeChanged { size ->
                    val w = size.width.toFloat()
                    if (w != containerWidth) containerWidth = w
                }
        )

        if (needsScrollbar) {
            HorizontalScrollbarChrome(
                modifier = Modifier.align(Alignment.BottomCenter),
                scrollState = scrollState,
                maxScroll = maxScroll,
                containerWidth = containerWidth,
                scrollbarHeight = scrollbarHeight,
                trackPadding = trackPadding,
                trackColor = trackColor,
                thumbColorIdle = thumbColorIdle,
                thumbColorDragging = thumbColorDragging,
            )
        }
    }
}

@Composable
private fun HorizontalScrollbarChrome(
    modifier: Modifier = Modifier,
    scrollState: ScrollState,
    maxScroll: Float,
    containerWidth: Float,
    scrollbarHeight: Dp,
    trackPadding: Dp,
    trackColor: Color,
    thumbColorIdle: Color,
    thumbColorDragging: Color,
) {
    val isDragging = remember { mutableStateOf(false) }
    val maxScrollState = rememberUpdatedState(maxScroll)
    val containerWidthState = rememberUpdatedState(containerWidth)

    val dragStartX = remember { mutableFloatStateOf(0f) }
    val dragStartScroll = remember { mutableIntStateOf(0) }
    val thumbColor = if (isDragging.value) thumbColorDragging else thumbColorIdle

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(scrollbarHeight + trackPadding * 2)
                .padding(horizontal = 4.dp, vertical = trackPadding)
                .drawBehind {
                    val metrics =
                        thumbMetrics(
                            maxScroll = scrollState.maxValue.toFloat(),
                            scrollValue = scrollState.value.toFloat(),
                            containerSize = size.width,
                        )
                    if (metrics == null) return@drawBehind
                    val barHeight = scrollbarHeight.toPx()
                    drawRoundRect(
                        color = trackColor,
                        topLeft = Offset(0f, 0f),
                        size = Size(size.width, barHeight),
                        cornerRadius = CornerRadius(barHeight / 2),
                    )
                    drawRoundRect(
                        color = thumbColor,
                        topLeft = Offset(metrics.offset, 0f),
                        size = Size(metrics.thumbSize, barHeight),
                        cornerRadius = CornerRadius(barHeight / 2),
                    )
                }
                .pointerInput(maxScroll, containerWidth) {
                    detectTapGestures { offset ->
                        val metrics =
                            thumbMetrics(
                                maxScroll = maxScrollState.value,
                                scrollValue = scrollState.value.toFloat(),
                                containerSize = containerWidthState.value,
                            ) ?: return@detectTapGestures
                        val trackTravel =
                            (containerWidthState.value - metrics.thumbSize).coerceAtLeast(1f)
                        val targetFraction =
                            ((offset.x - metrics.thumbSize / 2) / trackTravel).coerceIn(0f, 1f)
                        scrollState.jumpToScroll((targetFraction * maxScrollState.value).toInt())
                    }
                }
                .pointerInput(maxScroll, containerWidth) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val metrics =
                                thumbMetrics(
                                    maxScroll = maxScrollState.value,
                                    scrollValue = scrollState.value.toFloat(),
                                    containerSize = containerWidthState.value,
                                ) ?: return@detectDragGestures
                            if (offset.x in metrics.offset..(metrics.offset + metrics.thumbSize)) {
                                isDragging.value = true
                                dragStartX.floatValue = offset.x
                                dragStartScroll.intValue = scrollState.value
                            }
                        },
                        onDrag = { change, _ ->
                            if (!isDragging.value) return@detectDragGestures
                            change.consume()
                            val metrics =
                                thumbMetrics(
                                    maxScroll = maxScrollState.value,
                                    scrollValue = 0f,
                                    containerSize = containerWidthState.value,
                                ) ?: return@detectDragGestures
                            val trackTravel =
                                (containerWidthState.value - metrics.thumbSize).coerceAtLeast(1f)
                            val scrollPerPx = maxScrollState.value / trackTravel
                            val totalDragX = change.position.x - dragStartX.floatValue
                            val newScroll =
                                (dragStartScroll.intValue + totalDragX * scrollPerPx)
                                    .toInt()
                                    .coerceIn(0, maxScrollState.value.toInt())
                            scrollState.jumpToScroll(newScroll)
                        },
                        onDragEnd = { isDragging.value = false },
                        onDragCancel = { isDragging.value = false },
                    )
                }
    )
}

private data class ThumbMetrics(val thumbSize: Float, val offset: Float)

/** Shared proportion math for vertical (height) and horizontal (width) thumbs. */
private fun thumbMetrics(
    maxScroll: Float,
    scrollValue: Float,
    containerSize: Float,
): ThumbMetrics? {
    if (maxScroll <= 0f || containerSize <= 0f) return null
    val contentSize = containerSize + maxScroll
    val thumbSize = (containerSize / contentSize * containerSize).coerceAtLeast(40f)
    val scrollFraction = (scrollValue / maxScroll).coerceIn(0f, 1f)
    val offset = scrollFraction * (containerSize - thumbSize)
    return ThumbMetrics(thumbSize = thumbSize, offset = offset)
}

/**
 * Apply absolute scroll position without launching a cancelable coroutine.
 *
 * Using [ScrollState.scrollTo] from a new launch on every pointer move raced the MutatorMutex and
 * thrash-cancelled itself — drag felt broken and janky.
 */
private fun ScrollState.jumpToScroll(target: Int) {
    val clamped = target.coerceIn(0, maxValue)
    val delta = (clamped - value).toFloat()
    if (delta != 0f) {
        dispatchRawDelta(delta)
    }
}
