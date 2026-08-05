package org.kryspetrie.fileimport.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Horizontal split with a draggable divider. First pane width is controlled in dp; second pane
 * fills remaining space.
 */
@Composable
fun ResizableSplitPane(
    firstWidthDp: Dp,
    onFirstWidthChange: (Dp) -> Unit,
    modifier: Modifier = Modifier,
    minFirstWidth: Dp = 160.dp,
    maxFirstWidth: Dp = 600.dp,
    dividerWidth: Dp = 6.dp,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    var dragAccumulation by remember { mutableStateOf(0f) }
    val clampedWidth = firstWidthDp.coerceIn(minFirstWidth, maxFirstWidth)

    Row(modifier = modifier) {
        Box(modifier = Modifier.width(clampedWidth)) { first() }
        Box(
            modifier =
                Modifier.width(dividerWidth)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant)
                    .pointerInput(clampedWidth) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            dragAccumulation += dragAmount.x
                            val deltaDp = with(density) { dragAccumulation.toDp() }
                            if (abs(deltaDp.value) >= 1f) {
                                onFirstWidthChange(
                                    (clampedWidth + deltaDp).coerceIn(minFirstWidth, maxFirstWidth)
                                )
                                dragAccumulation = 0f
                            }
                        }
                    }
        ) {
            Box(
                modifier =
                    Modifier.width(2.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outline)
                        .align(Alignment.Center)
            )
        }
        Box(modifier = Modifier.weight(1f)) { second() }
    }
}

/** Vertical weight split for preview vs metadata panes. */
@Composable
fun ResizableWeightSplitPane(
    firstWeight: Float,
    onFirstWeightChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    minFirstWeight: Float = 0.35f,
    maxFirstWeight: Float = 0.75f,
    dividerHeight: Dp = 6.dp,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    var dragAccumulation by remember { mutableStateOf(0f) }
    val clampedWeight = firstWeight.coerceIn(minFirstWeight, maxFirstWeight)
    val secondWeight = (1f - clampedWeight).coerceAtLeast(minFirstWeight)

    Column(modifier = modifier) {
        Box(modifier = Modifier.weight(clampedWeight)) { first() }
        Box(
            modifier =
                Modifier.height(dividerHeight)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.outlineVariant)
                    .pointerInput(clampedWeight) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            dragAccumulation += dragAmount.y
                            val deltaDp = with(density) { dragAccumulation.toDp() }
                            if (abs(deltaDp.value) >= 2f) {
                                val deltaWeight = deltaDp.value / 800f
                                onFirstWeightChange(
                                    (clampedWeight + deltaWeight).coerceIn(
                                        minFirstWeight,
                                        maxFirstWeight,
                                    )
                                )
                                dragAccumulation = 0f
                            }
                        }
                    }
        )
        Box(modifier = Modifier.weight(secondWeight)) { second() }
    }
}
