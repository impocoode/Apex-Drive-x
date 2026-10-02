package com.example.game.physics

import androidx.compose.ui.graphics.Color

data class CarSpecs(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val horsepower: Int,
    val maxRpm: Float = 8500f,
    val idleRpm: Float = 950f,
    val massKg: Float,
    val topSpeedKmh: Int,
    val accel0To100Sec: Float,
    val handlingRating: Float, // 1 to 10
    val driftRating: Float, // 1 to 10
    val driveType: DriveType,
    val gearRatios: FloatArray, // 1st to 6th + reverse
    val finalDrive: Float,
    val dragCoeff: Float,
    val downforceCoeff: Float,
    val brakeForce: Float,
    val tireGrip: Float,
    val defaultColor: Color,
    val availableColors: List<Color>,
    val soundProfile: CarSoundProfile = CarSoundProfile.V6_TURBO
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CarSpecs) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

enum class DriveType {
    RWD, AWD, FWD
}

enum class CarSoundProfile {
    V6_TURBO,
    ROTARY_DRIFT,
    HYBRID_V8,
    V8_MUSCLE,
    INLINE4_TURBO
}

object CarDatabase {
    val APEX_GTR = CarSpecs(
        id = "apex_gtr",
        name = "Apex GT-R Concept",
        category = "Supercar / Track",
        description = "Supercar serba bisa bermesin Twin-Turbo V6 dengan sistem All-Wheel Drive dan downforce tinggi untuk melibas tikungan dengan presisi mutlak.",
        horsepower = 650,
        maxRpm = 8600f,
        idleRpm = 1000f,
        massKg = 1420f,
        topSpeedKmh = 345,
        accel0To100Sec = 2.7f,
        handlingRating = 9.2f,
        driftRating = 7.4f,
        driveType = DriveType.AWD,
        gearRatios = floatArrayOf(3.82f, 2.36f, 1.68f, 1.31f, 1.05f, 0.85f, -3.55f),
        finalDrive = 3.70f,
        dragCoeff = 0.31f,
        downforceCoeff = 0.85f,
        brakeForce = 18000f,
        tireGrip = 1.35f,
        defaultColor = Color(0xFFE53935), // Racing Crimson
        availableColors = listOf(
            Color(0xFFE53935), // Racing Crimson
            Color(0xFF00E5FF), // Cyber Cyan
            Color(0xFF1E293B), // Midnight Slate
            Color(0xFFFFD600), // Apex Gold
            Color(0xFFF8FAFC)  // Pearl White
        ),
        soundProfile = CarSoundProfile.V6_TURBO
    )

    val VENOM_RX = CarSpecs(
        id = "venom_rx",
        name = "Venom RX Driftmaster",
        category = "Drift Spec / Coupe",
        description = "Monster Rear-Wheel Drive bertenaga buas, dirancang khusus untuk sudut drifting ekstrem, respons kemudi agresif, dan ban belakang yang siap membakar aspal.",
        horsepower = 580,
        maxRpm = 9100f,
        idleRpm = 1100f,
        massKg = 1250f,
        topSpeedKmh = 315,
        accel0To100Sec = 3.1f,
        handlingRating = 8.0f,
        driftRating = 9.9f,
        driveType = DriveType.RWD,
        gearRatios = floatArrayOf(3.95f, 2.45f, 1.75f, 1.35f, 1.08f, 0.88f, -3.60f),
        finalDrive = 3.90f,
        dragCoeff = 0.34f,
        downforceCoeff = 0.55f,
        brakeForce = 16500f,
        tireGrip = 1.15f,
        defaultColor = Color(0xFFFF6D00), // Flame Orange
        availableColors = listOf(
            Color(0xFFFF6D00), // Flame Orange
            Color(0xFF7C3AED), // Tokyo Violet
            Color(0xFF10B981), // Acid Emerald
            Color(0xFF0F172A), // Stealth Black
            Color(0xFFEC4899)  // Neon Magenta
        ),
        soundProfile = CarSoundProfile.ROTARY_DRIFT
    )

