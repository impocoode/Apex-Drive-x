package com.example.game.physics

import com.example.game.engine3d.Vector3
import kotlin.math.*

class VehiclePhysics(
    val specs: CarSpecs
) {
    // Kinematics & Transform
    var position = Vector3(0f, 0.45f, 0f)
    var velocity = Vector3(0f, 0f, 0f) // World space velocity
    var heading = 0f // Yaw in radians (0 = pointing along +Z)
    var angularVelocity = 0f // Yaw rotation rate

    var pitch = 0f // Suspension pitch: dive under brake, squat under accel
    var roll = 0f  // Body roll: lean outward during hard cornering
    var steerAngle = 0f // Current front wheel steering angle

    // Drivetrain
    var currentGear = 1 // 1..6, -1 is Reverse, 0 is Neutral
    var isAutomatic = true
    var engineRpm = specs.idleRpm
    var clutchEngaged = 1f
    var shiftTimer = 0f

    // Controls
    var throttleInput = 0f
    var brakeInput = 0f
    var steerInput = 0f
    var handbrake = false
    var nitroActive = false
    var nitroFuel = 100f

    // Tuning & Upgrades multipliers
    var engineBonusHp = 0
    var tireGripMultiplier = 1.0f
    var aeroDownforceMultiplier = 1.0f

    // Assists
    var absEnabled = true
    var tcsEnabled = true
    var espEnabled = true

    // Telemetry & Dynamics
    var speedMs = 0f
    var speedKmh = 0f
    var lateralG = 0f
    var longitudinalG = 0f
    var driftAngle = 0f // Difference between heading and velocity vector
    var isDrifting = false
    var driftPoints = 0f
    var driftMultiplier = 1.0f

    var wheelSlipRear = 0f
    var wheelSlipFront = 0f
    var turboBoost = 0f // in PSI (0 to 22)
    var rpmLimiterBounce = false

    // Surface interaction
    var currentSurfaceGrip = 1.0f
    var currentSurfaceDrag = 1.0f
    var onCurbs = false
    var isOffRoad = false

    // Physical constants
    private val wheelRadius = 0.34f // meters
    private val wheelBase = 2.7f // meters
    private val airDensity = 1.225f
    private val frontalArea = 2.1f
    private val gravity = 9.81f

    fun reset(startPos: Vector3 = Vector3(0f, 0.45f, 0f), startHeading: Float = 0f) {
        position = startPos
        velocity = Vector3(0f, 0f, 0f)
        heading = startHeading
        angularVelocity = 0f
        pitch = 0f
        roll = 0f
        steerAngle = 0f
        currentGear = 1
        engineRpm = specs.idleRpm
        shiftTimer = 0f
        throttleInput = 0f
        brakeInput = 0f
        steerInput = 0f
        handbrake = false
        nitroActive = false
        nitroFuel = 100f
        speedMs = 0f
        speedKmh = 0f
        driftPoints = 0f
        driftMultiplier = 1.0f
        isDrifting = false
    }

    fun update(dt: Float) {
        val safeDt = dt.coerceIn(0.001f, 0.05f)

        // 1. Steering dynamics (speed-sensitive steering)
        val maxSteerAngle = Math.toRadians(34.0).toFloat()
        val speedSensitivity = (1f - (speedKmh / 320f).coerceIn(0f, 0.65f))
        val targetSteer = steerInput * maxSteerAngle * speedSensitivity
        steerAngle += (targetSteer - steerAngle) * (18f * safeDt)

        // 2. Gearbox & Clutch
        if (shiftTimer > 0f) {
            shiftTimer -= safeDt
            clutchEngaged = (1f - (shiftTimer / 0.14f)).coerceIn(0f, 1f)
        } else {
            clutchEngaged = 1f
        }

        // Automatic Transmission logic
        if (isAutomatic && shiftTimer <= 0f) {
            if (currentGear in 1..5 && engineRpm > specs.maxRpm * 0.94f && throttleInput > 0.4f) {
                // Upshift
                currentGear++
                shiftTimer = 0.14f
            } else if (currentGear > 1 && (engineRpm < specs.maxRpm * 0.42f || (throttleInput > 0.85f && engineRpm < specs.maxRpm * 0.6f && speedKmh < 160f))) {
                // Downshift / Kickdown
                currentGear--
                shiftTimer = 0.14f
            }
        }

        // 3. Local Velocity computation
        val forwardDir = Vector3(sin(heading), 0f, cos(heading))
        val rightDir = Vector3(cos(heading), 0f, -sin(heading))

        val forwardSpeed = velocity.dot(forwardDir)
        val lateralSpeed = velocity.dot(rightDir)
        speedMs = velocity.length()
        speedKmh = speedMs * 3.6f

        // 4. Engine Torque & RPM simulation
        val gearRatio = if (currentGear in 1..6) specs.gearRatios[currentGear - 1] else if (currentGear == -1) specs.gearRatios[6] else 0f
        val totalDriveRatio = gearRatio * specs.finalDrive

        val wheelRpm = (forwardSpeed / (2f * Math.PI.toFloat() * wheelRadius)) * 60f
        val targetEngineRpm = abs(wheelRpm * totalDriveRatio)

        if (clutchEngaged > 0.8f && abs(forwardSpeed) > 1.5f && currentGear != 0) {
            engineRpm += (targetEngineRpm - engineRpm) * (14f * safeDt)
        } else {
            // Free revving when idling or slipping
            val revTarget = if (throttleInput > 0.05f) {
                specs.idleRpm + (specs.maxRpm - specs.idleRpm) * throttleInput * (if (nitroActive) 1.08f else 1f)
            } else {
                specs.idleRpm
            }
            engineRpm += (revTarget - engineRpm) * (8f * safeDt)
        }

        // Rev limiter bounce
        if (engineRpm >= specs.maxRpm) {
            engineRpm = specs.maxRpm - 180f * (sin(System.currentTimeMillis() * 0.04f).toFloat() + 1f)
            rpmLimiterBounce = true
        } else {
            rpmLimiterBounce = false
        }
        engineRpm = engineRpm.coerceIn(specs.idleRpm, specs.maxRpm + 100f)

        // Turbo boost calculation
        val targetBoost = if (throttleInput > 0.5f && engineRpm > 3200f) {
            ((engineRpm - 3200f) / (specs.maxRpm - 3200f)) * 22f * (if (nitroActive) 1.25f else 1f)
        } else 0f
        turboBoost += (targetBoost - turboBoost) * (6f * safeDt)

        // 5. Driving & Braking Forces
        // Torque curve (peak torque around 60% of RPM range)
        val normRpm = (engineRpm / specs.maxRpm).coerceIn(0.1f, 1.1f)
        val torqueFactor = (sin(normRpm * Math.PI.toFloat() * 0.85f)).coerceIn(0.35f, 1.05f)
        val totalHp = specs.horsepower + engineBonusHp
        val baseEngineTorque = (totalHp * 745.7f) / (specs.maxRpm * (2f * Math.PI.toFloat() / 60f)) * 1.6f
        var driveTorque = baseEngineTorque * torqueFactor * throttleInput * clutchEngaged

        // Nitro Boost
        if (nitroActive && nitroFuel > 0f) {
            driveTorque *= 1.45f
            nitroFuel = (nitroFuel - 18f * safeDt).coerceAtLeast(0f)
            if (nitroFuel <= 0f) nitroActive = false
        }

        // Traction control system (TCS) prevents complete burnout
        if (tcsEnabled && !handbrake && wheelSlipRear > 0.35f) {
            driveTorque *= (1f - (wheelSlipRear - 0.35f) * 1.5f).coerceIn(0.2f, 1f)
        }

        var driveForce = (driveTorque * totalDriveRatio) / wheelRadius

        // Aerodynamic Drag & Downforce
        val effectiveAeroCoeff = specs.downforceCoeff * aeroDownforceMultiplier
        val aeroDrag = 0.5f * airDensity * (specs.dragCoeff + effectiveAeroCoeff * 0.05f) * frontalArea * forwardSpeed * forwardSpeed * sign(forwardSpeed)
        val rollingResistance = 180f * currentSurfaceDrag * sign(forwardSpeed) * (if (abs(forwardSpeed) > 0.1f) 1f else 0f)
        val downforce = 0.5f * airDensity * effectiveAeroCoeff * frontalArea * forwardSpeed * forwardSpeed

        // Braking with ABS
        var brakeForce = brakeInput * specs.brakeForce
        if (absEnabled && brakeInput > 0.8f && speedKmh > 30f) {
            // ABS cadence pulse
            val absPulse = if ((System.currentTimeMillis() / 70) % 2 == 0L) 1.0f else 0.75f
            brakeForce *= absPulse
        }

        if (handbrake) {
            brakeForce += specs.brakeForce * 0.9f
        }

        // Total longitudinal force
        val netDriveForce = if (currentGear >= 1) {
            driveForce - (brakeForce * sign(forwardSpeed)) - aeroDrag - rollingResistance
        } else if (currentGear == -1) {
            -driveForce - (brakeForce * sign(forwardSpeed)) - aeroDrag - rollingResistance
        } else {
            -(brakeForce * sign(forwardSpeed)) - aeroDrag - rollingResistance
        }

        // 6. Lateral Tire Forces & Drift Mechanics
        // Slip angle calculation
        val frontSlipAngle = if (abs(forwardSpeed) > 0.5f) {
            atan2(lateralSpeed + angularVelocity * (wheelBase * 0.5f), abs(forwardSpeed)) - steerAngle
        } else 0f

        val rearSlipAngle = if (abs(forwardSpeed) > 0.5f) {
            atan2(lateralSpeed - angularVelocity * (wheelBase * 0.5f), abs(forwardSpeed))
        } else 0f

        val effectiveGrip = specs.tireGrip * tireGripMultiplier * currentSurfaceGrip * (1f + downforce / (specs.massKg * gravity))

        // Lateral forces (Pacejka-like response)
        val corneringStiffness = 38000f * effectiveGrip
        var frontLateralForce = (-corneringStiffness * frontSlipAngle).coerceIn(-specs.massKg * gravity * effectiveGrip * 0.6f, specs.massKg * gravity * effectiveGrip * 0.6f)
        var rearLateralForce = (-corneringStiffness * rearSlipAngle).coerceIn(-specs.massKg * gravity * effectiveGrip * 0.55f, specs.massKg * gravity * effectiveGrip * 0.55f)

        // Handbrake or overpowered throttle initiates drift
        if (handbrake) {
            rearLateralForce *= 0.22f
            wheelSlipRear = 0.85f
        } else if (specs.driveType == DriveType.RWD && throttleInput > 0.7f && abs(rearSlipAngle) > 0.12f) {
            // Power oversteer!
            rearLateralForce *= 0.65f
            wheelSlipRear = 0.65f
        } else {
            wheelSlipRear = (abs(rearSlipAngle) / 0.35f).coerceIn(0f, 1f)
        }
        wheelSlipFront = (abs(frontSlipAngle) / 0.35f).coerceIn(0f, 1f)

        // ESP stability correction
        if (espEnabled && !handbrake && abs(rearSlipAngle) > 0.38f) {
            rearLateralForce *= 1.25f
        }

        // 7. Vehicle Acceleration and Integration
        val accelForward = netDriveForce / specs.massKg
        val accelLateral = (frontLateralForce + rearLateralForce) / specs.massKg

        // Angular acceleration (Yaw torque)
        val yawTorque = (frontLateralForce * (wheelBase * 0.5f) * cos(steerAngle)) - (rearLateralForce * (wheelBase * 0.5f))
        val momentOfInertia = specs.massKg * (wheelBase * wheelBase) / 12f
        val angularAccel = (yawTorque / momentOfInertia) - (angularVelocity * 4.5f) // Angular damping

        angularVelocity += angularAccel * safeDt
        heading += angularVelocity * safeDt

        // World velocity update
        val newForwardVel = forwardSpeed + accelForward * safeDt
        val newLateralVel = lateralSpeed + accelLateral * safeDt

        velocity = (forwardDir * newForwardVel) + (rightDir * newLateralVel)

        // Minimal crawl stop
        if (throttleInput < 0.05f && brakeInput < 0.05f && velocity.length() < 0.2f) {
            velocity = Vector3(0f, 0f, 0f)
            angularVelocity = 0f
        }

        // Update position
        position += velocity * safeDt

        // 8. Suspension Pitch & Roll Dynamics
        longitudinalG = (accelForward / gravity).coerceIn(-1.8f, 1.8f)
        lateralG = (accelLateral / gravity).coerceIn(-2.2f, 2.2f)

        val targetPitch = (-longitudinalG * 0.045f).coerceIn(-0.08f, 0.06f)
        pitch += (targetPitch - pitch) * (14f * safeDt)

        val targetRoll = (lateralG * 0.055f).coerceIn(-0.1f, 0.1f)
        roll += (targetRoll - roll) * (14f * safeDt)

        // 9. Drift Scoring Engine
        if (speedKmh > 35f && abs(forwardSpeed) > 5f) {
            driftAngle = Math.toDegrees(atan2(lateralSpeed.toDouble(), forwardSpeed.toDouble()).absoluteValue).toFloat()
            if (driftAngle > 14f) {
                isDrifting = true
                val driftRate = driftAngle * (speedKmh / 60f) * 1.2f
                driftMultiplier = (driftMultiplier + safeDt * 0.4f).coerceAtMost(5.0f)
                driftPoints += driftRate * driftMultiplier * safeDt
            } else {
                isDrifting = false
                driftMultiplier = (driftMultiplier - safeDt * 1.5f).coerceAtLeast(1.0f)
            }
        } else {
            isDrifting = false
            driftAngle = 0f
            driftMultiplier = 1.0f
        }
    }

    fun shiftUp() {
        if (currentGear in 1..5) {
            currentGear++
            shiftTimer = 0.14f
        }
    }

    fun shiftDown() {
        if (currentGear > 1) {
            currentGear--
            shiftTimer = 0.14f
        }
    }

    fun toggleReverse() {
        if (speedKmh < 5f) {
            currentGear = if (currentGear == -1) 1 else -1
            shiftTimer = 0.2f
        }
    }

    fun activateNitro() {
        if (nitroFuel > 10f) {
            nitroActive = true
        }
    }

    fun stopNitro() {
        nitroActive = false
    }
}
