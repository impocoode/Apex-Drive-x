package com.example.game.engine3d

import androidx.compose.ui.graphics.Color
import kotlin.math.*

data class TrackSegment(
    val center: Vector3,
    val normal: Vector3,
    val tangent: Vector3,
    val right: Vector3,
    val width: Float = 14f,
    val hasKerbLeft: Boolean = false,
    val hasKerbRight: Boolean = false,
    val elevation: Float = 0f,
    val distanceAlongTrack: Float = 0f
)

data class TrackObject3D(
    val position: Vector3,
    val type: ObjectType,
    val rotationY: Float = 0f,
    val scale: Vector3 = Vector3(1f, 1f, 1f)
)

enum class ObjectType {
    LIGHT_POLE,
    GRANDSTAND,
    START_GANTRY,
    TREE,
    BARRIER_BLOCK,
    DISTANCE_BOARD_100,
    DISTANCE_BOARD_50,
    TUNNEL_ARCH
}

data class TrackTheme(
    val name: String,
    val id: String,
    val description: String,
    val skyColorTop: Color,
    val skyColorBottom: Color,
    val groundColor: Color,
    val roadColor: Color,
    val kerbColor1: Color,
    val kerbColor2: Color,
    val barrierColor: Color,
    val sunDirection: Vector3,
    val sunColor: Color,
    val fogDensity: Float,
    val nightMode: Boolean = false
)

object TrackThemes {
    val APEX_RING = TrackTheme(
        name = "Apex Ring Grand Prix",
        id = "apex_ring",
        description = "Sirkuit balap Formula kelas dunia dengan tikungan cepat, apex teknis, dan tribun penonton megah di bawah sinar matahari cerah.",
        skyColorTop = Color(0xFF1E3A8A),
        skyColorBottom = Color(0xFF93C5FD),
        groundColor = Color(0xFF1B432A), // Lush grass green
        roadColor = Color(0xFF262A33),
        kerbColor1 = Color(0xFFDC2626), // Racing red
        kerbColor2 = Color(0xFFF8FAFC), // White
        barrierColor = Color(0xFF475569),
        sunDirection = Vector3(0.5f, 0.8f, 0.3f).normalized(),
        sunColor = Color(0xFFFFFBEB),
        fogDensity = 0.0018f,
        nightMode = false
    )

    val NEON_EXPRESSWAY = TrackTheme(
        name = "Neon Cyber Expressway",
        id = "neon_city",
        description = "Jalan layang futuristik melintasi kota metropolis malam hari dengan lampu neon bercahaya dan gedung pencakar langit megah.",
        skyColorTop = Color(0xFF050510),
        skyColorBottom = Color(0xFF180A2E),
        groundColor = Color(0xFF0A0D14),
        roadColor = Color(0xFF181B22),
        kerbColor1 = Color(0xFF00E5FF), // Glowing Cyan
        kerbColor2 = Color(0xFFD946EF), // Glowing Magenta
        barrierColor = Color(0xFF00E5FF),
        sunDirection = Vector3(-0.3f, 0.6f, -0.7f).normalized(),
        sunColor = Color(0xFF67E8F9),
        fogDensity = 0.0012f,
        nightMode = true
    )

    val CANYON_PASS = TrackTheme(
        name = "Canyon Sunset Pass",
        id = "canyon_pass",
        description = "Rute pegunungan berkelok di tebing bebatuan merah saat matahari terbenam dengan pemandangan dramatis.",
        skyColorTop = Color(0xFF4C0519),
        skyColorBottom = Color(0xFFF97316),
        groundColor = Color(0xFF78350F), // Canyon sandstone
        roadColor = Color(0xFF292524),
        kerbColor1 = Color(0xFFEA580C),
        kerbColor2 = Color(0xFFFEF3C7),
        barrierColor = Color(0xFF78716C),
        sunDirection = Vector3(0.8f, 0.25f, 0.5f).normalized(),
        sunColor = Color(0xFFFFEDD5),
        fogDensity = 0.0022f,
        nightMode = false
    )
}

class Track3D(val theme: TrackTheme) {
    val segments = mutableListOf<TrackSegment>()
    val sceneryObjects = mutableListOf<TrackObject3D>()
    var totalTrackLength = 0f

    init {
        generateCircuit()
    }

