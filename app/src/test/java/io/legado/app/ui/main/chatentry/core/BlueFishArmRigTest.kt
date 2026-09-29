package io.legado.app.ui.main.chatentry.core

import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Geometry checks in artwork space; these do not establish rendered visual acceptance. */
class BlueFishArmRigTest {
    @Test
    fun everyEatingSampleReachesTheRequestedChopstickTip() {
        val rig = BlueFishArmRig()
        forEachEatingSample { time, motion ->
            rig.update(motion)

            assertTrue("reach error at ${time}ms: ${rig.reachError}", rig.reachError < .01f)
            val error = hypot(rig.tipX - motion.tip.x, rig.tipY - motion.tip.y)
            assertTrue("tip error at ${time}ms: $error", error < .01f)

            // Independently map the fixed source chopstick tip through the hand transform.
            val matrix = rig.handMatrix
            val x = matrix[0] * 518f + matrix[2] * 673f + matrix[4]
            val y = matrix[1] * 518f + matrix[3] * 673f + matrix[5]
            val transformedError = hypot(x - motion.tip.x, y - motion.tip.y)
            assertTrue("hand-matrix tip error at ${time}ms: $transformedError", transformedError < .01f)
        }
    }

    @Test
    fun everyMeshCellPreservesOrientationForBothTriangleDiagonals() {
        assertEquals(12, BlueFishArmRig.COLUMNS)
        assertEquals(10, BlueFishArmRig.ROWS)
        val rig = BlueFishArmRig()
        val stride = BlueFishArmRig.COLUMNS + 1
        assertEquals(stride * (BlueFishArmRig.ROWS + 1) * 2, rig.meshVertices.size)

        forEachEatingSample { time, motion ->
            rig.update(motion)
            val vertices = rig.meshVertices
            assertTrue("finite mesh at ${time}ms", vertices.all { it.isFinite() })

            for (row in 0 until BlueFishArmRig.ROWS) {
                for (column in 0 until BlueFishArmRig.COLUMNS) {
                    val topLeft = row * stride + column
                    val topRight = topLeft + 1
                    val bottomLeft = topLeft + stride
                    val bottomRight = bottomLeft + 1
                    val label = "${time}ms cell($column,$row)"

                    // Renderer diagonal: top-left to bottom-right.
                    assertPositiveArea(vertices, topLeft, topRight, bottomRight, "$label first diagonal A")
                    assertPositiveArea(vertices, topLeft, bottomRight, bottomLeft, "$label first diagonal B")
                    // Alternate diagonal must also remain valid: catches a concave quad.
                    assertPositiveArea(vertices, topLeft, topRight, bottomLeft, "$label second diagonal A")
                    assertPositiveArea(vertices, topRight, bottomRight, bottomLeft, "$label second diagonal B")
                }
            }
        }
    }

    @Test
    fun theHandAndChopsticksRemainAnOrthogonalRigidTransform() {
        val rig = BlueFishArmRig()
        forEachEatingSample { time, motion ->
            rig.update(motion)
            val matrix = rig.handMatrix
            assertEquals(6, matrix.size)
            assertTrue("finite hand matrix at ${time}ms", matrix.all { it.isFinite() })
            val xLength = hypot(matrix[0], matrix[1])
            val yLength = hypot(matrix[2], matrix[3])
            val dot = matrix[0] * matrix[2] + matrix[1] * matrix[3]
            val determinant = matrix[0] * matrix[3] - matrix[1] * matrix[2]

            assertEquals("hand x scale at ${time}ms", .9f, xLength, .00001f)
            assertEquals("hand y scale at ${time}ms", .9f, yLength, .00001f)
            assertEquals("hand axes orthogonal at ${time}ms", 0f, dot, .000001f)
            assertEquals("hand has no reflection at ${time}ms", .81f, determinant, .00001f)
        }
    }

    private fun forEachEatingSample(check: (Long, BlueFishArmMotion) -> Unit) {
        assertEquals(16_000L, BlueFishMotion.EATING_MS)
        var count = 0
        for (time in 0L..BlueFishMotion.EATING_MS step 25L) {
            val visual = BlueFishMotion.eating(time, time)
            assertEquals("eating stage at ${time}ms", BlueFishStage.EATING, visual.stage)
            val motion = requireNotNull(visual.arm) { "missing eating arm at ${time}ms" }
            assertTrue("finite target at ${time}ms", motion.tip.x.isFinite() && motion.tip.y.isFinite())
            assertTrue("finite hand angle at ${time}ms", motion.handAngle.isFinite())
            check(time, motion)
            count++
        }
        assertEquals("includes both 0ms and 16000ms", 641, count)
    }

    private fun assertPositiveArea(vertices: FloatArray, a: Int, b: Int, c: Int, label: String) {
        val ax = vertices[a * 2].toDouble()
        val ay = vertices[a * 2 + 1].toDouble()
        val bx = vertices[b * 2].toDouble()
        val by = vertices[b * 2 + 1].toDouble()
        val cx = vertices[c * 2].toDouble()
        val cy = vertices[c * 2 + 1].toDouble()
        val signedDoubleArea = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)
        assertTrue("$label folds or collapses: $signedDoubleArea", signedDoubleArea > 0.0)
    }
}
