package com.example.game.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.audio.CarAudioEngine
import com.example.game.data.ControlType
import com.example.game.data.GamePreferences
import com.example.game.engine3d.*
import com.example.game.physics.CarDatabase
import com.example.game.physics.CarSpecs
import com.example.game.physics.VehiclePhysics
import com.example.ui.theme.*
import kotlinx.coroutines.isActive
import kotlin.math.*

@Composable
fun RacingGameScreen(
    prefs: GamePreferences,
    trackTheme: TrackTheme,
    carSpecs: CarSpecs,
    onExitToMenu: () -> Unit,
    onOpenGarage: () -> Unit
) {
    val context = LocalContext.current

    // Audio Engine
    val audioEngine = remember { CarAudioEngine() }
    DisposableEffect(Unit) {
        audioEngine.isMuted = prefs.audioMuted
        audioEngine.start()
        onDispose {
            audioEngine.stop()
        }
    }

    // Vibrator for haptics
    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun vibrate(durationMs: Long, amplitude: Int = 180) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (_: Exception) {}
    }

    // Game Core State
    val track = remember(trackTheme) { Track3D(trackTheme) }
    val physics = remember(carSpecs) {
        val engineStage = prefs.getEngineStage(carSpecs.id)
        val tireCompound = prefs.getTireCompound(carSpecs.id)
        val aeroLevel = prefs.getAeroLevel(carSpecs.id)

        VehiclePhysics(carSpecs).apply {
            absEnabled = prefs.absEnabled
            tcsEnabled = prefs.tcsEnabled
            espEnabled = prefs.espEnabled
            isAutomatic = prefs.autoTransmission
            engineBonusHp = engineStage * 35
            tireGripMultiplier = if (tireCompound == 1) 1.15f else if (tireCompound == 2) 0.90f else 1.0f
            aeroDownforceMultiplier = when (aeroLevel) {
                0 -> 0.75f
                2 -> 1.35f
                else -> 1.0f
            }
            reset(
                startPos = Vector3(0f, 0.45f, 5f),
                startHeading = 0f
            )
        }
    }

    val carColor = remember(carSpecs) {
        prefs.getCustomColor(carSpecs.id) ?: carSpecs.defaultColor
    }
    val carModel = remember(carSpecs, carColor) {
        CarModel3D(carSpecs, bodyColor = carColor)
    }
    val renderer = remember { Renderer3D() }
    val particleSystem = remember { ParticleSystem() }
    val camera = remember { Camera3D() }

    // Race Timing & Telemetry
    var currentLapTime by remember { mutableFloatStateOf(0f) }
    var bestLapTime by remember { mutableFloatStateOf(prefs.getBestLapTime(trackTheme.id)) }
    var lapCount by remember { mutableIntStateOf(1) }
    var isPaused by remember { mutableStateOf(false) }
    var showLapCelebration by remember { mutableStateOf(false) }
    var lastLapSummaryTime by remember { mutableFloatStateOf(0f) }

    // Controls input states
    var leftPressed by remember { mutableStateOf(false) }
    var rightPressed by remember { mutableStateOf(false) }
    var gasPressed by remember { mutableStateOf(false) }
    var brakePressed by remember { mutableStateOf(false) }
    var handbrakePressed by remember { mutableStateOf(false) }
    var steeringWheelAngle by remember { mutableFloatStateOf(0f) }
    var tiltSteering by remember { mutableFloatStateOf(0f) }

    // Accelerometer Sensor for Tilt Steering
    DisposableEffect(prefs.controlType) {
        if (prefs.controlType == ControlType.TILT_ACCELEROMETER) {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    event?.let {
                        // In landscape, Y axis measures lateral phone tilt
                        val tiltY = it.values[1]
                        tiltSteering = (tiltY / 4.5f).coerceIn(-1f, 1f)
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sensorManager?.registerListener(listener, accel, SensorManager.SENSOR_DELAY_GAME)
            onDispose {
                sensorManager?.unregisterListener(listener)
            }
        } else {
            onDispose {}
        }
    }

    BackHandler {
        isPaused = !isPaused
    }

    // MAIN 60 FPS GAME LOOP
    var lastNanoTime by remember { mutableLongStateOf(0L) }
    var hasCrossedHalfway by remember { mutableStateOf(false) }

    LaunchedEffect(isPaused) {
        while (isActive && !isPaused) {
            withFrameNanos { now ->
                if (lastNanoTime == 0L) {
                    lastNanoTime = now
                    return@withFrameNanos
                }
                val dt = ((now - lastNanoTime) / 1_000_000_000f).coerceIn(0.001f, 0.035f)
                lastNanoTime = now

                // 1. Process Steering Input
                when (prefs.controlType) {
                    ControlType.BUTTONS -> {
                        val targetSteer = when {
                            leftPressed && !rightPressed -> -1f
                            rightPressed && !leftPressed -> 1f
                            else -> 0f
                        }
                        physics.steerInput += (targetSteer - physics.steerInput) * (14f * dt)
                    }
                    ControlType.STEERING_WHEEL -> {
                        physics.steerInput = (steeringWheelAngle / 85f).coerceIn(-1f, 1f)
                    }
                    ControlType.TILT_ACCELEROMETER -> {
                        physics.steerInput = tiltSteering
                    }
                }

                // 2. Throttle & Brake Input
                physics.throttleInput = if (gasPressed) 1f else 0f
                physics.brakeInput = if (brakePressed) 1f else 0f
                physics.handbrake = handbrakePressed

                // 3. Update Vehicle Physics
                physics.update(dt)

                // 4. Track Surface interaction & Barrier collision
                val surface = track.checkSurface(physics.position)
                physics.currentSurfaceGrip = surface.surfaceGrip
                physics.currentSurfaceDrag = surface.surfaceDrag
                physics.onCurbs = surface.onCurbs
                physics.isOffRoad = surface.isOffRoad

                if (surface.onCurbs) {
                    camera.shakeAmount = 0.18f
                    vibrate(25, 120)
                }

                if (surface.collidedWithBarrier) {
                    // Barrier bounce & spark
                    val seg = track.segments[surface.segmentIndex]
                    val normalPush = if (surface.lateralDist > 0) -seg.right else seg.right
                    physics.velocity = (physics.velocity * -0.45f) + (normalPush * 6f)
                    physics.angularVelocity *= -0.5f
                    particleSystem.emitSparks(physics.position)
                    camera.shakeAmount = 0.55f
                    vibrate(80, 240)
                }

                // 5. Particles generation
                val carForward = Vector3(sin(physics.heading), 0f, cos(physics.heading))
                val carRight = Vector3(cos(physics.heading), 0f, -sin(physics.heading))

                if (physics.isDrifting || (physics.wheelSlipRear > 0.45f && physics.speedKmh > 20f)) {
                    val rearLeft = physics.position - (carForward * 1.5f) - (carRight * 0.85f)
                    val rearRight = physics.position - (carForward * 1.5f) + (carRight * 0.85f)
                    particleSystem.emitTireSmoke(rearLeft, physics.velocity)
                    particleSystem.emitTireSmoke(rearRight, physics.velocity)

                    // Add skid marks
                    particleSystem.addSkidMark(rearLeft, rearLeft - (physics.velocity * dt))
                    particleSystem.addSkidMark(rearRight, rearRight - (physics.velocity * dt))
                }

                if (physics.nitroActive) {
                    val exhaustL = physics.position - (carForward * 2.1f) - (carRight * 0.4f)
                    val exhaustR = physics.position - (carForward * 2.1f) + (carRight * 0.4f)
                    particleSystem.emitNitroFlame(exhaustL, physics.heading)
                    particleSystem.emitNitroFlame(exhaustR, physics.heading)
                    camera.shakeAmount = 0.12f
                }

                particleSystem.update(dt)
                carModel.updateAnimation(physics.speedMs, dt)

                // 6. Camera Follow System
                val speedRatio = (physics.speedKmh / 320f).coerceIn(0f, 1f)
                when (camera.mode) {
                    CameraMode.CHASE -> {
                        val chaseDist = 6.5f + (speedRatio * 1.8f) + (if (physics.nitroActive) 1.2f else 0f)
                        val chaseHeight = 2.4f - (speedRatio * 0.4f)
                        val targetCamPos = physics.position - (carForward * chaseDist) + Vector3(0f, chaseHeight, 0f)
                        camera.position = Vector3.lerp(camera.position, targetCamPos, 9f * dt)
                        camera.target = physics.position + (carForward * 3.5f) + Vector3(0f, 0.7f, 0f)
                        camera.fov = 58f + speedRatio * 16f
                    }
                    CameraMode.COCKPIT -> {
                        val cockpitEye = physics.position + (carForward * 0.25f) + Vector3(0f, 0.78f, 0f) - (carRight * 0.22f)
                        camera.position = cockpitEye
                        camera.target = cockpitEye + (carForward * 25f) - (carRight * (physics.steerAngle * 4f))
                        camera.fov = 70f + speedRatio * 10f
                    }
                    CameraMode.HOOD -> {
                        val hoodPos = physics.position + (carForward * 1.9f) + Vector3(0f, 0.55f, 0f)
                        camera.position = hoodPos
                        camera.target = hoodPos + (carForward * 30f)
                        camera.fov = 68f + speedRatio * 12f
                    }
                    CameraMode.ORBIT -> {
                        val orbitAngle = (now / 1_000_000_000.0 * 0.5).toFloat()
                        camera.position = physics.position + Vector3(sin(orbitAngle) * 7.5f, 2.5f, cos(orbitAngle) * 7.5f)
                        camera.target = physics.position + Vector3(0f, 0.6f, 0f)
                        camera.fov = 55f
                    }
                }
                camera.shakeAmount = (camera.shakeAmount - dt * 2.5f).coerceAtLeast(0f)

                // 7. Audio Engine Sync
                audioEngine.targetRpm = physics.engineRpm
                audioEngine.targetThrottle = physics.throttleInput
                audioEngine.targetTireSlip = max(physics.wheelSlipRear, if (physics.isDrifting) 0.7f else 0f)
                audioEngine.targetTurboBoost = physics.turboBoost
                audioEngine.isRedlining = physics.rpmLimiterBounce

                // 8. Lap Timing & Finish Line Detection
                currentLapTime += dt
                val segIdx = surface.segmentIndex
                val totalSegs = track.segments.size
                if (segIdx > totalSegs / 2) {
                    hasCrossedHalfway = true
                }
                if (hasCrossedHalfway && segIdx in 0..6) {
                    // Completed a lap!
                    hasCrossedHalfway = false
                    lastLapSummaryTime = currentLapTime
                    showLapCelebration = true
                    if (bestLapTime <= 0f || currentLapTime < bestLapTime) {
                        bestLapTime = currentLapTime
                        prefs.saveBestLapTime(trackTheme.id, currentLapTime)
                    }
                    lapCount++
                    currentLapTime = 0f
                    vibrate(120, 255)
                }

                // Drift high score sync
                if (physics.driftPoints > 0) {
                    prefs.saveBestDriftScore(trackTheme.id, physics.driftPoints.toInt())
                }
            }
        }
    }

    // Dismiss lap celebration after 3 seconds
    LaunchedEffect(showLapCelebration) {
        if (showLapCelebration) {
            kotlinx.coroutines.delay(3200)
            showLapCelebration = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // --- 1. 3D RENDERING CANVAS ---
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("game_3d_canvas")
        ) {
            renderer.render(
                drawScope = this,
                camera = camera,
                track = track,
                physics = physics,
                carModel = carModel,
                particleSystem = particleSystem
            )
        }

        // --- 2. TOP BAR HUD (Timing, Mini-map, Camera Toggle, Pause) ---
        TopBarHud(
            currentLapTime = currentLapTime,
            bestLapTime = bestLapTime,
            lapCount = lapCount,
            track = track,
            carPos = physics.position,
            carHeading = physics.heading,
            currentCameraMode = camera.mode,
            onToggleCamera = {
                camera.mode = when (camera.mode) {
                    CameraMode.CHASE -> CameraMode.COCKPIT
                    CameraMode.COCKPIT -> CameraMode.HOOD
                    CameraMode.HOOD -> CameraMode.ORBIT
                    CameraMode.ORBIT -> CameraMode.CHASE
                }
            },
            isMuted = audioEngine.isMuted,
            onToggleMute = {
                val newMuted = !audioEngine.isMuted
                audioEngine.isMuted = newMuted
                prefs.audioMuted = newMuted
            },
            onPause = { isPaused = true }
        )

        // --- 3. DRIFT POINTS POPUP NOTIFICATION ---
        if (physics.isDrifting || physics.driftPoints > 50) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 65.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xCC0F172A)),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(2.dp, RacingYellow)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.FlashOn, contentDescription = null, tint = RacingYellow)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "+${physics.driftPoints.toInt()} DRIFT",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "x${String.format("%.1f", physics.driftMultiplier)}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = RacingYellow
                        )
                    }
                }
            }
        }

        // --- 4. SURFACE OFF-ROAD / CURB ALERTS ---
        if (physics.isOffRoad) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 120.dp),
                color = RacingRed.copy(alpha = 0.85f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    "PERINGATAN: KELUAR JALUR (OFF-ROAD PENALTY)",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }
        }

        // --- 5. DASHBOARD INSTRUMENT CLUSTER (Gauges, RPM, Speed, Gear, G-Force) ---
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        ) {
            RealisticDashboardCluster(physics = physics)
        }

        // --- 6. DRIVING CONTROLS (Steering, Pedals, Handbrake, Nitro) ---
        DrivingControlsOverlay(
            prefs = prefs,
            physics = physics,
            leftPressed = leftPressed,
            onLeftChange = { leftPressed = it },
            rightPressed = rightPressed,
            onRightChange = { rightPressed = it },
            gasPressed = gasPressed,
            onGasChange = { gasPressed = it },
            brakePressed = brakePressed,
            onBrakeChange = { brakePressed = it },
            handbrakePressed = handbrakePressed,
            onHandbrakeChange = { handbrakePressed = it },
            wheelAngle = steeringWheelAngle,
            onWheelAngleChange = { steeringWheelAngle = it },
            onShiftUp = { physics.shiftUp() },
            onShiftDown = { physics.shiftDown() },
            onNitro = {
                physics.activateNitro()
                vibrate(40, 200)
            }
        )

        // --- 7. LAP CELEBRATION BANNER ---
        AnimatedVisibility(
            visible = showLapCelebration,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xEE090D16)),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(2.dp, RacingCyan)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "LAP SELESAI!",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = RacingYellow
                    )
                    Spacer(Modifier.height(8.dp))
                    val m = (lastLapSummaryTime / 60).toInt()
                    val s = lastLapSummaryTime % 60
                    Text(
                        "WAKTU: ${String.format("%02d:%05.2f", m, s)}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    if (bestLapTime == lastLapSummaryTime) {
                        Text(
                            "★ REKOR TERCEPAT BARU! ★",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = RacingCyan,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        // --- 8. PAUSE DIALOG MENU ---
        if (isPaused) {
            PauseMenuDialog(
                carName = carSpecs.name,
                trackName = trackTheme.name,
                onResume = { isPaused = false },
                onRestart = {
                    physics.reset(Vector3(0f, 0.45f, 5f), 0f)
                    currentLapTime = 0f
                    isPaused = false
                },
                onExit = onExitToMenu,
                onGarage = onOpenGarage
            )
        }
    }
}

