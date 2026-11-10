package io.legado.plugins.pets.layered.core

import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * Blue-fish interaction state. The host owns alpha hit testing, the drag
 * threshold, lifecycle suspension and click navigation, just as for other pets.
 */
class BlueFishPetEngine : PetController {
    override val definition = BlueFishCharacter.definition
    override var widthDp = 0f
        private set
    override var heightDp = 0f
        private set

    private enum class Action { FREE_SEQUENCE, RELIEF, SCOOTER }

    private data class State(
        val clock: Long = 0L,
        val mode: PetStableMode = PetStableMode.DOCKED,
        val phase: PetPhase = PetPhase.IDLE,
        val phaseStartedAt: Long = 0L,
        val position: PetPoint = PetPoint(0f, 0f),
        val nextActionAt: Long = FIRST_ACTION_MS,
        val nextDockActionAt: Long = FIRST_DOCK_ACTION_MS,
        val edgeStartedAt: Long = 0L,
        val randomSeed: Int = 8675309,
        val action: Action? = null,
        val scooterPlayed: Boolean = false,
        val struggleStartedAt: Long = 0L,
        val transitionFrom: PetFrame? = null,
        val transitionTarget: PetPoint? = null,
    )

    private data class Pointer(
        val before: State,
        val frozen: PetFrame,
        val grabOffset: PetPoint,
        val dragging: Boolean = false,
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
            return if (!it.dragging) it.frozen else makeFrame(state.position, suspendedVisual())
        }
        val from = state.transitionFrom
        val elapsed = state.clock - state.phaseStartedAt
        if (from != null && state.phase == PetPhase.DOCKING) {
            val hidden = PetPoint(widthDp + HIDDEN_SOURCE_OFFSET * definition.scale, safeY(from.anchor.y))
            val y = mix(from.anchor.y, hidden.y, BlueFishMotion.ease(elapsed / DOCK_MS.toFloat()))
            return if (elapsed < DOCK_SWITCH_MS) {
                val x = mix(from.anchor.x, hidden.x, BlueFishMotion.ease(elapsed / DOCK_SWITCH_MS.toFloat()))
                from.copy(anchor = PetPoint(x, y), visual = from.blueFish?.copy(bubbleFollowsAnchor = true))
            } else {
                val x = mix(hidden.x, definition.dockAnchorX(widthDp), BlueFishMotion.ease((elapsed - DOCK_SWITCH_MS) / (DOCK_MS - DOCK_SWITCH_MS).toFloat()))
                makeFrame(PetPoint(x, y), BlueFishVisual(BlueFishStage.PEEK))
            }
        }
        if (from != null && state.phase == PetPhase.SETTLING) {
            val progress = (elapsed / RECOVER_MS.toFloat()).coerceIn(0f, 1f)
            val target = state.transitionTarget!!
            val anchor = mix(from.anchor, target, progress * progress)
            return makeFrame(anchor, suspendedVisual().copy(
                stage = BlueFishStage.LANDING,
                actionElapsedMs = elapsed,
                landingProgress = progress,
                landingOffsetY = (target.y - anchor.y) / definition.scale,
                tailAngle = reliefTail(),
            ))
        }
        if (state.action == Action.RELIEF) {
            return makeFrame(state.position, BlueFishVisual(
                BlueFishStage.RELIEF, actionElapsedMs = elapsed, tailAngle = reliefTail(),
            ))
        }
        if (state.action == Action.SCOOTER) {
            return BlueFishScooterMotion.sample(
                elapsed, widthDp, state.position.y, minOf(state.position.y, heightDp - SCOOTER_BOTTOM_MARGIN_DP),
            )
        }
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
        // A cruise frame moves across the screen; its saved dock remains the
        // placement anchor and is also the correct anchor after a resize.
        val current = if (state.action == Action.SCOOTER) state.position else frame().anchor
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
        if (state.action == Action.SCOOTER) {
            state = scheduleDockWait(state.copy(
                phase = PetPhase.IDLE, action = null, phaseStartedAt = state.clock,
                edgeStartedAt = state.clock,
                position = PetPoint(definition.dockAnchorX(widthDp), safeY(current.y / oldHeight * heightDp)),
            ), state.clock)
            return
        }
        val docking = state.phase == PetPhase.DOCKING
        val settling = state.phase == PetPhase.SETTLING
        val docked = state.mode == PetStableMode.DOCKED && !docking
        fun reposition(point: PetPoint) = PetPoint(safeX(point.x / oldWidth * widthDp), safeY(point.y / oldHeight * heightDp))
        val landingTarget = if (settling) state.transitionTarget?.let(::reposition) else null
        val landingFrom = if (settling) state.transitionFrom?.let {
            val anchor = reposition(it.anchor)
            // Viewport scaling must not magnify the fixed 23dp landing drop.
            it.copy(anchor = if (landingTarget == null) anchor else anchor.copy(
                y = anchor.y.coerceIn(landingTarget.y - DROP_DISTANCE_DP, landingTarget.y),
            ))
        } else null
        state = state.copy(
            mode = if (docked) PetStableMode.DOCKED else PetStableMode.FREE,
            phase = if (docking) PetPhase.IDLE else state.phase,
            position = PetPoint(
                if (docked) definition.dockAnchorX(widthDp) else safeX(current.x / oldWidth * widthDp),
                safeY(current.y / oldHeight * heightDp),
            ),
            nextActionAt = if (docking) state.clock + FIRST_ACTION_MS else state.nextActionAt,
            transitionFrom = landingFrom,
            transitionTarget = landingTarget,
        )
    }

    override fun press(x: Float, y: Float, eventTimeMs: Long): Boolean {
        if (pointer != null || widthDp <= 0f || heightDp <= 0f || !x.isFinite() || !y.isFinite()) return false
        val frozen = frame()
        pointer = Pointer(state, frozen, PetPoint(x - frozen.anchor.x, y - frozen.anchor.y))
        return true
    }

    override fun move(x: Float, y: Float, eventTimeMs: Long, dragThresholdExceeded: Boolean) {
        val active = pointer ?: return
        if (!x.isFinite() || !y.isFinite()) return
        val dragging = active.dragging || dragThresholdExceeded
        val beginsDrag = !active.dragging && dragging
        // Keep the horizontal grab point when the pose changes, but move the
        // feet below the hand so every actual drag becomes a head-held pose.
        val nextPointer = active.copy(
            dragging = dragging,
            grabOffset = if (beginsDrag) active.grabOffset.copy(y = -SUSPENDED_PIN_TO_FEET_DP) else active.grabOffset,
        )
        pointer = nextPointer
        if (!dragging) return
        state = state.copy(
            mode = PetStableMode.FREE,
            phase = PetPhase.DRAGGING,
            action = null,
            position = dragPosition(x, y, nextPointer),
            struggleStartedAt = if (beginsDrag) state.clock else state.struggleStartedAt,
            transitionFrom = null,
            transitionTarget = null,
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
            action = null,
            phaseStartedAt = state.clock,
            transitionFrom = from,
            transitionTarget = if (docking) PetPoint(definition.dockAnchorX(widthDp), safeY(position.y))
                else PetPoint(safeX(position.x), safeY(position.y + DROP_DISTANCE_DP)),
        )
        return false
    }

    override fun cancel() {
        val active = pointer ?: return
        state = active.before
        pointer = null
    }

    /** A press freezes the scene; once dragging, the new struggle clock keeps running. */
    override fun advance(deltaMs: Long) {
        require(deltaMs >= 0L)
        if (widthDp <= 0f || heightDp <= 0f || deltaMs == 0L) return
        pointer?.let {
            if (it.dragging) state = state.copy(clock = state.clock + deltaMs)
            return
        }
        state = state.copy(clock = state.clock + deltaMs)
        // Anchor transitions to deadlines so irregular foreground frames do not
        // skip a bite, extend a phase, or change the next random idle deadline.
        while (true) {
            val elapsed = state.clock - state.phaseStartedAt
            when {
                state.phase == PetPhase.DOCKING && elapsed >= DOCK_MS -> {
                    val end = state.phaseStartedAt + DOCK_MS
                    state = scheduleDockWait(state.copy(
                        mode = PetStableMode.DOCKED, phase = PetPhase.IDLE, action = null, phaseStartedAt = end,
                        edgeStartedAt = end, position = state.transitionTarget!!, transitionFrom = null, transitionTarget = null,
                    ), end)
                }
                state.phase == PetPhase.SETTLING && elapsed >= RECOVER_MS -> {
                    val end = state.phaseStartedAt + RECOVER_MS
                    state = state.copy(
                        mode = PetStableMode.FREE, phase = PetPhase.ACTION, action = Action.RELIEF, phaseStartedAt = end,
                        position = state.transitionTarget!!, transitionFrom = null, transitionTarget = null,
                    )
                }
                state.action == Action.RELIEF && elapsed >= RELIEF_MS -> {
                    val end = state.phaseStartedAt + RELIEF_MS
                    state = state.copy(phase = PetPhase.IDLE, action = null, phaseStartedAt = end, nextActionAt = end + FIRST_ACTION_MS)
                }
                state.action == Action.SCOOTER && elapsed >= BlueFishScooterMotion.DURATION_MS -> {
                    val end = state.phaseStartedAt + BlueFishScooterMotion.DURATION_MS
                    state = scheduleDockWait(state.copy(
                        phase = PetPhase.IDLE, action = null, phaseStartedAt = end, edgeStartedAt = end,
                    ), end)
                }
                state.action == Action.FREE_SEQUENCE && elapsed >= BlueFishMotion.SEQUENCE_MS -> {
                    val end = state.phaseStartedAt + BlueFishMotion.SEQUENCE_MS
                    val seed = nextSeed(state.randomSeed)
                    state = state.copy(phase = PetPhase.IDLE, action = null, phaseStartedAt = end, randomSeed = seed,
                        nextActionAt = end + 12000L + randomDelay(seed, 6000L))
                }
                state.mode == PetStableMode.FREE && state.phase == PetPhase.IDLE && state.clock >= state.nextActionAt -> {
                    state = state.copy(phase = PetPhase.ACTION, action = Action.FREE_SEQUENCE, phaseStartedAt = state.nextActionAt)
                }
                state.mode == PetStableMode.DOCKED && state.phase == PetPhase.IDLE && state.clock >= state.nextDockActionAt -> {
                    state = state.copy(phase = PetPhase.ACTION, action = Action.SCOOTER,
                        scooterPlayed = true, phaseStartedAt = state.nextDockActionAt)
                }
                else -> return
            }
        }
    }

    override fun nextFrameDelayMs(): Long? {
        if (widthDp <= 0f || heightDp <= 0f) return null
        val active = pointer
        if (active != null && !active.dragging) return null
        return if (active?.dragging == true || state.phase == PetPhase.SETTLING || state.phase == PetPhase.DOCKING ||
            state.action == Action.RELIEF || state.action == Action.SCOOTER) 16L else 33L
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
        visual = visual,
    )

    private fun suspendedVisual() = BlueFishVisual(
        BlueFishStage.SUSPENDED, struggleElapsedMs = state.clock - state.struggleStartedAt,
    )

    private fun reliefTail() = sin(state.clock / 1000.0 * 2.4).toFloat() * .035f

    private fun scheduleDockWait(saved: State, fromMs: Long): State {
        if (!saved.scooterPlayed) return saved.copy(nextDockActionAt = fromMs + FIRST_DOCK_ACTION_MS)
        val seed = nextSeed(saved.randomSeed)
        var delay = DOCK_COOLDOWN_MS + randomDelay(seed, DOCK_COOLDOWN_SPREAD_MS)
        val peekPhase = delay % BlueFishMotion.EDGE_PERIOD_MS
        // Start with the head at its resting cut, rather than snapping a peek
        // back into the edge when a randomly chosen deadline is reached.
        if (peekPhase in 4701L..9799L) delay -= peekPhase - 4700L
        return saved.copy(randomSeed = seed, nextDockActionAt = fromMs + delay)
    }

    private fun nextSeed(seed: Int) = seed * 1664525 + 1013904223
    private fun randomDelay(seed: Int, spreadMs: Long) = ((seed.toLong() and 0xffffffffL) / 4294967296.0 * spreadMs).roundToLong()

    private fun dragPosition(x: Float, y: Float, active: Pointer) = PetPoint(
        x - active.grabOffset.x,
        safeY(y - active.grabOffset.y),
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
        const val RECOVER_MS = 380L
        const val RELIEF_MS = 2100L
        // Initial integration cadence: one early demonstration, then a rare
        // foreground-only dock action. These are not persisted preferences.
        const val FIRST_DOCK_ACTION_MS = 15000L
        const val DOCK_COOLDOWN_MS = 90000L
        const val DOCK_COOLDOWN_SPREAD_MS = 60000L
        const val DOCK_MS = 850L
        const val DOCK_SWITCH_MS = 470L
        const val DOCK_ZONE_DP = 36f
        const val SUSPENDED_PIN_TO_FEET_DP = (718f - 75f) * BlueFishCharacter.HEIGHT_DP / 700f
        private const val DROP_DISTANCE_DP = 23f
        private const val SCOOTER_BOTTOM_MARGIN_DP = 36f
        private const val HIDDEN_SOURCE_OFFSET = 680f
    }
}
