package io.legado.app.ui.main.chatentry.core

import kotlin.math.exp
import kotlin.math.roundToLong

internal data class PetActionRun(
    val id: PetActionId,
    val startedAt: Long,
    val from: PetMotion,
)

internal data class PetPendingReaction(val id: PetActionId, val dueAt: Long)

internal data class PetDockRun(val from: PetPoint, val to: PetPoint, val motion: PetMotion)

internal data class PetSavedState(
    val sceneMs: Long = 0L,
    val mode: PetStableMode = PetStableMode.DOCKED,
    val phase: PetPhase = PetPhase.IDLE,
    val phaseStartMs: Long = 0L,
    val position: PetPoint = PetPoint(0f, 0f),
    val nextIdleAt: Long = 1600L,
    val idleCount: Int = 0,
    val idleSeed: Int = 817,
    val lastIdleAction: PetActionId? = null,
    val action: PetActionRun? = null,
    val pendingReaction: PetPendingReaction? = null,
    val dragHead: Float = 0f,
    val headTarget: Float = 0f,
    val dragFrom: PetMotion = PetMotion.ZERO,
    val settleFrom: PetMotion = PetMotion.ZERO,
    val dock: PetDockRun? = null,
)

internal data class PetPointerState(
    val before: PetSavedState,
    val frozenFrame: PetFrame,
    val grabOffset: PetPoint,
    val point: PetPoint,
    val eventTimeMs: Long,
    val dragging: Boolean = false,
    val velocity: PetPoint = PetPoint(0f, 0f),
)

/** An in-memory interaction snapshot, with no View, Context, bitmap, or callback. */
class PetEngineSnapshot internal constructor(
    internal val state: PetSavedState,
    internal val pointer: PetPointerState?,
    internal val widthDp: Float,
    internal val heightDp: Float,
    internal val pendingPlacement: PetPlacement?,
    internal val quietIdle: Boolean,
) {
    override fun equals(other: Any?): Boolean = other is PetEngineSnapshot &&
        state == other.state && pointer == other.pointer && widthDp == other.widthDp &&
        heightDp == other.heightDp && pendingPlacement == other.pendingPlacement && quietIdle == other.quietIdle

    override fun hashCode(): Int {
        var result = state.hashCode()
        result = 31 * result + (pointer?.hashCode() ?: 0)
        result = 31 * result + widthDp.hashCode()
        result = 31 * result + heightDp.hashCode()
        result = 31 * result + (pendingPlacement?.hashCode() ?: 0)
        return 31 * result + quietIdle.hashCode()
    }
}

/**
 * Accepted v3.5 interaction state machine. Coordinates are viewport dp and the
 * artwork scale never changes. The host owns hit testing, the 6dp threshold,
 * lifecycle suspension, navigation, and supplying elapsed foreground time.
 */
class ChatPetEngine(override val definition: PetCharacterDefinition = GuguGagaCharacter.definition) : PetController {
    private var state = PetSavedState()
    private var pointer: PetPointerState? = null
    private var pendingPlacement: PetPlacement? = null

    override var widthDp = 0f
        private set
    override var heightDp = 0f
        private set
    var quietIdle = true

    override val isPressed get() = pointer != null
    override val isDragging get() = pointer?.dragging == true
    val stableMode get() = state.mode
    val phase get() = state.phase
    val sceneMs get() = state.sceneMs
    val currentAction get() = state.action?.id
    val hiddenAnchorX get() = widthDp + definition.hiddenReach

    override fun frame(): PetFrame {
        val activePointer = pointer
        if (state.phase == PetPhase.PRESSED && activePointer != null) return activePointer.frozenFrame
        if (state.phase == PetPhase.DOCKING) return dockingFrame()
        val pose = if (state.mode == PetStableMode.FREE) PetPose.FULL else PetPose.PEEK
        val elapsed = state.sceneMs - state.phaseStartMs
        val motion = when (state.phase) {
            PetPhase.ACTION -> actionMotion()
            PetPhase.DRAGGING -> dragMotion()
            PetPhase.SETTLING -> state.settleFrom.scaled(1f - smooth(elapsed, SETTLE_MS))
            else -> PetMotion(blink = PetActionSampler.blink((elapsed % 9000L) / 1000.0, 3.2).toFloat())
        }
        return poseFrame(pose, state.position, motion)
    }