@Composable
private fun TopBarHud(
    currentLapTime: Float,
    bestLapTime: Float,
    lapCount: Int,
    track: Track3D,
    carPos: Vector3,
    carHeading: Float,
    currentCameraMode: CameraMode,
    onToggleCamera: () -> Unit,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onPause: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        // Left: Lap timer & Best lap
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xCC0F172A)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.border(1.dp, Color(0xFF334155), RoundedCornerShape(10.dp))
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("LAP $lapCount", fontSize = 11.sp, fontWeight = FontWeight.Black, color = RacingYellow)
                    Spacer(Modifier.width(8.dp))
                    val m = (currentLapTime / 60).toInt()
                    val s = currentLapTime % 60
                    Text(
                        String.format("%02d:%05.2f", m, s),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White
                    )
                }
                if (bestLapTime > 0f) {
                    val bm = (bestLapTime / 60).toInt()
                    val bs = bestLapTime % 60
                    Text(
                        "BEST: ${String.format("%02d:%05.2f", bm, bs)}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = RacingCyan
                    )
                }
            }
        }

        // Center: Mini-Map of circuit
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xCC090D16)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .size(75.dp)
                .border(1.dp, Color(0xFF334155), RoundedCornerShape(10.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                val w = size.width
                val h = size.height

                // Normalize track points to mini map
                var minX = Float.MAX_VALUE
                var maxX = -Float.MAX_VALUE
                var minZ = Float.MAX_VALUE
                var maxZ = -Float.MAX_VALUE
                for (seg in track.segments) {
                    if (seg.center.x < minX) minX = seg.center.x
                    if (seg.center.x > maxX) maxX = seg.center.x
                    if (seg.center.z < minZ) minZ = seg.center.z
                    if (seg.center.z > maxZ) maxZ = seg.center.z
                }
                val rangeX = max(1f, maxX - minX)
                val rangeZ = max(1f, maxZ - minZ)

                val mapPath = Path()
                for (i in track.segments.indices) {
                    val seg = track.segments[i]
                    val mx = ((seg.center.x - minX) / rangeX) * w
                    val my = ((seg.center.z - minZ) / rangeZ) * h
                    if (i == 0) mapPath.moveTo(mx, my) else mapPath.lineTo(mx, my)
                }
                mapPath.close()

                drawPath(mapPath, color = Color(0xFF64748B), style = Stroke(width = 2.5f))

                // Player position dot
                val px = ((carPos.x - minX) / rangeX) * w
                val py = ((carPos.z - minZ) / rangeZ) * h
                drawCircle(color = RacingCyan, radius = 4f, center = Offset(px, py))
            }
        }

        // Right: Action buttons (Camera, Audio, Pause)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onToggleCamera,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xCC0F172A)),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
            ) {
                Icon(Icons.Default.Videocam, contentDescription = null, tint = RacingCyan, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(currentCameraMode.title.split(" ")[0], fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            IconButton(
                onClick = onToggleMute,
                modifier = Modifier
                    .background(Color(0xCC0F172A), CircleShape)
                    .border(1.dp, Color(0xFF334155), CircleShape)
            ) {
                Icon(
                    if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = "Mute",
                    tint = if (isMuted) Color.Gray else RacingCyan
                )
            }

            IconButton(
                onClick = onPause,
                modifier = Modifier
                    .background(Color(0xCC0F172A), CircleShape)
                    .border(1.dp, Color(0xFF334155), CircleShape)
                    .testTag("pause_game_button")
            ) {
                Icon(Icons.Default.Pause, contentDescription = "Pause", tint = Color.White)
            }
        }
    }
}

