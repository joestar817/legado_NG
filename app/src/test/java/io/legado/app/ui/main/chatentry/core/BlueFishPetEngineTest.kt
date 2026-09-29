package io.legado.app.ui.main.chatentry.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

/** Native state and sampling contracts; artwork rendering remains a separate check. */
class BlueFishPetEngineTest {
    @Test
    fun startsDockedAndOnlyRevealsTheAcceptedHeadSlice() {
        val engine = edgeEngine()
        assertEquals(PetStableMode.DOCKED, engine.stableMode)
        assertEquals(PetPose.PEEK, engine.frame().pose)
        assertEquals(WIDTH + 160f * engine.definition.scale, engine.frame().anchor.x, EPSILON)
        engine.advance(6000L)
        assertEquals(WIDTH + 10f * engine.definition.scale, engine.frame().anchor.x, EPSILON)
        engine.advance(3800L)
        assertEquals(WIDTH + 160f * engine.definition.scale, engine.frame().anchor.x, EPSILON)
        repeat(90) {
            engine.advance(1000L)
            assertEquals(BlueFishStage.PEEK, engine.frame().blueFish!!.stage)
            assertNull(engine.frame().blueFish!!.arm)
        }
    }

    @Test
    fun freeActionUsesTheDesignTimelineAndThenWaitsTwelveToEighteenSeconds() {
        val engine = freeEngine()
        engine.advance(3999L)
        assertStage(engine, BlueFishStage.IDLE)
        engine.advance(1L)
        assertStage(engine, BlueFishStage.THINKING)
        engine.advance(6500L)
        assertStage(engine, BlueFishStage.AHA)
        engine.advance(1500L)
        assertStage(engine, BlueFishStage.SETTLE)
        engine.advance(450L)
        assertStage(engine, BlueFishStage.EATING)
        assertEquals(.95f, engine.frame().blueFish!!.scaleY, EPSILON)
        engine.advance(15999L)
        assertStage(engine, BlueFishStage.EATING)
        engine.advance(1L)
        assertStage(engine, BlueFishStage.IDLE)
        engine.advance(11999L)
        assertStage(engine, BlueFishStage.IDLE)
        engine.advance(6001L)
        assertStage(engine, BlueFishStage.THINKING)
    }

    @Test
    fun thoughtSymbolsChangeEveryThreeHundredMillisecondsAndClearForAha() {
        val a = BlueFishMotion.sequence(4000L, 0L)
        val b = BlueFishMotion.sequence(4299L, 299L)
        val c = BlueFishMotion.sequence(4300L, 300L)
        assertEquals(a.thoughtText, b.thoughtText)
        assertNotEquals(a.thoughtText, c.thoughtText)
        assertEquals("", BlueFishMotion.sequence(10500L, 6500L).thoughtText)
    }

    @Test
    fun sixBitesReduceRiceMonotonicallyAndFinishWithAnEmptyBowlHold() {
        assertEquals(1f, BlueFishMotion.eating(1539L, 1539L).riceLevel, EPSILON)
        repeat(6) { index ->
            val at = 1760L + index * 2250L
            assertEquals(1f - (index + 1) / 6f, BlueFishMotion.eating(at, at).riceLevel, EPSILON)
        }
        var previous = 1f
        for (at in 0L..16000L step 25) {
            val sample = BlueFishMotion.eating(at, at)
            val arm = sample.arm!!
            assertTrue(sample.riceLevel <= previous + EPSILON)
            assertTrue(sample.riceLevel in 0f..1f)
            assertTrue(arm.tip.x.isFinite() && arm.tip.y.isFinite())
            assertTrue(arm.riceBallScale in 0f..1f)
            previous = sample.riceLevel
        }
        for (at in 13900L..16000L step 100) {
            val sample = BlueFishMotion.eating(at, at)
            val arm = sample.arm!!
            assertEquals(0f, sample.riceLevel, EPSILON)
            assertEquals(0f, arm.riceBallScale, EPSILON)
            assertEquals(PetPoint(520f, 690f), arm.tip)
            assertTrue(sample.chew)
        }
    }

