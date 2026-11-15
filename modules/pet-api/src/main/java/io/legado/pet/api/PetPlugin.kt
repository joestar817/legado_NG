package io.legado.pet.api

import android.graphics.Canvas
import android.graphics.RectF
import java.io.InputStream

/** ABI version 2. Entry classes are constructed via their public no-argument constructor. */
interface PetPlugin {
    val id: String

    /** Called on a worker thread. The factory must not retain an Activity or start UI work. */
    fun createSession(assets: PetAssets): PetSession
}

fun interface PetAssets {
    /** Relative to the package's assets directory. The plugin owns the returned stream. */
    fun open(path: String): InputStream
}

/**
 * Calls after construction are serialized on the UI thread. All coordinates are viewport dp.
 * A successfully created session is closed even if its view disappears before first display.
 */
interface PetSession {
    fun resize(widthDp: Float, heightDp: Float)
    fun setVisible(visible: Boolean)
    fun draw(canvas: Canvas)
    val hitBounds: RectF
    fun hitTest(x: Float, y: Float): Boolean
    fun press(x: Float, y: Float, eventTimeMs: Long)
    fun move(x: Float, y: Float, eventTimeMs: Long, dragging: Boolean)
    /** Return true to activate the host's chat entry after this release. */
    fun release(x: Float, y: Float): Boolean
    fun cancel()
    fun advance(deltaMs: Long)
    /** Milliseconds until the next animation tick, or null to sleep until the next input. */
    fun nextFrameDelayMs(): Long?
    /** Return a plugin-versioned blob of at most 4096 bytes, or null. */
    fun saveState(): ByteArray?
    /** Called on a fresh session. Ignore unknown or obsolete plugin state. */
    fun restoreState(state: ByteArray)
    fun close()
}
