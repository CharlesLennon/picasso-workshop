package com.picassoworkshop.app.ui.views

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.picassoworkshop.app.data.WorkshopDataManager
import com.picassoworkshop.app.data.model.ExplodedPart
import com.picassoworkshop.app.ui.theme.*
import kotlin.math.sqrt

@Composable
fun BlownOutDiagramView(
    parts: List<ExplodedPart>,
    explodeProgress: Float,
    selectedPartId: String?,
    onPartSelected: (ExplodedPart) -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1.0f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }

    val textMeasurer = rememberTextMeasurer()
    val activePart = remember(selectedPartId, parts) {
        parts.firstOrNull { it.id == selectedPartId } ?: parts.firstOrNull()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WorkshopBackground)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.6f, 3.0f)
                    panX += pan.x
                    panY += pan.y
                }
            }
            .pointerInput(parts, explodeProgress, scale, panX, panY) {
                detectTapGestures { tapOffset ->
                    // Hit test callout pins
                    for (part in parts) {
                        val currX = WorkshopDataManager.interpolatePos(part.assemblyX, part.explodedX, explodeProgress)
                        val currY = WorkshopDataManager.interpolatePos(part.assemblyY, part.explodedY, explodeProgress)

                        val screenX = (currX * scale) + panX
                        val screenY = (currY * scale) + panY

                        val dx = tapOffset.x - screenX
                        val dy = tapOffset.y - screenY
                        val distance = sqrt(dx * dx + dy * dy)

                        // 36dp generous touch radius
                        if (distance <= 36f * scale) {
                            onPartSelected(part)
                            break
                        }
                    }
                }
            }
    ) {
        // 1. Technical Vector Blueprint Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridSpacing = 40f * scale
            val startX = panX % gridSpacing
            val startY = panY % gridSpacing

            // Blueprint grid dots
            var gx = startX
            while (gx < size.width) {
                var gy = startY
                while (gy < size.height) {
                    drawCircle(
                        color = WorkshopBorder.copy(alpha = 0.45f),
                        radius = 1.2f * scale,
                        center = Offset(gx, gy)
                    )
                    gy += gridSpacing
                }
                gx += gridSpacing
            }

            // Draw technical components & exploded leader lines
            for (part in parts) {
                val isSelected = part.id == selectedPartId
                val currX = WorkshopDataManager.interpolatePos(part.assemblyX, part.explodedX, explodeProgress)
                val currY = WorkshopDataManager.interpolatePos(part.assemblyY, part.explodedY, explodeProgress)

                val screenX = (currX * scale) + panX
                val screenY = (currY * scale) + panY

                val assembledScreenX = (part.assemblyX * scale) + panX
                val assembledScreenY = (part.assemblyY * scale) + panY

                // Exploded leader line if separated
                if (explodeProgress > 0.05f) {
                    drawLine(
                        color = if (isSelected) CyanAccent else WorkshopBorder.copy(alpha = 0.7f),
                        start = Offset(assembledScreenX, assembledScreenY),
                        end = Offset(screenX, screenY),
                        strokeWidth = 1.5f * scale,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f * scale, 8f * scale), 0f)
                    )
                    drawCircle(
                        color = WorkshopBorder,
                        radius = 3f * scale,
                        center = Offset(assembledScreenX, assembledScreenY)
                    )
                }

                // Component geometry
                drawPartSchematic(
                    drawScope = this,
                    part = part,
                    center = Offset(screenX, screenY),
                    scale = scale,
                    isSelected = isSelected
                )

                // Numbered Callout Pin
                val pinRadius = 14f * scale
                val pinColor = when {
                    isSelected -> CitroenRed
                    part.torqueNm != null -> GoldenBolt
                    else -> CyanAccent
                }

                drawCircle(
                    color = pinColor,
                    radius = pinRadius,
                    center = Offset(screenX, screenY)
                )
                drawCircle(
                    color = WorkshopBackground,
                    radius = pinRadius * 0.85f,
                    center = Offset(screenX, screenY)
                )

                val calloutText = "${part.calloutNumber}"
                val textLayout = textMeasurer.measure(
                    text = calloutText,
                    style = TextStyle(
                        color = pinColor,
                        fontSize = (11 * scale).sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                )
                drawText(
                    textLayoutResult = textLayout,
                    topLeft = Offset(
                        screenX - (textLayout.size.width / 2f),
                        screenY - (textLayout.size.height / 2f)
                    )
                )
            }
        }

        // 2. Technical Callout Info Card (Floating Overlay at Bottom)
        activePart?.let { part ->
            Surface(
                color = WorkshopSurface.copy(alpha = 0.95f),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (part.id == selectedPartId) CyanAccent else WorkshopBorder),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = CyanAccent,
                                shape = CircleShape,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${part.calloutNumber}",
                                        color = WorkshopBackground,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                            Text(
                                text = part.name,
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (part.torqueNm != null) {
                            Surface(
                                color = GoldenBolt.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, GoldenBolt)
                            ) {
                                Text(
                                    text = "TORQUE: ${part.torqueNm} Nm",
                                    color = GoldenBolt,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "OEM: ${part.oemPartNumber}",
                            color = CyanAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace
                        )
                        if (part.fastenerType.isNotBlank()) {
                            Text(
                                text = "Tool: ${part.fastenerType}",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    if (part.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = part.description,
                            color = TextMuted,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}

private fun drawPartSchematic(
    drawScope: DrawScope,
    part: ExplodedPart,
    center: Offset,
    scale: Float,
    isSelected: Boolean
) {
    val color = try {
        Color(android.graphics.Color.parseColor(part.colorHex))
    } catch (e: Throwable) {
        CyanAccent
    }

    val w = part.width * scale
    val h = part.height * scale
    val strokeWidth = if (isSelected) 3f * scale else 1.5f * scale

    when (part.meshShape) {
        "CYLINDER" -> {
            drawScope.drawOval(
                color = color.copy(alpha = if (isSelected) 0.35f else 0.15f),
                topLeft = Offset(center.x - w / 2f, center.y - h / 2f),
                size = Size(w, h)
            )
            drawScope.drawOval(
                color = if (isSelected) CyanAccent else color,
                topLeft = Offset(center.x - w / 2f, center.y - h / 2f),
                size = Size(w, h),
                style = Stroke(width = strokeWidth)
            )
            drawScope.drawCircle(
                color = color.copy(alpha = 0.5f),
                radius = (w * 0.25f),
                center = center
            )
        }
        "PULLEY" -> {
            drawScope.drawCircle(
                color = color.copy(alpha = if (isSelected) 0.35f else 0.2f),
                radius = w / 2f,
                center = center
            )
            drawScope.drawCircle(
                color = if (isSelected) GoldenBolt else color,
                radius = w / 2f,
                center = center,
                style = Stroke(width = strokeWidth)
            )
            drawScope.drawCircle(
                color = WorkshopBackground,
                radius = w * 0.35f,
                center = center
            )
            drawScope.drawCircle(
                color = GoldenBolt,
                radius = w * 0.15f,
                center = center
            )
        }
        "BOLT" -> {
            drawScope.drawRect(
                color = color.copy(alpha = if (isSelected) 0.8f else 0.4f),
                topLeft = Offset(center.x - w / 2f, center.y - h / 2f),
                size = Size(w, h)
            )
            drawScope.drawRect(
                color = if (isSelected) GoldenBolt else color,
                topLeft = Offset(center.x - w / 2f, center.y - h / 2f),
                size = Size(w, h),
                style = Stroke(width = strokeWidth)
            )
        }
        "BELT" -> {
            drawScope.drawRoundRect(
                color = color.copy(alpha = if (isSelected) 0.4f else 0.15f),
                topLeft = Offset(center.x - w / 2f, center.y - h / 2f),
                size = Size(w, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f * scale, 16f * scale)
            )
            drawScope.drawRoundRect(
                color = if (isSelected) MintGreen else color,
                topLeft = Offset(center.x - w / 2f, center.y - h / 2f),
                size = Size(w, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f * scale, 16f * scale),
                style = Stroke(width = strokeWidth)
            )
        }
        else -> {
            drawScope.drawRoundRect(
                color = color.copy(alpha = if (isSelected) 0.35f else 0.18f),
                topLeft = Offset(center.x - w / 2f, center.y - h / 2f),
                size = Size(w, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f * scale, 8f * scale)
            )
            drawScope.drawRoundRect(
                color = if (isSelected) CyanAccent else color,
                topLeft = Offset(center.x - w / 2f, center.y - h / 2f),
                size = Size(w, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f * scale, 8f * scale),
                style = Stroke(width = strokeWidth)
            )
        }
    }
}