    @Test
    fun chopsticksScoopLowerAsRiceDecreasesAndFoodDisappearsInsideTheMouth() {
        val firstScoop = BlueFishMotion.eating(940L, 940L).arm!!
        val lastScoop = BlueFishMotion.eating(12190L, 12190L).arm!!
        assertEquals(480f, firstScoop.tip.x, EPSILON)
        assertTrue(lastScoop.tip.y > firstScoop.tip.y + 130f)
        assertTrue(lastScoop.handAngle > firstScoop.handAngle)
        assertEquals(1f, firstScoop.riceBallScale, EPSILON)

        val mouth = BlueFishMotion.eating(1615L, 1615L)
        val mouthArm = mouth.arm!!
        assertEquals(PetPoint(555f, 617f), mouthArm.tip)
        assertFalse(mouth.chew)
        assertEquals(1f, mouthArm.riceBallScale, EPSILON)
        assertEquals(0f, BlueFishMotion.eating(1773L, 1773L).arm!!.riceBallScale, EPSILON)
        assertTrue(BlueFishMotion.eating(1863L, 1863L).chew)
    }

    @Test
    fun eachCenterStageHasItsOwnTailAmplitudeAndPeekKeepsItsConnectedPose() {
        assertEquals(radians(5f), BlueFishMotion.idle(1000L).tailAngle, EPSILON)
        assertEquals(-radians(5f), BlueFishMotion.idle(3000L).tailAngle, EPSILON)
        assertEquals(radians(3f), BlueFishMotion.sequence(1350L, 500L).tailAngle, EPSILON)
        assertEquals(radians(8f), BlueFishMotion.sequence(0L, 6725L).tailAngle, EPSILON)
        assertEquals(radians(6f), BlueFishMotion.eating(825L, 5000L).tailAngle, EPSILON)
        assertEquals(0f, BlueFishMotion.peek(1000L, 1000L).tailAngle, EPSILON)
    }

    @Test
    fun blinkUsesTheSameClosedEyeThresholdAndDoubleBlinkTiming() {
        assertFalse(BlueFishMotion.idle(3000L).blink)
        assertTrue(BlueFishMotion.idle(3090L).blink)
        assertFalse(BlueFishMotion.idle(3180L).blink)
        assertTrue(BlueFishMotion.peek(14090L, 14090L).blink)
        assertTrue(BlueFishMotion.peek(14350L, 14350L).blink)
        assertTrue(BlueFishMotion.sequence(0L, 1240L).blink)
        assertTrue(BlueFishMotion.sequence(0L, 1500L).blink)
    }

    @Test
    fun grabbingFoodFreezesEveryLayerAndCancellationRestoresFutureScheduling() {
        val engine = freeEngine()
        val control = freeEngine()
        engine.advance(14000L)
        control.advance(14000L)
        val original = engine.frame()
        val grab = PetPoint(original.anchor.x - 10f, original.anchor.y - 20f)
        assertTrue(engine.press(grab.x, grab.y))
        assertNull(engine.nextFrameDelayMs())
        engine.advance(3000L)
        assertEquals(original, engine.frame())
        engine.move(grab.x - 25f, grab.y + 10f, 100L, true)
        assertEquals(original.blueFish, engine.frame().blueFish)
        assertEquals(PetPoint(original.anchor.x - 25f, original.anchor.y + 10f), engine.frame().anchor)
        engine.advance(3000L)
        engine.cancel()
        assertEquals(original, engine.frame())
        assertFalse(engine.isPressed)
        engine.advance(55000L)
        control.advance(55000L)
        assertEquals(control.frame(), engine.frame())
    }

    @Test
    fun aClickResumesTheSameActionInsteadOfSchedulingANewOne() {
        val engine = freeEngine()
        engine.advance(14500L)
        val before = engine.frame()
        assertTrue(engine.press(before.anchor.x, before.anchor.y))
        engine.move(before.anchor.x + 30f, before.anchor.y, 16L, false)
        assertFalse(engine.isDragging)
        engine.advance(3000L)
        assertTrue(engine.release(before.anchor.x, before.anchor.y))
        assertEquals(before, engine.frame())
        assertEquals(14500L, engine.sceneMs)
    }

