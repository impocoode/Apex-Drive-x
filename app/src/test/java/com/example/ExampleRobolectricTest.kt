package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.game.engine3d.Track3D
import com.example.game.engine3d.TrackThemes
import com.example.game.engine3d.Vector3
import com.example.game.physics.CarDatabase
import com.example.game.physics.VehiclePhysics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Apex Drive 3D", appName)
    }

    @Test
    fun `test garage car models have distinct speed and accel attributes`() {
        val cars = CarDatabase.ALL_CARS
        assertTrue("Car database should have multiple diverse models", cars.size >= 5)

        val topSpeeds = cars.map { it.topSpeedKmh }
        val accels = cars.map { it.accel0To100Sec }

        // Must have diverse speeds and accelerations
        assertTrue("Top speeds should vary across garage cars", topSpeeds.distinct().size > 3)
        assertTrue("Accelerations should vary across garage cars", accels.distinct().size > 3)

        // Verify Hypercar is fastest in top speed and acceleration
        val hypercar = cars.find { it.id == "phantom_hyperx" }!!
        val tuner = cars.find { it.id == "kaze_sprint" }!!

        assertTrue("Hypercar should have higher top speed than tuner", hypercar.topSpeedKmh > tuner.topSpeedKmh)
        assertTrue("Hypercar should accelerate to 100 faster than tuner", hypercar.accel0To100Sec < tuner.accel0To100Sec)
    }

    @Test
    fun `test vehicle physics acceleration and throttle`() {
        val car = CarDatabase.APEX_GTR
        val physics = VehiclePhysics(car)
        physics.reset(Vector3(0f, 0.45f, 0f), 0f)

        // Apply throttle for 1 second
        physics.throttleInput = 1.0f
        for (i in 0 until 60) {
            physics.update(1f / 60f)
        }

        // Speed should have increased from rest
        assertTrue("Car should accelerate when throttle is applied", physics.speedKmh > 10f)
        assertTrue("Engine RPM should rev up", physics.engineRpm > car.idleRpm)
    }

    @Test
    fun `test track segment generation`() {
        val track = Track3D(TrackThemes.APEX_RING)
        assertTrue("Track segments must be generated", track.segments.isNotEmpty())
        assertTrue("Total track length should be greater than 500m", track.totalTrackLength > 500f)
    }
}
