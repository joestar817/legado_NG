package io.legado.app.ui.book.read.aloud

import android.opengl.GLES30
import android.util.Log
import io.legado.app.help.config.ListeningFluidType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A small GLES 3 port of Pavel Dobryakov's WebGL Fluid Simulation.
 *
 * Copyright (c) 2017 Pavel Dobryakov. Original project and algorithm are MIT licensed:
 * https://github.com/PavelDoGreat/WebGL-Fluid-Simulation
 *
 * This renderer deliberately keeps the accepted local proof's simulation and presentation apart:
 * splat energy is fixed at 100%, while the user intensity controls only final output alpha.
 */
internal class ReadAloudStableFluidRenderer {

    private val quad: FloatBuffer = ByteBuffer.allocateDirect(8 * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
            position(0)
        }

    @Volatile
    private var renderState = FluidRenderState()

    private var ready = false
    private var surfaceWidth = 1
    private var surfaceHeight = 1
    private var simWidth = 0
    private var simHeight = 0
    private var dyeWidth = 0
    private var dyeHeight = 0
    private var velocity: DoubleFramebuffer? = null
    private var dye: DoubleFramebuffer? = null
    private var pressure: DoubleFramebuffer? = null
    private var divergence: Framebuffer? = null
    private var curl: Framebuffer? = null
    private var bloom: Framebuffer? = null
    private val bloomFramebuffers = mutableListOf<Framebuffer>()
    private var programs = mutableListOf<GlProgram>()
    private var vertexShader = 0
    private var clearProgram: GlProgram? = null
    private var splatProgram: GlProgram? = null
    private var advectionProgram: GlProgram? = null
    private var curlProgram: GlProgram? = null
    private var vorticityProgram: GlProgram? = null
    private var divergenceProgram: GlProgram? = null
    private var pressureProgram: GlProgram? = null
    private var gradientProgram: GlProgram? = null
    private var bloomPrefilterProgram: GlProgram? = null
    private var bloomBlurProgram: GlProgram? = null
    private var bloomFinalProgram: GlProgram? = null
    private var smokeDisplayProgram: GlProgram? = null
    private var waterDisplayProgram: GlProgram? = null
    private var edgeDisplayProgram: GlProgram? = null
    private var lastFrameNanos = 0L
    private var elapsedSeconds = 0f
    private var simulationFrame = 0
    private var displayType = ListeningFluidType.SMOKE
    private var emitterPhases = FloatArray(9)
    private var validateNextFrame = true

    fun update(type: ListeningFluidType, intensity: Int) {
        renderState = FluidRenderState(type, intensity.coerceIn(0, 100))
    }

    fun onSurfaceCreated() {
        release()
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        checkHalfFloatFramebufferSupport()
        vertexShader = compile(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER)
        clearProgram = program(CLEAR_FRAGMENT_SHADER)
        splatProgram = program(SPLAT_FRAGMENT_SHADER)
        advectionProgram = program(ADVECTION_FRAGMENT_SHADER)
        curlProgram = program(CURL_FRAGMENT_SHADER)
        vorticityProgram = program(VORTICITY_FRAGMENT_SHADER)
        divergenceProgram = program(DIVERGENCE_FRAGMENT_SHADER)
        pressureProgram = program(PRESSURE_FRAGMENT_SHADER)
        gradientProgram = program(GRADIENT_FRAGMENT_SHADER)
        displayType = renderState.type
        ensureDisplayProgram(displayType)
        resetDriver()
        validateNextFrame = true
        ready = true
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        surfaceWidth = width.coerceAtLeast(1)
        surfaceHeight = height.coerceAtLeast(1)
        if (ready) initFramebuffers()
    }

    fun onDrawFrame(frameTimeNanos: Long) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        if (!ready || dye == null) return