    @Test
    fun fastEdgePullHoldsSurpriseFor650MsThenShyFor1100MsBeforeTheFreeWait() {
        val engine = edgeEngine()
        engine.advance(6300L)
        drag(engine, PetPoint(180f, 400f))
        assertEquals(PetPhase.SETTLING, engine.phase)
        assertEquals(PetPose.FULL, engine.frame().pose)
        assertStage(engine, BlueFishStage.SURPRISED)
        engine.advance(649L)
        assertStage(engine, BlueFishStage.SURPRISED)
        engine.advance(1L)
        assertStage(engine, BlueFishStage.SHY)
        engine.advance(1099L)
        assertStage(engine, BlueFishStage.SHY)
        engine.advance(1L)
        assertStage(engine, BlueFishStage.IDLE)
        assertEquals(PetStableMode.FREE, engine.stableMode)
        engine.advance(3999L)
        assertStage(engine, BlueFishStage.IDLE)
        engine.advance(1L)
        assertStage(engine, BlueFishStage.THINKING)
    }

    @Test
    fun anEdgeClickNeverChangesPoseAndCrossingTheThresholdBeginsSurprise() {
        val engine = edgeEngine()
        engine.advance(6500L)
        val before = engine.frame()
        engine.press(before.anchor.x, before.anchor.y)
        engine.move(before.anchor.x - 5f, before.anchor.y, 16L, false)
        engine.advance(2000L)
        assertEquals(before, engine.frame())
        assertNull(engine.nextFrameDelayMs())
        assertTrue(engine.release(before.anchor.x - 5f, before.anchor.y))
        assertEquals(before, engine.frame())

        engine.press(before.anchor.x, before.anchor.y)
        engine.move(180f, 400f, 32L, true)
        assertStage(engine, BlueFishStage.SURPRISED)
        assertEquals(PetPoint(180f, 400f), engine.frame().anchor)
        assertEquals(33L, engine.nextFrameDelayMs())
        assertNull(engine.frame().blueFish!!.arm)
        engine.advance(500L)
        assertEquals(7000L, engine.sceneMs)
        assertStage(engine, BlueFishStage.SURPRISED)
    }

    @Test
    fun longEdgeDragStillGetsA350MsReleaseSettleBeforeShy() {
        val engine = edgeEngine()
        val before = engine.frame()
        engine.press(before.anchor.x, before.anchor.y)
        engine.move(180f, 400f, 16L, true)
        engine.advance(2000L)
        assertStage(engine, BlueFishStage.SURPRISED)
        engine.release(180f, 400f)
        engine.advance(349L)
        assertStage(engine, BlueFishStage.SURPRISED)
        engine.advance(1L)
        assertStage(engine, BlueFishStage.SHY)
        engine.advance(1100L)
        assertStage(engine, BlueFishStage.IDLE)
    }

    @Test
    fun cancellingACuriousEdgePullRestoresItsPoseClockAndFutureReveal() {
        val engine = edgeEngine()
        val control = edgeEngine()
        engine.advance(6250L)
        control.advance(6250L)
        val before = engine.frame()
        val placement = engine.placement()
        assertFalse(before.blueFish!!.bubbleFollowsAnchor)
        engine.press(before.anchor.x, before.anchor.y)
        engine.move(180f, 400f, 16L, true)
        engine.advance(2500L)
        engine.cancel()
        assertEquals(before, engine.frame())
        assertFalse(engine.frame().blueFish!!.bubbleFollowsAnchor)
        assertEquals(6250L, engine.sceneMs)
        assertEquals(placement, engine.placement())
        engine.advance(3200L)
        control.advance(3200L)
        assertEquals(control.frame(), engine.frame())
    }

    @Test
    fun droppingTheEdgeReactionBackIntoTheDockDoesNotPlayShy() {
        val engine = edgeEngine()
        drag(engine, PetPoint(WIDTH - 20f, 400f))
        assertEquals(PetPhase.DOCKING, engine.phase)
        assertTrue(engine.frame().blueFish!!.bubbleFollowsAnchor)
        engine.advance(469L)
        assertStage(engine, BlueFishStage.SURPRISED)
        assertTrue(engine.frame().blueFish!!.bubbleFollowsAnchor)
        engine.advance(1L)
        assertStage(engine, BlueFishStage.PEEK)
        assertFalse(engine.frame().blueFish!!.bubbleFollowsAnchor)
        engine.advance(380L)
        assertEquals(PetStableMode.DOCKED, engine.stableMode)
        engine.advance(2500L)
        assertStage(engine, BlueFishStage.PEEK)
    }

