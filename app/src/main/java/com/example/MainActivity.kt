package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.game.data.GamePreferences
import com.example.game.engine3d.TrackTheme
import com.example.game.engine3d.TrackThemes
import com.example.game.physics.CarDatabase
import com.example.game.physics.CarSpecs
import com.example.game.ui.GarageScreen
import com.example.game.ui.RacingGameScreen
import com.example.game.ui.TrackSelectScreen
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.MyApplicationTheme

enum class GameScreen {
    TRACK_SELECT,
    RACING_GAME,
    GARAGE
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep screen on during racing sessions
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val prefs = GamePreferences(applicationContext)

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    var currentScreen by remember { mutableStateOf(GameScreen.TRACK_SELECT) }
                    var selectedTrackTheme by remember {
                        val currTrackId = prefs.selectedTrackId
                        val track = when (currTrackId) {
                            TrackThemes.NEON_EXPRESSWAY.id -> TrackThemes.NEON_EXPRESSWAY
                            TrackThemes.CANYON_PASS.id -> TrackThemes.CANYON_PASS
                            else -> TrackThemes.APEX_RING
                        }
                        mutableStateOf(track)
                    }
                    var selectedCarSpecs by remember {
                        val currCarId = prefs.selectedCarId
                        val car = CarDatabase.ALL_CARS.find { it.id == currCarId } ?: CarDatabase.APEX_GTR
                        mutableStateOf(car)
                    }

                    Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
                        when (screen) {
                            GameScreen.TRACK_SELECT -> {
                                TrackSelectScreen(
                                    prefs = prefs,
                                    onTrackSelected = { track ->
                                        selectedTrackTheme = track
                                        val carId = prefs.selectedCarId
                                        selectedCarSpecs = CarDatabase.ALL_CARS.find { it.id == carId } ?: CarDatabase.APEX_GTR
                                        currentScreen = GameScreen.RACING_GAME
                                    },
                                    onOpenGarage = {
                                        currentScreen = GameScreen.GARAGE
                                    }
                                )
                            }
                            GameScreen.GARAGE -> {
                                GarageScreen(
                                    prefs = prefs,
                                    onBack = {
                                        val carId = prefs.selectedCarId
                                        selectedCarSpecs = CarDatabase.ALL_CARS.find { it.id == carId } ?: CarDatabase.APEX_GTR
                                        currentScreen = GameScreen.TRACK_SELECT
                                    },
                                    onSelectAndPlay = { car ->
                                        selectedCarSpecs = car
                                        val trackId = prefs.selectedTrackId
                                        selectedTrackTheme = when (trackId) {
                                            TrackThemes.NEON_EXPRESSWAY.id -> TrackThemes.NEON_EXPRESSWAY
                                            TrackThemes.CANYON_PASS.id -> TrackThemes.CANYON_PASS
                                            else -> TrackThemes.APEX_RING
                                        }
                                        currentScreen = GameScreen.RACING_GAME
                                    }
                                )
                            }
                            GameScreen.RACING_GAME -> {
                                RacingGameScreen(
                                    prefs = prefs,
                                    trackTheme = selectedTrackTheme,
                                    carSpecs = selectedCarSpecs,
                                    onExitToMenu = {
                                        currentScreen = GameScreen.TRACK_SELECT
                                    },
                                    onOpenGarage = {
                                        currentScreen = GameScreen.GARAGE
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
