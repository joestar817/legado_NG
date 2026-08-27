package io.legado.app.ui.book.read.aloud

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES30
import io.legado.app.help.config.ListeningFireStyle
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Samples the accepted v3 fire source from four compact 24 fps WebP atlases, then applies the
 * Godfire / Inferno / Rift compositor in real time. The atlases only carry motion luminance;
 * color, heat distortion, optical fields, and the fixed-red Rift rings remain GPU driven.
 */
internal class ReadAloudFlipbookFireRenderer(
    context: Context,
) {

    private val appContext = context.applicationContext
    private val quad: FloatBuffer = ByteBuffer.allocateDirect(8 * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
            position(0)
        }

    @Volatile
    private var renderState = FireRenderState()

    private var ready = false
    private var surfaceWidth = 1
    private var surfaceHeight = 1
    private var lastFrameNanos = 0L
    private var elapsedSeconds = 0f
    private var atlasTextures = IntArray(0)
    private var loadedAtlasPages = BooleanArray(ATLAS_FILES.size)
    private var compositeProgram: GlProgram? = null
    private var validateNextFrame = true

    fun update(
        style: ListeningFireStyle,
        intensity: Int,
        color: Int,
        accentFollowsMain: Boolean,
    ) {
        renderState = FireRenderState(
            style = style,
            intensity = intensity.coerceIn(0, 100),
            color = color,
            accentFollowsMain = accentFollowsMain,
        )
    }

    fun onSurfaceCreated() {
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        ready = false
        val maxTextureSize = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, maxTextureSize, 0)
        check(maxTextureSize[0] >= maxOf(ATLAS_WIDTH, ATLAS_HEIGHT)) {
            "Fire atlas requires ${maxOf(ATLAS_WIDTH, ATLAS_HEIGHT)}px textures; " +
                "device limit is ${maxTextureSize[0]}px"
        }
        compositeProgram = GlProgram(COMPOSITE_FRAGMENT_SHADER)
        atlasTextures = createAtlasTextures()
        loadedAtlasPages = BooleanArray(ATLAS_FILES.size)
        validateNextFrame = true
        ready = true
        lastFrameNanos = 0L
        elapsedSeconds = 0f
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        surfaceWidth = width.coerceAtLeast(1)
        surfaceHeight = height.coerceAtLeast(1)
    }

    fun onDrawFrame(frameTimeNanos: Long) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        if (!ready) return

        val delta = if (lastFrameNanos == 0L) {
            0f
        } else {
            ((frameTimeNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 1f / 20f)
        }
        lastFrameNanos = frameTimeNanos
        elapsedSeconds = (elapsedSeconds + delta) % LOOP_SECONDS

        renderComposite(elapsedSeconds)
    }

    fun release() {
        if (atlasTextures.isNotEmpty()) {
            GLES30.glDeleteTextures(atlasTextures.size, atlasTextures, 0)
            atlasTextures = IntArray(0)
        }
        loadedAtlasPages = BooleanArray(ATLAS_FILES.size)
        compositeProgram?.release()
        compositeProgram = null
        ready = false
    }

    private fun renderComposite(time: Float) {
        val state = renderState
        val program = checkNotNull(compositeProgram)
        val frameA = resolveAtlasFrame(time)
        val frameB = resolveAtlasFrame(time + SECOND_PHASE_SECONDS)
        program.bind()
        bindTexture(program, "uSourceA", textureForPage(frameA.page), 0)
        bindTexture(program, "uSourceB", textureForPage(frameB.page), 1)
        program.uniform1f("uFrameA", frameA.localFrame.toFloat())
        program.uniform1f("uFrameB", frameB.localFrame.toFloat())
        program.uniform2f("uResolution", surfaceWidth.toFloat(), surfaceHeight.toFloat())
        program.uniform1f("uTime", time)
        program.uniform1f("uIntensity", state.intensity / 100f)
        program.uniform1f("uMode", state.style.ordinal.toFloat())
        program.uniform1f("uAccentFollowsMain", if (state.accentFollowsMain) 1f else 0f)
        program.uniform3f(
            "uMainColor",
            (state.color shr 16 and 0xFF) / 255f,
            (state.color shr 8 and 0xFF) / 255f,
            (state.color and 0xFF) / 255f,
        )
        quad.position(0)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, quad)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        if (validateNextFrame) {
            val error = GLES30.glGetError()
            check(error == GLES30.GL_NO_ERROR) {
                "Fire first frame GL error: 0x${error.toString(16)}"
            }
            validateNextFrame = false
        }
    }

    private fun textureForPage(page: Int): Int {
        if (loadedAtlasPages.getOrElse(page) { false }) return atlasTextures[page]
        val fallback = loadedAtlasPages.indexOfFirst { it }.takeIf { it >= 0 } ?: 0
        return atlasTextures[fallback]
    }

    private fun bindTexture(program: GlProgram, name: String, texture: Int, unit: Int) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        program.uniform1i(name, unit)
    }

    private fun resolveAtlasFrame(time: Float): AtlasFrame {
        val wrapped = ((time % LOOP_SECONDS) + LOOP_SECONDS) % LOOP_SECONDS
        val frameIndex = ((wrapped * FRAME_RATE + .5f).toInt() % FRAME_COUNT)
        return AtlasFrame(
            page = frameIndex / FRAMES_PER_PAGE,
            localFrame = frameIndex % FRAMES_PER_PAGE,
        )
    }

    private fun createAtlasTextures(): IntArray = IntArray(ATLAS_FILES.size).also { textures ->
        GLES30.glGenTextures(textures.size, textures, 0)
    }

    fun decodeAtlasPage(index: Int): ByteBuffer {
        check(index in ATLAS_FILES.indices)
        val name = ATLAS_FILES[index]
        val row = IntArray(ATLAS_WIDTH)
        val pixels = ByteBuffer.allocateDirect(ATLAS_WIDTH * ATLAS_HEIGHT)
        val bitmap = appContext.assets.open("$ASSET_ROOT/$name").use { input ->
            BitmapFactory.decodeStream(
                input,
                null,
                BitmapFactory.Options().apply {
                    inScaled = false
                    inPremultiplied = false
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            ) ?: error("Unable to decode fire atlas $name")
        }
        try {
            check(bitmap.width == ATLAS_WIDTH && bitmap.height == ATLAS_HEIGHT) {
                "Unexpected fire atlas size ${bitmap.width}x${bitmap.height}: $name"
            }
            pixels.clear()
            for (y in ATLAS_HEIGHT - 1 downTo 0) {
                bitmap.getPixels(row, 0, ATLAS_WIDTH, 0, y, ATLAS_WIDTH, 1)
                repeat(ATLAS_WIDTH) { x ->
                    pixels.put((row[x] shr 16 and 0xFF).toByte())
                }
            }
            pixels.flip()
            return pixels
        } finally {
            bitmap.recycle()
        }
    }

    fun uploadAtlasPage(index: Int, pixels: ByteBuffer) {
        if (loadedAtlasPages.getOrElse(index) { false }) return
        check(ready && index in ATLAS_FILES.indices && atlasTextures.size == ATLAS_FILES.size)
        val name = ATLAS_FILES[index]
        pixels.position(0)
        try {
            GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, atlasTextures[index])
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
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D,
                0,
                GLES30.GL_R8,
                ATLAS_WIDTH,
                ATLAS_HEIGHT,
                0,
                GLES30.GL_RED,
                GLES30.GL_UNSIGNED_BYTE,
                pixels,
            )
            check(GLES30.glGetError() == GLES30.GL_NO_ERROR) {
                "Unable to upload fire atlas $name"
            }
            loadedAtlasPages[index] = true
        } finally {
            pixels.position(0)
        }
    }

    private class GlProgram(fragmentSource: String) {
        private val program = GLES30.glCreateProgram()
        private val uniforms = HashMap<String, Int>()

        init {
            val vertex = compileShader(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER)
            val fragment = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
            GLES30.glAttachShader(program, vertex)
            GLES30.glAttachShader(program, fragment)
            GLES30.glLinkProgram(program)
            val status = IntArray(1)
            GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
            GLES30.glDeleteShader(vertex)
            GLES30.glDeleteShader(fragment)
            check(status[0] == GLES30.GL_TRUE) {
                "Program link failed: ${GLES30.glGetProgramInfoLog(program)}"
            }
        }

        fun bind() = GLES30.glUseProgram(program)
        fun release() = GLES30.glDeleteProgram(program)
        fun uniform1i(name: String, value: Int) = GLES30.glUniform1i(location(name), value)
        fun uniform1f(name: String, value: Float) = GLES30.glUniform1f(location(name), value)
        fun uniform2f(name: String, first: Float, second: Float) =
            GLES30.glUniform2f(location(name), first, second)
        fun uniform3f(name: String, first: Float, second: Float, third: Float) =
            GLES30.glUniform3f(location(name), first, second, third)

        private fun location(name: String): Int = uniforms.getOrPut(name) {
            GLES30.glGetUniformLocation(program, name)
        }
    }

    private data class FireRenderState(
        val style: ListeningFireStyle = ListeningFireStyle.GODFIRE,
        val intensity: Int = 40,
        val color: Int = DEFAULT_FIRE_COLOR,
        val accentFollowsMain: Boolean = false,
    )

    private data class AtlasFrame(
        val page: Int,
        val localFrame: Int,
    )

    private companion object {
        const val ASSET_ROOT = "listening_motion/fire_flipbook"
        const val ATLAS_WIDTH = 3072
        const val ATLAS_HEIGHT = 2496
        const val LOOP_SECONDS = 16f
        const val SECOND_PHASE_SECONDS = 7.4f
        const val FRAME_RATE = 24f
        const val FRAME_COUNT = 384
        const val FRAMES_PER_PAGE = 96
        const val DEFAULT_FIRE_COLOR = -0x002EC5D9
        val ATLAS_FILES = arrayOf(
            "fire-atlas-00.webp",
            "fire-atlas-01.webp",
            "fire-atlas-02.webp",
            "fire-atlas-03.webp",
        )

        fun compileShader(type: Int, source: String): Int {
            val shader = GLES30.glCreateShader(type)
            GLES30.glShaderSource(shader, source)
            GLES30.glCompileShader(shader)
            val status = IntArray(1)
            GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
            check(status[0] == GLES30.GL_TRUE) {
                "Shader compile failed: ${GLES30.glGetShaderInfoLog(shader)}"
            }
            return shader
        }

        const val VERTEX_SHADER = """#version 300 es
            precision highp float;
            layout(location = 0) in vec2 aPosition;
            out vec2 vUv;
            void main() {
                vUv = aPosition * .5 + .5;
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """

        const val COMPOSITE_FRAGMENT_SHADER = """#version 300 es
            precision highp float;
            in vec2 vUv;
            uniform sampler2D uSourceA;
            uniform sampler2D uSourceB;
            uniform float uFrameA;
            uniform float uFrameB;
            uniform vec2 uResolution;
            uniform float uTime;
            uniform float uIntensity;
            uniform float uMode;
            uniform float uAccentFollowsMain;
            uniform vec3 uMainColor;
            out vec4 fragColor;

            float hash21(vec2 p) {
                p = fract(p * vec2(123.34, 456.21));
                p += dot(p, p + 45.32);
                return fract(p.x * p.y);
            }

            float noise(vec2 p) {
                vec2 i = floor(p);
                vec2 f = fract(p);
                f = f * f * (3.0 - 2.0 * f);
                return mix(
                    mix(hash21(i), hash21(i + vec2(1.0, 0.0)), f.x),
                    mix(hash21(i + vec2(0.0, 1.0)), hash21(i + vec2(1.0, 1.0)), f.x),
                    f.y
                );
            }

            float fbm(vec2 p) {
                float value = 0.0;
                float amplitude = .5;
                mat2 rotation = mat2(.8, -.6, .6, .8);
                for (int i = 0; i < 5; i++) {
                    value += amplitude * noise(p);
                    p = rotation * p * 2.03 + 7.1;
                    amplitude *= .5;
                }
                return value;
            }

            vec2 atlasFrameUv(float frameIndex, vec2 uv) {
                float column = mod(frameIndex, 16.0);
                float row = floor(frameIndex / 16.0);
                float atlasX = column * 192.0 + .5 + uv.x * 191.0;
                float atlasYFromTop = row * 416.0 + .5 + (1.0 - uv.y) * 415.0;
                return vec2(atlasX / 3072.0, 1.0 - atlasYFromTop / 2496.0);
            }

            float sampleFire(sampler2D source, float frameIndex, vec2 uv) {
                if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) return 0.0;
                return texture(source, atlasFrameUv(frameIndex, uv)).r;
            }

            vec3 palette(float fire, float halo, float coreStart) {
                vec3 vivid = uMainColor / max(max(uMainColor.r, uMainColor.g), max(uMainColor.b, .001));
                vec3 deep = vivid * .025;
                vec3 wine = vivid * .20;
                vec3 hot = vivid;
                vec3 pale = mix(vivid, vec3(1.0), .84);
                vec3 color = mix(deep, wine, smoothstep(.025, .19, fire));
                color = mix(color, hot, smoothstep(.12, .52, fire));
                color = mix(color, pale, smoothstep(coreStart, 1.0, fire));
                color += vivid * .30 * halo;
                return color;
            }

            float softEllipse(vec2 point, vec2 center, vec2 scale) {
                vec2 delta = (point - center) * scale;
                return exp(-dot(delta, delta));
            }

            void main() {
                vec2 uv = vUv;
                vec2 screenPoint = uv * 2.0 - 1.0;
                vec2 p = uv * 2.0 - 1.0;
                p.x *= uResolution.x / uResolution.y;
                float n1 = fbm(vec2(p.y * 2.7 - uTime * .31, p.x * 3.2 + uTime * .13));
                float n2 = fbm(vec2(p.x * 4.1 + 17.0, p.y * 2.1 - uTime * .22));
                vec2 heatWarp = vec2(n1 - .5, n2 - .5) * (.014 + .016 * uIntensity);
                heatWarp.x += sin(p.y * 23.0 - uTime * 2.0) * .0038 * uIntensity;
                vec2 warped = uv + heatWarp;
                float baseA = sampleFire(uSourceA, uFrameA, warped);
                float baseB = sampleFire(
                    uSourceB,
                    uFrameB,
                    vec2(1.0 - warped.x, fract(warped.y + .045))
                );
                float flame = 0.0;
                float halo = 0.0;
                float ring = 0.0;
                float ring2 = 0.0;
                float coreStart = .76;

                if (uMode < .5) {
                    float echo1 = sampleFire(
                        uSourceB,
                        uFrameB,
                        vec2(warped.x * .82 + .09, warped.y + .028 * sin(uTime * .7))
                    );
                    float echo2 = sampleFire(
                        uSourceA,
                        uFrameA,
                        vec2(1.0 - warped.x, warped.y - .035)
                    );
                    flame = max(baseA, max(baseB * .70, echo1 * .48));
                    flame += echo2 * .18;
                    halo = smoothstep(.03, .32, flame) * .23;
                    coreStart = .67;
                } else if (uMode < 1.5) {
                    vec2 mirrorUv = vec2(abs(uv.x - .5) * 1.85, uv.y);
                    float mirror = sampleFire(
                        uSourceA,
                        uFrameA,
                        mirrorUv + heatWarp * 1.35
                    );
                    float tiled = sampleFire(
                        uSourceB,
                        uFrameB,
                        vec2(fract(warped.x * 1.64 + .18), warped.y)
                    );
                    float low = sampleFire(
                        uSourceA,
                        uFrameA,
                        vec2(1.0 - warped.x, clamp(warped.y * .88 + .11, 0.0, 1.0))
                    );
                    flame = max(baseA * .72, max(mirror, tiled * .72));
                    flame += low * .35;
                    flame = pow(clamp(flame * 1.18, 0.0, 1.0), .76);
                    halo = smoothstep(.02, .38, flame) * .34 +
                        (1.0 - smoothstep(.0, .78, uv.y)) * .12;
                    coreStart = .78;
                } else {
                    float angle = atan(p.y, p.x);
                    float radius = length(p);
                    float sector = abs(mod(angle + .5236, 1.0472) - .5236) / .5236;
                    vec2 kaleidoUv = vec2(
                        clamp(sector, 0.0, 1.0),
                        fract(radius * 1.38 - uTime * .038)
                    );
                    kaleidoUv += heatWarp * 1.7;
                    float kaleidoA = sampleFire(uSourceA, uFrameA, kaleidoUv);
                    float kaleidoB = sampleFire(
                        uSourceB,
                        uFrameB,
                        vec2(1.0 - kaleidoUv.x, fract(kaleidoUv.y + .19))
                    );
                    float shards = max(kaleidoA, kaleidoB * .82);
                    flame = max(baseA * .42, shards * .98);
                    flame += sampleFire(
                        uSourceA,
                        uFrameA,
                        vec2(fract(warped.x * 1.9), warped.y)
                    ) * .22;
                    float wave = fract(uTime * .156);
                    ring = exp(-82.0 * abs(radius - wave * .78));
                    ring2 = exp(-110.0 * abs(radius - fract(wave + .48) * .72));
                    halo = smoothstep(.025, .31, flame) * .30;
                    coreStart = .61;
                }

                flame = clamp(flame * (1.0 + uIntensity * .72), 0.0, 1.0);
                halo *= .62 + uIntensity * .72;
                vec3 color = palette(flame, halo, coreStart);
                vec3 vivid = uMainColor / max(max(uMainColor.r, uMainColor.g), max(uMainColor.b, .001));

                float slowNoiseA = noise(
                    screenPoint * vec2(1.28, 1.04) + vec2(uTime * .055, -uTime * .072)
                );
                float slowNoiseB = noise(
                    screenPoint * vec2(2.15, 1.72) + vec2(-uTime * .083, uTime * .041) + 9.7
                );
                float fogFlow = smoothstep(
                    .34,
                    .72,
                    slowNoiseA * .54 + slowNoiseB * .25 + n1 * .21
                );
                float spinPeriod = uMode < .5 ? 15.0 : (uMode < 1.5 ? 11.0 : 7.6);
                float spinAngle = atan(screenPoint.y, screenPoint.x) +
                    uTime * 6.2831853 / spinPeriod;
                float conicLobe = pow(.5 + .5 * sin(spinAngle * 3.0 + n2 * 1.35), 3.0);
                float fineLobe = pow(.5 + .5 * sin(spinAngle * 5.0 - 1.2), 6.0);
                float fieldRadius = length(screenPoint * vec2(.82, 1.0));
                float broadOrbit = exp(
                    -5.8 * abs(fieldRadius - (.56 + .055 * sin(uTime * .23)))
                );
                float fieldEnvelope = 1.0 - smoothstep(.62, 1.58, fieldRadius);
                float energyField = fogFlow * .60 + conicLobe * .18 +
                    fineLobe * .06 + broadOrbit * .16;
                energyField *= .24 + fieldEnvelope * .76;

                float edgePulse = .88 + .12 * sin(uTime * 1.495);
                float leftBloom = softEllipse(
                    screenPoint,
                    vec2(-1.08 + .05 * sin(uTime * .31), -.14 + .10 * sin(uTime * .23)),
                    vec2(1.28 / edgePulse, 1.72)
                );
                float rightBloom = softEllipse(
                    screenPoint,
                    vec2(1.08 + .05 * sin(uTime * .27 + 1.7), .20 + .11 * sin(uTime * .19 + .8)),
                    vec2(1.22 / edgePulse, 1.62)
                );
                float lowerBloom = softEllipse(
                    screenPoint,
                    vec2(.10 * sin(uTime * .21), -1.20 + .045 * sin(uTime * .37)),
                    vec2(.78, 1.34 / edgePulse)
                );
                float edgeBloom = max(max(leftBloom, rightBloom), lowerBloom);
                float energyWeight = uMode < .5 ? .54 : (uMode < 1.5 ? .20 : .90);
                float edgeWeight = uMode < .5 ? .72 : (uMode < 1.5 ? .92 : .76);
                float opticalStrength = .42 + uIntensity * .58;
                color += vivid * energyField * energyWeight * (.045 + opticalStrength * .050);
                color += vivid * edgeBloom * edgeWeight * (.060 + opticalStrength * .070);

                float sparkCell = hash21(floor(vec2(uv.x * 18.0, uv.y * 26.0)));
                vec2 sparkUv = fract(
                    vec2(uv.x * 18.0, uv.y * 26.0 + uTime * (.72 + sparkCell * 1.18))
                );
                float spark = (1.0 - smoothstep(.0, .055, abs(sparkUv.x - sparkCell))) *
                    (1.0 - smoothstep(.0, .16, sparkUv.y));
                spark *= step(.982, sparkCell) *
                    (uMode < .5 ? .08 : (uMode > 1.5 ? .12 : .045));
                color += vivid * spark * (.16 + uIntensity * .12);

                vec3 ringRed = vec3(.88, .025, .018);
                vec3 ringColor = mix(ringRed, vivid, uAccentFollowsMain);
                color += ringColor * (ring * .88 + ring2 * .26);
                float sideGlow = pow(
                    1.0 - smoothstep(.0, .34, min(uv.x, 1.0 - uv.x)),
                    2.0
                );
                float bottomGlow = pow(1.0 - smoothstep(.0, .32, uv.y), 2.0);
                color += vivid * (sideGlow * .035 + bottomGlow * .055) * uIntensity;
                float vignette = 1.0 - smoothstep(
                    .36,
                    1.18,
                    length(p * vec2(1.15, .76))
                );
                color *= mix(.72, 1.0, vignette);
                color = 1.0 - exp(-color * (1.05 + uIntensity * .66));
                float alpha = max(max(color.r, color.g), color.b);
                fragColor = vec4(color, alpha);
            }
        """
    }
}