    @Test
    fun thePeekAlreadyVisibleDuringDockingCanBePulledOutAgain() {
        val engine = freeEngine()
        drag(engine, PetPoint(WIDTH - 20f, 400f))
        engine.advance(700L)
        val before = engine.frame()
        assertEquals(PetPose.PEEK, before.pose)
        engine.press(before.anchor.x, before.anchor.y)
        engine.move(180f, 400f, 16L, true)
        assertStage(engine, BlueFishStage.SURPRISED)
        engine.advance(300L)
        engine.cancel()
        assertEquals(before, engine.frame())
        engine.advance(150L)
        assertEquals(PetStableMode.DOCKED, engine.stableMode)
    }

    @Test
    fun regrabbingShyFreezesItAndCancellationRestoresTheRemainingReaction() {
        val engine = edgeEngine()
        drag(engine, PetPoint(180f, 400f))
        engine.advance(850L)
        val before = engine.frame()
        assertStage(engine, BlueFishStage.SHY)
        engine.press(before.anchor.x, before.anchor.y)
        engine.move(150f, 350f, 16L, true)
        engine.advance(5000L)
        assertNull(engine.nextFrameDelayMs())
        assertEquals(before.blueFish, engine.frame().blueFish)
        engine.cancel()
        assertEquals(before, engine.frame())
        engine.advance(899L)
        assertStage(engine, BlueFishStage.SHY)
        engine.advance(1L)
        assertStage(engine, BlueFishStage.IDLE)
    }

    @Test
    fun repeatedlyMovingAReleasedReactionPreservesItsUnplayedShyTime() {
        val engine = edgeEngine()
        drag(engine, PetPoint(180f, 400f))
        engine.advance(850L)
        drag(engine, PetPoint(160f, 380f))
        engine.advance(100L)
        // Grab again before the previous 350ms recovery has finished.
        drag(engine, PetPoint(140f, 360f))
        engine.advance(350L)
        assertStage(engine, BlueFishStage.SHY)
        engine.advance(899L)
        assertStage(engine, BlueFishStage.SHY)
        engine.advance(1L)
        assertStage(engine, BlueFishStage.IDLE)
    }

    @Test
    fun edgeReactionSurvivesResizeAndIrregularForegroundFrameSteps() {
        val engine = edgeEngine()
        drag(engine, PetPoint(180f, 400f))
        engine.advance(300L)
        val visual = engine.frame().blueFish
        engine.resize(WIDTH * 1.5f, HEIGHT * 1.2f)
        assertEquals(visual, engine.frame().blueFish)
        engine.advance(350L)
        assertStage(engine, BlueFishStage.SHY)
        val placement = engine.placement()
        assertEquals(PetPose.FULL, placement.pose)
        engine.advance(1100L)
        assertStage(engine, BlueFishStage.IDLE)

        val batched = edgeEngine()
        val split = edgeEngine()
        drag(batched, PetPoint(180f, 400f))
        drag(split, PetPoint(180f, 400f))
        batched.advance(6400L)
        repeat(400) { split.advance(16L) }
        assertEquals(batched.frame(), split.frame())
    }

    @Test
    fun dockingHidesFrozenEatingPoseBeforeSwitchingToPeekAndFinishesAt850Ms() {
        val engine = freeEngine()
        engine.advance(14000L)
        val before = engine.frame().blueFish
        drag(engine, PetPoint(WIDTH - 20f, 400f))
        assertEquals(PetPhase.DOCKING, engine.phase)
        engine.advance(469L)
        assertEquals(before!!.copy(bubbleFollowsAnchor = true), engine.frame().blueFish)
        assertTrue(engine.frame().anchor.x - 660f * engine.definition.scale > WIDTH)
        engine.advance(1L)
        assertEquals(PetPose.PEEK, engine.frame().pose)
        assertTrue(engine.frame().anchor.x - 660f * engine.definition.scale > WIDTH)
        engine.advance(379L)
        assertEquals(PetPhase.DOCKING, engine.phase)
        engine.advance(1L)
        assertEquals(PetStableMode.DOCKED, engine.stableMode)
        assertEquals(WIDTH + 160f * engine.definition.scale, engine.frame().anchor.x, EPSILON)
    }

