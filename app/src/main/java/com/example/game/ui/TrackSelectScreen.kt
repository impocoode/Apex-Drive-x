package com.example.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.data.ControlType
import com.example.game.data.GamePreferences
import com.example.game.engine3d.TrackTheme
import com.example.game.engine3d.TrackThemes
import com.example.game.physics.CarDatabase
import com.example.ui.theme.*

@Composable
fun TrackSelectScreen(
    prefs: GamePreferences,
    onTrackSelected: (TrackTheme) -> Unit,
    onOpenGarage: () -> Unit
) {
    val allTracks = listOf(
        TrackThemes.APEX_RING,
        TrackThemes.NEON_EXPRESSWAY,
        TrackThemes.CANYON_PASS
    )

    var selectedTrack by remember {
        val currId = prefs.selectedTrackId
        val track = allTracks.find { it.id == currId } ?: TrackThemes.APEX_RING
        mutableStateOf(track)
    }

    var controlType by remember { mutableStateOf(prefs.controlType) }
    var absEnabled by remember { mutableStateOf(prefs.absEnabled) }
    var tcsEnabled by remember { mutableStateOf(prefs.tcsEnabled) }
    var autoTrans by remember { mutableStateOf(prefs.autoTransmission) }

    val currentCar = remember(prefs.selectedCarId) {
        val carId = prefs.selectedCarId
        CarDatabase.ALL_CARS.find { it.id == carId } ?: CarDatabase.APEX_GTR
    }

    Scaffold(
        containerColor = DarkBackground,
        contentWindowInsets = WindowInsets(0.dp)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SportsScore, contentDescription = null, tint = RacingCyan, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "APEX DRIVE 3D",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = 2.sp
                        )
                        Text(
                            "PILIH LINTASAN & PENGATURAN FISIKA",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = RacingYellow
                        )
                    }
                }

                // Garage Shortcut button
                OutlinedButton(
                    onClick = onOpenGarage,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RacingCyan),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.linearGradient(listOf(RacingCyan, RacingRed))),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("open_garage_button")
                ) {
                    Icon(Icons.Default.DirectionsCar, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.Start) {
                        Text("GARASI & PILIH MOBIL", fontSize = 11.sp, fontWeight = FontWeight.Black)
                        Text("${currentCar.name} • ${currentCar.topSpeedKmh} KM/H • ${currentCar.accel0To100Sec}s", fontSize = 9.sp, color = Color.LightGray)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Main Content: Track Cards Carousel
            LazyRow(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(allTracks) { track ->
                    val isSelected = (track.id == selectedTrack.id)
                    val bestLap = prefs.getBestLapTime(track.id)
                    val bestDrift = prefs.getBestDriftScore(track.id)

                    Card(
                        modifier = Modifier
                            .width(280.dp)
                            .fillMaxHeight(0.95f)
                            .clip(RoundedCornerShape(16.dp))
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) RacingCyan else Color(0xFF334155),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                selectedTrack = track
                                prefs.selectedTrackId = track.id
                            }
                            .testTag("track_card_${track.id}"),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Color(0xFF131D33) else DarkSurface
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                // Track visual badge
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(75.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(track.skyColorTop, track.roadColor)
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        if (track.nightMode) Icons.Default.NightsStay else Icons.Default.WbSunny,
                                        contentDescription = null,
                                        tint = track.sunColor,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }

                                Spacer(Modifier.height(8.dp))
                                Text(
                                    track.name,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                                Text(
                                    track.description,
                                    fontSize = 10.sp,
                                    color = Color.LightGray,
                                    maxLines = 2,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }

                            // Best Records Card
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("REKOR LAP", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                    val lapStr = if (bestLap > 0f) {
                                        val m = (bestLap / 60).toInt()
                                        val s = bestLap % 60
                                        String.format("%02d:%05.2f", m, s)
                                    } else "--:--.--"
                                    Text(lapStr, fontSize = 12.sp, fontWeight = FontWeight.Black, color = RacingCyan)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("REKOR DRIFT", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                    Text(
                                        if (bestDrift > 0) "$bestDrift PTS" else "0 PTS",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black,
                                        color = RacingYellow
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Bottom Bar: Controls, Assists & Start Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Control Type Selector
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("KONTROL:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.LightGray)
                    Spacer(Modifier.width(8.dp))

                    FilterChip(
                        selected = controlType == ControlType.BUTTONS,
                        onClick = {
                            controlType = ControlType.BUTTONS
                            prefs.controlType = ControlType.BUTTONS
                        },
                        label = { Text("TOMBOL", fontSize = 11.sp) }
                    )
                    Spacer(Modifier.width(6.dp))
                    FilterChip(
                        selected = controlType == ControlType.STEERING_WHEEL,
                        onClick = {
                            controlType = ControlType.STEERING_WHEEL
                            prefs.controlType = ControlType.STEERING_WHEEL
                        },
                        label = { Text("SETIR", fontSize = 11.sp) }
                    )
                    Spacer(Modifier.width(6.dp))
                    FilterChip(
                        selected = controlType == ControlType.TILT_ACCELEROMETER,
                        onClick = {
                            controlType = ControlType.TILT_ACCELEROMETER
                            prefs.controlType = ControlType.TILT_ACCELEROMETER
                        },
                        label = { Text("GYRO/TILT", fontSize = 11.sp) }
                    )
                }

                // Assists toggles
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AssistPill("ABS", absEnabled) {
                        absEnabled = !absEnabled
                        prefs.absEnabled = absEnabled
                    }
                    Spacer(Modifier.width(6.dp))
                    AssistPill("TCS", tcsEnabled) {
                        tcsEnabled = !tcsEnabled
                        prefs.tcsEnabled = tcsEnabled
                    }
                    Spacer(Modifier.width(6.dp))
                    AssistPill(if (autoTrans) "AUTO" else "MANUAL", autoTrans) {
                        autoTrans = !autoTrans
                        prefs.autoTransmission = autoTrans
                    }
                }

                // Race Launch Button
                Button(
                    onClick = { onTrackSelected(selectedTrack) },
                    modifier = Modifier
                        .height(48.dp)
                        .padding(start = 12.dp)
                        .testTag("start_race_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = RacingRed),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.SportsMotorsports, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "MULAI BALAPAN",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistPill(label: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (active) Color(0xFF065F46) else Color(0xFF1E293B),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (active) RacingGreen else Color.DarkGray)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) RacingGreen else Color.Gray
        )
    }
}
