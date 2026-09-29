package io.legado.app.ui.main.chatentry.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure core contracts. View hit masks, density, lifecycle, and visual QA are separate. */
class ChatPetEngineTest {
    private val definition = GuguGagaCharacter.definition

    @Test
    fun fullCallRaisesBothHandsAndKeepsTheFeetFixed() {
        val engine = freeEngine()
        val before = engine.frame()
        engine.advance(4_500L)
        engine.advance(2_500L)
        val shouting = engine.frame()

        assertEquals(PetPose.FULL, shouting.pose)
        assertTrue(shouting.motion.leftArm > 90f)
        assertTrue(shouting.motion.rightArm < -90f)
        assertTrue(shouting.motion.shout > .9f)
        assertTrue(shouting.motion.mouth > .9f)
        assertPoint(before.anchor, shouting.anchor)
        definition.layers(PetPose.FULL).filter { it.id.endsWith("-foot") }.forEach { foot ->
            assertArrayEquals(
                definition.layerMatrix(before, foot),
                definition.layerMatrix(shouting, foot),
                EPSILON,
            )
        }
    }

    @Test
    fun actionCurvesReturnToNeutralAndRejectUnsupportedPoses() {
        listOf(PetActionId.FULL_CALL, PetActionId.WAVE, PetActionId.CURIOUS, PetActionId.YAWN)
            .forEach { id ->
                val duration = PetActionSampler.spec(id).durationMs
                assertEquals(PetMotion.ZERO, PetActionSampler.sample(id, 0L, PetPose.FULL))
                assertEquals(PetMotion.ZERO, PetActionSampler.sample(id, duration, PetPose.FULL))
                assertEquals(PetMotion.ZERO, PetActionSampler.sample(id, duration + 1L, PetPose.FULL))
                if (id != PetActionId.CURIOUS) {
                    assertEquals(PetMotion.ZERO, PetActionSampler.sample(id, duration / 2, PetPose.PEEK))
                }
            }
    }

    @Test
    fun peekRevealMovesTheHeadAndBodyTogetherThroughTheWholeCycle() {
        val engine = edgeEngine()
        val head = definition.layers(PetPose.PEEK).first { it.id == "head" }
        val body = definition.layers(PetPose.PEEK).first { it.id == "body" }
        val rest = engine.frame()
        val restHead = pivot(rest, head)
        val restBody = pivot(rest, body)
        var largestReveal = 0f

        // The first peek idle choice is randomized; wait for the real look action.
        while (engine.currentAction != PetActionId.PEEK_LOOK && engine.sceneMs < 60_000L) {
            engine.advance(40L)
        }
        assertEquals(PetActionId.PEEK_LOOK, engine.currentAction)
        repeat(131) { index ->
            if (index > 0) engine.advance(40L)
            val frame = engine.frame()
            assertEquals(PetPose.PEEK, frame.pose)
            val headPivot = pivot(frame, head)
            val bodyPivot = pivot(frame, body)
            assertEquals(restHead.x - restBody.x, headPivot.x - bodyPivot.x, EPSILON)
            assertEquals(restHead.y - restBody.y, headPivot.y - bodyPivot.y, EPSILON)
            assertEquals(rest.anchor.x - 90f * definition.scale * frame.motion.reveal, frame.anchor.x, EPSILON)
            largestReveal = maxOf(largestReveal, frame.motion.reveal)
            definition.layers(PetPose.PEEK).filter { it.parentId == "head" }.forEach { expression ->
                assertArrayEquals(
                    definition.layerMatrix(frame, head),
                    definition.layerMatrix(frame, expression),
                    EPSILON,
                )
            }
        }
        assertTrue("the test must cover an actual reveal", largestReveal > .99f)
    }

    @Test
    fun theViewOwnsTheDragThresholdAndCrossingItCannotBecomeAClickAgain() {
        val engine = freeEngine()
        val before = engine.snapshot()
        val start = engine.frame().anchor
        assertTrue(engine.press(start.x, start.y, 0L))
        engine.move(start.x + 80f, start.y, 100L, dragThresholdExceeded = false)
        assertFalse(engine.isDragging)
        assertTrue(engine.release(start.x + 80f, start.y))
        assertEquals(before, engine.snapshot())

        assertTrue(engine.press(start.x, start.y, 200L))
        engine.move(start.x + 1f, start.y, 210L, dragThresholdExceeded = true)
        assertTrue(engine.isDragging)
        engine.move(start.x, start.y, 220L, dragThresholdExceeded = false)
        assertFalse(engine.release(start.x, start.y))
        assertEquals(PetPhase.SETTLING, engine.phase)
    }

