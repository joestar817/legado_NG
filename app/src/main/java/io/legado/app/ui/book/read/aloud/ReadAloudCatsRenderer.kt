package io.legado.app.ui.book.read.aloud

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** Renders the fixed CATV0001 Curious Cats scene; this is not an MPKG runtime. */
internal class ReadAloudCatsRenderer(context: Context) {

    private val appContext = context.applicationContext
    private val decorationVertices: FloatBuffer = ByteBuffer.allocateDirect(
        MAX_DECORATION_VERTICES * DECORATION_VERTEX_FLOATS * Float.SIZE_BYTES
    ).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val fullScreenQuad: FloatBuffer = ByteBuffer.allocateDirect(8 * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
            position(0)
        }

    @Volatile
    private var renderState = CatsRenderState()

    private val resetFrameClock = AtomicBoolean(true)

    private var ready = false
    private var surfaceWidth = 1
    private var surfaceHeight = 1
    private var elapsedSeconds = 0.0
    private var lastFrameNanos = 0L
    private var uploadedTextureCount = 0
    private var layers = emptyList<GlLayer>()
    private var textures = IntArray(0)
    private var staticProgram: GlProgram? = null
    private var puppetProgram: GlProgram? = null
    private var lightProgram: GlProgram? = null
    private var decorationProgram: GlProgram? = null
    private var decorationBuffer = 0
    private var validateNextFrame = true

    fun update(intensity: Int, animationAllowed: Boolean) {
        val previous = renderState
        renderState = CatsRenderState(
            intensity = intensity.coerceIn(0, 100),
            animationAllowed = animationAllowed,
        )
        if (
            previous.animationAllowed != animationAllowed ||
            (previous.intensity == 0) != (renderState.intensity == 0)
        ) {
            resetFrameClock.set(true)
        }
    }

    fun shouldAnimate(): Boolean {
        val state = renderState
        return ready && state.animationAllowed && state.intensity > 0
    }

