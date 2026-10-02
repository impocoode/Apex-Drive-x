package com.example.game.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.audio.CarAudioEngine
import com.example.game.data.GamePreferences
import com.example.game.engine3d.*
import com.example.game.physics.CarDatabase
import com.example.game.physics.CarSpecs
import com.example.game.physics.DriveType
import com.example.game.physics.VehiclePhysics
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

@Composable
fun GarageScreen(
    prefs: GamePreferences,
    onBack: () -> Unit,
    onSelectAndPlay: (CarSpecs) -> Unit
) {
    var selectedIndex by remember {
        val currId = prefs.selectedCarId
        val idx = CarDatabase.ALL_CARS.indexOfFirst { it.id == currId }
        mutableIntStateOf(if (idx >= 0) idx else 0)
    }

    val car = CarDatabase.ALL_CARS[selectedIndex]
    var customColor by remember(car) {
        mutableStateOf(prefs.getCustomColor(car.id) ?: car.defaultColor)
    }

    var turntableYaw by remember { mutableFloatStateOf(0.55f) }
    var autoRotate by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Karakteristik, 1: Modifikasi & Tuning

    // Tuning upgrades states
    var engineStage by remember(car) { mutableIntStateOf(prefs.getEngineStage(car.id)) }
    var tireCompound by remember(car) { mutableIntStateOf(prefs.getTireCompound(car.id)) }
    var aeroLevel by remember(car) { mutableIntStateOf(prefs.getAeroLevel(car.id)) }

    // Calculated modified attributes
    val bonusHp = engineStage * 35
    val effectiveHp = car.horsepower + bonusHp
    val effectiveTopSpeed = car.topSpeedKmh + (engineStage * 8) - (aeroLevel * 2)
    val effective0To100 = max(1.8f, car.accel0To100Sec - (engineStage * 0.12f) - (if (tireCompound == 1) 0.15f else 0f))
    val effectiveHandling = (car.handlingRating + (aeroLevel * 0.35f) + (if (tireCompound == 1) 0.45f else 0f)).coerceAtMost(10f)
    val effectiveDrift = (car.driftRating + (if (tireCompound == 2) 0.8f else 0f)).coerceAtMost(10f)

    // Virtual physics for 3D preview
    val previewPhysics = remember(car) {
        VehiclePhysics(car).apply {
            position = Vector3(0f, 0.45f, 0f)
            heading = turntableYaw
        }
    }
    val previewModel = remember(car, customColor) {
        CarModel3D(car, bodyColor = customColor)
    }
    val previewCamera = remember {
        Camera3D(
            position = Vector3(0f, 2.1f, -5.6f),
            target = Vector3(0f, 0.55f, 0f),
            fov = 55f
        )
    }

    // Sound engine for "Tes Suara Mesin (Rev Engine)"
    val audioEngine = remember { CarAudioEngine() }
    var isRevvingEngine by remember { mutableStateOf(false) }
    var revvedRpm by remember { mutableFloatStateOf(car.idleRpm) }

    DisposableEffect(Unit) {
        audioEngine.start()
        onDispose {
            audioEngine.stop()
        }
    }

    // Auto rotate showroom turntable
    LaunchedEffect(autoRotate) {
        while (autoRotate) {
            turntableYaw += 0.008f
            delay(16)
        }
    }

    // Engine rev simulation
    LaunchedEffect(isRevvingEngine) {
        if (isRevvingEngine) {
            audioEngine.targetThrottle = 1.0f
            while (isRevvingEngine) {
                revvedRpm = (revvedRpm + 1400f).coerceAtMost(car.maxRpm - 200f)
                audioEngine.targetRpm = revvedRpm
                delay(30)
            }
        } else {
            audioEngine.targetThrottle = 0.0f
            while (revvedRpm > car.idleRpm) {
                revvedRpm = (revvedRpm - 900f).coerceAtLeast(car.idleRpm)
                audioEngine.targetRpm = revvedRpm
                delay(30)
            }
        }
    }

    LaunchedEffect(turntableYaw) {
        previewPhysics.heading = turntableYaw
    }

    Scaffold(
        containerColor = DarkBackground,
        contentWindowInsets = WindowInsets(0.dp)
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // LEFT PANEL: 3D Turntable Showroom (Rotatable 360°)
            Box(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            autoRotate = false
                            turntableYaw += dragAmount.x * 0.012f
                        }
                    }
            ) {
                // 3D Canvas
                Canvas(modifier = Modifier.fillMaxSize().testTag("garage_showroom_canvas")) {
                    val w = size.width
                    val h = size.height

                    // Radial luxury lighting floor
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF1E293B), DarkBackground),
                            center = Offset(w * 0.5f, h * 0.65f),
                            radius = w * 0.75f
                        )
                    )

                    // Circular stage platform with metallic neon rim
                    val podiumCenter = Offset(w * 0.5f, h * 0.76f)
                    drawOval(
                        color = Color(0xFF0F172A),
                        topLeft = Offset(podiumCenter.x - w * 0.44f, podiumCenter.y - 48f),
                        size = androidx.compose.ui.geometry.Size(w * 0.88f, 96f)
                    )
                    drawOval(
                        color = RacingCyan.copy(alpha = 0.4f),
                        topLeft = Offset(podiumCenter.x - w * 0.44f, podiumCenter.y - 48f),
                        size = androidx.compose.ui.geometry.Size(w * 0.88f, 96f),
                        style = Stroke(width = 3.5f)
                    )

                    // Render 3D Car Polygons
                    val polygons = previewModel.generatePolygons(previewPhysics)
                    val sorted = polygons.sortedByDescending { poly ->
                        poly.vertices.map { (it - previewCamera.position).length() }.average()
                    }

                    val scratchPath = Path()
                    val sunDir = Vector3(0.6f, 0.85f, -0.5f).normalized()

                    for (poly in sorted) {
                        scratchPath.reset()
                        var anyVisible = false
                        for (i in poly.vertices.indices) {
                            val p = previewCamera.project(poly.vertices[i], w, h)
                            if (p.visible) anyVisible = true
                            if (i == 0) scratchPath.moveTo(p.sx, p.sy) else scratchPath.lineTo(p.sx, p.sy)
                        }
                        scratchPath.close()

                        if (anyVisible) {
                            val v0 = poly.vertices[0]
                            val v1 = poly.vertices[1]
                            val v2 = poly.vertices[2]
                            val normal = (v1 - v0).cross(v2 - v0).normalized()
                            val diffuse = max(0f, normal.dot(sunDir))
                            val light = (0.42f + diffuse * 0.65f).coerceIn(0.2f, 1.25f)

                            val col = if (poly.isEmissive) poly.color else Color(
                                (poly.color.red * light).coerceIn(0f, 1f),
                                (poly.color.green * light).coerceIn(0f, 1f),
                                (poly.color.blue * light).coerceIn(0f, 1f),
                                poly.color.alpha
                            )
                            drawPath(scratchPath, color = col)
                        }
                    }
                }

                // Top Left: Back & Title Bar
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .testTag("garage_back_button")
                            .background(Color(0x99000000), CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "GARASI & PILIH MOBIL",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = RacingCyan,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            "Pilih mobil balap dengan karakter performa unik",
                            fontSize = 11.sp,
                            color = Color.LightGray
                        )
                    }
                }

                // Top Right Tools: Auto-Rotate & Rev Engine
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Auto-Rotate Button
                    IconButton(
                        onClick = { autoRotate = !autoRotate },
                        modifier = Modifier
                            .background(if (autoRotate) RacingCyan else Color(0x99000000), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.RotateRight,
                            contentDescription = "Auto Rotate",
                            tint = if (autoRotate) Color.Black else Color.White
                        )
                    }

                    // Rev Engine Sound Button
                    Box(
                        modifier = Modifier
                            .background(if (isRevvingEngine) RacingRed else Color(0xCC0F172A), RoundedCornerShape(20.dp))
                            .border(1.5.dp, if (isRevvingEngine) RacingYellow else Color(0xFF334155), RoundedCornerShape(20.dp))
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        isRevvingEngine = true
                                        tryAwaitRelease()
                                        isRevvingEngine = false
                                    }
                                )
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (isRevvingEngine) "${revvedRpm.toInt()} RPM" else "TES MESIN",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }
                    }
                }

                // Bottom Left: Color Palette Selector
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xCC0F172A)),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("WARNA:", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White)
                        Spacer(Modifier.width(8.dp))
                        car.availableColors.forEach { col ->
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .padding(2.dp)
                                    .clip(CircleShape)
                                    .background(col)
                                    .border(
                                        width = if (customColor == col) 2.5.dp else 1.dp,
                                        color = if (customColor == col) RacingCyan else Color.DarkGray,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        customColor = col
                                        prefs.saveCustomColor(car.id, col)
                                    }
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                    }
                }

                // Car Index Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(14.dp)
                        .background(Color(0x99000000), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        "${selectedIndex + 1} / ${CarDatabase.ALL_CARS.size}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.LightGray
                    )
                }
            }

            // RIGHT PANEL: Car Selector & Performance Comparison
            Column(
                modifier = Modifier
                    .weight(1.05f)
                    .fillMaxHeight()
                    .background(DarkSurface)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                // Horizontal Car Selector Carousel
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(CarDatabase.ALL_CARS) { idx, itemCar ->
                        val isSelected = (idx == selectedIndex)
                        Surface(
                            onClick = {
                                selectedIndex = idx
                                prefs.selectedCarId = itemCar.id
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) RacingCyan else Color(0xFF1E293B),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, Color.White) else null,
                            modifier = Modifier.testTag("car_select_${itemCar.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    itemCar.name.split(" ").take(2).joinToString(" "),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.Black else Color.White
                                )
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .background(if (isSelected) Color.Black else Color(0xFF0F172A), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        "${itemCar.topSpeedKmh}",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (isSelected) RacingCyan else RacingYellow
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Car Name, Class Badge & Description Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(car.name, fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color.White)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(car.category.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Black, color = RacingYellow)
                            Text(" • ", color = Color.Gray, fontSize = 10.sp)
                            Text("DRIVETRAIN: ${car.driveType}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = RacingCyan)
                        }
                    }

                    // Power to Weight Ratio Pill
                    val hpPerTon = ((effectiveHp.toFloat() / car.massKg) * 1000f).toInt()
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$hpPerTon", fontSize = 13.sp, fontWeight = FontWeight.Black, color = RacingRed)
                            Text("HP / TON", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // PROMINENT KEY ATTRIBUTES: TOP SPEED & ACCELERATION HIGHLIGHT CARDS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Top Speed Metric Card
                    AttributeHighlightCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Speed,
                        label = "KECEPATAN PUNCAK",
                        value = "$effectiveTopSpeed",
                        unit = "KM/JAM",
                        highlightColor = RacingCyan,
                        badge = if (effectiveTopSpeed > car.topSpeedKmh) "+${effectiveTopSpeed - car.topSpeedKmh} KM/H" else null
                    )

                    // 0-100 Acceleration Metric Card
                    AttributeHighlightCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Bolt,
                        label = "AKSELERASI 0-100",
                        value = String.format("%.2f", effective0To100),
                        unit = "DETIK",
                        highlightColor = RacingYellow,
                        badge = if (effective0To100 < car.accel0To100Sec) String.format("-%.2fs", car.accel0To100Sec - effective0To100) else null
                    )
                }

                Spacer(Modifier.height(6.dp))

                // TAB NAVIGATION: 0: Karakteristik, 1: Modifikasi & Tuning
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFF0F172A),
                    contentColor = RacingCyan,
                    modifier = Modifier.height(34.dp)
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("KOMPARASI ATRIBUT", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("UPGRADE & TUNING", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                }

                Spacer(Modifier.height(6.dp))

                if (selectedTab == 0) {
                    // COMPARATIVE ATTRIBUTE PROGRESS BARS
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.SpaceEvenly
                    ) {
                        AttributeBar("TENAGA MESIN", "$effectiveHp HP", effectiveHp / 1100f, RacingRed)
                        AttributeBar("KECEPATAN PUNCAK", "$effectiveTopSpeed KM/H", effectiveTopSpeed / 420f, RacingCyan)
                        AttributeBar("AKSELERASI 0-100", "${String.format("%.2f", effective0To100)}s", (4.2f - effective0To100) / 2.5f, RacingYellow)
                        AttributeBar("CORNERING GRIP", "${String.format("%.1f", effectiveHandling)} / 10", effectiveHandling / 10f, RacingGreen)
                        AttributeBar("DRIFT CAPABILITY", "${String.format("%.1f", effectiveDrift)} / 10", effectiveDrift / 10f, RacingOrange)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("BOBOT: ${car.massKg.toInt()} KG", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.LightGray)
                            Text("DRIVETRAIN: ${car.driveType}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = RacingCyan)
                            Text("MAX RPM: ${car.maxRpm.toInt()}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = RacingYellow)
                        }
                    }
                } else {
                    // TUNING & UPGRADES CONTROLS
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Engine Stage Upgrade
                        TuningUpgradeRow(
                            title = "MODIFIKASI MESIN & TURBO",
                            options = listOf("Standar", "Stage 1 (+35 HP)", "Stage 2 (+70 HP)"),
                            selectedIndex = engineStage,
                            onSelect = {
                                engineStage = it
                                prefs.saveEngineStage(car.id, it)
                            }
                        )

                        // Tire Compound
                        TuningUpgradeRow(
                            title = "KOMPON BAN",
                            options = listOf("Sport", "Semi-Slick (+Grip)", "Drift Hard (+Slide)"),
                            selectedIndex = tireCompound,
                            onSelect = {
                                tireCompound = it
                                prefs.saveTireCompound(car.id, it)
                            }
                        )

                        // Aero Downforce Package
                        TuningUpgradeRow(
                            title = "PAKET AERODINAMIKA",
                            options = listOf("Low Drag (Top Speed)", "Seimbang", "High Downforce"),
                            selectedIndex = aeroLevel,
                            onSelect = {
                                aeroLevel = it
                                prefs.saveAeroLevel(car.id, it)
                            }
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Bottom Action Buttons: Save & Race
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            prefs.selectedCarId = car.id
                            onBack()
                        },
                        modifier = Modifier
                            .weight(0.85f)
                            .height(44.dp)
                            .testTag("garage_save_button"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF475569)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("PILIH MOBIL", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            prefs.selectedCarId = car.id
                            onSelectAndPlay(car)
                        },
                        modifier = Modifier
                            .weight(1.15f)
                            .height(44.dp)
                            .testTag("garage_race_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = RacingCyan),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("GAS BALAPAN!", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.Black)
                    }
                }
            }
        }
    }
}

