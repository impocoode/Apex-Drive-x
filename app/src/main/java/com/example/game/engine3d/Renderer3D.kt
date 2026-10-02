package com.example.game.engine3d

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.game.physics.VehiclePhysics
import kotlin.math.*
import kotlin.random.Random

class Renderer3D {
    private val scratchPath = Path()
    private val polySortList = mutableListOf<Polygon3D>()

    fun render(
        drawScope: DrawScope,
        camera: Camera3D,
        track: Track3D,
        physics: VehiclePhysics,
        carModel: CarModel3D,
        particleSystem: ParticleSystem
    ) {
        val width = drawScope.size.width
        val height = drawScope.size.height
        val theme = track.theme

        // Screen shake offset
        val shakeX = if (camera.shakeAmount > 0.001f) (Random.nextFloat() - 0.5f) * camera.shakeAmount * 30f else 0f
        val shakeY = if (camera.shakeAmount > 0.001f) (Random.nextFloat() - 0.5f) * camera.shakeAmount * 30f else 0f

        // --- 1. SKYBOX & HORIZON ---
        renderSkybox(drawScope, camera, theme, width, height, shakeX, shakeY)

        // --- 2. 3D TRACK ROAD SEGMENTS & CURBS ---
        renderTrack(drawScope, camera, track, width, height, shakeX, shakeY)

        // --- 3. SKID MARKS ---
        renderSkidMarks(drawScope, camera, particleSystem, width, height, shakeX, shakeY)

        // --- 4. CAR SHADOW ---
        renderCarShadow(drawScope, camera, physics, width, height, shakeX, shakeY)

        // --- 5. 3D CAR MODEL ---
        renderCar(drawScope, camera, physics, carModel, track, width, height, shakeX, shakeY)

        // --- 6. PARTICLES (Smoke, Sparks, Nitro Flames) ---
        renderParticles(drawScope, camera, particleSystem, width, height, shakeX, shakeY)

        // --- 7. POST-PROCESSING FX (Speed Lines, Flash) ---
        renderSpeedFx(drawScope, physics, width, height)
    }

