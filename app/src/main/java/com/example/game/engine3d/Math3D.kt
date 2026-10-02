package com.example.game.engine3d

import androidx.compose.ui.graphics.Color
import kotlin.math.*

data class Vector3(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f
) {
    operator fun plus(other: Vector3) = Vector3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vector3) = Vector3(x - other.x, y - other.y, z - other.z)
    operator fun unaryMinus() = Vector3(-x, -y, -z)
    operator fun times(scalar: Float) = Vector3(x * scalar, y * scalar, z * scalar)
    operator fun div(scalar: Float) = Vector3(x / scalar, y / scalar, z / scalar)

    fun dot(other: Vector3): Float = x * other.x + y * other.y + z * other.z

    fun cross(other: Vector3) = Vector3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x
    )

    fun length(): Float = sqrt(x * x + y * y + z * z)
    fun lengthSquared(): Float = x * x + y * y + z * z

    fun normalized(): Vector3 {
        val len = length()
        return if (len > 0.0001f) Vector3(x / len, y / len, z / len) else Vector3(0f, 0f, 1f)
    }

    fun rotateY(angleRad: Float): Vector3 {
        val cosA = cos(angleRad)
        val sinA = sin(angleRad)
        return Vector3(
            x * cosA + z * sinA,
            y,
            -x * sinA + z * cosA
        )
    }

    fun rotateX(angleRad: Float): Vector3 {
        val cosA = cos(angleRad)
        val sinA = sin(angleRad)
        return Vector3(
            x,
            y * cosA - z * sinA,
            y * sinA + z * cosA
        )
    }

    fun rotateZ(angleRad: Float): Vector3 {
        val cosA = cos(angleRad)
        val sinA = sin(angleRad)
        return Vector3(
            x * cosA - y * sinA,
            x * sinA + y * cosA,
            z
        )
    }

    fun copyTo(other: Vector3) {
        other.x = x
        other.y = y
        other.z = z
    }

    companion object {
        val ZERO get() = Vector3(0f, 0f, 0f)
        val UP get() = Vector3(0f, 1f, 0f)
        val FORWARD get() = Vector3(0f, 0f, 1f)
        val RIGHT get() = Vector3(1f, 0f, 0f)

        fun lerp(a: Vector3, b: Vector3, t: Float): Vector3 {
            val clampedT = t.coerceIn(0f, 1f)
            return Vector3(
                a.x + (b.x - a.x) * clampedT,
                a.y + (b.y - a.y) * clampedT,
                a.z + (b.z - a.z) * clampedT
            )
        }
    }
}

data class Vector2(var x: Float = 0f, var y: Float = 0f) {
    operator fun plus(other: Vector2) = Vector2(x + other.x, y + other.y)
    operator fun minus(other: Vector2) = Vector2(x - other.x, y - other.y)
    operator fun times(scalar: Float) = Vector2(x * scalar, y * scalar)
    fun length(): Float = sqrt(x * x + y * y)
}

data class ProjectedPoint(
    val sx: Float,
    val sy: Float,
    val depth: Float,
    val visible: Boolean
)

data class Polygon3D(
    val vertices: List<Vector3>,
    val color: Color,
    val isEmissive: Boolean = false,
    val isMetallic: Boolean = false,
    val doubleSided: Boolean = false,
    val wireframe: Boolean = false,
    var depth: Float = 0f
)

enum class CameraMode(val title: String) {
    CHASE("Chase Cam"),
    COCKPIT("Cockpit (1st Person)"),
    HOOD("Hood / Bumper"),
    ORBIT("Orbit Showcase")
}

class Camera3D(
    var position: Vector3 = Vector3(0f, 3.5f, -8f),
    var target: Vector3 = Vector3(0f, 1f, 0f),
    var up: Vector3 = Vector3(0f, 1f, 0f),
    var fov: Float = 60f,
    var mode: CameraMode = CameraMode.CHASE
) {
    var shakeAmount: Float = 0f

    fun getForward(): Vector3 = (target - position).normalized()
    fun getRight(): Vector3 = getForward().cross(up).normalized()
    fun getActualUp(): Vector3 = getRight().cross(getForward()).normalized()

    fun project(
        worldPos: Vector3,
        screenWidth: Float,
        screenHeight: Float,
        nearPlane: Float = 0.5f,
        farPlane: Float = 800f
    ): ProjectedPoint {
        // Transform worldPos to camera local space
        val forward = getForward()
        val right = getRight()
        val camUp = getActualUp()

        val rel = worldPos - position
        val zCam = rel.dot(forward)

        if (zCam < nearPlane || zCam > farPlane) {
            return ProjectedPoint(0f, 0f, zCam, false)
        }

        val xCam = rel.dot(right)
        val yCam = rel.dot(camUp)

        val aspect = screenWidth / screenHeight
        val fovRad = Math.toRadians(fov.toDouble()).toFloat()
        val tanHalfFov = tan(fovRad / 2f)

        // Perspective divide
        val xNorm = xCam / (zCam * tanHalfFov * aspect)
        val yNorm = yCam / (zCam * tanHalfFov)

        val sx = (xNorm + 1f) * 0.5f * screenWidth
        val sy = (1f - yNorm) * 0.5f * screenHeight

        return ProjectedPoint(sx, sy, zCam, true)
    }
}