@Composable
private fun AttributeHighlightCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    unit: String,
    highlightColor: Color,
    badge: String? = null
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = highlightColor, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(label, fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color.LightGray)
                }
                if (badge != null) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF065F46), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(badge, fontSize = 8.sp, fontWeight = FontWeight.Black, color = RacingGreen)
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    unit,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = highlightColor,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun AttributeBar(label: String, value: String, progress: Float, color: Color) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.LightGray)
            Text(value, fontSize = 10.sp, fontWeight = FontWeight.Black, color = color)
        }
        Spacer(Modifier.height(2.dp))
        LinearProgressIndicator(
            progress = { progress.coerceIn(0.05f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = Color(0xFF1E293B)
        )
    }
}

@Composable
private fun TuningUpgradeRow(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(title, fontSize = 10.sp, fontWeight = FontWeight.Black, color = RacingCyan)
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                options.forEachIndexed { idx, opt ->
                    val isOptSelected = (idx == selectedIndex)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (isOptSelected) Color(0xFF1E293B) else Color.Transparent,
                                RoundedCornerShape(6.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isOptSelected) RacingCyan else Color(0xFF334155),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { onSelect(idx) }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            opt,
                            fontSize = 9.sp,
                            fontWeight = if (isOptSelected) FontWeight.Black else FontWeight.Normal,
                            color = if (isOptSelected) Color.White else Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
