package io.legado.pet.api

/** Optional pet API 4 extension, queried on the UI thread after drawing. */
interface PetPlacementSession : PetSession {
    /**
     * True while the pet is parked inside the viewport. In-place animations count as parked.
     * Report false during dragging, settling into position, docking, or roaming.
     */
    val isParked: Boolean
}
