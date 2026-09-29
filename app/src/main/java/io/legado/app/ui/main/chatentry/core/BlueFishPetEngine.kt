package io.legado.app.ui.main.chatentry.core

import kotlin.math.roundToLong

/**
 * Blue-fish v3 interaction state. The host owns alpha hit testing, the drag
 * threshold, lifecycle suspension and click navigation, just as for other pets.
 */
class BlueFishPetEngine : PetController {
    override val definition = BlueFishCharacter.definition
    override var widthDp = 0f
        private set
    override var heightDp = 0f
        private set

    private data class EdgeReaction(val stage: BlueFishStage, val startedAt: Long, val endsAt: Long) {
        fun shifted(deltaMs: Long) = copy(startedAt = startedAt + deltaMs, endsAt = endsAt + deltaMs)
    }

    private data class State(
        val clock: Long = 0L,
        val mode: PetStableMode = PetStableMode.DOCKED,
        val phase: PetPhase = PetPhase.IDLE,
        val phaseStartedAt: Long = 0L,
        val position: PetPoint = PetPoint(0f, 0f),
        val nextActionAt: Long = FIRST_ACTION_MS,
        val edgeStartedAt: Long = 0L,
        val randomSeed: Int = 8675309,
        val transitionFrom: PetFrame? = null,
        val transitionTarget: PetPoint? = null,
        val reaction: EdgeReaction? = null,
        val reactionAfterRecover: EdgeReaction? = null,
    )

    private data class Pointer(
        val before: State,
        val frozen: PetFrame,
        val grabOffset: PetPoint,
        val dragging: Boolean = false,
        val pullsFromEdge: Boolean = false,
        val reactionToResume: EdgeReaction? = null,
    )

    private var state = State()
    private var pointer: Pointer? = null
    private var pendingPlacement: PetPlacement? = null

    override val isPressed get() = pointer != null
    override val isDragging get() = pointer?.dragging == true
    val stableMode get() = state.mode
    val phase get() = if (pointer != null && !isDragging) PetPhase.PRESSED else state.phase
    val sceneMs get() = state.clock

    override fun frame(): PetFrame {
        pointer?.let {
            return when {
                !it.dragging -> it.frozen
                it.pullsFromEdge -> makeFrame(state.position, BlueFishMotion.surprised(state.clock - state.reaction!!.startedAt))
                else -> it.frozen.copy(anchor = state.position)
            }
        }
        val from = state.transitionFrom
        val elapsed = state.clock - state.phaseStartedAt
        if (from != null && state.phase == PetPhase.DOCKING) {
            val hidden = PetPoint(widthDp + HIDDEN_SOURCE_OFFSET * definition.scale, safeY(from.anchor.y))
            val y = mix(from.anchor.y, hidden.y, BlueFishMotion.ease(elapsed / DOCK_MS.toFloat()))
            return if (elapsed < DOCK_SWITCH_MS) {
                val x = mix(from.anchor.x, hidden.x, BlueFishMotion.ease(elapsed / DOCK_SWITCH_MS.toFloat()))
                from.copy(anchor = PetPoint(x, y), blueFish = from.blueFish?.copy(bubbleFollowsAnchor = true))
            } else {
                val x = mix(hidden.x, definition.dockAnchorX(widthDp), BlueFishMotion.ease((elapsed - DOCK_SWITCH_MS) / (DOCK_MS - DOCK_SWITCH_MS).toFloat()))
                makeFrame(PetPoint(x, y), BlueFishVisual(BlueFishStage.PEEK))
            }
        }
        if (from != null && state.phase == PetPhase.SETTLING) {
            val anchor = mix(from.anchor, state.transitionTarget!!, BlueFishMotion.ease(elapsed / RECOVER_MS.toFloat()))
            return state.reaction?.let { makeFrame(anchor, reactionVisual(it)) } ?: from.copy(anchor = anchor)
        }
        state.reaction?.let { return makeFrame(state.position, reactionVisual(it)) }
        if (state.mode == PetStableMode.DOCKED) {
            val edgeElapsed = state.clock - state.edgeStartedAt
            val reveal = BlueFishMotion.edgeReveal(edgeElapsed)
            val anchor = PetPoint(widthDp + (BlueFishCharacter.ROOT_X - reveal) * definition.scale, state.position.y)
            return makeFrame(anchor, BlueFishMotion.peek(state.clock, edgeElapsed))
        }
        val visual = if (state.phase == PetPhase.ACTION) BlueFishMotion.sequence(state.clock, elapsed) else BlueFishMotion.idle(state.clock)
        return makeFrame(state.position, visual)
    }