    override fun resize(widthDp: Float, heightDp: Float) {
        require(widthDp.isFinite() && heightDp.isFinite() && widthDp >= 0f && heightDp >= 0f)
        cancel()
        if (widthDp <= 0f || heightDp <= 0f) return
        val oldWidth = this.widthDp
        val oldHeight = this.heightDp
        this.widthDp = widthDp
        this.heightDp = heightDp
        if (oldWidth <= 0f || oldHeight <= 0f) {
            val placement = pendingPlacement
            pendingPlacement = null
            if (placement == null) resetToEdge() else applyPlacement(placement)
            return
        }
        if (oldWidth == widthDp && oldHeight == heightDp) return
        fun reposition(p: PetPoint) = PetPoint(p.x / oldWidth * widthDp, safeY(p.y / oldHeight * heightDp))
        val position = reposition(state.position)
        val dock = state.dock?.let {
            PetDockRun(
                from = reposition(it.from),
                to = PetPoint(definition.dockAnchorX(widthDp), safeY(it.to.y / oldHeight * heightDp)),
                motion = it.motion,
            )
        }
        state = state.copy(
            position = when {
                state.phase == PetPhase.DOCKING -> position
                state.mode == PetStableMode.DOCKED -> PetPoint(definition.dockAnchorX(widthDp), position.y)
                else -> PetPoint(safeX(position.x), position.y)
            },
            dock = dock,
        )
    }

    override fun press(x: Float, y: Float, eventTimeMs: Long): Boolean {
        if (pointer != null || widthDp <= 0f || heightDp <= 0f || !x.isFinite() || !y.isFinite()) return false
        val frozen = frame()
        pointer = PetPointerState(
            before = state,
            frozenFrame = frozen,
            grabOffset = PetPoint(x - frozen.anchor.x, y - frozen.anchor.y),
            point = PetPoint(x, y),
            eventTimeMs = eventTimeMs,
        )
        state = state.copy(phase = PetPhase.PRESSED)
        return true
    }

    override fun move(x: Float, y: Float, eventTimeMs: Long, dragThresholdExceeded: Boolean) {
        val previous = pointer ?: return
        if (!x.isFinite() || !y.isFinite()) return
        val dt = if (previous.eventTimeMs == 0L) 16L else (eventTimeMs - previous.eventTimeMs).coerceAtLeast(8L)
        val dx = x - previous.point.x
        val dy = y - previous.point.y
        val velocity = if (dx * dx + dy * dy > .000001f) {
            PetPoint(dx / dt * 1000f, dy / dt * 1000f)
        } else previous.velocity
        val dragging = previous.dragging || dragThresholdExceeded
        if (!previous.dragging && dragging) {
            state = state.copy(
                mode = PetStableMode.FREE,
                phase = PetPhase.DRAGGING,
                phaseStartMs = state.sceneMs,
                position = previous.frozenFrame.anchor,
                idleCount = 0,
                dragFrom = previous.frozenFrame.motion,
                dragHead = previous.frozenFrame.motion.head,
                action = null,
                pendingReaction = null,
                dock = null,
            )
        }
        pointer = previous.copy(point = PetPoint(x, y), eventTimeMs = eventTimeMs, dragging = dragging, velocity = velocity)
        if (dragging) {
            state = state.copy(
                position = PetPoint(x - previous.grabOffset.x, y - previous.grabOffset.y),
                headTarget = (-previous.grabOffset.x * .08f + velocity.x * .0015f).coerceIn(-6f, 6f),
            )
        }
    }

