package io.legado.plugins.pets.layered.core

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/** The accepted v12 straight-fist / v10 rigid-shoe rig, sampled on one continuous clock. */
class BlueFishSuspendedMotion {
    val sleeveMatrices = Array(2) { FloatArray(6) }
    val handMatrices = Array(2) { FloatArray(6) }
    val calfMatrices = Array(2) { FloatArray(6) }
    val shoeMatrices = Array(2) { FloatArray(6) }
    var tailAngle = -.10f
        private set

    fun update(elapsedMs: Long, settling: Float = 1f) {
        val t = elapsedMs.coerceAtLeast(0L) / 1000.0
        val settled = settling.coerceIn(0f, 1f).toDouble()
        val armDrive = smooth(t / .10) * settled
        val legDrive = smooth(t / .18) * settled
        for (index in 0..1) {
            val g = ARMS[index]
            val side = if (index == 0) -1.0 else 1.0
            val height = gesture(t, index)
            val dx = side * (72.0 - 9.0 * height * armDrive)
            val dy = 25.0 - 64.0 * height * armDrive
            val sx = g.wristX - g.shoulderX
            val sy = g.wristY - g.shoulderY
            val length = hypot(sx, sy)
            val ux = sx / length
            val uy = sy / length
            // Only the straight sleeve's screen projection changes. Its cuff
            // keeps facing the camera, and the fist remains a rigid drawing.
            val a = dx / length * ux
            val c = dx / length * uy
            val b = dy / length * ux - side * .53 * uy
            val d = dy / length * uy + side * .53 * ux
            set(sleeveMatrices[index], a, b, c, d,
                g.attachX - a * g.shoulderX - c * g.shoulderY,
                g.attachY - b * g.shoulderX - d * g.shoulderY)
            rigid(handMatrices[index], g.wristX, g.wristY, g.attachX + dx, g.attachY + dy,
                (if (index == 0) PI else 0.0) - atan2(sy, sx), .53)

            val leg = LEGS[index]
            val rate = if (index == 0) 1.94 else 2.04
            val offset = if (index == 0) 0.0 else 2.7
            val phase = 2.0 * PI * rate * t + offset + .10 * sin(t * .8 + index)
            val shaped = phase + .12 * sin(phase)
            val tuck = (1.0 - cos(shaped)) * .5
            val lagTime = t - .035
            val lagPhase = 2.0 * PI * rate * lagTime + offset + .10 * sin(lagTime * .8 + index)
            val legDx = side * (2.0 + 3.5 * sin(phase)) * legDrive
            val legDy = -21.0 * tuck * legDrive
            val angle = side * (2.0 + 6.0 * sin(lagPhase)) * PI / 180.0 * legDrive
            val n = leg.ankleY - leg.pivotY
            val baseLength = n * .58
            val legA = cos(angle) * .58
            val legB = sin(angle) * .58
            val legC = legDx / n
            val legD = (baseLength + legDy) / n
            set(calfMatrices[index], legA, legB, legC, legD,
                leg.attachX - legA * leg.pivotX - legC * leg.pivotY,
                leg.attachY - legB * leg.pivotX - legD * leg.pivotY)
            rigid(shoeMatrices[index], leg.pivotX, leg.ankleY,
                leg.attachX + legDx, leg.attachY + baseLength + legDy, angle, .58)
        }
        // Keep the already accepted, subtle tail envelope independent of the
        // subsequent hand and foot cadence refinements.
        tailAngle = (-.10 + .012 * (pulse(t, LEFT_ARM) - pulse(t, RIGHT_ARM)) * settled +
            .009 * (pulse(t, LEFT_LEG) - pulse(t, RIGHT_LEG)) * settled).toFloat()
    }

