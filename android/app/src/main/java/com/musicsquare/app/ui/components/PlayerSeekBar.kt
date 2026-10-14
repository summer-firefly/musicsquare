package com.musicsquare.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.musicsquare.app.util.formatTimeMs
import kotlin.math.roundToLong

@Composable
fun PlayerSeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val duration = durationMs.toFloat().coerceAtLeast(0f)
    var scrubbing by remember { mutableFloatStateOf(Float.NaN) }
    val progress = if (!scrubbing.isNaN()) {
        scrubbing
    } else if (duration > 0f) {
        (positionMs / duration).coerceIn(0f, 1f)
    } else {
        0f
    }
    val displayPosition = if (!scrubbing.isNaN() && duration > 0f) {
        (scrubbing * duration).roundToLong()
    } else {
        positionMs
    }

    val activeStart = MaterialTheme.colorScheme.primary
    val activeEnd = MaterialTheme.colorScheme.secondary
    val inactive = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
    val thumbColor = Color.White
    val thumbRing = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        if (duration <= 0f) return@detectTapGestures
                        val ratio = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onSeek((ratio * duration).roundToLong())
                    }
                }
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            if (duration <= 0f) return@detectHorizontalDragGestures
                            scrubbing = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            if (!scrubbing.isNaN() && duration > 0f) {
                                onSeek((scrubbing * duration).roundToLong())
                            }
                            scrubbing = Float.NaN
                        },
                        onDragCancel = { scrubbing = Float.NaN },
                        onHorizontalDrag = { change, _ ->
                            if (duration <= 0f) return@detectHorizontalDragGestures
                            change.consume()
                            scrubbing = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        },
                    )
                },
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(28.dp)) {
                val trackHeight = 4.dp.toPx()
                val trackTop = (size.height - trackHeight) / 2f
                val thumbRadius = if (scrubbing.isNaN()) 6.dp.toPx() else 8.dp.toPx()
                val inset = thumbRadius
                val usable = (size.width - inset * 2f).coerceAtLeast(0f)
                val thumbX = inset + usable * progress

                // Inactive track
                drawRoundRect(
                    color = inactive,
                    topLeft = Offset(inset, trackTop),
                    size = Size(usable, trackHeight),
                    cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f),
                )

                // Active track with soft gradient
                if (progress > 0f) {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(activeStart, activeEnd),
                            startX = inset,
                            endX = thumbX,
                        ),
                        topLeft = Offset(inset, trackTop),
                        size = Size((usable * progress).coerceAtLeast(trackHeight), trackHeight),
                        cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f),
                    )
                }

                // Soft glow under thumb
                drawCircle(
                    color = thumbRing,
                    radius = thumbRadius + 5.dp.toPx(),
                    center = Offset(thumbX, size.height / 2f),
                )
                // Thumb
                drawCircle(
                    color = thumbColor,
                    radius = thumbRadius,
                    center = Offset(thumbX, size.height / 2f),
                )
                // Inner accent
                drawCircle(
                    color = activeStart,
                    radius = thumbRadius * 0.42f,
                    center = Offset(thumbX, size.height / 2f),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTimeMs(displayPosition),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatTimeMs(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