    /** True means a click; state has already been restored before host navigation. */
    override fun release(x: Float, y: Float): Boolean {
        val active = pointer ?: return false
        if (!x.isFinite() || !y.isFinite() || x < 0f || y < 0f || y > heightDp) {
            cancel()
            return false
        }
        if (!active.dragging) {
            pointer = null
            state = active.before
            return true
        }
        // ACTION_UP may contain a final position not delivered by ACTION_MOVE.
        state = state.copy(position = PetPoint(x - active.grabOffset.x, y - active.grabOffset.y))
        val motion = dragMotion()
        pointer = null
        state = if (x >= widthDp - DOCK_WIDTH_DP) {
            state.copy(
                phase = PetPhase.DOCKING,
                phaseStartMs = state.sceneMs,
                settleFrom = motion,
                pendingReaction = null,
                dock = PetDockRun(state.position, PetPoint(definition.dockAnchorX(widthDp), state.position.y), motion),
            )
        } else {
            state.copy(
                mode = PetStableMode.FREE,
                phase = PetPhase.SETTLING,
                phaseStartMs = state.sceneMs,
                settleFrom = motion,
                dock = null,
                pendingReaction = PetPendingReaction(PetActionId.WAVE, state.sceneMs + SETTLE_MS + 220L),
            )
        }
        return false
    }

    override fun cancel() {
        val active = pointer ?: return
        pointer = null
        state = active.before
    }

    /** Call only while the entry is visible; a pressed-but-not-dragged pet freezes. */
    override fun advance(deltaMs: Long) {
        require(deltaMs >= 0L)
        if (widthDp <= 0f || heightDp <= 0f || state.phase == PetPhase.PRESSED || deltaMs == 0L) return
        state = state.copy(sceneMs = state.sceneMs + deltaMs)
        if (state.phase == PetPhase.DRAGGING) {
            state = state.copy(dragHead = state.headTarget + (state.dragHead - state.headTarget) * exp(-deltaMs / 90.0).toFloat())
            pointer = pointer?.let {
                val decay = exp(-deltaMs / 140.0).toFloat()
                it.copy(velocity = PetPoint(it.velocity.x * decay, it.velocity.y * decay))
            }
        }
        updatePhase()
    }

    fun resetToEdge() {
        pointer = null
        pendingPlacement = null
        state = PetSavedState(position = PetPoint(definition.dockAnchorX(widthDp), safeY(heightDp - 210f)))
    }

    fun snapshot(): PetEngineSnapshot = PetEngineSnapshot(state, pointer, widthDp, heightDp, pendingPlacement, quietIdle)

    fun restore(snapshot: PetEngineSnapshot) {
        state = snapshot.state
        pointer = snapshot.pointer
        widthDp = snapshot.widthDp
        heightDp = snapshot.heightDp
        pendingPlacement = snapshot.pendingPlacement
        quietIdle = snapshot.quietIdle
    }

    override fun placement(): PetPlacement {
        pendingPlacement?.let { return it }
        val placementState = pointer?.before ?: state
        val docked = placementState.mode == PetStableMode.DOCKED || placementState.phase == PetPhase.DOCKING
        val position = placementState.dock?.to ?: placementState.position
        return PetPlacement(
            pose = if (docked) PetPose.PEEK else PetPose.FULL,
            xFraction = if (docked || widthDp <= 0f) 1f else position.x / widthDp,
            yFraction = if (heightDp <= 0f) .69f else position.y / heightDp,
        )
    }

    override fun restorePlacement(placement: PetPlacement) {
        require(placement.xFraction.isFinite() && placement.yFraction.isFinite())
        cancel()
        if (widthDp <= 0f || heightDp <= 0f) pendingPlacement = placement else applyPlacement(placement)
    }

    /** Null suspends drawing. Idle wakes at the next blink/action rather than polling. */
    override fun nextFrameDelayMs(): Long? {
        if (widthDp <= 0f || heightDp <= 0f || state.phase == PetPhase.PRESSED) return null
        if (state.phase != PetPhase.IDLE) return 16L
        val elapsed = state.sceneMs - state.phaseStartMs
        val cycle = elapsed % 9000L
        val blinkDelay = when {
            cycle < 3130L -> 3130L - cycle
            cycle < 3270L -> 16L
            else -> 9000L - cycle + 3130L
        }
        val actionDelay = (state.pendingReaction?.dueAt ?: state.nextIdleAt) - state.sceneMs
        return minOf(blinkDelay, actionDelay).coerceAtLeast(1L)
    }