    private fun gesture(t: Double, index: Int): Double {
        val p = CADENCE[index]
        val omega = 2.0 * PI / p[2]
        val phase = 2.0 * PI * p[0] * t + 2.0 * PI * p[1] / omega *
            (sin(omega * t + p[3]) - sin(p[3])) + p[4]
        val shaped = phase + p[5] * sin(phase)
        return p[6] + (.98 - p[6]) * (1.0 - cos(shaped)) * .5
    }

    private fun pulse(t: Double, events: DoubleArray): Double {
        val time = t % 4.24
        var value = 0.0
        for (index in events.indices step 4) {
            val delta = (time - events[index] + 4.24) % 4.24
            val push = events[index + 1]
            val recover = events[index + 2]
            val strength = events[index + 3]
            if (delta < push) value = max(value, strength * smooth(delta / push))
            else if (delta < push + recover) {
                value = max(value, strength * (1.0 - smooth((delta - push) / recover)))
            }
        }
        return value
    }

    data class ArmGeometry(
        val shoulderX: Double, val shoulderY: Double,
        val wristX: Double, val wristY: Double,
        val attachX: Double, val attachY: Double,
    )

    data class LegGeometry(
        val pivotX: Double, val pivotY: Double, val ankleY: Double,
        val attachX: Double, val attachY: Double,
    )

    companion object {
        val ARMS = arrayOf(
            ArmGeometry(835.0, 260.0, 713.0, 372.0, 205.0, 407.0),
            ArmGeometry(1179.0, 260.0, 1301.0, 372.0, 375.0, 407.0),
        )
        private val LEGS = arrayOf(
            LegGeometry(122.0, 790.0, 843.0, 243.0, 627.0),
            LegGeometry(370.0, 790.0, 843.0, 337.0, 627.0),
        )
        private val CADENCE = arrayOf(
            doubleArrayOf(2.75, .12, 4.8, .3, 0.0, .09, .03),
            doubleArrayOf(2.95, .13, 5.6, 2.0, 1.8, -.07, .08),
        )
        private val LEFT_ARM = doubleArrayOf(0.0,.16,.25,1.0, .48,.14,.29,.84, 1.03,.18,.25,.91, 1.75,.15,.27,.72, 2.29,.16,.25,1.0, 3.02,.16,.28,.82, 3.55,.15,.27,.96)
        private val RIGHT_ARM = doubleArrayOf(.095,.17,.25,.84, .59,.14,.27,1.0, 1.13,.16,.27,.76, 1.88,.15,.27,.94, 2.40,.15,.28,.80, 3.12,.17,.24,1.0, 3.65,.17,.26,.83)
        private val LEFT_LEG = doubleArrayOf(.22,.13,.24,.88, .73,.15,.26,1.0, 1.44,.14,.25,.76, 1.98,.14,.27,.92, 2.70,.16,.24,.83, 3.23,.15,.25,1.0, 3.88,.13,.22,.70)
        private val RIGHT_LEG = doubleArrayOf(.315,.15,.25,1.0, .84,.14,.24,.78, 1.55,.16,.26,.93, 2.08,.14,.25,.79, 2.81,.14,.27,1.0, 3.34,.16,.24,.86, 3.99,.13,.22,.72)

        fun smooth(value: Double): Double {
            val t = value.coerceIn(0.0, 1.0)
            return t * t * (3.0 - 2.0 * t)
        }

        fun rigid(matrix: FloatArray, sourceX: Double, sourceY: Double,
            targetX: Double, targetY: Double, angle: Double, scale: Double) {
            val c = cos(angle) * scale
            val s = sin(angle) * scale
            set(matrix, c, s, -s, c,
                targetX - sourceX * c + sourceY * s,
                targetY - sourceX * s - sourceY * c)
        }

        private fun set(matrix: FloatArray, a: Double, b: Double, c: Double, d: Double, tx: Double, ty: Double) {
            matrix[0] = a.toFloat(); matrix[1] = b.toFloat()
            matrix[2] = c.toFloat(); matrix[3] = d.toFloat()
            matrix[4] = tx.toFloat(); matrix[5] = ty.toFloat()
        }
    }
}
