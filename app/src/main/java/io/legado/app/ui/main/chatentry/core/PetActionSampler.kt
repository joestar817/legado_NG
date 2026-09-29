package io.legado.app.ui.main.chatentry.core

import kotlin.math.PI
import kotlin.math.sin

data class PetActionSpec(val id: PetActionId, val durationMs: Long, val poses: Set<PetPose>)

/** The accepted v3.5 action curves. No rendering, timers, or Android state. */
object PetActionSampler {
    private val fullOnly = setOf(PetPose.FULL)
    private val specs = mapOf(
        PetActionId.FULL_CALL to PetActionSpec(PetActionId.FULL_CALL, 5200L, fullOnly),
        PetActionId.WAVE to PetActionSpec(PetActionId.WAVE, 2000L, fullOnly),
        PetActionId.CURIOUS to PetActionSpec(PetActionId.CURIOUS, 2500L, PetPose.entries.toSet()),
        PetActionId.YAWN to PetActionSpec(PetActionId.YAWN, 3200L, fullOnly),
        PetActionId.PEEK_LOOK to PetActionSpec(PetActionId.PEEK_LOOK, 5200L, setOf(PetPose.PEEK)),
    )

    fun spec(id: PetActionId): PetActionSpec = specs.getValue(id)

    fun sample(id: PetActionId, elapsedMs: Long, pose: PetPose): PetMotion {
        val spec = spec(id)
        if (elapsedMs <= 0L || elapsedMs >= spec.durationMs || pose !in spec.poses) return PetMotion.ZERO
        val t = elapsedMs.toDouble()
        return when (id) {
            PetActionId.FULL_CALL -> {
                val seconds = t / 1000.0
                val raised = pulse(seconds, 1.4, 2.1, 3.5, 4.1)
                val sway = 3.0 * sin(2.0 * PI * (seconds - 2.1) / .7) * pulse(seconds, 2.1, 2.35, 3.25, 3.5)
                val shout = pulse(seconds, 1.98, 2.1, 3.5, 4.1).toFloat()
                PetMotion(
                    leftArm = (110.0 * raised + sway).toFloat(),
                    rightArm = (-110.0 * raised - sway).toFloat(),
                    shout = shout,
                    mouth = shout,
                    blink = maxOf(blink(seconds, .6), blink(seconds, 4.5)).toFloat(),
                )
            }
            PetActionId.WAVE -> {
                val amount = pulse(t, 0.0, 420.0, 1510.0, 2000.0)
                val wave = pulse(t, 420.0, 570.0, 1350.0, 1510.0) * sin(2.0 * PI * (t - 420.0) / 460.0)
                PetMotion(
                    rightArm = (-101.0 * amount - 7.0 * wave).toFloat(),
                    rightElbow = (12.0 * amount).toFloat(),
                    head = (-2.0 * amount).toFloat(),
                    blink = pulse(t, 970.0, 1010.0, 1060.0, 1110.0).toFloat(),
                )
            }
            PetActionId.CURIOUS -> PetMotion(
                head = (((if (pose == PetPose.PEEK) 3.2 else 4.2) * pulse(t, 0.0, 500.0, 1200.0, 1600.0)) -
                    2.0 * pulse(t, 1350.0, 1740.0, 2060.0, 2500.0)).toFloat(),
                blink = pulse(t, 1750.0, 1790.0, 1850.0, 1890.0).toFloat(),
            )
            PetActionId.YAWN -> PetMotion(
                head = (-2.5 * pulse(t, 0.0, 700.0, 1900.0, 2900.0)).toFloat(),
                blink = pulse(t, 180.0, 460.0, 2230.0, 2850.0).toFloat(),
                mouth = (.82 * pulse(t, 340.0, 850.0, 1770.0, 2450.0)).toFloat(),
            )
            PetActionId.PEEK_LOOK -> {
                val seconds = t / 1000.0
                PetMotion(
                    reveal = pulse(seconds, .5, 1.6, 3.6, 4.8).toFloat(),
                    head = (2.0 * sin(2.0 * PI * (seconds - 1.6) / 2.0) * pulse(seconds, 1.6, 1.9, 3.3, 3.6)).toFloat(),
                    blink = blink(seconds, 2.65).toFloat(),
                )
            }
        }
    }

    internal fun smooth(value: Double): Double {
        val t = value.coerceIn(0.0, 1.0)
        return t * t * (3.0 - 2.0 * t)
    }

    internal fun pulse(t: Double, a: Double, b: Double, c: Double, d: Double): Double =
        smooth((t - a) / (b - a)) * (1.0 - smooth((t - c) / (d - c)))

    internal fun blink(seconds: Double, center: Double): Double =
        pulse(seconds, center - .07, center - .025, center + .025, center + .07)
}
