package com.example.game.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

enum class ControlType {
    STEERING_WHEEL,
    BUTTONS,
    TILT_ACCELEROMETER
}

class GamePreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("apex_drive_prefs", Context.MODE_PRIVATE)

    var selectedCarId: String
        get() = prefs.getString("selected_car_id", "apex_gtr") ?: "apex_gtr"
        set(value) = prefs.edit().putString("selected_car_id", value).apply()

    var selectedTrackId: String
        get() = prefs.getString("selected_track_id", "apex_ring") ?: "apex_ring"
        set(value) = prefs.edit().putString("selected_track_id", value).apply()

    var controlType: ControlType
        get() {
            val name = prefs.getString("control_type", ControlType.BUTTONS.name)
            return try {
                ControlType.valueOf(name ?: ControlType.BUTTONS.name)
            } catch (e: Exception) {
                ControlType.BUTTONS
            }
        }
        set(value) = prefs.edit().putString("control_type", value.name).apply()

    var absEnabled: Boolean
        get() = prefs.getBoolean("abs_enabled", true)
        set(value) = prefs.edit().putBoolean("abs_enabled", value).apply()

    var tcsEnabled: Boolean
        get() = prefs.getBoolean("tcs_enabled", true)
        set(value) = prefs.edit().putBoolean("tcs_enabled", value).apply()

    var espEnabled: Boolean
        get() = prefs.getBoolean("esp_enabled", true)
        set(value) = prefs.edit().putBoolean("esp_enabled", value).apply()

    var autoTransmission: Boolean
        get() = prefs.getBoolean("auto_trans", true)
        set(value) = prefs.edit().putBoolean("auto_trans", value).apply()

    var audioMuted: Boolean
        get() = prefs.getBoolean("audio_muted", false)
        set(value) = prefs.edit().putBoolean("audio_muted", value).apply()

    fun getBestLapTime(trackId: String): Float {
        return prefs.getFloat("best_lap_$trackId", 0f)
    }

    fun saveBestLapTime(trackId: String, timeSec: Float) {
        val curr = getBestLapTime(trackId)
        if (curr <= 0.001f || timeSec < curr) {
            prefs.edit().putFloat("best_lap_$trackId", timeSec).apply()
        }
    }

    fun getBestDriftScore(trackId: String): Int {
        return prefs.getInt("best_drift_$trackId", 0)
    }

    fun saveBestDriftScore(trackId: String, score: Int) {
        val curr = getBestDriftScore(trackId)
        if (score > curr) {
            prefs.edit().putInt("best_drift_$trackId", score).apply()
        }
    }

    fun getCustomColor(carId: String): Color? {
        val argb = prefs.getInt("custom_color_$carId", -1)
        return if (argb != -1) Color(argb) else null
    }

    fun saveCustomColor(carId: String, color: Color) {
        prefs.edit().putInt("custom_color_$carId", color.toArgb()).apply()
    }

    // Tuning & Performance Upgrades
    fun getEngineStage(carId: String): Int {
        return prefs.getInt("tuning_engine_$carId", 0)
    }

    fun saveEngineStage(carId: String, stage: Int) {
        prefs.edit().putInt("tuning_engine_$carId", stage).apply()
    }

    fun getTireCompound(carId: String): Int {
        return prefs.getInt("tuning_tires_$carId", 0)
    }

    fun saveTireCompound(carId: String, compound: Int) {
        prefs.edit().putInt("tuning_tires_$carId", compound).apply()
    }

    fun getAeroLevel(carId: String): Int {
        return prefs.getInt("tuning_aero_$carId", 1)
    }

    fun saveAeroLevel(carId: String, level: Int) {
        prefs.edit().putInt("tuning_aero_$carId", level).apply()
    }
}
