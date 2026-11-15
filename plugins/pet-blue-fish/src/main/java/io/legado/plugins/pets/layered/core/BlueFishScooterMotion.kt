package io.legado.plugins.pets.layered.core

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Transient source-artwork state; a hidden frame also has an empty touch mask. */
data class BlueFishScooterVisual(
    val hidden: Boolean = false,
    val turning: Boolean = false,
    val yaw: Float = 0f,
    val rightward: Boolean = false,
    val push: Boolean = false,
    val wheelAngle: Float = 0f,
)

/** The v2 scooter scene, sampled from one clock rather than chained animations. */
object BlueFishScooterMotion {
    const val DURATION_MS = 10600L
    private const val START = 2.05
    private const val TURN_IN = 4.65
    private const val TURN_OUT = 5.75
    private const val END = 8.35
    private const val TRAVEL_TIME = 2.6
    private const val TURN_TIME = 1.1
    private const val RECOVER_TIME = 1.3
    private const val AXIS_X = .12
    private const val AXIS_Y = .10
    private const val TURN_STEPS = 2048
    private const val RETURN_STEPS = 1024
    private val size = BlueFishCharacter.HEIGHT_DP.toDouble()
    private val sourceScale = BlueFishCharacter.definition.scale
    private val turnLengths = DoubleArray(TURN_STEPS + 1).apply {
        for (i in 1..TURN_STEPS) {
            this[i] = this[i - 1] + arcIntegral(PI * (i - 1) / TURN_STEPS, PI * i / TURN_STEPS)
        }
    }
    private val unitTurnLength = turnLengths[TURN_STEPS]
    private var cachedLayout: Layout? = null

    /**
     * groundY is the saved dock height. A lower-screen dock may supply a slightly
     * higher rideGroundY; the height changes only while both poses are offscreen.
     */
    fun sample(elapsedMs: Long, widthDp: Float, groundY: Float, rideGroundY: Float = groundY): PetFrame {
        require(widthDp.isFinite() && widthDp > 0f && groundY.isFinite() && rideGroundY.isFinite())
        val t = elapsedMs.coerceIn(0L, DURATION_MS) / 1000.0
        var reveal = 480f
        var look = 0f
        var blinking = false
        when {
            t < .55 -> Unit
            t < 1.5 -> {
                reveal = mix(480f, 760f, smooth((t - .55) / .55))
                blinking = blink(t, 1.25)
                look = (sin(clamp((t - 1.05) / .45) * PI) * .7).toFloat()
            }
            t < 1.88 -> reveal = mix(760f, 120f, smooth((t - 1.5) / .38))
            t < START -> return hiddenFrame(widthDp, groundY)
            t < END -> {
                val route = routeAt(elapsedMs, widthDp, rideGroundY)
                val push = !route.turning && !route.rightward &&
                    smooth((t - 2.90) / .10) * (1f - smooth((t - 3.11) / .14)) >= .5f
                return PetFrame(
                    PetPose.FULL, PetPoint(route.x, route.ground), PetMotion.ZERO,
                    BlueFishVisual(
                        stage = BlueFishStage.SCOOTER,
                        scooter = BlueFishScooterVisual(
                            turning = route.turning, yaw = route.yaw, rightward = route.rightward,
                            push = push, wheelAngle = route.wheelAngle * if (route.rightward) 1f else -1f,
                        ),
                    ),
                )
            }
            t < 8.55 -> return hiddenFrame(widthDp, groundY)
            t < 9.2 -> reveal = mix(120f, 630f, smooth((t - 8.55) / .65))
            t < 9.55 -> { reveal = 630f; blinking = blink(t, 9.32) }
            t < 10.15 -> reveal = mix(630f, 480f, smooth((t - 9.55) / .6))
        }
        val angle = -look * .012f
        // The preview turns the head about (640, 700); compensate its root so
        // the shared native renderer can still rotate around (640, 1230).
        val pivotDistance = BlueFishCharacter.ROOT_Y - 700f
        val anchor = PetPoint(
            widthDp + (BlueFishCharacter.ROOT_X - reveal - sin(angle) * pivotDistance) * sourceScale,
            groundY + (cos(angle) - 1f) * pivotDistance * sourceScale,
        )
        return PetFrame(PetPose.PEEK, anchor, PetMotion.ZERO,
            BlueFishVisual(BlueFishStage.PEEK, angle = angle, blink = blinking))
    }

