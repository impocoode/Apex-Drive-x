package com.example.game.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.*
import kotlin.random.Random

class CarAudioEngine {
    private val sampleRate = 22050
    private var audioTrack: AudioTrack? = null
    private val isRunning = AtomicBoolean(false)
    var isMuted = false

    // Audio parameters updated by physics
    @Volatile var targetRpm: Float = 1000f
    @Volatile var targetThrottle: Float = 0f
    @Volatile var targetTireSlip: Float = 0f
    @Volatile var targetTurboBoost: Float = 0f
    @Volatile var isRedlining: Boolean = false
    @Volatile var triggerBovHiss: Boolean = false
    @Volatile var triggerBackfire: Boolean = false

    fun start() {
        if (isRunning.get()) return
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
            isRunning.set(true)

            thread(name = "CarAudioThread", priority = Thread.MAX_PRIORITY) {
                runAudioLoop(bufferSize)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun runAudioLoop(chunkSize: Int) {
        val buffer = ShortArray(chunkSize)
        var phase1 = 0.0
        var phase2 = 0.0
        var turboPhase = 0.0
        var squealPhase = 0.0
        var currentSmoothedRpm = 1000.0
        var bovTimeLeft = 0f
        var backfireTimeLeft = 0f

        while (isRunning.get()) {
            if (isMuted) {
                buffer.fill(0)
                audioTrack?.write(buffer, 0, buffer.size)
                Thread.sleep(20)
                continue
            }

            // Smooth RPM
            currentSmoothedRpm += (targetRpm - currentSmoothedRpm) * 0.08
            val throttle = targetThrottle.coerceIn(0f, 1f)
            val tireSlip = targetTireSlip.coerceIn(0f, 1f)
            val boost = targetTurboBoost.coerceIn(0f, 25f)

            if (triggerBovHiss) {
                bovTimeLeft = 0.35f
                triggerBovHiss = false
            }
            if (triggerBackfire) {
                backfireTimeLeft = 0.12f
                triggerBackfire = false
            }

            // Engine base frequency (6 cylinders: 3 combustion pulses per revolution)
            val baseFreq = (currentSmoothedRpm / 60.0) * 3.0
            val phaseInc1 = (2.0 * Math.PI * baseFreq) / sampleRate
            val phaseInc2 = (2.0 * Math.PI * (baseFreq * 2.0)) / sampleRate

            val turboFreq = 1800.0 + (boost * 120.0)
            val turboPhaseInc = (2.0 * Math.PI * turboFreq) / sampleRate

            val squealFreq = 950.0 + tireSlip * 350.0
            val squealPhaseInc = (2.0 * Math.PI * squealFreq) / sampleRate

            for (i in buffer.indices) {
                phase1 = (phase1 + phaseInc1) % (2.0 * Math.PI)
                phase2 = (phase2 + phaseInc2) % (2.0 * Math.PI)
                turboPhase = (turboPhase + turboPhaseInc) % (2.0 * Math.PI)
                squealPhase = (squealPhase + squealPhaseInc) % (2.0 * Math.PI)

                // Engine harmonics (raw raspy combustion sound)
                var engineSample = sin(phase1) * 0.45 + sin(phase2) * 0.35 + sin(phase1 * 3.0) * 0.15
                // Distortion on high throttle
                if (throttle > 0.2f) {
                    engineSample = tanh(engineSample * (1.0 + throttle * 1.5))
                }

                // Turbo whistle
                var turboSample = 0.0
                if (boost > 2.0f) {
                    val turboVol = (boost / 25.0f).coerceIn(0f, 0.25f)
                    turboSample = sin(turboPhase) * turboVol
                }

                // Blow-off valve hiss
                var bovSample = 0.0
                if (bovTimeLeft > 0f) {
                    bovTimeLeft -= (1.0f / sampleRate)
                    val noise = (Random.nextFloat() * 2.0 - 1.0)
                    bovSample = noise * 0.3 * (bovTimeLeft / 0.35f)
                }

                // Tire squeal during drift / burnout
                var tireSample = 0.0
                if (tireSlip > 0.25f) {
                    val slipVol = ((tireSlip - 0.25f) / 0.75f).coerceIn(0f, 0.45f)
                    val hiss = (Random.nextFloat() * 2.0 - 1.0) * 0.4
                    tireSample = (sin(squealPhase) * 0.6 + hiss) * slipVol
                }

                // Backfire pop
                var popSample = 0.0
                if (backfireTimeLeft > 0f) {
                    backfireTimeLeft -= (1.0f / sampleRate)
                    popSample = (Random.nextFloat() * 2.0 - 1.0) * 0.8
                }

                // Redline limiter stutter
                val limiterMul = if (isRedlining && (System.currentTimeMillis() % 90 < 45)) 0.2 else 1.0

                val mixed = (engineSample * 0.65 * limiterMul + turboSample + bovSample + tireSample + popSample)
                val clamped = mixed.coerceIn(-1.0, 1.0)
                buffer[i] = (clamped * 28000.0).toInt().toShort()
            }

            audioTrack?.write(buffer, 0, buffer.size)
        }
    }

    fun stop() {
        isRunning.set(false)
        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