    val PHANTOM_HYPERX = CarSpecs(
        id = "phantom_hyperx",
        name = "Phantom Hyper-X LM",
        category = "Hypercar / Prototype",
        description = "Karya agung aerodinamika dengan tenaga raksasa 1020 HP dari paduan Twin-Turbo Hybrid, menghasilkan akselerasi roket dan kecepatan tertinggi tak tertandingi.",
        horsepower = 1020,
        maxRpm = 9400f,
        idleRpm = 1200f,
        massKg = 1320f,
        topSpeedKmh = 405,
        accel0To100Sec = 2.1f,
        handlingRating = 9.9f,
        driftRating = 7.8f,
        driveType = DriveType.AWD,
        gearRatios = floatArrayOf(3.35f, 2.10f, 1.50f, 1.18f, 0.95f, 0.75f, -3.25f),
        finalDrive = 3.35f,
        dragCoeff = 0.26f,
        downforceCoeff = 1.25f,
        brakeForce = 23000f,
        tireGrip = 1.48f,
        defaultColor = Color(0xFF00E5FF), // Hyper Cyan
        availableColors = listOf(
            Color(0xFF00E5FF), // Hyper Cyan
            Color(0xFF0F172A), // Carbon Raw
            Color(0xFFFFD600), // Monaco Gold
            Color(0xFFDC2626), // Rosso Corsa
            Color(0xFF4338CA)  // Deep Indigo
        ),
        soundProfile = CarSoundProfile.HYBRID_V8
    )

    val VIPER_GTS = CarSpecs(
        id = "viper_gts",
        name = "Viper GTS Muscle V8",
        category = "American Muscle / GT",
        description = "Muscle car legendaris dengan mesin 8.4L V8 bertenaga 740 HP. Torsi putaran bawah yang brutal menghasilkan hentakan akselerasi awal yang menggelegar.",
        horsepower = 740,
        maxRpm = 7600f,
        idleRpm = 850f,
        massKg = 1580f,
        topSpeedKmh = 338,
        accel0To100Sec = 2.8f,
        handlingRating = 7.6f,
        driftRating = 9.1f,
        driveType = DriveType.RWD,
        gearRatios = floatArrayOf(3.60f, 2.25f, 1.60f, 1.25f, 1.00f, 0.79f, -3.40f),
        finalDrive = 3.55f,
        dragCoeff = 0.35f,
        downforceCoeff = 0.60f,
        brakeForce = 17500f,
        tireGrip = 1.22f,
        defaultColor = Color(0xFF1E3A8A), // Royal Blue with white stripe
        availableColors = listOf(
            Color(0xFF1E3A8A), // Royal Blue
            Color(0xFF991B1B), // Crimson Red
            Color(0xFF18181B), // Onyx Black
            Color(0xFFD97706), // Copper Bronze
            Color(0xFFF8FAFC)  // Pure White
        ),
        soundProfile = CarSoundProfile.V8_MUSCLE
    )

    val KAZE_SPRINT = CarSpecs(
        id = "kaze_sprint",
        name = "Kaze Sprint Type-R",
        category = "Lightweight Tuner / Coupe",
        description = "Mobil sport kompak ultra-ringan seberat 1.080 kg dengan mesin Turbo 4-Silinder. Kemampuan melahap tikungan tajam dengan kecepatan masuk yang luar biasa lincah.",
        horsepower = 420,
        maxRpm = 8800f,
        idleRpm = 950f,
        massKg = 1080f,
        topSpeedKmh = 290,
        accel0To100Sec = 3.6f,
        handlingRating = 9.6f,
        driftRating = 8.4f,
        driveType = DriveType.FWD,
        gearRatios = floatArrayOf(4.10f, 2.60f, 1.85f, 1.40f, 1.12f, 0.90f, -3.70f),
        finalDrive = 4.15f,
        dragCoeff = 0.30f,
        downforceCoeff = 0.70f,
        brakeForce = 16000f,
        tireGrip = 1.38f,
        defaultColor = Color(0xFF10B981), // Emerald Tuner
        availableColors = listOf(
            Color(0xFF10B981), // Emerald Tuner
            Color(0xFFF59E0B), // Sunburst Yellow
            Color(0xFF06B6D4), // Aqua Turbo
            Color(0xFFF43F5E), // Cherry Red
            Color(0xFF475569)  // Steel Gray
        ),
        soundProfile = CarSoundProfile.INLINE4_TURBO
    )

    val ALL_CARS = listOf(APEX_GTR, PHANTOM_HYPERX, VENOM_RX, VIPER_GTS, KAZE_SPRINT)
}
