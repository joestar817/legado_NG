package io.legado.app.ui.main.chatentry.core

import kotlin.math.PI
import kotlin.math.sin

enum class BlueFishStage { IDLE, PEEK, THINKING, AHA, SETTLE, EATING, SURPRISED, SHY }

/** Artwork coordinates, with angles in radians. This is transient rendering state. */
data class BlueFishArmMotion(val tip: PetPoint, val handAngle: Float, val riceBallScale: Float)

data class BlueFishVisual(
    val stage: BlueFishStage,
    val angle: Float = 0f,
    val scaleY: Float = 1f,
    val tailAngle: Float = 0f,
    val blink: Boolean = false,
    val thoughtText: String = "",
    val riceLevel: Float = 1f,
    val chew: Boolean = false,
    val arm: BlueFishArmMotion? = null,
    val bubbleFollowsAnchor: Boolean = false,
)

/** Pure sampling of the accepted local v3 animation, independent of frame rate. */
object BlueFishMotion {
    const val THINKING_MS = 6500L
    const val AHA_MS = 1500L
    const val SIT_MS = 450L
    const val EATING_MS = 16000L
    const val SEQUENCE_MS = THINKING_MS + AHA_MS + SIT_MS + EATING_MS
    const val EDGE_PERIOD_MS = 11000L
    const val SURPRISED_MS = 650L
    const val SHY_MS = 1100L

    private val thoughts = listOf("∑ ? % #", "{ x ? }", "0x… + ?", "[?] ≠ ∞", "λ / # %", "? → ∑", "% {…} #", "x² ? 0x")

    fun idle(clockMs: Long) = BlueFishVisual(
        stage = BlueFishStage.IDLE,
        angle = sin(clockMs / 2300.0).toFloat() * .004f,
        scaleY = 1f + sin(clockMs / 1500.0).toFloat() * .0018f,
        tailAngle = tail(5f, clockMs, 4000L),
        blink = blink(clockMs % 5100L, 3000L, clockMs / 5100L % 4L == 2L),
    )

    /** One intact front pose: no detached head or articulated hand for this reaction. */
    fun surprised(elapsedMs: Long): BlueFishVisual {
        val t = elapsedMs.coerceAtLeast(0L)
        val settle = 1f - smooth(t / SURPRISED_MS.toFloat())
        return BlueFishVisual(
            stage = BlueFishStage.SURPRISED,
            angle = -.025f * settle + sin(t / 700.0).toFloat() * .003f,
            scaleY = 1f + .006f * settle,
            tailAngle = tail(2f, t, 2400L),
        )
    }

    fun shy(clockMs: Long, elapsedMs: Long): BlueFishVisual {
        val t = elapsedMs.coerceIn(0L, SHY_MS)
        val settle = 1f - smooth(t / SHY_MS.toFloat())
        val idle = idle(clockMs)
        return BlueFishVisual(
            stage = BlueFishStage.SHY,
            angle = idle.angle + sin(t / SHY_MS.toDouble() * PI * 2).toFloat() * .012f * settle,
            scaleY = idle.scaleY - sin(t / SHY_MS.toDouble() * PI).toFloat() * .004f,
            tailAngle = tail(2f + 3f * (1f - settle), clockMs, 4000L),
        )
    }

    fun peek(clockMs: Long, edgeElapsedMs: Long): BlueFishVisual {
        val t = edgeElapsedMs % EDGE_PERIOD_MS
        val cycle = edgeElapsedMs / EDGE_PERIOD_MS
        val look = if (t in 6000L until 8500L) sin((t - 6000L) / 2500.0 * PI * 2).toFloat() else 0f
        return BlueFishVisual(
            stage = BlueFishStage.PEEK,
            angle = look * .014f,
            scaleY = 1f + sin(clockMs / 1700.0).toFloat() * .0012f,
            blink = blink(t, 3000L, cycle % 3L == 1L) || blink(t, 8000L),
        )
    }

    fun edgeReveal(edgeElapsedMs: Long): Float {
        val t = edgeElapsedMs % EDGE_PERIOD_MS
        return when {
            t < 4700L -> 480f
            t < 6000L -> mix(480f, 630f, smooth((t - 4700L) / 1300f))
            t < 8500L -> 630f
            t < 9800L -> mix(630f, 480f, smooth((t - 8500L) / 1300f))
            else -> 480f
        }
    }

    fun sequence(clockMs: Long, elapsedMs: Long): BlueFishVisual {
        val t = elapsedMs.coerceAtLeast(0L)
        return when {
            t < THINKING_MS -> BlueFishVisual(
                stage = BlueFishStage.THINKING,
                angle = sin(t / 1000.0).toFloat() * .006f,
                scaleY = 1f + sin(clockMs / 1500.0).toFloat() * .0018f,
                tailAngle = tail(3f, clockMs, 5400L),
                blink = blink(t, 1150L, true),
                thoughtText = thoughts[(t / 300L % thoughts.size).toInt()],
            )
            t < THINKING_MS + AHA_MS -> {
                val local = t - THINKING_MS
                val arc = sin((local / 700.0).coerceAtMost(1.0) * PI).toFloat()
                BlueFishVisual(
                    stage = BlueFishStage.AHA,
                    angle = -.012f * arc,
                    scaleY = 1f + .006f * arc,
                    tailAngle = tail(8f, local, 900L),
                )
            }
            t < THINKING_MS + AHA_MS + SIT_MS -> BlueFishVisual(
                stage = BlueFishStage.SETTLE,
                scaleY = mix(1f, .95f, smooth((t - THINKING_MS - AHA_MS) / SIT_MS.toFloat())),
            )
            else -> eating(clockMs, t - THINKING_MS - AHA_MS - SIT_MS)
        }
    }