    private fun generateCircuit() {
        // Generate closed loop race circuit
        val controlPoints = when (theme.id) {
            "neon_city" -> listOf(
                Vector3(0f, 0f, 0f),
                Vector3(20f, 0f, 250f),
                Vector3(80f, 4f, 480f),
                Vector3(220f, 6f, 620f),
                Vector3(380f, 2f, 540f),
                Vector3(420f, 0f, 320f),
                Vector3(340f, 0f, 120f),
                Vector3(200f, 3f, -50f),
                Vector3(60f, 0f, -120f)
            )
            "canyon_pass" -> listOf(
                Vector3(0f, 0f, 0f),
                Vector3(-30f, 8f, 180f),
                Vector3(60f, 16f, 320f),
                Vector3(190f, 22f, 420f),
                Vector3(120f, 15f, 580f),
                Vector3(-40f, 10f, 520f),
                Vector3(-140f, 5f, 360f),
                Vector3(-180f, 0f, 180f),
                Vector3(-80f, -2f, 40f)
            )
            else -> listOf(
                // Apex Ring Grand Prix
                Vector3(0f, 0f, 0f),
                Vector3(0f, 0f, 260f), // Main straight
                Vector3(45f, 2f, 420f), // Turn 1
                Vector3(140f, 4f, 520f), // Turn 2
                Vector3(260f, 2f, 480f), // Chicane entry
                Vector3(300f, 0f, 340f), // Chicane exit
                Vector3(220f, 0f, 200f), // Back curve
                Vector3(150f, 3f, 60f),  // S-Curves
                Vector3(60f, 1f, -40f)   // Final turn
            )
        }

        // Catmull-Rom spline interpolation for silky smooth high-resolution racing track
        val splineSubdivisions = 24
        val n = controlPoints.size
        val points = mutableListOf<Vector3>()

        for (i in 0 until n) {
            val p0 = controlPoints[(i - 1 + n) % n]
            val p1 = controlPoints[i]
            val p2 = controlPoints[(i + 1) % n]
            val p3 = controlPoints[(i + 2) % n]

            for (step in 0 until splineSubdivisions) {
                val t = step.toFloat() / splineSubdivisions
                val t2 = t * t
                val t3 = t2 * t

                // Catmull-Rom formula
                val pt = (p1 * 2f +
                        (p2 - p0) * t +
                        (p0 * 2f - p1 * 5f + p2 * 4f - p3) * t2 +
                        ((p0 * -1f) + p1 * 3f - p2 * 3f + p3) * t3) * 0.5f
                points.add(pt)
            }
        }

        // Build track segments
        var cumulativeDist = 0f
        val numPts = points.size

        for (i in 0 until numPts) {
            val curr = points[i]
            val next = points[(i + 1) % numPts]
            val prev = points[(i - 1 + numPts) % numPts]

            val tangent = (next - prev).normalized()
            val up = Vector3.UP
            val right = tangent.cross(up).normalized()
            val normal = right.cross(tangent).normalized()

            // Calculate curvature for kerb rumble placement
            val turnSharpness = abs(tangent.x * (next.z - curr.z) - tangent.z * (next.x - curr.x))
            val hasKerbLeft = (i in 25..55 || i in 110..140 || i in 170..195)
            val hasKerbRight = (i in 65..95 || i in 145..165)

            val segmentLength = (next - curr).length()
            val seg = TrackSegment(
                center = curr,
                normal = normal,
                tangent = tangent,
                right = right,
                width = 15f,
                hasKerbLeft = hasKerbLeft,
                hasKerbRight = hasKerbRight,
                elevation = curr.y,
                distanceAlongTrack = cumulativeDist
            )
            segments.add(seg)
            cumulativeDist += segmentLength

            // Add Scenery Objects along the track
            if (i == 0) {
                // Start/Finish Gantry
                sceneryObjects.add(
                    TrackObject3D(curr + Vector3(0f, 0f, 0f), ObjectType.START_GANTRY, rotationY = atan2(tangent.x, tangent.z))
                )
            }

            if (i % 6 == 0) {
                // Light poles / streetlamps
                val side = if ((i / 6) % 2 == 0) 1f else -1f
                val polePos = curr + (right * (side * (seg.width * 0.5f + 3.5f)))
                sceneryObjects.add(
                    TrackObject3D(polePos, ObjectType.LIGHT_POLE, rotationY = if (side > 0) 0f else Math.PI.toFloat())
                )
            }

            if (i in listOf(18, 58, 102, 140)) {
                // Distance boards
                val boardPos = curr + (right * (seg.width * 0.5f + 2.5f))
                sceneryObjects.add(
                    TrackObject3D(boardPos, ObjectType.DISTANCE_BOARD_100, rotationY = atan2(tangent.x, tangent.z))
                )
            }

            if (i in listOf(22, 62, 106, 144)) {
                val boardPos = curr + (right * (seg.width * 0.5f + 2.5f))
                sceneryObjects.add(
                    TrackObject3D(boardPos, ObjectType.DISTANCE_BOARD_50, rotationY = atan2(tangent.x, tangent.z))
                )
            }

            if (i in listOf(5, 12, 50, 85, 130, 180) && theme.id == "apex_ring") {
                // Grandstands for spectators
                val grandstandPos = curr + (right * (seg.width * 0.5f + 14f))
                sceneryObjects.add(
                    TrackObject3D(grandstandPos, ObjectType.GRANDSTAND, rotationY = atan2(tangent.x, tangent.z) - 1.57f)
                )
            }

            if (theme.id == "canyon_pass" && i % 10 == 0) {
                // Trees / pines
                val side = if (i % 20 == 0) 1f else -1f
                val treePos = curr + (right * (side * (seg.width * 0.5f + 9f + (i % 5))))
                sceneryObjects.add(TrackObject3D(treePos, ObjectType.TREE))
            }

            if (theme.id == "neon_city" && i in 70..95 && i % 4 == 0) {
                // Neon Tunnel arches
                sceneryObjects.add(
                    TrackObject3D(curr, ObjectType.TUNNEL_ARCH, rotationY = atan2(tangent.x, tangent.z))
                )
            }
        }

        totalTrackLength = cumulativeDist
    }

