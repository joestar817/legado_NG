package io.legado.plugins.pets.layered.core

import io.legado.pet.api.PetActivities

/** Foreground clock for work and reactions, separate from the pet's idle schedule. */
class PetActivityAnimation(private val completionDurationMs: Long) {
    var positionHeld = false
    var workActivity: String? = null
    var elapsedMs = 0L
        private set
    private var completionStartedAt: Long? = null

    val completionElapsedMs get() = completionStartedAt?.let { elapsedMs - it }
    val isActive get() = positionHeld || workActivity != null || completionStartedAt != null

    fun takesOver(phase: PetPhase): Boolean = isActive &&
        (positionHeld || phase != PetPhase.DOCKING && phase != PetPhase.SETTLING)

    fun playReaction(reactionId: String) {
        if (reactionId == PetActivities.COMPLETED) completionStartedAt = elapsedMs
    }

    fun clearReaction() {
        completionStartedAt = null
    }

    fun advance(deltaMs: Long) {
        elapsedMs += deltaMs
        val completion = completionElapsedMs
        if (completion != null && completion >= completionDurationMs) clearReaction()
    }
}
