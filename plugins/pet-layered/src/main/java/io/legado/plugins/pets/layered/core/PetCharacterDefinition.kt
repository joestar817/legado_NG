package io.legado.plugins.pets.layered.core

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

enum class PetPose { FULL, PEEK }

enum class PetStableMode { FREE, DOCKED }

enum class PetPhase { IDLE, ACTION, PRESSED, DRAGGING, SETTLING, DOCKING }

enum class PetActionId { FULL_CALL, WAVE, CURIOUS, YAWN, PEEK_LOOK }

data class PetPoint(val x: Float, val y: Float)

/** Position state owned by this layered animation library. */
data class PetPlacement(val pose: PetPose, val xFraction: Float, val yFraction: Float)

data class PetRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
}

data class PetMotion(
    val head: Float = 0f,
    val headLift: Float = 0f,
    val body: Float = 0f,
    val leftArm: Float = 0f,
    val rightArm: Float = 0f,
    val tail: Float = 0f,
    val breath: Float = 0f,
    val blink: Float = 0f,
    val shout: Float = 0f,
    val mouth: Float = 0f,
    val reveal: Float = 0f,
    val rightElbow: Float = 0f,
) {
    fun scaled(amount: Float): PetMotion = blend(ZERO, this, amount)

    companion object {
        val ZERO = PetMotion()

        fun blend(from: PetMotion, to: PetMotion, amount: Float): PetMotion {
            val t = amount.coerceIn(0f, 1f)
            fun mix(a: Float, b: Float) = a + (b - a) * t
            return PetMotion(
                head = mix(from.head, to.head),
                headLift = mix(from.headLift, to.headLift),
                body = mix(from.body, to.body),
                leftArm = mix(from.leftArm, to.leftArm),
                rightArm = mix(from.rightArm, to.rightArm),
                tail = mix(from.tail, to.tail),
                breath = mix(from.breath, to.breath),
                blink = mix(from.blink, to.blink),
                shout = mix(from.shout, to.shout),
                mouth = mix(from.mouth, to.mouth),
                reveal = mix(from.reveal, to.reveal),
                rightElbow = mix(from.rightElbow, to.rightElbow),
            )
        }
    }
}

data class PetFrame(
    val pose: PetPose,
    val anchor: PetPoint,
    val motion: PetMotion,
    val visual: Any? = null,
) {
    val anchorX get() = anchor.x
    val anchorY get() = anchor.y
}

data class PetLayerDefinition(
    val id: String,
    val file: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val z: Int,
    val pivotX: Float = width / 2f,
    val pivotY: Float = height / 2f,
    val parentId: String? = null,
)

/** Immutable artwork geometry; asset decoding and Android density belong to the view. */
class PetCharacterDefinition(
    val id: String,
    val assetDirectory: String,
    val scale: Float,
    fullLayers: List<PetLayerDefinition>,
    peekLayers: List<PetLayerDefinition>,
    val peekRestCutX: Float = 225f,
    val peekMoreCutX: Float = 315f,
    additionalAssets: List<String> = emptyList(),
    val bitmapSampleSize: Int = 1,
) {
    private val full = fullLayers.sortedBy { it.z }
    private val peek = peekLayers.sortedBy { it.z }
    private val fullById = full.associateBy { it.id }
    private val peekById = peek.associateBy { it.id }
    private val fullAnchor = anchor(fullById.getValue("head"))
    private val peekAnchor = anchor(peekById.getValue("head"))
    val assetFiles: List<String> = (full.map { it.file } + peek.map { it.file } + additionalAssets).distinct()

    /** Conservative distance beyond the right edge that hides either rotating pose. */
    val hiddenReach: Float = PetPose.entries.maxOf { pose ->
        val anchor = headAnchor(pose)
        layers(pose).filter { it.parentId == null }.maxOf { layer ->
            val radius = listOf(
                hypot(layer.pivotX, layer.pivotY),
                hypot(layer.width - layer.pivotX, layer.pivotY),
                hypot(layer.pivotX, layer.height - layer.pivotY),
                hypot(layer.width - layer.pivotX, layer.height - layer.pivotY),
            ).max()
            anchor.x - (layer.x + layer.pivotX) + radius
        }
    } * scale + 3f

    init {
        require(scale.isFinite() && scale > 0f)
        require(bitmapSampleSize > 0)
    }

    fun layers(pose: PetPose): List<PetLayerDefinition> = if (pose == PetPose.FULL) full else peek

    fun headAnchor(pose: PetPose): PetPoint = if (pose == PetPose.FULL) fullAnchor else peekAnchor

    fun dockAnchorX(widthDp: Float): Float = widthDp + (peekAnchor.x - peekRestCutX) * scale

    /** Layer-local coordinates to viewport dp: [a, b, c, d, e, f]. */
    fun layerMatrix(frame: PetFrame, layer: PetLayerDefinition): FloatArray {
        val owner = layer.parentId?.let {
            (if (frame.pose == PetPose.FULL) fullById else peekById).getValue(it)
        } ?: layer
        val motion = frame.motion
        val degrees = when (owner.id) {
            "head" -> motion.head
            "left-arm" -> motion.leftArm
            "right-arm" -> motion.rightArm
            "tail" -> motion.tail
            "body" -> motion.body
            else -> 0f
        }
        val radians = degrees * Math.PI / 180.0
        val c = cos(radians).toFloat()
        val s = sin(radians).toFloat()
        val px = owner.x + owner.pivotX
        val py = owner.y + owner.pivotY
        val dy = if (owner.id.endsWith("-foot")) 0f else {
            motion.breath + if (owner.id == "head") motion.headLift else 0f
        }
        val tx = px - c * px + s * py
        val ty = py + dy - s * px - c * py
        val sourceAnchor = headAnchor(frame.pose)
        return floatArrayOf(
            scale * c, scale * s, -scale * s, scale * c,
            frame.anchor.x - sourceAnchor.x * scale + scale * (c * layer.x - s * layer.y + tx),
            frame.anchor.y - sourceAnchor.y * scale + scale * (s * layer.x + c * layer.y + ty),
        )
    }

    fun layerOpacity(motion: PetMotion, layer: PetLayerDefinition, pose: PetPose): Float = when (layer.id) {
        "eyes-closed" -> motion.blink
        "mouth-shout" -> motion.mouth
        "mouth-neutral" -> if (pose == PetPose.PEEK) maxOf(1f - motion.reveal, motion.blink) else 0f
        else -> 1f
    }.coerceIn(0f, 1f)

    private fun anchor(layer: PetLayerDefinition) = PetPoint(layer.x + layer.pivotX, layer.y + layer.pivotY)
}

