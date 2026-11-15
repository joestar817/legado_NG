package io.legado.plugins.pets.layered.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class BlueFishScooterMotionTest {
    @Test
    fun directionChangesFollowContinuousNonzeroSpeedTurn() {
        for (width in listOf(260f, 300f, 646f, 1120f)) {
            for (join in listOf(4650L, 5750L)) {
                val before = BlueFishScooterMotion.routeAt(join - 1L, width, 400f)
                val at = BlueFishScooterMotion.routeAt(join, width, 400f)
                val after = BlueFishScooterMotion.routeAt(join + 1L, width, 400f)
                assertTrue(at.speed > 35f)
                assertEquals(before.speed, at.speed, .01f)
                assertEquals(at.speed, after.speed, .01f)
                assertTrue(hypot(at.x - before.x, at.ground - before.ground) < .05f)
                assertTrue(hypot(after.x - at.x, after.ground - at.ground) < .05f)
                assertTrue(abs(after.yaw - before.yaw) < .003f)
            }
            val turn = (4650L..5750L step 10).map { BlueFishScooterMotion.routeAt(it, width, 400f) }
            assertTrue(turn.zipWithNext().all { (a, b) -> b.yaw > a.yaw })
            assertTrue(turn.zipWithNext().all { (a, b) -> b.ground >= a.ground })
            assertEquals(turn.first().x, turn.last().x, .001f)
            assertEquals(25.6f, turn.last().ground - turn.first().ground, .001f)
        }
    }

    @Test
    fun wheelsTrackTrueTravelDistanceAndStopOutsideScreen() {
        val width = 360f
        val start = BlueFishScooterMotion.routeAt(2050L, width, 440f)
        val end = BlueFishScooterMotion.routeAt(8350L, width, 440f)
        assertEquals(start.x, end.x, .001f)
        assertEquals(start.ground, end.ground, .001f)
        assertEquals(0f, start.speed, .001f)
        assertEquals(0f, end.speed, .001f)
        // Even a fully opaque source canvas has left the screen before a pose swap.
        assertTrue(end.x - 640f * BlueFishCharacter.HEIGHT_DP / 1150f > width)
        val wheelRadius = 75f * BlueFishCharacter.HEIGHT_DP / 1150f
        var previous = start
        for (time in 2060L..8350L step 10) {
            val current = BlueFishScooterMotion.routeAt(time, width, 440f)
            assertTrue(current.distance >= previous.distance)
            assertEquals(current.distance / wheelRadius, current.wheelAngle, .001f)
            previous = current
        }
    }

    @Test
    fun savedDockHeightIsPreservedWhileLowRidesCanUseSafeHeight() {
        val width = 360f
        val dockY = 700f
        val rideY = 680f
        val first = BlueFishScooterMotion.sample(0L, width, dockY, rideY)
        val last = BlueFishScooterMotion.sample(10600L, width, dockY, rideY)
        assertEquals(first, last)
        assertEquals(BlueFishCharacter.definition.dockAnchorX(width), first.anchorX, .001f)
        assertEquals(dockY, first.anchorY, .001f)
        assertEquals(rideY, BlueFishScooterMotion.sample(2500L, width, dockY, rideY).anchorY, .001f)
        assertTrue(BlueFishScooterMotion.sample(1950L, width, dockY).blueFish!!.scooter!!.hidden)
        assertTrue(BlueFishScooterMotion.sample(8450L, width, dockY).blueFish!!.scooter!!.hidden)
        assertFalse(BlueFishScooterMotion.sample(5100L, width, dockY).blueFish!!.scooter!!.hidden)
    }

    @Test
    fun resizeAndHeightOnlyChangeWorldPositionNotActionTiming() {
        for (width in listOf(40f, 180f, 260f, 412f, 800f)) {
            for (time in 0L..10600L step 17) {
                val frame = BlueFishScooterMotion.sample(time, width, 300f)
                assertTrue(frame.anchorX.isFinite() && frame.anchorY.isFinite())
                val raised = BlueFishScooterMotion.sample(time, width, 440f)
                assertEquals(frame.blueFish, raised.blueFish)
                assertEquals(frame.anchorX, raised.anchorX, .001f)
                assertEquals(140f, raised.anchorY - frame.anchorY, .001f)
            }
        }
    }
}