    private fun renderSkybox(
        drawScope: DrawScope,
        camera: Camera3D,
        theme: TrackTheme,
        width: Float,
        height: Float,
        shakeX: Float,
        shakeY: Float
    ) {
        // Sky gradient
        drawScope.drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(theme.skyColorTop, theme.skyColorBottom),
                startY = 0f,
                endY = height * 0.75f
            ),
            topLeft = Offset(0f, 0f),
            size = Size(width, height)
        )

        // Distant horizon mountains/city skyline
        val horizonY = height * 0.42f + shakeY
        val camYaw = atan2(camera.getForward().x, camera.getForward().z)
        val skylineOffset = (camYaw / (Math.PI * 2f).toFloat()) * width * 1.5f

        // Distant hills / mountains
        val mountainPath = Path().apply {
            moveTo(0f, height)
            lineTo(0f, horizonY + 15f)
            val step = width / 12f
            for (i in 0..13) {
                val x = i * step
                val hillHeight = sin((x + skylineOffset) * 0.008f) * 45f + cos((x + skylineOffset) * 0.016f) * 25f
                val y = horizonY - 15f + hillHeight
                lineTo(x, y)
            }
            lineTo(width, height)
            close()
        }
        val mountainColor = if (theme.nightMode) Color(0xFF0F172A) else Color(
            theme.groundColor.red * 0.5f,
            theme.groundColor.green * 0.5f,
            theme.groundColor.blue * 0.5f,
            0.65f
        )
        drawScope.drawPath(mountainPath, color = mountainColor, style = Fill)

        // Ground base plane below horizon
        drawScope.drawRect(
            color = theme.groundColor,
            topLeft = Offset(0f, horizonY),
            size = Size(width, height - horizonY)
        )

        // Sun or Moon with radial glow
        val sunDir = theme.sunDirection
        val sunCamPos = camera.position + (sunDir * 400f)
        val sunProj = camera.project(sunCamPos, width, height)
        if (sunProj.visible && sunProj.sy < height * 0.65f) {
            val sunCenter = Offset(sunProj.sx + shakeX, sunProj.sy + shakeY)
            val sunRadius = if (theme.nightMode) 22f else 36f
            drawScope.drawCircle(
                color = theme.sunColor.copy(alpha = 0.35f),
                radius = sunRadius * 2.8f,
                center = sunCenter
            )
            drawScope.drawCircle(
                color = theme.sunColor,
                radius = sunRadius,
                center = sunCenter
            )
        }
    }

    private fun renderTrack(
        drawScope: DrawScope,
        camera: Camera3D,
        track: Track3D,
        width: Float,
        height: Float,
        shakeX: Float,
        shakeY: Float
    ) {
        val theme = track.theme
        val segments = track.segments
        val numSegments = segments.size
        val camPos = camera.position

        val closestIdx = track.getClosestSegmentIndex(camPos)
        val renderDistance = 75 // Number of segments forward/backward to render

        // We collect road quads and sort them from back to front for clean rendering
        data class RoadQuad(
            val p1: ProjectedPoint,
            val p2: ProjectedPoint,
            val p3: ProjectedPoint,
            val p4: ProjectedPoint,
            val color: Color,
            val depth: Float,
            val isCenterStripe: Boolean = false,
            val isKerb: Boolean = false
        )

        val roadQuads = mutableListOf<RoadQuad>()

        for (offset in -15..renderDistance) {
            val i = (closestIdx + offset + numSegments) % numSegments
            val nextI = (i + 1) % numSegments

            val seg1 = segments[i]
            val seg2 = segments[nextI]

            val distFromCam = (seg1.center - camPos).length()
            if (distFromCam > 450f) continue

            val halfW1 = seg1.width * 0.5f
            val halfW2 = seg2.width * 0.5f

            // Road edge vertices
            val l1 = seg1.center - (seg1.right * halfW1)
            val r1 = seg1.center + (seg1.right * halfW1)
            val l2 = seg2.center - (seg2.right * halfW2)
            val r2 = seg2.center + (seg2.right * halfW2)

            val pL1 = camera.project(l1, width, height)
            val pR1 = camera.project(r1, width, height)
            val pL2 = camera.project(l2, width, height)
            val pR2 = camera.project(r2, width, height)

            if (!pL1.visible && !pR1.visible && !pL2.visible && !pR2.visible) continue

            val avgDepth = (pL1.depth + pR1.depth + pL2.depth + pR2.depth) * 0.25f

            // Asphalt Road Base Quad
            val asphaltTone = if ((i / 2) % 2 == 0) theme.roadColor else Color(
                (theme.roadColor.red * 1.05f).coerceAtMost(1f),
                (theme.roadColor.green * 1.05f).coerceAtMost(1f),
                (theme.roadColor.blue * 1.05f).coerceAtMost(1f),
                1f
            )
            roadQuads.add(RoadQuad(pL1, pR1, pR2, pL2, asphaltTone, avgDepth))

            // White Dashed Centerline
            if (i % 3 != 0) {
                val cW1 = 0.22f
                val cL1 = seg1.center - (seg1.right * cW1)
                val cR1 = seg1.center + (seg1.right * cW1)
                val cL2 = seg2.center - (seg2.right * cW1)
                val cR2 = seg2.center + (seg2.right * cW1)

                val pcL1 = camera.project(cL1, width, height)
                val pcR1 = camera.project(cR1, width, height)
                val pcL2 = camera.project(cL2, width, height)
                val pcR2 = camera.project(cR2, width, height)

                if (pcL1.visible || pcR1.visible || pcL2.visible || pcR2.visible) {
                    val lineCol = if (theme.nightMode) Color(0xFF00E5FF) else Color(0xFFF8FAFC)
                    roadQuads.add(RoadQuad(pcL1, pcR1, pcR2, pcL2, lineCol, avgDepth - 0.05f, isCenterStripe = true))
                }
            }

            // Rumble Curbs (Left & Right)
            val kerbWidth = 1.4f
            val kerbColor = if ((i / 2) % 2 == 0) theme.kerbColor1 else theme.kerbColor2

            if (seg1.hasKerbLeft) {
                val klOut1 = seg1.center - (seg1.right * (halfW1 + kerbWidth))
                val klOut2 = seg2.center - (seg2.right * (halfW2 + kerbWidth))
                val pKlOut1 = camera.project(klOut1, width, height)
                val pKlOut2 = camera.project(klOut2, width, height)

                roadQuads.add(RoadQuad(pKlOut1, pL1, pL2, pKlOut2, kerbColor, avgDepth, isKerb = true))
            }

            if (seg1.hasKerbRight) {
                val krOut1 = seg1.center + (seg1.right * (halfW1 + kerbWidth))
                val krOut2 = seg2.center + (seg2.right * (halfW2 + kerbWidth))
                val pKrOut1 = camera.project(krOut1, width, height)
                val pKrOut2 = camera.project(krOut2, width, height)

                roadQuads.add(RoadQuad(pR1, pKrOut1, pKrOut2, pR2, kerbColor, avgDepth, isKerb = true))
            }
        }

        // Depth sort back to front (Painter's algorithm)
        roadQuads.sortByDescending { it.depth }

        for (quad in roadQuads) {
            scratchPath.reset()
            scratchPath.moveTo(quad.p1.sx + shakeX, quad.p1.sy + shakeY)
            scratchPath.lineTo(quad.p2.sx + shakeX, quad.p2.sy + shakeY)
            scratchPath.lineTo(quad.p3.sx + shakeX, quad.p3.sy + shakeY)
            scratchPath.lineTo(quad.p4.sx + shakeX, quad.p4.sy + shakeY)
            scratchPath.close()

            // Distance fog blend
            val fogFactor = (quad.depth * theme.fogDensity).coerceIn(0f, 0.85f)
            val finalColor = if (fogFactor > 0.01f) {
                Color(
                    quad.color.red * (1f - fogFactor) + theme.skyColorBottom.red * fogFactor,
                    quad.color.green * (1f - fogFactor) + theme.skyColorBottom.green * fogFactor,
                    quad.color.blue * (1f - fogFactor) + theme.skyColorBottom.blue * fogFactor,
                    quad.color.alpha
                )
            } else quad.color

            drawScope.drawPath(scratchPath, color = finalColor, style = Fill)
        }

        // Render Scenery Objects (grandstands, start gantry, light poles, distance boards)
        renderScenery(drawScope, camera, track, width, height, shakeX, shakeY)
    }

    private fun renderScenery(
        drawScope: DrawScope,
        camera: Camera3D,
        track: Track3D,
        width: Float,
        height: Float,
        shakeX: Float,
        shakeY: Float
    ) {
        val camPos = camera.position
        for (obj in track.sceneryObjects) {
            val dist = (obj.position - camPos).length()
            if (dist > 350f) continue

            val baseProj = camera.project(obj.position, width, height)
            if (!baseProj.visible) continue

            val sx = baseProj.sx + shakeX
            val sy = baseProj.sy + shakeY
            val scale = (100f / baseProj.depth).coerceIn(2f, 90f)

            when (obj.type) {
                ObjectType.START_GANTRY -> {
                    // Big Start/Finish overhead banner
                    val topProj = camera.project(obj.position + Vector3(0f, 9f, 0f), width, height)
                    if (topProj.visible) {
                        val gantryW = scale * 12f
                        val gantryH = (baseProj.sy - topProj.sy)
                        drawScope.drawRect(
                            color = Color(0xFF1E293B),
                            topLeft = Offset(sx - gantryW * 0.5f, topProj.sy + shakeY),
                            size = Size(gantryW, gantryH * 0.35f)
                        )
                        // Checkered pattern banner strip
                        drawScope.drawRect(
                            color = Color(0xFFFF2A4B),
                            topLeft = Offset(sx - gantryW * 0.5f, topProj.sy + shakeY + gantryH * 0.1f),
                            size = Size(gantryW, gantryH * 0.12f)
                        )
                    }
                }
                ObjectType.LIGHT_POLE -> {
                    val topProj = camera.project(obj.position + Vector3(0f, 8f, 0f), width, height)
                    if (topProj.visible) {
                        drawScope.drawLine(
                            color = Color(0xFF64748B),
                            start = Offset(sx, sy),
                            end = Offset(topProj.sx + shakeX, topProj.sy + shakeY),
                            strokeWidth = (scale * 0.25f).coerceAtLeast(1.5f)
                        )
                        // Glowing bulb at night
                        if (track.theme.nightMode) {
                            drawScope.drawCircle(
                                color = Color(0xFF00E5FF),
                                radius = scale * 0.6f,
                                center = Offset(topProj.sx + shakeX, topProj.sy + shakeY)
                            )
                        }
                    }
                }
                ObjectType.GRANDSTAND -> {
                    val topProj = camera.project(obj.position + Vector3(0f, 7f, 0f), width, height)
                    if (topProj.visible) {
                        val standW = scale * 9f
                        val standH = (baseProj.sy - topProj.sy)
                        drawScope.drawRect(
                            color = Color(0xFF334155),
                            topLeft = Offset(sx - standW * 0.5f, topProj.sy + shakeY),
                            size = Size(standW, standH)
                        )
                        // Spectator stands colorful lines
                        drawScope.drawRect(
                            color = Color(0xFFFF2A4B),
                            topLeft = Offset(sx - standW * 0.45f, topProj.sy + shakeY + standH * 0.2f),
                            size = Size(standW * 0.9f, standH * 0.15f)
                        )
                        drawScope.drawRect(
                            color = Color(0xFFFFD600),
                            topLeft = Offset(sx - standW * 0.45f, topProj.sy + shakeY + standH * 0.45f),
                            size = Size(standW * 0.9f, standH * 0.15f)
                        )
                    }
                }
                ObjectType.DISTANCE_BOARD_100, ObjectType.DISTANCE_BOARD_50 -> {
                    val boardH = scale * 3.5f
                    val boardW = scale * 2.5f
                    drawScope.drawRect(
                        color = Color(0xFFF8FAFC),
                        topLeft = Offset(sx - boardW * 0.5f, sy - boardH),
                        size = Size(boardW, boardH)
                    )
                    drawScope.drawRect(
                        color = Color(0xFF0F172A),
                        topLeft = Offset(sx - boardW * 0.4f, sy - boardH * 0.85f),
                        size = Size(boardW * 0.8f, boardH * 0.7f),
                        style = Stroke(width = 2f)
                    )
                }
                ObjectType.TREE -> {
                    val treeH = scale * 6f
                    val treeW = scale * 3.5f
                    // Trunk
                    drawScope.drawRect(
                        color = Color(0xFF5D4037),
                        topLeft = Offset(sx - treeW * 0.15f, sy - treeH * 0.35f),
                        size = Size(treeW * 0.3f, treeH * 0.35f)
                    )
                    // Foliage
                    drawScope.drawCircle(
                        color = Color(0xFF1B5E20),
                        radius = treeW * 0.65f,
                        center = Offset(sx, sy - treeH * 0.7f)
                    )
                }
                ObjectType.TUNNEL_ARCH -> {
                    val archProj = camera.project(obj.position + Vector3(0f, 8f, 0f), width, height)
                    if (archProj.visible) {
                        drawScope.drawCircle(
                            color = Color(0xFF00E5FF),
                            radius = scale * 6f,
                            center = Offset(sx, archProj.sy + shakeY),
                            style = Stroke(width = scale * 0.4f)
                        )
                    }
                }
                else -> {}
            }
        }
    }

    private fun renderSkidMarks(
        drawScope: DrawScope,
        camera: Camera3D,
        particleSystem: ParticleSystem,
        width: Float,
        height: Float,
        shakeX: Float,
        shakeY: Float
    ) {
        for (sm in particleSystem.skidMarks) {
            val p1 = camera.project(sm.p1, width, height)
            val p2 = camera.project(sm.p2, width, height)
            if (p1.visible && p2.visible) {
                drawScope.drawLine(
                    color = Color.Black.copy(alpha = sm.alpha * 0.7f),
                    start = Offset(p1.sx + shakeX, p1.sy + shakeY),
                    end = Offset(p2.sx + shakeX, p2.sy + shakeY),
                    strokeWidth = (28f / p1.depth).coerceIn(2f, 18f)
                )
            }
        }
    }

    private fun renderCarShadow(
        drawScope: DrawScope,
        camera: Camera3D,
        physics: VehiclePhysics,
        width: Float,
        height: Float,
        shakeX: Float,
        shakeY: Float
    ) {
        val carPos = physics.position
        val yaw = physics.heading
        val w = 1.05f
        val l = 2.2f

        val groundShadow = listOf(
            Vector3(-w, 0.02f, l).rotateY(yaw) + carPos,
            Vector3(w, 0.02f, l).rotateY(yaw) + carPos,
            Vector3(w, 0.02f, -l).rotateY(yaw) + carPos,
            Vector3(-w, 0.02f, -l).rotateY(yaw) + carPos
        )

        val projected = groundShadow.map { camera.project(it, width, height) }
        if (projected.all { it.visible }) {
            scratchPath.reset()
            scratchPath.moveTo(projected[0].sx + shakeX, projected[0].sy + shakeY)
            scratchPath.lineTo(projected[1].sx + shakeX, projected[1].sy + shakeY)
            scratchPath.lineTo(projected[2].sx + shakeX, projected[2].sy + shakeY)
            scratchPath.lineTo(projected[3].sx + shakeX, projected[3].sy + shakeY)
            scratchPath.close()

            drawScope.drawPath(
                scratchPath,
                color = Color(0x99000000),
                style = Fill
            )
        }
    }

    private fun renderCar(
        drawScope: DrawScope,
        camera: Camera3D,
        physics: VehiclePhysics,
        carModel: CarModel3D,
        track: Track3D,
        width: Float,
        height: Float,
        shakeX: Float,
        shakeY: Float
    ) {
        val isCockpit = (camera.mode == CameraMode.COCKPIT)
        val polygons = carModel.generatePolygons(physics, isCockpitView = isCockpit)
        val sunDir = track.theme.sunDirection

        polySortList.clear()

        // Calculate depth and backface culling
        for (poly in polygons) {
            val v0 = poly.vertices[0]
            val v1 = poly.vertices[1]
            val v2 = poly.vertices[2]

            // Normal calculation
            val normal = (v1 - v0).cross(v2 - v0).normalized()
            val toCam = (camera.position - v0).normalized()

            val facing = normal.dot(toCam)
            if (!poly.doubleSided && facing <= 0f) {
                continue // Backface culled
            }

            // Depth calculation (distance from camera)
            var sumDepth = 0f
            for (v in poly.vertices) {
                sumDepth += (v - camera.position).length()
            }
            poly.depth = sumDepth / poly.vertices.size
            polySortList.add(poly)
        }

        // Painter's algorithm sort
        polySortList.sortByDescending { it.depth }

        for (poly in polySortList) {
            val v0 = poly.vertices[0]
            val v1 = poly.vertices[1]
            val v2 = poly.vertices[2]
            val normal = (v1 - v0).cross(v2 - v0).normalized()

            // Directional Lighting & Specular Calculation
            val diffuse = max(0f, normal.dot(sunDir))
            val toCam = (camera.position - v0).normalized()
            val reflectDir = (normal * (2f * normal.dot(sunDir)) - sunDir).normalized()
            val specular = if (poly.isMetallic) {
                max(0f, reflectDir.dot(toCam)).pow(18f) * 0.45f
            } else 0f

            val baseCol = poly.color
            val litColor = if (poly.isEmissive) {
                baseCol
            } else {
                val ambient = if (track.theme.nightMode) 0.35f else 0.48f
                val lightTotal = (ambient + diffuse * 0.52f + specular).coerceIn(0.1f, 1.4f)
                Color(
                    (baseCol.red * lightTotal + specular).coerceIn(0f, 1f),
                    (baseCol.green * lightTotal + specular).coerceIn(0f, 1f),
                    (baseCol.blue * lightTotal + specular).coerceIn(0f, 1f),
                    baseCol.alpha
                )
            }

            scratchPath.reset()
            var anyVisible = false
            for (i in poly.vertices.indices) {
                val p = camera.project(poly.vertices[i], width, height)
                if (p.visible) anyVisible = true
                if (i == 0) {
                    scratchPath.moveTo(p.sx + shakeX, p.sy + shakeY)
                } else {
                    scratchPath.lineTo(p.sx + shakeX, p.sy + shakeY)
                }
            }
            scratchPath.close()

            if (anyVisible) {
                drawScope.drawPath(scratchPath, color = litColor, style = Fill)
                // Outline edge for crisp 3D styling
                if (poly.wireframe || poly.isMetallic) {
                    drawScope.drawPath(
                        scratchPath,
                        color = Color.Black.copy(alpha = 0.25f),
                        style = Stroke(width = 1f)
                    )
                }
            }
        }
    }

    private fun renderParticles(
        drawScope: DrawScope,
        camera: Camera3D,
        particleSystem: ParticleSystem,
        width: Float,
        height: Float,
        shakeX: Float,
        shakeY: Float
    ) {
        for (p in particleSystem.particles) {
            val proj = camera.project(p.position, width, height)
            if (!proj.visible) continue

            val alpha = (p.life / p.maxLife).coerceIn(0f, 1f)
            val radius = (p.size * (120f / proj.depth)).coerceIn(1.5f, 45f)
            val center = Offset(proj.sx + shakeX, proj.sy + shakeY)

            when (p.type) {
                ParticleType.TIRE_SMOKE -> {
                    drawScope.drawCircle(
                        color = p.color.copy(alpha = alpha * 0.45f),
                        radius = radius,
                        center = center
                    )
                }
                ParticleType.NITRO_FLAME -> {
                    drawScope.drawCircle(
                        color = p.color.copy(alpha = alpha * 0.9f),
                        radius = radius * 0.75f,
                        center = center
                    )
                }
                ParticleType.SPARK -> {
                    drawScope.drawCircle(
                        color = p.color.copy(alpha = alpha),
                        radius = radius * 0.4f,
                        center = center
                    )
                }
            }
        }
    }

    private fun renderSpeedFx(
        drawScope: DrawScope,
        physics: VehiclePhysics,
        width: Float,
        height: Float
    ) {
        val speedKmh = physics.speedKmh
        if (speedKmh > 180f) {
            // Speed blur streaks at screen corners
            val intensity = ((speedKmh - 180f) / 140f).coerceIn(0f, 0.75f)
            val lineColor = if (physics.nitroActive) Color(0xFF00E5FF) else Color.White

            for (i in 0..12) {
                val isLeft = i % 2 == 0
                val startX = if (isLeft) Random.nextFloat() * 100f else width - Random.nextFloat() * 100f
                val startY = Random.nextFloat() * height
                val endX = if (isLeft) startX + 120f + Random.nextFloat() * 80f else startX - 120f - Random.nextFloat() * 80f
                val endY = startY + (Random.nextFloat() - 0.5f) * 40f

                drawScope.drawLine(
                    color = lineColor.copy(alpha = intensity * 0.6f),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 2.5f
                )
            }
        }
    }
}
