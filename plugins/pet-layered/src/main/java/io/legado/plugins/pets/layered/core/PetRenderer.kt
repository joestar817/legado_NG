package io.legado.plugins.pets.layered.core

import android.graphics.Canvas
import android.graphics.RectF

/** Canvas coordinates and touch points are viewport dp; transparent pixels pass through. */
interface PetRenderer {
    val hitBounds: RectF
    fun draw(canvas: Canvas, frame: PetFrame, viewportWidth: Float, viewportHeight: Float)
    fun hitTest(point: PetPoint): Boolean
}
