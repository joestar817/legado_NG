package io.legado.plugins.pets.layered.core

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
        repeat(5) {
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
    fun grabbingFoodFreezesUntilDraggingAndCancellationRestoresFutureScheduling() {
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
        assertStage(engine, BlueFishStage.SUSPENDED)
        assertEquals(original.anchor.x - 25f, engine.frame().anchor.x, EPSILON)
        assertEquals(grab.y + 10f + BlueFishPetEngine.SUSPENDED_PIN_TO_FEET_DP, engine.frame().anchor.y, EPSILON)
        assertEquals(0L, engine.frame().blueFish!!.struggleElapsedMs)
        assertEquals(16L, engine.nextFrameDelayMs())
        engine.advance(3000L)
        assertEquals(3000L, engine.frame().blueFish!!.struggleElapsedMs)
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
    fun everyReleaseLandsFor380MsThenRelaxesFor2100MsBeforeTheFreeWait() {
        val engine = edgeEngine()
        engine.advance(6300L)
        drag(engine, PetPoint(180f, 400f))
        assertEquals(PetPhase.SETTLING, engine.phase)
        assertEquals(PetPose.FULL, engine.frame().pose)
        assertStage(engine, BlueFishStage.LANDING)
        assertEquals(0f, engine.frame().blueFish!!.landingProgress, EPSILON)
        val startY = engine.frame().anchor.y
        engine.advance(190L)
        assertEquals(startY + 23f * .25f, engine.frame().anchor.y, EPSILON)
        assertEquals(.5f, engine.frame().blueFish!!.landingProgress, EPSILON)
        assertEquals(23f * .75f / engine.definition.scale, engine.frame().blueFish!!.landingOffsetY, EPSILON)
        engine.advance(189L)
        assertStage(engine, BlueFishStage.LANDING)
        engine.advance(1L)
        assertStage(engine, BlueFishStage.RELIEF)
        assertEquals(0L, engine.frame().blueFish!!.actionElapsedMs)
        assertEquals(startY + 23f, engine.frame().anchor.y, EPSILON)
        engine.advance(2099L)
        assertStage(engine, BlueFishStage.RELIEF)
        assertEquals(2099L, engine.frame().blueFish!!.actionElapsedMs)
        engine.advance(1L)
        assertStage(engine, BlueFishStage.IDLE)
        assertEquals(PetStableMode.FREE, engine.stableMode)
        engine.advance(3999L)
        assertStage(engine, BlueFishStage.IDLE)
        engine.advance(1L)
        assertStage(engine, BlueFishStage.THINKING)
    }

    @Test
    fun anEdgeClickNeverChangesPoseAndCrossingTheThresholdBeginsStruggling() {
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
        assertStage(engine, BlueFishStage.SUSPENDED)
        assertEquals(PetPoint(180f, 400f + BlueFishPetEngine.SUSPENDED_PIN_TO_FEET_DP), engine.frame().anchor)
        assertEquals(16L, engine.nextFrameDelayMs())
        assertNull(engine.frame().blueFish!!.arm)
        engine.advance(500L)
        assertEquals(7000L, engine.sceneMs)
        assertStage(engine, BlueFishStage.SUSPENDED)
        assertEquals(500L, engine.frame().blueFish!!.struggleElapsedMs)
    }

    @Test
    fun longDragKeepsItsContinuousStruggleClockThroughLanding() {
        val engine = edgeEngine()
        val before = engine.frame()
        engine.press(before.anchor.x, before.anchor.y)
        engine.move(180f, 400f, 16L, true)
        engine.advance(2000L)
        assertStage(engine, BlueFishStage.SUSPENDED)
        assertEquals(2000L, engine.frame().blueFish!!.struggleElapsedMs)
        engine.release(180f, 400f)
        assertEquals(2000L, engine.frame().blueFish!!.struggleElapsedMs)
        engine.advance(379L)
        assertStage(engine, BlueFishStage.LANDING)
        assertEquals(2379L, engine.frame().blueFish!!.struggleElapsedMs)
        engine.advance(1L)
        assertStage(engine, BlueFishStage.RELIEF)
        engine.advance(2100L)
        assertStage(engine, BlueFishStage.IDLE)
    }

    @Test
    fun draggingAtVerticalEdgesKeepsBothFeetInsideAndNeverLandsUpward() {
        for (y in listOf(0f, HEIGHT)) {
            val engine = freeEngine()
            val before = engine.frame().anchor
            engine.press(before.x, before.y - 90f)
            engine.move(180f, y, 16L, true)
            val heldY = engine.frame().anchor.y
            assertTrue(heldY >= BlueFishCharacter.HEIGHT_DP + 18f)
            assertTrue(heldY <= HEIGHT - 24f)
            engine.release(180f, y)
            assertTrue(engine.frame().blueFish!!.landingOffsetY >= 0f)
            assertTrue(engine.frame().blueFish!!.landingOffsetY <= 23f / engine.definition.scale + EPSILON)
            engine.advance(380L)
            assertTrue(engine.frame().anchor.y >= heldY)
            assertTrue(engine.frame().anchor.y <= HEIGHT - 24f)
        }
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
    fun droppingBackIntoTheDockHidesTheHeldPoseAndSkipsTheLandingReaction() {
        val engine = edgeEngine()
        drag(engine, PetPoint(WIDTH - 20f, 400f))
        assertEquals(PetPhase.DOCKING, engine.phase)
        assertTrue(engine.frame().blueFish!!.bubbleFollowsAnchor)
        engine.advance(469L)
        assertStage(engine, BlueFishStage.SUSPENDED)
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
        assertStage(engine, BlueFishStage.SUSPENDED)
        engine.advance(300L)
        engine.cancel()
        assertEquals(before, engine.frame())
        engine.advance(150L)
        assertEquals(PetStableMode.DOCKED, engine.stableMode)
    }

    @Test
    fun regrabbingReliefStartsANewStruggleAndCancellationRestoresRemainingRelief() {
        val engine = edgeEngine()
        drag(engine, PetPoint(180f, 400f))
        engine.advance(850L)
        val before = engine.frame()
        assertStage(engine, BlueFishStage.RELIEF)
        engine.press(before.anchor.x, before.anchor.y)
        engine.advance(1000L)
        assertEquals(before, engine.frame())
        engine.move(150f, 350f, 16L, true)
        assertStage(engine, BlueFishStage.SUSPENDED)
        assertEquals(0L, engine.frame().blueFish!!.struggleElapsedMs)
        engine.advance(5000L)
        assertEquals(16L, engine.nextFrameDelayMs())
        assertEquals(5000L, engine.frame().blueFish!!.struggleElapsedMs)
        engine.cancel()
        assertEquals(before, engine.frame())
        engine.advance(1629L)
        assertStage(engine, BlueFishStage.RELIEF)
        engine.advance(1L)
        assertStage(engine, BlueFishStage.IDLE)
    }

    @Test
    fun draggingAgainDuringLandingStartsANewCompleteLandingAndRelief() {
        val engine = edgeEngine()
        drag(engine, PetPoint(180f, 400f))
        engine.advance(850L)
        drag(engine, PetPoint(160f, 380f))
        engine.advance(100L)
        // Grab again before the previous landing has finished.
        drag(engine, PetPoint(140f, 360f))
        assertEquals(0L, engine.frame().blueFish!!.struggleElapsedMs)
        engine.advance(380L)
        assertStage(engine, BlueFishStage.RELIEF)
        assertEquals(0L, engine.frame().blueFish!!.actionElapsedMs)
        engine.advance(2099L)
        assertStage(engine, BlueFishStage.RELIEF)
        engine.advance(1L)
        assertStage(engine, BlueFishStage.IDLE)
    }

    @Test
    fun landingSurvivesResizeAndIrregularForegroundFrameSteps() {
        val engine = edgeEngine()
        drag(engine, PetPoint(180f, 400f))
        engine.advance(300L)
        val visual = engine.frame().blueFish
        engine.resize(WIDTH * 1.5f, HEIGHT * 1.2f)
        assertEquals(visual!!.stage, engine.frame().blueFish!!.stage)
        assertEquals(visual.landingProgress, engine.frame().blueFish!!.landingProgress, EPSILON)
        assertEquals(visual.struggleElapsedMs, engine.frame().blueFish!!.struggleElapsedMs)
        engine.advance(80L)
        assertStage(engine, BlueFishStage.RELIEF)
        val placement = engine.placement()
        assertEquals(PetPose.FULL, placement.pose)
        engine.advance(2100L)
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
    fun triplingViewportHeightDoesNotMagnifyTheRemainingLandingDrop() {
        val engine = freeEngine()
        drag(engine, PetPoint(180f, 400f))
        engine.advance(247L)
        val before = engine.frame().blueFish!!
        engine.resize(WIDTH, HEIGHT * 3f)
        val after = engine.frame().blueFish!!
        assertEquals(before.landingProgress, after.landingProgress, EPSILON)
        assertEquals(before.struggleElapsedMs, after.struggleElapsedMs)
        assertEquals(before.actionElapsedMs, after.actionElapsedMs)
        val remainingFraction = 1f - after.landingProgress * after.landingProgress
        val fullDrop = after.landingOffsetY * engine.definition.scale / remainingFraction
        assertTrue(fullDrop in 0f..23.001f)
        val currentY = engine.frame().anchor.y
        engine.advance(133L)
        assertStage(engine, BlueFishStage.RELIEF)
        assertEquals(currentY + after.landingOffsetY * engine.definition.scale, engine.frame().anchor.y, .001f)
    }

    @Test
    fun dockingHidesTheNewSuspendedPoseBeforeSwitchingToPeekAndFinishesAt850Ms() {
        val engine = freeEngine()
        engine.advance(14000L)
        drag(engine, PetPoint(WIDTH - 20f, 400f))
        assertEquals(PetPhase.DOCKING, engine.phase)
        engine.advance(469L)
        assertStage(engine, BlueFishStage.SUSPENDED)
        assertTrue(engine.frame().blueFish!!.bubbleFollowsAnchor)
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
    fun dockedScooterRunsOnceThenWaitsAtLeastNinetySecondsWithoutSnappingThePeek() {
        val engine = edgeEngine()
        val rest = engine.frame().anchor
        engine.advance(BlueFishPetEngine.FIRST_DOCK_ACTION_MS - 1L)
        assertEquals(PetPhase.IDLE, engine.phase)
        engine.advance(1L)
        assertEquals(PetPhase.ACTION, engine.phase)
        assertEquals(rest, engine.frame().anchor)
        engine.advance(3000L)
        assertStage(engine, BlueFishStage.SCOOTER)
        engine.advance(BlueFishScooterMotion.DURATION_MS - 3001L)
        assertEquals(PetPhase.ACTION, engine.phase)
        engine.advance(1L)
        assertEquals(PetPhase.IDLE, engine.phase)
        assertEquals(rest, engine.frame().anchor)
        engine.advance(BlueFishPetEngine.DOCK_COOLDOWN_MS - 1L)
        assertEquals(PetPhase.IDLE, engine.phase)
        var waited = BlueFishPetEngine.DOCK_COOLDOWN_MS - 1L
        var lastIdle = engine.frame()
        while (engine.phase == PetPhase.IDLE && waited <= 150000L) {
            lastIdle = engine.frame()
            engine.advance(1L)
            waited++
        }
        assertTrue(waited in 90000L..150000L)
        assertEquals(PetPhase.ACTION, engine.phase)
        assertEquals(lastIdle.anchor.x, engine.frame().anchor.x, .001f)
        assertEquals(rest, engine.frame().anchor)
    }

    @Test
    fun scooterPlacementStaysAtTheSavedDockForTheWholeTrip() {
        val engine = edgeEngine()
        val placement = engine.placement()
        engine.advance(BlueFishPetEngine.FIRST_DOCK_ACTION_MS)
        repeat(107) {
            assertEquals(PetStableMode.DOCKED, engine.stableMode)
            assertEquals(placement, engine.placement())
            engine.advance(100L)
        }
        assertEquals(PetPhase.IDLE, engine.phase)
        assertEquals(placement, engine.placement())
    }

    @Test
    fun scooterPressClickAndDragCancellationRestoreTheExactSceneAndDeadline() {
        val engine = edgeEngine()
        val control = edgeEngine()
        engine.advance(18000L)
        control.advance(18000L)
        val before = engine.frame()
        val placement = engine.placement()
        assertStage(engine, BlueFishStage.SCOOTER)
        engine.press(before.anchor.x, before.anchor.y - 50f)
        assertNull(engine.nextFrameDelayMs())
        engine.advance(8000L)
        assertTrue(engine.release(before.anchor.x, before.anchor.y - 50f))
        assertEquals(before, engine.frame())
        assertEquals(18000L, engine.sceneMs)
        engine.press(before.anchor.x, before.anchor.y - 50f)
        engine.move(160f, 330f, 16L, true)
        assertStage(engine, BlueFishStage.SUSPENDED)
        engine.advance(3000L)
        assertEquals(placement, engine.placement())
        engine.cancel()
        assertEquals(before, engine.frame())
        assertEquals(18000L, engine.sceneMs)
        engine.advance(135000L)
        control.advance(135000L)
        assertEquals(control.frame(), engine.frame())
        assertEquals(control.placement(), engine.placement())
    }

    @Test
    fun droppingDuringScooterReplacesTheRideWithTheCompleteLandingReaction() {
        val engine = edgeEngine()
        engine.advance(18000L)
        drag(engine, PetPoint(180f, 400f))
        assertEquals(PetStableMode.FREE, engine.stableMode)
        assertStage(engine, BlueFishStage.LANDING)
        assertEquals(PetPose.FULL, engine.placement().pose)
        engine.advance(380L)
        assertStage(engine, BlueFishStage.RELIEF)
        engine.advance(2100L)
        assertStage(engine, BlueFishStage.IDLE)
        engine.advance(4000L)
        assertStage(engine, BlueFishStage.THINKING)
    }

    @Test
    fun dockingAfterAnInterruptedRideUsesTheRareCooldownInsteadOfAnotherEarlyDemo() {
        val engine = edgeEngine()
        engine.advance(18000L)
        drag(engine, PetPoint(WIDTH - 20f, 400f))
        assertEquals(PetPhase.DOCKING, engine.phase)
        engine.advance(BlueFishPetEngine.DOCK_MS)
        assertEquals(PetStableMode.DOCKED, engine.stableMode)
        engine.advance(89999L)
        assertEquals(PetPhase.IDLE, engine.phase)
        assertStage(engine, BlueFishStage.PEEK)
    }

    @Test
    fun shortNewActionsUseDisplayFramesWhileOriginalIdleAndFoodCadenceStayUnchanged() {
        val engine = freeEngine()
        assertEquals(33L, engine.nextFrameDelayMs())
        engine.advance(14000L)
        assertEquals(33L, engine.nextFrameDelayMs())
        drag(engine, PetPoint(180f, 400f))
        assertEquals(16L, engine.nextFrameDelayMs())
        engine.advance(380L)
        assertEquals(16L, engine.nextFrameDelayMs())
        engine.advance(2100L)
        assertEquals(33L, engine.nextFrameDelayMs())
        val edge = edgeEngine()
        edge.advance(15000L)
        assertEquals(PetPhase.ACTION, edge.phase)
        assertStage(edge, BlueFishStage.PEEK)
        assertEquals(16L, edge.nextFrameDelayMs())
        edge.advance(BlueFishScooterMotion.DURATION_MS)
        assertEquals(33L, edge.nextFrameDelayMs())
    }

    @Test
    fun resizingDuringScooterReturnsToTheSavedEdgeAndStartsANewCooldown() {
        val engine = edgeEngine()
        val saved = engine.placement()
        engine.advance(19000L)
        assertStage(engine, BlueFishStage.SCOOTER)
        engine.resize(WIDTH * 1.5f, HEIGHT * 1.2f)
        assertEquals(PetStableMode.DOCKED, engine.stableMode)
        assertEquals(PetPhase.IDLE, engine.phase)
        assertStage(engine, BlueFishStage.PEEK)
        assertEquals(saved.yFraction, engine.placement().yFraction, EPSILON)
        assertEquals(1f, engine.placement().xFraction, EPSILON)
        engine.advance(89999L)
        assertEquals(PetPhase.IDLE, engine.phase)
    }

    @Test
    fun bottomDockMovesOnlyTheOffscreenScooterGroundToKeepTheTurnInsideTheViewport() {
        val engine = edgeEngine()
        engine.restorePlacement(PetPlacement(PetPose.PEEK, 1f, 1f))
        val dock = engine.frame().anchor
        assertEquals(HEIGHT - 24f, dock.y, EPSILON)
        engine.advance(15000L)
        assertEquals(dock, engine.frame().anchor)
        engine.advance(5750L)
        assertStage(engine, BlueFishStage.SCOOTER)
        assertEquals(HEIGHT - 36f + BlueFishCharacter.HEIGHT_DP * .2f, engine.frame().anchor.y, EPSILON)
        assertTrue(engine.frame().anchor.y < HEIGHT - 10f)
        engine.advance(BlueFishScooterMotion.DURATION_MS - 5750L)
        assertEquals(dock, engine.frame().anchor)
    }

    @Test
    fun frameRateDoesNotChangeDockToursOrTheirRandomCooldowns() {
        val batched = edgeEngine()
        val split = edgeEngine()
        batched.advance(640000L)
        repeat(40000) { split.advance(16L) }
        assertEquals(batched.frame(), split.frame())
        assertEquals(batched.placement(), split.placement())
        assertEquals(batched.phase, split.phase)
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
        assertTrue(files.size > 15)
        assertFalse(files.contains("drag-surprised.webp"))
        assertFalse(files.contains("release-shy.webp"))
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