    fun eating(clockMs: Long, elapsedMs: Long): BlueFishVisual {
        val food = foodAt(elapsedMs)
        val biteArc = sin(food.progress * PI).toFloat()
        var scaleY = 1f - if (food.satisfied) 0f else biteArc * .004f
        if (food.chew && !food.satisfied) scaleY += sin(elapsedMs / 110.0).toFloat() * .0018f
        if (elapsedMs < 400L) scaleY = mix(.95f, 1f, smooth(elapsedMs / 400f))
        return BlueFishVisual(
            stage = BlueFishStage.EATING,
            angle = if (food.satisfied) 0f else sin(food.index * 1.7).toFloat() * .006f + biteArc * .006f,
            scaleY = scaleY,
            tailAngle = tail(6f, clockMs, 3300L),
            riceLevel = food.riceLevel,
            chew = food.chew,
            arm = armAt(food),
        )
    }

    private enum class BitePhase { HOLD, SCOOP, LIFT, BITE, CHEW, REST }
    private data class Food(
        val riceLevel: Float, val progress: Float, val index: Int,
        val chew: Boolean, val phase: BitePhase, val satisfied: Boolean = false,
    )

    private fun foodAt(elapsedMs: Long): Food {
        val t = elapsedMs.coerceIn(0L, EATING_MS)
        if (t < 400L) return Food(1f, 0f, 0, true, BitePhase.HOLD)
        var eaten = 0f
        repeat(6) { i -> eaten += smooth((t - (400L + i * 2250L + 1140L)) / 220f) }
        val riceLevel = (1f - eaten / 6f).coerceIn(0f, 1f)
        if (t >= 13900L) return Food(0f, 1f, 6, true, BitePhase.REST, satisfied = true)
        val index = ((t - 400L) / 2250L).toInt().coerceIn(0, 5)
        val progress = ((t - 400L) % 2250L) / 2250f
        val phase = when {
            progress < .24f -> BitePhase.SCOOP
            progress < .50f -> BitePhase.LIFT
            progress < .65f -> BitePhase.BITE
            else -> BitePhase.CHEW
        }
        return Food(riceLevel, progress, index + 1, phase != BitePhase.BITE, phase)
    }

    private fun armAt(food: Food): BlueFishArmMotion {
        val rest = PetPoint(520f, 690f)
        val mouth = PetPoint(555f, 617f)
        val rice = PetPoint(480f, 708f + 164f * (1f - food.riceLevel))
        val riceAngle = .1f + .5f * (1f - food.riceLevel)
        val p = food.progress
        return when (food.phase) {
            BitePhase.SCOOP -> {
                val t = smooth(p / .24f)
                BlueFishArmMotion(bezier(rest, PetPoint(500f, 700f), PetPoint(rice.x - 25f, rice.y - 35f), rice, t), mix(.1f, riceAngle, t), 0f)
            }
            BitePhase.LIFT -> {
                val t = smooth((p - .24f) / .26f)
                BlueFishArmMotion(bezier(rice, PetPoint(rice.x + 30f, rice.y - 65f), PetPoint(mouth.x - 65f, mouth.y - 30f), mouth, t), mix(riceAngle, 0f, t), 1f)
            }
            BitePhase.BITE -> BlueFishArmMotion(mouth, 0f, 1f - smooth((p - .54f) / .07f))
            BitePhase.CHEW -> {
                val t = smooth((p - .65f) / .20f)
                BlueFishArmMotion(bezier(mouth, PetPoint(mouth.x + 5f, mouth.y + 20f), PetPoint(rest.x + 15f, rest.y - 12f), rest, t), mix(0f, .1f, t), 0f)
            }
            else -> BlueFishArmMotion(rest, .1f, 0f)
        }
    }

    private fun blink(ms: Long, startMs: Long, twice: Boolean = false): Boolean {
        fun pulse(t: Long) = t in 0L..180L && sin(t / 180.0 * PI) > .48
        return pulse(ms - startMs) || (twice && pulse(ms - startMs - 260L))
    }

    private fun tail(degrees: Float, clockMs: Long, periodMs: Long) =
        (degrees * PI / 180.0 * sin(clockMs / periodMs.toDouble() * PI * 2)).toFloat()

    internal fun smooth(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    internal fun ease(value: Float): Float {
        val t = 1f - value.coerceIn(0f, 1f)
        return 1f - t * t * t
    }

    private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t
    private fun bezier(a: PetPoint, b: PetPoint, c: PetPoint, d: PetPoint, t: Float): PetPoint {
        val q = 1f - t
        fun coordinate(a: Float, b: Float, c: Float, d: Float) = q * q * q * a + 3f * q * q * t * b + 3f * q * t * t * c + t * t * t * d
        return PetPoint(coordinate(a.x, b.x, c.x, d.x), coordinate(a.y, b.y, c.y, d.y))
    }
}
