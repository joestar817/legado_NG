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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/** Renders the accepted fixed Sakura crop, local lake ripples, and dense left-to-right petals. */
internal class ReadAloudSakuraRenderer(context: Context) {

    private val appContext = context.applicationContext
    private val quad: FloatBuffer = ByteBuffer.allocateDirect(8 * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
            position(0)
        }
    private val petalVertices: FloatBuffer = ByteBuffer.allocateDirect(
        MAX_PETALS * PETAL_VERTEX_COUNT * PETAL_VERTEX_FLOATS * Float.SIZE_BYTES
    ).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val petals = Array(MAX_PETALS) { index -> createPetal(index) }

    @Volatile
    private var renderState = SakuraRenderState()

    private val resetFrameClock = AtomicBoolean(true)

    private var ready = false
    private var surfaceWidth = 1
    private var surfaceHeight = 1
    private var sceneWidth = SCENE_WIDTH
    private var sceneHeight = SCENE_HEIGHT
    private var lastFrameNanos = 0L
    private var elapsedSeconds = 0.0
    private var textures = IntArray(0)
    private var sceneProgram: GlProgram? = null
    private var petalProgram: GlProgram? = null
    private var validateNextFrame = true

    fun update(intensity: Int, animationAllowed: Boolean) {
        val previous = renderState
        renderState = SakuraRenderState(
            intensity = intensity.coerceIn(0, 100),
            animationAllowed = animationAllowed,
        )
        if (previous.animationAllowed != animationAllowed) resetFrameClock.set(true)
    }

    fun shouldAnimate(): Boolean {
        val state = renderState
        return ready && state.animationAllowed && state.intensity > 0
    }

