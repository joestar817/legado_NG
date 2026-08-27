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

/**
 * Renders the accepted portrait Rain Night background and its bounded procedural overlays.
 *
 * At 100% master intensity the fixed baseline matches the accepted prototype: rain 240%,
 * droplets 160%, fog 160%, and leaves 60%. The shared master slider scales opacity only.
 *
 * The five draws are: opaque background/wet pulse, four logical rain layers in one instanced
 * draw, 48 glass droplets, six screen-blended fog sprites, and 25 slow leaves. Droplet gradients
 * and leaf silhouettes are analytic approximations of Canvas raster shapes; their accepted
 * timing and coordinates are preserved. No video, audio, mask, framebuffer, or per-frame vertex
 * upload is used.
 */
internal class ReadAloudRainNightRenderer(context: Context) {

    private val appContext = context.applicationContext
    private val quad: FloatBuffer = ByteBuffer.allocateDirect(8 * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
            position(0)
        }
    private val hashLookup: FloatBuffer = ByteBuffer.allocateDirect(
        RainNightProfile.HASH_ENTRY_COUNT * Float.SIZE_BYTES
    ).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        repeat(RainNightProfile.HASH_ENTRY_COUNT) { seed ->
            put(RainNightProfile.hash(seed).toFloat())
        }
        position(0)
    }

    @Volatile
    private var renderState = RainNightRenderState()

    private val resetFrameClock = AtomicBoolean(true)

    private var ready = false
    private var surfaceWidth = 1
    private var surfaceHeight = 1
    private var sceneWidth = SCENE_WIDTH
    private var sceneHeight = SCENE_HEIGHT
    private var lastFrameNanos = 0L
    private var elapsedSeconds = 0.0
    private var sceneTexture = 0
    private var hashTexture = 0
    private var sceneProgram: GlProgram? = null
    private var rainProgram: GlProgram? = null
    private var dropletProgram: GlProgram? = null
    private var fogProgram: GlProgram? = null
    private var leafProgram: GlProgram? = null
    private var validateNextFrame = true

    fun update(intensity: Int, animationAllowed: Boolean) {
        val previous = renderState
        renderState = RainNightRenderState(
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
        rainProgram = GlProgram(RAIN_VERTEX_SHADER, RAIN_FRAGMENT_SHADER)
        dropletProgram = GlProgram(DROPLET_VERTEX_SHADER, DROPLET_FRAGMENT_SHADER)
        fogProgram = GlProgram(FOG_VERTEX_SHADER, FOG_FRAGMENT_SHADER)
        leafProgram = GlProgram(LEAF_VERTEX_SHADER, LEAF_FRAGMENT_SHADER)
        val maxTextureSize = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, maxTextureSize, 0)
        check(maxTextureSize[0] >= SCENE_HEIGHT / 2) {
            "Rain Night scene requires at least " + (SCENE_HEIGHT / 2) +
                "px textures; device limit is " + maxTextureSize[0] + "px"
        }
        uploadHashLookupTexture()
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

    fun decodeAssets(maxTextureSize: Int): RainNightTextureData {
        val sceneSampleSize = if (maxTextureSize >= SCENE_HEIGHT) 1 else 2
        val scene = decodeBitmap(RainNightMotionAssets.BACKGROUND, sceneSampleSize)
        try {
            check(
                scene.width == SCENE_WIDTH / sceneSampleSize &&
                    scene.height == SCENE_HEIGHT / sceneSampleSize
            ) {
                "Unexpected Rain Night scene size " + scene.width + "x" + scene.height
            }
            return RainNightTextureData(scene)
        } catch (error: Throwable) {
            scene.recycle()
            throw error
        }
    }

    fun uploadAssets(data: RainNightTextureData) {
        val uploadedTexture = IntArray(1)
        GLES30.glGenTextures(1, uploadedTexture, 0)
        try {
            uploadTexture(uploadedTexture[0], data.scene)
            sceneWidth = data.scene.width
            sceneHeight = data.scene.height
            sceneTexture = uploadedTexture[0]
            ready = true
        } catch (error: Throwable) {
            GLES30.glDeleteTextures(1, uploadedTexture, 0)
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

        val intensity = if (animationActive) state.intensity / 100f else 0f
        val time = (elapsedSeconds % GPU_TIME_WRAP_SECONDS).toFloat()
        renderScene(time, intensity)
        if (animationActive) {
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
            renderRain(time, intensity)
            renderDroplets(time, intensity)
            GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_COLOR)
            renderFog(time, intensity)
            GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
            renderLeaves(time, intensity)
            GLES30.glDisable(GLES30.GL_BLEND)
        }
        if (validateNextFrame) {
            val error = GLES30.glGetError()
            check(error == GLES30.GL_NO_ERROR) {
                "Rain Night first complete frame GL error: 0x" + error.toString(16)
            }
            validateNextFrame = false
        }
    }

    fun release() {
        if (sceneTexture != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(sceneTexture), 0)
            sceneTexture = 0
        }
        if (hashTexture != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(hashTexture), 0)
            hashTexture = 0
        }
        sceneProgram?.release()
        sceneProgram = null
        rainProgram?.release()
        rainProgram = null
        dropletProgram?.release()
        dropletProgram = null
        fogProgram?.release()
        fogProgram = null
        leafProgram?.release()
        leafProgram = null
        ready = false
    }

    private fun renderScene(time: Float, intensity: Float) {
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

        GLES30.glDisable(GLES30.GL_BLEND)
        program.bind()
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, sceneTexture)
        program.uniform1i("uScene", 0)
        program.uniform1f("uTime", time)
        program.uniform1f("uIntensity", intensity)
        program.uniform2f("uCropScale", cropScaleX, cropScaleY)
        quad.position(0)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, quad)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glDisableVertexAttribArray(0)
    }

    private fun renderRain(time: Float, intensity: Float) {
        val program = checkNotNull(rainProgram)
        bindParticleProgram(program, time, intensity)
        GLES30.glDrawArraysInstanced(
            GLES30.GL_TRIANGLES,
            0,
            PARTICLE_VERTEX_COUNT,
            RAIN_LINE_COUNT,
        )
    }

    private fun renderDroplets(time: Float, intensity: Float) {
        val program = checkNotNull(dropletProgram)
        bindParticleProgram(program, time, intensity)
        GLES30.glDrawArraysInstanced(
            GLES30.GL_TRIANGLES,
            0,
            PARTICLE_VERTEX_COUNT,
            DROPLET_COUNT,
        )
    }

    private fun renderFog(time: Float, intensity: Float) {
        val program = checkNotNull(fogProgram)
        bindParticleProgram(program, time, intensity)
        GLES30.glDrawArraysInstanced(
            GLES30.GL_TRIANGLES,
            0,
            PARTICLE_VERTEX_COUNT,
            FOG_COUNT,
        )
    }

    private fun renderLeaves(time: Float, intensity: Float) {
        val program = checkNotNull(leafProgram)
        bindParticleProgram(program, time, intensity)
        GLES30.glDrawArraysInstanced(
            GLES30.GL_TRIANGLES,
            0,
            PARTICLE_VERTEX_COUNT,
            LEAF_COUNT,
        )
    }

    private fun bindParticleProgram(program: GlProgram, time: Float, intensity: Float) {
        val referenceScale = maxOf(
            surfaceWidth / REFERENCE_WIDTH,
            surfaceHeight / REFERENCE_HEIGHT,
        )
        program.bind()
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, hashTexture)
        program.uniform1i("uHash", 0)
        program.uniform1f("uTime", time)
        program.uniform1f("uIntensity", intensity)
        program.uniform2f(
            "uPixel",
            2f * referenceScale / surfaceWidth,
            2f * referenceScale / surfaceHeight,
        )
    }

    private fun uploadHashLookupTexture() {
        check(
            RainNightProfile.HASH_TEXTURE_WIDTH * RainNightProfile.HASH_TEXTURE_HEIGHT ==
                RainNightProfile.HASH_ENTRY_COUNT
        ) { "Rain Night hash texture dimensions do not cover the lookup table" }
        check(RainNightProfile.MAX_USED_HASH_INDEX < RainNightProfile.HASH_ENTRY_COUNT) {
            "Rain Night hash seed exceeds the lookup table"
        }
        check(
            RainNightProfile.computedMaxUsedHashIndex() ==
                RainNightProfile.MAX_USED_HASH_INDEX
        ) { "Rain Night hash seed bound is stale" }
        val uploadedTexture = IntArray(1)
        GLES30.glGenTextures(1, uploadedTexture, 0)
        try {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, uploadedTexture[0])
            GLES30.glTexParameteri(
                GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_MIN_FILTER,
                GLES30.GL_NEAREST,
            )
            GLES30.glTexParameteri(
                GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_MAG_FILTER,
                GLES30.GL_NEAREST,
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
            GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, Float.SIZE_BYTES)
            hashLookup.position(0)
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D,
                0,
                GLES30.GL_R32F,
                RainNightProfile.HASH_TEXTURE_WIDTH,
                RainNightProfile.HASH_TEXTURE_HEIGHT,
                0,
                GLES30.GL_RED,
                GLES30.GL_FLOAT,
                hashLookup,
            )
            val error = GLES30.glGetError()
            check(error == GLES30.GL_NO_ERROR) {
                "Unable to upload Rain Night hash lookup: 0x" + error.toString(16)
            }
            hashTexture = uploadedTexture[0]
        } catch (error: Throwable) {
            GLES30.glDeleteTextures(1, uploadedTexture, 0)
            throw error
        }
    }

    private fun uploadTexture(texture: Int, bitmap: Bitmap) {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
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
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        val error = GLES30.glGetError()
        check(error == GLES30.GL_NO_ERROR) {
            "Unable to upload Rain Night texture: 0x" + error.toString(16)
        }
    }

    private fun decodeBitmap(fileName: String, sampleSize: Int): Bitmap =
        appContext.assets.open(RainNightMotionAssets.path(fileName)).use { input ->
            BitmapFactory.decodeStream(
                input,
                null,
                BitmapFactory.Options().apply {
                    inScaled = false
                    inSampleSize = sampleSize
                    inPremultiplied = false
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            ) ?: error("Unable to decode Rain Night asset " + fileName)
        }

    internal data class RainNightTextureData(val scene: Bitmap) {
        fun recycle() {
            if (!scene.isRecycled) scene.recycle()
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
                "Rain Night program link failed: " + GLES30.glGetProgramInfoLog(program)
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

    private data class RainNightRenderState(
        val intensity: Int = 100,
        val animationAllowed: Boolean = true,
    )

    private companion object {
        const val SCENE_WIDTH = 1440
        const val SCENE_HEIGHT = 3200
        const val REFERENCE_WIDTH = RainNightProfile.REFERENCE_WIDTH
        const val REFERENCE_HEIGHT = RainNightProfile.REFERENCE_HEIGHT
        const val PARTICLE_VERTEX_COUNT = 6
        const val RAIN_LINE_COUNT = RainNightProfile.RAIN_LINE_COUNT
        const val DROPLET_COUNT = RainNightProfile.DROPLET_COUNT
        const val FOG_COUNT = RainNightProfile.FOG_COUNT
        const val LEAF_COUNT = RainNightProfile.LEAF_COUNT
        const val GPU_TIME_WRAP_SECONDS = 20.0

        fun compileShader(type: Int, source: String): Int {
            val shader = GLES30.glCreateShader(type)
            GLES30.glShaderSource(shader, source)
            GLES30.glCompileShader(shader)
            val status = IntArray(1)
            GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
            check(status[0] == GLES30.GL_TRUE) {
                "Rain Night shader compile failed: " + GLES30.glGetShaderInfoLog(shader)
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
            uniform float uTime;
            uniform float uIntensity;
            uniform vec2 uCropScale;
            out vec4 fragColor;

            vec3 sampleScene(vec2 uv) {
                vec2 bitmapUv = vec2(uv.x, 1.0 - uv.y);
                return texture(uScene, clamp(bitmapUv, vec2(.001), vec2(.999))).rgb;
            }

            void main() {
                vec2 sceneUv = (vUv - .5) * uCropScale + .5;
                vec3 color = sampleScene(sceneUv);
                float loop = mod(uTime, 20.0) / 20.0;
                float wetZone = 1.0 - smoothstep(.0, .31, sceneUv.y);
                float wetPulse = .5 + .5 * sin(
                    sceneUv.x * 22.0 - loop * 6.28318530718
                );
                color += vec3(.014, .020, .016) * wetZone *
                    (.35 + .18 * wetPulse) * uIntensity;
                fragColor = vec4(color, 1.0);
            }
        """

        const val PARTICLE_QUAD = """
            const vec2 QUAD[6] = vec2[6](
                vec2(-1.0, -1.0),
                vec2( 1.0, -1.0),
                vec2(-1.0,  1.0),
                vec2(-1.0,  1.0),
                vec2( 1.0, -1.0),
                vec2( 1.0,  1.0)
            );
            uniform highp sampler2D uHash;
            float hash11(int seed) {
                int x = seed % 1024;
                int y = seed / 1024;
                return texelFetch(uHash, ivec2(x, y), 0).r;
            }
        """

        const val RAIN_VERTEX_SHADER = """#version 300 es
            precision highp float;
            uniform float uTime;
            uniform float uIntensity;
            uniform vec2 uPixel;
            out vec2 vLocal;
            out vec4 vColor;
            flat out float vHalfLength;
            flat out float vRadius;
        """ + PARTICLE_QUAD + """
            void main() {
                int id = gl_InstanceID;
                int layer;
                int layerIndex;
                float cycles;
                float lengthPx;
                float widthPx;
                float baseAlpha;
                if (gl_InstanceID < 504) {
                    layer = 0;
                    layerIndex = id;
                    cycles = 11.0;
                    lengthPx = 18.0;
                    widthPx = .45;
                    baseAlpha = .065;
                } else if (gl_InstanceID < 888) {
                    layer = 1;
                    layerIndex = id - 504;
                    cycles = 16.0;
                    lengthPx = 30.0;
                    widthPx = .65;
                    baseAlpha = .095;
                } else if (gl_InstanceID < 1140) {
                    layer = 2;
                    layerIndex = id - 888;
                    cycles = 22.0;
                    lengthPx = 48.0;
                    widthPx = .90;
                    baseAlpha = .140;
                } else {
                    layer = 3;
                    layerIndex = id - 1140;
                    cycles = 30.0;
                    lengthPx = 76.0;
                    widthPx = 1.20;
                    baseAlpha = .200;
                }
                int seed = layerIndex + layer * 1009;
                float loop = mod(uTime, 20.0) / 20.0;
                float xPx = hash11(seed) * (732.0 + 80.0) - 40.0;
                float travelPx = 1626.0 + lengthPx + 100.0;
                float yPx = fract(hash11(seed + 31) + loop * cycles) *
                    travelPx - lengthPx;
                float centerYPx = yPx + lengthPx * .5;
                vec2 center = vec2(
                    (xPx - 366.0) * uPixel.x,
                    (813.0 - centerYPx) * uPixel.y
                );
                vec2 local = QUAD[gl_VertexID];
                float radius = widthPx * .5;
                vec2 position = center + local *
                    vec2(widthPx, lengthPx + widthPx) * .5 * uPixel;
                float alpha = baseAlpha * 1.3416407865 * uIntensity;
                vLocal = local;
                vColor = vec4(vec3(.800, .878431, .839216), alpha);
                vHalfLength = lengthPx * .5;
                vRadius = radius;
                gl_Position = vec4(position, 0.0, 1.0);
            }
        """

        const val RAIN_FRAGMENT_SHADER = """#version 300 es
            precision highp float;
            in vec2 vLocal;
            in vec4 vColor;
            flat in float vHalfLength;
            flat in float vRadius;
            out vec4 fragColor;
            void main() {
                vec2 point = vLocal * vec2(
                    vRadius,
                    vHalfLength + vRadius
                );
                float distanceToCapsule = length(vec2(
                    point.x,
                    max(abs(point.y) - vHalfLength, .0)
                )) - vRadius;
                float edge = max(fwidth(distanceToCapsule), .08);
                float coverage = 1.0 - smoothstep(-edge, edge, distanceToCapsule);
                float alpha = vColor.a * coverage;
                if (alpha < .003) discard;
                fragColor = vec4(vColor.rgb * alpha, alpha);
            }
        """

        const val DROPLET_VERTEX_SHADER = """#version 300 es
            precision highp float;
            uniform float uTime;
            uniform float uIntensity;
            uniform vec2 uPixel;
            out vec2 vLocal;
            out vec2 vGradient;
            out float vAlpha;
        """ + PARTICLE_QUAD + """
            void main() {
                int id = gl_InstanceID;
                float loop = mod(uTime, 20.0) / 20.0;
                float cycles = 2.0 + floor(hash11(id + 300) * 5.0);
                float phase = fract(loop * cycles + hash11(id + 390));
                float alive = phase <= .68 ? 1.0 : .0;
                float life = min(phase / .68, 1.0);
                float radiusPx = (2.0 + hash11(id + 410) * 9.0) *
                    (.55 + life * .70);
                float alpha = sin(life * 3.14159265359) * .16 * 1.6 *
                    uIntensity * alive;
                float xPx = 18.0 + hash11(id + 430) * (732.0 - 36.0);
                float yPx = 28.0 + hash11(id + 450) * (1626.0 - 110.0);
                vec2 center = vec2(
                    (xPx - 366.0) * uPixel.x,
                    (813.0 - yPx) * uPixel.y
                );
                vec2 local = QUAD[gl_VertexID];
                vec2 ellipse = local * vec2(radiusPx * .72, radiusPx);
                float cosine = cos(.12);
                float sine = sin(.12);
                vec2 rotated = vec2(
                    ellipse.x * cosine - ellipse.y * sine,
                    ellipse.x * sine + ellipse.y * cosine
                );
                vec2 position = center + vec2(rotated.x, -rotated.y) * uPixel;
                vLocal = local;
                vGradient = rotated / max(radiusPx, .001);
                vAlpha = alpha;
                gl_Position = vec4(position, 0.0, 1.0);
            }
        """

        const val DROPLET_FRAGMENT_SHADER = """#version 300 es
            precision highp float;
            in vec2 vLocal;
            in vec2 vGradient;
            in float vAlpha;
            out vec4 fragColor;
            void main() {
                float ellipse = length(vLocal);
                if (ellipse > 1.0 || vAlpha <= .0) discard;
                float gradientRadius = clamp(
                    (length(vGradient - vec2(-.25, -.25)) - .10) / .90,
                    .0,
                    1.0
                );
                float alphaFactor;
                vec3 color;
                if (gradientRadius < .55) {
                    float part = gradientRadius / .55;
                    alphaFactor = mix(.65, .20, part);
                    color = mix(vec3(.882353, .933333, .905882),
                        vec3(.682353, .800000, .745098), part);
                } else if (gradientRadius < .78) {
                    float part = (gradientRadius - .55) / .23;
                    alphaFactor = mix(.20, 1.0, part);
                    color = mix(vec3(.682353, .800000, .745098),
                        vec3(.886275, .933333, .909804), part);
                } else {
                    float part = (gradientRadius - .78) / .22;
                    alphaFactor = 1.0 - part;
                    color = mix(vec3(.886275, .933333, .909804),
                        vec3(.705882, .803922, .752941), part);
                }
                float edge = 1.0 - smoothstep(.96, 1.0, ellipse);
                float alpha = vAlpha * alphaFactor * edge;
                if (alpha < .003) discard;
                fragColor = vec4(color * alpha, alpha);
            }
        """

        const val FOG_VERTEX_SHADER = """#version 300 es
            precision highp float;
            uniform float uTime;
            uniform float uIntensity;
            uniform vec2 uPixel;
            out vec2 vLocal;
            out float vAlpha;
        """ + PARTICLE_QUAD + """
            void main() {
                int id = gl_InstanceID;
                int side = id / 3;
                int index = id % 3;
                int seed = 600 + side * 17 + index;
                float loop = mod(uTime, 20.0) / 20.0;
                float baseXPx = side == 0 ? -35.0 : 732.0 + 35.0;
                float xCycles = 1.0 + float(index % 2);
                float yCycles = 1.0 + float((index + 1) % 2);
                float xPx = baseXPx + sin(
                    6.28318530718 * (loop * xCycles + hash11(seed))
                ) * 28.0;
                float yPx = 240.0 + float(index) * 470.0 + sin(
                    6.28318530718 * (loop * yCycles + hash11(seed + 1))
                ) * 55.0;
                float radiusPx = 160.0 + hash11(seed) * 90.0;
                vec2 center = vec2(
                    (xPx - 366.0) * uPixel.x,
                    (813.0 - yPx) * uPixel.y
                );
                vec2 local = QUAD[gl_VertexID];
                vec2 position = center + vec2(local.x, -local.y) *
                    radiusPx * uPixel;
                vLocal = local;
                vAlpha = .037 * 1.6 * uIntensity;
                gl_Position = vec4(position, 0.0, 1.0);
            }
        """

        const val FOG_FRAGMENT_SHADER = """#version 300 es
            precision highp float;
            in vec2 vLocal;
            in float vAlpha;
            out vec4 fragColor;
            void main() {
                float radius = length(vLocal);
                float alpha = max(0.0, 1.0 - radius) * vAlpha;
                if (alpha < .002) discard;
                vec3 color = vec3(.541176, .639216, .584314);
                fragColor = vec4(color * alpha, alpha);
            }
        """

        const val LEAF_VERTEX_SHADER = """#version 300 es
            precision highp float;
            uniform float uTime;
            uniform float uIntensity;
            uniform vec2 uPixel;
            out vec2 vLocal;
            out vec4 vColor;
            flat out float vVariant;
        """ + PARTICLE_QUAD + """
            void main() {
                int id = gl_InstanceID;
                int seed = 5000 + id * 37;
                float loop = mod(uTime, 20.0) / 20.0;
                float phase = fract(loop + hash11(seed + 3));
                float fadeIn = clamp(phase / .10, .0, 1.0);
                float fadeOut = clamp((1.0 - phase) / .12, .0, 1.0);
                float alpha = clamp(
                    .89 * min(fadeIn, fadeOut) * .7745966692 * uIntensity,
                    .0,
                    1.0
                );
                bool rightEmitter = hash11(seed + 1) > .70;
                float startXPx = rightEmitter
                    ? 732.0 + 5.0 + hash11(seed + 4) * 30.0
                    : hash11(seed + 4) * 608.0;
                float startYPx = rightEmitter
                    ? 145.0 + hash11(seed + 5) * 113.0
                    : 175.0 + hash11(seed + 5) * 113.0;
                float driftDistancePx = rightEmitter
                    ? 80.0 + hash11(seed + 6) * 60.0
                    : 30.0 + hash11(seed + 6) * 50.0;
                float swayPx = sin(
                    6.28318530718 * (phase + hash11(seed + 9))
                ) * (8.0 + hash11(seed + 10) * 12.0);
                float xPx = startXPx - driftDistancePx * phase + swayPx;
                float yPx = startYPx +
                    (850.0 + hash11(seed + 7) * 300.0) * phase;
                bool small = hash11(seed + 13) < .24;
                float sizePx = small
                    ? 24.0 + hash11(seed + 14) * 12.0
                    : 42.0 + hash11(seed + 14) * 16.0;
                float spinDirection = hash11(seed + 15) < .5 ? -1.0 : 1.0;
                float rotation = hash11(seed + 17) * 6.28318530718 +
                    phase * spinDirection * (.25 + hash11(seed + 16) * .35) +
                    sin(6.28318530718 * (phase + hash11(seed + 18))) * .12;
                float flip = .72 + cos(
                    6.28318530718 * (phase + hash11(seed + 19))
                ) * .28;
                float variant = hash11(seed + 20) < .5 ? .0 : 1.0;
                float widthPx = sizePx * mix(.90, .68, variant) * flip;
                float heightPx = sizePx * mix(1.08, 1.16, variant);
                float cosine = cos(rotation);
                float sine = sin(rotation);
                vec2 local = QUAD[gl_VertexID];
                vec2 scaled = local * vec2(widthPx, heightPx) * .5;
                vec2 rotated = vec2(
                    scaled.x * cosine - scaled.y * sine,
                    scaled.x * sine + scaled.y * cosine
                );
                vec2 center = vec2(
                    (xPx - 366.0) * uPixel.x,
                    (813.0 - yPx) * uPixel.y
                );
                vec2 position = center + vec2(rotated.x, -rotated.y) * uPixel;
                float paletteIndex = floor(hash11(seed + 21) * 4.0);
                vec3 color;
                if (paletteIndex < 1.0) {
                    color = vec3(.301961, .415686, .164706);
                } else if (paletteIndex < 2.0) {
                    color = vec3(.301961, .384314, .0);
                } else if (paletteIndex < 3.0) {
                    color = vec3(.301961, .309804, .0);
                } else {
                    color = vec3(.396078, .482353, .188235);
                }
                vLocal = local;
                vColor = vec4(color, alpha);
                vVariant = variant;
                gl_Position = vec4(position, 0.0, 1.0);
            }
        """

        const val LEAF_FRAGMENT_SHADER = """#version 300 es
            precision highp float;
            in vec2 vLocal;
            in vec4 vColor;
            flat in float vVariant;
            out vec4 fragColor;
            void main() {
                float vertical = clamp(abs(vLocal.y), .0, 1.0);
                float boundA = pow(max(.0, 1.0 - vertical * vertical), .58);
                float boundB = pow(max(.0, 1.0 - vertical), .36) *
                    (.82 + .18 * (1.0 - vertical));
                float bound = mix(boundA, boundB, vVariant);
                float distanceToEdge = abs(vLocal.x) - bound;
                float edge = max(fwidth(distanceToEdge) * 1.5, .015);
                float coverage = 1.0 - smoothstep(-edge, edge, distanceToEdge);
                coverage *= 1.0 - smoothstep(.96, 1.0, vertical);
                float border = smoothstep(-.12, -.015, distanceToEdge) * coverage;
                float vein = (1.0 - smoothstep(.012, .050, abs(vLocal.x))) *
                    (1.0 - smoothstep(.15, .82, vertical));
                float alpha = coverage * vColor.a;
                if (alpha < .004) discard;
                vec3 color = mix(
                    vColor.rgb,
                    vec3(.109804, .149020, .047059),
                    border * .72
                );
                color = mix(color, vec3(.678431, .721569, .360784), vein * .52);
                fragColor = vec4(color * alpha, alpha);
            }
        """
    }
}

/** Pure accepted-profile constants and deterministic helpers, independent from Android/GL. */
internal object RainNightProfile {
    const val REFERENCE_WIDTH = 732f
    const val REFERENCE_HEIGHT = 1626f
    const val DURATION_SECONDS = 20f
    const val HASH_ENTRY_COUNT = 6144
    const val HASH_TEXTURE_WIDTH = 1024
    const val HASH_TEXTURE_HEIGHT = 6
    const val MAX_USED_HASH_INDEX = 5909
    const val RAIN_DENSITY = 2.4f
    const val DROP_LEVEL = 1.6f
    const val FOG_LEVEL = 1.6f
    const val LEAF_LEVEL = .6f
    const val RAIN_LAYER_0_COUNT = 504
    const val RAIN_LAYER_1_COUNT = 384
    const val RAIN_LAYER_2_COUNT = 252
    const val RAIN_LAYER_3_COUNT = 132
    const val RAIN_LINE_COUNT =
        RAIN_LAYER_0_COUNT + RAIN_LAYER_1_COUNT + RAIN_LAYER_2_COUNT + RAIN_LAYER_3_COUNT
    const val DROPLET_COUNT = 48
    const val FOG_COUNT = 6
    const val LEAF_COUNT = 25

    fun hash(value: Int): Double {
        val raw = kotlin.math.sin(value * 91.173 + 17.31) * 43758.5453
        return raw - kotlin.math.floor(raw)
    }

    fun rainCount(baseCount: Int): Int =
        kotlin.math.floor(baseCount * RAIN_DENSITY + .5).toInt()

    fun dropletCycles(index: Int): Int =
        2 + kotlin.math.floor(hash(index + 300) * 5.0).toInt()

    fun leafCount(): Int =
        kotlin.math.floor(42f * LEAF_LEVEL + .5f).toInt()

    fun computedMaxUsedHashIndex(): Int = maxOf(
        RAIN_LAYER_3_COUNT - 1 + 3 * 1009 + 31,
        DROPLET_COUNT - 1 + 450,
        600 + 17 + 2 + 1,
        5000 + (LEAF_COUNT - 1) * 37 + 21,
    )
}

internal object RainNightMotionAssets {
    const val ROOT = "listening_motion/cartoon/rain_night"
    const val BACKGROUND = "background.webp"
    val REQUIRED_FILES = setOf(BACKGROUND)

    fun path(fileName: String): String = "$ROOT/$fileName"
}