    fun onSurfaceCreated(): Int {
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        staticProgram = GlProgram(STATIC_VERTEX_SHADER, TEXTURE_FRAGMENT_SHADER)
        puppetProgram = GlProgram(PUPPET_VERTEX_SHADER, TEXTURE_FRAGMENT_SHADER)
        lightProgram = GlProgram(FULL_SCREEN_VERTEX_SHADER, LIGHT_FRAGMENT_SHADER)
        decorationProgram = GlProgram(DECORATION_VERTEX_SHADER, DECORATION_FRAGMENT_SHADER)
        val buffer = IntArray(1)
        GLES30.glGenBuffers(1, buffer, 0)
        decorationBuffer = buffer[0]
        checkGl("create Cats programs")
        val maximum = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, maximum, 0)
        return maximum[0]
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        surfaceWidth = width.coerceAtLeast(1)
        surfaceHeight = height.coerceAtLeast(1)
        GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
    }

    fun decodeScene(): CatsSceneData {
        val bytes = appContext.assets.open(CatsMotionAssets.path(CatsMotionAssets.SCENE)).use {
            it.readBytes()
        }
        require(bytes.size in 1..MAX_SCENE_BYTES) { "Invalid CATV0001 size ${bytes.size}" }
        val source = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = ByteArray(CATV_MAGIC.size)
        source.get(magic)
        require(magic.contentEquals(CATV_MAGIC)) { "Unsupported Cats scene protocol" }
        require(source.unsignedShort() == SCENE_WIDTH)
        require(source.unsignedShort() == SCENE_HEIGHT)
        require(source.unsignedShort() == FRAME_RATE)
        require(source.unsignedShort() == FRAME_COUNT)
        require(source.unsignedShort() == LAYER_COUNT)
        require(source.unsignedShort() == CatsMotionAssets.TEXTURES.size)

        val decodedLayers = ArrayList<CatsLayerData>(LAYER_COUNT)
        repeat(LAYER_COUNT) { layerIndex ->
            val kind = source.get().toInt() and 0xff
            val textureIndex = source.get().toInt() and 0xff
            val boneCount = source.get().toInt() and 0xff
            val reserved = source.get().toInt() and 0xff
            val originX = source.getFloat()
            val originY = source.getFloat()
            val scaleX = source.getFloat()
            val scaleY = source.getFloat()
            val vertexCount = source.int
            val indexCount = source.int
            val matrixFloatCount = source.int
            require(kind == EXPECTED_KINDS[layerIndex])
            require(textureIndex == layerIndex && reserved == 0)
            require(boneCount == EXPECTED_BONES[layerIndex])
            require(vertexCount == EXPECTED_VERTICES[layerIndex])
            require(indexCount == EXPECTED_INDICES[layerIndex])
            val expectedMatrixFloats = FRAME_COUNT * boneCount * MATRIX_FLOATS
            require(matrixFloatCount == expectedMatrixFloats)
            require(originX.isFinite() && originY.isFinite())
            require(scaleX.isFinite() && scaleY.isFinite())

            val vertexStride = if (kind == LAYER_PUPPET) PUPPET_VERTEX_BYTES else STATIC_VERTEX_BYTES
            val vertexBytes = vertexCount.checkedMultiply(vertexStride)
            val indexBytes = indexCount.checkedMultiply(Short.SIZE_BYTES)
            validateVertices(source, kind, vertexCount, boneCount)
            val vertexBuffer = source.copyDirect(vertexBytes)
            validateIndices(source, indexCount, vertexCount)
            val indexBuffer = source.copyDirect(indexBytes)
            val matrices = FloatArray(matrixFloatCount)
            repeat(matrixFloatCount) { matrices[it] = source.getFloat() }
            require(matrices.all { it.isFinite() })
            decodedLayers += CatsLayerData(
                kind = kind,
                textureIndex = textureIndex,
                boneCount = boneCount,
                originX = originX,
                originY = originY,
                scaleX = scaleX,
                scaleY = scaleY,
                vertexCount = vertexCount,
                indexCount = indexCount,
                vertices = vertexBuffer,
                indices = indexBuffer,
                matrices = matrices,
            )
        }
        require(!source.hasRemaining()) { "Trailing bytes in CATV0001" }
        return CatsSceneData(decodedLayers)
    }

    fun uploadScene(scene: CatsSceneData) {
        require(layers.isEmpty())
        val glLayers = ArrayList<GlLayer>(scene.layers.size)
        scene.layers.forEach { layer ->
            val buffers = IntArray(2)
            GLES30.glGenBuffers(2, buffers, 0)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffers[0])
            GLES30.glBufferData(
                GLES30.GL_ARRAY_BUFFER,
                layer.vertices.remaining(),
                layer.vertices,
                GLES30.GL_STATIC_DRAW,
            )
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, buffers[1])
            GLES30.glBufferData(
                GLES30.GL_ELEMENT_ARRAY_BUFFER,
                layer.indices.remaining(),
                layer.indices,
                GLES30.GL_STATIC_DRAW,
            )
            glLayers += GlLayer(layer, buffers[0], buffers[1])
        }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
        layers = glLayers
        textures = IntArray(CatsMotionAssets.TEXTURES.size)
        GLES30.glGenTextures(textures.size, textures, 0)
        checkGl("upload CATV0001 geometry")
    }

    fun selectTextureSampleSize(
        maximumTextureSize: Int,
        surfaceWidth: Int,
        surfaceHeight: Int,
        memoryClassMb: Int?,
    ): Int {
        val supportsHighResolution = maximumTextureSize >= HIGH_RES_LONG_EDGE
        val hasMemoryBudget = memoryClassMb != null && memoryClassMb >= HIGH_RES_MEMORY_CLASS_MB
        val surfaceNeedsHighResolution =
            surfaceWidth > SCENE_WIDTH || surfaceHeight > SCENE_HEIGHT
        return if (
            supportsHighResolution && hasMemoryBudget && surfaceNeedsHighResolution
        ) {
            1
        } else {
            2
        }
    }

    fun decodeTexture(textureIndex: Int, sampleSize: Int): CatsTextureData {
        require(textureIndex in CatsMotionAssets.TEXTURES.indices)
        require(sampleSize == 1 || sampleSize == 2)
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inPremultiplied = true
            inScaled = false
            inSampleSize = sampleSize
        }
        val bitmap = appContext.assets.open(
            CatsMotionAssets.path(CatsMotionAssets.TEXTURES[textureIndex])
        ).use { input ->
            requireNotNull(BitmapFactory.decodeStream(input, null, options)) {
                "Unable to decode Cats texture $textureIndex"
            }
        }
        val expectedWidth = EXPECTED_TEXTURE_WIDTHS[textureIndex] / sampleSize
        val expectedHeight = EXPECTED_TEXTURE_HEIGHTS[textureIndex] / sampleSize
        if (bitmap.width != expectedWidth || bitmap.height != expectedHeight) {
            val actualSize = "${bitmap.width}x${bitmap.height}"
            bitmap.recycle()
            error(
                "Unexpected Cats texture $textureIndex size $actualSize, " +
                    "expected ${expectedWidth}x$expectedHeight"
            )
        }
        return CatsTextureData(textureIndex, bitmap)
    }

    fun uploadTexture(textureData: CatsTextureData) {
        require(textureData.textureIndex == uploadedTextureCount)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[textureData.textureIndex])
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D,
            GLES30.GL_TEXTURE_MIN_FILTER,
            GLES30.GL_LINEAR,
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D,
            GLES30.GL_TEXTURE_MAG_FILTER,
            GLES30.GL_LINEAR,
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D,
            GLES30.GL_TEXTURE_WRAP_S,
            GLES30.GL_CLAMP_TO_EDGE,
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D,
            GLES30.GL_TEXTURE_WRAP_T,
            GLES30.GL_CLAMP_TO_EDGE,
        )
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, textureData.bitmap, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        uploadedTextureCount++
        checkGl("upload Cats texture ${textureData.textureIndex}")
    }

    fun finishUpload() {
        require(layers.size == LAYER_COUNT)
        require(uploadedTextureCount == CatsMotionAssets.TEXTURES.size)
        ready = true
        resetFrameClock.set(true)
        validateNextFrame = true
    }

    fun onDrawFrame(frameTimeNanos: Long) {
        if (!ready) return
        val state = renderState
        if (resetFrameClock.getAndSet(false)) {
            lastFrameNanos = frameTimeNanos
        }
        val deltaSeconds = if (lastFrameNanos == 0L) {
            0.0
        } else {
            ((frameTimeNanos - lastFrameNanos).coerceAtLeast(0L) / 1_000_000_000.0)
                .coerceAtMost(0.1)
        }
        lastFrameNanos = frameTimeNanos
        if (state.animationAllowed && state.intensity > 0) {
            elapsedSeconds = (elapsedSeconds + deltaSeconds) % LOOP_SECONDS
        }
        val intensity = state.intensity / 100f
        val animationFrame = floor(elapsedSeconds * FRAME_RATE).toInt() % FRAME_COUNT

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        drawLayer(0, animationFrame, intensity)
        drawSoftLight(intensity)
        drawBirds(intensity)
        drawLayer(1, animationFrame, intensity)
        drawLeaves(intensity, front = false)
        for (layerIndex in 2 until LAYER_COUNT) {
            drawLayer(layerIndex, animationFrame, intensity)
        }
        drawLeaves(intensity, front = true)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        if (validateNextFrame) {
            checkGl("draw first Cats frame")
            validateNextFrame = false
        }
    }

    fun release() {
        ready = false
        layers.forEach { layer ->
            GLES30.glDeleteBuffers(2, intArrayOf(layer.vertexBuffer, layer.indexBuffer), 0)
        }
        layers = emptyList()
        if (decorationBuffer != 0) {
            GLES30.glDeleteBuffers(1, intArrayOf(decorationBuffer), 0)
            decorationBuffer = 0
        }
        if (textures.isNotEmpty()) GLES30.glDeleteTextures(textures.size, textures, 0)
        textures = IntArray(0)
        uploadedTextureCount = 0
        staticProgram?.release()
        puppetProgram?.release()
        lightProgram?.release()
        decorationProgram?.release()
        staticProgram = null
        puppetProgram = null
        lightProgram = null
        decorationProgram = null
    }

    private fun drawLayer(layerIndex: Int, animationFrame: Int, intensity: Float) {
        val layer = layers[layerIndex]
        val data = layer.data
        val program = if (data.kind == LAYER_PUPPET) puppetProgram else staticProgram
        requireNotNull(program).use()
        program.uniform2f("uOrigin", data.originX, data.originY)
        program.uniform2f("uScale", data.scaleX, data.scaleY)
        program.uniform2f("uCanvas", SCENE_WIDTH.toFloat(), SCENE_HEIGHT.toFloat())
        program.uniform2f("uSurface", surfaceWidth.toFloat(), surfaceHeight.toFloat())
        program.uniform1f("uTime", elapsedSeconds.toFloat())
        val sway = when (layerIndex) {
            1 -> 1.25f * intensity
            6 -> 0.7f * intensity
            else -> 0f
        }
        program.uniform1f("uSway", sway)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[data.textureIndex])
        program.uniform1i("uTexture", 0)
        if (data.kind == LAYER_PUPPET) {
            val offset = animationFrame * data.boneCount * MATRIX_FLOATS
            GLES30.glUniformMatrix4fv(
                program.uniform("uBones[0]"),
                data.boneCount,
                false,
                data.matrices,
                offset,
            )
        }

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, layer.vertexBuffer)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, layer.indexBuffer)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glEnableVertexAttribArray(1)
        val stride = if (data.kind == LAYER_PUPPET) PUPPET_VERTEX_BYTES else STATIC_VERTEX_BYTES
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, stride, 3 * Float.SIZE_BYTES)
        if (data.kind == LAYER_PUPPET) {
            GLES30.glEnableVertexAttribArray(2)
            GLES30.glEnableVertexAttribArray(3)
            GLES30.glVertexAttribIPointer(2, 4, GLES30.GL_UNSIGNED_BYTE, stride, 5 * Float.SIZE_BYTES)
            GLES30.glVertexAttribPointer(
                3,
                4,
                GLES30.GL_FLOAT,
                false,
                stride,
                5 * Float.SIZE_BYTES + 4,
            )
        }
        GLES30.glDrawElements(
            GLES30.GL_TRIANGLES,
            data.indexCount,
            GLES30.GL_UNSIGNED_SHORT,
            0,
        )
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        if (data.kind == LAYER_PUPPET) {
            GLES30.glDisableVertexAttribArray(2)
            GLES30.glDisableVertexAttribArray(3)
        }
    }

    private fun drawSoftLight(intensity: Float) {
        if (intensity <= 0f) return
        val program = requireNotNull(lightProgram)
        program.use()
        program.uniform1f("uTime", elapsedSeconds.toFloat())
        program.uniform1f("uIntensity", intensity)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        fullScreenQuad.position(0)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, fullScreenQuad)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glDisableVertexAttribArray(0)
    }

    private fun drawBirds(intensity: Float) {
        if (intensity <= 0f) return
        decorationVertices.clear()
        repeat(BIRD_COUNT) { index ->
            val phase = (elapsedSeconds / LOOP_SECONDS + index / BIRD_COUNT.toDouble()) % 1.0
            val x = -70f + phase.toFloat() * 940f
            val arc = sin(phase * PI).toFloat()
            val y = 1120f + arc * 95f + index * 24f
            val wing = sin(elapsedSeconds * 8.0 + index * 1.7).toFloat() * 4.5f
            val size = 8f + index * 1.7f
            putTriangle(x, y, x - size, y + wing, x - 1f, y - 2f, 0.09f, 0.16f, 0.08f, 0.42f)
            putTriangle(x, y, x + size, y - wing, x + 1f, y - 2f, 0.09f, 0.16f, 0.08f, 0.42f)
        }
        drawDecorations(decorationVertices.position(), intensity)
    }

    private fun drawLeaves(intensity: Float, front: Boolean) {
        if (intensity <= 0f) return
        decorationVertices.clear()
        val count = if (front) FRONT_LEAF_COUNT else BACK_LEAF_COUNT
        repeat(count) { index ->
            val seed = hash01(index + if (front) 109 else 17)
            val seed2 = hash01(index + if (front) 307 else 61)
            val phase = (elapsedSeconds / LOOP_SECONDS + seed) % 1.0
            val xBase = seed2 * 820f - 50f
            val drift = sin((phase * 2.0 * PI) + seed * 9.0).toFloat() * 42f
            val x = xBase + drift
            val y = 1370f - phase.toFloat() * 1540f
            val width = (if (front) 8f else 5f) + seed * 8f
            val height = width * (0.42f + seed2 * 0.2f)
            val angle = (phase * 2.0 * PI + seed2 * 4.0).toFloat()
            val red = if (index % 4 == 0) 0.72f else 0.28f
            val green = if (index % 4 == 0) 0.78f else 0.48f
            val blue = 0.12f
            val alpha = if (front) 0.56f else 0.34f
            putLeaf(x, y, width, height, angle, red, green, blue, alpha)
        }
        drawDecorations(decorationVertices.position(), intensity)
    }

    private fun putLeaf(
        x: Float,
        y: Float,
        radiusX: Float,
        radiusY: Float,
        angle: Float,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
    ) {
        val centerX = x
        val centerY = y
        val cosAngle = cos(angle.toDouble()).toFloat()
        val sinAngle = sin(angle.toDouble()).toFloat()
        var previousX = x + cosAngle * radiusX
        var previousY = y + sinAngle * radiusX
        repeat(LEAF_SEGMENTS) { segment ->
            val local = (segment + 1) * (2.0 * PI / LEAF_SEGMENTS)
            val localX = cos(local).toFloat() * radiusX
            val localY = sin(local).toFloat() * radiusY
            val nextX = x + localX * cosAngle - localY * sinAngle
            val nextY = y + localX * sinAngle + localY * cosAngle
            putTriangle(
                centerX,
                centerY,
                previousX,
                previousY,
                nextX,
                nextY,
                red,
                green,
                blue,
                alpha,
            )
            previousX = nextX
            previousY = nextY
        }
    }

    private fun putTriangle(
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float,
        cx: Float,
        cy: Float,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
    ) {
        putDecorationVertex(ax, ay, red, green, blue, alpha)
        putDecorationVertex(bx, by, red, green, blue, alpha)
        putDecorationVertex(cx, cy, red, green, blue, alpha)
    }

    private fun putDecorationVertex(
        x: Float,
        y: Float,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
    ) {
        decorationVertices.put(x)
        decorationVertices.put(y)
        decorationVertices.put(red)
        decorationVertices.put(green)
        decorationVertices.put(blue)
        decorationVertices.put(alpha)
    }

    private fun drawDecorations(floatCount: Int, intensity: Float) {
        if (floatCount == 0) return
        val vertexCount = floatCount / DECORATION_VERTEX_FLOATS
        decorationVertices.flip()
        val program = requireNotNull(decorationProgram)
        program.use()
        program.uniform2f("uCanvas", SCENE_WIDTH.toFloat(), SCENE_HEIGHT.toFloat())
        program.uniform2f("uSurface", surfaceWidth.toFloat(), surfaceHeight.toFloat())
        program.uniform1f("uIntensity", intensity)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, decorationBuffer)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            floatCount * Float.SIZE_BYTES,
            decorationVertices,
            GLES30.GL_STREAM_DRAW,
        )
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(
            0,
            2,
            GLES30.GL_FLOAT,
            false,
            DECORATION_VERTEX_FLOATS * Float.SIZE_BYTES,
            0,
        )
        GLES30.glVertexAttribPointer(
            1,
            4,
            GLES30.GL_FLOAT,
            false,
            DECORATION_VERTEX_FLOATS * Float.SIZE_BYTES,
            2 * Float.SIZE_BYTES,
        )
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, vertexCount)
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
    }

    private fun hash01(seed: Int): Float {
        var value = seed * 0x45d9f3b
        value = value xor (value ushr 16)
        value *= 0x45d9f3b
        value = value xor (value ushr 16)
        return (value and 0x00ffffff) / 16777216f
    }

    private fun checkGl(operation: String) {
        val error = GLES30.glGetError()
        check(error == GLES30.GL_NO_ERROR) { "$operation failed with GL error 0x${error.toString(16)}" }
    }

    private fun validateVertices(
        source: ByteBuffer,
        kind: Int,
        vertexCount: Int,
        boneCount: Int,
    ) {
        val reader = source.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        repeat(vertexCount) {
            val positionX = reader.getFloat()
            val positionY = reader.getFloat()
            val positionZ = reader.getFloat()
            val uvX = reader.getFloat()
            val uvY = reader.getFloat()
            require(
                positionX.isFinite() && positionY.isFinite() && positionZ.isFinite() &&
                    uvX.isFinite() && uvY.isFinite()
            ) { "Non-finite CATV0001 vertex" }
            if (kind == LAYER_PUPPET) {
                val boneIndices = IntArray(4) { reader.get().toInt() and 0xff }
                val weights = FloatArray(4) { reader.getFloat() }
                require(boneIndices.all { it in 0 until boneCount }) {
                    "CATV0001 bone index outside layer skeleton"
                }
                require(weights.all { it.isFinite() && it in 0f..1.001f }) {
                    "Invalid CATV0001 skin weight"
                }
                require(kotlin.math.abs(weights.sum() - 1f) <= 0.02f) {
                    "CATV0001 skin weights do not sum to one"
                }
            }
        }
    }

    private fun validateIndices(source: ByteBuffer, indexCount: Int, vertexCount: Int) {
        val reader = source.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        repeat(indexCount) {
            require((reader.getShort().toInt() and 0xffff) < vertexCount) {
                "CATV0001 element index outside vertex range"
            }
        }
    }

    internal data class CatsSceneData(val layers: List<CatsLayerData>)

    internal data class CatsTextureData(val textureIndex: Int, val bitmap: Bitmap)

    internal data class CatsLayerData(
        val kind: Int,
        val textureIndex: Int,
        val boneCount: Int,
        val originX: Float,
        val originY: Float,
        val scaleX: Float,
        val scaleY: Float,
        val vertexCount: Int,
        val indexCount: Int,
        val vertices: ByteBuffer,
        val indices: ByteBuffer,
        val matrices: FloatArray,
    )

    private data class GlLayer(
        val data: CatsLayerData,
        val vertexBuffer: Int,
        val indexBuffer: Int,
    )

    private data class CatsRenderState(
        val intensity: Int = 100,
        val animationAllowed: Boolean = true,
    )

    private class GlProgram(vertexSource: String, fragmentSource: String) {
        private val program = GLES30.glCreateProgram().also { program ->
            val vertex = compile(GLES30.GL_VERTEX_SHADER, vertexSource)
            val fragment = compile(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
            try {
                GLES30.glAttachShader(program, vertex)
                GLES30.glAttachShader(program, fragment)
                GLES30.glLinkProgram(program)
                val status = IntArray(1)
                GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
                check(status[0] == GLES30.GL_TRUE) { GLES30.glGetProgramInfoLog(program) }
            } finally {
                GLES30.glDeleteShader(vertex)
                GLES30.glDeleteShader(fragment)
            }
        }
        private val uniforms = HashMap<String, Int>()

        fun use() = GLES30.glUseProgram(program)

        fun uniform(name: String): Int = uniforms.getOrPut(name) {
            GLES30.glGetUniformLocation(program, name).also { location ->
                check(location >= 0) { "Missing GL uniform $name" }
            }
        }

        fun uniform1f(name: String, value: Float) = GLES30.glUniform1f(uniform(name), value)

        fun uniform1i(name: String, value: Int) = GLES30.glUniform1i(uniform(name), value)

        fun uniform2f(name: String, x: Float, y: Float) =
            GLES30.glUniform2f(uniform(name), x, y)

        fun release() = GLES30.glDeleteProgram(program)

        companion object {
            private fun compile(type: Int, source: String): Int =
                GLES30.glCreateShader(type).also { shader ->
                    GLES30.glShaderSource(shader, source)
                    GLES30.glCompileShader(shader)
                    val status = IntArray(1)
                    GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
                    check(status[0] == GLES30.GL_TRUE) { GLES30.glGetShaderInfoLog(shader) }
                }
        }
    }

    private companion object {
        val CATV_MAGIC = "CATV0001".encodeToByteArray()
        const val MAX_SCENE_BYTES = 2 * 1024 * 1024
        const val SCENE_WIDTH = 720
        const val SCENE_HEIGHT = 1280
        const val FRAME_RATE = 30
        const val FRAME_COUNT = 300
        const val LOOP_SECONDS = 10.0
        const val LAYER_COUNT = 7
        const val LAYER_PUPPET = 1
        const val MATRIX_FLOATS = 16
        const val STATIC_VERTEX_BYTES = 5 * Float.SIZE_BYTES
        const val PUPPET_VERTEX_BYTES = 9 * Float.SIZE_BYTES + 4
        const val HIGH_RES_LONG_EDGE = 2560
        const val HIGH_RES_MEMORY_CLASS_MB = 256
        const val DECORATION_VERTEX_FLOATS = 6
        const val MAX_DECORATION_VERTICES = 1024
        const val LEAF_SEGMENTS = 6
        const val BACK_LEAF_COUNT = 14
        const val FRONT_LEAF_COUNT = 8
        const val BIRD_COUNT = 3
        val EXPECTED_KINDS = intArrayOf(0, 0, 1, 1, 0, 1, 1)
        val EXPECTED_BONES = intArrayOf(0, 0, 3, 3, 0, 3, 8)
        val EXPECTED_VERTICES = intArrayOf(4, 4, 328, 351, 4, 167, 666)
        val EXPECTED_INDICES = intArrayOf(6, 6, 1575, 1722, 6, 852, 3210)
        val EXPECTED_TEXTURE_WIDTHS = intArrayOf(1440, 1430, 960, 884, 1496, 1196, 1172)
        val EXPECTED_TEXTURE_HEIGHTS = intArrayOf(2560, 1280, 1038, 1124, 1938, 1186, 1656)

        const val STATIC_VERTEX_SHADER = """#version 300 es
            layout(location = 0) in vec3 aPosition;
            layout(location = 1) in vec2 aUv;
            uniform vec2 uOrigin;
            uniform vec2 uScale;
            uniform vec2 uCanvas;
            uniform vec2 uSurface;
            uniform float uTime;
            uniform float uSway;
            out vec2 vUv;
            void main() {
                vec2 local = aPosition.xy;
                float heightWeight = smoothstep(-420.0, 420.0, local.y);
                local.x += sin(uTime * 0.82 + local.y * 0.011) * uSway * 3.2 * heightWeight;
                vec2 scene = uOrigin + local * uScale;
                float cover = max(uSurface.x / uCanvas.x, uSurface.y / uCanvas.y);
                vec2 clip = (scene - uCanvas * 0.5) * cover * 2.0 / uSurface;
                gl_Position = vec4(clip, 0.0, 1.0);
                vUv = aUv;
            }
        """

        const val PUPPET_VERTEX_SHADER = """#version 300 es
            layout(location = 0) in vec3 aPosition;
            layout(location = 1) in vec2 aUv;
            layout(location = 2) in uvec4 aBoneIndices;
            layout(location = 3) in vec4 aBoneWeights;
            uniform mat4 uBones[8];
            uniform vec2 uOrigin;
            uniform vec2 uScale;
            uniform vec2 uCanvas;
            uniform vec2 uSurface;
            uniform float uTime;
            uniform float uSway;
            out vec2 vUv;
            void main() {
                mat4 skin = uBones[aBoneIndices.x] * aBoneWeights.x
                    + uBones[aBoneIndices.y] * aBoneWeights.y
                    + uBones[aBoneIndices.z] * aBoneWeights.z
                    + uBones[aBoneIndices.w] * aBoneWeights.w;
                vec2 local = (skin * vec4(aPosition, 1.0)).xy;
                local.x += sin(uTime * 0.74 + local.y * 0.012) * uSway * 2.7;
                vec2 scene = uOrigin + local * uScale;
                float cover = max(uSurface.x / uCanvas.x, uSurface.y / uCanvas.y);
                vec2 clip = (scene - uCanvas * 0.5) * cover * 2.0 / uSurface;
                gl_Position = vec4(clip, 0.0, 1.0);
                vUv = aUv;
            }
        """

        const val TEXTURE_FRAGMENT_SHADER = """#version 300 es
            precision mediump float;
            uniform sampler2D uTexture;
            in vec2 vUv;
            out vec4 fragColor;
            void main() {
                fragColor = texture(uTexture, vUv);
            }
        """

        const val FULL_SCREEN_VERTEX_SHADER = """#version 300 es
            layout(location = 0) in vec2 aPosition;
            out vec2 vUv;
            void main() {
                vUv = aPosition * 0.5 + 0.5;
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """

        const val LIGHT_FRAGMENT_SHADER = """#version 300 es
            precision mediump float;
            uniform float uTime;
            uniform float uIntensity;
            in vec2 vUv;
            out vec4 fragColor;
            void main() {
                vec2 p = vUv - vec2(0.34, 0.58);
                float radial = 1.0 - smoothstep(0.05, 0.72, length(p));
                float ray = exp(-abs(p.x + p.y * 0.22) * 7.5) * radial;
                float pulse = 0.82 + 0.18 * sin(uTime * 0.63);
                float alpha = ray * pulse * uIntensity * 0.055;
                vec3 color = vec3(0.84, 1.0, 0.62);
                fragColor = vec4(color * alpha, alpha);
            }
        """

        const val DECORATION_VERTEX_SHADER = """#version 300 es
            layout(location = 0) in vec2 aPosition;
            layout(location = 1) in vec4 aColor;
            uniform vec2 uCanvas;
            uniform vec2 uSurface;
            out vec4 vColor;
            void main() {
                float cover = max(uSurface.x / uCanvas.x, uSurface.y / uCanvas.y);
                vec2 clip = (aPosition - uCanvas * 0.5) * cover * 2.0 / uSurface;
                gl_Position = vec4(clip, 0.0, 1.0);
                vColor = aColor;
            }
        """

        const val DECORATION_FRAGMENT_SHADER = """#version 300 es
            precision mediump float;
            uniform float uIntensity;
            in vec4 vColor;
            out vec4 fragColor;
            void main() {
                float alpha = vColor.a * uIntensity;
                fragColor = vec4(vColor.rgb * alpha, alpha);
            }
        """
    }
}

internal object CatsMotionAssets {
    const val ROOT = "listening_motion/cartoon/cats"
    const val SCENE = "scene.catv"
    val TEXTURES = listOf(
        "background.webp",
        "canopy.webp",
        "black_cat.webp",
        "white_cat.webp",
        "fence.webp",
        "paws.webp",
        "foreground.webp",
    )
    val REQUIRED_FILES = setOf(SCENE) + TEXTURES

    fun path(fileName: String): String = "$ROOT/$fileName"
}

private fun ByteBuffer.unsignedShort(): Int = getShort().toInt() and 0xffff

private fun ByteBuffer.copyDirect(byteCount: Int): ByteBuffer {
    require(byteCount >= 0 && remaining() >= byteCount)
    val result = ByteBuffer.allocateDirect(byteCount).order(ByteOrder.nativeOrder())
    val oldLimit = limit()
    limit(position() + byteCount)
    result.put(this)
    limit(oldLimit)
    result.flip()
    return result
}

private fun Int.checkedMultiply(other: Int): Int {
    val result = toLong() * other.toLong()
    require(result in 0..Int.MAX_VALUE)
    return result.toInt()
}