    @Test
    fun cancellationDuringADockRestoresItsExactRemainingDuration() {
        val engine = freeEngine()
        drag(engine, PetPoint(WIDTH - 20f, 400f))
        engine.advance(320L)
        val before = engine.frame()
        engine.press(before.anchor.x, before.anchor.y)
        engine.move(120f, 250f, 16L, true)
        engine.advance(1000L)
        engine.cancel()
        assertEquals(before, engine.frame())
        engine.advance(530L)
        assertEquals(PetStableMode.DOCKED, engine.stableMode)
    }

    @Test
    fun frameRateDoesNotChangeTheSequenceOrRandomIdleDeadline() {
        val batched = freeEngine()
        val split = freeEngine()
        batched.advance(160000L)
        repeat(10000) { split.advance(16L) }
        assertEquals(batched.frame(), split.frame())
        assertEquals(batched.placement(), split.placement())
    }

    @Test
    fun placementRestoresBeforeMeasurementAndResizePreservesTheAction() {
        val engine = BlueFishPetEngine()
        engine.restorePlacement(PetPlacement(PetPose.FULL, .5f, .7f))
        assertNull(engine.nextFrameDelayMs())
        engine.resize(WIDTH, HEIGHT)
        engine.advance(14000L)
        val visual = engine.frame().blueFish
        val previous = engine.placement()
        engine.resize(WIDTH * 2f, HEIGHT * 1.2f)
        assertEquals(visual, engine.frame().blueFish)
        assertEquals(previous.xFraction, engine.placement().xFraction, EPSILON)
        assertEquals(previous.yFraction, engine.placement().yFraction, EPSILON)
        val restored = BlueFishPetEngine()
        restored.restorePlacement(engine.placement())
        restored.resize(WIDTH * 2f, HEIGHT * 1.2f)
        assertEquals(engine.placement(), restored.placement())
        assertEquals(BlueFishStage.IDLE, restored.frame().blueFish!!.stage)
    }

    @Test
    fun releaseUsesTheLastPointerPositionAndInvalidReleaseCancels() {
        val engine = freeEngine()
        val original = engine.frame()
        engine.press(original.anchor.x, original.anchor.y)
        engine.move(original.anchor.x + 1f, original.anchor.y, 16L, true)
        assertFalse(engine.release(WIDTH - 10f, original.anchor.y))
        assertEquals(PetPhase.DOCKING, engine.phase)
        val before = engine.frame()
        engine.press(before.anchor.x, before.anchor.y)
        engine.move(100f, 200f, 32L, true)
        assertFalse(engine.release(Float.NaN, 200f))
        assertEquals(before, engine.frame())
    }

    @Test
    fun sourceScaleStaysFixedForEveryViewportAndRegisteredAssetsAreUnique() {
        val engine = edgeEngine()
        val scale = engine.definition.scale
        engine.resize(800f, 1000f)
        assertEquals(128f / 1200f, scale, EPSILON)
        assertEquals(scale, engine.definition.scale, EPSILON)
        val files = engine.definition.assetFiles
        assertEquals(15, files.size)
        assertEquals(files.size, files.toSet().size)
        assertEquals(2, engine.definition.bitmapSampleSize)
    }

    private fun edgeEngine() = BlueFishPetEngine().apply { resize(WIDTH, HEIGHT) }
    private fun freeEngine() = edgeEngine().apply { restorePlacement(PetPlacement(PetPose.FULL, .5f, .7f)) }
    private fun drag(engine: BlueFishPetEngine, target: PetPoint) {
        val start = engine.frame().anchor
        assertTrue(engine.press(start.x, start.y))
        engine.move(target.x, target.y, 16L, true)
        assertFalse(engine.release(target.x, target.y))
    }
    private fun assertStage(engine: BlueFishPetEngine, expected: BlueFishStage) = assertEquals(expected, engine.frame().blueFish!!.stage)
    private fun radians(degrees: Float) = (degrees * PI / 180.0).toFloat()

    companion object {
        private const val WIDTH = 400f
        private const val HEIGHT = 800f
        private const val EPSILON = .0001f
    }
}