    private fun applyPlacement(placement: PetPlacement) {
        pendingPlacement = null
        pointer = null
        val docked = placement.pose == PetPose.PEEK
        state = PetSavedState(
            mode = if (docked) PetStableMode.DOCKED else PetStableMode.FREE,
            position = PetPoint(
                if (docked) definition.dockAnchorX(widthDp) else safeX(placement.xFraction * widthDp),
                safeY(placement.yFraction * heightDp),
            ),
            nextIdleAt = if (docked) 1600L else 4500L,
        )
    }

    private fun poseFrame(pose: PetPose, position: PetPoint, motion: PetMotion): PetFrame {
        val revealX = if (pose == PetPose.PEEK) {
            -(definition.peekMoreCutX - definition.peekRestCutX) * definition.scale * motion.reveal
        } else 0f
        return PetFrame(pose, PetPoint(position.x + revealX, position.y), motion)
    }

    private fun actionMotion(): PetMotion {
        val action = state.action ?: return PetMotion.ZERO
        val elapsed = state.sceneMs - action.startedAt
        val pose = if (state.mode == PetStableMode.FREE) PetPose.FULL else PetPose.PEEK
        val target = PetActionSampler.sample(action.id, elapsed, pose)
        // Legacy peek-look entered directly, with an already-neutral starting pose.
        return if (action.id == PetActionId.PEEK_LOOK) target else PetMotion.blend(action.from, target, smooth(elapsed, 160L))
    }

    private fun dragMotion(): PetMotion {
        val target = PetMotion(
            leftArm = 15f,
            rightArm = -15f,
            head = state.dragHead,
            tail = ((pointer?.velocity?.x ?: 0f) / 250f).coerceIn(-3f, 3f),
        )
        return PetMotion.blend(state.dragFrom, target, smooth(state.sceneMs - state.phaseStartMs, 150L))
    }

    private fun dockingFrame(): PetFrame {
        val dock = state.dock ?: return poseFrame(PetPose.PEEK, state.position, PetMotion.ZERO)
        val elapsed = state.sceneMs - state.phaseStartMs
        val hidden = PetPoint(hiddenAnchorX, dock.from.y)
        if (elapsed < DOCK_SWITCH_MS) {
            val position = mix(dock.from, hidden, smooth(elapsed - 120L, 320L))
            val reach = smooth(elapsed, 150L)
            val motion = dock.motion.scaled(1f - reach)
            return poseFrame(PetPose.FULL, position, motion.copy(
                leftArm = motion.leftArm + 12f * reach,
                rightArm = motion.rightArm - 72f * reach,
                head = motion.head - 3f * reach,
            ))
        }
        return poseFrame(PetPose.PEEK, mix(hidden, dock.to, smooth(elapsed - DOCK_SWITCH_MS, 350L)), PetMotion.ZERO)
    }

    private fun updatePhase() {
        // Foreground scheduling can advance directly to a deadline. Anchor every
        // transition to that deadline, so split and single advances agree.
        while (true) {
            val elapsed = state.sceneMs - state.phaseStartMs
            when {
                state.phase == PetPhase.SETTLING && elapsed >= SETTLE_MS -> {
                    val at = state.phaseStartMs + SETTLE_MS
                    state = state.copy(phase = PetPhase.IDLE, phaseStartMs = at, nextIdleAt = at + 5200L, settleFrom = PetMotion.ZERO)
                }
                state.phase == PetPhase.DOCKING && elapsed >= DOCK_MS -> {
                    val at = state.phaseStartMs + DOCK_MS
                    state = state.copy(mode = PetStableMode.DOCKED, phase = PetPhase.IDLE, phaseStartMs = at,
                        position = state.dock!!.to, dock = null, nextIdleAt = at + 1500L)
                }
                state.phase == PetPhase.IDLE && state.pendingReaction != null && state.sceneMs >= state.pendingReaction!!.dueAt -> {
                    val pending = state.pendingReaction!!
                    startAction(pending.id, pending.dueAt)
                }
                state.phase == PetPhase.IDLE && state.pendingReaction == null && state.sceneMs >= state.nextIdleAt -> startIdleAction()
                state.phase == PetPhase.ACTION && state.action != null -> {
                    val action = state.action!!
                    val end = action.startedAt + PetActionSampler.spec(action.id).durationMs
                    if (state.sceneMs < end) return
                    state = state.copy(phase = PetPhase.IDLE, phaseStartMs = end, action = null, idleCount = state.idleCount + 1)
                    val delay = idleDelay()
                    state = state.copy(nextIdleAt = end + delay)
                }
                else -> return
            }
        }
    }