    override fun resize(widthDp: Float, heightDp: Float) {
        require(widthDp.isFinite() && heightDp.isFinite() && widthDp >= 0f && heightDp >= 0f)
        cancel()
        if (widthDp == 0f || heightDp == 0f) return
        val oldWidth = this.widthDp
        val oldHeight = this.heightDp
        val current = frame().anchor
        this.widthDp = widthDp
        this.heightDp = heightDp
        if (oldWidth == 0f || oldHeight == 0f) {
            val placement = pendingPlacement
            pendingPlacement = null
            if (placement == null) {
                state = State(position = PetPoint(definition.dockAnchorX(widthDp), safeY(heightDp * .70f + BlueFishCharacter.HEIGHT_DP * .2f)))
            } else applyPlacement(placement)
            return
        }
        if (oldWidth == widthDp && oldHeight == heightDp) return
        val transitioning = state.phase == PetPhase.DOCKING || state.phase == PetPhase.SETTLING
        val keepsReaction = state.reaction != null || state.reactionAfterRecover != null
        val docked = state.mode == PetStableMode.DOCKED && !transitioning
        fun reposition(point: PetPoint) = PetPoint(safeX(point.x / oldWidth * widthDp), safeY(point.y / oldHeight * heightDp))
        state = state.copy(
            mode = if (docked) PetStableMode.DOCKED else PetStableMode.FREE,
            phase = if (transitioning && !keepsReaction) PetPhase.IDLE else state.phase,
            position = PetPoint(
                if (docked) definition.dockAnchorX(widthDp) else safeX(current.x / oldWidth * widthDp),
                safeY(current.y / oldHeight * heightDp),
            ),
            nextActionAt = if (transitioning && !keepsReaction) state.clock + FIRST_ACTION_MS else state.nextActionAt,
            transitionFrom = if (keepsReaction) state.transitionFrom?.let { it.copy(anchor = reposition(it.anchor)) } else null,
            transitionTarget = if (keepsReaction) state.transitionTarget?.let(::reposition) else null,
        )
    }

    override fun press(x: Float, y: Float, eventTimeMs: Long): Boolean {
        if (pointer != null || widthDp <= 0f || heightDp <= 0f || !x.isFinite() || !y.isFinite()) return false
        val frozen = frame()
        // During a previous recovery the saved reaction has not resumed yet.
        // Normalize it to this clock so another drag cannot consume its hold time.
        val resume = state.reaction ?: state.reactionAfterRecover?.shifted(state.clock - state.phaseStartedAt)
        pointer = Pointer(state, frozen, PetPoint(x - frozen.anchor.x, y - frozen.anchor.y), reactionToResume = resume)
        return true
    }

    override fun move(x: Float, y: Float, eventTimeMs: Long, dragThresholdExceeded: Boolean) {
        val active = pointer ?: return
        if (!x.isFinite() || !y.isFinite()) return
        val dragging = active.dragging || dragThresholdExceeded
        val startsEdgePull = !active.dragging && dragging && active.frozen.pose == PetPose.PEEK &&
            (active.before.mode == PetStableMode.DOCKED || active.before.phase == PetPhase.DOCKING)
        pointer = active.copy(dragging = dragging, pullsFromEdge = active.pullsFromEdge || startsEdgePull)
        if (!dragging) return
        state = state.copy(
            mode = PetStableMode.FREE,
            phase = PetPhase.DRAGGING,
            position = dragPosition(x, y, active),
            transitionFrom = null,
            transitionTarget = null,
            reaction = if (startsEdgePull) EdgeReaction(BlueFishStage.SURPRISED, state.clock, state.clock + BlueFishMotion.SURPRISED_MS) else state.reaction,
            reactionAfterRecover = null,
        )
    }

