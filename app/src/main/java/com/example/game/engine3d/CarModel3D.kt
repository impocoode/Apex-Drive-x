package com.example.game.engine3d

import androidx.compose.ui.graphics.Color
import com.example.game.physics.CarSpecs
import com.example.game.physics.VehiclePhysics
import kotlin.math.cos
import kotlin.math.sin

class CarModel3D(
    val specs: CarSpecs,
    var bodyColor: Color = specs.defaultColor
) {
    private var wheelRotation = 0f

    fun updateAnimation(speedMs: Float, dt: Float) {
        val wheelRadius = 0.34f
        val angularSpeed = speedMs / wheelRadius
        wheelRotation = (wheelRotation + angularSpeed * dt) % (Math.PI.toFloat() * 2f)
    }

    fun generatePolygons(
        physics: VehiclePhysics,
        isCockpitView: Boolean = false
    ): List<Polygon3D> {
        val polygons = mutableListOf<Polygon3D>()

        val carPos = physics.position
        val yaw = physics.heading
        val pitch = physics.pitch
        val roll = physics.roll
        val steer = physics.steerAngle
        val isBraking = physics.brakeInput > 0.1f || physics.handbrake

        fun transformChassis(local: Vector3): Vector3 {
            var v = local
            v = v.rotateX(pitch)
            v = v.rotateZ(roll)
            v = v.rotateY(yaw)
            return v + carPos
        }

        // Palette
        val primaryPaint = bodyColor
        val darkShadow = Color(
            (primaryPaint.red * 0.6f),
            (primaryPaint.green * 0.6f),
            (primaryPaint.blue * 0.6f),
            1f
        )
        val carbonDark = Color(0xFF181B20)
        val glassColor = Color(0xCC111827)
        val headlightColor = when (specs.id) {
            "phantom_hyperx" -> Color(0xFF67E8F9) // Laser Cyan
            "viper_gts" -> Color(0xFFFEF08A)     // Xenon Yellow
            else -> Color(0xFFE0F2FE)            // LED White
        }
        val taillightColor = if (isBraking) Color(0xFFFF1744) else Color(0xFF991B1B)
        val tireColor = Color(0xFF1E2024)
        val rimColor = when (specs.id) {
            "viper_gts" -> Color(0xFFCBD5E1)      // Chrome
            "kaze_sprint" -> Color(0xFFF59E0B)    // Bronze
            "venom_rx" -> Color(0xFF0F172A)       // Matte Black
            else -> Color(0xFFE2E8F0)             // Silver
        }
        val brakeCaliperColor = when (specs.id) {
            "phantom_hyperx" -> Color(0xFF00E5FF)
            "venom_rx" -> Color(0xFFFF2A4B)
            else -> Color(0xFFFFD600)
        }

        // --- MODEL SPECIFIC DIMENSIONS & SILHOUETTES ---
        val isHypercar = specs.id == "phantom_hyperx"
        val isMuscle = specs.id == "viper_gts"
        val isTuner = specs.id == "kaze_sprint"
        val isDrift = specs.id == "venom_rx"

        val w = if (isHypercar) 1.05f else if (isTuner) 0.88f else if (isMuscle) 1.02f else 0.95f
        val lF = if (isHypercar) 2.25f else if (isMuscle) 2.2f else if (isTuner) 1.85f else 2.1f
        val lR = if (isHypercar) 2.15f else if (isTuner) 1.75f else 2.0f
        val hHood = if (isHypercar) 0.42f else if (isMuscle) 0.54f else 0.48f
        val hRoof = if (isHypercar) 0.74f else if (isTuner) 0.88f else if (isMuscle) 0.86f else 0.83f
        val hGround = 0.12f

        // --- 1. CHASSIS MAIN BODY PANELS ---
        // Hood
        polygons.add(
            Polygon3D(
                listOf(
                    transformChassis(Vector3(-w * 0.85f, hHood * 0.9f, lF * 0.95f)),
                    transformChassis(Vector3(w * 0.85f, hHood * 0.9f, lF * 0.95f)),
                    transformChassis(Vector3(w * 0.88f, hHood, 0.45f)),
                    transformChassis(Vector3(-w * 0.88f, hHood, 0.45f))
                ),
                color = primaryPaint,
                isMetallic = true
            )
        )

        // Muscle Car Hood Scoop
        if (isMuscle) {
            polygons.add(
                Polygon3D(
                    listOf(
                        transformChassis(Vector3(-0.25f, hHood + 0.12f, 1.4f)),
                        transformChassis(Vector3(0.25f, hHood + 0.12f, 1.4f)),
                        transformChassis(Vector3(0.25f, hHood, 0.6f)),
                        transformChassis(Vector3(-0.25f, hHood, 0.6f))
                    ),
                    color = carbonDark,
                    isMetallic = false
                )
            )
        }

        // Front Nose / Splitter
        polygons.add(
            Polygon3D(
                listOf(
                    transformChassis(Vector3(-w * 0.85f, hHood * 0.9f, lF * 0.95f)),
                    transformChassis(Vector3(w * 0.85f, hHood * 0.9f, lF * 0.95f)),
                    transformChassis(Vector3(w * 0.88f, hGround, lF)),
                    transformChassis(Vector3(-w * 0.88f, hGround, lF))
                ),
                color = carbonDark
            )
        )

        // Roof
        polygons.add(
            Polygon3D(
                listOf(
                    transformChassis(Vector3(-w * 0.72f, hRoof, 0.05f)),
                    transformChassis(Vector3(w * 0.72f, hRoof, 0.05f)),
                    transformChassis(Vector3(w * 0.72f, hRoof, -0.95f)),
                    transformChassis(Vector3(-w * 0.72f, hRoof, -0.95f))
                ),
                color = primaryPaint,
                isMetallic = true
            )
        )

        // Windshield
        polygons.add(
            Polygon3D(
                listOf(
                    transformChassis(Vector3(-w * 0.85f, hHood, 0.45f)),
                    transformChassis(Vector3(w * 0.85f, hHood, 0.45f)),
                    transformChassis(Vector3(w * 0.72f, hRoof, 0.05f)),
                    transformChassis(Vector3(-w * 0.72f, hRoof, 0.05f))
                ),
                color = glassColor
            )
        )

        // Rear Window
        polygons.add(
            Polygon3D(
                listOf(
                    transformChassis(Vector3(-w * 0.72f, hRoof, -0.95f)),
                    transformChassis(Vector3(w * 0.72f, hRoof, -0.95f)),
                    transformChassis(Vector3(w * 0.82f, hHood * 0.95f, -lR * 0.8f)),
                    transformChassis(Vector3(-w * 0.82f, hHood * 0.95f, -lR * 0.8f))
                ),
                color = glassColor
            )
        )

        // Trunk / Rear Deck
        polygons.add(
            Polygon3D(
                listOf(
                    transformChassis(Vector3(-w * 0.82f, hHood * 0.95f, -lR * 0.8f)),
                    transformChassis(Vector3(w * 0.82f, hHood * 0.95f, -lR * 0.8f)),
                    transformChassis(Vector3(w * 0.86f, hHood * 0.9f, -lR)),
                    transformChassis(Vector3(-w * 0.86f, hHood * 0.9f, -lR))
                ),
                color = primaryPaint,
                isMetallic = true
            )
        )

        // Sides
        polygons.add(
            Polygon3D(
                listOf(
                    transformChassis(Vector3(-w, hGround, lF * 0.85f)),
                    transformChassis(Vector3(-w, hGround, -lR * 0.85f)),
                    transformChassis(Vector3(-w * 0.9f, hHood, -lR * 0.7f)),
                    transformChassis(Vector3(-w * 0.9f, hHood, lF * 0.7f))
                ),
                color = darkShadow,
                isMetallic = true
            )
        )
        polygons.add(
            Polygon3D(
                listOf(
                    transformChassis(Vector3(w, hGround, lF * 0.85f)),
                    transformChassis(Vector3(w, hGround, -lR * 0.85f)),
                    transformChassis(Vector3(w * 0.9f, hHood, -lR * 0.7f)),
                    transformChassis(Vector3(w * 0.9f, hHood, lF * 0.7f))
                ),
                color = darkShadow,
                isMetallic = true
            )
        )

        // Rear Bumper & Diffuser
        polygons.add(
            Polygon3D(
                listOf(
                    transformChassis(Vector3(-w * 0.86f, hHood * 0.9f, -lR)),
                    transformChassis(Vector3(w * 0.86f, hHood * 0.9f, -lR)),
                    transformChassis(Vector3(w * 0.86f, hGround, -lR)),
                    transformChassis(Vector3(-w * 0.86f, hGround, -lR))
                ),
                color = carbonDark
            )
        )

        // Headlights
        polygons.add(
            Polygon3D(
                listOf(
                    transformChassis(Vector3(-w * 0.78f, hHood * 0.85f, lF * 0.96f)),
                    transformChassis(Vector3(-w * 0.45f, hHood * 0.85f, lF * 0.97f)),
                    transformChassis(Vector3(-w * 0.5f, hHood * 0.72f, lF * 0.98f)),
                    transformChassis(Vector3(-w * 0.82f, hHood * 0.72f, lF * 0.97f))
                ),
                color = headlightColor,
                isEmissive = true
            )
        )
        polygons.add(
            Polygon3D(
                listOf(
                    transformChassis(Vector3(w * 0.45f, hHood * 0.85f, lF * 0.97f)),
                    transformChassis(Vector3(w * 0.78f, hHood * 0.85f, lF * 0.96f)),
                    transformChassis(Vector3(w * 0.82f, hHood * 0.72f, lF * 0.97f)),
                    transformChassis(Vector3(w * 0.5f, hHood * 0.72f, lF * 0.98f))
                ),
                color = headlightColor,
                isEmissive = true
            )
        )

        // Taillights
        polygons.add(
            Polygon3D(
                listOf(
                    transformChassis(Vector3(-w * 0.8f, hHood * 0.82f, -lR - 0.01f)),
                    transformChassis(Vector3(w * 0.8f, hHood * 0.82f, -lR - 0.01f)),
                    transformChassis(Vector3(w * 0.8f, hHood * 0.74f, -lR - 0.01f)),
                    transformChassis(Vector3(-w * 0.8f, hHood * 0.74f, -lR - 0.01f))
                ),
                color = taillightColor,
                isEmissive = true
            )
        )

        // Wing / Spoiler Variations per car model
        val wingY = if (isHypercar) hHood + 0.46f else if (isDrift) hHood + 0.22f else hHood + 0.38f
        val wingZ = -lR * 0.92f

        if (!isTuner) {
            polygons.add(
                Polygon3D(
                    listOf(
                        transformChassis(Vector3(-w * 0.92f, wingY, wingZ)),
                        transformChassis(Vector3(w * 0.92f, wingY, wingZ)),
                        transformChassis(Vector3(w * 0.92f, wingY, wingZ - 0.38f)),
                        transformChassis(Vector3(-w * 0.92f, wingY, wingZ - 0.38f))
                    ),
                    color = carbonDark,
                    doubleSided = true
                )
            )
            // Wing Endplates
            polygons.add(
                Polygon3D(
                    listOf(
                        transformChassis(Vector3(-w * 0.92f, wingY - 0.12f, wingZ + 0.06f)),
                        transformChassis(Vector3(-w * 0.92f, wingY + 0.14f, wingZ + 0.06f)),
                        transformChassis(Vector3(-w * 0.92f, wingY + 0.14f, wingZ - 0.42f)),
                        transformChassis(Vector3(-w * 0.92f, wingY - 0.12f, wingZ - 0.42f))
                    ),
                    color = primaryPaint,
                    doubleSided = true
                )
            )
            polygons.add(
                Polygon3D(
                    listOf(
                        transformChassis(Vector3(w * 0.92f, wingY - 0.12f, wingZ + 0.06f)),
                        transformChassis(Vector3(w * 0.92f, wingY + 0.14f, wingZ + 0.06f)),
                        transformChassis(Vector3(w * 0.92f, wingY + 0.14f, wingZ - 0.42f)),
                        transformChassis(Vector3(w * 0.92f, wingY - 0.12f, wingZ - 0.42f))
                    ),
                    color = primaryPaint,
                    doubleSided = true
                )
            )
        } else {
            // Tuner Roof Spoiler
            polygons.add(
                Polygon3D(
                    listOf(
                        transformChassis(Vector3(-w * 0.72f, hRoof + 0.08f, -0.92f)),
                        transformChassis(Vector3(w * 0.72f, hRoof + 0.08f, -0.92f)),
                        transformChassis(Vector3(w * 0.72f, hRoof + 0.08f, -1.18f)),
                        transformChassis(Vector3(-w * 0.72f, hRoof + 0.08f, -1.18f))
                    ),
                    color = carbonDark,
                    doubleSided = true
                )
            )
        }

        // Hypercar central aerodynamic fin
        if (isHypercar) {
            polygons.add(
                Polygon3D(
                    listOf(
                        transformChassis(Vector3(0f, hRoof, -0.2f)),
                        transformChassis(Vector3(0f, hRoof + 0.22f, -1.2f)),
                        transformChassis(Vector3(0f, wingY, wingZ)),
                        transformChassis(Vector3(0f, hHood, -lR * 0.8f))
                    ),
                    color = carbonDark,
                    doubleSided = true
                )
            )
        }

        // Exhausts
        polygons.add(
            Polygon3D(
                listOf(
                    transformChassis(Vector3(-0.48f, hGround + 0.08f, -lR - 0.02f)),
                    transformChassis(Vector3(-0.32f, hGround + 0.08f, -lR - 0.02f)),
                    transformChassis(Vector3(-0.32f, hGround + 0.02f, -lR - 0.02f)),
                    transformChassis(Vector3(-0.48f, hGround + 0.02f, -lR - 0.02f))
                ),
                color = if (physics.nitroActive) Color(0xFF00E5FF) else Color(0xFF475569),
                isEmissive = physics.nitroActive
            )
        )
        polygons.add(
            Polygon3D(
                listOf(
                    transformChassis(Vector3(0.32f, hGround + 0.08f, -lR - 0.02f)),
                    transformChassis(Vector3(0.48f, hGround + 0.08f, -lR - 0.02f)),
                    transformChassis(Vector3(0.48f, hGround + 0.02f, -lR - 0.02f)),
                    transformChassis(Vector3(0.32f, hGround + 0.02f, -lR - 0.02f))
                ),
                color = if (physics.nitroActive) Color(0xFF00E5FF) else Color(0xFF475569),
                isEmissive = physics.nitroActive
            )
        )

        // --- 2. 4 WHEELS (with distinct rim colors, steering, spinning) ---
        val wheelRadius = 0.34f
        val wheelWidth = if (isDrift || isMuscle) 0.26f else 0.22f
        val wheelOffsets = listOf(
            Vector3(-w, 0.28f, lF * 0.65f) to true,
            Vector3(w, 0.28f, lF * 0.65f) to true,
            Vector3(-w, 0.28f, -lR * 0.68f) to false,
            Vector3(w, 0.28f, -lR * 0.68f) to false
        )

        for ((wheelHubLocal, isFront) in wheelOffsets) {
            val steerAngleWheel = if (isFront) steer else 0f
            val isLeftSide = wheelHubLocal.x < 0f

            fun transformWheelPoint(pt: Vector3): Vector3 {
                var v = pt.rotateX(wheelRotation)
                v = v.rotateY(steerAngleWheel)
                v += wheelHubLocal
                return transformChassis(v)
            }

            val numSegments = 8
            val stepAngle = (Math.PI * 2f / numSegments).toFloat()

            for (i in 0 until numSegments) {
                val a1 = i * stepAngle
                val a2 = (i + 1) * stepAngle

                val y1 = sin(a1) * wheelRadius
                val z1 = cos(a1) * wheelRadius
                val y2 = sin(a2) * wheelRadius
                val z2 = cos(a2) * wheelRadius

                val xInner = if (isLeftSide) wheelWidth * 0.5f else -wheelWidth * 0.5f
                val xOuter = if (isLeftSide) -wheelWidth * 0.5f else wheelWidth * 0.5f

                polygons.add(
                    Polygon3D(
                        listOf(
                            transformWheelPoint(Vector3(xInner, y1, z1)),
                            transformWheelPoint(Vector3(xOuter, y1, z1)),
                            transformWheelPoint(Vector3(xOuter, y2, z2)),
                            transformWheelPoint(Vector3(xInner, y2, z2))
                        ),
                        color = tireColor
                    )
                )

                polygons.add(
                    Polygon3D(
                        listOf(
                            transformWheelPoint(Vector3(xOuter, 0f, 0f)),
                            transformWheelPoint(Vector3(xOuter, y1 * 0.75f, z1 * 0.75f)),
                            transformWheelPoint(Vector3(xOuter, y2 * 0.75f, z2 * 0.75f))
                        ),
                        color = if (i % 2 == 0) rimColor else carbonDark
                    )
                )
            }

            val brakeGlow = if (isBraking) Color(0xFFFF5722) else brakeCaliperColor
            polygons.add(
                Polygon3D(
                    listOf(
                        transformWheelPoint(Vector3(if (isLeftSide) 0.02f else -0.02f, 0.15f, 0.05f)),
                        transformWheelPoint(Vector3(if (isLeftSide) 0.02f else -0.02f, 0.15f, -0.15f)),
                        transformWheelPoint(Vector3(if (isLeftSide) 0.02f else -0.02f, -0.05f, -0.15f)),
                        transformWheelPoint(Vector3(if (isLeftSide) 0.02f else -0.02f, -0.05f, 0.05f))
                    ),
                    color = brakeGlow,
                    isEmissive = isBraking
                )
            )
        }

        // --- 3. COCKPIT INTERIOR DETAILS ---
        if (isCockpitView) {
            polygons.add(
                Polygon3D(
                    listOf(
                        transformChassis(Vector3(-0.6f, hHood + 0.12f, 0.35f)),
                        transformChassis(Vector3(0.6f, hHood + 0.12f, 0.35f)),
                        transformChassis(Vector3(0.6f, hHood - 0.1f, 0.15f)),
                        transformChassis(Vector3(-0.6f, hHood - 0.1f, 0.15f))
                    ),
                    color = carbonDark
                )
            )
            val steerWheelAngle = -steer * 2.8f
            val steerWheelCenter = Vector3(-0.25f, hHood + 0.08f, 0.12f)
            val swRadius = 0.16f
            for (i in 0 until 8) {
                val a1 = i * (Math.PI * 2f / 8f).toFloat() + steerWheelAngle
                val a2 = (i + 1) * (Math.PI * 2f / 8f).toFloat() + steerWheelAngle

                val p1 = steerWheelCenter + Vector3(cos(a1) * swRadius, sin(a1) * swRadius, 0f)
                val p2 = steerWheelCenter + Vector3(cos(a2) * swRadius, sin(a2) * swRadius, 0f)

                polygons.add(
                    Polygon3D(
                        listOf(
                            transformChassis(p1),
                            transformChassis(p2),
                            transformChassis(p2 + Vector3(0f, 0f, 0.02f)),
                            transformChassis(p1 + Vector3(0f, 0f, 0.02f))
                        ),
                        color = if (i == 2 || i == 6) Color(0xFFFF2A4B) else Color(0xFF334155)
                    )
                )
            }
        }

        return polygons
    }
}
