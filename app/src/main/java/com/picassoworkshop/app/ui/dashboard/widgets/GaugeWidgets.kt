package com.picassoworkshop.app.ui.dashboard.widgets

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.picassoworkshop.app.dashboard.model.DashboardWidgetConfig
import com.picassoworkshop.app.dashboard.model.PidCatalog
import com.picassoworkshop.app.ui.theme.*
import kotlin.math.*

/**
 * High-precision analog automotive radial dial gauge with rotating needle sweep,
 * numbered tick marks, alert zone arc, and digital center readout.
 */
@Composable
fun RadialDialGaugeCard(
    config: DashboardWidgetConfig,
    currentValue: Double,
    modifier: Modifier = Modifier
) {
    val meta = remember(config.pidId) { PidCatalog.getById(config.pidId) }
    val title = config.customTitle ?: meta?.displayName ?: config.pidId
    val unit = config.unitOverride ?: meta?.defaultUnit ?: ""

    val minVal = config.minVal
    val maxVal = if (config.maxVal > minVal) config.maxVal else minVal + 100.0
    val warnVal = config.warnThreshold ?: meta?.defaultWarn
    val dangerVal = config.dangerThreshold ?: meta?.defaultDanger

    val clampedVal = currentValue.coerceIn(minVal, maxVal)
    val fraction = ((clampedVal - minVal) / (maxVal - minVal)).toFloat()

    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 350),
        label = "radial_needle"
    )

    val alertColor = when {
        dangerVal != null && currentValue >= dangerVal -> CitroenRed
        warnVal != null && currentValue >= warnVal -> GoldenBolt
        else -> CyanAccent
    }

    Surface(
        color = WorkshopSurface,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, WorkshopBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Title and Unit
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title.uppercase(),
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = alertColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, alertColor)
                ) {
                    Text(
                        text = unit,
                        color = alertColor,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Radial Dial Canvas
            Box(
                modifier = Modifier
                    .size(170.dp)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val center = Offset(w / 2f, h / 2f)
                    val radius = (minOf(w, h) / 2f) - 12f

                    val startAngle = 135f
                    val sweepAngle = 270f

                    // 1. Background Arc Track
                    drawArc(
                        color = BlueprintGrid.copy(alpha = 0.4f),
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2f, radius * 2f),
                        style = Stroke(width = 8f, cap = StrokeCap.Round)
                    )

                    // 2. Active Value Sweep Arc
                    val activeSweep = sweepAngle * animatedFraction
                    if (activeSweep > 0f) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                0.0f to CyanAccent,
                                0.6f to GoldenBolt,
                                1.0f to CitroenRed,
                                center = center
                            ),
                            startAngle = startAngle,
                            sweepAngle = activeSweep,
                            useCenter = false,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2f, radius * 2f),
                            style = Stroke(width = 8f, cap = StrokeCap.Round)
                        )
                    }

                    // 3. Tick Marks
                    val totalTicks = 8
                    for (i in 0..totalTicks) {
                        val tickFraction = i.toFloat() / totalTicks
                        val tickAngleRad = Math.toRadians((startAngle + (sweepAngle * tickFraction)).toDouble())
                        val innerR = radius - 14f
                        val outerR = radius - 4f

                        val startP = Offset(
                            (center.x + innerR * cos(tickAngleRad)).toFloat(),
                            (center.y + innerR * sin(tickAngleRad)).toFloat()
                        )
                        val endP = Offset(
                            (center.x + outerR * cos(tickAngleRad)).toFloat(),
                            (center.y + outerR * sin(tickAngleRad)).toFloat()
                        )

                        val isMajor = i % 2 == 0
                        drawLine(
                            color = if (isMajor) TextPrimary.copy(alpha = 0.8f) else TextSecondary.copy(alpha = 0.4f),
                            start = startP,
                            end = endP,
                            strokeWidth = if (isMajor) 2.5f else 1.5f,
                            cap = StrokeCap.Round
                        )
                    }

                    // 4. Pointer Needle
                    val needleAngleRad = Math.toRadians((startAngle + (sweepAngle * animatedFraction)).toDouble())
                    val needleLen = radius - 16f
                    val needleEnd = Offset(
                        (center.x + needleLen * cos(needleAngleRad)).toFloat(),
                        (center.y + needleLen * sin(needleAngleRad)).toFloat()
                    )

                    // Needle line
                    drawLine(
                        color = alertColor,
                        start = center,
                        end = needleEnd,
                        strokeWidth = 3.5f,
                        cap = StrokeCap.Round
                    )

                    // Center Hub Pivot
                    drawCircle(color = WorkshopSurfaceElevated, radius = 9f, center = center)
                    drawCircle(color = alertColor, radius = 5f, center = center)
                }

                // Digital readout in lower center of dial
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 48.dp)
                ) {
                    val formattedVal = if (currentValue >= 100 || currentValue == currentValue.toInt().toDouble()) {
                        currentValue.toInt().toString()
                    } else {
                        String.format("%.2f", currentValue)
                    }
                    Text(
                        text = formattedVal,
                        color = alertColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = unit,
                        color = TextSecondary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Min and Max range scale footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${minVal.toInt()}",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "${maxVal.toInt()}",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

/**
 * Live rolling waveform trend graph showing real-time sensor fluctuation over the last 30 samples.
 */
@Composable
fun TrendGraphCard(
    config: DashboardWidgetConfig,
    currentValue: Double,
    modifier: Modifier = Modifier
) {
    val meta = remember(config.pidId) { PidCatalog.getById(config.pidId) }
    val title = config.customTitle ?: meta?.displayName ?: config.pidId
    val unit = config.unitOverride ?: meta?.defaultUnit ?: ""

    // Rolling history buffer (max 30 samples)
    val history = remember { mutableStateListOf<Float>() }

    LaunchedEffect(currentValue) {
        history.add(currentValue.toFloat())
        if (history.size > 30) {
            history.removeAt(0)
        }
    }

    val minVal = config.minVal.toFloat()
    val maxVal = if (config.maxVal > config.minVal) config.maxVal.toFloat() else minVal + 10f
    val warnVal = config.warnThreshold?.toFloat()
    val dangerVal = config.dangerThreshold?.toFloat()

    val alertColor = when {
        dangerVal != null && currentValue >= dangerVal -> CitroenRed
        warnVal != null && currentValue >= warnVal -> GoldenBolt
        else -> CyanAccent
    }

    Surface(
        color = WorkshopSurface,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, WorkshopBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ShowChart,
                        contentDescription = null,
                        tint = alertColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = title.uppercase(),
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val formattedVal = if (currentValue >= 100 || currentValue == currentValue.toInt().toDouble()) {
                        currentValue.toInt().toString()
                    } else {
                        String.format("%.2f", currentValue)
                    }
                    Text(
                        text = formattedVal,
                        color = alertColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = unit,
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Waveform Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .background(DarkBackground, RoundedCornerShape(8.dp))
                    .border(0.5.dp, BlueprintGrid.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 6.dp)) {
                    val w = size.width
                    val h = size.height

                    // Grid lines (3 horizontal reference lines)
                    val gridDash = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                    drawLine(
                        color = BlueprintGrid.copy(alpha = 0.3f),
                        start = Offset(0f, 0f),
                        end = Offset(w, 0f),
                        strokeWidth = 0.8f,
                        pathEffect = gridDash
                    )
                    drawLine(
                        color = BlueprintGrid.copy(alpha = 0.3f),
                        start = Offset(0f, h / 2f),
                        end = Offset(w, h / 2f),
                        strokeWidth = 0.8f,
                        pathEffect = gridDash
                    )
                    drawLine(
                        color = BlueprintGrid.copy(alpha = 0.3f),
                        start = Offset(0f, h),
                        end = Offset(w, h),
                        strokeWidth = 0.8f,
                        pathEffect = gridDash
                    )

                    if (history.size > 1) {
                        val stepX = w / (29f)
                        val linePath = Path()
                        val fillPath = Path()

                        val range = (maxVal - minVal).coerceAtLeast(0.001f)

                        history.forEachIndexed { i, sample ->
                            val x = i * stepX
                            val normalized = ((sample.coerceIn(minVal, maxVal) - minVal) / range).coerceIn(0f, 1f)
                            val y = h - (normalized * h)

                            if (i == 0) {
                                linePath.moveTo(x, y)
                                fillPath.moveTo(x, h)
                                fillPath.lineTo(x, y)
                            } else {
                                val prevX = (i - 1) * stepX
                                val prevNorm = ((history[i - 1].coerceIn(minVal, maxVal) - minVal) / range).coerceIn(0f, 1f)
                                val prevY = h - (prevNorm * h)

                                // Smooth cubic interpolation
                                val cx = (prevX + x) / 2f
                                linePath.cubicTo(cx, prevY, cx, y, x, y)
                                fillPath.cubicTo(cx, prevY, cx, y, x, y)
                            }
                        }

                        // Close fill path
                        val lastX = (history.size - 1) * stepX
                        fillPath.lineTo(lastX, h)
                        fillPath.close()

                        // Draw area gradient fill
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(alertColor.copy(alpha = 0.35f), alertColor.copy(alpha = 0.03f)),
                                startY = 0f,
                                endY = h
                            )
                        )

                        // Draw line stroke
                        drawPath(
                            path = linePath,
                            color = alertColor,
                            style = Stroke(width = 2.5f, cap = StrokeCap.Round)
                        )

                        // Draw endpoint pulse dot
                        val lastNorm = ((history.last().coerceIn(minVal, maxVal) - minVal) / range).coerceIn(0f, 1f)
                        val lastY = h - (lastNorm * h)
                        drawCircle(color = alertColor, radius = 4f, center = Offset(lastX, lastY))
                        drawCircle(color = Color.White, radius = 2f, center = Offset(lastX, lastY))
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Subtitle status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "MIN: ${minVal.toInt()} $unit",
                    color = TextSecondary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "30-SAMPLE ROLLING BUFFER",
                    color = TextSecondary,
                    fontSize = 8.5.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "MAX: ${maxVal.toInt()} $unit",
                    color = TextSecondary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

/**
 * Clean linear horizontal bar meter with colored threshold fill.
 */
@Composable
fun BarGaugeCard(
    config: DashboardWidgetConfig,
    currentValue: Double,
    modifier: Modifier = Modifier
) {
    val meta = remember(config.pidId) { PidCatalog.getById(config.pidId) }
    val title = config.customTitle ?: meta?.displayName ?: config.pidId
    val unit = config.unitOverride ?: meta?.defaultUnit ?: ""

    val minVal = config.minVal
    val maxVal = if (config.maxVal > minVal) config.maxVal else minVal + 100.0
    val warnVal = config.warnThreshold ?: meta?.defaultWarn
    val dangerVal = config.dangerThreshold ?: meta?.defaultDanger

    val fraction = ((currentValue.coerceIn(minVal, maxVal) - minVal) / (maxVal - minVal)).toFloat()
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 300),
        label = "bar_meter"
    )

    val alertColor = when {
        dangerVal != null && currentValue >= dangerVal -> CitroenRed
        warnVal != null && currentValue >= warnVal -> GoldenBolt
        else -> MintGreen
    }

    Surface(
        color = WorkshopSurface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, WorkshopBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val formattedVal = if (currentValue >= 100 || currentValue == currentValue.toInt().toDouble()) {
                        currentValue.toInt().toString()
                    } else {
                        String.format("%.1f", currentValue)
                    }
                    Text(
                        text = "$formattedVal $unit",
                        color = alertColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Progress bar track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(WorkshopBackground)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedFraction)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(CyanAccent, alertColor)
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${minVal.toInt()} $unit",
                    color = TextSecondary,
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "${maxVal.toInt()} $unit",
                    color = TextSecondary,
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

/**
 * Large bold digital numeric readout card with nominal range indicators and unit badges.
 */
@Composable
fun DigitalReadoutCard(
    config: DashboardWidgetConfig,
    currentValue: Double,
    modifier: Modifier = Modifier
) {
    val meta = remember(config.pidId) { PidCatalog.getById(config.pidId) }
    val title = config.customTitle ?: meta?.displayName ?: config.pidId
    val unit = config.unitOverride ?: meta?.defaultUnit ?: ""

    val warnVal = config.warnThreshold ?: meta?.defaultWarn
    val dangerVal = config.dangerThreshold ?: meta?.defaultDanger

    val alertColor = when {
        dangerVal != null && currentValue >= dangerVal -> CitroenRed
        warnVal != null && currentValue >= warnVal -> GoldenBolt
        else -> CyanAccent
    }

    Surface(
        color = WorkshopSurface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, WorkshopBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title.uppercase(),
                color = TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val formattedVal = if (currentValue >= 100 || currentValue == currentValue.toInt().toDouble()) {
                    currentValue.toInt().toString()
                } else {
                    String.format("%.2f", currentValue)
                }

                Text(
                    text = formattedVal,
                    color = alertColor,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )

                Text(
                    text = unit,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Surface(
                color = alertColor.copy(alpha = 0.12f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = if (dangerVal != null && currentValue >= dangerVal) "HIGH ALERT" else if (warnVal != null && currentValue >= warnVal) "WARNING" else "NOMINAL",
                    color = alertColor,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }
    }
}

/**
 * Warning / Status LED beacon indicator tile with status badge.
 */
@Composable
fun StatusIndicatorCard(
    config: DashboardWidgetConfig,
    currentValue: Double,
    modifier: Modifier = Modifier
) {
    val meta = remember(config.pidId) { PidCatalog.getById(config.pidId) }
    val title = config.customTitle ?: meta?.displayName ?: config.pidId

    val warnVal = config.warnThreshold ?: meta?.defaultWarn
    val dangerVal = config.dangerThreshold ?: meta?.defaultDanger

    val isAlert = dangerVal != null && currentValue >= dangerVal
    val isWarning = warnVal != null && currentValue >= warnVal

    val statusColor = when {
        isAlert -> CitroenRed
        isWarning -> GoldenBolt
        else -> MintGreen
    }

    val statusText = when {
        isAlert -> "CRITICAL THRESHOLD"
        isWarning -> "CHECK SENSOR"
        else -> "SYSTEM NOMINAL"
    }

    Surface(
        color = WorkshopSurface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.6f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Glowing LED Beacon
                Surface(
                    color = statusColor,
                    shape = CircleShape,
                    modifier = Modifier.size(16.dp)
                ) {}

                Column {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Surface(
                color = statusColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = "${currentValue.toInt()}",
                    color = statusColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
