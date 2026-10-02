package com.example.game.engine3d

import androidx.compose.ui.graphics.Color
import kotlin.random.Random

data class Particle(
    var position: Vector3,
    var velocity: Vector3,
    var size: Float,
    var life: Float,
    val maxLife: Float,
    val color: Color,
    val type: ParticleType
)

enum class ParticleType {
    TIRE_SMOKE,
    NITRO_FLAME,
    SPARK
}

data class SkidMark(
    val p1: Vector3,
    val p2: Vector3,
    var alpha: Float = 0.75f,
    val width: Float = 0.28f
)

class ParticleSystem {
    val particles = mutableListOf<Particle>()
    val skidMarks = mutableListOf<SkidMark>()

    private val random = Random(42)

    fun emitTireSmoke(pos: Vector3, carVelocity: Vector3) {
        if (particles.size > 140) return
        val vel = Vector3(
            (random.nextFloat() - 0.5f) * 1.5f + carVelocity.x * 0.1f,
            random.nextFloat() * 1.2f + 0.4f,
            (random.nextFloat() - 0.5f) * 1.5f + carVelocity.z * 0.1f
        )
        val life = 0.6f + random.nextFloat() * 0.4f
        particles.add(
            Particle(
                position = pos + Vector3((random.nextFloat() - 0.5f) * 0.2f, 0.1f, (random.nextFloat() - 0.5f) * 0.2f),
                velocity = vel,
                size = 0.35f,
                life = life,
                maxLife = life,
                color = Color(0xFFE2E8F0),
                type = ParticleType.TIRE_SMOKE
            )
        )
    }

    fun emitNitroFlame(pos: Vector3, carHeading: Float) {
        if (particles.size > 160) return
        val forwardDir = Vector3(kotlin.math.sin(carHeading), 0f, kotlin.math.cos(carHeading))
        val exhaustVel = forwardDir * (-14f) + Vector3(
            (random.nextFloat() - 0.5f) * 1.2f,
            (random.nextFloat() - 0.5f) * 0.8f,
            (random.nextFloat() - 0.5f) * 1.2f
        )
        val flameColor = if (random.nextBoolean()) Color(0xFF00E5FF) else Color(0xFF67E8F9)
        particles.add(
            Particle(
                position = pos,
                velocity = exhaustVel,
                size = 0.22f,
                life = 0.15f,
                maxLife = 0.15f,
                color = flameColor,
                type = ParticleType.NITRO_FLAME
            )
        )
    }

    fun emitSparks(pos: Vector3) {
        if (particles.size > 160) return
        for (i in 0 until 5) {
            val vel = Vector3(
                (random.nextFloat() - 0.5f) * 7f,
                random.nextFloat() * 5f + 1f,
                (random.nextFloat() - 0.5f) * 7f
            )
            particles.add(
                Particle(
                    position = pos,
                    velocity = vel,
                    size = 0.12f,
                    life = 0.25f,
                    maxLife = 0.25f,
                    color = Color(0xFFFFD600),
                    type = ParticleType.SPARK
                )
            )
        }
    }

    fun addSkidMark(p1: Vector3, p2: Vector3) {
        if (skidMarks.size > 180) {
            skidMarks.removeAt(0)
        }
        skidMarks.add(SkidMark(p1, p2))
    }

    fun update(dt: Float) {
        val iter = particles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.life -= dt
            if (p.life <= 0f) {
                iter.remove()
                continue
            }
            p.position += p.velocity * dt
            if (p.type == ParticleType.TIRE_SMOKE) {
                p.size += dt * 1.8f // Puffs expand
                p.velocity = p.velocity * 0.92f // Drag
            } else if (p.type == ParticleType.SPARK) {
                p.velocity.y -= 12f * dt // Gravity
            }
        }

        // Fade skid marks
        val skidIter = skidMarks.iterator()
        while (skidIter.hasNext()) {
            val sm = skidIter.next()
            sm.alpha -= dt * 0.035f
            if (sm.alpha <= 0.05f) {
                skidIter.remove()
            }
        }
    }

    fun clear() {
        particles.clear()
        skidMarks.clear()
    }
}