    fun getClosestSegmentIndex(pos: Vector3): Int {
        var closestIdx = 0
        var minDistSq = Float.MAX_VALUE
        for (i in segments.indices) {
            val seg = segments[i]
            val dx = pos.x - seg.center.x
            val dz = pos.z - seg.center.z
            val dSq = dx * dx + dz * dz
            if (dSq < minDistSq) {
                minDistSq = dSq
                closestIdx = i
            }
        }
        return closestIdx
    }

    /**
     * Checks car interaction with track (grip, curbs, off-road penalty, barrier collision)
     */
    fun checkSurface(pos: Vector3): SurfaceStatus {
        val idx = getClosestSegmentIndex(pos)
        val seg = segments[idx]
        val rel = pos - seg.center
        val lateralDist = rel.dot(seg.right)
        val halfWidth = seg.width * 0.5f

        val absLat = abs(lateralDist)
        return when {
            absLat <= halfWidth -> {
                SurfaceStatus(
                    surfaceGrip = 1.0f,
                    surfaceDrag = 1.0f,
                    onCurbs = false,
                    isOffRoad = false,
                    lateralDist = lateralDist,
                    segmentIndex = idx
                )
            }
            absLat <= halfWidth + 1.8f -> {
                // Rumble curb strip
                SurfaceStatus(
                    surfaceGrip = 0.88f,
                    surfaceDrag = 1.25f,
                    onCurbs = true,
                    isOffRoad = false,
                    lateralDist = lateralDist,
                    segmentIndex = idx
                )
            }
            absLat <= halfWidth + 12f -> {
                // Off-road grass or gravel runoff
                SurfaceStatus(
                    surfaceGrip = 0.45f,
                    surfaceDrag = 4.0f,
                    onCurbs = false,
                    isOffRoad = true,
                    lateralDist = lateralDist,
                    segmentIndex = idx
                )
            }
            else -> {
                // Barrier collision
                SurfaceStatus(
                    surfaceGrip = 0.3f,
                    surfaceDrag = 8.0f,
                    onCurbs = false,
                    isOffRoad = true,
                    collidedWithBarrier = true,
                    lateralDist = lateralDist,
                    segmentIndex = idx
                )
            }
        }
    }
}

data class SurfaceStatus(
    val surfaceGrip: Float,
    val surfaceDrag: Float,
    val onCurbs: Boolean,
    val isOffRoad: Boolean,
    val collidedWithBarrier: Boolean = false,
    val lateralDist: Float = 0f,
    val segmentIndex: Int = 0
)
