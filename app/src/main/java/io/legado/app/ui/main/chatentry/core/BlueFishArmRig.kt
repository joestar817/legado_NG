package io.legado.app.ui.main.chatentry.core

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** Fixed v3 bind geometry. All values remain in the 1254px artwork coordinate space. */
class BlueFishArmRig {
    val meshVertices = FloatArray((COLUMNS + 1) * (ROWS + 1) * 2)
    val handMatrix = FloatArray(6)
    private val sourceVertices = FloatArray(meshVertices.size)
    private val lowerWeights = FloatArray(meshVertices.size / 2)
    private val upperMatrix = FloatArray(6)
    private val lowerMatrix = FloatArray(6)

    var tipX = 0f
        private set
    var tipY = 0f
        private set
    var reachError = 0f
        private set

    init {
        for (row in 0..ROWS) for (column in 0..COLUMNS) {
            val i = row * (COLUMNS + 1) + column
            val x = SLEEVE_X + SLEEVE_WIDTH * column / COLUMNS
            val y = SLEEVE_Y + SLEEVE_HEIGHT * row / ROWS
            sourceVertices[i * 2] = x
            sourceVertices[i * 2 + 1] = y
            val upper = distanceToSegment(x, y, 319f, 838f, 235f, 814f)
            val lower = distanceToSegment(x, y, 235f, 814f, 290f, 753f)
            var weight = (.5f + (upper - lower) / 140f).coerceIn(0f, 1f)
            // Keep the shoulder and cuff attached; a distance blend alone lets them slide.
            weight *= smooth(hypot(x - 319f, y - 838f) / 18f)
            weight = 1f - (1f - weight) * smooth(hypot(x - 290f, y - 753f) / 18f)
            lowerWeights[i] = weight
        }
    }

    fun update(motion: BlueFishArmMotion) {
        val handCos = cos(motion.handAngle)
        val handSin = sin(motion.handAngle)
        val requestedX = motion.tip.x - ((518f - 290f) * handCos - (673f - 753f) * handSin) * SCALE
        val requestedY = motion.tip.y - ((518f - 290f) * handSin + (673f - 753f) * handCos) * SCALE
        val dx = requestedX - 375f
        val dy = requestedY - 748f
        val requestedDistance = hypot(dx, dy)
        val distance = requestedDistance.coerceIn(abs(UPPER_LENGTH - LOWER_LENGTH) + .001f, UPPER_LENGTH + LOWER_LENGTH - .001f)
        reachError = abs(distance - requestedDistance)
        val ux = if (requestedDistance > .0001f) dx / requestedDistance else 0f
        val uy = if (requestedDistance > .0001f) dy / requestedDistance else 1f
        val wristX = 375f + ux * distance
        val wristY = 748f + uy * distance
        val along = (UPPER_LENGTH * UPPER_LENGTH - LOWER_LENGTH * LOWER_LENGTH + distance * distance) / (2f * distance)
        val across = sqrt((UPPER_LENGTH * UPPER_LENGTH - along * along).coerceAtLeast(0f))
        val elbowX = 375f + ux * along + uy * across
        val elbowY = 748f + uy * along - ux * across
        val upperAngle = atan2(elbowY - 748f, elbowX - 375f) - atan2(814f - 838f, 235f - 319f)
        val lowerAngle = atan2(wristY - elbowY, wristX - elbowX) - atan2(753f - 814f, 290f - 235f)
        rigidMatrix(upperMatrix, 319f, 838f, 375f, 748f, upperAngle)
        rigidMatrix(lowerMatrix, 235f, 814f, elbowX, elbowY, lowerAngle)
        rigidMatrix(handMatrix, 290f, 753f, wristX, wristY, motion.handAngle)
        tipX = transformX(handMatrix, 518f, 673f)
        tipY = transformY(handMatrix, 518f, 673f)
        for (i in lowerWeights.indices) {
            val x = sourceVertices[i * 2]
            val y = sourceVertices[i * 2 + 1]
            val weight = lowerWeights[i]
            meshVertices[i * 2] = transformX(upperMatrix, x, y) * (1f - weight) + transformX(lowerMatrix, x, y) * weight
            meshVertices[i * 2 + 1] = transformY(upperMatrix, x, y) * (1f - weight) + transformY(lowerMatrix, x, y) * weight
        }
    }

    private fun rigidMatrix(out: FloatArray, sx: Float, sy: Float, tx: Float, ty: Float, angle: Float) {
        val c = cos(angle) * SCALE
        val s = sin(angle) * SCALE
        out[0] = c; out[1] = s; out[2] = -s; out[3] = c
        out[4] = tx - sx * c + sy * s
        out[5] = ty - sx * s - sy * c
    }

    private fun distanceToSegment(x: Float, y: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val t = (((x - ax) * dx + (y - ay) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
        return hypot(x - ax - dx * t, y - ay - dy * t)
    }

    private fun smooth(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun transformX(matrix: FloatArray, x: Float, y: Float) = matrix[0] * x + matrix[2] * y + matrix[4]
    private fun transformY(matrix: FloatArray, x: Float, y: Float) = matrix[1] * x + matrix[3] * y + matrix[5]

    companion object {
        const val COLUMNS = 12
        const val ROWS = 10
        const val SLEEVE_X = 185f
        const val SLEEVE_Y = 765f
        const val SLEEVE_WIDTH = 195f
        const val SLEEVE_HEIGHT = 110f
        private const val SCALE = .9f
        private val UPPER_LENGTH = hypot(235f - 319f, 814f - 838f) * SCALE
        private val LOWER_LENGTH = hypot(290f - 235f, 753f - 814f) * SCALE
    }
}
