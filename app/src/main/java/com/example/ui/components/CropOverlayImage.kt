package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.NormalizedRect
import kotlin.math.abs

private enum class DragMode {
    NONE, MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
}

@Composable
fun CropOverlayImage(
    bitmap: Bitmap?,
    cropRect: NormalizedRect,
    onCropChanged: (NormalizedRect) -> Unit,
    modifier: Modifier = Modifier
) {
    val aspect = if (bitmap != null && bitmap.height > 0) {
        (bitmap.width.toFloat() / bitmap.height.toFloat()).coerceIn(0.5f, 2.2f)
    } else 16f / 9f

    var dragMode by remember { mutableStateOf(DragMode.NONE) }
    var currentRect by remember(cropRect) { mutableStateOf(cropRect) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = cropRect.isFullFrame,
                onClick = { onCropChanged(NormalizedRect.FULL_FRAME) },
                label = { Text("Auto (Poora Frame)") },
                modifier = Modifier.testTag("crop_auto_btn"),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
            FilterChip(
                selected = !cropRect.isFullFrame && cropRect.top > 0.55f,
                onClick = { onCropChanged(NormalizedRect.BOTTOM_SUBTITLE) },
                label = { Text("Neeche Subtitle Box") },
                modifier = Modifier.testTag("crop_bottom_btn"),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }

        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspect)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Video frame preview for text area selection",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = "Frame load ho raha hai...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            val accent = MaterialTheme.colorScheme.primary
            val cornerColor = MaterialTheme.colorScheme.secondary

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { start ->
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                val h = size.height.toFloat().coerceAtLeast(1f)
                                val nx = (start.x / w).coerceIn(0f, 1f)
                                val ny = (start.y / h).coerceIn(0f, 1f)
                                val r = currentRect
                                val tol = 0.11f
                                dragMode = when {
                                    abs(nx - r.left) < tol && abs(ny - r.top) < tol -> DragMode.TOP_LEFT
                                    abs(nx - r.right) < tol && abs(ny - r.top) < tol -> DragMode.TOP_RIGHT
                                    abs(nx - r.left) < tol && abs(ny - r.bottom) < tol -> DragMode.BOTTOM_LEFT
                                    abs(nx - r.right) < tol && abs(ny - r.bottom) < tol -> DragMode.BOTTOM_RIGHT
                                    nx in r.left..r.right && ny in r.top..r.bottom -> DragMode.MOVE
                                    else -> DragMode.NONE
                                }
                            },
                            onDragEnd = {
                                dragMode = DragMode.NONE
                                onCropChanged(currentRect)
                            },
                            onDragCancel = {
                                dragMode = DragMode.NONE
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                val h = size.height.toFloat().coerceAtLeast(1f)
                                val dx = dragAmount.x / w
                                val dy = dragAmount.y / h
                                val r = currentRect
                                val minSize = 0.12f
                                currentRect = when (dragMode) {
                                    DragMode.MOVE -> {
                                        val bw = r.right - r.left
                                        val bh = r.bottom - r.top
                                        val nl = (r.left + dx).coerceIn(0f, 1f - bw)
                                        val nt = (r.top + dy).coerceIn(0f, 1f - bh)
                                        NormalizedRect(nl, nt, nl + bw, nt + bh)
                                    }
                                    DragMode.TOP_LEFT -> r.copy(
                                        left = (r.left + dx).coerceIn(0f, r.right - minSize),
                                        top = (r.top + dy).coerceIn(0f, r.bottom - minSize)
                                    )
                                    DragMode.TOP_RIGHT -> r.copy(
                                        right = (r.right + dx).coerceIn(r.left + minSize, 1f),
                                        top = (r.top + dy).coerceIn(0f, r.bottom - minSize)
                                    )
                                    DragMode.BOTTOM_LEFT -> r.copy(
                                        left = (r.left + dx).coerceIn(0f, r.right - minSize),
                                        bottom = (r.bottom + dy).coerceIn(r.top + minSize, 1f)
                                    )
                                    DragMode.BOTTOM_RIGHT -> r.copy(
                                        right = (r.right + dx).coerceIn(r.left + minSize, 1f),
                                        bottom = (r.bottom + dy).coerceIn(r.top + minSize, 1f)
                                    )
                                    DragMode.NONE -> r
                                }
                            }
                        )
                    }
            ) {
                val r = currentRect
                val leftPx = r.left * size.width
                val topPx = r.top * size.height
                val rightPx = r.right * size.width
                val bottomPx = r.bottom * size.height

                // Dim outside crop area
                if (!r.isFullFrame) {
                    val dim = Color.Black.copy(alpha = 0.55f)
                    drawRect(dim, Offset.Zero, Size(size.width, topPx))
                    drawRect(dim, Offset(0f, bottomPx), Size(size.width, size.height - bottomPx))
                    drawRect(dim, Offset(0f, topPx), Size(leftPx, bottomPx - topPx))
                    drawRect(dim, Offset(rightPx, topPx), Size(size.width - rightPx, bottomPx - topPx))
                }

                // Active selection border
                drawRect(
                    color = accent,
                    topLeft = Offset(leftPx, topPx),
                    size = Size(rightPx - leftPx, bottomPx - topPx),
                    style = Stroke(width = 3.dp.toPx())
                )

                // Draggable corner handles
                val handleRadius = 8.dp.toPx()
                listOf(
                    Offset(leftPx, topPx),
                    Offset(rightPx, topPx),
                    Offset(leftPx, bottomPx),
                    Offset(rightPx, bottomPx)
                ).forEach { pt ->
                    drawCircle(color = cornerColor, radius = handleRadius, center = pt)
                    drawCircle(color = Color.Black, radius = handleRadius * 0.45f, center = pt)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (currentRect.isFullFrame) {
                "Auto Mode: Poora video screen padha jayega. Box drag karke sirf ek hissa bhi chun sakte hain."
            } else {
                "Custom Area Active: Sirf chune hue box ke andar ka text padha jayega."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