@Composable
private fun RealisticDashboardCluster(physics: VehiclePhysics) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xDE0B111E)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
            .padding(horizontal = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Speedometer & Gear display
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val speed = physics.speedKmh.toInt()
                Text(
                    "$speed",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White
                )
                Text(
                    "KM/H",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = RacingCyan,
                    letterSpacing = 1.sp
                )
            }

            Spacer(Modifier.width(16.dp))

            // Current Gear pill
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                    .border(2.dp, if (physics.rpmLimiterBounce) RacingRed else RacingCyan, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                val gearText = when (physics.currentGear) {
                    -1 -> "R"
                    0 -> "N"
                    else -> "${physics.currentGear}"
                }
                Text(
                    gearText,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = if (physics.rpmLimiterBounce) RacingRed else Color.White
                )
            }

            Spacer(Modifier.width(16.dp))

            // Circular Tachometer RPM Needle & Boost Bar
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(modifier = Modifier.size(68.dp)) {
                    val w = size.width
                    val h = size.height
                    val center = Offset(w * 0.5f, h * 0.5f)
                    val radius = w * 0.42f

                    // Base Arc (220 degrees from 160 deg to 380 deg)
                    drawArc(
                        color = Color(0xFF1E293B),
                        startAngle = 145f,
                        sweepAngle = 250f,
                        useCenter = false,
                        style = Stroke(width = 7f)
                    )

                    // Redline section
                    drawArc(
                        color = RacingRed,
                        startAngle = 330f,
                        sweepAngle = 65f,
                        useCenter = false,
                        style = Stroke(width = 7f)
                    )

                    // Active RPM sweep
                    val normRpm = (physics.engineRpm / physics.specs.maxRpm).coerceIn(0f, 1f)
                    val activeSweep = normRpm * 250f
                    val arcColor = if (normRpm > 0.88f) RacingRed else RacingCyan

                    drawArc(
                        color = arcColor,
                        startAngle = 145f,
                        sweepAngle = activeSweep,
                        useCenter = false,
                        style = Stroke(width = 7f)
                    )

                    // Needle
                    val needleAngleRad = Math.toRadians((145f + activeSweep).toDouble()).toFloat()
                    val needleEnd = center + Offset(cos(needleAngleRad) * radius, sin(needleAngleRad) * radius)
                    drawLine(
                        color = if (physics.rpmLimiterBounce) RacingRed else Color.White,
                        start = center,
                        end = needleEnd,
                        strokeWidth = 3f
                    )
                    drawCircle(color = Color.White, radius = 3.5f, center = center)
                }

                Text(
                    "${physics.engineRpm.toInt()} RPM",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = if (physics.rpmLimiterBounce) RacingRed else Color.LightGray
                )
            }

            Spacer(Modifier.width(14.dp))

            // Boost & Assists LEDs
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "BOOST: ${String.format("%.1f", physics.turboBoost)} PSI",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = RacingYellow
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    LedPill("ABS", physics.absEnabled, activeGlow = physics.brakeInput > 0.8f)
                    LedPill("TCS", physics.tcsEnabled, activeGlow = physics.wheelSlipRear > 0.4f)
                    LedPill("ESP", physics.espEnabled, activeGlow = physics.isDrifting)
                }
            }
        }
    }
}

