package io.legado.app.ui.main.chatentry.core

/** The app host owns gestures, visibility and navigation; each character owns its animation. */
interface PetController {
    val definition: PetCharacterDefinition
    val widthDp: Float
    val heightDp: Float
    val isPressed: Boolean
    val isDragging: Boolean

    fun resize(widthDp: Float, heightDp: Float)
    fun frame(): PetFrame
    fun press(x: Float, y: Float, eventTimeMs: Long = 0L): Boolean
    fun move(x: Float, y: Float, eventTimeMs: Long, dragThresholdExceeded: Boolean)
    fun release(x: Float, y: Float): Boolean
    fun cancel()
    fun advance(deltaMs: Long)
    fun nextFrameDelayMs(): Long?
    fun placement(): PetPlacement
    fun restorePlacement(placement: PetPlacement)
}