    /** Returns true only for a click, after restoring the complete pre-press state. */
    override fun release(x: Float, y: Float): Boolean {
        val active = pointer ?: return false
        if (!x.isFinite() || !y.isFinite() || x < 0f || y < 0f || y > heightDp) {
            cancel()
            return false
        }
        if (!active.dragging) {
            cancel()
            return true
        }
        val position = dragPosition(x, y, active)
        val from = frame().copy(anchor = position)
        pointer = null
        val docking = position.x >= widthDp - DOCK_ZONE_DP
        state = state.copy(
            position = position,
            mode = PetStableMode.FREE,
            phase = if (docking) PetPhase.DOCKING else PetPhase.SETTLING,
            phaseStartedAt = state.clock,
            transitionFrom = from,
            transitionTarget = if (docking) PetPoint(definition.dockAnchorX(widthDp), safeY(position.y)) else PetPoint(safeX(position.x), safeY(position.y)),
            reaction = if (!docking && active.pullsFromEdge) state.reaction!!.let {
                it.copy(endsAt = maxOf(it.endsAt, state.clock + RECOVER_MS))
            } else null,
            reactionAfterRecover = if (!docking && !active.pullsFromEdge) active.reactionToResume else null,
        )
        return false
    }

    override fun cancel() {
        val active = pointer ?: return
        state = active.before
        pointer = null
    }

    /** Existing poses freeze on grab; only the explicit edge-pull reaction ticks while dragged. */
    override fun advance(deltaMs: Long) {
        require(deltaMs >= 0L)
        if (widthDp <= 0f || heightDp <= 0f || deltaMs == 0L) return
        pointer?.let {
            if (it.dragging && it.pullsFromEdge) state = state.copy(clock = state.clock + deltaMs)
            return
        }
        state = state.copy(clock = state.clock + deltaMs)
        // Anchor transitions to deadlines so irregular foreground frames do not
        // skip a bite, extend a phase, or change the next random idle deadline.
        while (true) {
            val elapsed = state.clock - state.phaseStartedAt
            val reaction = state.reaction
            when {
                reaction != null && state.clock >= reaction.endsAt -> {
                    val end = reaction.endsAt
                    val next = if (reaction.stage == BlueFishStage.SURPRISED) {
                        EdgeReaction(BlueFishStage.SHY, end, end + BlueFishMotion.SHY_MS)
                    } else null
                    state = state.copy(
                        phase = if (next != null) PetPhase.ACTION else PetPhase.IDLE,
                        phaseStartedAt = end, position = state.transitionTarget ?: state.position,
                        reaction = next, nextActionAt = end + FIRST_ACTION_MS,
                        transitionFrom = null, transitionTarget = null,
                    )
                }
                reaction != null -> return
                state.phase == PetPhase.DOCKING && elapsed >= DOCK_MS -> {
                    val end = state.phaseStartedAt + DOCK_MS
                    state = state.copy(mode = PetStableMode.DOCKED, phase = PetPhase.IDLE, phaseStartedAt = end,
                        edgeStartedAt = end, position = state.transitionTarget!!, transitionFrom = null, transitionTarget = null)
                }
                state.phase == PetPhase.SETTLING && elapsed >= RECOVER_MS -> {
                    val end = state.phaseStartedAt + RECOVER_MS
                    val resume = state.reactionAfterRecover?.shifted(RECOVER_MS)
                    state = state.copy(mode = PetStableMode.FREE, phase = if (resume != null) PetPhase.ACTION else PetPhase.IDLE, phaseStartedAt = end,
                        position = state.transitionTarget!!, nextActionAt = end + FIRST_ACTION_MS, transitionFrom = null, transitionTarget = null,
                        reaction = resume, reactionAfterRecover = null)
                }
                state.mode == PetStableMode.FREE && state.phase == PetPhase.IDLE && state.clock >= state.nextActionAt -> {
                    state = state.copy(phase = PetPhase.ACTION, phaseStartedAt = state.nextActionAt)
                }
                state.phase == PetPhase.ACTION && elapsed >= BlueFishMotion.SEQUENCE_MS -> {
                    val end = state.phaseStartedAt + BlueFishMotion.SEQUENCE_MS
                    val seed = state.randomSeed * 1664525 + 1013904223
                    val random = (seed.toLong() and 0xffffffffL) / 4294967296.0
                    state = state.copy(phase = PetPhase.IDLE, phaseStartedAt = end, randomSeed = seed,
                        nextActionAt = end + 12000L + (random * 6000.0).roundToLong())
                }
                else -> return
            }
        }
    }