@Composable
private fun LedPill(label: String, enabled: Boolean, activeGlow: Boolean) {
    val bgColor = when {
        activeGlow -> RacingYellow
        enabled -> Color(0xFF065F46)
        else -> Color(0xFF1E293B)
    }
    val textColor = when {
        activeGlow -> Color.Black
        enabled -> RacingGreen
        else -> Color.Gray
    }
    Box(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Black, color = textColor)
    }
}

@Composable
private fun DrivingControlsOverlay(
    prefs: GamePreferences,
    physics: VehiclePhysics,
    leftPressed: Boolean,
    onLeftChange: (Boolean) -> Unit,
    rightPressed: Boolean,
    onRightChange: (Boolean) -> Unit,
    gasPressed: Boolean,
    onGasChange: (Boolean) -> Unit,
    brakePressed: Boolean,
    onBrakeChange: (Boolean) -> Unit,
    handbrakePressed: Boolean,
    onHandbrakeChange: (Boolean) -> Unit,
    wheelAngle: Float,
    onWheelAngleChange: (Float) -> Unit,
    onShiftUp: () -> Unit,
    onShiftDown: () -> Unit,
    onNitro: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // LEFT SIDE: STEERING INTERFACE
        when (prefs.controlType) {
            ControlType.BUTTONS -> {
                Row(
                    modifier = Modifier.align(Alignment.BottomStart),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ControlButton(
                        icon = Icons.Default.ArrowBack,
                        isPressed = leftPressed,
                        onPress = { onLeftChange(true) },
                        onRelease = { onLeftChange(false) },
                        tag = "steer_left_button"
                    )
                    ControlButton(
                        icon = Icons.Default.ArrowForward,
                        isPressed = rightPressed,
                        onPress = { onRightChange(true) },
                        onRelease = { onRightChange(false) },
                        tag = "steer_right_button"
                    )
                }
            }
            ControlType.STEERING_WHEEL -> {
                // Interactive 3D/realistic Steering Wheel
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .size(130.dp)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = { onWheelAngleChange(0f) },
                                onDragCancel = { onWheelAngleChange(0f) },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val newAngle = (wheelAngle + dragAmount.x * 0.9f).coerceIn(-90f, 90f)
                                    onWheelAngleChange(newAngle)
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .rotate(wheelAngle)
                    ) {
                        val w = size.width
                        val h = size.height
                        val c = Offset(w * 0.5f, h * 0.5f)
                        val r = w * 0.44f

                        // Outer Wheel Rim
                        drawCircle(color = Color(0xFF1E293B), radius = r, center = c, style = Stroke(width = 16f))
                        drawCircle(color = RacingCyan, radius = r, center = c, style = Stroke(width = 2.5f))

                        // Center hub & spokes
                        drawCircle(color = Color(0xFF0F172A), radius = r * 0.38f, center = c)
                        drawCircle(color = RacingRed, radius = 5f, center = c)

                        // 3 Steering Spokes
                        drawLine(color = Color(0xFF475569), start = c, end = Offset(c.x - r, c.y), strokeWidth = 8f)
                        drawLine(color = Color(0xFF475569), start = c, end = Offset(c.x + r, c.y), strokeWidth = 8f)
                        drawLine(color = Color(0xFF475569), start = c, end = Offset(c.x, c.y + r), strokeWidth = 8f)

                        // 12 o'clock center alignment strip
                        drawRect(
                            color = RacingYellow,
                            topLeft = Offset(c.x - 4f, c.y - r - 8f),
                            size = androidx.compose.ui.geometry.Size(8f, 16f)
                        )
                    }
                }
            }
            ControlType.TILT_ACCELEROMETER -> {
                // Gyro meter indicator
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .background(Color(0x990F172A), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.ScreenRotation, contentDescription = null, tint = RacingCyan)
                    Text("TILT TO STEER", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        // RIGHT SIDE: PEDALS (Gas, Brake), HANDBRAKE & NITRO
        Row(
            modifier = Modifier.align(Alignment.BottomEnd),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // NITRO BUTTON
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Nitro Fuel gauge bar
                LinearProgressIndicator(
                    progress = { physics.nitroFuel / 100f },
                    modifier = Modifier
                        .width(48.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = RacingCyan,
                    trackColor = Color(0xFF1E293B)
                )
                Spacer(Modifier.height(4.dp))
                IconButton(
                    onClick = onNitro,
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            brush = Brush.radialGradient(
                                if (physics.nitroActive) listOf(RacingCyan, Color(0xFF005B66))
                                else listOf(Color(0xFF00838F), Color(0xFF00363A))
                            ),
                            shape = CircleShape
                        )
                        .border(2.dp, RacingCyan, CircleShape)
                        .testTag("nitro_button")
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = "Nitro", tint = Color.White)
                }
            }

            // HANDBRAKE BUTTON
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(if (handbrakePressed) RacingRed else Color(0xCC331B24), CircleShape)
                    .border(2.dp, RacingRed, CircleShape)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                onHandbrakeChange(true)
                                tryAwaitRelease()
                                onHandbrakeChange(false)
                            }
                        )
                    }
                    .testTag("handbrake_button"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "DRIFT\n(P)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            // BRAKE PEDAL
            Pedal(
                label = "BRAKE",
                isPressed = brakePressed,
                color = RacingRed,
                width = 58.dp,
                height = 84.dp,
                onPressChange = onBrakeChange,
                tag = "brake_pedal"
            )

            // THROTTLE GAS PEDAL
            Pedal(
                label = "GAS",
                isPressed = gasPressed,
                color = RacingCyan,
                width = 54.dp,
                height = 105.dp,
                onPressChange = onGasChange,
                tag = "gas_pedal"
            )
        }

        // MANUAL GEAR SHIFT PADDLES (if manual mode)
        if (!physics.isAutomatic) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    onClick = onShiftUp,
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xCC0F172A),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RacingCyan)
                ) {
                    Text("+ SHIFT", modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), fontSize = 11.sp, fontWeight = FontWeight.Black, color = RacingCyan)
                }
                Surface(
                    onClick = onShiftDown,
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xCC0F172A),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RacingRed)
                ) {
                    Text("- SHIFT", modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), fontSize = 11.sp, fontWeight = FontWeight.Black, color = RacingRed)
                }
            }
        }
    }
}

