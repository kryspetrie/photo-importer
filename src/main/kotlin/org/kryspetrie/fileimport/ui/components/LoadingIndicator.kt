package org.kryspetrie.fileimport.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.Center
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * CubeGrid-style loading indicator using native Compose.
 *
 * This is a performant, desktop-compatible implementation inspired by the Compose-SpinKit library's
 * CubeGrid animation.
 *
 * The animation consists of a 3x3 grid of squares that animate in a wave pattern, creating a
 * visually appealing loading indicator.
 */
@Composable
fun CubeGridLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    animationDuration: Int = 1200,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "CubeGridTransition")

    // Single master progress value from 0f to 1f
    val progress by
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(animationDuration, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "CubeGridProgress",
        )

    val density = androidx.compose.ui.platform.LocalDensity.current

    Box(modifier = modifier.size(60.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(48.dp)) {
            val t = progress

            // Fixed dimensions in density-independent pixels
            val cellSize = with(density) { 12.dp.toPx() }
            val gap = with(density) { 4.dp.toPx() }
            val totalSize = 3f * cellSize + 2f * gap
            val offsetX = (size.width - totalSize) / 2f
            val offsetY = (size.height - totalSize) / 2f

            // Wave animation pattern - each cell animates with a delay
            for (row in 0 until 3) {
                for (col in 0 until 3) {
                    val index = row * 3 + col
                    // Each cell has a different phase in the wave
                    val phase = (index.toFloat() / 9f)
                    // Calculate animation progress for this cell
                    val cellProgress = ((t + phase) % 1f)

                    // Opacity animation - fade in and out
                    val alpha =
                        when {
                            cellProgress < 0.25f -> cellProgress / 0.25f
                            cellProgress < 0.75f -> 1f
                            else -> (1f - cellProgress) / 0.25f
                        }

                    // Scale animation - grow and shrink
                    val scale =
                        when {
                            cellProgress < 0.25f -> 0.5f + 0.5f * (cellProgress / 0.25f)
                            cellProgress < 0.75f -> 1f
                            else -> 1f - 0.5f * ((cellProgress - 0.75f) / 0.25f)
                        }

                    val x = offsetX + col * (cellSize + gap)
                    val y = offsetY + row * (cellSize + gap)

                    // Draw the cell with scale and alpha
                    drawRect(
                        color = color.copy(alpha = alpha.coerceIn(0.3f, 1f)),
                        topLeft =
                            Offset(
                                x = x + cellSize * (1f - scale) / 2f,
                                y = y + cellSize * (1f - scale) / 2f,
                            ),
                        size = Size(width = cellSize * scale, height = cellSize * scale),
                    )
                }
            }
        }
    }
}

/**
 * Loading overlay that displays a CubeGrid spinner with a message.
 *
 * @param isLoading Whether to show the loading overlay
 * @param message Optional message to display below the spinner
 * @param content The main content to display behind the overlay
 */
@Composable
fun LoadingOverlay(
    isLoading: Boolean,
    message: String = "Processing...",
    content: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        content()

        if (isLoading) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CubeGridLoadingIndicator(
                        modifier = Modifier.size(72.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/** Simple loading indicator without overlay. Suitable for inline loading states. */
@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 40.dp,
) {
    CubeGridLoadingIndicator(modifier = modifier.size(size), color = color)
}

/** Loading indicator with progress information. */
@Composable
fun LoadingProgressIndicator(
    progress: Float, // 0.0 to 1.0
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CubeGridLoadingIndicator(
            modifier = Modifier.size(56.dp),
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (progress >= 0f) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(0.6f))
        }
    }
}