    fun onSurfaceCreated(): Int {
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        sceneProgram = GlProgram(SCENE_VERTEX_SHADER, SCENE_FRAGMENT_SHADER)
        petalProgram = GlProgram(PETAL_VERTEX_SHADER, PETAL_FRAGMENT_SHADER)
        val maxTextureSize = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, maxTextureSize, 0)
        check(maxTextureSize[0] >= SCENE_HEIGHT / 2) {
            "Sakura scene requires at least ${SCENE_HEIGHT / 2}px textures; " +
                "device limit is ${maxTextureSize[0]}px"
        }
        ready = false
        validateNextFrame = true
        resetFrameClock.set(true)
        lastFrameNanos = 0L
        elapsedSeconds = 0.0
        return maxTextureSize[0]
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        surfaceWidth = width.coerceAtLeast(1)
        surfaceHeight = height.coerceAtLeast(1)
    }

    fun decodeAssets(maxTextureSize: Int): SakuraTextureData {
        val sceneSampleSize = if (maxTextureSize >= SCENE_HEIGHT) 1 else 2
        val scene = decodeBitmap(SakuraMotionAssets.BACKGROUND, sceneSampleSize)
        val normal = decodeBitmap(SakuraMotionAssets.WATER_NORMAL, 1)
        val phase = decodeBitmap(SakuraMotionAssets.WATER_PHASE, 1)
        try {
            check(
                scene.width == SCENE_WIDTH / sceneSampleSize &&
                    scene.height == SCENE_HEIGHT / sceneSampleSize
            ) {
                "Unexpected Sakura scene size ${scene.width}x${scene.height}"
            }
            check(normal.width == NORMAL_SIZE && normal.height == NORMAL_SIZE) {
                "Unexpected Sakura normal size ${normal.width}x${normal.height}"
            }
            check(phase.width == PHASE_SIZE && phase.height == PHASE_SIZE) {
                "Unexpected Sakura phase size ${phase.width}x${phase.height}"
            }
            return SakuraTextureData(scene, normal, phase)
        } catch (error: Throwable) {
            scene.recycle()
            normal.recycle()
            phase.recycle()
            throw error
        }
    }

    fun uploadAssets(data: SakuraTextureData) {
        val uploadedTextures = IntArray(TEXTURE_COUNT)
        GLES30.glGenTextures(uploadedTextures.size, uploadedTextures, 0)
        try {
            uploadTexture(uploadedTextures[SCENE_TEXTURE], data.scene, repeat = false)
            uploadTexture(uploadedTextures[NORMAL_TEXTURE], data.normal, repeat = true)
            uploadTexture(uploadedTextures[PHASE_TEXTURE], data.phase, repeat = true)
            sceneWidth = data.scene.width
            sceneHeight = data.scene.height
            textures = uploadedTextures
            ready = true
        } catch (error: Throwable) {
            GLES30.glDeleteTextures(uploadedTextures.size, uploadedTextures, 0)
            throw error
        } finally {
            data.recycle()
        }
    }

    fun onDrawFrame(frameTimeNanos: Long) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        if (!ready) return

        if (resetFrameClock.getAndSet(false)) {
            lastFrameNanos = 0L
        }
        val state = renderState
        val animationActive = state.animationAllowed && state.intensity > 0
        if (animationActive) {
            val delta = if (lastFrameNanos == 0L) {
                0.0
            } else {
                ((frameTimeNanos - lastFrameNanos) / 1_000_000_000.0).coerceIn(0.0, 0.05)
            }
            elapsedSeconds += delta
            lastFrameNanos = frameTimeNanos
        } else {
            lastFrameNanos = 0L
        }

        renderScene(state, animationActive)
        if (animationActive) renderPetals(state)
        if (validateNextFrame) {
            val error = GLES30.glGetError()
            check(error == GLES30.GL_NO_ERROR) {
                "Sakura first frame GL error: 0x${error.toString(16)}"
            }
            validateNextFrame = false
        }
    }

    fun release() {
        if (textures.isNotEmpty()) {
            GLES30.glDeleteTextures(textures.size, textures, 0)
            textures = IntArray(0)
        }
        sceneProgram?.release()
        sceneProgram = null
        petalProgram?.release()
        petalProgram = null
        ready = false
    }

    private fun renderScene(state: SakuraRenderState, animationActive: Boolean) {
        val program = checkNotNull(sceneProgram)
        val sourceAspect = sceneWidth.toFloat() / sceneHeight
        val targetAspect = surfaceWidth.toFloat() / surfaceHeight
        val cropScaleX: Float
        val cropScaleY: Float
        if (targetAspect < sourceAspect) {
            cropScaleX = targetAspect / sourceAspect
            cropScaleY = 1f
        } else {
            cropScaleX = 1f
            cropScaleY = sourceAspect / targetAspect
        }
        val intensity = state.intensity / 100f
        val waterMotion = if (animationActive) 0.28f + intensity * 0.72f else 0f

        GLES30.glDisable(GLES30.GL_BLEND)
        program.bind()
        bindTexture(program, "uScene", textures[SCENE_TEXTURE], 0)
        bindTexture(program, "uNormal", textures[NORMAL_TEXTURE], 1)
        bindTexture(program, "uPhase", textures[PHASE_TEXTURE], 2)
        program.uniform1f("uTime", (elapsedSeconds % GPU_TIME_WRAP_SECONDS).toFloat())
        program.uniform1f("uMotion", waterMotion)
        program.uniform2f("uCropScale", cropScaleX, cropScaleY)
        quad.position(0)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, quad)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun renderPetals(state: SakuraRenderState) {
        val count = (MAX_PETALS * (state.intensity / 100f)).roundToInt()
        if (count <= 0) return
        val age = elapsedSeconds * (0.54 + state.intensity / 100.0 * 0.74)
        val scale = minOf(
            surfaceWidth / REFERENCE_WIDTH,
            surfaceHeight / REFERENCE_HEIGHT,
        )
        val scaleX = surfaceWidth / REFERENCE_WIDTH
        val scaleY = surfaceHeight / REFERENCE_HEIGHT
        val travelX = REFERENCE_WIDTH + 520.0
        val travelY = REFERENCE_HEIGHT + 520.0
        petalVertices.clear()
        repeat(count) { index ->
            val petal = petals[index]
            val flutter = sin(age * (2.0 + petal.depth * 2.4) + petal.phase)
            val wave = sin(age * (0.8 + petal.depth) + petal.phase) *
                (16.0 + petal.depth * 34.0)
            val x = (-200.0 + ((petal.speedX * age + petal.offsetX) % travelX) + wave) * scaleX
            val y = (
                -180.0 + ((petal.speedY * age + petal.offsetY) % travelY) +
                    cos(age * 1.2 + petal.phase) * 10.0
                ) * scaleY
            val rotation = petal.phase + age * petal.spin + flutter * 0.75
            val width = petal.size * (0.50 + abs(flutter) * 0.34) * scale
            val height = petal.size * scale
            val alpha = (0.38 + petal.depth * 0.54).toFloat()
            val red = if (petal.hue > 0.75) 1f else 249f / 255f
            val green = if (petal.hue > 0.75) 188f / 255f else 213f / 255f
            val blue = if (petal.hue > 0.75) 227f / 255f else 239f / 255f
            putPetalQuad(
                centerX = x,
                centerY = y,
                width = width,
                height = height,
                rotation = rotation,
                red = red,
                green = green,
                blue = blue,
                alpha = alpha,
            )
        }
        petalVertices.flip()
        val program = checkNotNull(petalProgram)
        program.bind()
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        val stride = PETAL_VERTEX_FLOATS * Float.SIZE_BYTES
        petalVertices.position(0)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, stride, petalVertices)
        petalVertices.position(2)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, stride, petalVertices)
        petalVertices.position(4)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 4, GLES30.GL_FLOAT, false, stride, petalVertices)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, count * PETAL_VERTEX_COUNT)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    private fun putPetalQuad(
        centerX: Double,
        centerY: Double,
        width: Double,
        height: Double,
        rotation: Double,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
    ) {
        putPetalVertex(-PETAL_QUAD, -PETAL_QUAD, centerX, centerY, width, height, rotation, red, green, blue, alpha)
        putPetalVertex(PETAL_QUAD, -PETAL_QUAD, centerX, centerY, width, height, rotation, red, green, blue, alpha)
        putPetalVertex(-PETAL_QUAD, PETAL_QUAD, centerX, centerY, width, height, rotation, red, green, blue, alpha)
        putPetalVertex(-PETAL_QUAD, PETAL_QUAD, centerX, centerY, width, height, rotation, red, green, blue, alpha)
        putPetalVertex(PETAL_QUAD, -PETAL_QUAD, centerX, centerY, width, height, rotation, red, green, blue, alpha)
        putPetalVertex(PETAL_QUAD, PETAL_QUAD, centerX, centerY, width, height, rotation, red, green, blue, alpha)
    }

    @Suppress("LongParameterList")
    private fun putPetalVertex(
        localX: Float,
        localY: Float,
        centerX: Double,
        centerY: Double,
        width: Double,
        height: Double,
        rotation: Double,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
    ) {
        val localPixelX = localX.toDouble() * width * 0.50
        val localPixelY = localY.toDouble() * height * 0.50
        val rotatedX = localPixelX * cos(rotation) - localPixelY * sin(rotation)
        val rotatedY = localPixelX * sin(rotation) + localPixelY * cos(rotation)
        val pixelX = centerX + rotatedX
        val pixelY = centerY + rotatedY
        petalVertices.put((pixelX / surfaceWidth * 2.0 - 1.0).toFloat())
        petalVertices.put((1.0 - pixelY / surfaceHeight * 2.0).toFloat())
        petalVertices.put(localX)
        petalVertices.put(localY)
        petalVertices.put(red)
        petalVertices.put(green)
        petalVertices.put(blue)
        petalVertices.put(alpha)
    }

    private fun bindTexture(program: GlProgram, name: String, texture: Int, unit: Int) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        program.uniform1i(name, unit)
    }

    private fun uploadTexture(texture: Int, bitmap: Bitmap, repeat: Boolean) {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        val wrap = if (repeat) GLES30.GL_REPEAT else GLES30.GL_CLAMP_TO_EDGE
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, wrap)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, wrap)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        val error = GLES30.glGetError()
        check(error == GLES30.GL_NO_ERROR) {
            "Unable to upload Sakura texture: 0x${error.toString(16)}"
        }
    }

    private fun decodeBitmap(fileName: String, sampleSize: Int): Bitmap =
        appContext.assets.open(SakuraMotionAssets.path(fileName)).use { input ->
            BitmapFactory.decodeStream(
                input,
                null,
                BitmapFactory.Options().apply {
                    inScaled = false
                    inSampleSize = sampleSize
                    inPremultiplied = false
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            ) ?: error("Unable to decode Sakura asset $fileName")
        }

    private fun createPetal(index: Int): Petal {
        val depth = 0.18 + seeded(index, 1) * 0.82
        return Petal(
            depth = depth,
            speedX = 23.0 + depth * 53.0 + seeded(index, 2) * 17.0,
            speedY = 16.0 + depth * 38.0 + seeded(index, 3) * 15.0,
            offsetX = seeded(index, 4) * (REFERENCE_WIDTH + 520.0),
            offsetY = seeded(index, 5) * (REFERENCE_HEIGHT + 520.0),
            size = 4.0 + depth * 18.0 + seeded(index, 7) * 7.0,
            phase = seeded(index, 8) * PI * 2.0,
            spin = (seeded(index, 9) - 0.5) * 4.8,
            hue = seeded(index, 10),
        )
    }

    private fun seeded(index: Int, salt: Int): Double {
        val value = sin(index * 127.1 + salt * 311.7) * 43758.5453
        return value - floor(value)
    }

    internal data class SakuraTextureData(
        val scene: Bitmap,
        val normal: Bitmap,
        val phase: Bitmap,
    ) {
        fun recycle() {
            if (!scene.isRecycled) scene.recycle()
            if (!normal.isRecycled) normal.recycle()
            if (!phase.isRecycled) phase.recycle()
        }
    }

    private class GlProgram(vertexSource: String, fragmentSource: String) {
        private val program = GLES30.glCreateProgram()
        private val uniforms = HashMap<String, Int>()

        init {
            val vertex = compileShader(GLES30.GL_VERTEX_SHADER, vertexSource)
            val fragment = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
            GLES30.glAttachShader(program, vertex)
            GLES30.glAttachShader(program, fragment)
            GLES30.glLinkProgram(program)
            val status = IntArray(1)
            GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
            GLES30.glDeleteShader(vertex)
            GLES30.glDeleteShader(fragment)
            check(status[0] == GLES30.GL_TRUE) {
                "Sakura program link failed: ${GLES30.glGetProgramInfoLog(program)}"
            }
        }

        fun bind() = GLES30.glUseProgram(program)
        fun release() = GLES30.glDeleteProgram(program)
        fun uniform1i(name: String, value: Int) = GLES30.glUniform1i(location(name), value)
        fun uniform1f(name: String, value: Float) = GLES30.glUniform1f(location(name), value)
        fun uniform2f(name: String, first: Float, second: Float) =
            GLES30.glUniform2f(location(name), first, second)

        private fun location(name: String): Int = uniforms.getOrPut(name) {
            GLES30.glGetUniformLocation(program, name)
        }
    }

    private data class SakuraRenderState(
        val intensity: Int = 100,
        val animationAllowed: Boolean = true,
    )

    private data class Petal(
        val depth: Double,
        val speedX: Double,
        val speedY: Double,
        val offsetX: Double,
        val offsetY: Double,
        val size: Double,
        val phase: Double,
        val spin: Double,
        val hue: Double,
    )

    private companion object {
        const val SCENE_WIDTH = 1440
        const val SCENE_HEIGHT = 3200
        const val NORMAL_SIZE = 256
        const val PHASE_SIZE = 32
        const val TEXTURE_COUNT = 3
        const val SCENE_TEXTURE = 0
        const val NORMAL_TEXTURE = 1
        const val PHASE_TEXTURE = 2
        const val MAX_PETALS = 360
        const val PETAL_VERTEX_COUNT = 6
        const val PETAL_VERTEX_FLOATS = 8
        const val PETAL_QUAD = 1.30f
        const val REFERENCE_WIDTH = 732.0
        const val REFERENCE_HEIGHT = 1626.0
        const val GPU_TIME_WRAP_SECONDS = 4096.0

        fun compileShader(type: Int, source: String): Int {
            val shader = GLES30.glCreateShader(type)
            GLES30.glShaderSource(shader, source)
            GLES30.glCompileShader(shader)
            val status = IntArray(1)
            GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
            check(status[0] == GLES30.GL_TRUE) {
                "Sakura shader compile failed: ${GLES30.glGetShaderInfoLog(shader)}"
            }
            return shader
        }

        const val SCENE_VERTEX_SHADER = """#version 300 es
            precision highp float;
            layout(location = 0) in vec2 aPosition;
            out vec2 vUv;
            void main() {
                vUv = aPosition * .5 + .5;
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """

        const val SCENE_FRAGMENT_SHADER = """#version 300 es
            precision highp float;
            in vec2 vUv;
            uniform sampler2D uScene;
            uniform sampler2D uNormal;
            uniform sampler2D uPhase;
            uniform float uTime;
            uniform float uMotion;
            uniform vec2 uCropScale;
            out vec4 fragColor;

            vec3 sampleScene(vec2 uv) {
                vec2 bitmapUv = vec2(uv.x, 1.0 - uv.y);
                return texture(uScene, clamp(bitmapUv, vec2(.001), vec2(.999))).rgb;
            }

            void main() {
                vec2 sceneUv = (vUv - .5) * uCropScale + .5;
                float lakeMask = 1.0 - smoothstep(.215, .265, sceneUv.y);
                float phase = texture(uPhase, sceneUv * vec2(6.0, 12.0)).r;
                vec2 normalUvA = sceneUv * vec2(4.4, 9.8) +
                    vec2(uTime * .0052, uTime * .0070);
                vec2 normalUvB = sceneUv * vec2(5.8, 12.9) -
                    vec2(uTime * .0037, uTime * .0051);
                vec3 n1 = texture(uNormal, normalUvA).xyz * 2.0 - 1.0;
                vec3 n2 = texture(uNormal, normalUvB).xyz * 2.0 - 1.0;
                vec2 normal = normalize(vec3(n1.xy + n2.xy, n1.z)).xy;
                vec2 fineWave = vec2(
                    sin(sceneUv.y * 180.0 + phase * 6.0 + uTime * .62) * .0018,
                    cos(sceneUv.x * 86.0 - phase * 4.0 - uTime * .43) * .0008
                );
                vec2 waterOffset = (normal * vec2(.0042, .0017) + fineWave) *
                    lakeMask * uMotion;
                vec3 base = sampleScene(sceneUv);
                vec3 moved = sampleScene(sceneUv + waterOffset);
                vec3 color = mix(base, moved, lakeMask);
                float highlight = pow(max(0.0, normal.y * .5 + .5), 10.0) *
                    .035 * lakeMask * uMotion;
                color += highlight * vec3(1.0, .86, .96);
                color = mix(color, color * vec3(1.015, 1.008, 1.028), .45);
                fragColor = vec4(color, 1.0);
            }
        """

        const val PETAL_VERTEX_SHADER = """#version 300 es
            precision highp float;
            layout(location = 0) in vec2 aPosition;
            layout(location = 1) in vec2 aLocal;
            layout(location = 2) in vec4 aColor;
            out vec2 vLocal;
            out vec4 vColor;
            void main() {
                vLocal = aLocal;
                vColor = aColor;
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """

        const val PETAL_FRAGMENT_SHADER = """#version 300 es
            precision highp float;
            in vec2 vLocal;
            in vec4 vColor;
            out vec4 fragColor;
            void main() {
                vec2 p = vec2(vLocal.x, -vLocal.y + .15);
                float base = p.x * p.x + p.y * p.y - 1.0;
                float shape = base * base * base - p.x * p.x * p.y * p.y * p.y;
                float edge = max(fwidth(shape), .012);
                float coverage = 1.0 - smoothstep(-edge, edge, shape);
                float alpha = vColor.a * coverage;
                if (alpha < .004) discard;
                float sheen = (1.0 - smoothstep(-.7, .9, vLocal.y)) * .08;
                vec3 color = mix(vColor.rgb, vec3(1.0), sheen);
                fragColor = vec4(color * alpha, alpha);
            }
        """
    }
}