@Composable
private fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPressed: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    tag: String
) {
    Box(
        modifier = Modifier
            .size(68.dp)
            .background(if (isPressed) RacingCyan else Color(0xBB1E293B), RoundedCornerShape(18.dp))
            .border(2.dp, if (isPressed) Color.White else Color(0xFF475569), RoundedCornerShape(18.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onPress()
                        tryAwaitRelease()
                        onRelease()
                    }
                )
            }
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (isPressed) Color.Black else Color.White,
            modifier = Modifier.size(34.dp)
        )
    }
}

@Composable
private fun Pedal(
    label: String,
    isPressed: Boolean,
    color: Color,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    onPressChange: (Boolean) -> Unit,
    tag: String
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .background(
                if (isPressed) color else Color(0xDD181E29),
                RoundedCornerShape(12.dp)
            )
            .border(2.dp, if (isPressed) Color.White else color, RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onPressChange(true)
                        tryAwaitRelease()
                        onPressChange(false)
                    }
                )
            }
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Metal grip treads
            for (i in 0 until 4) {
                Box(
                    modifier = Modifier
                        .width(width * 0.65f)
                        .height(3.dp)
                        .background(if (isPressed) Color.Black.copy(alpha = 0.4f) else Color(0xFF334155))
                )
                Spacer(Modifier.height(5.dp))
            }
            Text(
                label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = if (isPressed) Color.Black else Color.White
            )
        }
    }
}

@Composable
private fun PauseMenuDialog(
    carName: String,
    trackName: String,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onExit: () -> Unit,
    onGarage: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .width(360.dp)
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(2.dp, RacingCyan)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "GAME DIJEDA",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 1.5.sp
                )
                Text(
                    "$carName • $trackName",
                    fontSize = 11.sp,
                    color = RacingYellow,
                    modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
                )

                Button(
                    onClick = onResume,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RacingCyan),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("LANJUTKAN BALAPAN", fontWeight = FontWeight.Black, color = Color.Black)
                }

                Spacer(Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onRestart,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("MULAI ULANG LAP", fontWeight = FontWeight.Bold, color = Color.White)
                }

                Spacer(Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onGarage,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("KE GARAGE & TUNING", fontWeight = FontWeight.Bold, color = RacingYellow)
                }

                Spacer(Modifier.height(10.dp))

                TextButton(
                    onClick = onExit,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("KEMBALI KE MENU UTAMA", fontWeight = FontWeight.Bold, color = RacingRed)
                }
            }
        }
    }
}