    @Test
    fun grabbingMidActionPreservesMotionAndCancellationRestoresTheSnapshot() {
        val engine = freeEngine()
        engine.advance(7_000L)
        val before = engine.snapshot()
        val frame = engine.frame()
        val grab = PetPoint(frame.anchor.x - 12f, frame.anchor.y - 20f)

        assertTrue(engine.press(grab.x, grab.y, 0L))
        engine.advance(1_000L)
        assertEquals(frame, engine.frame())
        engine.move(grab.x - 25f, grab.y + 10f, 100L, dragThresholdExceeded = true)
        assertEquals(frame.motion, engine.frame().motion)
        assertPoint(PetPoint(frame.anchor.x - 25f, frame.anchor.y + 10f), engine.frame().anchor)
        engine.advance(80L)
        engine.cancel()

        assertFalse(engine.isPressed)
        assertFalse(engine.isDragging)
        assertEquals(before, engine.snapshot())
        assertEquals(frame, engine.frame())
    }

    @Test
    fun clickingMidActionAndExplicitRestoreKeepTheOriginalProgress() {
        val engine = freeEngine()
        engine.advance(7_000L)
        val before = engine.snapshot()
        val frame = engine.frame()
        assertTrue(engine.press(frame.anchor.x, frame.anchor.y, 0L))
        engine.advance(2_000L)
        assertTrue(engine.release(frame.anchor.x, frame.anchor.y))
        assertEquals(before, engine.snapshot())
        engine.advance(1_000L)
        engine.restore(before)
        assertEquals(before, engine.snapshot())
        assertEquals(frame, engine.frame())
    }

    @Test
    fun releaseSettlesFor350MsThenWaits220MsBeforeWaving() {
        val engine = freeEngine()
        dragTo(engine, PetPoint(180f, 400f))
        val anchor = engine.frame().anchor
        assertEquals(PetPhase.SETTLING, engine.phase)
        engine.advance(349L)
        assertEquals(PetPhase.SETTLING, engine.phase)
        engine.advance(1L)
        assertEquals(PetPhase.IDLE, engine.phase)
        engine.advance(219L)
        assertEquals(PetPhase.IDLE, engine.phase)
        engine.advance(1L)
        assertEquals(PetPhase.ACTION, engine.phase)
        engine.advance(600L)
        assertTrue("release reaction is the retained wave", engine.frame().motion.rightElbow > 11f)
        assertEquals(0f, engine.frame().motion.shout, EPSILON)
        assertPoint(anchor, engine.frame().anchor)
    }

    @Test
    fun cancellingANewDragRestoresThePendingReleaseReaction() {
        val engine = freeEngine()
        dragTo(engine, PetPoint(180f, 400f))
        engine.advance(400L)
        val before = engine.snapshot()
        val anchor = engine.frame().anchor
        assertTrue(engine.press(anchor.x, anchor.y, 1_000L))
        engine.move(anchor.x + 25f, anchor.y, 1_100L, dragThresholdExceeded = true)
        engine.advance(300L)
        engine.cancel()
        assertEquals(before, engine.snapshot())
        engine.advance(170L)
        assertEquals(PetPhase.ACTION, engine.phase)
        engine.advance(600L)
        assertTrue(engine.frame().motion.rightElbow > 11f)
    }

    @Test
    fun dockingSwitchesPosesOnlyWhenBothAreOutsideTheViewport() {
        val engine = freeEngine()
        dragTo(engine, PetPoint(WIDTH - 20f, 400f))
        assertEquals(PetPhase.DOCKING, engine.phase)
        engine.advance(469L)
        assertEquals(PetPose.FULL, engine.frame().pose)
        assertEntirelyRightOfViewport(engine.frame())
        engine.advance(1L)
        assertEquals(PetPose.PEEK, engine.frame().pose)
        assertEntirelyRightOfViewport(engine.frame())
        engine.advance(379L)
        assertEquals(PetPhase.DOCKING, engine.phase)
        engine.advance(1L)
        assertEquals(PetStableMode.DOCKED, engine.stableMode)
        assertEquals(PetPhase.IDLE, engine.phase)
        assertEquals(definition.dockAnchorX(WIDTH), engine.frame().anchor.x, EPSILON)
    }