    private fun hiddenFrame(widthDp: Float, groundY: Float) = PetFrame(
        PetPose.FULL, PetPoint(BlueFishCharacter.definition.dockAnchorX(widthDp), groundY), PetMotion.ZERO,
        BlueFishVisual(BlueFishStage.SCOOTER, scooter = BlueFishScooterVisual(hidden = true)),
    )

    internal data class Route(
        val x: Float, val ground: Float, val yaw: Float, val distance: Float,
        val wheelAngle: Float, val speed: Float, val vx: Float, val vy: Float,
        val turning: Boolean, val rightward: Boolean,
    )

    /** Kept separate from artwork selection so the real path can be regression-tested. */
    internal fun routeAt(elapsedMs: Long, widthDp: Float, groundY: Float): Route {
        val layout = cachedLayout?.takeIf { it.width == widthDp }
            ?: Layout(widthDp).also { cachedLayout = it }
        val t = (elapsedMs / 1000.0).coerceIn(START, END)
        val x: Double
        var ground = groundY.toDouble()
        val yaw: Double
        val distance: Double
        val speed: Double
        val vx: Double
        var vy = 0.0
        val turning = t >= TURN_IN && t < TURN_OUT
        val rightward = t >= TURN_OUT
        when {
            t < TURN_IN -> {
                val point = outward(layout, t - START)
                x = layout.outside - point.distance
                distance = point.distance
                speed = point.speed
                vx = -speed
                yaw = 0.0
            }
            turning -> {
                val progress = clamp((t - TURN_IN) / TURN_TIME)
                val theta = thetaAt(progress)
                val dx = -layout.a * cos(theta)
                val dy = layout.b * sin(theta)
                val norm = hypot(dx, dy)
                x = layout.entryX - layout.a * sin(theta)
                ground += layout.b * (1 - cos(theta))
                speed = layout.turnSpeed
                vx = dx / norm * speed
                vy = dy / norm * speed
                yaw = (atan2(dx, dy) + PI * .5) / PI
                distance = layout.travelDistance + progress * layout.turnLength
            }
            else -> {
                val u = (t - TURN_OUT).coerceIn(0.0, TRAVEL_TIME)
                val point = returnState(layout, u)
                x = point.x
                ground += point.ground
                vx = point.vx
                vy = point.vy
                speed = point.speed
                yaw = 1.0
                val index = u / TRAVEL_TIME * RETURN_STEPS
                val i = index.toInt().coerceAtMost(RETURN_STEPS - 1)
                val f = index - i
                distance = layout.travelDistance + layout.turnLength +
                    layout.returnLengths[i] * (1 - f) + layout.returnLengths[i + 1] * f
            }
        }
        return Route(x.toFloat(), ground.toFloat(), yaw.toFloat(), distance.toFloat(),
            (distance / layout.wheelRadius).toFloat(), speed.toFloat(), vx.toFloat(), vy.toFloat(),
            turning, rightward)
    }

    private class Layout(val width: Float) {
        val a = AXIS_X * size
        val b = AXIS_Y * size
        // Unlike the browser mockup, the native screen has no 12px painted edge.
        val outside = width + size * .57
        val turnLength = unitTurnLength * size
        val turnSpeed = turnLength / TURN_TIME
        val decelerate = .55
        // Very narrow transient layouts still get finite, continuous sampling.
        private val minDistance = turnSpeed * (TRAVEL_TIME * .5 + decelerate * .5)
        val entryX = minOf(maxOf(size * .48 + 18, 42.0) + a, outside - minDistance)
        val travelDistance = outside - entryX
        val accelerate = (2 * (TRAVEL_TIME - travelDistance / turnSpeed) + .16)
            .coerceIn(.45, TRAVEL_TIME - decelerate)
        val cruise = (travelDistance - turnSpeed * decelerate * .5) /
            (TRAVEL_TIME - (accelerate + decelerate) * .5)
        val wheelRadius = 75 * size / 1150
        val returnLengths = DoubleArray(RETURN_STEPS + 1).apply {
            val dt = TRAVEL_TIME / RETURN_STEPS
            for (i in 1..RETURN_STEPS) {
                val t = (i - 1) * dt
                val length = dt * (returnState(this@Layout, t).speed +
                    4 * returnState(this@Layout, t + dt * .5).speed +
                    returnState(this@Layout, t + dt).speed) / 6
                this[i] = this[i - 1] + length
            }
        }
    }