        val state = renderState
        if (displayType != state.type) {
            displayType = state.type
            validateNextFrame = true
        }
        if (lastFrameNanos != 0L) {
            injectDriver(simulationFrame, elapsedSeconds, FIXED_STEP_SECONDS)
            simulate(FIXED_STEP_SECONDS)
            elapsedSeconds += FIXED_STEP_SECONDS
            simulationFrame += 1
        }
        lastFrameNanos = frameTimeNanos
        renderDisplay(displayType, state.intensity / 100f)
        if (validateNextFrame) {
            val error = GLES30.glGetError()
            check(error == GLES30.GL_NO_ERROR) {
                "Fluid frame GL error: 0x${error.toString(16)}"
            }
            validateNextFrame = false
        }
    }

    fun release() {
        releaseFramebuffers()
        programs.forEach(GlProgram::release)
        programs.clear()
        if (vertexShader != 0) {
            GLES30.glDeleteShader(vertexShader)
            vertexShader = 0
        }
        clearProgram = null
        splatProgram = null
        advectionProgram = null
        curlProgram = null
        vorticityProgram = null
        divergenceProgram = null
        pressureProgram = null
        gradientProgram = null
        bloomPrefilterProgram = null
        bloomBlurProgram = null
        bloomFinalProgram = null
        smokeDisplayProgram = null
        waterDisplayProgram = null
        edgeDisplayProgram = null
        ready = false
        lastFrameNanos = 0L
    }

    private fun checkHalfFloatFramebufferSupport() {
        val extensions = GLES30.glGetString(GLES30.GL_EXTENSIONS).orEmpty()
        val advertised = extensions.contains("GL_EXT_color_buffer_half_float") ||
            extensions.contains("GL_EXT_color_buffer_float")
        val texture = IntArray(1)
        val framebuffer = IntArray(1)
        GLES30.glGenTextures(1, texture, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA16F, 4, 4, 0,
            GLES30.GL_RGBA, GLES30.GL_HALF_FLOAT, null,
        )
        GLES30.glGenFramebuffers(1, framebuffer, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer[0])
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D, texture[0], 0,
        )
        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glDeleteFramebuffers(1, framebuffer, 0)
        GLES30.glDeleteTextures(1, texture, 0)
        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            Log.w(TAG, "Stable fluid disabled: half-float color FBO unavailable " +
                "(advertised=$advertised, status=0x${status.toString(16)})")
            throw UnsupportedOperationException("Half-float color framebuffer is required")
        }
    }

    private fun initFramebuffers() {
        val sim = resolution(SIM_SHORT_SIDE)
        val dyeResolution = resolution(preferredDyeShortSide())
        if (simWidth == sim.first && simHeight == sim.second &&
            dyeWidth == dyeResolution.first && dyeHeight == dyeResolution.second && dye != null
        ) return
        releaseFramebuffers()
        simWidth = sim.first
        simHeight = sim.second
        dyeWidth = dyeResolution.first
        dyeHeight = dyeResolution.second
        velocity = DoubleFramebuffer(simWidth, simHeight)
        dye = DoubleFramebuffer(dyeWidth, dyeHeight)
        pressure = DoubleFramebuffer(simWidth, simHeight)
        divergence = Framebuffer(simWidth, simHeight)
        curl = Framebuffer(simWidth, simHeight)
        if (displayType == ListeningFluidType.SMOKE) ensureBloomResources()
        clearAll()
        seedInitial()
    }

    private fun initBloomFramebuffers() {
        releaseBloomFramebuffers()
        val base = resolution(BLOOM_SHORT_SIDE)
        bloom = Framebuffer(base.first, base.second)
        var width = base.first
        var height = base.second
        repeat(BLOOM_ITERATIONS) {
            width /= 2
            height /= 2
            if (width < 2 || height < 2) return@repeat
            bloomFramebuffers += Framebuffer(width, height)
        }
    }

    private fun preferredDyeShortSide(): Int {
        val maxTextureSize = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, maxTextureSize, 0)
        val aspect = max(surfaceWidth, surfaceHeight).toFloat() / minOf(surfaceWidth, surfaceHeight)
        return minOf(DYE_SHORT_SIDE, (maxTextureSize[0] / aspect).toInt())
            .coerceAtLeast(SIM_SHORT_SIDE)
    }

    private fun resolution(shortSide: Int): Pair<Int, Int> {
        val aspect = max(surfaceWidth, surfaceHeight).toFloat() / minOf(surfaceWidth, surfaceHeight)
        val longSide = (shortSide * aspect).roundToInt().coerceAtLeast(shortSide)
        return if (surfaceWidth > surfaceHeight) longSide to shortSide else shortSide to longSide
    }

    private fun clearAll() {
        listOf(velocity?.read, velocity?.write, dye?.read, dye?.write, pressure?.read,
            pressure?.write, divergence, curl).forEach { target ->
            target?.let { clear(it, 0f) }
        }
    }

    private fun clear(target: Framebuffer, value: Float) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, target.framebuffer)
        GLES30.glViewport(0, 0, target.width, target.height)
        GLES30.glClearColor(value, value, value, value)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glClearColor(0f, 0f, 0f, 0f)
    }

    private fun resetDriver() {
        val random = Mulberry32(DRIVER_SEED)
        emitterPhases = FloatArray(9) { random.nextFloat() * (2f * PI.toFloat()) }
        elapsedSeconds = 0f
        simulationFrame = 0
        lastFrameNanos = 0L
        if (dye != null) {
            if (displayType == ListeningFluidType.SMOKE) ensureBloomResources()
            clearAll()
            seedInitial()
        }
    }

    private fun seedInitial() {
        val random = Mulberry32(DRIVER_SEED)
        repeat(9) { random.nextFloat() } // Match the phase draws performed by driver.reset().
        repeat(3) { index ->
            val point = roamingPoint(index, 0f)
            val angle = emitterPhases[index]
            splat(point.x, point.y, cos(angle) * 420f, sin(angle) * 420f,
                colorFor(index, 0f, .75f))
        }
    }

    private fun injectDriver(frame: Int, time: Float, dt: Float) {
        val index = frame % 3
        val current = roamingPoint(index, time)
        val previous = roamingPoint(index, time - dt * 3f)
        splat(current.x, current.y, (current.x - previous.x) * 11_000f,
            (current.y - previous.y) * 11_000f, colorFor(index, time, .16f))
    }

    private fun roamingPoint(index: Int, time: Float): Point {
        val phase = emitterPhases[index]
        return when (index) {
            0 -> Point(.2f + .13f * sin(time * .23f + phase),
                .5f + .42f * sin(time * .17f + phase * .7f))
            1 -> Point(.8f + .13f * cos(time * .21f + phase),
                .5f + .42f * cos(time * .16f + phase * .61f))
            else -> Point(.5f + .36f * sin(time * .14f + phase),
                .5f + .24f * sin(time * .22f + phase * .83f))
        }
    }

    private fun colorFor(index: Int, time: Float, amount: Float): Color {
        val hue = positiveModulo(time * .035f + index / 3f +
            emitterPhases[index] / (12f * PI.toFloat()), 1f)
        val rgb = hsvToRgb(hue)
        return Color(rgb.r * amount, rgb.g * amount, rgb.b * amount)
    }

    private fun hsvToRgb(hue: Float): Color {
        val sector = (hue * 6f).toInt()
        val fraction = hue * 6f - sector
        return when (sector % 6) {
            0 -> Color(1f, fraction, 0f)
            1 -> Color(1f - fraction, 1f, 0f)
            2 -> Color(0f, 1f, fraction)
            3 -> Color(0f, 1f - fraction, 1f)
            4 -> Color(fraction, 0f, 1f)
            else -> Color(1f, 0f, 1f - fraction)
        }
    }

    private fun simulate(dt: Float) {
        val velocity = checkNotNull(velocity)
        val dye = checkNotNull(dye)
        val pressure = checkNotNull(pressure)
        val curl = checkNotNull(curl)
        val divergence = checkNotNull(divergence)

        checkNotNull(curlProgram).run {
            bind(); texture("uVelocity", velocity.read.texture, 0)
            uniform2f("uTexelSize", 1f / simWidth, 1f / simHeight)
        }
        blit(curl)

        checkNotNull(vorticityProgram).run {
            bind(); texture("uVelocity", velocity.read.texture, 0)
            texture("uCurl", curl.texture, 1)
            uniform2f("uTexelSize", 1f / simWidth, 1f / simHeight)
            uniform1f("uCurlStrength", CURL_STRENGTH)
            uniform1f("uDt", dt)
        }
        blit(velocity.write); velocity.swap()

        checkNotNull(divergenceProgram).run {
            bind(); texture("uVelocity", velocity.read.texture, 0)
            uniform2f("uTexelSize", 1f / simWidth, 1f / simHeight)
        }
        blit(divergence)

        checkNotNull(clearProgram).run {
            bind(); texture("uTexture", pressure.read.texture, 0)
            uniform1f("uValue", PRESSURE_DECAY)
        }
        blit(pressure.write); pressure.swap()

        repeat(PRESSURE_ITERATIONS) {
            checkNotNull(pressureProgram).run {
                bind(); texture("uPressure", pressure.read.texture, 0)
                texture("uDivergence", divergence.texture, 1)
                uniform2f("uTexelSize", 1f / simWidth, 1f / simHeight)
            }
            blit(pressure.write); pressure.swap()
        }

        checkNotNull(gradientProgram).run {
            bind(); texture("uPressure", pressure.read.texture, 0)
            texture("uVelocity", velocity.read.texture, 1)
            uniform2f("uTexelSize", 1f / simWidth, 1f / simHeight)
        }
        blit(velocity.write); velocity.swap()

        checkNotNull(advectionProgram).run {
            bind(); texture("uVelocity", velocity.read.texture, 0)
            texture("uSource", velocity.read.texture, 1)
            uniform2f("uVelocityTexelSize", 1f / simWidth, 1f / simHeight)
            uniform1f("uDt", dt)
            uniform1f("uDissipation", VELOCITY_DISSIPATION)
        }
        blit(velocity.write); velocity.swap()

        checkNotNull(advectionProgram).run {
            bind(); texture("uVelocity", velocity.read.texture, 0)
            texture("uSource", dye.read.texture, 1)
            uniform2f("uVelocityTexelSize", 1f / simWidth, 1f / simHeight)
            uniform1f("uDt", dt)
            uniform1f("uDissipation", DENSITY_DISSIPATION)
        }
        blit(dye.write); dye.swap()
    }

    private fun splat(x: Float, y: Float, dx: Float, dy: Float, color: Color) {
        val velocity = checkNotNull(velocity)
        val dye = checkNotNull(dye)
        val aspect = surfaceWidth.toFloat() / surfaceHeight
        val program = checkNotNull(splatProgram)
        program.bind()
        program.uniform1f("uAspectRatio", aspect)
        program.uniform2f("uPoint", x, y)
        program.uniform1f("uRadius", SMOKE_SPLAT_RADIUS / 100f)
        program.texture("uTarget", velocity.read.texture, 0)
        program.uniform3f("uColor", dx, dy, 0f)
        blit(velocity.write); velocity.swap()

        program.bind()
        program.uniform1f("uAspectRatio", aspect)
        program.uniform2f("uPoint", x, y)
        program.uniform1f("uRadius", SMOKE_SPLAT_RADIUS / 100f)
        program.texture("uTarget", dye.read.texture, 0)
        program.uniform3f("uColor", color.r, color.g, color.b)
        blit(dye.write); dye.swap()
    }

    private fun applyBloom(source: Framebuffer, destination: Framebuffer) {
        if (bloomFramebuffers.size < 2) return
        GLES30.glDisable(GLES30.GL_BLEND)

        var last = destination
        val knee = BLOOM_THRESHOLD * BLOOM_SOFT_KNEE + .0001f
        checkNotNull(bloomPrefilterProgram).run {
            bind()
            texture("uTexture", source.texture, 0)
            uniform3f("uCurve", BLOOM_THRESHOLD - knee, knee * 2f, .25f / knee)
            uniform1f("uThreshold", BLOOM_THRESHOLD)
        }
        blit(last)

        val blur = checkNotNull(bloomBlurProgram)
        bloomFramebuffers.forEach { target ->
            blur.bind()
            blur.texture("uTexture", last.texture, 0)
            blur.uniform2f("uTexelSize", 1f / last.width, 1f / last.height)
            blit(target)
            last = target
        }

        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
        GLES30.glEnable(GLES30.GL_BLEND)
        for (index in bloomFramebuffers.size - 2 downTo 0) {
            val target = bloomFramebuffers[index]
            blur.bind()
            blur.texture("uTexture", last.texture, 0)
            blur.uniform2f("uTexelSize", 1f / last.width, 1f / last.height)
            blit(target)
            last = target
        }
        GLES30.glDisable(GLES30.GL_BLEND)

        checkNotNull(bloomFinalProgram).run {
            bind()
            texture("uTexture", last.texture, 0)
            uniform2f("uTexelSize", 1f / last.width, 1f / last.height)
            uniform1f("uIntensity", BLOOM_INTENSITY)
        }
        blit(destination)
    }

    private fun renderDisplay(type: ListeningFluidType, alphaScale: Float) {
        val dye = checkNotNull(dye)
        val program = when (type) {
            ListeningFluidType.SMOKE -> {
                ensureBloomResources()
                val bloom = checkNotNull(bloom)
                applyBloom(dye.read, bloom)
                ensureDisplayProgram(type)
            }
            ListeningFluidType.WATER,
            ListeningFluidType.EDGE -> ensureDisplayProgram(type)
        }
        program.bind()
        program.texture("uTexture", dye.read.texture, 0)
        program.uniform2f("uTexelSize", 1f / surfaceWidth, 1f / surfaceHeight)
        program.uniform1f("uAlphaScale", alphaScale)
        program.uniform1f("uExposure", DISPLAY_EXPOSURE)
        when (type) {
            ListeningFluidType.SMOKE -> {
                program.texture("uBloom", checkNotNull(bloom).texture, 1)
            }
            ListeningFluidType.WATER -> {
                program.texture("uVelocity", checkNotNull(velocity).read.texture, 1)
                program.uniform1f("uTime", elapsedSeconds)
                program.uniform3f("uWaterColorA", 24f / 255f, 224f / 255f, 1f)
                program.uniform3f("uWaterColorB", 41f / 255f, 121f / 255f, 1f)
                program.uniform3f("uWaterColorC", 198f / 255f, 60f / 255f, 1f)
            }
            ListeningFluidType.EDGE -> Unit
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
        drawQuad()
    }

    private fun ensureBloomResources() {
        if (bloomPrefilterProgram == null) {
            bloomPrefilterProgram = program(BLOOM_PREFILTER_FRAGMENT_SHADER)
            bloomBlurProgram = program(BLOOM_BLUR_FRAGMENT_SHADER)
            bloomFinalProgram = program(BLOOM_FINAL_FRAGMENT_SHADER)
        }
        if (bloom == null) initBloomFramebuffers()
    }

    private fun ensureDisplayProgram(type: ListeningFluidType): GlProgram = when (type) {
        ListeningFluidType.SMOKE -> smokeDisplayProgram
            ?: program(SMOKE_DISPLAY_FRAGMENT_SHADER).also { smokeDisplayProgram = it }
        ListeningFluidType.WATER -> waterDisplayProgram
            ?: program(WATER_DISPLAY_FRAGMENT_SHADER).also { waterDisplayProgram = it }
        ListeningFluidType.EDGE -> edgeDisplayProgram
            ?: program(EDGE_DISPLAY_FRAGMENT_SHADER).also { edgeDisplayProgram = it }
    }

    private fun blit(target: Framebuffer) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, target.framebuffer)
        GLES30.glViewport(0, 0, target.width, target.height)
        drawQuad()
    }

    private fun drawQuad() {
        quad.position(0)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, quad)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun releaseFramebuffers() {
        velocity?.release(); velocity = null
        dye?.release(); dye = null
        pressure?.release(); pressure = null
        divergence?.release(); divergence = null
        curl?.release(); curl = null
        releaseBloomFramebuffers()
        simWidth = 0; simHeight = 0; dyeWidth = 0; dyeHeight = 0
    }

    private fun releaseBloomFramebuffers() {
        bloom?.release(); bloom = null
        bloomFramebuffers.forEach(Framebuffer::release)
        bloomFramebuffers.clear()
    }

    private fun program(fragment: String): GlProgram {
        check(vertexShader != 0) { "Fluid vertex shader is unavailable" }
        return GlProgram(vertexShader, fragment).also(programs::add)
    }

    private class Framebuffer(val width: Int, val height: Int) {
        val texture: Int
        val framebuffer: Int

        init {
            val textures = IntArray(1)
            GLES30.glGenTextures(1, textures, 0)
            texture = textures[0]
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA16F, width, height, 0,
                GLES30.GL_RGBA, GLES30.GL_HALF_FLOAT, null)
            val framebuffers = IntArray(1)
            GLES30.glGenFramebuffers(1, framebuffers, 0)
            framebuffer = framebuffers[0]
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer)
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
                GLES30.GL_TEXTURE_2D, texture, 0)
            check(GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) ==
                GLES30.GL_FRAMEBUFFER_COMPLETE) { "Unable to create half-float fluid framebuffer" }
        }

        fun release() {
            GLES30.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
            GLES30.glDeleteTextures(1, intArrayOf(texture), 0)
        }
    }

    private class DoubleFramebuffer(width: Int, height: Int) {
        var read = Framebuffer(width, height)
        var write = Framebuffer(width, height)
        fun swap() { val old = read; read = write; write = old }
        fun release() { read.release(); write.release() }
    }

    private class GlProgram(vertexShader: Int, fragmentSource: String) {
        private val id = GLES30.glCreateProgram()
        private val uniforms = HashMap<String, Int>()

        init {
            val fragment = compile(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
            GLES30.glAttachShader(id, vertexShader)
            GLES30.glAttachShader(id, fragment)
            GLES30.glLinkProgram(id)
            val status = IntArray(1)
            GLES30.glGetProgramiv(id, GLES30.GL_LINK_STATUS, status, 0)
            GLES30.glDeleteShader(fragment)
            check(status[0] == GLES30.GL_TRUE) { "Fluid program link failed: ${GLES30.glGetProgramInfoLog(id)}" }
        }

        fun bind() = GLES30.glUseProgram(id)
        fun release() = GLES30.glDeleteProgram(id)
        fun uniform1f(name: String, value: Float) = GLES30.glUniform1f(location(name), value)
        fun uniform2f(name: String, x: Float, y: Float) = GLES30.glUniform2f(location(name), x, y)
        fun uniform3f(name: String, x: Float, y: Float, z: Float) = GLES30.glUniform3f(location(name), x, y, z)
        fun texture(name: String, texture: Int, unit: Int) {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
            GLES30.glUniform1i(location(name), unit)
        }
        private fun location(name: String) = uniforms.getOrPut(name) { GLES30.glGetUniformLocation(id, name) }
    }

    private class Mulberry32(seed: Int) {
        private var value = seed.toUInt()
        fun nextFloat(): Float {
            value += 0x6D2B79F5u
            var result = value
            result = (result xor (result shr 15)) * (result or 1u)
            result = result xor (result + (result xor (result shr 7)) * (result or 61u))
            return ((result xor (result shr 14)).toDouble() / 4_294_967_296.0).toFloat()
        }
    }

    private data class FluidRenderState(
        val type: ListeningFluidType = ListeningFluidType.SMOKE,
        val intensity: Int = 100,
    )
    private data class Point(val x: Float, val y: Float)
    private data class Color(val r: Float, val g: Float, val b: Float)
    private companion object {
        const val TAG = "ReadAloudStableFluid"
        const val DRIVER_SEED = 20260827
        const val SIM_SHORT_SIDE = 128
        const val DYE_SHORT_SIDE = 512
        const val BLOOM_SHORT_SIDE = 256
        const val PRESSURE_ITERATIONS = 15
        const val FIXED_STEP_SECONDS = 1f / 60f
        const val DENSITY_DISSIPATION = 1f
        const val VELOCITY_DISSIPATION = .2f
        const val PRESSURE_DECAY = .8f
        const val CURL_STRENGTH = 30f
        const val SMOKE_SPLAT_RADIUS = .28f
        const val BLOOM_ITERATIONS = 3
        const val BLOOM_INTENSITY = .12f
        const val BLOOM_THRESHOLD = .92f
        const val BLOOM_SOFT_KNEE = .2f
        const val DISPLAY_EXPOSURE = 2.4f

        fun positiveModulo(value: Float, divisor: Float) = ((value % divisor) + divisor) % divisor

        fun compile(type: Int, source: String): Int {
            val shader = GLES30.glCreateShader(type)
            GLES30.glShaderSource(shader, source)
            GLES30.glCompileShader(shader)
            val status = IntArray(1)
            GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
            check(status[0] == GLES30.GL_TRUE) { "Fluid shader compile failed: ${GLES30.glGetShaderInfoLog(shader)}" }
            return shader
        }

        const val VERTEX_SHADER = """#version 300 es
            layout(location=0) in vec2 aPosition;
            out vec2 vUv;
            void main() { vUv = aPosition * 0.5 + 0.5; gl_Position = vec4(aPosition, 0.0, 1.0); }
        """
        const val SHADER_HEADER = """#version 300 es
            precision highp float;
            in vec2 vUv;
            out vec4 fragColor;
        """
        const val CLEAR_FRAGMENT_SHADER = SHADER_HEADER + """
            uniform sampler2D uTexture; uniform float uValue;
            void main() { fragColor = texture(uTexture, vUv) * uValue; }
        """
        const val SPLAT_FRAGMENT_SHADER = SHADER_HEADER + """
            uniform sampler2D uTarget; uniform float uAspectRatio; uniform vec2 uPoint;
            uniform vec3 uColor; uniform float uRadius;
            void main() { vec2 p = vUv - uPoint; p.x *= uAspectRatio;
                vec3 splat = exp(-dot(p,p) / uRadius) * uColor;
                fragColor = vec4(texture(uTarget, vUv).xyz + splat, 1.0); }
        """
        const val ADVECTION_FRAGMENT_SHADER = SHADER_HEADER + """
            uniform sampler2D uVelocity; uniform sampler2D uSource;
            uniform vec2 uVelocityTexelSize; uniform float uDt; uniform float uDissipation;
            void main() { vec2 coord = vUv - uDt * texture(uVelocity, vUv).xy * uVelocityTexelSize;
                fragColor = texture(uSource, coord) / (1.0 + uDissipation * uDt); }
        """
        const val CURL_FRAGMENT_SHADER = SHADER_HEADER + """
            uniform sampler2D uVelocity; uniform vec2 uTexelSize;
            void main() { float L=texture(uVelocity,vUv-vec2(uTexelSize.x,0)).y;
                float R=texture(uVelocity,vUv+vec2(uTexelSize.x,0)).y;
                float B=texture(uVelocity,vUv-vec2(0,uTexelSize.y)).x;
                float T=texture(uVelocity,vUv+vec2(0,uTexelSize.y)).x;
                fragColor=vec4(0.5*(R-L-T+B),0,0,1); }
        """
        const val VORTICITY_FRAGMENT_SHADER = SHADER_HEADER + """
            uniform sampler2D uVelocity; uniform sampler2D uCurl; uniform vec2 uTexelSize;
            uniform float uCurlStrength; uniform float uDt;
            void main() { float L=texture(uCurl,vUv-vec2(uTexelSize.x,0)).x;
                float R=texture(uCurl,vUv+vec2(uTexelSize.x,0)).x;
                float B=texture(uCurl,vUv-vec2(0,uTexelSize.y)).x;
                float T=texture(uCurl,vUv+vec2(0,uTexelSize.y)).x;
                float C=texture(uCurl,vUv).x; vec2 force=0.5*vec2(abs(T)-abs(B),abs(R)-abs(L));
                force/=length(force)+0.0001; force*=uCurlStrength*C; force.y*=-1.0;
                vec2 velocity=texture(uVelocity,vUv).xy+force*uDt;
                velocity=clamp(velocity,vec2(-1000.0),vec2(1000.0));
                fragColor=vec4(velocity,0,1); }
        """
        const val DIVERGENCE_FRAGMENT_SHADER = SHADER_HEADER + """
            uniform sampler2D uVelocity; uniform vec2 uTexelSize;
            void main() { vec2 leftUv=vUv-vec2(uTexelSize.x,0);
                vec2 rightUv=vUv+vec2(uTexelSize.x,0);
                vec2 bottomUv=vUv-vec2(0,uTexelSize.y);
                vec2 topUv=vUv+vec2(0,uTexelSize.y);
                float L=texture(uVelocity,leftUv).x; float R=texture(uVelocity,rightUv).x;
                float B=texture(uVelocity,bottomUv).y; float T=texture(uVelocity,topUv).y;
                vec2 C=texture(uVelocity,vUv).xy;
                if(leftUv.x<0.0)L=-C.x; if(rightUv.x>1.0)R=-C.x;
                if(topUv.y>1.0)T=-C.y; if(bottomUv.y<0.0)B=-C.y;
                fragColor=vec4(0.5*(R-L+T-B),0,0,1); }
        """
        const val PRESSURE_FRAGMENT_SHADER = SHADER_HEADER + """
            uniform sampler2D uPressure; uniform sampler2D uDivergence; uniform vec2 uTexelSize;
            void main() { float L=texture(uPressure,vUv-vec2(uTexelSize.x,0)).x;
                float R=texture(uPressure,vUv+vec2(uTexelSize.x,0)).x;
                float B=texture(uPressure,vUv-vec2(0,uTexelSize.y)).x;
                float T=texture(uPressure,vUv+vec2(0,uTexelSize.y)).x;
                float d=texture(uDivergence,vUv).x; fragColor=vec4((L+R+B+T-d)*0.25,0,0,1); }
        """
        const val GRADIENT_FRAGMENT_SHADER = SHADER_HEADER + """
            uniform sampler2D uPressure; uniform sampler2D uVelocity; uniform vec2 uTexelSize;
            void main() { float L=texture(uPressure,vUv-vec2(uTexelSize.x,0)).x;
                float R=texture(uPressure,vUv+vec2(uTexelSize.x,0)).x;
                float B=texture(uPressure,vUv-vec2(0,uTexelSize.y)).x;
                float T=texture(uPressure,vUv+vec2(0,uTexelSize.y)).x;
                vec2 velocity=texture(uVelocity,vUv).xy-vec2(R-L,T-B);
                fragColor=vec4(velocity,0,1); }
        """
        const val BLOOM_PREFILTER_FRAGMENT_SHADER = SHADER_HEADER + """
            uniform sampler2D uTexture; uniform vec3 uCurve; uniform float uThreshold;
            void main(){vec3 c=texture(uTexture,vUv).rgb;float br=max(c.r,max(c.g,c.b));
                float rq=clamp(br-uCurve.x,0.0,uCurve.y);rq=uCurve.z*rq*rq;
                c*=max(rq,br-uThreshold)/max(br,0.0001);fragColor=vec4(c,0.0);}
        """
        const val BLOOM_BLUR_FRAGMENT_SHADER = SHADER_HEADER + """
            uniform sampler2D uTexture; uniform vec2 uTexelSize;
            void main(){vec4 sum=texture(uTexture,vUv-vec2(uTexelSize.x,0));
                sum+=texture(uTexture,vUv+vec2(uTexelSize.x,0));
                sum+=texture(uTexture,vUv-vec2(0,uTexelSize.y));
                sum+=texture(uTexture,vUv+vec2(0,uTexelSize.y));fragColor=sum*0.25;}
        """
        const val BLOOM_FINAL_FRAGMENT_SHADER = SHADER_HEADER + """
            uniform sampler2D uTexture; uniform vec2 uTexelSize; uniform float uIntensity;
            void main(){vec4 sum=texture(uTexture,vUv-vec2(uTexelSize.x,0));
                sum+=texture(uTexture,vUv+vec2(uTexelSize.x,0));
                sum+=texture(uTexture,vUv-vec2(0,uTexelSize.y));
                sum+=texture(uTexture,vUv+vec2(0,uTexelSize.y));fragColor=sum*0.25*uIntensity;}
        """
        const val DISPLAY_COMMON_SHADER = """
            uniform vec2 uTexelSize; uniform float uAlphaScale; uniform float uExposure;
            float foregroundAttenuation(vec2 uv){
                float centerX=1.0-smoothstep(0.20,0.46,abs(uv.x-0.5));
                float bodyY=1.0-smoothstep(0.12,0.33,abs(uv.y-0.455));
                float controlsY=1.0-smoothstep(0.08,0.22,abs(uv.y-0.22));
                float guarded=max(centerX*bodyY*0.52,centerX*controlsY*0.60);
                return 1.0-guarded;
            }
            void writeEffect(vec3 c){
                c*=uExposure;float luma=dot(c,vec3(0.2126,0.7152,0.0722));
                c=mix(vec3(luma),c,1.16);c=(c-0.5)*1.06+0.5;c=clamp(c,0.0,1.0);
                float alpha=clamp(max(c.r,max(c.g,c.b))*uAlphaScale,0.0,1.0);
                fragColor=vec4(c*uAlphaScale,alpha);
            }
        """
        const val SMOKE_DISPLAY_FRAGMENT_SHADER = SHADER_HEADER + DISPLAY_COMMON_SHADER + """
            uniform sampler2D uTexture; uniform sampler2D uBloom;
            vec3 linearToGamma(vec3 color){color=max(color,vec3(0.0));
                return max(1.055*pow(color,vec3(0.416666667))-0.055,vec3(0.0));}
            void main(){vec3 c=texture(uTexture,vUv).rgb;
                vec3 lc=texture(uTexture,vUv-vec2(uTexelSize.x,0)).rgb;
                vec3 rc=texture(uTexture,vUv+vec2(uTexelSize.x,0)).rgb;
                vec3 bc=texture(uTexture,vUv-vec2(0,uTexelSize.y)).rgb;
                vec3 tc=texture(uTexture,vUv+vec2(0,uTexelSize.y)).rgb;
                float dx=length(rc)-length(lc);float dy=length(tc)-length(bc);
                vec3 n=normalize(vec3(dx,dy,length(uTexelSize)));
                float diffuse=clamp(dot(n,vec3(0.0,0.0,1.0))+0.7,0.7,1.0);c*=diffuse;
                vec3 bloomColor=texture(uBloom,vUv).rgb;
                float noise=fract(52.9829189*fract(dot(vUv/uTexelSize,vec2(0.06711056,0.00583715))));
                bloomColor+=(noise*2.0-1.0)/255.0;c+=linearToGamma(bloomColor);
                writeEffect(c);}
        """
        const val WATER_DISPLAY_FRAGMENT_SHADER = SHADER_HEADER + DISPLAY_COMMON_SHADER + """
            uniform sampler2D uTexture; uniform sampler2D uVelocity; uniform float uTime;
            uniform vec3 uWaterColorA; uniform vec3 uWaterColorB; uniform vec3 uWaterColorC;
            void main(){vec3 c=texture(uTexture,vUv).rgb;
                vec3 lc=texture(uTexture,vUv-vec2(uTexelSize.x,0)).rgb;
                vec3 rc=texture(uTexture,vUv+vec2(uTexelSize.x,0)).rgb;
                vec3 bc=texture(uTexture,vUv-vec2(0,uTexelSize.y)).rgb;
                vec3 tc=texture(uTexture,vUv+vec2(0,uTexelSize.y)).rgb;
                float rawDensity=max(c.r,max(c.g,c.b));
                float density=log(1.0+rawDensity*2.0);
                float densityL=log(1.0+max(lc.r,max(lc.g,lc.b))*2.0);
                float densityR=log(1.0+max(rc.r,max(rc.g,rc.b))*2.0);
                float densityT=log(1.0+max(tc.r,max(tc.g,tc.b))*2.0);
                float densityB=log(1.0+max(bc.r,max(bc.g,bc.b))*2.0);
                vec2 densityGradient=vec2(densityR-densityL,densityT-densityB);
                float presence=smoothstep(0.015,0.12,density);
                vec2 velocityWarp=clamp(texture(uVelocity,vUv).xy*0.00045,vec2(-0.025),vec2(0.025));
                vec2 waterUv=vUv+velocityWarp;
                float waveA=sin(waterUv.x*27.0+waterUv.y*7.0+uTime*0.56);
                float waveB=sin(waterUv.x*53.0+waterUv.y*17.0+uTime*1.34);
                vec2 microGradient=vec2(waveA+waveB*0.42,waveA*0.22-waveB*0.58)*0.0035;
                vec2 surfaceGradient=densityGradient*0.94+microGradient*presence;
                float gradient=length(surfaceGradient);
                float curvature=abs(densityL+densityR+densityT+densityB-density*4.0);
                float depth=smoothstep(0.10,0.56,density);
                vec3 colorWeights=max(c,vec3(0.0));
                colorWeights/=max(colorWeights.r+colorWeights.g+colorWeights.b,0.0001);
                vec3 waterTint=uWaterColorA*colorWeights.r+uWaterColorB*colorWeights.g+
                    uWaterColorC*colorWeights.b;
                vec3 surfaceNormal=normalize(vec3(-surfaceGradient*15.0,0.22));
                vec3 surfaceLight=normalize(vec3(-0.35,0.55,1.0));
                float ridge=smoothstep(0.004,0.014,gradient);
                float ridgeWindow=ridge*(1.0-smoothstep(0.028,0.052,gradient));
                float fresnel=pow(1.0-clamp(surfaceNormal.z,0.0,1.0),1.35);
                float specular=pow(max(dot(surfaceNormal,surfaceLight),0.0),40.0)*ridge;
                float crest=(1.0-smoothstep(0.035,0.095,abs(waveA-0.28)))*ridge;
                float caustic=smoothstep(0.0003,0.0015,curvature)*
                    (1.0-smoothstep(0.004,0.011,curvature));
                vec3 bodyColor=waterTint*presence*mix(0.065,0.145,depth);
                vec3 ridgeColor=mix(waterTint,vec3(0.90,0.98,1.0),0.16);
                c=(bodyColor+ridgeColor*presence*(ridgeWindow*0.18+crest*0.12+
                    caustic*0.18+fresnel*0.12+specular*0.72))*foregroundAttenuation(vUv);
                writeEffect(c);}
        """
        const val EDGE_DISPLAY_FRAGMENT_SHADER = SHADER_HEADER + DISPLAY_COMMON_SHADER + """
            uniform sampler2D uTexture;
            void main(){vec3 c=texture(uTexture,vUv).rgb;
                vec3 lc=texture(uTexture,vUv-vec2(uTexelSize.x,0)).rgb;
                vec3 rc=texture(uTexture,vUv+vec2(uTexelSize.x,0)).rgb;
                vec3 bc=texture(uTexture,vUv-vec2(0,uTexelSize.y)).rgb;
                vec3 tc=texture(uTexture,vUv+vec2(0,uTexelSize.y)).rgb;
                float rawDensity=max(c.r,max(c.g,c.b));
                float density=log(1.0+rawDensity*2.0);
                float densityL=log(1.0+max(lc.r,max(lc.g,lc.b))*2.0);
                float densityR=log(1.0+max(rc.r,max(rc.g,rc.b))*2.0);
                float densityT=log(1.0+max(tc.r,max(tc.g,tc.b))*2.0);
                float densityB=log(1.0+max(bc.r,max(bc.g,bc.b))*2.0);
                vec2 densityGradient=vec2(densityR-densityL,densityT-densityB);
                float gradient=length(densityGradient);
                float outerDistance=abs(density-0.10);float innerDistance=abs(density-0.34);
                float outerCore=1.0-smoothstep(0.012,0.030,outerDistance);
                float innerCore=1.0-smoothstep(0.016,0.038,innerDistance);
                float outerHalo=1.0-smoothstep(0.032,0.078,outerDistance);
                float innerHalo=1.0-smoothstep(0.040,0.090,innerDistance);
                float gradientSupport=smoothstep(0.0008,0.018,gradient);
                float haloSupport=smoothstep(0.0004,0.010,gradient);
                float core=max(outerCore,innerCore*0.82)*gradientSupport;
                float halo=max(outerHalo,innerHalo*0.72)*(1.0-core)*haloSupport*0.22;
                vec3 hue=c/max(rawDensity,0.0001);vec3 haloColor=mix(hue,vec3(1.0),0.18);
                c=(hue*core*1.12+haloColor*halo)*foregroundAttenuation(vUv);
                writeEffect(c);}
        """
    }
}