    @Test
    fun normalizedPlacementRestoresAcrossViewportSizes() {
        val engine = freeEngine()
        dragTo(engine, PetPoint(180f, 400f))
        engine.advance(350L)
        val placement = engine.placement()
        val restored = ChatPetEngine(definition)
        restored.resize(WIDTH * 2f, HEIGHT * 2f)
        restored.restorePlacement(placement)

        assertEquals(PetStableMode.FREE, restored.stableMode)
        assertEquals(placement.xFraction, restored.placement().xFraction, EPSILON)
        assertEquals(placement.yFraction, restored.placement().yFraction, EPSILON)
        assertEquals(engine.frame().anchor.x * 2f, restored.frame().anchor.x, EPSILON)
        assertEquals(engine.frame().anchor.y * 2f, restored.frame().anchor.y, EPSILON)

        engine.resetToEdge()
        val docked = engine.placement()
        restored.restorePlacement(docked)
        assertEquals(PetStableMode.DOCKED, restored.stableMode)
        assertEquals(definition.dockAnchorX(WIDTH * 2f), restored.frame().anchor.x, EPSILON)
        assertEquals(docked.yFraction, restored.placement().yFraction, EPSILON)
    }

    @Test
    fun naturalFullActionsStartWithCallAndAlternateWithSmallActions() {
        val engine = freeEngine()
        engine.advance(4_499L)
        assertEquals(PetPhase.IDLE, engine.phase)
        engine.advance(1L)
        assertEquals(PetActionId.FULL_CALL, engine.currentAction)
        engine.advance(5_200L)
        assertEquals(PetPhase.IDLE, engine.phase)

        val starts = mutableListOf<PetActionId>()
        var previousAction: PetActionId? = null
        repeat(2_000) {
            engine.advance(100L)
            val action = engine.currentAction
            if (action != null && previousAction == null) starts.add(action)
            previousAction = action
        }
        assertTrue("observe several natural transitions", starts.size >= 4)
        starts.forEachIndexed { index, id ->
            if (index % 2 == 0) {
                assertTrue(id in setOf(PetActionId.WAVE, PetActionId.CURIOUS, PetActionId.YAWN))
            } else {
                assertEquals(PetActionId.FULL_CALL, id)
            }
        }
    }

    @Test
    fun placementSavedBeforeLayoutIsAppliedOnceTheViewportExists() {
        val engine = ChatPetEngine(definition)
        val placement = PetPlacement(PetPose.FULL, .4f, .6f)
        engine.restorePlacement(placement)
        assertEquals(placement, engine.placement())
        assertFalse(engine.press(100f, 100f))
        engine.resize(WIDTH, HEIGHT)
        assertEquals(PetStableMode.FREE, engine.stableMode)
        assertPoint(PetPoint(WIDTH * .4f, HEIGHT * .6f), engine.frame().anchor)
        assertEquals(placement, engine.placement())
    }

    private fun freeEngine() = edgeEngine().apply {
        restorePlacement(PetPlacement(PetPose.FULL, .5f, .7f))
    }

    private fun edgeEngine() = ChatPetEngine(definition).apply {
        resize(WIDTH, HEIGHT)
        resetToEdge()
    }

    private fun dragTo(engine: ChatPetEngine, destination: PetPoint) {
        val anchor = engine.frame().anchor
        assertTrue(engine.press(anchor.x, anchor.y, 0L))
        engine.move(destination.x, destination.y, 100L, dragThresholdExceeded = true)
        assertFalse(engine.release(destination.x, destination.y))
    }

    private fun pivot(frame: PetFrame, layer: PetLayerDefinition): PetPoint =
        map(definition.layerMatrix(frame, layer), layer.pivotX, layer.pivotY)

    private fun map(matrix: FloatArray, x: Float, y: Float) = PetPoint(
        matrix[0] * x + matrix[2] * y + matrix[4],
        matrix[1] * x + matrix[3] * y + matrix[5],
    )

    private fun assertPoint(expected: PetPoint, actual: PetPoint) {
        assertEquals(expected.x, actual.x, EPSILON)
        assertEquals(expected.y, actual.y, EPSILON)
    }

    private fun assertEntirelyRightOfViewport(frame: PetFrame) {
        definition.layers(frame.pose).forEach { layer ->
            val matrix = definition.layerMatrix(frame, layer)
            listOf(0f to 0f, layer.width to 0f, 0f to layer.height, layer.width to layer.height)
                .forEach { (x, y) -> assertTrue("${frame.pose}/${layer.id} must be hidden", map(matrix, x, y).x >= WIDTH) }
        }
    }

    companion object {
        private const val WIDTH = 390f
        private const val HEIGHT = 680f
        private const val EPSILON = .001f
    }
}