    private data class Travel(val distance: Double, val speed: Double)
    private data class Return(val x: Double, val ground: Double, val vx: Double, val vy: Double) {
        val speed get() = hypot(vx, vy)
    }

    private fun ramp(time: Double, duration: Double, from: Double, to: Double): Travel {
        val q = clamp(time / duration)
        val delta = to - from
        return Travel(from * duration * q + delta * duration * (q * .5 - sin(PI * q) / (2 * PI)),
            from + delta * (1 - cos(PI * q)) * .5)
    }

    private fun outward(layout: Layout, time: Double): Travel {
        val t = time.coerceIn(0.0, TRAVEL_TIME)
        val a = layout.accelerate
        val b = layout.decelerate
        val v = layout.cruise
        if (t < a) return ramp(t, a, 0.0, v)
        val accelerationDistance = v * a * .5
        if (t < TRAVEL_TIME - b) return Travel(accelerationDistance + (t - a) * v, v)
        val tail = ramp(t - (TRAVEL_TIME - b), b, v, layout.turnSpeed)
        return Travel(accelerationDistance + (TRAVEL_TIME - b - a) * v + tail.distance, tail.speed)
    }

    private fun returnState(layout: Layout, time: Double): Return {
        val t = time.coerceIn(0.0, TRAVEL_TIME)
        val reverse = outward(layout, TRAVEL_TIME - t)
        val q = t / RECOVER_TIME
        return Return(layout.entryX + layout.travelDistance - reverse.distance,
            2 * layout.b * (1 - smooth5(q)), reverse.speed,
            -2 * layout.b * smooth5Derivative(q) / RECOVER_TIME)
    }

    private fun ellipseSpeed(theta: Double) = hypot(AXIS_X * cos(theta), AXIS_Y * sin(theta))
    private fun arcIntegral(a: Double, b: Double) =
        (b - a) * (ellipseSpeed(a) + 4 * ellipseSpeed((a + b) * .5) + ellipseSpeed(b)) / 6

    private fun thetaAt(progress: Double): Double {
        val length = clamp(progress) * unitTurnLength
        var low = 0
        var high = TURN_STEPS
        while (high - low > 1) {
            val mid = (low + high) ushr 1
            if (turnLengths[mid] < length) low = mid else high = mid
        }
        val p = (length - turnLengths[low]) / (turnLengths[high] - turnLengths[low])
        val start = PI * low / TURN_STEPS
        var theta = PI * (low + p) / TURN_STEPS
        repeat(2) { theta -= (turnLengths[low] + arcIntegral(start, theta) - length) / ellipseSpeed(theta) }
        return theta.coerceIn(0.0, PI)
    }

    private fun clamp(value: Double) = value.coerceIn(0.0, 1.0)
    private fun smooth(value: Double): Float { val p = clamp(value); return (p * p * (3 - 2 * p)).toFloat() }
    private fun smooth5(value: Double): Double { val p = clamp(value); return p * p * p * (10 + p * (-15 + 6 * p)) }
    private fun smooth5Derivative(value: Double) = if (value <= 0 || value >= 1) 0.0 else 30 * value * value * (1 - value) * (1 - value)
    private fun mix(from: Float, to: Float, amount: Float) = from + (to - from) * amount
    private fun blink(time: Double, start: Double) = time >= start + .025 && time < start + .125
}
