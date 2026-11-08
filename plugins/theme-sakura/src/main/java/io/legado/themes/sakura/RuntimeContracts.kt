package io.legado.themes.sakura

internal interface ListeningCartoonTextureHost {
    fun update(intensity: Int, animationAllowed: Boolean, timelineOriginNanos: Long? = null)
    fun release(onReleased: (() -> Unit)? = null)
}
internal object SakuraMotionAssets {
    const val ROOT = "listening_motion/cartoon/sakura"
    const val BACKGROUND = "background.webp"
    const val WATER_NORMAL = "water_normal.png"
    const val WATER_PHASE = "water_phase.png"
    fun path(fileName: String) = "$ROOT/$fileName"
}