    private fun startAction(id: PetActionId, at: Long) {
        val now = state.sceneMs
        state = state.copy(sceneMs = at)
        val from = frame().motion
        state = state.copy(sceneMs = now, phase = PetPhase.ACTION, phaseStartMs = at, action = PetActionRun(id, at, from),
            pendingReaction = null, lastIdleAction = id)
    }

    private fun startIdleAction() {
        val full = state.mode == PetStableMode.FREE
        val pool = (if (full) listOf(PetActionId.FULL_CALL, PetActionId.WAVE, PetActionId.CURIOUS, PetActionId.YAWN)
        else listOf(PetActionId.PEEK_LOOK, PetActionId.CURIOUS)).filter { it != state.lastIdleAction }
        val id = if (full && state.lastIdleAction != PetActionId.FULL_CALL) PetActionId.FULL_CALL else {
            pool[(nextIdleRandom() * pool.size).toInt().coerceAtMost(pool.lastIndex)]
        }
        startAction(id, state.nextIdleAt)
    }

    private fun idleDelay(): Long {
        val base = if (state.mode == PetStableMode.FREE) 7000L else 5200L
        val quiet = if (quietIdle) minOf(state.idleCount, 3) * 4500L else 0L
        return base + (nextIdleRandom() * 3000.0).roundToLong() + quiet
    }

    private fun nextIdleRandom(): Double {
        val seed = state.idleSeed * 1664525 + 1013904223
        state = state.copy(idleSeed = seed)
        return (seed.toLong() and 0xffffffffL) / 4294967296.0
    }

    private fun safeX(x: Float): Float {
        val anchor = definition.headAnchor(PetPose.FULL)
        val layers = definition.layers(PetPose.FULL).filter { it.parentId == null }
        val left = minOf((anchor.x - layers.minOf { it.x }) * definition.scale + 8f, widthDp / 2f)
        val right = maxOf(left, widthDp - (layers.maxOf { it.x + it.width } - anchor.x) * definition.scale - 8f)
        return x.coerceIn(left, right)
    }

    private fun safeY(y: Float): Float {
        if (heightDp <= 0f) return 0f
        val topExtent = PetPose.entries.maxOf { pose ->
            val anchor = definition.headAnchor(pose)
            (anchor.y - definition.layers(pose).minOf { it.y }) * definition.scale
        }
        val bottomExtent = PetPose.entries.maxOf { pose ->
            val anchor = definition.headAnchor(pose)
            (definition.layers(pose).maxOf { it.y + it.height } - anchor.y) * definition.scale
        }
        val top = minOf(topExtent + 8f, heightDp / 2f)
        val bottom = maxOf(top, heightDp - bottomExtent - 8f)
        return y.coerceIn(top, bottom)
    }

    private fun smooth(elapsed: Long, duration: Long) = PetActionSampler.smooth(elapsed.toDouble() / duration).toFloat()
    private fun mix(a: PetPoint, b: PetPoint, t: Float) = PetPoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

    companion object {
        const val SETTLE_MS = 350L
        const val DOCK_MS = 850L
        const val DOCK_SWITCH_MS = 470L
        const val DOCK_WIDTH_DP = 36f
    }
}