    override fun nextFrameDelayMs(): Long? {
        if (widthDp <= 0f || heightDp <= 0f) return null
        val active = pointer
        if (active != null && (!active.dragging || !active.pullsFromEdge)) return null
        return 33L
    }

    override fun placement(): PetPlacement {
        pendingPlacement?.let { return it }
        val saved = pointer?.before ?: state
        val docked = saved.mode == PetStableMode.DOCKED || saved.phase == PetPhase.DOCKING
        val position = saved.transitionTarget ?: saved.position
        return PetPlacement(
            if (docked) PetPose.PEEK else PetPose.FULL,
            if (docked || widthDp <= 0f) 1f else position.x / widthDp,
            if (heightDp <= 0f) .70f else position.y / heightDp,
        )
    }

    override fun restorePlacement(placement: PetPlacement) {
        require(placement.xFraction.isFinite() && placement.yFraction.isFinite())
        cancel()
        if (widthDp <= 0f || heightDp <= 0f) pendingPlacement = placement else applyPlacement(placement)
    }

    private fun applyPlacement(placement: PetPlacement) {
        pendingPlacement = null
        val docked = placement.pose == PetPose.PEEK
        state = State(
            mode = if (docked) PetStableMode.DOCKED else PetStableMode.FREE,
            position = PetPoint(if (docked) definition.dockAnchorX(widthDp) else safeX(placement.xFraction * widthDp), safeY(placement.yFraction * heightDp)),
        )
    }

    private fun makeFrame(anchor: PetPoint, visual: BlueFishVisual) = PetFrame(
        if (visual.stage == BlueFishStage.PEEK) PetPose.PEEK else PetPose.FULL,
        anchor,
        PetMotion.ZERO,
        blueFish = visual,
    )

    private fun reactionVisual(reaction: EdgeReaction): BlueFishVisual = when (reaction.stage) {
        BlueFishStage.SURPRISED -> BlueFishMotion.surprised(state.clock - reaction.startedAt)
        else -> BlueFishMotion.shy(state.clock, state.clock - reaction.startedAt)
    }

    private fun dragPosition(x: Float, y: Float, active: Pointer) = PetPoint(
        x - active.grabOffset.x,
        (y - active.grabOffset.y).coerceIn(minOf(BlueFishCharacter.HEIGHT_DP * .65f, heightDp), heightDp + 10f),
    )

    private fun safeX(x: Float): Float {
        val left = minOf(BlueFishCharacter.HEIGHT_DP * .5f + 8f, widthDp * .5f)
        return x.coerceIn(left, maxOf(left, widthDp - BlueFishCharacter.HEIGHT_DP * .5f - 8f))
    }

    private fun safeY(y: Float): Float {
        val top = minOf(BlueFishCharacter.HEIGHT_DP + 18f, heightDp * .5f)
        return y.coerceIn(top, maxOf(top, heightDp - 24f))
    }

    private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t
    private fun mix(a: PetPoint, b: PetPoint, t: Float) = PetPoint(mix(a.x, b.x, t), mix(a.y, b.y, t))

    companion object {
        const val FIRST_ACTION_MS = 4000L
        const val RECOVER_MS = 350L
        const val DOCK_MS = 850L
        const val DOCK_SWITCH_MS = 470L
        const val DOCK_ZONE_DP = 36f
        private const val HIDDEN_SOURCE_OFFSET = 680f
    }
}
