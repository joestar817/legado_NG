package io.legado.app.ui.main.chatentry.core

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

enum class PetPose { FULL, PEEK }

enum class PetStableMode { FREE, DOCKED }

enum class PetPhase { IDLE, ACTION, PRESSED, DRAGGING, SETTLING, DOCKING }

enum class PetActionId { FULL_CALL, WAVE, CURIOUS, YAWN, PEEK_LOOK }

data class PetPoint(val x: Float, val y: Float)

/** Written explicitly by the host; this is not a Gson or reflection DTO. */
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
    val blueFish: BlueFishVisual? = null,
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

object GuguGagaCharacter {
    val definition = PetCharacterDefinition(
        id = "gugugaga",
        assetDirectory = "ai_pets/gugugaga",
        scale = 0.23f,
        fullLayers = listOf(
            PetLayerDefinition("tail", "front/tail.png", 334f, 510f, 104f, 61f, 5, 13f, 30f),
            PetLayerDefinition("left-foot", "front/left-foot.png", 197f, 552f, 52f, 42f, 10, 26f, 39f),
            PetLayerDefinition("right-foot", "front/right-foot.png", 263f, 552f, 52f, 42f, 10, 26f, 39f),
            PetLayerDefinition("left-arm", "front/left-arm.png", 103.6f, 378f, 108f, 161f, 20, 86.4f, 18f),
            PetLayerDefinition("right-arm", "front/right-arm.png", 300.4f, 378f, 108f, 157f, 20, 21.6f, 18f),
            PetLayerDefinition("torso", "front/torso.png", 144f, 355f, 224f, 215f, 30, 112f, 30f),
            PetLayerDefinition("head", "front/head.png", 106f, 110f, 300f, 270f, 40, 150f, 251f),
            PetLayerDefinition("eyes-closed", "front/eyes-closed.png", 106f, 110f, 300f, 270f, 41, parentId = "head"),
            PetLayerDefinition("mouth-shout", "front/mouth-shout.png", 106f, 110f, 300f, 270f, 42, parentId = "head"),
        ),
        peekLayers = listOf(
            PetLayerDefinition("body", "peek/body.png", 192f, 348f, 190f, 250f, 30, 94f, 31f),
            PetLayerDefinition("head", "peek/head.png", 94f, 110f, 324f, 289.4f, 40, 188f, 270f),
            PetLayerDefinition("eyes-closed", "peek/eyes-closed.png", 94f, 110f, 324f, 289.4f, 41, parentId = "head"),
            PetLayerDefinition("mouth-neutral", "peek/mouth-neutral.png", 94f, 110f, 324f, 289.4f, 42, parentId = "head"),
        ),
    )
}
