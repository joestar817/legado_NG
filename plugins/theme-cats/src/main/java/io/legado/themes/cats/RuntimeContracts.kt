package io.legado.themes.cats

internal interface ListeningCartoonTextureHost {
    fun update(intensity: Int, animationAllowed: Boolean, timelineOriginNanos: Long? = null)
    fun release(onReleased: (() -> Unit)? = null)
}
