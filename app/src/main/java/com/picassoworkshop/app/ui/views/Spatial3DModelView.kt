package com.picassoworkshop.app.ui.views

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.picassoworkshop.app.data.model.ExplodedPart
import com.picassoworkshop.app.ui.theme.*
import kotlin.math.*

data class Point3D(val x: Float, val y: Float, val z: Float) {
    operator fun plus(other: Point3D): Point3D = Point3D(x + other.x, y + other.y, z + other.z)
    operator fun times(factor: Float): Point3D = Point3D(x * factor, y * factor, z * factor)
}

data class Polygon3D(
    val vertices: List<Point3D>,
    val baseColor: Color,
    val partId: String,
    val calloutNumber: Int,
    val normal: Point3D
)

@Composable
fun Spatial3DModelView(
    parts: List<ExplodedPart>,
    explodeProgress: Float,
    selectedPartId: String?,
    onPartSelected: (ExplodedPart) -> Unit,
    modifier: Modifier = Modifier
) {
    // 3D Camera Orbit Angles (in degrees)
    var pitch by remember { mutableFloatStateOf(25f) } // Elevation angle
    var yaw by remember { mutableFloatStateOf(-35f) }   // Azimuth angle
    var zoomScale by remember { mutableFloatStateOf(1.0f) }

    val textMeasurer = rememberTextMeasurer()
    val activePart = remember(selectedPartId, parts) {
        parts.firstOrNull { it.id == selectedPartId } ?: parts.firstOrNull()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WorkshopBackground)
            .pointerInput(Unit) {
                // 1-finger drag for 360 degree 3D rotation
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    yaw = (yaw + dragAmount.x * 0.45f) % 360f
                    pitch = (pitch - dragAmount.y * 0.45f).coerceIn(-80f, 80f)
                }
            }
            .pointerInput(Unit) {
                // 2-finger pinch zoom
                detectTransformGestures { _, _, zoom, _ ->
                    zoomScale = (zoomScale * zoom).coerceIn(0.5f, 2.5f)
                }
            }
    ) {
        // 3D Render Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val fovDistance = 600f * zoomScale

            // Light vector for directional shading (coming from top-front-right)
            val lightDir = normalize(Point3D(0.4f, -0.8f, 0.5f))

            // Radian angles
            val pitchRad = Math.toRadians(pitch.toDouble()).toFloat()
            val yawRad = Math.toRadians(yaw.toDouble()).toFloat()

            val cosP = cos(pitchRad)
            val sinP = sin(pitchRad)
            val cosY = cos(yawRad)
            val sinY = sin(yawRad)

            // Generate 3D geometry for all exploded parts
            val allPolygons = mutableListOf<Polygon3D>()

            for (part in parts) {
                val isSelected = part.id == selectedPartId

                // Explode displacement along 3D unit vector
                val explodeMagnitude = explodeProgress * 130f * part.explodeDistance
                val centerDisplacement = Point3D(
                    x = (part.assemblyX - 250f) * 0.9f + (part.explodeDirX * explodeMagnitude),
                    y = (part.assemblyY - 260f) * 0.9f + (part.explodeDirY * explodeMagnitude),
                    z = (part.explodeDirZ * explodeMagnitude)
                )

                val baseColor = try {
                    Color(android.graphics.Color.parseColor(part.colorHex))
                } catch (e: Throwable) {
                    CyanAccent
                }
                val finalColor = if (isSelected) CyanAccent else baseColor

                // Generate 3D bounding mesh polygons for part
                val w = part.width * 0.7f
                val h = part.height * 0.7f
                val d = part.depth * 0.7f

                val cubePolys = createBoxPolygons(
                    center = centerDisplacement,
                    w = w,
                    h = h,
                    d = d,
                    color = finalColor,
                    partId = part.id,
                    calloutNumber = part.calloutNumber
                )
                allPolygons.addAll(cubePolys)
            }

            // Project 3D polygons onto 2D view and compute depth
            data class ProjectedPoly(
                val screenPoints: List<Offset>,
                val avgZ: Float,
                val shadedColor: Color,
                val partId: String,
                val calloutNumber: Int
            )

            val projectedPolys = allPolygons.mapNotNull { poly ->
                val transformedVertices = poly.vertices.map { v ->
                    rotate3D(v, cosP, sinP, cosY, sinY)
                }

                // Average depth for Painter's algorithm
                val avgZ = transformedVertices.map { it.z }.average().toFloat()

                // Face normal rotation for lighting calculation
                val transNormal = rotate3D(poly.normal, cosP, sinP, cosY, sinY)

                // Backface culling: skip faces pointing away from camera
                if (transNormal.z < -0.1f) return@mapNotNull null

                // Lambertian diffuse lighting intensity
                val dot = max(0.25f, dotProduct(transNormal, lightDir))
                val shadedColor = poly.baseColor.copy(
                    red = (poly.baseColor.red * dot).coerceIn(0f, 1f),
                    green = (poly.baseColor.green * dot).coerceIn(0f, 1f),
                    blue = (poly.baseColor.blue * dot).coerceIn(0f, 1f)
                )

                // Perspective projection: x' = (x * fov) / (z + fov)
                val screenPoints = transformedVertices.map { v ->
                    val depthFactor = fovDistance / (v.z + fovDistance + 350f)
                    Offset(
                        x = cx + (v.x * depthFactor),
                        y = cy + (v.y * depthFactor)
                    )
                }

                ProjectedPoly(screenPoints, avgZ, shadedColor, poly.partId, poly.calloutNumber)
            }

            // Depth sorting (farthest polygons drawn first)
            val sortedPolys = projectedPolys.sortedByDescending { it.avgZ }

            // Draw shaded faces and crisp wireframe outlines
            for (poly in sortedPolys) {
                if (poly.screenPoints.size >= 3) {
                    val path = Path().apply {
                        moveTo(poly.screenPoints[0].x, poly.screenPoints[0].y)
                        for (i in 1 until poly.screenPoints.size) {
                            lineTo(poly.screenPoints[i].x, poly.screenPoints[i].y)
                        }
                        close()
                    }

                    // Fill face
                    drawPath(path = path, color = poly.shadedColor)

                    // Stroke edges
                    drawPath(
                        path = path,
                        color = if (poly.partId == selectedPartId) CyanAccent else WorkshopBorder.copy(alpha = 0.5f),
                        style = Stroke(width = if (poly.partId == selectedPartId) 2.5f else 1f)
                    )
                }
            }

            // Draw numbered 3D callout badges at the projected centers
            for (part in parts) {
                val isSelected = part.id == selectedPartId
                val explodeMagnitude = explodeProgress * 130f * part.explodeDistance
                val center3D = Point3D(
                    x = (part.assemblyX - 250f) * 0.9f + (part.explodeDirX * explodeMagnitude),
                    y = (part.assemblyY - 260f) * 0.9f + (part.explodeDirY * explodeMagnitude),
                    z = (part.explodeDirZ * explodeMagnitude)
                )

                val transCenter = rotate3D(center3D, cosP, sinP, cosY, sinY)
                val depthFactor = fovDistance / (transCenter.z + fovDistance + 350f)
                val screenX = cx + (transCenter.x * depthFactor)
                val screenY = cy + (transCenter.y * depthFactor)

                val badgeColor = if (isSelected) CitroenRed else GoldenBolt
                val radius = 13f * zoomScale

                drawCircle(color = badgeColor, radius = radius, center = Offset(screenX, screenY))
                drawCircle(color = WorkshopBackground, radius = radius * 0.85f, center = Offset(screenX, screenY))

                val label = "${part.calloutNumber}"
                val layout = textMeasurer.measure(
                    text = label,
                    style = TextStyle(
                        color = badgeColor,
                        fontSize = (10 * zoomScale).sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                )
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(screenX - (layout.size.width / 2f), screenY - (layout.size.height / 2f))
                )
            }
        }

        // Top-End Camera & 3D Orbit Reset Controls
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = WorkshopSurface.copy(alpha = 0.9f),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, WorkshopBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(imageVector = Icons.Default.Rotate90DegreesCcw, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(14.dp))
                    Text(text = "DRAG TO ROTATE 360°", color = TextSecondary, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace)
                }
            }

            // Reset Camera Angle FAB
            FloatingActionButton(
                onClick = {
                    pitch = 25f
                    yaw = -35f
                    zoomScale = 1.0f
                },
                containerColor = WorkshopSurfaceElevated,
                contentColor = TextPrimary,
                shape = CircleShape,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reset Camera", modifier = Modifier.size(18.dp))
            }
        }

        // Part selection chips row
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 92.dp, start = 12.dp, end = 12.dp)
                .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            parts.forEach { part ->
                val isSelected = part.id == selectedPartId
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) CyanAccent else WorkshopSurface.copy(alpha = 0.85f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) CyanAccent else WorkshopBorder),
                    modifier = Modifier.clickable { onPartSelected(part) }
                ) {
                    Text(
                        text = "${part.calloutNumber}. ${part.name.take(14)}",
                        color = if (isSelected) WorkshopBackground else TextPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Active Part Technical Callout Card at Bottom
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
                                color = GoldenBolt,
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
                                    text = "${part.torqueNm} Nm",
                                    color = GoldenBolt,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "OEM: ${part.oemPartNumber} • Tool: ${part.fastenerType}",
                        color = CyanAccent,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

// 3D Geometry generation helpers
private fun createBoxPolygons(
    center: Point3D,
    w: Float,
    h: Float,
    d: Float,
    color: Color,
    partId: String,
    calloutNumber: Int
): List<Polygon3D> {
    val hx = w / 2f
    val hy = h / 2f
    val hz = d / 2f

    val c000 = center + Point3D(-hx, -hy, -hz)
    val c001 = center + Point3D(-hx, -hy, hz)
    val c010 = center + Point3D(-hx, hy, -hz)
    val c011 = center + Point3D(-hx, hy, hz)
    val c100 = center + Point3D(hx, -hy, -hz)
    val c101 = center + Point3D(hx, -hy, hz)
    val c110 = center + Point3D(hx, hy, -hz)
    val c111 = center + Point3D(hx, hy, hz)

    return listOf(
        // Front face (Z+)
        Polygon3D(listOf(c001, c101, c111, c011), color, partId, calloutNumber, Point3D(0f, 0f, 1f)),
        // Back face (Z-)
        Polygon3D(listOf(c100, c000, c010, c110), color.copy(alpha = 0.9f), partId, calloutNumber, Point3D(0f, 0f, -1f)),
        // Top face (Y-)
        Polygon3D(listOf(c000, c100, c101, c001), color.copy(alpha = 0.95f), partId, calloutNumber, Point3D(0f, -1f, 0f)),
        // Bottom face (Y+)
        Polygon3D(listOf(c011, c111, c110, c010), color.copy(alpha = 0.85f), partId, calloutNumber, Point3D(0f, 1f, 0f)),
        // Right face (X+)
        Polygon3D(listOf(c101, c100, c110, c111), color.copy(alpha = 0.92f), partId, calloutNumber, Point3D(1f, 0f, 0f)),
        // Left face (X-)
        Polygon3D(listOf(c000, c001, c011, c010), color.copy(alpha = 0.88f), partId, calloutNumber, Point3D(-1f, 0f, 0f))
    )
}

private fun rotate3D(p: Point3D, cosP: Float, sinP: Float, cosY: Float, sinY: Float): Point3D {
    // 1. Yaw rotation (around Y axis)
    val x1 = (p.x * cosY) + (p.z * sinY)
    val y1 = p.y
    val z1 = (-p.x * sinY) + (p.z * cosY)

    // 2. Pitch rotation (around X axis)
    val x2 = x1
    val y2 = (y1 * cosP) - (z1 * sinP)
    val z2 = (y1 * sinP) + (z1 * cosP)

    return Point3D(x2, y2, z2)
}

private fun dotProduct(a: Point3D, b: Point3D): Float = (a.x * b.x) + (a.y * b.y) + (a.z * b.z)

private fun normalize(p: Point3D): Point3D {
    val len = sqrt(p.x * p.x + p.y * p.y + p.z * p.z)
    return if (len > 0f) Point3D(p.x / len, p.y / len, p.z / len) else p
}
