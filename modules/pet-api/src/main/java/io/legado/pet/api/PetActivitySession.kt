package io.legado.pet.api

/** Optional pet API 3 extension. All calls follow the UI-thread contract of [PetSession]. */
interface PetActivitySession : PetSession {
    /**
     * Keep the pet's position still while its menu is open. In-place animations may continue.
     * The host releases the hold before dispatching the first dragging event.
     */
    fun setPositionHeld(held: Boolean)

    /**
     * Null returns to idle. A running activity uses a stable identifier from [PetActivities].
     * Unknown work identifiers use the same animation as [PetActivities.WORK].
     */
    fun setWorkActivity(activityId: String?)

    /**
     * Play a supported reaction once, then resume the current work or idle animation.
     * Ignore unknown identifiers. The host sends this only while the pet is visible.
     */
    fun playReaction(reactionId: String)
}

object PetActivities {
    const val WORK = "work"
    const val BOOK_SOURCE_REPAIR = "book_source_repair"
    const val COMPLETED = "completed"
}
